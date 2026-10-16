#!/bin/bash
# icondb.sh <out.tsv> : component<TAB>md5 of Neo's cached icon blob, for profile 0.
# icondb.sh cmp <baseline.tsv> <run.tsv> : components whose icon equals the baseline (stock).
S=/tmp/claude-1000/-mnt-storage-Claude/d094e88f-8b92-417d-a5bb-1312d124139a/scratchpad
if [ "$1" = cmp ]; then
  join -t $'\t' <(sort "$2") <(sort "$3") | awk -F'\t' '{ if ($2==$3) s=s" "$1; else t++ } END { print "stock:" s; print "themed: " t+0 }'
  exit
fi
adb shell "sqlite3 /data/data/com.saggitt.omega/databases/app_icons.db \"select componentName, hex(icon) from icons where profileId=0;\"" \
  | tr -d '\r' | while IFS='|' read -r c h; do printf '%s\t%s\n' "$c" "$(printf %s "$h" | md5sum | cut -c1-12)"; done > "$1"
wc -l < "$1"
