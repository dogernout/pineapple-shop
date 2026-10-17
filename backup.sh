#!/bin/sh
set -eu

DIR=/backups
KEEP="${BACKUP_KEEP:-7}"

while true; do
  # если свежий бэкап (моложе 22 часов) уже есть, лишний не делаем
  if find "$DIR" -name 'daily-*.dump' -mmin -1320 | grep -q .; then
    echo "$(date '+%F %T') already has fresh backup, skipping"
  else
    FILE="$DIR/daily-$(date +%F_%H-%M).dump"
    if pg_dump -Fc -f "$FILE.tmp"; then
      mv "$FILE.tmp" "$FILE"
      echo "$(date '+%F %T') created $FILE"
      # оставляем только KEEP самых новых
      ls -1 "$DIR"/daily-*.dump | sort -r | tail -n +"$((KEEP + 1))" | xargs -r rm --
    else
      echo "$(date '+%F %T') ERROR backup"
      rm -f "$FILE.tmp"
    fi
  fi
  sleep 86400
done
