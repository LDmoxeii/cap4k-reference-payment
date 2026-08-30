# Payment Reference 人工验收指南

本指南是 `cap4k-reference-payment` 的**人工验收主入口**。自动化证据整理完成后，后续阅读式教学请先阅读[教学上下文](payment-teaching-context.md)和[教学大纲](payment-teaching-syllabus.md)，再回到[自动化测试证据目录](payment-test-evidence-catalog.md)。正式人工验收开始前仍需以本指南为执行入口。业务预期仍以 [payment-scenarios.md](payment-scenarios.md) 的 Given/When/Then 为准；场景状态、projection 与 evidence 的机器真源仍是 [traceability.yaml](../traceability.yaml)。

## 1. 当前验收范围

| 项目 | 数量/状态 |
|---|---:|
| 场景总数 | 53 |
| `verified` | 49 |
| `planned/not-built` | 4 |
| 暂不进入实现验收 | `PAY-AC-080`、`PAY-AC-081`、`PAY-AC-084`、`PAY-AC-086` |

状态含义：

- `verified`：可以按本指南定位到运行入口、代码、Flow 和自动化证据；
- `planned/not-built`：只评审需求和缺口，不能把局部机制或测试 fixture 当作完成证据；
- acceptance 状态与更宽的 capability projection closure 不是同一层级。单个场景 verified，不代表所属 projection 的所有未来能力都已完成。

当前 reference 的边界：

- H2 内存数据库用于可重复验收，不代表生产数据库兼容矩阵；
- Payment/Refund/Statement/Settlement 都使用 Fake Provider 或测试 fixture；
- Scheduler 是普通 `@Scheduled` 入口，不是持久化调度、lease 或跨实例 exactly-once；
- outbound HTTP Integration Event 证明 at-least-once handoff 与稳定 identity，不代表下游业务 exactly-once；
- Analyzer Flow 描述独立入口的静态可达关系，不拼接跨入口运行时状态机；
- 测试 receiver 不是生产商户通知服务。

## 2. 推荐验收顺序

不要从 `PAY-AC-001` 开始机械执行 53 项。建议按以下顺序验收：

1. **主链冒烟**：`PAY-AC-001 → 004 → 020/021 → 040 → 060 → 063 → 083 → 088`；
2. **模块异常**：支付/退款失败、未知、超时、差异、负结算额；
3. **复杂边界**：幂等、并发、迟到、重复、矛盾结果、revision、review 与可靠重试；
4. **planned 审计**：确认四个 planned 场景没有被文档或代码证据误宣称为完成。

每个场景都按同一阅读链定位：

```text
PAY-AC 场景
  → PAY-BR 规则与 lifecycle
  → traceability status/evidence
  → Design 与 Aggregate owned graph
  → Behavior
  → Command / Capability
  → HTTP / Time / Integration Event 入口
  → Analyzer Flow
  → 精确测试方法
  → HTTP / Query / 持久化证据观察点
```

## 3. 环境、启动和复位

### 3.1 前提

- JDK 17；
- 当前 mainline 合同建议通过显式 Composite Build 指向本地 cap4k；
- 不向仓库提交本机路径、`mavenLocal()`、Snapshot 或私服配置。

```powershell
# <cap4k-local-path> 替换为本机 cap4k 检出路径；不要写入仓库文件。
.\gradlew.bat clean build -Pcap4k.local.path='<cap4k-local-path>' --no-daemon --console=plain

# 固定 8080 端口启动人工 HTTP 验收；Ctrl+C 停止。
.\gradlew.bat :start:bootRun -Pcap4k.local.path='<cap4k-local-path>' --args='--server.port=8080' --no-daemon --console=plain
```

完整生成和分析检查：

```powershell
.\gradlew.bat cap4kPlan cap4kGenerate cap4kGenerateSources -Pcap4k.local.path='<cap4k-local-path>' --no-daemon --console=plain
.\gradlew.bat cap4kAnalysisPlan cap4kAnalysisGenerate -Pcap4k.local.path='<cap4k-local-path>' --no-daemon --console=plain
.\gradlew.bat cap4kAgentSnapshot -Pcap4k.local.path='<cap4k-local-path>' --no-daemon --console=plain
.\gradlew.bat :start:test --tests '*TraceabilityContractTests' -Pcap4k.local.path='<cap4k-local-path>' --no-daemon --console=plain
```

### 3.2 Fixture 与复位

