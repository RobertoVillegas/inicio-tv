#!/bin/bash
# Measures Inicio on the TV: returning Home after it was killed, dock smoothness, idle memory and CPU.
# It only restarts Inicio; at the end Inicio is in front. The TV must be on, with nobody using the remote
# while it measures (about 3 minutes).
#
# Usage:  ./measure.sh [tv-ip]          RUNS=5 SAMPLES=5 INTERVAL=30 ./measure.sh
set -u
TV="${1:-${TV:-192.168.0.122}}"
[[ "$TV" == *:* ]] || TV="$TV:5555"
INICIO=dev.robertovillegas.inicio
RUNS="${RUNS:-5}"              # returns to Home measured
SAMPLES="${SAMPLES:-5}"        # memory samples, as in docs/tcl-living-room.md
INTERVAL="${INTERVAL:-30}"     # seconds between samples

read_tv() { adb -s "$TV" shell "$@" </dev/null 2>/dev/null | tr -d '\r'; }
average() { awk '{ s += $1; n++ } END { if (n) printf "%.0f", s / n }'; }

adb connect "$TV" >/dev/null 2>&1
adb -s "$TV" get-state >/dev/null 2>&1 || { echo "Can't reach the TV ($TV)."; exit 1; }
read_tv dumpsys power | grep -q 'mWakefulness=Awake' || { echo "The TV is off or asleep; turn it on and run again."; exit 1; }
version=$(read_tv dumpsys package $INICIO | grep -m1 versionName | cut -d= -f2)
[ -n "$version" ] || { echo "Inicio isn't installed on $TV."; exit 1; }
echo "Inicio $version on $(read_tv getprop ro.product.manufacturer) $(read_tv getprop ro.product.model)"
echo

# Not a true cold start: the system revives the process within a couple of seconds to reconnect
# MediaListener, so what this measures is building the home screen from scratch (a WARM launch).
times=()
for _ in $(seq "$RUNS"); do
  read_tv am start -a android.settings.SETTINGS >/dev/null
  sleep 4
  read_tv am force-stop $INICIO
  sleep 2
  t=$(read_tv am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $INICIO/.HomeActivity | awk -F': ' '/TotalTime/ { print $2 }')
  times+=("$t")
  sleep 3
done
echo "Home after a kill:  $(printf '%s\n' "${times[@]}" | average) ms  (${times[*]})"

# Smoothness: across the dock and back, like someone looking for an app.
sleep 8
read_tv dumpsys gfxinfo $INICIO reset >/dev/null
read_tv 'for i in 1 2 3 4 5 6 7 8 9 10 11 12; do input keyevent KEYCODE_DPAD_RIGHT; sleep 0.4; done;
         for i in 1 2 3 4 5 6 7 8 9 10 11 12; do input keyevent KEYCODE_DPAD_LEFT; sleep 0.4; done'
sleep 2
gfx=$(read_tv dumpsys gfxinfo $INICIO | sed -n '/^Stats since/,/^HISTOGRAM/p')
field() { awk -F': ' -v k="$1" '$1 == k { print $2 }' <<<"$gfx"; }
echo "Dock, 24 keys:      $(field 'Total frames rendered') frames, $(field 'Janky frames') janky;" \
  "p50 $(field '50th percentile'), p90 $(field '90th percentile'), p99 $(field '99th percentile')"

# Idle: memory (PSS) over several samples, and the CPU Inicio used over the same time,
# from utime + stime in /proc/<pid>/stat (hundredths of a second).
pid=$(read_tv pidof $INICIO)
ticks() { read_tv cat /proc/"$pid"/stat | awk '{ print $14 + $15 }'; }
start_ticks=$(ticks)
start_s=$(date +%s)
pss=()
for i in $(seq "$SAMPLES"); do
  [ "$i" -gt 1 ] && sleep "$INTERVAL"
  pss+=("$(read_tv dumpsys meminfo $INICIO | awk '/TOTAL PSS:/ { print int($3 / 1024) }')")
done
end_ticks=$(ticks)
seconds=$(( $(date +%s) - start_s ))
cpu=$(awk -v d=$(( end_ticks - start_ticks )) -v s="$seconds" 'BEGIN { printf "%.1f", s ? d / s : 0 }')
echo "Idle memory:        $(printf '%s\n' "${pss[@]}" | average) MB PSS  (${pss[*]})"
echo "Idle CPU:           $cpu % of one core over $seconds s"
