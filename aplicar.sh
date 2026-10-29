#!/bin/bash
# Uso: ./aplicar.sh fase1-desactivar.txt
set -u
TV="${TV:-192.168.0.122:5555}"
adb connect "$TV" >/dev/null 2>&1
adb -s "$TV" get-state >/dev/null 2>&1 || { echo "No conecta con la TV ($TV)."; exit 1; }
grep -vE '^\s*(#|$)' "$1" | while read -r p; do
  printf '%-45s ' "$p"; adb -s "$TV" shell "pm disable-user --user 0 $p" </dev/null | tail -1
done
echo "Espera ~15 s antes de reiniciar para que se guarden los cambios."
