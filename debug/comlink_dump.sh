#!/usr/bin/env bash
# Replays the backend's Comlink calls in order and saves raw responses.
set -euo pipefail
cd "$(dirname "$0")/.."
D=debug/comlink
mkdir -p "$D"
ALLY=${1:-191483497}

call() { curl -s -X POST "http://localhost:3000/$1" -H 'Content-Type: application/json' -d "$2" | jq . ; }

call player "{\"payload\":{\"allyCode\":\"$ALLY\"},\"enums\":false}" > "$D/1_player_by_allycode.json"
GID=$(jq -r .guildId "$D/1_player_by_allycode.json")
call guild "{\"payload\":{\"guildId\":\"$GID\",\"includeRecentGuildActivityInfo\":false},\"enums\":false}" > "$D/2_guild.json"
PID=$(jq -r '.guild.member[1].playerId' "$D/2_guild.json")
call player "{\"payload\":{\"playerId\":\"$PID\"},\"enums\":false}" > "$D/3_player_by_id_member.json"
ls -la "$D"
