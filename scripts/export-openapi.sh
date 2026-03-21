#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_FILE="$ROOT_DIR/docs/07-OpenAPI-HistoryBoard-v1.runtime.json"
API_URL="${1:-http://127.0.0.1:8080/v3/api-docs}"

if ! command -v curl >/dev/null 2>&1; then
  echo "[ERROR] curl 未安装。"
  exit 1
fi

curl -sS "$API_URL" -o "$OUT_FILE"
echo "已导出 OpenAPI 到: $OUT_FILE"