- 默认商户：`M-001`；
- 默认渠道：`C-001`；
- 币种：`CNY`；
- Payment/Refund verification secret：读取 `start/src/main/resources/application.yml` 的 sandbox 配置；
- 结算验证使用 reference settlement fixture；
- 渠道账单通过 Spring 测试中的 `ChannelStatementFixtureStore` 注入，当前没有公共 statement/reset HTTP API。

H2 使用进程内内存库和 create-drop 生命周期。重复人工验收时：

1. 最可靠方式是停止并重启应用；或
2. 为 merchant order、idempotency key、notification identity、statement identity 使用全新值。

不要假设仓库已有数据库清理或 fixture 管理 Endpoint。

### 3.3 最小支付 HTTP 冒烟

```powershell
$base = 'http://localhost:8080'
$order = 'ORDER-' + [guid]::NewGuid().ToString('N')
$key = 'IDEM-' + [guid]::NewGuid().ToString('N')

$payment = Invoke-RestMethod -Method Post -Uri "$base/api/payments" -ContentType 'application/json' -Body (@{
  merchantId = 'M-001'
  merchantOrderNumber = $order
  idempotencyKey = $key
  amount = 100.00
  currency = 'CNY'
  paymentMethod = 'TEST'
  expiresAt = (Get-Date).ToUniversalTime().AddMinutes(30).ToString('o')
} | ConvertTo-Json)

$attempt = Invoke-RestMethod -Method Post -Uri "$base/api/payments/$($payment.paymentId)/attempts" -ContentType 'application/json' -Body '{}'
```

具体 callback payload 以 [ConfirmPaymentResultEndpoint](../../../contract/src/main/kotlin/com/only4/cap4k/reference/payment/contract/endpoints/payment/api/ConfirmPaymentResultEndpoint.kt) 为准。人工验收负责观察 HTTP/Query；完整 statement、并发、回滚和可靠事件场景优先运行下文指定的 Spring/H2/JPA 测试。

## 4. 公共 Design 与代码入口

| 目的 | 文件 |
|---|---|
| 表、唯一约束、owned graph | [design/schema.sql](../../../design/schema.sql) |
| Command/Query/Capability/Endpoint authoring | [design/design.json](../../../design/design.json) |
| 状态空间及 numeric value | [design/enums.json](../../../design/enums.json) |
| 跨层不可变值语义 | [design/value-objects.json](../../../design/value-objects.json) |
| Command Drawing Board | [design/drawing_board_command.json](../../../design/drawing_board_command.json) |
| Endpoint Drawing Board | [design/drawing_board_endpoint.json](../../../design/drawing_board_endpoint.json) |
| Capability Drawing Board | [design/drawing_board_capability.json](../../../design/drawing_board_capability.json) |
| Aggregate Structure | [design/drawing_board_aggregate_elements.json](../../../design/drawing_board_aggregate_elements.json) |
| 业务生命周期 | [payment-lifecycle.md](../business/payment-lifecycle.md) |
| 业务规则 | [payment-rules.md](../business/payment-rules.md) |
| 当前 cap4k 投影 | [cap4k-current.md](../projection/cap4k-current.md) |

### 4.1 如何阅读数据库备注

- `design/schema.sql` 是表结构、唯一约束、owned graph 和 cap4k DB source 的设计真源。每张表和字段现在都有中文职责说明；打开 SQL 时先看 `comment on table` / 字段后的 `comment`。
- 同一个 COMMENT 值中的中文自然语言位于前部，`@Managed`、`@Type`、`@ParentRef`、`@Parent`、`@RefAggregate` 位于后部；后者是生成器机器元数据，不要删除或改名。
- `start/src/main/resources/schema.sql` 是 Hibernate `create-drop` 后的 H2 运行时投影，补充复合唯一约束和同一套表/字段备注；它不是生产迁移脚本。
- 验收时，表备注回答“这张表属于哪个业务聚合、保存哪类事实”，字段备注回答“这个值代表什么、何时形成、是否是快照/计数/证据”。
- `design/value-objects.json` 的 `fields` 与 `design/design.json` 的 `fields` / `resultFields` 也提供字段级中文 `description`；验收值对象、Command/Query/Endpoint 入参出参时，先看 `name` / `type`，再用 `description` 理解业务含义。
- JSON 字段对象已按分析器产物的紧凑风格局部压缩：`value-objects.json` 保持单行，`design.json` 保留顶层和 entry 的多行结构，`fields` / `resultFields` 数组逐行展示紧凑字段对象。压缩只改变空白，不改变 `name`、`type`、`defaultValue`、聚合引用或入口语义。字段备注是人工阅读元数据，cap4k source provider 会忽略它，不要把 `description` 当成运行时字段。

