#!/usr/bin/env bash
# Prepares a local .env for docker compose, generating the core-banking credential
# so that no credential literal ever appears in tracked source.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "Created .env from .env.example"
fi

if ! grep -q '^CORE_MOCK_API_KEY=.\+' .env; then
  key="dev-core-$(openssl rand -hex 16)"
  # Replace the empty assignment with the generated value.
  if grep -q '^CORE_MOCK_API_KEY=' .env; then
    sed -i '' "s|^CORE_MOCK_API_KEY=.*|CORE_MOCK_API_KEY=${key}|" .env 2>/dev/null \
      || sed -i "s|^CORE_MOCK_API_KEY=.*|CORE_MOCK_API_KEY=${key}|" .env
  else
    echo "CORE_MOCK_API_KEY=${key}" >> .env
  fi
  echo "Generated CORE_MOCK_API_KEY in .env"
fi

echo "Environment ready. Run: docker compose up --build"
