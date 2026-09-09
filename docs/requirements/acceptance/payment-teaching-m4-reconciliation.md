# M4：Reconciliation 对账快照、差异与追加式处置

> 本文是面向阅读式教学的自包含材料。目标是沿平台事实、渠道 statement、对账 run、差异处置和 effective ownership，理解对账如何把两侧记录收敛成可供结算采用的事实，同时保留不能被覆盖的历史证据。

## 固定上下文胶囊

- 工程：`cap4k-reference-payment`；完整资金事实链为 `Payment → Refund → Reconciliation → Merchant Settlement`。
- 本课范围：按渠道/币种/对账日形成批次；Pull 渠道账单；保留平台与渠道双侧快照；分类差异；处理 revision/effective run；追加 disposition/confirmation；阻断未决事实进入结算。
- 对应验收场景：`PAY-AC-040`–`PAY-AC-047`、`PAY-AC-082`、`PAY-AC-085`、`PAY-AC-087`。
- 当前证据状态：这些场景在证据目录中为 `verified`；这只表示 reference 工程有可复核证据，不表示生产清算网络、通用 Inbox、跨实例 exactly-once 或人工处置 SLA 已完成。
- 测试边界：应用测试使用 H2、Fake Provider、测试 statement source 和 Command/UoW；领域测试证明分类、revision、处置和不变量，不能单独证明 HTTP/JPA wiring 或生产账单供应商。

## 业务问题与全链路位置

Payment 和 Refund 记录的是平台已知事实，渠道 statement 记录的是外部渠道事实。Reconciliation 要回答的不是“哪一边是真的”，而是：在一个明确的 `channel + currency + reconciliationDate` 范围内，两边是否能按稳定身份匹配？如果不能，差异是否已经被授权处置？

```text
Payment success fact + Refund success fact
  → platform snapshot + channel statement snapshot
  → ReconciliationBatch / ReconciliationRun / DifferenceItem
  → effective run + disposition/confirmation
  → Merchant Settlement candidate eligibility
```

对账批次完成不等于原始记录完全相同。无差异、已有明确处置结论的差异可以完成；未决差异、账单不完整或 Payment review blocker 必须阻断结算。原始平台事实和原始渠道记录只能追加新的处置事实，不能被处置动作覆盖。

## 阅读路线

