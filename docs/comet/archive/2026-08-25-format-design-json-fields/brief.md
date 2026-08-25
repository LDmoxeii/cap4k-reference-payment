# Outcome

将 `design/design.json` 从全文件单行压缩调整为“结构格式化、字段对象局部压缩”的稳定表示，便于人工验收和阅读，同时保留 Analyzer 需要的字段语义。

# Scope

- 保持顶层 JSON、entry 对象和普通数组的多行缩进。
- `fields`、`resultFields` 数组保持多行；每个字段对象独占一行并压缩为紧凑对象。
- 字段对象继续保留 `name`、`type`、可选 `defaultValue`、`description`，不删除中文备注。
- 不改变键顺序、字段值、数组顺序、entry 数量或设计语义。

# Non-goals

- 不修改 SQL、值对象 JSON、业务代码或生成器逻辑。
- 不把整个文件再次压缩为单行。
- 不新增或删除设计字段。

# Acceptance examples

```json
"fields": [
  { "name": "merchantId", "type": "String", "description": "商户标识" },
  { "name": "channelId", "type": "String", "description": "渠道标识" }
],
```

# Constraints and invariants

- `design/design.json` 仍必须能被标准 JSON 解析器解析。
- 57 个 entry、所有 `fields`/`resultFields` 字段及其值保持不变；仅允许空白布局变化。
- 字段对象必须是一行一个对象，数组和 entry 仍可读地换行缩进。

# Decisions

- 采用同目录 Analyzer 产物的局部压缩风格：entry 多行，字段对象单行。

# Open questions

- 无。

# Verification expectations

- 解析 JSON 并与变更前内容比较，确认去除空白后的结构完全一致。
- 静态检查字段数组布局：每个字段对象单独一行，顶层和 entry 保持多行。
- 运行现有 `:start:test` 与 `cap4kGenerateSources`。
