#!/bin/bash
# Makes Inicio the home screen on Google TV or Android TV. Disables the other home screens it finds and
# records them on the TV, so restore-launcher.sh can bring them back exactly.
#
# Usage:  ./set-home.sh [tv-ip]          DRY_RUN=1 ./set-home.sh   only show what it would do
# If something goes wrong:  ./restore-launcher.sh [tv-ip]
set -u
TV="${1:-${TV:-192.168.0.122}}"
[[ "$TV" == *:* ]] || TV="$TV:5555"
INICIO=dev.robertovillegas.inicio
RECORD=/sdcard/inicio-launchers.txt
# Never touched: Inicio, Settings' fallback home and Android's chooser.
UNTOUCHABLE="$INICIO com.android.tv.settings com.android.settings android"

read_tv() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }
run() { if [ "${DRY_RUN:-0}" = 1 ]; then echo "  (dry run) $*"; else adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; fi; }
home_screens() {
  read_tv cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME |
    grep -E '^ +[A-Za-z0-9_.]+/' | tr -d ' '
}

adb connect "$TV" >/dev/null 2>&1
adb -s "$TV" get-state >/dev/null 2>&1 || { echo "Can't reach the TV ($TV). Did you accept debugging on screen?"; exit 1; }
read_tv pm path "$INICIO" | grep -q package: || { echo "Inicio isn't installed on $TV. Run ./install-inicio.sh first."; exit 1; }
if read_tv getprop ro.product.manufacturer | grep -qi amazon; then
  echo "This is a Fire TV: Amazon doesn't let anything replace its home screen. Inicio runs as a regular app."
  exit 1
fi

previous=$(read_tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)
others=$(home_screens | cut -d/ -f1 | sort -u | grep -vxF -f <(tr ' ' '\n' <<<"$UNTOUCHABLE"))

echo "TV: $(read_tv getprop ro.product.manufacturer) $(read_tv getprop ro.product.model), Android $(read_tv getprop ro.build.version.release)"
echo "Current home screen: $previous"
if [ -n "$others" ]; then
  echo "To be disabled (restore-launcher.sh brings them back):"
  sed 's/^/  /' <<<"$others"
fi
if [ "${DRY_RUN:-0}" != 1 ] && [ -t 0 ]; then
  read -r -p "Continue? [y/N] " ok
  [[ "$ok" =~ ^[yY] ]] || exit 0
fi

# The original home screen is recorded only once, so Inicio never ends up as "previous".
if [[ "$previous" != "$INICIO/"* ]]; then
  run "[ -f $RECORD ] || echo 'previous=$previous' > $RECORD"
fi

run cmd role add-role-holder --user 0 android.app.role.HOME "$INICIO"
run cmd package set-home-activity "$INICIO/.HomeActivity"
for p in $others; do
  printf '%-45s ' "$p"
  run "pm disable-user --user 0 $p && echo $p >> $RECORD" | tail -1
done

[ "${DRY_RUN:-0}" = 1 ] && exit 0
echo
echo "Home screens left:"
home_screens | sed 's/^/  /'
echo "Done. Wait about 15 s before rebooting the TV so Android saves the change."
