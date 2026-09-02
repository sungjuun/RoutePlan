#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
temporary_dir="$(mktemp -d)"
temporary_env="$temporary_dir/production.env"
staging_env="$temporary_dir/staging.env"
operations_env="$temporary_dir/operations.env"
release_env="$temporary_dir/release.env"
runtime_dir="$temporary_dir/runtime"
staging_runtime_dir="$temporary_dir/staging-runtime"
trap 'rm -rf -- "$temporary_dir"' EXIT
compose_env_file="$temporary_env"
compose_runtime_dir="$runtime_dir"
monitoring_mount="$repo_dir/deploy/monitoring"
prometheus_config_mount="$runtime_dir/prometheus.yml"
staging_prometheus_config_mount="$staging_runtime_dir/prometheus.yml"
alertmanager_config_mount="$runtime_dir/alertmanager.yml"
caddy_config_mount="$repo_dir/deploy/Caddyfile"
frontend_mount="$repo_dir/frontend"
operations_mount="$repo_dir/tests/operations"
powershell_env="$operations_env"
powershell_validator="$repo_dir/scripts/validate-production-env.ps1"
if command -v cygpath >/dev/null 2>&1; then
  compose_env_file="$(cygpath -w "$temporary_env")"
  compose_runtime_dir="$(cygpath -w "$runtime_dir")"
  monitoring_mount="$(cygpath -w "$monitoring_mount")"
  prometheus_config_mount="$(cygpath -w "$prometheus_config_mount")"
  staging_prometheus_config_mount="$(cygpath -w "$staging_prometheus_config_mount")"
  alertmanager_config_mount="$(cygpath -w "$alertmanager_config_mount")"
  caddy_config_mount="$(cygpath -w "$caddy_config_mount")"
  frontend_mount="$(cygpath -w "$frontend_mount")"
  operations_mount="$(cygpath -w "$operations_mount")"
  powershell_env="$(cygpath -w "$powershell_env")"
  powershell_validator="$(cygpath -w "$powershell_validator")"
fi

sed \
  -e 's/routeplan\.example\.com/routeplan.test/g' \
  -e 's/admin@example\.com/admin@routeplan.test/g' \
  -e 's/your-github-user/test-owner/g' \
  -e 's/replace-with-at-least-24-random-characters/ci-only-database-password-1234567890/g' \
  -e 's/replace-with-at-least-20-random-characters/ci-only-grafana-password-123456789/g' \
  -e 's/smtp\.example\.com/smtp.routeplan.test/g' \
  -e 's/replace-me/ci-only-smtp-secret/g' \
  -e 's/noreply@example\.com/noreply@routeplan.test/g' \
  "$repo_dir/.env.production.example" > "$temporary_env"
printf '\nROUTEPLAN_ENV_FILE=%s\nROUTEPLAN_RUNTIME_DIR=%s\nROUTEPLAN_IMAGE_TAG=ci-check\n' \
  "$compose_env_file" "$compose_runtime_dir" >> "$temporary_env"
staging_compose_env_file="$staging_env"
staging_compose_runtime_dir="$staging_runtime_dir"
if command -v cygpath >/dev/null 2>&1; then
  staging_compose_env_file="$(cygpath -w "$staging_env")"
  staging_compose_runtime_dir="$(cygpath -w "$staging_runtime_dir")"
fi
cp "$temporary_env" "$staging_env"
printf '\nROUTEPLAN_DEPLOYMENT_ENVIRONMENT=staging\nROUTEPLAN_DOMAIN=staging.routeplan.test\n' >> "$staging_env"
printf 'ROUTEPLAN_PUBLIC_URL=https://staging.routeplan.test\nROUTEPLAN_ENV_FILE=%s\nROUTEPLAN_RUNTIME_DIR=%s\n' \
  "$staging_compose_env_file" "$staging_compose_runtime_dir" >> "$staging_env"
sed \
  -e 's/^ROUTEPLAN_ROUTE_PROVIDER=SIMPLE$/ROUTEPLAN_ROUTE_PROVIDER=GOOGLE/' \
  -e 's/^ROUTEPLAN_TIME_DEPENDENT_GLOBAL_ENABLED=false$/ROUTEPLAN_TIME_DEPENDENT_GLOBAL_ENABLED=true/' \
  -e 's/^GOOGLE_MAPS_API_KEY=$/GOOGLE_MAPS_API_KEY=ci-only-server-key/' \
  -e 's/^GOOGLE_MAPS_BROWSER_KEY=$/GOOGLE_MAPS_BROWSER_KEY=ci-only-browser-key/' \
  -e 's/^GOOGLE_ROUTES_USD_PER_THOUSAND=0$/GOOGLE_ROUTES_USD_PER_THOUSAND=1.00/' \
  -e 's/^GOOGLE_MONTHLY_BUDGET_USD=0$/GOOGLE_MONTHLY_BUDGET_USD=10.00/' \
  "$temporary_env" > "$operations_env"
