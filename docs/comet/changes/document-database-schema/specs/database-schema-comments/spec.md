# 数据库脚本中文表/字段备注完整规格

## 1. 目标与边界

`cap4k-reference-payment` 的 SQL 当前已经包含供生成器使用的机器元数据，但业务职责主要依赖代码和文档推断。本 capability 只增加数据库元数据的可读性：让人打开 `design/schema.sql` 或运行时 H2 schema 时，可以直接知道表的职责、聚合归属和关键字段含义。

设计 schema、表结构、约束、字段名和机器元数据继续由既有 `payment-reference-build` 合同定义；本规格不新增业务能力。

## 2. 备注分层合同

### 2.1 机器元数据

以下现有语法必须保持不变：

- `@Managed=...;`
- `@Type=...;`
- `@ParentRef;`
- `@Parent=...;`
- `@RefAggregate=...;`

它们继续位于字段 `comment '...'` 或表级 `comment on table ... is '...'` 中，供 cap4k DB source 和生成器读取。

### 2.2 人类可读备注

新增备注与机器 token 共存于同一个 COMMENT 值，采用“自然语言在前、机器区在后”的语法：

```sql
merchant_id varchar(64) not null comment '商户标识，用于隔离商户订单和路由配置';
comment on table payment_attempt is '支付尝试：Payment 聚合内的渠道请求与结果证据 @Parent=payment;';
```

机器 token 必须逐个以分号结束，并与前后自然语言留空格；不能用第二条 COMMENT ON 覆盖原有机器元数据。表备注至少说明业务职责与聚合定位；列备注至少说明字段代表的业务事实、状态、快照、计数、时间点或关联身份。自然语言避免出现形如 `@Word` 的未知 token。

## 3. 覆盖范围

为以下 18 张表补充表备注，并覆盖其业务列：

- Payment：`payment`、`payment_attempt`、`payment_notification_receipt`、`payment_review_case`、`payment_review_decision`
- Refund：`refund`、`refund_attempt`、`refund_notification_receipt`
- Routing：`merchant_channel_configuration`
- Reconciliation：`reconciliation_batch`、`reconciliation_run`、`reconciliation_item`、`reconciliation_disposition`、`reconciliation_confirmation_fact`
- Settlement：`merchant_settlement`、`settlement_line`、`settlement_execution_attempt`、`settlement_result_receipt`

`id/version/created_at/created_by/updated_at/updated_by` 等通用技术字段使用统一中文模板即可；业务字段不得只写“字段值”之类空泛备注。

## 4. JSON 设计字段备注

### 4.1 值对象字段

- `design/value-objects.json` 的 11 个值对象、112 个字段均增加中文 `description`。
- 字段对象保留 `name`、`type` 等既有键；`description` 只增加人工可读语义，不参与值对象类型解析。
- 描述采用短句或短语，优先说明字段所代表的身份、金额、状态、事实、计数或时间点；不重复对象级 description。

### 4.2 Design entry 字段

- `design/design.json` 的 57 个 entry 中，`fields` 与 `resultFields` 共 1188 个字段均增加中文 `description`。
- 字段对象采用与 Analyzer 产物一致的紧凑最小表达：保留 `name`、`type`、可选 `defaultValue` 和 `description`，不复制对象级 `description`。
- JSON 文件使用稳定压缩格式，压缩只移除无意义空白，不改变键和值、数组顺序或生成器现有语义。

## 5. 设计脚本与运行时投影

- `design/schema.sql`：唯一设计/生成器输入，包含建表、约束、机器元数据和中文表/列备注。
- `start/src/main/resources/schema.sql`：Hibernate `ddl-auto=create-drop` 后执行的 H2 投影，保留可靠事件列宽与复合唯一约束补丁，并追加与设计 schema 对齐的中文备注。
- 不引入 Flyway/Liquibase，不把运行时 schema.sql 伪装成生产迁移脚本。

## 6. 可观察验收

### DBDOC-001 表级覆盖

18 张表每张都有包含中文自然语言和（如已有）机器 token 的表级 COMMENT，且说明包含职责/聚合/owned graph/事实来源之一。

### DBDOC-002 列级覆盖

每张表的业务列都有中文列 COMMENT；通用审计列可复用统一模板，但不能缺失关键业务字段说明。备注集合按 `table.column` 比较设计脚本和运行时脚本。

### DBDOC-003 机器元数据不回退

静态检查确认所有既有 `@Managed`、`@Type`、`@ParentRef`、`@Parent`、`@RefAggregate` token 仍存在且数量不减少。

### DBDOC-004 H2 可执行

使用项目既有 H2 `MODE=MySQL` 运行设计 INIT 和 start schema 初始化；`COMMENT ON TABLE/COLUMN` 不得导致启动、生成器 DB 分析或测试失败。

### DBDOC-005 合同守卫

新增测试读取两个 SQL 文件，解析独立表/列备注，检查 18 表覆盖、设计/运行时集合相等、中文内容存在、机器 token 保留。

### DBDOC-006 文档可发现

requirements README 或验收指南说明查看 SQL 备注的入口、两份 schema 的职责边界和机器元数据与人类备注的分层。

### DBDOC-007 回归

现有业务测试、Analyzer/traceability、代码生成和最终 `clean build` 全部通过。

### DBDOC-008 值对象字段覆盖

`design/value-objects.json` 的 112 个字段均有非空中文 `description`；字段对象不丢失 `name`/`type`，且 JSON 为稳定压缩格式。

### DBDOC-009 Design 字段覆盖

`design/design.json` 的 1188 个 `fields`/`resultFields` 均有非空中文 `description`；原有生成器字段键和值保持不变，文件使用稳定压缩格式。

### DBDOC-010 JSON 合同守卫

新增测试解析两份 JSON，统计对象和字段数量、验证中文 description、检查压缩格式，并将去除 description 后的结构与变更前快照比较。

## 7. 非目标

- 不修改业务字段值、DDL 类型、唯一约束、外键策略、表/列命名和聚合行为。
- 不迁移历史数据库，不增加 reset API，不增加数据库管理 UI。
- 不把中文备注当作 HTTP 文案，也不翻译稳定 code、enum、event name、provider raw result 或用户输入证据。


