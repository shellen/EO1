# EO2 setup — aphex3k/EO1, no keyboard needed

Files in this kit:
- `eo1-aphex3k-2.0.1.apk` — built from github.com/aphex3k/EO1 @ 4508656 (debug-signed)
  SHA-256 `2f8d7c64c5aed39029858aa78de1f1aa21d186f26f1882ecde70a8731bd23212`
- `configuration.json` — uploads-only config: 5-minute interval, screen off 00:00–06:59 Pacific

The EO2 ships with ADB (Android's debug tool) listening on Wi-Fi port 5555, with root, from boot.
So everything below is done from your computer over Wi-Fi. No OTG, keyboard or mouse needed.

## 0. Before you start

- The frame must be on your Wi-Fi and you need its IP address (check your router's client list;
  set a DHCP reservation so it never changes). If it isn't on Wi-Fi, that is the one time you
  need the keyboard: join Wi-Fi in Android Settings.
- Install adb on your computer:
  - Mac: `brew install android-platform-tools`
  - Windows: use WSL (Ubuntu) and `sudo apt install adb`. The aphex3k author found native Windows adb fails to connect.
- Edit `configuration.json` if you want: `selectedTimeZoneId` (e.g. `America/New_York`),
  `quietHours` (cron format: `* 0-6 * * *` = off midnight–6:59; `[]` = never off), `interval` (minutes).

## 1. Connect

    adb connect FRAME_IP:5555
    adb devices            # should list FRAME_IP:5555  device

## 2. Remove every other home-screen app (this fixes "Select a home app")

    adb shell pm list packages | grep -i -E "electric|eo1|geniatech"

You'll likely see `com.electricobjects.app`, `com.geniatech.upgrade`, and whatever APK you
flashed before (e.g. `com.dan.eo1`). Then:

    adb shell pm disable com.electricobjects.app
    adb shell pm disable com.geniatech.upgrade
    adb uninstall com.dan.eo1          # use the package name of the APK you flashed earlier

With only one app claiming to be the home screen, Android has nothing to ask about, so the
chooser can't come back after reboots or updates.

## 3. Install

    adb install -r eo1-aphex3k-2.0.1.apk

## 4. Configure without touching the frame

Launch it once so its data folder exists, then push the config:

    adb shell am start -n com.aphex3k.eo1/.MainActivity
    adb push configuration.json /data/data/com.aphex3k.eo1/files/configuration.json
    adb shell chmod 666 /data/data/com.aphex3k.eo1/files/configuration.json
    adb reboot

## 5. Use it from your phone

On any phone/laptop on the same Wi-Fi, open **http://FRAME_IP:8080/**
(bookmark it / "Add to Home Screen"). From that page:
- **Upload**: pick up to 32 photos at a time. They show up within one interval (or press **Next**).
- **Delete**, **Next**, **Screen** on/off, **Brightness** (the EO2 has no light sensor, so this is the only brightness control).

Photo tips: the panel is portrait 1080×1920 and photos are **cropped to fill**, so 9:16 portrait
shots look best. JPEG/PNG work; iPhone **HEIC does not**. Set iPhone to
Settings → Camera → Formats → *Most Compatible*, or share as JPEG. Video: H.264 MP4, ≤1080p30, plays muted.

## If something goes wrong

- Chooser shows up anyway: run step 2's `list packages` again. Another home-capable app is still enabled.
- Photo never appears: look in the page's Files table for `<name>.incompat.txt`. It says why.
- Page won't load: try `http://FRAME_IP/` (port 80), and confirm the IP hasn't changed.

## Security note

ADB on port 5555 is open as root to anything on your network. That's how the EO2 ships, not
something this app adds. Fine on a home network you trust; don't put the frame on public/guest Wi-Fi.
