# Outcome

让支付参考项目的数据库脚本同时服务于两类读者：cap4k 生成器继续读取现有机器元数据，验收者和开发者可以直接从 SQL 看懂每张表、关键字段和聚合关系。

# Scope

## Included work

- 以 `design/schema.sql` 为数据库设计真源，为 18 张业务表补充中文表级备注。`design/value-objects.json` 的 112 个值对象字段和 `design/design.json` 的 1188 个 fields/resultFields 也补充中文 `description`。
- 为每张表的业务字段补充中文列备注；保留现有 `@Managed`、`@Type`、`@ParentRef`、`@Parent`、`@RefAggregate` 机器指令，不把自然语言混入机器注释值。
- 为 `start/src/main/resources/schema.sql` 补充同一套运行时 H2 表/列备注，使应用启动后的数据库元数据也可读；该文件继续只承担 Hibernate `create-drop` 后的 H2 约束与注释投影，不变成迁移工具。
- 补充 SQL 注释约定和验收说明，明确设计脚本、运行时脚本、机器元数据与人类可读备注的边界。JSON 字段描述采用与 Analyzer 输出一致的紧凑 JSON 表达：字段对象保留 name/type/description 的最小信息集，文件使用稳定压缩格式。
- 增加静态合同测试/脚本守卫，确保 18 张表都有表备注、关键业务列都有列备注、机器元数据仍保留，且设计与运行时备注集合不漂移。

## Non-goals

- 不引入 Flyway、Liquibase 或新的迁移机制。
- 不改变表名、列名、数据类型、默认值、唯一约束、聚合边界、生成器行为或运行时业务语义。JSON 备注只增加人工可读元数据，不改变生成器使用的 name/type/description 对象语义。
- 不修改稳定的 `@...` 机器注释语法，不把中文备注作为生成器元数据解析输入。
- 不要求为每个纯技术审计字段重复解释；`id/version/created_at/created_by/updated_at/updated_by` 继续由 `@Managed` 语义覆盖，并只补必要的通用说明。
- 不将 provider raw 字段、用户输入证据或稳定 code 翻译成展示文案；数据库备注描述字段职责，不改字段值。

# Acceptance examples

- **DBDOC-001**：`design/schema.sql` 的 18 张表均有包含中文自然语言的表级 COMMENT（已有 `@Parent` 时与 token 共存），且每条说明包含业务职责或聚合定位。
- **DBDOC-002**：18 张表的业务字段均有中文列 COMMENT；技术审计字段使用统一模板，不重复制造噪音。
- **DBDOC-003**：现有 `@Managed`、`@Type`、`@ParentRef`、`@Parent`、`@RefAggregate` 机器指令逐字保留，生成器分析仍可读取 schema。
- **DBDOC-004**：运行时 H2 `schema.sql` 在约束补丁后写入同一套表/列备注，应用启动不因 H2 方言失败。
- **DBDOC-005**：设计脚本与运行时投影的备注集合可由静态合同测试对齐；缺表备注、缺关键列备注、机器注释被改写时测试失败。
- **DBDOC-006**：README/验收指南明确从 `design/schema.sql` 查看表备注、字段备注和 `@...` 机器元数据，并说明 `start/schema.sql` 是运行时投影。
- **DBDOC-007**：现有 Gradle build、数据库分析、应用测试和 Analyzer/traceability 合同不回退。
- **DBDOC-008**：`design/value-objects.json` 的 112 个值对象字段均有中文 `description`，且字段对象采用压缩的最小键集合。
- **DBDOC-009**：`design/design.json` 的 1188 个 fields/resultFields 均有中文 `description`，且 JSON 使用稳定压缩格式；原有 tag/name/type/defaultValue 等键和值不漂移。
- **DBDOC-010**：新增合同测试统计并校验 SQL、值对象 JSON、设计 JSON 的字段备注覆盖、中文内容、压缩格式和结构不漂移。

# Constraints and invariants

- 备注使用中文自然语言；表名、列名、枚举名、错误码、事件名和 cap4k 机器 token 保持原文。
- 表/列备注使用与机器 token 共存的混合 COMMENT：中文自然语言放在前面，机器 token 放在后面并逐个以分号结束；不能用第二条 COMMENT ON 覆盖同一对象的机器备注。
- `design/schema.sql` 是生成器输入和设计真源；`start/src/main/resources/schema.sql` 是 Hibernate `create-drop` 之后的 H2 补丁与投影，二者必须保持业务备注语义一致。
- 备注只解释职责、来源、生命周期、不变量和关联，不声称不存在的外部系统能力。
- JSON 字段描述使用短句或短语，不重复对象级 description，不翻译稳定类型名；压缩只影响空白，不改变 JSON 语义。
- 本次变更保持单一 Comet change，由主线程完成所有写操作；只读代理只做事实核对或最终验收。

# Decisions

- 采用“同一 COMMENT 内分层”：中文自然语言置于前部，`@Managed`、`@Type`、`@ParentRef`、`@Parent`、`@RefAggregate` 等机器 token 置于后部，token 之间用空格分隔并以分号结束；这样 JDBC REMARKS 清洗后同时得到人类说明和机器元数据。
- 18 张表全部补表备注；每张表的业务字段全部补列备注，审计/技术字段按统一模板处理。
- 值对象和 design entry 的字段备注统一使用 `description` 键，字段级描述短而具体，便于验收者和 Analyzer 快速扫描。
- 设计脚本和运行时 H2 脚本同步维护；不新增第三份 schema 真源。
- 通过静态测试守卫备注集合和机器 token，而不是依赖数据库 GUI 才能发现缺失。

# Open questions

- 无。当前项目已明确使用 H2 `MODE=MySQL`，且已有 `comment on table` 语法；本 change 采用同一方言边界。

# Verification expectations

- 静态检查 SQL 语法、表/列备注覆盖、机器 token 保留、值对象/设计 JSON 字段备注覆盖、压缩格式和结构对齐。
- 运行 `:start:test`、数据库分析相关 Gradle 任务以及最终 `clean build`；重点观察 H2 启动和 schema INIT。
- 独立只读 Verifier 逐项检查 DBDOC-001..007，并确认没有改变业务 schema 或生成器合同。


