#!/bin/bash
# Desde la Mac: deja la TV como venia de fabrica (original: 0 apps desactivadas).
TV="${TV:-192.168.0.122:5555}"
adb connect "$TV" >/dev/null 2>&1
adb -s "$TV" push "$(dirname "$0")/restaurar-tv.sh" /sdcard/restaurar-tv.sh >/dev/null
adb -s "$TV" shell sh /sdcard/restaurar-tv.sh
sleep 15
adb -s "$TV" reboot
