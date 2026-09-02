#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
env_file="${1:-$repo_dir/.env.production}"
runtime_dir="${2:-$repo_dir/.routeplan-runtime}"

if [[ ! -f "$env_file" ]]; then
  echo "Deployment environment file not found: $env_file" >&2
  exit 1
fi

env_value() {
  local name="$1" value
  value="$(awk -v key="$name" 'index($0, key "=") == 1 { value=substr($0, length(key)+2) } END { print value }' "$env_file")"
  value="${value%$'\r'}"
  if [[ ${#value} -ge 2 && (( "$value" == \"*\" ) || ( "$value" == \'*\' )) ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "$value"
}

require_value() {
  local name="$1" value
  value="$(env_value "$name")"
  if [[ -z "${value//[[:space:]]/}" ]]; then
    echo "$name is required." >&2
    exit 1
  fi
  printf '%s' "$value"
}

json_string() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  printf '"%s"' "$value"
}

smtp_host="$(require_value SMTP_HOST)"
smtp_port="$(require_value SMTP_PORT)"
smtp_from="$(require_value SMTP_FROM)"
smtp_auth="$(require_value SMTP_AUTH)"
smtp_starttls="$(require_value SMTP_STARTTLS)"
smtp_ssl="$(require_value SMTP_SSL)"
alert_email="$(require_value ROUTEPLAN_ALERT_EMAIL_TO)"
grafana_password="$(require_value GRAFANA_ADMIN_PASSWORD)"
deployment_environment="$(require_value ROUTEPLAN_DEPLOYMENT_ENVIRONMENT)"
[[ "$deployment_environment" =~ ^(staging|production)$ ]] || {
  echo 'ROUTEPLAN_DEPLOYMENT_ENVIRONMENT must be staging or production.' >&2
  exit 1
}

mkdir -p -- "$runtime_dir"
chmod 0700 -- "$runtime_dir"
config_file="$runtime_dir/alertmanager.yml"
smtp_password_file="$runtime_dir/smtp-password"
grafana_password_file="$runtime_dir/grafana-admin-password"
prometheus_config="$runtime_dir/prometheus.yml"
temporary_config="$config_file.tmp"
temporary_smtp_password="$smtp_password_file.tmp"
temporary_grafana_password="$grafana_password_file.tmp"
temporary_prometheus="$prometheus_config.tmp"
umask 077

sed "s/__ROUTEPLAN_DEPLOYMENT_ENVIRONMENT__/$deployment_environment/g" \
  "$repo_dir/deploy/monitoring/prometheus.yml" > "$temporary_prometheus"

{
  printf '{\n  "global": {\n'
  printf '    "smtp_from": %s,\n' "$(json_string "$smtp_from")"
  printf '    "smtp_smarthost": %s,\n' "$(json_string "$smtp_host:$smtp_port")"
  printf '    "smtp_require_tls": %s' "$([[ "${smtp_starttls,,}" == 'true' || "${smtp_ssl,,}" == 'true' ]] && printf true || printf false)"
  if [[ "${smtp_auth,,}" == 'true' ]]; then
    printf ',\n    "smtp_auth_username": %s,\n' "$(json_string "$(require_value SMTP_USERNAME)")"
    printf '    "smtp_auth_password_file": "/run/secrets/smtp_password"'
  fi
  printf '\n  },\n'
  printf '  "route": {\n'
  printf '    "receiver": "routeplan-operator",\n'
  printf '    "group_by": ["alertname", "severity", "provider"],\n'
  printf '    "group_wait": "30s",\n'
  printf '    "group_interval": "5m",\n'
  printf '    "repeat_interval": "4h",\n'
  printf '    "routes": [\n'
  printf '      {"matchers": ["severity=\\"critical\\""], "repeat_interval": "30m"},\n'
  printf '      {"matchers": ["severity=\\"warning\\""], "repeat_interval": "4h"}\n'
  printf '    ]\n'
  printf '  },\n'
  printf '  "receivers": [\n'
  printf '    {"name": "routeplan-operator", "email_configs": ['
  printf '{"to": %s, "send_resolved": true, "force_implicit_tls": %s}' \
    "$(json_string "$alert_email")" "$([[ "${smtp_ssl,,}" == 'true' ]] && printf true || printf false)"
  printf ']}\n'
  printf '  ],\n'
  printf '  "inhibit_rules": [\n'
  printf '    {"source_matchers": ["alertname=\\"RoutePlanBackendDown\\""], "target_matchers": ["severity=\\"warning\\""], "equal": ["service", "environment"]}\n'
  printf '  ]\n'
  printf '}\n'
} > "$temporary_config"

if [[ "${smtp_auth,,}" == 'true' ]]; then
  printf '%s' "$(require_value SMTP_PASSWORD)" > "$temporary_smtp_password"
else
  : > "$temporary_smtp_password"
fi
printf '%s' "$grafana_password" > "$temporary_grafana_password"

chmod 0644 -- "$temporary_config" "$temporary_smtp_password" "$temporary_grafana_password" \
  "$temporary_prometheus"
mv -- "$temporary_config" "$config_file"
mv -- "$temporary_smtp_password" "$smtp_password_file"
mv -- "$temporary_grafana_password" "$grafana_password_file"
mv -- "$temporary_prometheus" "$prometheus_config"

printf 'Monitoring runtime configuration prepared at %s\n' "$runtime_dir"
