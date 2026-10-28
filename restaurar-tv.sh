#!/system/bin/sh
# Deja la TV como venia: reactiva todo lo desactivado, regresa el inicio de Google TV,
# su protector de pantalla y los tiempos de apagado originales.
# En la TV:  adb shell sh /sdcard/restaurar-tv.sh
for p in $(pm list packages -d | sed 's/package://'); do pm enable "$p"; done
pm enable com.google.android.apps.tv.launcherx
cmd role add-role-holder --user 0 android.app.role.HOME com.google.android.apps.tv.launcherx 2>/dev/null
cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity
settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop
settings put secure screensaver_default_component com.google.android.apps.tv.dreamx/.service.Backdrop
settings put system screen_off_timeout 600000
settings put secure sleep_timeout 1139000
settings put secure attentive_timeout -1
echo "Listo. Espera 15 s y reinicia la TV. Las apps instaladas (Inicio, SmartTube) siguen ahi;"
echo "quitalas desde Ajustes > Apps si ya no las quieres."
