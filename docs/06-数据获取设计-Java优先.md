# 数据获取设计（Java 优先，Python 兜底）

## 1. 目标

建立可持续的数据获取与清洗流程，保证历史数据可扩展、可追溯、可审校。

## 2. 技术原则

1. **优先 Java**：采集器、清洗器、构建器优先使用 Java 实现。
2. **Python 兜底**：当 Java 对某源实现成本高（例如临时脚本抓取、反爬处理、第三方库缺失）时，允许 Python 补充。
3. **统一出口**：无论 Java/Python，输出都必须落到统一 JSON Schema。

## 3. 分层设计

- Source Connector（Java）
  - Wiki/Baike/API/CSV 等连接器
- Normalizer（Java）
  - 字段规范、时间标准化、去重
- Validator（Java）
  - 必填字段校验、年份区间校验
- Publisher（Java）
  - 输出 `backend/app/data/*.json`
- Python Fallback Collector（可选）
  - 将结果转换为同 schema 后进入 Normalizer

## 4. 字段规范

### 中国历史事件

- id, title, aliases[], dynasty, start_year, end_year
- location, figures[], summary, impact, category, tags[]
- importance, keywords[]

### 世界历史事件

- id, title, region, start_year, end_year, summary

## 5. 处理流程

1. 读取 raw seed / 外部源
2. 清洗标准化
3. 生成关键词
4. 排序与去重
5. 输出标准 JSON
6. 产物校验（数量、字段、唯一性）

## 6. 工程化建议

- Java CLI：`HistoryDatasetBuilder` 作为主构建器
- Java 引用采集器：`HistoryCitationEnricher`（默认接 Wikipedia + Wikidata）
- 定时任务：后续支持每日/每周自动构建
- 质量闸门：构建失败不覆盖线上数据
- 版本快照：按日期保存构建产物用于回滚
