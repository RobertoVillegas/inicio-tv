#!/bin/bash
# Regresa la pantalla de inicio de fábrica (Google TV o Android TV) y le devuelve el botón Home.
# Sirve de emergencia o simplemente para dejar de usar Inicio. Reactiva lo que anotó
# poner-inicio.sh en la TV; si no hay registro, prueba con las de Google conocidas.
#
# Uso:  ./rescate-launcher.sh [ip-de-la-tv]      SIMULAR=1 ./rescate-launcher.sh  solo muestra qué haría
set -u
TV="${1:-${TV:-192.168.0.122}}"
[[ "$TV" == *:* ]] || TV="$TV:5555"
REGISTRO=/sdcard/inicio-launchers.txt
# En orden de preferencia: Google TV, Android TV, Android TV viejo, y la app de configuración de Google TV.
CONOCIDAS="com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher com.google.android.tungsten.setupwraith"

leer() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }
hacer() { if [ "${SIMULAR:-0}" = 1 ]; then echo "  (simulado) $*"; else adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; fi; }

adb connect "$TV" >/dev/null 2>&1
adb -s "$TV" get-state >/dev/null 2>&1 || { echo "No conecta con la TV ($TV)."; exit 1; }

registro=$(leer cat "$REGISTRO" 2>/dev/null | grep -v 'No such file')
paquetes=$({ grep -v '^anterior=' <<<"$registro"; tr ' ' '\n' <<<"$CONOCIDAS"; } | grep . | sort -u)
for p in $paquetes; do
  leer pm path "$p" | grep -q package: || continue
  echo "Reactivando $p"
  hacer pm enable "$p" >/dev/null
done

anterior=$(grep '^anterior=' <<<"$registro" | cut -d= -f2)
if [ -z "$anterior" ]; then
  # Sin registro: la primera pantalla de inicio conocida que exista en esta TV.
  for p in $CONOCIDAS; do
    [ "$p" = com.google.android.tungsten.setupwraith ] && continue
    leer pm path "$p" | grep -q package: || continue
    anterior=$(leer cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME |
      tr -d ' ' | grep -m1 "^$p/") && break
  done
fi
if [ -z "$anterior" ] && [ "${SIMULAR:-0}" = 1 ]; then
  anterior="(la que aparezca después de reactivar)"
fi
[ -n "$anterior" ] || { echo "No encontré la pantalla de inicio de fábrica en esta TV."; exit 1; }

echo "Pantalla de inicio: $anterior"
hacer cmd package set-home-activity "$anterior"
hacer cmd role add-role-holder --user 0 android.app.role.HOME "${anterior%%/*}"
hacer rm -f "$REGISTRO"
[ "${SIMULAR:-0}" = 1 ] && exit 0

echo "Esperando 15 s a que Android guarde el cambio y reiniciando la TV…"
sleep 15
adb -s "$TV" reboot
echo "Listo. Inicio sigue instalado; puedes abrirlo como app o quitarlo desde Ajustes > Apps."
