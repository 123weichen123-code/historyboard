# HistoryBoard Java Backend

本目录提供历史看板的 Java 版本服务端实现（Spring Boot）。

## 1. 功能覆盖

- `GET /api/history/search`
- `GET /api/history/suggest`
- `GET /api/history/events/{eventId}`
- `GET /api/history/timeline`
- `GET /api/history/discover`
- `GET /api/health`
- `GET /`（返回前端页面）
- `GET /assets/**`（前端静态资源）

## 2. 一键联调启动（推荐）

在项目根目录运行：

```bash
cd /Users/mercury/Documents/ChronoAtlas-HistoryBoard-JavaH5-20260321
./scripts/dev-java.sh
```

脚本会自动执行：

1. Java 数据构建器（生成标准数据）
2. 启动 Java 服务端（同时托管前端 H5）

访问地址：

- http://127.0.0.1:8080
- 如 8080 被占用，可使用 `HB_PORT=18080 ./scripts/dev-java.sh`

## 3. OpenAPI 文档

- 运行时接口文档 JSON：`/v3/api-docs`
- Swagger UI：`/swagger-ui.html`
- 仓库静态规范文件：`/Users/mercury/Documents/ChronoAtlas-HistoryBoard-JavaH5-20260321/docs/07-OpenAPI-HistoryBoard-v1.yaml`

## 4. 数据目录配置

默认读取：`../backend/app/data`

可通过参数覆盖：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--historyboard.data-dir=/absolute/path/to/data"
```

## 5. Java 数据构建器（优先方案）

```bash
cd /Users/mercury/Documents/ChronoAtlas-HistoryBoard-JavaH5-20260321/java-backend
mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.historyboard.ingest.HistoryDatasetBuilder
```

输入：

- `../backend/data/raw/china_history_seed.json`
- `../backend/data/raw/world_history_seed.json`

输出：

- `../backend/app/data/china_history_events.json`
- `../backend/app/data/world_history_events.json`

## 6. Java 数据引用采集（source/citation）

### 6.1 自动补充引用（连接器）

```bash
cd /Users/mercury/Documents/ChronoAtlas-HistoryBoard-JavaH5-20260321
./scripts/enrich-citations-java.sh 20
```

输出文件：

- `backend/data/raw/china_history_seed.with_sources.json`

默认连接器：

- Wikipedia Summary API
- Wikidata Search API

补充字段：

- `sources[]`（引用信息）
- `audit_status`
- `last_verified_at`

### 6.2 兜底方案（Python）

当 Java 对某些源实现成本较高时，可临时使用 Python 构建器：

```bash
cd /Users/mercury/Documents/ChronoAtlas-HistoryBoard-JavaH5-20260321
python3 backend/scripts/build_history_dataset.py
```

要求：输出 schema 与 Java 方案一致。
