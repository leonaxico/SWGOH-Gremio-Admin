#!/usr/bin/env bash
# Probes Comlink /data one bit at a time to map each `items` bit to its collection.
set -euo pipefail
VER=$(curl -s -X POST http://localhost:3000/metadata -H 'Content-Type: application/json' -d '{"payload":{}}' | jq -r .latestGamedataVersion)
for k in $(seq 0 51); do
  mask=$(python3 -c "print(1<<$k)")
  out=$(curl -s -X POST http://localhost:3000/data -H 'Content-Type: application/json' \
    -d "{\"payload\":{\"version\":\"$VER\",\"includePveUnits\":false,\"items\":\"$mask\"},\"enums\":false}" \
    | jq -r '[to_entries[] | select((.value|type)=="array" and (.value|length)>0) | "\(.key)(\(.value|length))"] | join(" ")')
  echo "bit $k: $out"
done
