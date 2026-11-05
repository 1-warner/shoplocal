#!/usr/bin/env bash
#
# setup-sso.sh — configure Google & Facebook SSO on the ShopLocal Supabase project
# via the Supabase Management API. This automates everything on the Supabase side;
# you still create the Google/Facebook OAuth apps in their consoles first (those
# can't be scripted — see SETUP.md).
#
# Usage (run from the repo root, in Git Bash):
#   1. Copy scripts/sso.env.example to scripts/sso.env and fill it in.
#   2. ./scripts/setup-sso.sh
#
# sso.env is git-ignored so your tokens/secrets stay off GitHub.

set -euo pipefail

PROJECT_REF="swchtpvdtncslvlsxeul"
REDIRECT="shoplocal://auth-callback"
ENV_FILE="$(dirname "$0")/sso.env"

if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$ENV_FILE"
fi

: "${SUPABASE_ACCESS_TOKEN:?Set SUPABASE_ACCESS_TOKEN (a Supabase personal access token from https://supabase.com/dashboard/account/tokens)}"

# Build the JSON body, enabling only the providers you supplied credentials for.
body='{"uri_allow_list":"'"$REDIRECT"'","site_url":"'"$REDIRECT"'"'

if [[ -n "${GOOGLE_CLIENT_ID:-}" && -n "${GOOGLE_SECRET:-}" ]]; then
  body+=',"external_google_enabled":true,"external_google_client_id":"'"$GOOGLE_CLIENT_ID"'","external_google_secret":"'"$GOOGLE_SECRET"'"'
  echo "• Google provider will be enabled"
fi

if [[ -n "${FACEBOOK_CLIENT_ID:-}" && -n "${FACEBOOK_SECRET:-}" ]]; then
  body+=',"external_facebook_enabled":true,"external_facebook_client_id":"'"$FACEBOOK_CLIENT_ID"'","external_facebook_secret":"'"$FACEBOOK_SECRET"'"'
  echo "• Facebook provider will be enabled"
fi

body+='}'

echo "Applying auth config to project $PROJECT_REF …"
http_code=$(curl -s -o /tmp/sso_resp.json -w "%{http_code}" \
  -X PATCH "https://api.supabase.com/v1/projects/$PROJECT_REF/config/auth" \
  -H "Authorization: Bearer $SUPABASE_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "$body")

if [[ "$http_code" == "200" ]]; then
  echo "✅ SSO providers configured. The app's Google/Facebook buttons now work."
  echo "   Redirect allow-list set to: $REDIRECT"
else
  echo "❌ Failed (HTTP $http_code):"
  cat /tmp/sso_resp.json
  exit 1
fi
