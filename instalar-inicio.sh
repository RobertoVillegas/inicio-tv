#!/bin/bash
# Instala Inicio (el último release de GitHub, o compilado aquí con COMPILAR=1), le da sus
# permisos y la deja como protector de pantalla con tiempos que cuidan el panel.
# No la pone como pantalla de inicio: eso lo hace poner-inicio.sh.
#
# Uso:  ./instalar-inicio.sh [ip-de-la-tv]        COMPILAR=1 ./instalar-inicio.sh  para compilar desde el código
set -eu
TV="${1:-${TV:-192.168.0.122}}"
[[ "$TV" == *:* ]] || TV="$TV:5555"
if [ "${COMPILAR:-0}" = 1 ]; then
  (cd "$(dirname "$0")/inicio" && ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}" ./gradlew assembleRelease -q)
  apk="$(dirname "$0")/inicio/app/build/outputs/apk/release/app-release.apk"
else
  apk="$(mktemp -d)/inicio.apk"
  curl -fsSL -o "$apk" https://github.com/RobertoVillegas/inicio-tv/releases/latest/download/inicio.apk
fi
adb connect "$TV" >/dev/null 2>&1
adb -s "$TV" get-state >/dev/null 2>&1 || { echo "No conecta con la TV ($TV). ¿Aceptaste la depuración en la pantalla?"; exit 1; }
adb -s "$TV" install -r "$apk"
adb -s "$TV" shell '
# TCL bloquea que las apps de terceros arranquen servicios solas; sin esto el protector no abre.
# En otras marcas ese permiso no existe y el comando solo avisa.
cmd appops set dev.robertovillegas.inicio APP_AUTO_START allow 2>/dev/null
# Leer la musica que esta sonando (Spotify, SmartTube) para mostrarla en el inicio y el protector.
cmd notification allow_listener dev.robertovillegas.inicio/dev.robertovillegas.inicio.Escucha
# Cambiar el apagado: 10 min de noche, ~19 min de dia.
pm grant dev.robertovillegas.inicio android.permission.WRITE_SECURE_SETTINGS
# Instalar sus propias versiones nuevas desde GitHub (la TV pide confirmar con el control).
cmd appops set dev.robertovillegas.inicio REQUEST_INSTALL_PACKAGES allow
settings put secure screensaver_enabled 1
settings put secure screensaver_components dev.robertovillegas.inicio/.PaisajesDream
settings put secure screensaver_default_component dev.robertovillegas.inicio/.PaisajesDream
settings put system screen_off_timeout 300000     # protector a los 5 min sin usar el control
settings put secure sleep_timeout 1139000         # pantalla apagada a los ~19 min de dia; Inicio la baja a 10 de noche
settings put secure attentive_timeout 14400000    # apagado tras 4 h sin tocar el control, aunque haya video
'
echo "Inicio instalado y configurado como protector de pantalla."
