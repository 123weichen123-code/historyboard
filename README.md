# 史镜 HistoryBoard（H5）

一句话：一个支持“任意关键词检索 + 同期世界对照”的中国历史看板应用，帮助用户快速定位历史事件并理解其全球时空背景。

## 1. 当前版本能力（V1）

- 中国历史事件快速检索（关键词 / 人物 / 朝代 / 年份 / 别名）
- 同期世界历史关键节点自动对照
- 热门建议词 + 随机探索
- 中国历史时间带浏览（便于全局定位）
- 数据采集第一版：可维护的种子数据 + 统一清洗构建流程

## 2. 技术策略（本次更新）

- **服务端优先 Java 实现**（Spring Boot，见 `java-backend/`）
- **数据获取优先 Java 实现**（`HistoryDatasetBuilder`）
- Python 作为数据获取兜底方案（复杂源或临时脚本场景）

## 3. 项目结构

```text
docs/                               # 设计与需求文档
backend/
  app/data/                         # 标准化历史数据
  data/raw/                         # 原始种子数据
  scripts/build_history_dataset.py  # Python 兜底构建器
frontend/                           # H5 前端
java-backend/                       # Java 服务端（主线）
```

## 4. Java 后端启动（推荐）

```bash
cd /Users/mercury/Documents/ChronoAtlas-HistoryBoard-JavaH5-20260321/java-backend
mvn spring-boot:run
```

或一键联调启动（自动构建数据 + 启动服务）：

```bash
cd /Users/mercury/Documents/ChronoAtlas-HistoryBoard-JavaH5-20260321
./scripts/dev-java.sh
```

浏览器访问：

- [http://127.0.0.1:8080](http://127.0.0.1:8080)
- 可通过 `HB_PORT` 改端口，例如 `HB_PORT=18080 ./scripts/dev-java.sh`

## 5. API 列表

- `GET /api/health`
- `GET /api/history/search?q=秦始皇&limit=12`
- `GET /api/history/suggest?q=丝绸`
- `GET /api/history/events/{eventId}`
- `GET /api/history/timeline`
- `GET /api/history/discover`

## 6. 数据构建方式

### 6.1 Java 优先

```bash
cd /Users/mercury/Documents/ChronoAtlas-HistoryBoard-JavaH5-20260321/java-backend
mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.historyboard.ingest.HistoryDatasetBuilder
```

### 6.2 Python 兜底

```bash
cd /Users/mercury/Documents/ChronoAtlas-HistoryBoard-JavaH5-20260321
python3 backend/scripts/build_history_dataset.py
```

## 7. 文档入口

- `docs/00-需求梳理.md`
- `docs/01-项目设计文档.md`
- `docs/02-需求文档-PRD.md`
- `docs/03-需求规格-SPEC.yaml`
- `docs/04-前端设计文档.md`
- `docs/05-服务端设计文档-Java.md`
- `docs/06-数据获取设计-Java优先.md`
- `docs/07-OpenAPI-HistoryBoard-v1.yaml`

## 8. 常用脚本

- `./scripts/dev-java.sh`：一键联调启动（Java 构建数据 + 启动服务）
- `./scripts/enrich-citations-java.sh 20`：自动补充 source/citation（最多 20 条）
- `./scripts/export-openapi.sh`：导出运行中的 OpenAPI JSON
