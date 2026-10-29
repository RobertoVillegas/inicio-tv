#!/bin/bash
# Installs or updates SmartTube (YouTube without an account, for guests) from its official GitHub.
# The TV is armeabi-v7a (32-bit): it needs that APK, not the arm64 one.
set -eu
TV="${TV:-192.168.0.122:5555}"
tmp=$(mktemp -d)
tag=$(gh api repos/yuliskov/SmartTube/releases --jq '[.[] | select(.prerelease==false)][0].tag_name')
gh release download "$tag" -R yuliskov/SmartTube -p 'SmartTube_stable_*_armeabi-v7a.apk' -D "$tmp"
adb connect "$TV" >/dev/null 2>&1
adb -s "$TV" install -r "$tmp"/*.apk
echo "SmartTube $tag installed."
