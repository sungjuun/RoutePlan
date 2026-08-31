#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
temporary_env="$(mktemp)"
trap 'rm -f -- "$temporary_env"' EXIT

sed \
  -e 's/routeplan\.example\.com/routeplan.test/g' \
  -e 's/admin@example\.com/admin@routeplan.test/g' \
  -e 's/your-github-user/test-owner/g' \
  -e 's/replace-with-at-least-24-random-characters/ci-only-database-password-1234567890/g' \
  -e 's/smtp\.example\.com/smtp.routeplan.test/g' \
  -e 's/replace-me/ci-only-smtp-secret/g' \
  -e 's/noreply@example\.com/noreply@routeplan.test/g' \
  "$repo_dir/.env.production.example" > "$temporary_env"
printf '\nROUTEPLAN_ENV_FILE=%s\nROUTEPLAN_IMAGE_TAG=ci-check\n' "$temporary_env" >> "$temporary_env"

bash "$repo_dir/scripts/validate-production-env.sh" "$temporary_env"
docker compose --env-file "$temporary_env" -f "$repo_dir/compose.production.yaml" config --quiet
docker run --rm \
  -e ROUTEPLAN_DOMAIN=routeplan.test \
  -e TLS_EMAIL=admin@routeplan.test \
  -v "$repo_dir/deploy/Caddyfile:/etc/caddy/Caddyfile:ro" \
  caddy:2.11.3-alpine caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile

echo 'Production Compose and Caddy configuration are valid.'
