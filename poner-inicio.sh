#!/bin/bash
# Hace de Inicio la pantalla de inicio y desactiva la de Google TV (fase 2).
# Si algo sale mal: ./rescate-launcher.sh
set -u
TV="${TV:-192.168.0.122:5555}"
adb connect "$TV" >/dev/null 2>&1
adb -s "$TV" shell "cmd role add-role-holder --user 0 android.app.role.HOME dev.robertovillegas.inicio"
adb -s "$TV" shell "cmd package set-home-activity dev.robertovillegas.inicio/.HomeActivity"
"$(dirname "$0")/aplicar.sh" "$(dirname "$0")/fase2-desactivar.txt"
