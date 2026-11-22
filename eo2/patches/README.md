# Patches on top of aphex3k/EO1

`0001-mobile-web-page.patch` — against aphex3k/EO1 @ 4508656:

- `/` is a new phone-first page (`app/src/main/resources/web/mobile.html`): add photos
  (resized and converted on the phone to 1080×1920 JPEG, so HEIC works; "Fill frame" or
  "Show whole photo" with blurred edges), gallery with tap-to-preview, **Show now**, **Remove**,
  multi-select remove, GIF badges (GIFs upload untouched and animate on the frame), Next / Screen / Brightness, and simple settings: picture interval, night
  sleep hours, time zone.
- **Extras → The New York Times front page**: the phone downloads the Times' daily
  `scan.pdf` (CORS-open; falls back up to 3 days), renders it with pdf.js, makes a clean JPEG,
  a glitched JPEG or an animated glitch GIF (gifenc; unchanged pixels transparent, ~5 MB), uploads
  it and removes the previous front page. Optional "refresh whenever I open this page". Needs the
  phone online (pdf.js and gifenc load from cdnjs / jsdelivr).
- Compact layout: header status, "now playing" card with Next / Power and a brightness slider
  (`?action=brightness&step=0..7` sets a level directly), frame name and frame info in Settings,
  on-page confirm sheet instead of browser dialogs.
- `/thumb/<name>`: cached ~270x480 JPEG thumbnails for the gallery (beside `uploaded/` in
  `files/thumbs`), loaded by an IntersectionObserver.
- `POST /settings` also accepts `name` (the frame's display name).
- **Extras → Spotify**: album grid (9/12/20), animated grid (one cover at a time cross-fades,
  per-frame palettes, ~0.8 MB) and a now-playing poster that goes on screen immediately, with
  optional follow-along. OAuth PKCE in the browser (bundled SHA-256, since the http page has no
  `crypto.subtle`); logins return via `eo2/spotify/callback.html` on GitHub Pages, or directly
  when the page is served over https (Tailscale Serve). See `../SPOTIFY-AND-TAILSCALE.md`.
- "Whole photo" uses a scaled-down/up blur, since Safari's canvas has no `ctx.filter`.
- **Generative art**: `/art/` serves a self-contained animated art page (12 styles, ES5 for the
  frame's Android 4.4 WebView, rendered at 540x960 and scaled). `?style=`, `?styles=a,b`, `?seed=`.
  Turning art on adds `web` backend entries (`art-1`…`art-N`) pointing at it, so each appearance
  is a fresh piece.
- **Show on the frame**: `wall-sources.json` beside `uploaded/` switches photos / art / NYT /
  Spotify; the local backend skips switched-off kinds by file-name prefix. `GET /settings` returns
  it; `POST /settings {"sources": {...}}` saves it and rebuilds the rotation. Unit-tested in
  `WallSourcesTest`.
- The original page moves to `/advanced`.
- `POST /settings` changes only interval, quiet hours and time zone (no token, same-origin
  checked, like uploads). Clears the legacy quiet-hour mirror so "off" sticks.
- `/files` JSON includes each file's `assetKey`; release builds allow `?action=show` for
  uploaded (`local:`) assets only.
- `/icon.png` for the home-screen icon.

## Build

```bash
git clone https://github.com/aphex3k/EO1 && cd EO1 && git checkout 4508656
git apply /path/to/0001-mobile-web-page.patch
```

Add to `local.properties`: `sdk.dir=...` and the `eo1.signing.*` keys pointing at your
keystore (keep using the same keystore so `update-eo2.sh` can update in place), then:

```bash
./gradlew assembleRelease
```
