#!/usr/bin/env python3
"""Daily New York Times print front page for an Electric Objects frame, optionally glitched.

Downloads today's front-page scan (falls back up to 3 days if it isn't published yet), renders
it at the frame's 1080x1920, and writes one of:

  clean   a sharp still JPEG
  glitch  a single glitched JPEG
  gif     an animated GIF: the clean page holds, then short glitch bursts (default)

With --frame IP it uploads the result to the frame's web page and removes the previous day's
file, so exactly one front page is ever in rotation.

  pip3 install pymupdf pillow numpy
  python3 nyt-frontpage.py --style gif --frame 192.168.4.33

For personal, at-home display. The scan is the Times' copyrighted work; don't republish it.
"""
import argparse
import datetime as dt
import io
import json
import os
import random
import sys
import urllib.parse
import urllib.request
import uuid

import numpy as np
import pymupdf
from PIL import Image, ImageFilter

W, H = 1080, 1920
URL = "https://static01.nyt.com/images/{d:%Y/%m/%d}/nytfrontpage/scan.pdf"
PREFIX = "nyt-frontpage-"
UA = {"User-Agent": "Mozilla/5.0 (frame front page fetcher)"}


# ---------------------------------------------------------------- fetch + render
def fetch_pdf(day=None, back=3):
    start = day or dt.date.today()
    for i in range(back + 1):
        d = start - dt.timedelta(days=i)
        try:
            with urllib.request.urlopen(urllib.request.Request(URL.format(d=d), headers=UA), timeout=30) as r:
                data = r.read()
            if data[:4] == b"%PDF":
                return d, data
        except Exception:
            pass  # not published yet (S3 answers 403) or a network blip: try the day before
    sys.exit("No front page found for %s or the %d days before." % (start, back))


def render(pdf_bytes):
    page = pymupdf.open(stream=pdf_bytes, filetype="pdf")[0]
    zoom = max(W / page.rect.width, H / page.rect.height)      # cover the panel
    pix = page.get_pixmap(matrix=pymupdf.Matrix(zoom, zoom), alpha=False)
    img = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
    left, top = (img.width - W) // 2, (img.height - H) // 2
    return img.crop((left, top, left + W, top + H))


