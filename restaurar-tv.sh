#!/system/bin/sh
# Deja la TV como venia: reactiva todo lo desactivado, regresa la pantalla de inicio de fábrica,
# su protector de pantalla y los tiempos de apagado originales.
# En la TV:  adb shell sh /sdcard/restaurar-tv.sh
for p in $(pm list packages -d | sed 's/package://'); do pm enable "$p"; done
# La pantalla de inicio de fábrica: la que anotó poner-inicio.sh, o la de Google TV / Android TV que exista.
anterior=$(grep '^anterior=' /sdcard/inicio-launchers.txt 2>/dev/null | cut -d= -f2)
if [ -z "$anterior" ]; then
  for p in com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher; do
    anterior=$(cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d ' ' | grep -m1 "^$p/") && break
  done
fi
if [ -n "$anterior" ]; then
  cmd role add-role-holder --user 0 android.app.role.HOME "${anterior%%/*}" 2>/dev/null
  cmd package set-home-activity "$anterior"
fi
rm -f /sdcard/inicio-launchers.txt
settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop
settings put secure screensaver_default_component com.google.android.apps.tv.dreamx/.service.Backdrop
settings put system screen_off_timeout 600000
settings put secure sleep_timeout 1139000
settings put secure attentive_timeout -1
echo "Listo. Espera 15 s y reinicia la TV. Las apps instaladas (Inicio, SmartTube) siguen ahi;"
echo "quitalas desde Ajustes > Apps si ya no las quieres."
