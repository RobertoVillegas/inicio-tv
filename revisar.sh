#!/bin/bash
# Revisa que la TV siga como la dejaron los scripts. Sirve sobre todo después de una actualización
# de firmware, que vuelve a activar lo desactivado y le regresa el botón Home a Google TV.
# Revisa: lo de fase1-desactivar.txt y lo que apagó poner-inicio.sh, que Inicio sea la pantalla de
# inicio, y sus permisos. Con ARREGLAR=1 regresa lo que encuentre fuera de lugar.
#
# Uso:  ./revisar.sh [ip-de-la-tv]          ARREGLAR=1 ./revisar.sh
# Sale con 0 si todo está en orden (o quedó arreglado), 1 si algo está fuera de lugar, 2 si no conecta.
set -u
cd "$(dirname "$0")"
TV="${1:-${TV:-192.168.0.122}}"
[[ "$TV" == *:* ]] || TV="$TV:5555"
INICIO=dev.robertovillegas.inicio
REGISTRO=/sdcard/inicio-launchers.txt
INTOCABLES="$INICIO com.android.tv.settings com.android.settings android"

leer() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }
mal=0
aviso() { echo "  ✗ $*"; mal=1; }
bien() { echo "  ✓ $*"; }

adb connect "$TV" >/dev/null 2>&1
adb -s "$TV" get-state >/dev/null 2>&1 || { echo "No conecta con la TV ($TV). ¿Aceptaste la depuración en la pantalla?"; exit 2; }
leer pm path "$INICIO" | grep -q package: || { echo "Inicio no está instalado en $TV: corre ./instalar-inicio.sh"; exit 1; }
echo "TV: $(leer getprop ro.product.manufacturer) $(leer getprop ro.product.model), firmware $(leer getprop ro.build.id), parche $(leer getprop ro.build.version.security_patch)"

echo "Paquetes desactivados"
desactivados=$(leer pm list packages -d | cut -d: -f2)
instalados=$(leer pm list packages | cut -d: -f2)
lista=$({ grep -vE '^\s*(#|$)' fase1-desactivar.txt; leer cat "$REGISTRO" 2>/dev/null | grep -v -e '^anterior=' -e 'No such file'; } | sort -u)
volvieron=$(comm -12 <(sort -u <<<"$instalados") <(comm -23 <(echo "$lista") <(sort -u <<<"$desactivados")))
if [ -n "$volvieron" ]; then
  aviso "se volvieron a activar: $(echo $volvieron)"
else
  bien "los $(grep -c . <<<"$lista") de la lista siguen apagados (o ya no existen)"
fi

echo "Pantalla de inicio"
home=$(leer cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)
otras=$(leer cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME |
  grep -E '^ +[A-Za-z0-9_.]+/' | tr -d ' ' | cut -d/ -f1 | sort -u | grep -vxF -f <(tr ' ' '\n' <<<"$INTOCABLES"))
if [[ "$home" == "$INICIO/"* ]]; then bien "Home abre Inicio"; else aviso "Home abre $home"; fi
[ -z "$otras" ] || aviso "hay otras pantallas de inicio activas: $(echo $otras)"

echo "Permisos de Inicio"
permisos_mal=0
leer settings get secure enabled_notification_listeners | grep -q "$INICIO" && bien "lee lo que está sonando" ||
  { aviso "no puede leer lo que está sonando (ni vigilar el botón Home)"; permisos_mal=1; }
leer dumpsys package "$INICIO" | grep -q 'WRITE_SECURE_SETTINGS: granted=true' && bien "ajusta el apagado de noche" ||
  { aviso "no puede ajustar el apagado de noche"; permisos_mal=1; }
leer settings get secure screensaver_components | grep -q "$INICIO" && bien "Paisajes es el protector de pantalla" ||
  { aviso "el protector de pantalla ya no es Paisajes"; permisos_mal=1; }

# Solo informativo: lo que no existía cuando se respaldó la TV de fábrica. Son apps que instalaste o que
# trajo una actualización; si alguna es basura nueva, agrégala a fase1-desactivar.txt.
nuevos=$(comm -13 respaldo-original/paquetes-incluye-desinstalados.txt <(leer pm list packages -u | cut -d: -f2 | sort -u))
if [ -n "$nuevos" ]; then
  echo "Paquetes que no estaban en el respaldo de fábrica:"
  sed 's/^/    /' <<<"$nuevos"
fi

[ "$mal" = 0 ] && { echo "Todo en orden."; exit 0; }
[ "${ARREGLAR:-0}" = 1 ] || { echo "Para arreglarlo:  ARREGLAR=1 ./revisar.sh ${1:-}"; exit 1; }

echo
echo "Arreglando…"
for p in $volvieron; do
  printf '  %-45s ' "$p"; leer pm disable-user --user 0 "$p" | tail -1
done
[ "$permisos_mal" = 0 ] || SIN_APK=1 ./instalar-inicio.sh "$TV"
if [[ "$home" != "$INICIO/"* ]] || [ -n "$otras" ]; then
  ./poner-inicio.sh "$TV" </dev/null
else
  echo "Listo. Espera unos 15 s antes de reiniciar la TV para que Android guarde los cambios."
fi
