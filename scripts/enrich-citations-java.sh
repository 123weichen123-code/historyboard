#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_BACKEND_DIR="$ROOT_DIR/java-backend"

if ! command -v java >/dev/null 2>&1 || ! java -version >/dev/null 2>&1; then
  echo "[ERROR] Java 运行时不可用，请先安装 Java 17+（确保 java -version 可执行）。"
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1 || ! mvn -version >/dev/null 2>&1; then
  echo "[ERROR] Maven 不可用，请先安装 Maven（确保 mvn -version 可执行）。"
  exit 1
fi

cd "$JAVA_BACKEND_DIR"

MAX="${1:-20}"

mvn -q -DskipTests compile exec:java \
  -Dexec.mainClass=com.historyboard.ingest.HistoryCitationEnricher \
  -Dexec.args="--max=${MAX}"

echo "已生成: $ROOT_DIR/backend/data/raw/china_history_seed.with_sources.json"
