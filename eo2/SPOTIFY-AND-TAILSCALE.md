# Spotify on the frame, and reaching the frame over Tailscale

## Spotify (Extras → Spotify on the phone page)

Three styles, all made on your phone and sent to the frame:

- **Album grid**: your last 9, 12 or 20 different albums, with the date and the latest album.
- **Animated**: the same grid as a GIF where one cover at a time fades to an older album.
- **Now playing**: a poster of the current track (big cover on a blurred backdrop, title, artist,
  album). It goes on screen right away. Turn on **Follow along while this page is open** and it
  updates every 30 seconds as tracks change.

Grids replace the previous grid and posters replace the previous poster, so one of each at most is
in rotation. **Refresh whenever I open this page** keeps the grid current (at most every 3 hours).

### One-time setup (about 5 minutes)

1. **Turn on GitHub Pages for this repo** (so Spotify has an `https://` page to return to):
   GitHub → shellen/EO1 → Settings → Pages → *Deploy from a branch* → branch
   `claude/friendly-albattani-80t6uk` (or `main` once merged), folder `/ (root)` → Save.
   After a minute, `https://shellen.github.io/EO1/eo2/spotify/callback.html` should load. This
   page only passes Spotify's one-time code back to the frame's address on your network. The
   code is useless without a secret your phone keeps, and the page refuses to forward anywhere
   but private-network or tailnet addresses.
   *Skip this step if you only ever open the frame page through Tailscale Serve (below): an
   `https://…ts.net` page is its own return address.*
2. **Create a Spotify app**: <https://developer.spotify.com/dashboard> → Create app → any name and
   description, tick **Web API**. Add the **Redirect URI** the phone page shows you (copy button).
   If you use both the home address and Tailscale Serve, add both URIs.
3. Paste the app's **Client ID** into the phone page and tap **Connect**. Approve on Spotify, and
   you land back on the frame page, connected.

If Spotify answers "refused", open your app in the dashboard → **User Management** and add your
own Spotify account's email (apps in development mode only work for listed users).

The connection is stored on that phone's browser, per address: if you open the page from both
`http://192.168.1.50:8080` and a Tailscale address, connect once on each.

## Tailscale: the phone page from anywhere

The frame itself can't run Tailscale (Android 4.4 is far below what the Tailscale app needs), so
another always-on device at home does the job. Two ways; either works with everything on the page.

### A. Subnet router (simplest)

Run Tailscale on something that's always on at home (a Mac that stays awake, an Apple TV, a
Raspberry Pi), and let it route your home network:

- **Mac** (Terminal): `tailscale set --advertise-routes=192.168.1.0/24`
  (with the Mac app, the command is `/Applications/Tailscale.app/Contents/MacOS/Tailscale` if
  `tailscale` isn't on your PATH)
- **Apple TV**: Tailscale app → *Subnet router* → advertise `192.168.1.0/24`

Then approve the route in the Tailscale admin console (Machines → that device → *Edit route
settings*). The Tailscale app on your iPhone uses approved routes automatically, so
`http://192.168.1.50:8080` works wherever you are, with Tailscale on.

### B. Tailscale Serve (adds https)

On an always-on Mac with Tailscale:

```bash
tailscale serve --bg http://192.168.1.50:8080
```

This publishes the frame page **only inside your tailnet** at
`https://<mac-name>.<your-tailnet>.ts.net/` (HTTPS certificates must be enabled for the tailnet:
admin console → DNS → *Enable HTTPS*). Benefits: a real https address for your home-screen icon,
and the Spotify login returns straight to it, so GitHub Pages isn't needed. Stop it with
`tailscale serve reset`.

Don't use **Funnel**: it would put the frame's page, which has no login, on the public internet.
