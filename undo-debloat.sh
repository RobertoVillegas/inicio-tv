#!/bin/bash
# From the Mac: puts the TV back as it came from the factory (which had no disabled apps).
TV="${TV:-192.168.0.122:5555}"
adb connect "$TV" >/dev/null 2>&1
adb -s "$TV" push "$(dirname "$0")/undo-debloat-on-tv.sh" /sdcard/undo-debloat-on-tv.sh >/dev/null
adb -s "$TV" shell sh /sdcard/undo-debloat-on-tv.sh
sleep 15
adb -s "$TV" reboot
