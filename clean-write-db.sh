#!/bin/bash
user="postgres"
password="postgres"
dbhost="localhost:5432"
cmd="flywayClean"
dbs=("catalog_write_side")

migrate() {
for i in "${dbs[@]}"; do
  echo "Starting Migration of DB $i"
  sbt -Dflyway.user="${user}" \
 -Dflyway.url="jdbc:postgresql://${dbhost}/$i" \
 -Dflyway.password="${password}" \
 -Dflyway.locations="filesystem:sql/$i" \
  "${cmd}"
done
}

display_help() {
    echo '***********USAGE*********'
    echo "Usage: $0 <DBuser> <DBPassword> <DBIP:HOST>"
    echo "Flyway SQL scripts are expected under sql/<db> path"
    exit 1
}


if ! [ -x "$(command -v sbt)" ]; then
  echo '***********ERROR*********'
  echo 'Error: sbt is not installed.' >&2
  exit 1
fi

if [ "$1" != ""  -a "$2" != "" -a "$3" != "" ]; then
    user="$1"
    password="$2"
    dbhost="$3"
    if [ "$4" != "" ]; then
      cmd="$4"
    fi
    migrate
else
    display_help
fi
