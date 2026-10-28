#!/bin/bash
# EMERGENCIA: la TV se quedo sin pantalla de inicio. Reactiva el inicio de Google TV.
TV="${TV:-192.168.0.122:5555}"
adb connect "$TV" >/dev/null 2>&1
adb -s "$TV" shell "pm enable com.google.android.apps.tv.launcherx" </dev/null
adb -s "$TV" shell "pm enable com.google.android.tungsten.setupwraith" </dev/null
adb -s "$TV" shell "cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity" </dev/null
adb -s "$TV" shell "cmd role add-role-holder --user 0 android.app.role.HOME com.google.android.apps.tv.launcherx" </dev/null
sleep 15
adb -s "$TV" reboot
echo "Inicio de Google TV reactivado. La TV se esta reiniciando."
