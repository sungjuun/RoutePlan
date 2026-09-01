#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
env_file="${1:-$repo_dir/.env.production}"

if [[ ! -f "$env_file" ]]; then
  echo "Production environment file not found: $env_file" >&2
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

reject_placeholder() {
  local name="$1" value="${2,,}"
  if [[ "$value" =~ example\.com|replace-me|replace-with|your-github|change-me|changeme ]]; then
    echo "$name still contains an example placeholder." >&2
    exit 1
  fi
}

domain="$(require_value ROUTEPLAN_DOMAIN)"
if [[ ! "$domain" =~ ^([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$ ]]; then
  echo 'ROUTEPLAN_DOMAIN must be a hostname without a scheme, port, or path.' >&2
  exit 1
fi
reject_placeholder ROUTEPLAN_DOMAIN "$domain"

public_url="$(require_value ROUTEPLAN_PUBLIC_URL)"
[[ "$public_url" == "https://$domain" ]] || { echo 'ROUTEPLAN_PUBLIC_URL must exactly match https://ROUTEPLAN_DOMAIN.' >&2; exit 1; }

tls_email="$(require_value TLS_EMAIL)"
[[ "$tls_email" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]] || { echo 'TLS_EMAIL must be a valid email address.' >&2; exit 1; }
reject_placeholder TLS_EMAIL "$tls_email"

image_prefix="$(require_value ROUTEPLAN_IMAGE_PREFIX)"
[[ "$image_prefix" =~ ^ghcr\.io/[a-z0-9._-]+/[a-z0-9._/-]+$ ]] || { echo 'ROUTEPLAN_IMAGE_PREFIX must be a lowercase ghcr.io image prefix.' >&2; exit 1; }
reject_placeholder ROUTEPLAN_IMAGE_PREFIX "$image_prefix"

database_password="$(require_value POSTGRES_PASSWORD)"
(( ${#database_password} >= 24 )) || { echo 'POSTGRES_PASSWORD must contain at least 24 characters.' >&2; exit 1; }
reject_placeholder POSTGRES_PASSWORD "$database_password"

cookie_secure="$(require_value ROUTEPLAN_SESSION_COOKIE_SECURE)"
mail_mode="$(require_value ROUTEPLAN_AUTH_MAIL_MODE)"
[[ "${cookie_secure,,}" == 'true' ]] || { echo 'ROUTEPLAN_SESSION_COOKIE_SECURE must be true in production.' >&2; exit 1; }
[[ "${mail_mode^^}" == 'SMTP' ]] || { echo 'ROUTEPLAN_AUTH_MAIL_MODE must be SMTP in production.' >&2; exit 1; }
[[ "$(require_value ROUTEPLAN_AUTH_TRUSTED_PROXIES)" == '172.30.241.0/24' ]] || { echo 'ROUTEPLAN_AUTH_TRUSTED_PROXIES must match the isolated production app network.' >&2; exit 1; }

smtp_host="$(require_value SMTP_HOST)"
smtp_from="$(require_value SMTP_FROM)"
reject_placeholder SMTP_HOST "$smtp_host"
reject_placeholder SMTP_FROM "$smtp_from"
smtp_port="$(require_value SMTP_PORT)"
if [[ ! "$smtp_port" =~ ^[0-9]+$ ]] || (( smtp_port < 1 || smtp_port > 65535 )); then
  echo 'SMTP_PORT must be between 1 and 65535.' >&2
  exit 1
fi
smtp_starttls="$(require_value SMTP_STARTTLS)"
smtp_ssl="$(require_value SMTP_SSL)"
[[ "${smtp_starttls,,}" =~ ^(true|false)$ ]] || { echo 'SMTP_STARTTLS must be true or false.' >&2; exit 1; }
[[ "${smtp_ssl,,}" =~ ^(true|false)$ ]] || { echo 'SMTP_SSL must be true or false.' >&2; exit 1; }
[[ "${smtp_starttls,,}" == 'true' || "${smtp_ssl,,}" == 'true' ]] || { echo 'Production SMTP must enable STARTTLS or SSL.' >&2; exit 1; }
if [[ "$(require_value SMTP_AUTH)" == 'true' ]]; then
  smtp_username="$(require_value SMTP_USERNAME)"
  smtp_password="$(require_value SMTP_PASSWORD)"
  reject_placeholder SMTP_USERNAME "$smtp_username"
  reject_placeholder SMTP_PASSWORD "$smtp_password"
fi

grafana_user="$(require_value GRAFANA_ADMIN_USER)"
[[ "$grafana_user" =~ ^[A-Za-z0-9._-]{3,64}$ ]] || { echo 'GRAFANA_ADMIN_USER must contain 3-64 safe username characters.' >&2; exit 1; }
grafana_password="$(require_value GRAFANA_ADMIN_PASSWORD)"
(( ${#grafana_password} >= 20 )) || { echo 'GRAFANA_ADMIN_PASSWORD must contain at least 20 characters.' >&2; exit 1; }
reject_placeholder GRAFANA_ADMIN_PASSWORD "$grafana_password"
alert_email="$(require_value ROUTEPLAN_ALERT_EMAIL_TO)"
[[ "$alert_email" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]] || { echo 'ROUTEPLAN_ALERT_EMAIL_TO must be one valid email address.' >&2; exit 1; }
reject_placeholder ROUTEPLAN_ALERT_EMAIL_TO "$alert_email"

place_provider="$(require_value ROUTEPLAN_PLACE_PROVIDER)"
route_provider="$(require_value ROUTEPLAN_ROUTE_PROVIDER)"
ai_provider="$(require_value ROUTEPLAN_AI_PROVIDER)"
if [[ "${place_provider^^}" == 'GOOGLE' || "${route_provider^^}" == 'GOOGLE' ]]; then
  google_server_key="$(require_value GOOGLE_MAPS_API_KEY)"
  reject_placeholder GOOGLE_MAPS_API_KEY "$google_server_key"
fi
if [[ "${route_provider^^}" == 'GOOGLE' ]]; then
  google_browser_key="$(require_value GOOGLE_MAPS_BROWSER_KEY)"
  reject_placeholder GOOGLE_MAPS_BROWSER_KEY "$google_browser_key"
fi
if [[ "${ai_provider^^}" == 'OPENAI' ]]; then
  openai_key="$(require_value OPENAI_API_KEY)"
  reject_placeholder OPENAI_API_KEY "$openai_key"
fi

echo 'Production environment validation passed. Secret values were not printed.'
