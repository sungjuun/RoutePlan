#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
env_file="${ROUTEPLAN_PRODUCTION_ENV_FILE:-$repo_dir/.env.production}"
release_file="${ROUTEPLAN_RELEASE_FILE:-$repo_dir/.routeplan-release.env}"
backup_dir="$repo_dir/backups/postgres"

bash "$repo_dir/scripts/validate-production-env.sh" "$env_file" >/dev/null
[[ -f "$release_file" ]] || { echo 'Release state is missing; deploy once before backing up.' >&2; exit 1; }
deployment_environment="$(awk -F= '/^ROUTEPLAN_DEPLOYMENT_ENVIRONMENT=/{value=$2} END{print value}' "$env_file")"
compose_project="${ROUTEPLAN_COMPOSE_PROJECT_NAME:-routeplan-$deployment_environment}"

retention_days="$(awk -F= '/^ROUTEPLAN_BACKUP_RETENTION_DAYS=/{value=$2} END{print value}' "$env_file")"
retention_days="${retention_days:-14}"
[[ "$retention_days" =~ ^[0-9]+$ ]] || { echo 'ROUTEPLAN_BACKUP_RETENTION_DAYS must be a non-negative integer.' >&2; exit 1; }

compose=(docker compose -p "$compose_project" --env-file "$env_file" --env-file "$release_file" -f "$repo_dir/compose.production.yaml")
"${compose[@]}" ps --status running --services | grep -qx postgres || { echo 'RoutePlan PostgreSQL is not running.' >&2; exit 1; }

mkdir -p "$backup_dir"
backup_dir="$(cd "$backup_dir" && pwd -P)"
[[ "$backup_dir" == "$repo_dir/backups/postgres" ]] || { echo 'Refusing to use an unexpected backup directory.' >&2; exit 1; }
umask 077
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
temporary="$backup_dir/.routeplan-$timestamp.dump.tmp"
final="$backup_dir/routeplan-$timestamp.dump"
trap 'rm -f -- "$temporary"' EXIT

# Variables expand inside the PostgreSQL container.
# shellcheck disable=SC2016
"${compose[@]}" exec -T postgres sh -c \
  'PGPASSWORD="$POSTGRES_PASSWORD" exec pg_dump -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges' \
  > "$temporary"
[[ -s "$temporary" ]] || { echo 'PostgreSQL produced an empty backup.' >&2; exit 1; }
mv -- "$temporary" "$final"
(
  cd "$backup_dir"
  sha256sum "$(basename "$final")" > "$(basename "$final").sha256"
)

find "$backup_dir" -maxdepth 1 -type f \( -name 'routeplan-*.dump' -o -name 'routeplan-*.dump.sha256' \) \
  -mtime "+$retention_days" -delete

echo "BACKUP_PATH=$final"
