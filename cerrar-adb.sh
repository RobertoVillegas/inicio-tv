#!/bin/bash
# Apaga la depuración de la TV al terminar y confirma que el puerto 5555 quedó cerrado.
# El ADB por red no va cifrado y, mientras está encendido, cualquier aparato de la casa puede pedir
# entrar: basta un "Permitir" distraído en la pantalla. Inicio no lo necesita: los permisos que le dio
# instalar-inicio.sh se quedan y las versiones nuevas las instala él mismo desde GitHub.
# Para volver a usar los scripts, enciende la depuración en la TV como en el paso 1 del README.
#
# Uso:  ./cerrar-adb.sh [ip-de-la-tv]        SOLO_REVISAR=1 ./cerrar-adb.sh  dice si el puerto está abierto
# Sale con 0 si el puerto quedó cerrado y 1 si sigue abierto.
set -u
IP="${1:-${TV:-192.168.0.122}}"
IP="${IP%:5555}"
TV="$IP:5555"

abierto() { nc -z -G 3 "$IP" 5555 >/dev/null 2>&1; }

if [ "${SOLO_REVISAR:-0}" = 1 ]; then
  if abierto; then echo "El puerto 5555 de $IP está abierto."; exit 1; fi
  echo "El puerto 5555 de $IP está cerrado."
  exit 0
fi

if abierto; then
  adb connect "$TV" >/dev/null 2>&1
  if adb -s "$TV" get-state >/dev/null 2>&1; then
    echo "Apagando la depuración en la TV…"
    # Apagar adb_enabled detiene adbd y con él esta misma conexión.
    adb -s "$TV" shell 'settings put global adb_wifi_enabled 0; settings put global adb_enabled 0' </dev/null >/dev/null 2>&1
  fi
  adb disconnect "$TV" >/dev/null 2>&1
fi

for _ in 1 2 3 4 5; do
  abierto || {
    echo "Listo: el puerto 5555 de $IP está cerrado."
    echo "Opcional, en la TV: Opciones de desarrollador → Revocar autorizaciones de depuración USB."
    exit 0
  }
  sleep 2
done
echo "El puerto 5555 de $IP sigue abierto. Apágalo en la TV: Opciones de desarrollador → Depuración USB."
exit 1
