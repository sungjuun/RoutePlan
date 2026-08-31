#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
target_tag="${1:-}"
target_prefix="${2:-}"
env_file="$repo_dir/.env.production"
release_file="$repo_dir/.routeplan-release.env"

if [[ ! "$target_tag" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]]; then
  echo 'Usage: ./scripts/deploy-production.sh <image-tag> [ghcr-image-prefix]' >&2
  exit 2
fi

bash "$repo_dir/scripts/validate-production-env.sh" "$env_file"
configured_prefix="$(awk -F= '/^ROUTEPLAN_IMAGE_PREFIX=/{value=substr($0,index($0,"=")+1)} END{print value}' "$env_file")"
target_prefix="${target_prefix:-$configured_prefix}"
[[ "$target_prefix" =~ ^ghcr\.io/[a-z0-9._-]+/[a-z0-9._/-]+$ ]] || { echo 'Invalid GHCR image prefix.' >&2; exit 1; }

previous_tag=''
previous_prefix=''
had_previous_release='false'
if [[ -f "$release_file" ]]; then
  had_previous_release='true'
  previous_tag="$(awk -F= '/^ROUTEPLAN_IMAGE_TAG=/{print $2}' "$release_file")"
  previous_prefix="$(awk -F= '/^ROUTEPLAN_IMAGE_PREFIX=/{value=substr($0,index($0,"=")+1)} END{print value}' "$release_file")"
fi

write_release() {
  local prefix="$1" tag="$2"
  local temporary_release="$release_file.tmp"
  umask 077
  printf 'ROUTEPLAN_IMAGE_PREFIX=%s\nROUTEPLAN_IMAGE_TAG=%s\n' "$prefix" "$tag" > "$temporary_release"
  mv -- "$temporary_release" "$release_file"
}

restore_release_state() {
  if [[ "$had_previous_release" == 'true' ]]; then
    write_release "$previous_prefix" "$previous_tag"
  else
    rm -f -- "$release_file"
  fi
}

compose_command() {
  docker compose --env-file "$env_file" --env-file "$release_file" -f "$repo_dir/compose.production.yaml" "$@"
}

wait_healthy() {
  local service="$1" deadline=$((SECONDS + 180)) container_id status
  while (( SECONDS < deadline )); do
    container_id="$(compose_command ps -q "$service")"
    if [[ -n "$container_id" ]]; then
      status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id" 2>/dev/null || true)"
      [[ "$status" == 'healthy' ]] && return 0
      [[ "$status" == 'exited' || "$status" == 'dead' ]] && return 1
    fi
    sleep 3
  done
  return 1
}

public_health() {
  local domain
  domain="$(awk -F= '/^ROUTEPLAN_DOMAIN=/{value=$2} END{print value}' "$env_file")"
  curl --fail --silent --show-error --max-time 15 --retry 6 --retry-delay 3 --retry-all-errors \
    "https://$domain/api/v1/auth/me" >/dev/null
}

rollback_images() {
  if [[ -z "$previous_tag" || -z "$previous_prefix" ]]; then
    echo 'No previous image release is available for automatic application rollback.' >&2
    return 1
  fi
  echo "Health check failed; restoring application images tagged $previous_tag." >&2
  write_release "$previous_prefix" "$previous_tag"
  compose_command pull backend frontend
  compose_command up -d backend frontend caddy
  wait_healthy backend && wait_healthy frontend && wait_healthy caddy && public_health
}

deployment_finished='false'
on_unexpected_error() {
  local status=$?
  if [[ "$deployment_finished" != 'true' ]]; then
    restore_release_state
  fi
  exit "$status"
}
trap on_unexpected_error ERR

write_release "$target_prefix" "$target_tag"
compose_command config --quiet
compose_command pull postgres redis caddy backend frontend

predeploy_backup='none (first deployment)'
if compose_command ps --status running --services | grep -qx postgres; then
  backup_output="$(bash "$repo_dir/scripts/backup-production.sh")"
  predeploy_backup="${backup_output#BACKUP_PATH=}"
fi

compose_command up -d postgres redis
compose_command up -d backend frontend caddy

if ! wait_healthy backend || ! wait_healthy frontend || ! wait_healthy caddy || ! public_health; then
  compose_command logs --tail 120 backend frontend caddy >&2 || true
  if rollback_images; then
    echo "Application images rolled back. Database was not restored automatically. Pre-deploy backup: $predeploy_backup" >&2
  else
    echo "Rollback needs operator action. Pre-deploy backup: $predeploy_backup" >&2
  fi
  deployment_finished='true'
  exit 1
fi

deployment_finished='true'
trap - ERR
echo "Production deployment healthy: tag=$target_tag"
echo "Pre-deploy backup: $predeploy_backup"
compose_command ps
