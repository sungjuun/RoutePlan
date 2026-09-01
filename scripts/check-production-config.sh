#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
temporary_dir="$(mktemp -d)"
temporary_env="$temporary_dir/production.env"
runtime_dir="$temporary_dir/runtime"
trap 'rm -rf -- "$temporary_dir"' EXIT
compose_env_file="$temporary_env"
compose_runtime_dir="$runtime_dir"
monitoring_mount="$repo_dir/deploy/monitoring"
alertmanager_config_mount="$runtime_dir/alertmanager.yml"
caddy_config_mount="$repo_dir/deploy/Caddyfile"
if command -v cygpath >/dev/null 2>&1; then
  compose_env_file="$(cygpath -w "$temporary_env")"
  compose_runtime_dir="$(cygpath -w "$runtime_dir")"
  monitoring_mount="$(cygpath -w "$monitoring_mount")"
  alertmanager_config_mount="$(cygpath -w "$alertmanager_config_mount")"
  caddy_config_mount="$(cygpath -w "$caddy_config_mount")"
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

bash "$repo_dir/scripts/validate-production-env.sh" "$temporary_env"
bash "$repo_dir/scripts/prepare-monitoring-config.sh" "$temporary_env" "$runtime_dir"
docker compose --env-file "$temporary_env" -f "$repo_dir/compose.production.yaml" config --quiet
MSYS_NO_PATHCONV=1 docker run --rm \
  --entrypoint /bin/promtool \
  -v "$monitoring_mount:/etc/prometheus:ro" \
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

echo 'Production Compose, monitoring, dashboard, alerting, and Caddy configurations are valid.'
