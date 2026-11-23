#!/bin/bash
# update-eo2.sh — install a newer build of the frame app, keeping your photos and settings.
#
# Usage (from the folder holding this script and the new APK):
#     bash update-eo2.sh                  # asks for the frame's IP
#     bash update-eo2.sh 192.168.1.50     # or pass it
#
# Uses the newest .apk in this folder. If the new build is signed with a different key than the
# one on the frame (e.g. moving from the first debug build to the signed one), Android refuses a
# normal update. The script then backs up your photos and settings to this folder, reinstalls,
# and puts them back.
#
# Written for macOS's built-in bash 3.2. Safe to re-run.

set -u
APP_PKG="com.aphex3k.eo1"
APP_DIR="/data/data/${APP_PKG}"
HERE="$(cd "$(dirname "$0")" && pwd)"
# Connect over Wi-Fi without ever blocking: a stale "offline" connection (the frame rebooted, or an
# earlier session left one behind) makes `adb wait-for-device` hang forever, so reconnect and poll.
frame_connect() {
    local t="$1" i st
    adb disconnect "$t" >/dev/null 2>&1
    for i in 1 2 3 4 5; do
        adb connect "$t" >/dev/null 2>&1
        st="$(adb -s "$t" get-state 2>/dev/null | tr -d '\r')"
        [ "$st" = "device" ] && return 0
        [ "$i" -eq 2 ] && adb kill-server >/dev/null 2>&1   # clears a wedged adb server
        sleep 3
    done
    return 1
}
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

ok()   { printf '  \033[32m✓\033[0m %s\n' "$*"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$*"; }
die()  { printf '\n\033[31m✗ %s\033[0m\n' "$*"; exit 1; }
yesno(){ local a; printf '%s [Y/n]: ' "$1" >&2; read -r a </dev/tty; case "$a" in [nN]*) return 1;; *) return 0;; esac; }

command -v adb >/dev/null 2>&1 || die "adb isn't installed. Run setup-eo2.sh first (or: brew install android-platform-tools)."

APK="$(pick_apk)"
[ -n "$APK" ] || die "No .apk file in $HERE."
ok "Using $(basename "$APK")"

IP="${1:-}"
if [ -z "$IP" ]; then printf 'Frame IP address: ' >&2; read -r IP </dev/tty; fi
[ -n "$IP" ] || die "No IP address given."
T="${IP}:5555"
echo "  Connecting to ${T}…"
frame_connect "$T" || die "Couldn't reach the frame at $T after ~20 seconds.
  Check it's powered on and on Wi-Fi (its IP may have changed), then try again."
A() { adb -s "$T" "$@"; }
# Always let go of the frame when this script ends, however it ends: the frame's own installer
# (used by the phone page's "Update the frame") cannot run while a computer stays attached.
trap 'adb disconnect "$T" >/dev/null 2>&1' EXIT
ok "Connected to $(A shell getprop ro.product.model | tr -d '\r') at $IP"

OUT="$(A install -r "$APK" 2>&1)"
if echo "$OUT" | grep -q "Success"; then
    ok "Updated in place — photos and settings untouched"
else
    echo "$OUT" | grep -qi "INCONSISTENT_CERTIFICATES\|UPDATE_INCOMPATIBLE\|signatures do not match" \
        || die "Install failed: $OUT"
    warn "The new build is signed differently from the one on the frame, so it has to be reinstalled."
    BK="$HERE/eo2-backup-$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$BK"
    A pull "$APP_DIR/files/configuration.json" "$BK/configuration.json" >/dev/null 2>&1 \
        && ok "Backed up settings" || warn "No settings file found to back up"
    if A shell ls "$APP_DIR/files/uploaded" 2>/dev/null | tr -d '\r' | grep -q .; then
        A pull "$APP_DIR/files/uploaded" "$BK/uploaded" >/dev/null 2>&1 || die "Couldn't back up photos — stopping before anything is removed."
        ok "Backed up $(ls "$BK/uploaded" | wc -l | tr -d ' ') photo file(s) to $(basename "$BK")"
    else
        ok "No uploaded photos to back up"
    fi
    yesno "Reinstall the app now? (backup is in $(basename "$BK"))" || die "Stopped. Nothing on the frame was changed."
    A uninstall "$APP_PKG" 2>&1 | grep -q Success || die "Couldn't remove the old app."
    A install "$APK" 2>&1 | grep -q Success || die "Install failed — your backup is in $BK. Re-run setup-eo2.sh to recover."
    ok "New app installed"
    A shell am start -n "${APP_PKG}/.MainActivity" >/dev/null 2>&1
    sleep 6
    OWNER="$(A shell ls -ld "$APP_DIR" | tr -d '\r' | awk '{print $2}')"
    A shell mkdir -p "$APP_DIR/files/uploaded" >/dev/null 2>&1
    [ -f "$BK/configuration.json" ] && A push "$BK/configuration.json" "$APP_DIR/files/configuration.json" >/dev/null 2>&1
    if [ -d "$BK/uploaded" ]; then
        for f in "$BK/uploaded"/*; do [ -f "$f" ] && A push "$f" "$APP_DIR/files/uploaded/" >/dev/null 2>&1; done
    fi
    # Give the files back to the app so it can read them and the phone page can remove them.
    if [ -n "$OWNER" ]; then
        A shell "chown $OWNER:$OWNER $APP_DIR/files $APP_DIR/files/uploaded $APP_DIR/files/configuration.json; for f in $APP_DIR/files/uploaded/*; do chown $OWNER:$OWNER \"\$f\"; done" >/dev/null 2>&1
    fi
    A shell "chmod 771 $APP_DIR/files/uploaded; chmod 660 $APP_DIR/files/configuration.json" >/dev/null 2>&1
    ok "Photos and settings restored"
fi

A reboot >/dev/null 2>&1
echo "  Rebooting the frame…"
for i in $(seq 1 36); do
    sleep 5
    if curl -fs -m 3 -o /dev/null "http://${IP}:8080/"; then
        adb disconnect "$T" >/dev/null 2>&1   # don't leave a stale session that can wedge the frame's adb
        printf '\n\033[1mDone!\033[0m Open http://%s:8080/ on your phone.\n\n' "$IP"
        open "http://${IP}:8080/" 2>/dev/null || true
        exit 0
    fi
done
adb disconnect "$T" >/dev/null 2>&1
printf '\nInstalled. The page hasn'"'"'t answered yet — try http://%s:8080/ in a minute.\n' "$IP"
