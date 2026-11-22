# Patches on top of aphex3k/EO1

`0001-mobile-web-page.patch` — against aphex3k/EO1 @ 4508656:

- `/` is a new phone-first page (`app/src/main/resources/web/mobile.html`): add photos
  (resized and converted on the phone to 1080×1920 JPEG, so HEIC works; "Fill frame" or
  "Show whole photo" with blurred edges), gallery with tap-to-preview, **Show now**, **Remove**,
  multi-select remove, GIF badges (GIFs upload untouched and animate on the frame), Next / Screen / Brightness, and simple settings: picture interval, night
  sleep hours, time zone.
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
