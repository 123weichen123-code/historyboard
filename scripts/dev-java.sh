#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_BACKEND_DIR="$ROOT_DIR/java-backend"
HB_PORT="${HB_PORT:-8080}"

if ! command -v java >/dev/null 2>&1 || ! java -version >/dev/null 2>&1; then
  echo "[ERROR] Java 运行时不可用，请先安装 Java 17+（确保 java -version 可执行）。"
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1 || ! mvn -version >/dev/null 2>&1; then
  echo "[ERROR] Maven 不可用，请先安装 Maven（确保 mvn -version 可执行）。"
  exit 1
fi

echo "[1/2] 构建历史数据（Java 优先）..."
cd "$JAVA_BACKEND_DIR"
mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.historyboard.ingest.HistoryDatasetBuilder

echo "[2/2] 启动 Java 后端（包含 H5 前端联调）..."
echo "打开 http://127.0.0.1:${HB_PORT}"
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=${HB_PORT}"
