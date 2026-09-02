#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
compose_project="${ROUTEPLAN_SAMPLE_COMPOSE_PROJECT:-routeplan}"
compose_file="${ROUTEPLAN_SAMPLE_COMPOSE_FILE:-$repo_dir/compose.yaml}"
sql_file="$repo_dir/scripts/sample-data/global-routes.sql"

[[ -f "$sql_file" ]] || { echo "Global sample SQL file was not found: $sql_file" >&2; exit 1; }
postgres_container="$(docker compose -p "$compose_project" -f "$compose_file" ps -q --status running postgres)"
[[ -n "$postgres_container" ]] || { echo "The PostgreSQL Compose service is not running for project '$compose_project'." >&2; exit 1; }
docker compose -p "$compose_project" -f "$compose_file" exec -T postgres sh -c \
  'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "$sql_file"

echo 'Global sample import completed: 40 routes, 160 route items.'
