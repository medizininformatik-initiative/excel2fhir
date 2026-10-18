#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
compose=(docker compose -f compose.yml --profile '*')
state=.bulk-services
if [[ ! -s "$state" ]]; then
  printf 'No paused services recorded.\n'
  exit 0
fi
"${compose[@]}" exec -T api python -c 'import bulk_resources; bulk_resources.require_idle()'
services=()
while IFS= read -r service; do
  [[ "$service" =~ ^[a-zA-Z0-9][a-zA-Z0-9_-]*$ ]] || { printf 'Invalid service name in %s\n' "$state" >&2; exit 1; }
  services+=("$service")
done < "$state"
present=$("${compose[@]}" ps --all --services "${services[@]}" | sort -u)
expected=$(printf '%s\n' "${services[@]}" | sort -u)
[[ "$present" == "$expected" ]] || { printf 'Some recorded service containers are missing.\n' >&2; exit 1; }
ids=$("${compose[@]}" ps --all --quiet "${services[@]}")
[[ -n "$ids" ]] || { printf 'Recorded service containers are missing.\n' >&2; exit 1; }
containers=()
while IFS= read -r id; do containers+=("$id"); done <<< "$ids"
# Compose start also starts dependencies; docker start preserves the selected set.
docker start "${containers[@]}"
rm "$state"
printf 'Previously running services started.\n'
