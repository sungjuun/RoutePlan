#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
env_file="${ROUTEPLAN_DEPLOY_ENV_FILE:-$repo_dir/.env.production}"
expected_environment="${ROUTEPLAN_EXPECTED_DEPLOYMENT_ENV:-}"

env_value() {
  local name="$1" value
  value="$(awk -v key="$name" 'index($0, key "=") == 1 { value=substr($0, length(key)+2) } END { print value }' "$env_file")"
  value="${value%$'\r'}"
  if [[ ${#value} -ge 2 && (( "$value" == \"*\" ) || ( "$value" == \'*\' )) ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "$value"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "Required command is missing: $1" >&2; exit 1; }
}

require_positive_integer() {
  local name="$1" value="$2"
  [[ "$value" =~ ^[1-9][0-9]*$ ]] || { echo "$name must be a positive integer." >&2; exit 1; }
}

check_outbound() {
  local name="$1" url="$2"
  curl --silent --show-error --max-time 15 --output /dev/null "$url" || {
    echo "Outbound HTTPS check failed: $name ($url)" >&2
    exit 1
  }
}

[[ "$(uname -s)" == 'Linux' ]] || { echo 'Deployment host must run Linux.' >&2; exit 1; }
for command_name in awk curl df docker getent git grep sed sha256sum stat timeout; do
  require_command "$command_name"
done
docker compose version >/dev/null
docker info >/dev/null
[[ -f "$env_file" ]] || { echo "Deployment environment file not found: $env_file" >&2; exit 1; }
bash "$repo_dir/scripts/validate-production-env.sh" "$env_file" >/dev/null

deployment_environment="$(env_value ROUTEPLAN_DEPLOYMENT_ENVIRONMENT)"
if [[ -n "$expected_environment" && "$deployment_environment" != "$expected_environment" ]]; then
  echo "Deployment target mismatch: workflow=$expected_environment host=$deployment_environment." >&2
  exit 1
fi
compose_project="${ROUTEPLAN_COMPOSE_PROJECT_NAME:-routeplan-$deployment_environment}"

environment_mode="$(stat -c '%a' "$env_file")"
environment_permissions=$((8#$environment_mode))
if (( (environment_permissions & 077) != 0 )); then
  echo "$env_file must not be readable or writable by group/other (use chmod 600)." >&2
  exit 1
fi

minimum_memory_mb="${ROUTEPLAN_MIN_MEMORY_MB:-4096}"
minimum_disk_mb="${ROUTEPLAN_MIN_FREE_DISK_MB:-10240}"
require_positive_integer ROUTEPLAN_MIN_MEMORY_MB "$minimum_memory_mb"
require_positive_integer ROUTEPLAN_MIN_FREE_DISK_MB "$minimum_disk_mb"
available_memory_mb="$(awk '/^MemTotal:/ { print int($2 / 1024) }' /proc/meminfo)"
available_disk_mb="$(df -Pm "$repo_dir" | awk 'NR == 2 { print $4 }')"
(( available_memory_mb >= minimum_memory_mb )) || {
  echo "Insufficient host memory: ${available_memory_mb}MB available, ${minimum_memory_mb}MB required." >&2
  exit 1
}
(( available_disk_mb >= minimum_disk_mb )) || {
  echo "Insufficient free disk: ${available_disk_mb}MB available, ${minimum_disk_mb}MB required." >&2
  exit 1
}

git -C "$repo_dir" rev-parse --is-inside-work-tree >/dev/null
[[ -z "$(git -C "$repo_dir" status --porcelain --untracked-files=no)" ]] || {
  echo 'Tracked files are modified on the deployment host.' >&2
  exit 1
}

domain="$(env_value ROUTEPLAN_DOMAIN)"
getent ahosts "$domain" >/dev/null || { echo "ROUTEPLAN_DOMAIN does not resolve on the host: $domain" >&2; exit 1; }
check_outbound 'GitHub Container Registry' 'https://ghcr.io/v2/'
smtp_host="$(env_value SMTP_HOST)"
smtp_port="$(env_value SMTP_PORT)"
# Positional parameters expand inside the child Bash that opens the TCP socket.
# shellcheck disable=SC2016
timeout 10 bash -c 'exec 3<>"/dev/tcp/$1/$2"' bash "$smtp_host" "$smtp_port" || {
  echo "SMTP endpoint is unreachable: $smtp_host:$smtp_port" >&2
  exit 1
}
if [[ "$(env_value ROUTEPLAN_ROUTE_PROVIDER)" == 'GOOGLE' ]]; then
  check_outbound 'Google Routes' 'https://routes.googleapis.com/'
fi
if [[ "$(env_value ROUTEPLAN_PLACE_PROVIDER)" == 'GOOGLE' ]]; then
  check_outbound 'Google Places' 'https://places.googleapis.com/'
fi
if [[ "$(env_value ROUTEPLAN_AI_PROVIDER)" == 'OPENAI' ]]; then
  check_outbound 'OpenAI API' 'https://api.openai.com/'
fi

bash "$repo_dir/scripts/prepare-monitoring-config.sh" "$env_file" >/dev/null
temporary_release="$(mktemp)"
trap 'rm -f -- "$temporary_release"' EXIT
printf 'ROUTEPLAN_IMAGE_PREFIX=%s\nROUTEPLAN_IMAGE_TAG=host-preflight\n' \
  "$(env_value ROUTEPLAN_IMAGE_PREFIX)" > "$temporary_release"
chmod 0600 "$temporary_release"
docker compose -p "$compose_project" --env-file "$env_file" --env-file "$temporary_release" \
  -f "$repo_dir/compose.production.yaml" config --quiet

echo "Deployment host preflight passed: environment=$deployment_environment project=$compose_project memory=${available_memory_mb}MB free_disk=${available_disk_mb}MB"
