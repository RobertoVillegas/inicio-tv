#!/bin/bash
# Instala/actualiza SmartTube (YouTube sin cuenta para invitados) desde su GitHub oficial.
# La TV es armeabi-v7a (32 bits): hay que bajar ese APK, no el arm64.
set -eu
TV="${TV:-192.168.0.122:5555}"
tmp=$(mktemp -d)
tag=$(gh api repos/yuliskov/SmartTube/releases --jq '[.[] | select(.prerelease==false)][0].tag_name')
gh release download "$tag" -R yuliskov/SmartTube -p 'SmartTube_stable_*_armeabi-v7a.apk' -D "$tmp"
adb connect "$TV" >/dev/null 2>&1
adb -s "$TV" install -r "$tmp"/*.apk
echo "SmartTube $tag instalado."
