# 服务端设计文档（Java 优先）

## 1. 技术选型

- 框架：Spring Boot 3
- 语言：Java 17+
- 序列化：Jackson
- 构建：Maven

## 2. 服务职责

- 读取中国/世界历史标准数据
- 构建内存索引
- 提供检索、详情、建议词、时间带、发现页接口
- 提供中国事件到世界事件的同期对照能力

## 3. 包结构建议

```text
com.historyboard
  |- HistoryBoardApplication
  |- controller
  |    |- HistoryController
  |- service
  |    |- HistorySearchService
  |- model
  |    |- ChinaHistoryEvent
  |    |- WorldHistoryEvent
  |    |- dto/*
  |- config
       |- WebResourceConfig
```

## 4. 核心服务设计

### 4.1 数据加载

- 启动时从 `backend/app/data/*.json` 加载
- 构建索引结构：
  - `eventsById`
  - `indexedEvents`（search blob）
  - `suggestions`

### 4.2 搜索评分策略（V1）

- 标题命中 > 别名命中 > 关键词命中 > 人物命中 > 朝代命中
- 片段命中加分（分词后）
- 年份命中加权（区间内高分、邻近减分）
- 重要性权重加分（importance）
- 无命中 fallback：返回高重要性事件

### 4.3 同期对照策略

- 以中国事件时间区间为中心
- 世界事件在窗口范围（默认 ±90 年）内即候选
- 按中心年份距离排序后返回 TopN

## 5. API 设计

- `GET /api/history/search?q=&limit=`
- `GET /api/history/suggest?q=&limit=`
- `GET /api/history/events/{eventId}`
- `GET /api/history/timeline`
- `GET /api/history/discover`
- `GET /v3/api-docs`
- `GET /swagger-ui.html`

## 5.1 审校字段（source/citation）

- `sources[]`：来源引用列表（source_name、title、url、accessed_at、confidence 等）
- `audit_status`：`UNVERIFIED` / `AUTO_COLLECTED` / `VERIFIED`
- `last_verified_at`：最近审校日期

## 6. 错误处理

- 参数校验失败：400
- 资源不存在：404
- 内部异常：500
- 返回统一错误体（后续可加 traceId）

## 7. 可扩展设计

- Search SPI：可替换为 Lucene/ES
- DataSource SPI：可挂接多源采集器
- RankingPolicy：可切换不同排序策略

## 8. 性能建议

- 启动预热索引，减少首查延迟
- 热点词缓存（本地 Caffeine）
- 大数据量下迁移到外部索引服务