# ---------------------------------------------------------------- glitch
def glitch(img, rng, strength=1.0, zone=None):
    """Channel split, displaced horizontal bands, a few datamosh blocks, scanlines.
    zone=(y0, y1) confines it to one strip, which also lets the GIF store only that strip."""
    full = np.asarray(img).copy()
    y0, y1 = zone or (0, full.shape[0])
    a = full[y0:y1]
    h, w, _ = a.shape

    # RGB split across the whole frame, small
    s = int(rng.integers(2, 6) * strength)
    a[:, :, 0] = np.roll(a[:, :, 0], -s, axis=1)
    a[:, :, 2] = np.roll(a[:, :, 2], s, axis=1)

    # displaced bands
    for _ in range(int(rng.integers(4, 14) * strength)):
        bh = int(rng.integers(4, min(90, h // 3)))
        y = int(rng.integers(0, h - bh))
        dx = int(rng.normal(0, 60 * strength))
        band = a[y:y + bh]
        band[:] = np.roll(band, dx, axis=1)
        if rng.random() < 0.35:                               # extra channel tear inside the band
            c = int(rng.integers(0, 3))
            band[:, :, c] = np.roll(band[:, :, c], int(rng.integers(-30, 30)), axis=1)
        if rng.random() < 0.08:                               # rare inverted strip
            band[:] = 255 - band

    # datamosh blocks: copy a nearby block onto itself, offset
    for _ in range(int(rng.integers(0, 5) * strength)):
        bw, bh = int(rng.integers(60, 400)), int(rng.integers(20, 160))
        x, y = int(rng.integers(0, w - bw)), int(rng.integers(0, h - bh))
        sx = min(max(0, x + int(rng.integers(-120, 120))), w - bw)
        sy = min(max(0, y + int(rng.integers(-40, 40))), h - bh)
        a[y:y + bh, x:x + bw] = a[sy:sy + bh, sx:sx + bw]

    # scanlines
    a[::3] = (a[::3].astype(np.int16) * 0.86).astype(np.uint8)
    return Image.fromarray(full)


def make_gif(img, seed):
    """Clean page holds for a while, then a burst; GIF per-frame durations keep frames few, so the
    file stays small and the 2016-era board decodes it smoothly."""
    rng = np.random.default_rng(seed)
    frames, durations = [img], [6000]
    for burst in range(3):
        zh = int(rng.integers(260, 560))
        zy = int(rng.integers(0, H - zh))
        for k in range(int(rng.integers(3, 7))):
            j = int(rng.integers(-40, 40))                    # the strip jitters a little per frame
            zone = (max(0, zy + j), min(H, zy + zh + j))
            frames.append(glitch(img, rng, strength=float(rng.uniform(0.5, 1.6)), zone=zone))
            durations.append(int(rng.integers(50, 110)))
        frames.append(img)
        durations.append(int(rng.integers(2500, 7000)))
    # one shared palette from the clean page plus a glitch frame keeps colors stable between frames
    sample = Image.new("RGB", (W, H * 2)); sample.paste(img, (0, 0)); sample.paste(frames[1], (0, H))
    pal = sample.quantize(colors=256, method=Image.Quantize.MEDIANCUT)
    q = [f.quantize(palette=pal, dither=Image.Dither.NONE) for f in frames]
    buf = io.BytesIO()
    q[0].save(buf, "GIF", save_all=True, append_images=q[1:], duration=durations, loop=0,
              optimize=False, disposal=1)
    return buf.getvalue()


# ---------------------------------------------------------------- frame upload
def frame_request(base, path, data=None, ctype=None, method=None):
    req = urllib.request.Request(base + path, data=data, method=method)
    if ctype:
        req.add_header("Content-Type", ctype)
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.loads(r.read() or b"null")


def upload(ip, name, blob):
    base = ("http://%s:8080" % ip if ":" not in ip else "http://%s" % ip)
    old = [f["name"] for f in frame_request(base, "/files") if f["name"].startswith(PREFIX)]
    if name in old:
        print("%s is already on the frame; nothing to do" % name)
        return
    b = uuid.uuid4().hex
    body = (("--%s\r\nContent-Disposition: form-data; name=\"file\"; filename=\"%s\"\r\n"
             "Content-Type: application/octet-stream\r\n\r\n" % (b, name)).encode()
            + blob + ("\r\n--%s--\r\n" % b).encode())
    frame_request(base, "/upload", body, "multipart/form-data; boundary=" + b, "POST")
    for n in old:
        if n != name:
            frame_request(base, "/files/%s/delete" % urllib.parse.quote(n), b"", None, "POST")
    print("uploaded %s to %s (removed %d old)" % (name, ip, len([n for n in old if n != name])))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--style", choices=["clean", "glitch", "gif"], default="gif")
    ap.add_argument("--date", help="YYYY-MM-DD (default: today, falling back to earlier days)")
    ap.add_argument("--frame", metavar="IP", help="upload to this frame and replace yesterday's page")
    ap.add_argument("--out", default=".", help="folder to also save the file in (default: here)")
    args = ap.parse_args()

    day, pdf = fetch_pdf(dt.date.fromisoformat(args.date) if args.date else None)
    page = render(pdf)
    seed = int(day.strftime("%Y%m%d"))                         # same day -> same glitches
    if args.style == "clean":
        ext, blob = "jpg", _jpeg(page)
    elif args.style == "glitch":
        ext, blob = "jpg", _jpeg(glitch(page, np.random.default_rng(seed), 1.3))
    else:
        ext, blob = "gif", make_gif(page, seed)
    name = "%s%s.%s" % (PREFIX, day.isoformat(), ext)
    path = os.path.join(args.out, name)
    with open(path, "wb") as f:
        f.write(blob)
    print("front page for %s -> %s (%d KB)" % (day, path, len(blob) // 1024))
    if args.frame:
        upload(args.frame, name, blob)


def _jpeg(img):
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=90, optimize=True)
    return buf.getvalue()


if __name__ == "__main__":
    main()
