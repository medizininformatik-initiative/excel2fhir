#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
compose=(docker compose -f compose.yml --profile '*')
state=.bulk-services
"${compose[@]}" exec -T api python -c 'import bulk_resources; bulk_resources.require_idle()'
running=$("${compose[@]}" ps --status running --services)
services=()
while IFS= read -r service; do
  case "$service" in api|worker|web|'') ;; *) services+=("$service");; esac
done <<< "$running"
# Persist the exact set before stopping, including an earlier preparation.
if ((${#services[@]})); then
  { cat "$state" 2>/dev/null || true; printf '%s\n' "${services[@]}"; } | sort -u > "$state.tmp"
  mv "$state.tmp" "$state"
  "${compose[@]}" stop "${services[@]}"
fi
remaining=$("${compose[@]}" ps --status running --services | sed '/^api$/d; /^worker$/d; /^web$/d; /^$/d')
if [[ -n "$remaining" ]]; then
  printf 'Optional services are still running:\n%s\n' "$remaining" >&2
  exit 1
fi
"${compose[@]}" exec -T worker python -c 'import bulk_resources; bulk_resources.require_available(container=True)'
printf 'Bulk generation is ready. Start the run in the interface.\nAfter completion: bash web/restore-services.sh (from the project directory).\n'
