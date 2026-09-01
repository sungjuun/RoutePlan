#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
backup_file="${1:-}"
confirmation="${2:-}"
env_file="${ROUTEPLAN_PRODUCTION_ENV_FILE:-$repo_dir/.env.production}"
release_file="${ROUTEPLAN_RELEASE_FILE:-$repo_dir/.routeplan-release.env}"
compose_project="${ROUTEPLAN_COMPOSE_PROJECT_NAME:-routeplan-production}"

if [[ -z "$backup_file" || "$confirmation" != 'REPLACE_ROUTEPLAN_DATABASE' ]]; then
  echo 'Usage: ./scripts/restore-production.sh <backup.dump> REPLACE_ROUTEPLAN_DATABASE' >&2
  exit 2
fi
[[ -f "$backup_file" && -s "$backup_file" ]] || { echo 'Backup file does not exist or is empty.' >&2; exit 1; }
backup_file="$(cd "$(dirname "$backup_file")" && pwd -P)/$(basename "$backup_file")"

bash "$repo_dir/scripts/validate-production-env.sh" "$env_file" >/dev/null
[[ -f "$release_file" ]] || { echo 'Release state is missing.' >&2; exit 1; }

if [[ -f "$backup_file.sha256" ]]; then
  (cd "$(dirname "$backup_file")" && sha256sum -c "$(basename "$backup_file").sha256")
fi

compose=(docker compose -p "$compose_project" --env-file "$env_file" --env-file "$release_file" -f "$repo_dir/compose.production.yaml")
"${compose[@]}" up -d postgres redis
running_services="$("${compose[@]}" ps --status running --services)"
restart_services=()
for service in backend frontend caddy; do
  if grep -qx "$service" <<<"$running_services"; then restart_services+=("$service"); fi
done
current_backup_output="$(ROUTEPLAN_PRODUCTION_ENV_FILE="$env_file" ROUTEPLAN_RELEASE_FILE="$release_file" \
  ROUTEPLAN_COMPOSE_PROJECT_NAME="$compose_project" bash "$repo_dir/scripts/backup-production.sh")"
current_backup="${current_backup_output#BACKUP_PATH=}"

if (( ${#restart_services[@]} > 0 )); then
  "${compose[@]}" stop "${restart_services[@]}"
fi
echo "Restoring PostgreSQL. Safety backup: $current_backup"
set +e
# Variables expand inside the PostgreSQL container.
# shellcheck disable=SC2016
"${compose[@]}" exec -T postgres sh -c \
  'PGPASSWORD="$POSTGRES_PASSWORD" exec pg_restore -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --no-privileges' \
  < "$backup_file"
restore_status=$?
set -e

if (( restore_status != 0 )); then
  echo 'Restore failed. Application containers remain stopped; inspect PostgreSQL before restarting.' >&2
  echo "Pre-restore safety backup: $current_backup" >&2
  exit "$restore_status"
fi

if (( ${#restart_services[@]} > 0 )); then
  "${compose[@]}" up -d "${restart_services[@]}"
  for service in "${restart_services[@]}"; do
    deadline=$((SECONDS + 180))
    while (( SECONDS < deadline )); do
      container_id="$("${compose[@]}" ps -q "$service")"
      status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id" 2>/dev/null || true)"
      [[ "$status" == 'healthy' ]] && break
      [[ "$status" == 'exited' || "$status" == 'dead' ]] && break
      sleep 3
    done
    if [[ "$status" != 'healthy' ]]; then
      "${compose[@]}" stop "${restart_services[@]}" || true
      echo "Restore data completed, but $service did not become healthy. Application services were stopped." >&2
      echo "Pre-restore safety backup: $current_backup" >&2
      exit 1
    fi
  done
fi
echo "Restore completed from $backup_file"
