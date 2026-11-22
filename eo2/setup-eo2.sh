#!/bin/bash
# setup-eo2.sh — set up an Electric Objects EO2 with the aphex3k/EO1 app, over Wi-Fi.
#
# Usage: put this script and the APK (e.g. eo1-aphex3k-2.0.1.apk) in one folder, then:
#     cd that-folder
#     bash setup-eo2.sh
#
# What it does (it asks before anything that changes the frame):
#   1. makes sure `adb` is installed (offers to install it with Homebrew)
#   2. finds your EO2 on the network (or you type its IP)
#   3. disables the old Electric Objects app + updater and removes other replacement apps,
#      so the "Select a home app" screen stops appearing
#   4. installs the APK, writes a settings file, reboots, and opens the upload page
#
# Written for macOS's built-in bash 3.2. Safe to re-run.

set -u

APP_PKG="com.aphex3k.eo1"
CONFIG_REMOTE="/data/data/${APP_PKG}/files/configuration.json"
STOCK_PKGS="com.electricobjects.app com.electricobjects.updater com.electricobjects.test com.geniatech.upgrade"
OLD_REPLACEMENTS="com.dan.eo1 com.spalt.eo1"   # earlier community APKs that also claim HOME

HERE="$(cd "$(dirname "$0")" && pwd)"
# Pick the APK: if there is more than one, list them and let the user choose (newest first).
pick_apk() {
    local list n i choice
    list="$(ls -t "$HERE"/*.apk 2>/dev/null)"
    n="$(printf '%s\n' "$list" | grep -c . || true)"
    [ "$n" -gt 0 ] 2>/dev/null || return 1
    if [ "$n" -eq 1 ]; then printf '%s\n' "$list"; return 0; fi
    echo "  Found $n APK files (newest first):" >&2
    i=0; while IFS= read -r f; do i=$((i+1)); printf '    %d) %s\n' "$i" "$(basename "$f")" >&2; done <<< "$list"
    printf '  Which one? [1]: ' >&2; read -r choice </dev/tty
    case "${choice:-1}" in *[!0-9]*) choice=1;; esac
    [ "${choice:-1}" -ge 1 ] && [ "${choice:-1}" -le "$n" ] || choice=1
    printf '%s\n' "$list" | sed -n "${choice:-1}p"
}
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

bold()  { printf '\033[1m%s\033[0m\n' "$*"; }
ok()    { printf '  \033[32m✓\033[0m %s\n' "$*"; }
warn()  { printf '  \033[33m!\033[0m %s\n' "$*"; }
die()   { printf '\n\033[31m✗ %s\033[0m\n' "$*"; exit 1; }
ask()   { # ask "question" default -> echoes answer
    local a; printf '%s [%s]: ' "$1" "$2" >&2; read -r a </dev/tty; echo "${a:-$2}"; }
yesno() { # yesno "question" (default yes)
    local a; printf '%s [Y/n]: ' "$1" >&2; read -r a </dev/tty
    case "$a" in [nN]*) return 1;; *) return 0;; esac; }

echo
bold "EO2 setup — aphex3k/EO1"
echo "Keep the frame plugged in and on your Wi-Fi. This takes about 5 minutes."
echo

# ---------------------------------------------------------------- 1. adb
bold "Step 1/6 — Checking for adb"
if ! command -v adb >/dev/null 2>&1; then
    if command -v brew >/dev/null 2>&1; then
        yesno "adb isn't installed. Install it now with Homebrew?" || die "adb is required."
        brew install android-platform-tools || die "Homebrew couldn't install adb."
    else
        die "adb isn't installed and Homebrew wasn't found.
  Install Homebrew from https://brew.sh, then run this script again."
    fi
fi
ok "adb found: $(command -v adb)"

# ---------------------------------------------------------------- 2. APK
bold "Step 2/6 — Finding the APK"
APK="$(pick_apk)"
[ -n "$APK" ] || die "No .apk file next to this script (in $HERE).
  Put eo1-aphex3k-2.0.1.apk in this folder and run again."
ok "Using $(basename "$APK")"

# ---------------------------------------------------------------- 3. find the frame
bold "Step 3/6 — Finding your EO2 on the network"
scan() {
    local myip prefix i
    myip="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)"
    [ -n "$myip" ] || return 0
    prefix="${myip%.*}"
    echo "  Scanning ${prefix}.1–254 for adb (port 5555)…" >&2
    for i in $(seq 1 254); do
        ( nc -z -G 1 -w 1 "${prefix}.${i}" 5555 >/dev/null 2>&1 && echo "${prefix}.${i}" ) &
    done
    wait
}
FOUND="$(scan | sort -t. -k4 -n)"
COUNT="$(printf '%s' "$FOUND" | grep -c . || true)"
if [ "$COUNT" = "1" ]; then
    ok "Found a device at $FOUND"
    IP="$(ask "Frame IP address" "$FOUND")"
elif [ "$COUNT" -gt 1 ] 2>/dev/null; then
    warn "Found several devices with adb open:"; echo "$FOUND" | sed 's/^/      /'
    IP="$(ask "Which one is the frame?" "$(echo "$FOUND" | head -1)")"
else
    warn "Couldn't find it automatically. Look up the frame's IP in your router's device list."
    IP="$(ask "Frame IP address" "")"
fi
[ -n "$IP" ] || die "No IP address given."
TARGET="${IP}:5555"

adb disconnect "$TARGET" >/dev/null 2>&1
adb connect "$TARGET" >"$WORK/c" 2>&1
grep -qi "connected" "$WORK/c" || die "Couldn't connect to $TARGET: $(cat "$WORK/c")
  Check the IP and that the frame is powered on."
