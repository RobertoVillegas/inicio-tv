#!/bin/bash
# Hace de Inicio la pantalla de inicio en Google TV o Android TV.
# Desactiva las demás pantallas de inicio que encuentre y anota en la TV cuáles eran,
# para que rescate-launcher.sh las regrese tal cual.
#
# Uso:  ./poner-inicio.sh [ip-de-la-tv]          SIMULAR=1 ./poner-inicio.sh  solo muestra qué haría
# Si algo sale mal:  ./rescate-launcher.sh [ip-de-la-tv]
set -u
TV="${1:-${TV:-192.168.0.122}}"
[[ "$TV" == *:* ]] || TV="$TV:5555"
INICIO=dev.robertovillegas.inicio
REGISTRO=/sdcard/inicio-launchers.txt
# Nunca se tocan: Inicio, la pantalla de respaldo de Ajustes y el selector de Android.
INTOCABLES="$INICIO com.android.tv.settings com.android.settings android"

leer() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }
hacer() { if [ "${SIMULAR:-0}" = 1 ]; then echo "  (simulado) $*"; else adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; fi; }
pantallas() {
  leer cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME |
    grep -E '^ +[A-Za-z0-9_.]+/' | tr -d ' '
}

adb connect "$TV" >/dev/null 2>&1
adb -s "$TV" get-state >/dev/null 2>&1 || { echo "No conecta con la TV ($TV). ¿Aceptaste la depuración en la pantalla?"; exit 1; }
leer pm path "$INICIO" | grep -q package: || { echo "Inicio no está instalado en $TV. Instálalo primero."; exit 1; }
if leer getprop ro.product.manufacturer | grep -qi amazon; then
  echo "Esta es una Fire TV: Amazon no deja cambiar la pantalla de inicio. Inicio funciona como app normal."
  exit 1
fi

anterior=$(leer cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)
otras=$(pantallas | cut -d/ -f1 | sort -u | grep -vxF -f <(tr ' ' '\n' <<<"$INTOCABLES"))

echo "TV: $(leer getprop ro.product.manufacturer) $(leer getprop ro.product.model), Android $(leer getprop ro.build.version.release)"
echo "Pantalla de inicio actual: $anterior"
if [ -n "$otras" ]; then
  echo "Se van a desactivar (se pueden regresar con rescate-launcher.sh):"
  sed 's/^/  /' <<<"$otras"
fi
if [ "${SIMULAR:-0}" != 1 ] && [ -t 0 ]; then
  read -r -p "¿Seguir? [s/N] " ok
  [[ "$ok" =~ ^[sSyY] ]] || exit 0
fi

# El registro guarda la pantalla original solo la primera vez, para no anotar a Inicio como "anterior".
if [[ "$anterior" != "$INICIO/"* ]]; then
  hacer "[ -f $REGISTRO ] || echo 'anterior=$anterior' > $REGISTRO"
fi

hacer cmd role add-role-holder --user 0 android.app.role.HOME "$INICIO"
hacer cmd package set-home-activity "$INICIO/.HomeActivity"
for p in $otras; do
  printf '%-45s ' "$p"
  hacer "pm disable-user --user 0 $p && echo $p >> $REGISTRO" | tail -1
done

[ "${SIMULAR:-0}" = 1 ] && exit 0
echo
echo "Pantallas de inicio que quedan:"
pantallas | sed 's/^/  /'
echo "Listo. Espera unos 15 s antes de reiniciar la TV para que Android guarde el cambio."
