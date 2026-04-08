#!/bin/sh
set -eu

api_base_url="${HUNTCORE_DASHBOARD_API_BASE_URL:-http://127.0.0.1:8081}"
api_base_url="${api_base_url%/}"

cat > /usr/share/nginx/html/runtime-config.js <<EOF
window.__HUNTCORE_CONFIG__ = Object.assign({}, window.__HUNTCORE_CONFIG__, {
  apiBaseUrl: "${api_base_url}"
});
EOF
