#!/system/bin/sh
# Puts the TV back as it came: re-enables everything disabled and restores the factory home screen, its
# screensaver and the original sleep timers.
# On the TV:  adb shell sh /sdcard/undo-debloat-on-tv.sh
for p in $(pm list packages -d | sed 's/package://'); do pm enable "$p"; done
previous=$(grep '^previous=' /sdcard/inicio-launchers.txt 2>/dev/null | cut -d= -f2)
if [ -z "$previous" ]; then
  for p in com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher; do
    previous=$(cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d ' ' | grep -m1 "^$p/") && break
  done
fi
if [ -n "$previous" ]; then
  cmd role add-role-holder --user 0 android.app.role.HOME "${previous%%/*}" 2>/dev/null
  cmd package set-home-activity "$previous"
fi
rm -f /sdcard/inicio-launchers.txt
settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop
settings put secure screensaver_default_component com.google.android.apps.tv.dreamx/.service.Backdrop
settings put system screen_off_timeout 600000
settings put secure sleep_timeout 1139000
settings put secure attentive_timeout -1
echo "Done. Wait 15 s and reboot the TV. Installed apps (Inicio, SmartTube) are still there;"
echo "remove them in Settings > Apps if you no longer want them."
