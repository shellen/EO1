#!/bin/bash
# install-daily.sh — put the NYT front page on the frame every day, from this Mac.
#
#   bash install-daily.sh 192.168.1.50 [gif|glitch|clean]
#
# Installs Python packages into ~/.eo2-nyt (a private virtualenv), then a launchd job that runs
# at 6:10, 9:10 and 12:10 local time. A run that finds today's page already on the frame does
# nothing, so the extra runs only matter on days the Times publishes the scan late.
# Remove with:  bash install-daily.sh --uninstall
set -eu
LABEL="com.eo2.nyt-frontpage"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
HOME_DIR="$HOME/.eo2-nyt"

if [ "${1:-}" = "--uninstall" ]; then
    launchctl unload "$PLIST" 2>/dev/null || true
    rm -f "$PLIST"; echo "Removed the daily job. (Python files remain in $HOME_DIR.)"; exit 0
fi
IP="${1:?usage: bash install-daily.sh FRAME_IP [gif|glitch|clean]}"
STYLE="${2:-gif}"
HERE="$(cd "$(dirname "$0")" && pwd)"

command -v python3 >/dev/null || { echo "python3 not found. Install it with: xcode-select --install"; exit 1; }
mkdir -p "$HOME_DIR"
[ -x "$HOME_DIR/venv/bin/python" ] || python3 -m venv "$HOME_DIR/venv"
"$HOME_DIR/venv/bin/pip" install -q --upgrade pymupdf pillow numpy
cp "$HERE/nyt-frontpage.py" "$HOME_DIR/"

echo "Trying it once now…"
"$HOME_DIR/venv/bin/python" "$HOME_DIR/nyt-frontpage.py" --style "$STYLE" --frame "$IP" --out "$HOME_DIR"

mkdir -p "$HOME/Library/LaunchAgents"
cat > "$PLIST" <<PL
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key><array>
    <string>$HOME_DIR/venv/bin/python</string><string>$HOME_DIR/nyt-frontpage.py</string>
    <string>--style</string><string>$STYLE</string>
    <string>--frame</string><string>$IP</string>
    <string>--out</string><string>$HOME_DIR</string>
  </array>
  <key>StartCalendarInterval</key><array>
    <dict><key>Hour</key><integer>6</integer><key>Minute</key><integer>10</integer></dict>
    <dict><key>Hour</key><integer>9</integer><key>Minute</key><integer>10</integer></dict>
    <dict><key>Hour</key><integer>12</integer><key>Minute</key><integer>10</integer></dict>
  </array>
  <key>StandardOutPath</key><string>$HOME_DIR/log.txt</string>
  <key>StandardErrorPath</key><string>$HOME_DIR/log.txt</string>
</dict></plist>
PL
launchctl unload "$PLIST" 2>/dev/null || true
launchctl load "$PLIST"
echo "Done. The front page will refresh daily (log: $HOME_DIR/log.txt)."
