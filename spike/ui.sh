#!/usr/bin/env bash
# ui.sh shot <name>          screenshot to shots/<name>.png
# ui.sh texts                list visible text/desc with centres
# ui.sh tap "<text regex>"   tap first node whose text or content-desc matches
set -e
cd "$(dirname "$0")"; mkdir -p shots
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml; }
nodes() {
  dump | python3 -c '
import sys, re, xml.etree.ElementTree as ET
r = ET.fromstring(sys.stdin.read())
for n in r.iter("node"):
    t = n.get("text") or n.get("content-desc") or ""
    if not t: continue
    x1,y1,x2,y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    print(f"{(x1+x2)//2} {(y1+y2)//2}\t{t}")'
}
case "$1" in
  shot) adb exec-out screencap -p > "shots/$2.png"; echo "shots/$2.png" ;;
  texts) nodes ;;
  tap) xy=$(nodes | awk -F'\t' -v p="$2" 'tolower($2) ~ tolower(p) {print $1; exit}') || true
       [ -n "$xy" ] || { echo "no match: $2" >&2; exit 3; }
       adb shell input tap $xy; echo "tapped $2 at $xy" ;;
esac
