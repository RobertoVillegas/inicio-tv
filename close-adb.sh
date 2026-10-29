#!/bin/bash
# Turns the TV's debugging off when you're done and confirms port 5555 is closed. Network ADB isn't
# encrypted, and while it's on any device in the house can ask to connect: one absent-minded "Allow" on
# screen is enough. Inicio doesn't need it: the permissions install-inicio.sh granted stay, and it installs
# its own updates from GitHub. To use the scripts again, turn debugging back on as in step 1 of the README.
#
# Usage:  ./close-adb.sh [tv-ip]        CHECK_ONLY=1 ./close-adb.sh   just say whether the port is open
# Exits 0 when the port ended up closed and 1 when it's still open.
set -u
IP="${1:-${TV:-192.168.0.122}}"
IP="${IP%:5555}"
TV="$IP:5555"

port_open() { nc -z -G 3 "$IP" 5555 >/dev/null 2>&1; }

if [ "${CHECK_ONLY:-0}" = 1 ]; then
  if port_open; then echo "Port 5555 on $IP is open."; exit 1; fi
  echo "Port 5555 on $IP is closed."
  exit 0
fi

if port_open; then
  adb connect "$TV" >/dev/null 2>&1
  if adb -s "$TV" get-state >/dev/null 2>&1; then
    echo "Turning debugging off on the TV…"
    # Turning adb_enabled off stops adbd, and this very connection with it.
    adb -s "$TV" shell 'settings put global adb_wifi_enabled 0; settings put global adb_enabled 0' </dev/null >/dev/null 2>&1
  fi
  adb disconnect "$TV" >/dev/null 2>&1
fi

for _ in 1 2 3 4 5; do
  port_open || {
    echo "Done: port 5555 on $IP is closed."
    echo "Optional, on the TV: Developer options → Revoke USB debugging authorizations."
    exit 0
  }
  sleep 2
done
echo "Port 5555 on $IP is still open. Turn it off on the TV: Developer options → USB debugging."
exit 1
