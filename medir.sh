#!/bin/bash
# Mide a Inicio en la TV: volver a Home tras matarlo, fluidez del dock, memoria y CPU en reposo.
# Lo único que cambia es reiniciar Inicio para medir el arranque; al final Inicio queda al frente.
# La TV tiene que estar encendida y nadie usando el control mientras mide (unos 3 min).
#
# Uso:  ./medir.sh [ip-de-la-tv]          VECES=5 MUESTRAS=5 INTERVALO=30 ./medir.sh
set -u
TV="${1:-${TV:-192.168.0.122}}"
[[ "$TV" == *:* ]] || TV="$TV:5555"
INICIO=dev.robertovillegas.inicio
VECES="${VECES:-5}"            # veces que se mide volver a Home
MUESTRAS="${MUESTRAS:-5}"      # muestras de memoria, como en docs/tcl-sala.md
INTERVALO="${INTERVALO:-30}"   # segundos entre muestras

leer() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }
promedio() { awk '{ s += $1; n++ } END { if (n) printf "%.0f", s / n }'; }

adb connect "$TV" >/dev/null 2>&1
adb -s "$TV" get-state >/dev/null 2>&1 || { echo "No conecta con la TV ($TV)."; exit 1; }
leer dumpsys power | grep -q 'mWakefulness=Awake' || { echo "La TV está apagada o en reposo; enciéndela y vuelve a correr."; exit 1; }
version=$(leer dumpsys package $INICIO | grep -m1 versionName | cut -d= -f2)
[ -n "$version" ] || { echo "Inicio no está instalado en $TV."; exit 1; }
echo "Inicio $version en $(leer getprop ro.product.manufacturer) $(leer getprop ro.product.model)"
echo

# Volver a Home después de que Android mató a Inicio: con Ajustes al frente se mata el proceso y se
# presiona Home. No es un arranque en frío: el sistema revive el proceso en un par de segundos para
# reconectar a Escucha, así que lo que se mide es crear la pantalla desde cero (WARM).
tiempos=()
for _ in $(seq "$VECES"); do
  leer am start -a android.settings.SETTINGS >/dev/null
  sleep 4
  leer am force-stop $INICIO
  sleep 2
  t=$(leer am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $INICIO/.HomeActivity | awk -F': ' '/TotalTime/ { print $2 }')
  tiempos+=("$t")
  sleep 3
done
echo "Home tras matarlo:  $(printf '%s\n' "${tiempos[@]}" | promedio) ms  (${tiempos[*]})"

# Fluidez: recorre el dock de ida y vuelta, como alguien buscando una app, y cuenta los cuadros tardados.
sleep 8
leer dumpsys gfxinfo $INICIO reset >/dev/null
leer 'for i in 1 2 3 4 5 6 7 8 9 10 11 12; do input keyevent KEYCODE_DPAD_RIGHT; sleep 0.4; done;
      for i in 1 2 3 4 5 6 7 8 9 10 11 12; do input keyevent KEYCODE_DPAD_LEFT; sleep 0.4; done'
sleep 2
gfx=$(leer dumpsys gfxinfo $INICIO | sed -n '/^Stats since/,/^HISTOGRAM/p')
cuadros=$(awk -F': ' '/^Total frames rendered/ { print $2 }' <<<"$gfx")
tardados=$(awk -F': ' '/^Janky frames:/ { print $2 }' <<<"$gfx")
p50=$(awk -F': ' '/^50th percentile/ { print $2 }' <<<"$gfx")
p90=$(awk -F': ' '/^90th percentile/ { print $2 }' <<<"$gfx")
p99=$(awk -F': ' '/^99th percentile/ { print $2 }' <<<"$gfx")
echo "Dock, 24 teclas:    $cuadros cuadros, $tardados tardados; percentil 50 $p50, 90 $p90, 99 $p99"

# Reposo: memoria (PSS) en varias muestras y la CPU que usó Inicio en ese mismo tiempo.
# La CPU sale de /proc/<pid>/stat (utime + stime, en centésimas de segundo) al principio y al final.
pid=$(leer pidof $INICIO)
ticks() { leer cat /proc/"$pid"/stat | awk '{ print $14 + $15 }'; }
inicio_ticks=$(ticks)
inicio_seg=$(date +%s)
pss=()
for i in $(seq "$MUESTRAS"); do
  [ "$i" -gt 1 ] && sleep "$INTERVALO"
  pss+=("$(leer dumpsys meminfo $INICIO | awk '/TOTAL PSS:/ { print int($3 / 1024) }')")
done
fin_ticks=$(ticks)
segundos=$(( $(date +%s) - inicio_seg ))
cpu=$(awk -v d=$(( fin_ticks - inicio_ticks )) -v s="$segundos" 'BEGIN { printf "%.1f", s ? d / s : 0 }')
echo "Memoria en reposo:  $(printf '%s\n' "${pss[@]}" | promedio) MB PSS  (${pss[*]})"
echo "CPU en reposo:      $cpu % de un núcleo durante $segundos s"