## 5. 支付：PAY-AC-001..017

### 5.1 架构路径

- 规则：[PAY-BR-001..004、010..014、020..028](../business/payment-rules.md)；
- 生命周期：[支付生命周期](../business/payment-lifecycle.md#1-支付生命周期)；
- owned graph：`Payment → PaymentAttempt → PaymentNotificationReceipt / PaymentReviewCase → PaymentReviewDecision`；
- Behavior：[PaymentBehavior.kt](../../../domain/src/main/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/payment/PaymentBehavior.kt)；
- 主要 Commands：`CreatePaymentCmd`、`StartPaymentAttemptCmd`、`ConfirmPaymentResultCmd`、`ExpirePaymentsCmd`、`AdjudicatePaymentReviewCmd`；
- Capabilities：`StartChannelPayment`、`VerifyPaymentResult`、`SerializeMerchantOrderSuccess`；
- HTTP：create、attempt start、channel result、review adjudication、get；
- Time：`PaymentExpiryScheduler`；
- Flows：[create](../../../flows/endpoint_http_payment_create.mmd)、[attempt](../../../flows/endpoint_http_payment_attempt_start.mmd)、[result](../../../flows/endpoint_http_payment_result_confirm.mmd)、[review](../../../flows/endpoint_http_payment_review_adjudicate.mmd)、[expiry](../../../flows/com_only4_cap4k_reference_payment_adapter_start_PaymentExpiryScheduler_expirePay.mmd)。

### 5.2 场景矩阵

| 场景 | 状态 | 规则 | 推荐自动化证据 | 主要观察点 |
|---|---|---|---|---|
| PAY-AC-001 | verified | BR-001/002/020 | `PaymentReferenceApplicationTests.create attempt confirm duplicate conflict and query form one durable payment chain` | 201、PaymentId、PENDING、幂等键 |
| PAY-AC-002 | verified | BR-002 | 同上 | 相同 PaymentId，不新增 root |
| PAY-AC-003 | verified | BR-002 | 同上 | 409、`IDEMPOTENCY_CONFLICT`、中文 message |
| PAY-AC-004 | verified | BR-004/022/023 | 同上；`PaymentBehaviorTest.accepted channel result forms success once...` | Payment/Attempt SUCCEEDED、receipt、success fact |
| PAY-AC-005 | verified | BR-004 | 同上 | receive count 增加，不形成第二成功事实 |
| PAY-AC-006 | verified | BR-024 | `attempt identity and currency mismatches remain queryable...` | REJECTED receipt、中文 rejectionSummary、不推进 Payment |
| PAY-AC-007 | verified | BR-026 | `payment expiry closes without pending attempts...` | CLOSED、closedAt、重复扫描幂等 |
| PAY-AC-008 | verified | BR-026/027 | `expired processing payment enters one stable review...` | RESULT_UNKNOWN、stable review identity |
| PAY-AC-009 | verified | BR-027 | `late success after closed payment preserves terminal evidence...` | 迟到 success receipt、HELD_FOR_REVIEW、review decision |
| PAY-AC-010 | verified | BR-021/022 | `second attempt success preserves both successes...` | 两份 success evidence、收入/fee/intent 仅一次 |
| PAY-AC-011 | verified | BR-003/022 | `concurrent payments for one merchant order...` | 仅一个 accepted claim；`ORDER_ALREADY_PAID` 中文 message |
| PAY-AC-012 | verified | BR-010/012 | `invalid amount precision and unsupported currency...` | 400 `INVALID_REQUEST`、中文 message、幂等键未占用 |
| PAY-AC-013 | verified | BR-011 | create durable chain | 原 Payment 金额不变、冲突可观察 |
| PAY-AC-014 | verified | BR-014 | `configuration changes do not rewrite frozen payment fees...` | Payment fee snapshot、SettlementLine fee 不漂移 |
| PAY-AC-015 | verified | BR-023 | `accepted channel result forms success once and later failure cannot roll it back` | Payment 仍 SUCCEEDED、conflict receipt/review |
| PAY-AC-016 | verified | BR-025 | create durable chain | Gateway ACCEPTED 只进入 PROCESSING |
| PAY-AC-017 | verified | BR-028 | expiry/domain tests | 409、稳定 code、中文 message、不新增 Attempt |

核心测试文件：

- [PaymentBehaviorTest.kt](../../../domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/PaymentBehaviorTest.kt)
- [PaymentReferenceApplicationTests.kt](../../../start/src/test/kotlin/com/only4/cap4k/reference/payment/PaymentReferenceApplicationTests.kt)

## 6. 退款：PAY-AC-020..029

### 6.1 架构路径

- 规则：[PAY-BR-005、030..036](../business/payment-rules.md)；
- 生命周期：[退款生命周期](../business/payment-lifecycle.md#2-退款生命周期)；
- 预算权威：Payment 的 `reservedRefundAmount`、`successfulRefundAmount`、`refundableAmount`；
- owned graph：`Refund → RefundAttempt → RefundNotificationReceipt`；
- Behavior：[RefundBehavior.kt](../../../domain/src/main/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/refund/RefundBehavior.kt)；
- Commands：`CreateRefundCmd`、`ConfirmRefundResultCmd`、`ReviewPendingRefundsCmd`；
- Flows：[create](../../../flows/endpoint_http_refund_create.mmd)、[result](../../../flows/endpoint_http_refund_result_confirm.mmd)、[review](../../../flows/com_only4_cap4k_reference_payment_adapter_start_RefundReviewScheduler_review.mmd)。

### 6.2 场景矩阵

| 场景 | 状态 | 规则 | 推荐自动化证据 | 主要观察点 |
|---|---|---|---|---|
| PAY-AC-020 | verified | BR-030/031 | `a successful payment can be refunded in full` | Refund SUCCEEDED、预算转 successful |
| PAY-AC-021 | verified | BR-031 | `multiple partial refunds remain independently queryable...` | 多 Refund 独立、总额不超 Payment |
| PAY-AC-022 | verified | BR-031 | `refund beyond the exact remaining amount is rejected...` | 409、预算不变、无渠道请求 |
| PAY-AC-023 | verified | BR-032 | 两个 concurrent refund tests | 一个成功、一个 409 `CONCURRENT_MODIFICATION` |
| PAY-AC-024 | verified | BR-034 | failed result / gateway exception tests | reservation 释放，Refund FAILED |
| PAY-AC-025 | verified | BR-034/036 | `unknown refund result remains reserved...` | RESULT_UNKNOWN/REVIEW_REQUIRED，预算仍占用 |
| PAY-AC-026 | verified | BR-005 | callback idempotent + changed content tests | 同 merchant refund number 返回同 RefundId；变更内容冲突 |
| PAY-AC-027 | verified | BR-030 | eligibility mismatch + non-success tests | 400/409 中文消息，不占预算 |
| PAY-AC-028 | verified | BR-033 | `refund rejects non-success payments and requests after the refund window` | succeededAt + 180 天边界 |
| PAY-AC-029 | verified | BR-035 | `refund accepted callback is idempotent and queryable` | 成功不回退、冲突 receipt 保留 |

核心测试：[PaymentReferenceApplicationTests.kt](../../../start/src/test/kotlin/com/only4/cap4k/reference/payment/PaymentReferenceApplicationTests.kt)。

## 7. 对账：PAY-AC-040..047、082、085、087

### 7.1 架构路径

- 规则：[PAY-BR-040..046、062、063](../business/payment-rules.md)；
- 生命周期：[对账生命周期](../business/payment-lifecycle.md#3-对账生命周期)；
- owned graph：`ReconciliationBatch → ReconciliationRun → ReconciliationItem → Disposition / ConfirmationFact`，并保留 statement revision 历史；
- Behavior：[ReconciliationBatchBehavior.kt](../../../domain/src/main/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/reconciliation_batch/ReconciliationBatchBehavior.kt)；
- Commands：`RunDailyReconciliationCmd`、`RerunReconciliationBatchCmd`、`DisposeReconciliationDifferenceCmd`、`ProcessAvailableChannelStatementCmd`；
- Capability：`PullChannelStatement`、`LoadPlatformReconciliationFacts`；
- Flows：[daily](../../../flows/com_only4_cap4k_reference_payment_adapter_start_DailyReconciliationScheduler_rec.mmd)、[rerun](../../../flows/endpoint_http_reconciliation_batch_rerun.mmd)、[disposition](../../../flows/endpoint_http_reconciliation_difference_dispose.mmd)、[inbound event](../../../flows/com_only4_cap4k_reference_payment_contract_events_integration_inbound_reconcilia.mmd)。

### 7.2 场景矩阵

| 场景 | 状态 | 规则 | 推荐自动化证据 | 主要观察点 |
|---|---|---|---|---|
| PAY-AC-040 | verified | BR-042/043/046 | `daily reconciliation matches payment and refund facts...` | MATCHED、currentEffectiveRun、双方快照 |
| PAY-AC-041 | verified | BR-043/045 | domain classify test | PLATFORM_ONLY、settlementBlocked |
| PAY-AC-042 | verified | BR-043/044 | domain/app confirmation tests | CHANNEL_ONLY、授权 confirmation |
| PAY-AC-043 | verified | BR-043/044 | `authorized amount mismatch disposition preserves both amounts...` | AMOUNT_MISMATCH、两侧金额不改写 |
| PAY-AC-044 | verified | BR-044/046 | unknown refund confirmation test | append-only disposition/confirmation、原 Refund 不改写 |
| PAY-AC-045 | verified | BR-040/041 | replay/revision/concurrency tests | 同 revision 幂等，新 revision effective，旧 run 保留 |
| PAY-AC-046 | verified | BR-045/046 | unavailable/incomplete test | 非 COMPLETE、中文 blockingReason |
| PAY-AC-047 | verified | BR-041/044/062 | disposition domain/app tests | 原始 facts、run、item、早期 disposition 不覆盖 |
| PAY-AC-082 | verified | BR-041/044/062 | replay/revision/disposition test | revision/disposition/confirmation 完整历史 |
| PAY-AC-085 | verified | BR-063 | Asia Shanghai boundary test | `[start,end)`、本地业务日、原 Instant |
| PAY-AC-087 | verified | BR-040/041 | inbound replay + recovery convergence tests | Event 仅 signal；正文由 Pull；四入口收敛 |

核心测试：

- [ReconciliationBatchBehaviorTest.kt](../../../domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/reconciliation_batch/ReconciliationBatchBehaviorTest.kt)
- [ReconciliationReferenceApplicationTests.kt](../../../start/src/test/kotlin/com/only4/cap4k/reference/payment/ReconciliationReferenceApplicationTests.kt)：`daily reconciliation matches payment and refund facts and exposes one effective run`

## 8. 商户结算：PAY-AC-060..068、014、083、085、088

### 8.1 架构路径

- 规则：[PAY-BR-006、014/015、050..056、062/063](../business/payment-rules.md)；
- 生命周期：[商户结算生命周期](../business/payment-lifecycle.md#4-商户结算生命周期)；
- owned graph：`MerchantSettlement → SettlementLine / SettlementExecutionAttempt → SettlementResultReceipt`；
- Behavior：[MerchantSettlementBehavior.kt](../../../domain/src/main/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/merchant_settlement/MerchantSettlementBehavior.kt)；
- Commands：prepare/run、confirm、start execution、confirm result、review/adjudicate、void/adjustment；
- Capabilities：`LoadMerchantSettlementCandidates`、`StartSettlementTransfer`、`VerifySettlementResult`；
- Flows：[daily](../../../flows/com_only4_cap4k_reference_payment_adapter_start_DailyMerchantSettlementScheduler.mmd)、[unknown review](../../../flows/com_only4_cap4k_reference_payment_adapter_start_UnknownMerchantSettlementReviewS.mmd)、[prepare](../../../flows/endpoint_http_merchant_settlement_prepare.mmd)、[confirm](../../../flows/endpoint_http_merchant_settlement_confirm.mmd)、[execution](../../../flows/endpoint_http_merchant_settlement_execution_start.mmd)、[result](../../../flows/endpoint_http_merchant_settlement_result_confirm.mmd)、[void](../../../flows/endpoint_http_merchant_settlement_void.mmd)。

### 8.2 场景矩阵

| 场景 | 状态 | 规则 | 推荐自动化证据 | 主要观察点 |
|---|---|---|---|---|
| PAY-AC-060 | verified | BR-015/050/052 | `merchant settlement lifecycle produces net 127...` | lines、gross、fee、net=127 |
| PAY-AC-061 | verified | BR-045/050 | unresolved item exclusion tests | eligible/excluded count、中文 blocker summary |
| PAY-AC-062 | verified | BR-006/051 | concurrent prepare/execution tests | 一个 effective settlement/attempt |
| PAY-AC-063 | verified | BR-055 | lifecycle test | PROCESSING→SUCCEEDED、receipt、settled fact |
| PAY-AC-064 | verified | BR-054 | `unknown result blocks retry until review...` | RESULT_UNKNOWN、禁止重付、人工 evidence |
| PAY-AC-065 | verified | BR-055 | behavior conflict test | 迟到相反结果只追加 conflict，不回退成功 |
| PAY-AC-066 | verified | BR-056 | `negative and zero net settlements never invoke...` | negative review；zero success；均不错误转账 |
| PAY-AC-067 | verified | BR-053 | adjustment/freeze tests | 确认后 composition/fee/line 不改写 |
| PAY-AC-068 | verified | BR-006/051 | void/replacement/concurrent prepare tests | predecessor/replacement、effective ownership 唯一 |
| PAY-AC-014 | verified | BR-014 | configuration change test | Payment fee fact 与 SettlementLine 一致 |
| PAY-AC-083 | verified | BR-052/062 | `payment refund reconciliation and settlement preserve one durable composition trail` | Payment/Refund/Run/Item/Line ID 与净额 78 |
| PAY-AC-085 | verified | BR-063 | business day + composition tests | Asia/Shanghai period、原 Instant |
| PAY-AC-088 | verified | BR-055/062 | rollback、HTTP 503、timeout retry tests | business success 与 `__event` 同 UoW；delivery attempt 增加，identity/payload 不变 |

核心测试：

- [MerchantSettlementBehaviorTest.kt](../../../domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/merchant_settlement/MerchantSettlementBehaviorTest.kt)
- [MerchantSettlementReferenceApplicationTests.kt](../../../start/src/test/kotlin/com/only4/cap4k/reference/payment/MerchantSettlementReferenceApplicationTests.kt)：`merchant settlement lifecycle produces net 127 and preserves callback evidence`

## 9. Planned 场景：只验收边界，不寻找不存在的实现

| 场景 | 状态 | 当前可以确认 | 不得误宣称 |
|---|---|---|---|
| PAY-AC-080 商户数据隔离 | planned | traceability 指向 not-built authorization/tenant evidence | 当前没有完整 tenant isolation |
| PAY-AC-081 商户通知失败重试 | planned | B5 Settlement Integration Event transport 已有可靠重试 | 它不是生产商户成功通知服务 |
| PAY-AC-084 退役渠道不再参与新支付 | planned | MerchantChannelConfiguration 有状态和历史快照 | 配置对象存在不等于该场景已完整验收 |
| PAY-AC-086 敏感人工动作必须授权并留痕 | planned | reference 有 operator fixture 和 append-only evidence | fixture 不是生产认证、RBAC、双人复核或租户授权 |

## 10. 错误与中文消息观察规则

HTTP 错误保持稳定结构：

```json
{
  "status": 409,
  "code": "PAYMENT_EXPIRED",
  "message": "支付已过期，不能继续发起渠道支付",
  "details": {
    "paymentId": "..."
  }
}
```

验收原则：

- `code`、HTTP status、enum、event name、JSON 字段和 identity 保持机器稳定；
- `message` 使用中文；
- `details` 只包含安全业务上下文；
- 数据库、Hibernate、SQLState、异常类名、堆栈和 provider 原始 cause 不得进入 HTTP response；
- 新写入的 rejection/conflict/review/blocking/failure summary 使用中文；
- 渠道 raw result/code、matchingBasis、用户输入 reason/evidence/follow-up 保持原值。

## 11. 最终验收命令

```powershell
.\gradlew.bat :domain:test :application:test :adapter:test :start:test -Pcap4k.local.path='<cap4k-local-path>' --no-daemon --console=plain
.\gradlew.bat clean build -Pcap4k.local.path='<cap4k-local-path>' --no-daemon --console=plain
.\gradlew.bat cap4kAnalysisPlan cap4kAnalysisGenerate -Pcap4k.local.path='<cap4k-local-path>' --no-daemon --console=plain
.\gradlew.bat cap4kAgentSnapshot -Pcap4k.local.path='<cap4k-local-path>' --no-daemon --console=plain
```

Focused tests 用于定位，不能替代最终 `clean build`。最终结果必须为 0 failure、0 error、0 skip，并保持 Generator ownership、Analyzer、AgentFacts 和 traceability 合同不回退。