1. [payment-teaching-context.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-context.md)：确认事实链、证据层次和测试可选原则。
2. [payment-teaching-syllabus.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-syllabus.md#m4reconciliation)：确认 M4 的场景范围和重点观察项。
3. [payment-lifecycle.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/business/payment-lifecycle.md#3-对账生命周期)：先看批次范围、状态、差异分类和处置原则。
4. [ReconciliationBatchBehaviorTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/reconciliation_batch/ReconciliationBatchBehaviorTest.kt#L17)：按领域方法阅读分类、review blocker、revision/effective、处置和 confirmation。
5. [ReconciliationReferenceApplicationTests.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/ReconciliationReferenceApplicationTests.kt#L69)：阅读真实 Command、H2 回读、时区边界和 append-only 证据。
6. `ReconciliationEndpointHttpConfigurationContractTest`、`DailyReconciliationSchedulerStructureTest`、`ChannelStatementAvailableIntegrationEventSubscriber`：最后补入口、scheduler 和 inbound event 的边界。
7. [payment-test-evidence-catalog.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-test-evidence-catalog.md)：核对本课各 PAY-AC 的“证明了什么/没有证明什么”。

## 按业务时间顺序阅读对账链

### 1. 固定批次边界，Pull 才是账单权威

批次由渠道、币种和业务日唯一确定。应用测试 `daily reconciliation matches payment and refund facts and exposes one effective run`（[ReconciliationReferenceApplicationTests.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/ReconciliationReferenceApplicationTests.kt#L69)）先创建成功 Payment 和 Refund，再发布同一业务日的完整 statement，最后发送 `RunDailyReconciliationCmd.Request`。断言 batch 为 `COMPLETED`、`currentEffectiveRunId` 唯一、匹配数为 2、差异数和未决数为 0。

入站 `ChannelStatementAvailableIntegrationEvent` 只表示“账单可获取”，subscriber 传递标量和追踪身份后仍由应用 Command 通过 Pull 读取 statement。事件不是账单内容本身，也不能绕过 Pull 权威性。

### 2. 同时保存两侧快照，再分类差异

领域测试 `classifies matched payment and every required difference without overwriting either snapshot`（[ReconciliationBatchBehaviorTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/reconciliation_batch/ReconciliationBatchBehaviorTest.kt#L17)）构造匹配、平台单边、渠道单边、金额、币种、状态、交易类型和重复渠道记录。

关键断言不是只看一个最终状态，而是同时读取：

- `differenceType`：`MATCHED`、`PLATFORM_ONLY`、`CHANNEL_ONLY`、`AMOUNT_MISMATCH`、`CURRENCY_MISMATCH`、`STATUS_MISMATCH`、`UNMATCHED`、`DUPLICATE_CHANNEL_RECORD`；
- `platformAmount` 与 `channelAmount` 等双侧快照；
- `matchedCount`、`differenceCount`、`unresolvedDifferenceCount`；
- 已匹配项 `resolved=true`，差异项保留 `resolved=false`。

这证明对账是在保留原始证据的前提下分类，而不是用渠道记录覆盖平台记录。

### 3. review、账单不完整和未决差异阻断采用

当匹配的 Payment 自身有未解决 review，领域测试 `matched payment with blocking review remains unresolved and preserves the run snapshot` 仍将 item 标为 `MATCHED`，但 `resolved=false`、`settlementBlocked=true`，batch 进入 `AWAITING_DISPOSITION`。这说明“身份匹配”与“可以结算”是两个判断。

账单 `INCOMPLETE` 时，测试断言 batch 为 `REVIEW_REQUIRED`、`settlementBlocked=true`、阻断原因是“渠道账单不完整”；平台单边等未决差异则保持未决且没有 `completedAt`。下游 Settlement 只能采用当前 effective run 中已解决且未阻断的事实。

### 4. revision 幂等、effective run 和旧历史保留

领域方法 `same statement identity and revision is idempotent while a new revision supersedes and retains history`（[ReconciliationBatchBehaviorTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/reconciliation_batch/ReconciliationBatchBehaviorTest.kt#L94)）给同一 statement identity 发送 revision `2` 两次，再发送 revision `3` 和更旧的 revision `1`。

断言含义是：同一 identity + revision 重放返回原 run（`idempotentReplay=true`）；新 revision 成为当前 effective run；旧 revision 状态为 `SUPERSEDED` 但保留 item；迟到的更旧 revision 不能回滚 effective ownership。应用测试 `statement replay revision history and disposition preserve immutable evidence` 进一步证明 HTTP rerun 重放不创建第二个 run，旧 disposition 和 confirmation 不会被覆盖。

### 5. 时区采用半开区间，避免跨日重复归属

`ReconciliationReferenceApplicationTests` 的时区测试以 `Asia/Shanghai` 解释业务日：`2026-08-10T15:59:00Z` 属于 8 月 10 日，`2026-08-10T16:01:00Z` 属于 8 月 11 日。应用回读两个 batch 的 `reconciliationDate`、`businessTimezone` 和 item identity，证明边界采用 `[localDayStart, nextLocalDayStart)`，而不是用 UTC 日期直接切分。

### 6. 被拒处置只追加证据，授权处置必须形成确认事实

`denied disposition is retained but does not resolve the difference`（[ReconciliationBatchBehaviorTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/reconciliation_batch/ReconciliationBatchBehaviorTest.kt#L133)）断言拒绝处置有记录，但 item 仍未解决、batch 仍阻断。

`authorized disposition resolves difference and appends a confirmation fact`（同文件 [L149](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/reconciliation_batch/ReconciliationBatchBehaviorTest.kt#L149)）则要求授权操作同时提供明确结论和 confirmation fact。成功后 item `resolved=true`、`settlementBlocked=false`、batch `COMPLETED`。另一个测试明确拒绝“授权但没有结论”或“有结论但没有确认事实”的不完整输入。

## 关键断言速查

- 批次身份：`channel + currency + reconciliationDate`，同一 statement identity + revision 重放不新建 run。
- 快照完整性：平台事实和渠道记录各自保留，差异项同时可读两侧金额、状态和外部身份。
- 采用资格：`MATCHED` 不自动等于 settlement eligible；Payment review、未决差异或不完整账单仍会设置 `settlementBlocked`。
- effective ownership：新 revision supersede 旧 revision；旧 run 仍保留历史，迟到旧 revision 不能夺回 effective ownership。
- 处置：DENIED 只增加 disposition；AUTHORIZED 必须有明确 conclusion 和一致的 confirmation fact。
- 时间：业务日按 `Asia/Shanghai` 半开区间计算，Instant 只作为交换时间，不改变本地日归属。

## 测试证明了什么，未证明什么

**已证明**

- `PAY-AC-040`–`047`、`082`、`085`、`087` 覆盖的批次边界、Pull 主链、双侧快照、差异分类、review blocker、revision/effective、时区和处置事实有可回读证据。
- 领域测试证明分类、append-only、授权前置条件和 effective ownership 不变量；应用测试证明真实 Command/UoW、H2/JPA 回读和入口串联。
- 入站 event 重复或恢复时仍由 Pull 读取权威 statement；当前有效 run 能为 Settlement 提供可追溯的 eligibility basis。

**未证明**

- Fake Provider/H2 不证明生产清算机构、账单签名、证书轮换、网络分区或账单延迟 SLA。
- 普通 scheduler 和 inbound event 测试不证明通用 Inbox、broker 广播、跨实例 exactly-once 或 durable scheduler。
- 事务级 revision/处置证据不证明高并发压力曲线，也不证明人工团队实际审核时效。
- 对账本身没有证明 Settlement 已成功执行；它只决定哪些事实可以被 Settlement 采用。M5 继续阅读 composition、执行和结果收敛。

## 阅读检查点（非门槛）

1. 为什么 `MATCHED` 仍可能 `settlementBlocked=true`？
2. 为什么 statement replay 不创建第二个 run，但新 revision 会 supersede 旧 run？
3. 平台单边、渠道单边和金额差异分别保留哪些两侧证据？
4. 为什么 DENIED disposition 不能把差异标成 resolved？
5. 授权处置为什么必须同时有 conclusion 和 confirmation fact？
6. `Asia/Shanghai` 半开区间如何决定 8 月 10 日和 8 月 11 日的归属？
7. 哪些结论只能说明“可以进入结算候选”，不能说明“结算已经成功”？

## 与 M5 Merchant Settlement 的连接

M4 结束时，平台 Payment/Refund 成功事实和渠道 statement 已形成可追溯的当前 effective snapshot。M5 将读取这些 resolved、未阻断的对账 item，生成 settlement line，并把每条 line 绑定到 Payment、Refund、Reconciliation batch/run/item 的 source identity。任何 unresolved difference、Payment review 或不完整账单都会排除候选或冻结结算组成。

因此进入 M5 时要带着三个问题：当前 effective run 是哪一个？每个候选事实的 `eligibilityBasis` 是什么？如果执行结果 UNKNOWN 或相互矛盾，Settlement 如何在不重复划拨的情况下继续保留证据并等待处置？