A() { adb -s "$TARGET" "$@"; }
A wait-for-device
MODEL="$(A shell getprop ro.product.model | tr -d '\r')"
ANDROID="$(A shell getprop ro.build.version.release | tr -d '\r')"
ok "Connected: model '$MODEL', Android $ANDROID"
yesno "Is this your Electric Objects frame?" || die "Stopped — nothing was changed."

# ---------------------------------------------------------------- 4. clear other home apps
bold "Step 4/6 — Removing other home-screen apps (fixes 'Select a home app')"
PKGS="$(A shell pm list packages | tr -d '\r' | sed 's/^package://; s/.*=//')"
has_pkg() { echo "$PKGS" | grep -qx "$1"; }

for p in $STOCK_PKGS; do
    if has_pkg "$p"; then
        A shell pm disable "$p" >/dev/null 2>&1 && ok "Disabled $p" || warn "Couldn't disable $p"
    fi
done
# anything else that looks like an EO replacement app
OTHERS="$( (for p in $OLD_REPLACEMENTS; do echo "$p"; done; echo "$PKGS" | grep -i -E 'eo1|electric') \
          | sort -u | grep -vx "$APP_PKG" | grep -v -e '^com\.electricobjects\.' -e '^com\.geniatech\.' || true)"
for p in $OTHERS; do
    has_pkg "$p" || continue
    if yesno "  Found another frame app '$p'. Uninstall it?"; then
        A uninstall "$p" 2>&1 | grep -q Success && ok "Uninstalled $p" || warn "Couldn't uninstall $p"
    fi
done

# ---------------------------------------------------------------- 5. settings
bold "Step 5/6 — Settings"
TZ_DEFAULT="$(readlink /etc/localtime 2>/dev/null | sed 's#.*/zoneinfo/##')"
TZ_ID="$(ask "Time zone" "${TZ_DEFAULT:-America/Los_Angeles}")"
INTERVAL="$(ask "Minutes per picture" "5")"
case "$INTERVAL" in ''|*[!0-9]*) INTERVAL=5;; esac
[ "$INTERVAL" -ge 1 ] || INTERVAL=1

QUIET='[]'
if yesno "Turn the screen off at night?"; then
    QS="$(ask "  Screen off at hour (0-23)" "23")"
    QE="$(ask "  Screen back on at hour (0-23)" "7")"
    case "$QS$QE" in *[!0-9]*) die "Hours must be numbers 0-23.";; esac
    { [ "$QS" -le 23 ] && [ "$QE" -le 23 ] && [ "$QS" -ne "$QE" ]; } || die "Hours must be 0-23 and different."
    if [ "$QS" -lt "$QE" ]; then HOURS="${QS}-$((QE-1))"
    elif [ "$QE" -eq 0 ]; then HOURS="${QS}-23"
    else HOURS="${QS}-23,0-$((QE-1))"; fi
    HOURS="$(echo "$HOURS" | sed -E 's/(^|,)([0-9]+)-\2(,|$)/\1\2\3/g')"   # "23-23" -> "23"
    QUIET="[\"* ${HOURS} * * *\"]"
fi

cat > "$WORK/configuration.json" <<EOF
{
    "selectedTimeZoneId": "${TZ_ID}",
    "quietHours": ${QUIET},
    "interval": ${INTERVAL},
    "updateManifestUrl": "",
    "updateCheckIntervalMinutes": 120,
    "backends": [
        { "type": "local", "id": "local" }
    ]
}
EOF
ok "Settings: ${TZ_ID}, every ${INTERVAL} min, quiet hours ${QUIET}"

# ---------------------------------------------------------------- 6. install
bold "Step 6/6 — Installing"
yesno "Install the app on the frame and reboot it now?" || die "Stopped before installing."
A install -r "$APK" >"$WORK/i" 2>&1
grep -q "Success" "$WORK/i" || die "Install failed: $(cat "$WORK/i")"
ok "App installed"

A shell am start -n "${APP_PKG}/.MainActivity" >/dev/null 2>&1
sleep 5
A shell mkdir -p "/data/data/${APP_PKG}/files" >/dev/null 2>&1
A push "$WORK/configuration.json" "$CONFIG_REMOTE" >/dev/null 2>&1 || die "Couldn't copy the settings file to the frame."
A shell chmod 666 "$CONFIG_REMOTE" >/dev/null 2>&1
ok "Settings copied"

# Final check: only our app should be left as a home screen.
LEFT="$(A shell pm list packages -e | tr -d '\r' | sed 's/^package://; s/.*=//' | grep -x -e com.electricobjects.app -e com.dan.eo1 || true)"
[ -z "$LEFT" ] || warn "Still enabled: $LEFT — the home-app chooser may still appear."

A reboot >/dev/null 2>&1
echo "  Rebooting the frame — waiting for the upload page (up to 3 minutes)…"
URL=""
for i in $(seq 1 36); do
    sleep 5
    for port in 8080 80; do
        if curl -fs -m 3 -o /dev/null "http://${IP}:${port}/"; then
            [ "$port" = "80" ] && URL="http://${IP}/" || URL="http://${IP}:8080/"
            break 2
        fi
    done
done

echo
if [ -n "$URL" ]; then
    bold "Done! The frame is running."
    echo "  Upload page: $URL"
    echo "  On your phone (same Wi-Fi), open that address and choose 'Add to Home Screen'."
    open "$URL" 2>/dev/null || true
else
    bold "Installed, but the upload page hasn't answered yet."
    echo "  Give it a minute, then open http://${IP}:8080/ in a browser."
fi
echo "  Tip: iPhone HEIC photos won't display — set Camera → Formats → Most Compatible."
echo