printf 'ROUTEPLAN_IMAGE_PREFIX=ghcr.io/test-owner/routeplan\nROUTEPLAN_IMAGE_TAG=ci-check\n' > "$release_env"

bash "$repo_dir/scripts/validate-production-env.sh" "$temporary_env"
if command -v pwsh >/dev/null 2>&1; then
  pwsh -NoLogo -NoProfile -File "$powershell_validator" -Path "$powershell_env"
fi
bash "$repo_dir/scripts/prepare-monitoring-config.sh" "$temporary_env" "$runtime_dir"
bash "$repo_dir/scripts/validate-production-env.sh" "$staging_env"
bash "$repo_dir/scripts/prepare-monitoring-config.sh" "$staging_env" "$staging_runtime_dir"
grep -qx '    environment: production' "$runtime_dir/prometheus.yml"
grep -qx '    environment: staging' "$staging_runtime_dir/prometheus.yml"
docker compose --env-file "$temporary_env" -f "$repo_dir/compose.production.yaml" config --quiet
docker compose -p routeplan-staging --env-file "$staging_env" \
  -f "$repo_dir/compose.production.yaml" config --quiet
ROUTEPLAN_OPERATION_SCENARIOS_FILE="$repo_dir/tests/operations/scenarios.example.json" \
  docker compose --env-file "$temporary_env" -f "$repo_dir/compose.production.yaml" \
    -f "$repo_dir/compose.operations.yaml" --profile operations config --quiet
MSYS_NO_PATHCONV=1 docker run --rm \
  --entrypoint /bin/promtool \
  -v "$prometheus_config_mount:/etc/prometheus/prometheus.yml:ro" \
  -v "$monitoring_mount/alerts.yml:/etc/prometheus/rules/alerts.yml:ro" \
  prom/prometheus:v3.14.0 check config /etc/prometheus/prometheus.yml
MSYS_NO_PATHCONV=1 docker run --rm \
  --entrypoint /bin/promtool \
  -v "$staging_prometheus_config_mount:/etc/prometheus/prometheus.yml:ro" \
  -v "$monitoring_mount/alerts.yml:/etc/prometheus/rules/alerts.yml:ro" \
  prom/prometheus:v3.14.0 check config /etc/prometheus/prometheus.yml
MSYS_NO_PATHCONV=1 docker run --rm \
  --entrypoint /bin/amtool \
  -v "$alertmanager_config_mount:/etc/alertmanager/alertmanager.yml:ro" \
  prom/alertmanager:v0.34.0 --enable-feature=utf8-strict-mode check-config /etc/alertmanager/alertmanager.yml
MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$monitoring_mount:/monitoring:ro" \
  mikefarah/yq:4 eval 'true' /monitoring/grafana/provisioning/datasources/prometheus.yml \
  /monitoring/grafana/provisioning/dashboards/routeplan.yml \
  /monitoring/grafana/dashboards/routeplan-overview.json >/dev/null
MSYS_NO_PATHCONV=1 docker run --rm \
  -e ROUTEPLAN_DOMAIN=routeplan.test \
  -e TLS_EMAIL=admin@routeplan.test \
  -v "$caddy_config_mount:/etc/caddy/Caddyfile:ro" \
  caddy:2.11.3-alpine caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$frontend_mount/nginx.production.main.conf:/etc/nginx/nginx.conf:ro" \
  -v "$frontend_mount/nginx.production.conf:/etc/nginx/conf.d/default.conf:ro" \
  nginx:1.27-alpine nginx -t
MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$operations_mount:/operations:ro" \
  node:22-alpine sh -eu -c 'node --check /operations/v25-load.mjs && node --check /operations/v25-report.mjs'
ROUTEPLAN_OPERATION_ENV_FILE="$operations_env" \
ROUTEPLAN_OPERATION_RELEASE_FILE="$release_env" \
ROUTEPLAN_OPERATION_SCENARIOS_FILE="$repo_dir/tests/operations/scenarios.example.json" \
ROUTEPLAN_OPERATION_MAX_GOOGLE_UNITS_DELTA=0 \
ROUTEPLAN_OPERATION_MAX_GOOGLE_CALLS_DELTA=0 \
ROUTEPLAN_FAULT_DRILL_CONFIRM=RUN_ROUTEPLAN_PRODUCTION_FAULT_DRILL \
  bash "$repo_dir/scripts/run-v25-operational-validation.sh" --allow-production

echo 'Staging/production Compose, monitoring, proxy, dashboard, alerting, and operations runner configurations are valid.'
