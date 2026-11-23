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
- **Live music**: `MusicService` keeps the frame's own Spotify connection (the phone hands over the
  refresh token via `POST /music/connect`), polls now playing every 6-15 s and recent plays every
  2 min, and serves `GET /music/state`; album art is proxied and cached (`/music/img`). `/music/`
  is the live page (Kinetic Type, Liquid Light, Album Wall; `?lead=artist|track|date`), added to the rotation
  as the `music-1` web entry. With take-over on, the music page is pinned on every rotation tick
  while something plays.
- **Modern TLS for Spotify**: Android 4.4 only offers CBC cipher suites, which
  accounts.spotify.com refuses (handshake failure), so `MusicService` runs its connections through a
  bundled Conscrypt provider (`org.conscrypt:conscrypt-android:2.5.3`, ~1.3 MB armv7) capped at
  TLS 1.2 with AES-GCM, validated against the app's merged root bundle. Only the Spotify client
  uses it; the rest of the app's TLS is unchanged.
- **Storage cleanup**: `GeneratedFilesJanitor` keeps the newest file of each generated kind,
  removes orphaned notes/thumbnails and stale upload temp folders, and caps the art cache; it runs
  at start, after uploads and hourly. Uploads that would leave under 150 MB free are refused.
- **Up next**: the rotation worker publishes a read-only snapshot of the next ~24 assets after
  each tick (`MediaManager.upcomingAssets()`); `GET /queue` returns what's on now, what's next
  (with kind: photo, kept art, NYT, Spotify, live art, live music), the interval, the next change
  time and the music take-over status. The phone page shows it as a carousel with times, and the
  library as one horizontal row per source.
- **Update from the phone**: `POST /update/apk` (same-origin only, no Trusted Network token)
  stages an uploaded APK through `UpdateManager.stageUploadedApk`, which only accepts APKs signed by
  the installed app's certificate with a higher versionCode; the page then asks the frame to install
  it (`install-staged`, which on an EO2 goes through the frame's own adbd). Build with increasing
  `-PVERSION_CODE` (e.g. 2.0.7 → 20007).
- **Layout**: four tabs (Now, Library, Extras, Frame) with a flat, typographic style (system
  serif headings, monospace labels, hairlines, one accent). Each extra is one row with its own
  switch and expands for options. Frame → Maintenance has Update, Restart the app
  (`POST /control?action=restart-app`) and Reboot (`POST /control?action=reboot`, via the frame's
  adbd), both using the watchdog's existing routines.
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
./gradlew assembleRelease -PVERSION_NAME=2.0.7 -PVERSION_CODE=20007
```
