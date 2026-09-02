#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
env_file="${ROUTEPLAN_OPERATION_ENV_FILE:-$repo_dir/.env.production}"
release_file="${ROUTEPLAN_OPERATION_RELEASE_FILE:-$repo_dir/.routeplan-release.env}"
scenario_file="${ROUTEPLAN_OPERATION_SCENARIOS_FILE:-$repo_dir/tests/operations/scenarios.json}"
execute='false'
allow_production='false'
for argument in "$@"; do
  case "$argument" in
    --execute) execute='true' ;;
    --allow-production) allow_production='true' ;;
    *) echo "Unknown argument: $argument" >&2; exit 2 ;;
  esac
done

env_value() {
  local name="$1" value
  value="$(awk -v key="$name" 'index($0, key "=") == 1 { value=substr($0, length(key)+2) } END { print value }' "$env_file")"
  value="${value%$'\r'}"
  if [[ ${#value} -ge 2 && (( "$value" == \"*\" ) || ( "$value" == \'*\' )) ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "$value"
}

[[ -f "$env_file" ]] || { echo "Environment file not found: $env_file" >&2; exit 1; }
[[ -f "$release_file" ]] || { echo "Release file not found: $release_file" >&2; exit 1; }
[[ -f "$scenario_file" ]] || { echo "Dedicated scenario file not found: $scenario_file" >&2; exit 1; }
bash "$repo_dir/scripts/validate-production-env.sh" "$env_file"

deployment_environment="$(env_value ROUTEPLAN_DEPLOYMENT_ENVIRONMENT)"
compose_project="${ROUTEPLAN_COMPOSE_PROJECT_NAME:-routeplan-$deployment_environment}"
backend_replicas="$(env_value ROUTEPLAN_BACKEND_REPLICAS)"
route_provider="$(env_value ROUTEPLAN_ROUTE_PROVIDER)"
time_dependent="$(env_value ROUTEPLAN_TIME_DEPENDENT_GLOBAL_ENABLED)"
redis_cache="$(env_value ROUTEPLAN_ROUTE_CACHE_ENABLED)"
database_cache="$(env_value ROUTEPLAN_ROUTE_DB_CACHE_ENABLED)"
cache_prefix="$(env_value ROUTEPLAN_ROUTE_CACHE_KEY_PREFIX)"
[[ "${route_provider^^}" == 'GOOGLE' ]] || { echo 'Operational V25 validation requires ROUTEPLAN_ROUTE_PROVIDER=GOOGLE.' >&2; exit 1; }
[[ "${time_dependent,,}" == 'true' ]] || { echo 'Operational V25 validation requires ROUTEPLAN_TIME_DEPENDENT_GLOBAL_ENABLED=true.' >&2; exit 1; }
[[ "${redis_cache,,}" == 'true' && "${database_cache,,}" == 'true' ]] || {
  echo 'Operational V25 validation requires both Redis and PostGIS route caches.' >&2
  exit 1
}
[[ "$cache_prefix" =~ ^[A-Za-z0-9:_-]{3,100}$ ]] || { echo 'Unsafe route cache key prefix.' >&2; exit 1; }

max_google_units="${ROUTEPLAN_OPERATION_MAX_GOOGLE_UNITS_DELTA:-}"
max_google_calls="${ROUTEPLAN_OPERATION_MAX_GOOGLE_CALLS_DELTA:-}"
[[ "$max_google_units" =~ ^[0-9]+$ && "$max_google_calls" =~ ^[0-9]+$ ]] || {
  echo 'Set non-negative ROUTEPLAN_OPERATION_MAX_GOOGLE_UNITS_DELTA and ROUTEPLAN_OPERATION_MAX_GOOGLE_CALLS_DELTA.' >&2
  exit 1
}

if [[ "$deployment_environment" == 'production' ]]; then
  [[ "$allow_production" == 'true' ]] || {
    echo 'Production fault injection is refused without --allow-production.' >&2
    exit 1
  }
  required_confirmation='RUN_ROUTEPLAN_PRODUCTION_FAULT_DRILL'
else
  required_confirmation='RUN_ROUTEPLAN_STAGING_FAULT_DRILL'
fi
[[ "${ROUTEPLAN_FAULT_DRILL_CONFIRM:-}" == "$required_confirmation" ]] || {
  echo "ROUTEPLAN_FAULT_DRILL_CONFIRM must equal $required_confirmation." >&2
  exit 1
}

compose_command() {
  ROUTEPLAN_OPERATION_SCENARIOS_FILE="$scenario_file" \
    docker compose --env-file "$env_file" --env-file "$release_file" \
      -p "$compose_project" -f "$repo_dir/compose.production.yaml" \
      -f "$repo_dir/compose.operations.yaml" "$@"
}

if [[ "$execute" != 'true' ]]; then
  echo "Dry run passed: environment=$deployment_environment replicas=$backend_replicas"
  echo 'Planned phases: baseline load -> Redis stop/load/recovery -> PostGIS route-cache table interruption/load/recovery -> Prometheus report.'
  echo 'Re-run with --execute inside an approved maintenance window.'
  exit 0
fi

results_dir="$repo_dir/tests/operations/results/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p -- "$results_dir"
chmod 0700 -- "$results_dir"
postgis_interrupted='false'
redis_stopped='false'

restore_postgis() {
  if [[ "$postgis_interrupted" == 'true' ]]; then
    compose_command exec -T postgres psql -v ON_ERROR_STOP=1 \
      -U "$(env_value POSTGRES_USER)" -d "$(env_value POSTGRES_DB)" \
      -c 'ALTER TABLE IF EXISTS route_leg_cache_fault_drill RENAME TO route_leg_cache;' >/dev/null
    postgis_interrupted='false'
  fi
}

restore_redis() {
  if [[ "$redis_stopped" == 'true' ]]; then
    compose_command start redis >/dev/null
    redis_stopped='false'
  fi
}

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  restore_postgis || true
  restore_redis || true
  exit "$status"
}
trap cleanup EXIT INT TERM

wait_service() {
  local service="$1" expected="$2" deadline=$((SECONDS + 180))
  local -a ids
  while (( SECONDS < deadline )); do
    mapfile -t ids < <(compose_command ps -q "$service")
    if (( ${#ids[@]} == expected )); then
      local healthy='true' id state
      for id in "${ids[@]}"; do
        state="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id")"
        [[ "$state" == 'healthy' ]] || healthy='false'
      done
      [[ "$healthy" == 'true' ]] && return 0
    fi
    sleep 3
  done
  echo "$service did not reach $expected healthy container(s)." >&2
  return 1
}

run_load() {
  local label="$1"
  ROUTEPLAN_LOAD_CONFIRM=RUN_ROUTEPLAN_MUTATING_LOAD \
  ROUTEPLAN_LOAD_LABEL="$label" \
  ROUTEPLAN_LOAD_CONCURRENCY="$backend_replicas" \
  ROUTEPLAN_MAX_GOOGLE_UNITS_DELTA="$max_google_units" \
  ROUTEPLAN_MAX_GOOGLE_CALLS_DELTA="$max_google_calls" \
  ROUTEPLAN_EXPECTED_MIN_INSTANCES="$backend_replicas" \
    compose_command --profile operations run --rm operations-runner | tee "$results_dir/$label.json"
}

compose_command up -d --scale backend="$backend_replicas" backend frontend prometheus
wait_service backend "$backend_replicas"
run_load baseline

redis_stopped='true'
compose_command stop redis >/dev/null
run_load redis-down
restore_redis
wait_service redis 1

# The positional parameter expands inside the Redis container shell.
# shellcheck disable=SC2016
compose_command exec -T redis sh -eu -c \
  'redis-cli --scan --pattern "$1" | xargs -r redis-cli del >/dev/null' sh "$cache_prefix:google-routes:*"
cache_relations="$(compose_command exec -T postgres psql -At -v ON_ERROR_STOP=1 \
  -U "$(env_value POSTGRES_USER)" -d "$(env_value POSTGRES_DB)" \
  -c "SELECT (to_regclass('public.route_leg_cache') IS NOT NULL)::int || ':' || (to_regclass('public.route_leg_cache_fault_drill') IS NOT NULL)::int;")"
[[ "$cache_relations" == '1:0' ]] || {
  echo "Unsafe PostGIS cache relation state: $cache_relations (expected 1:0)." >&2
  exit 1
}
postgis_interrupted='true'
compose_command exec -T postgres psql -v ON_ERROR_STOP=1 \
  -U "$(env_value POSTGRES_USER)" -d "$(env_value POSTGRES_DB)" \
  -c 'ALTER TABLE route_leg_cache RENAME TO route_leg_cache_fault_drill;' >/dev/null
run_load postgis-route-cache-down
restore_postgis
run_load recovered

ROUTEPLAN_REPORT_LOOKBACK="${ROUTEPLAN_REPORT_LOOKBACK:-7d}" \
  compose_command --profile operations run --rm operations-report | tee "$results_dir/prometheus-report.json"

trap - EXIT INT TERM
echo "Operational V25 validation passed. Results: $results_dir"
