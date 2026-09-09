# M7：Payment → Refund → Reconciliation → Merchant Settlement 全链路综合理解

> 本文是整套阅读式教学的综合篇。目标不是再逐条朗读 PAY-AC，而是沿一条可回读的资金事实轨迹，说明四个业务族如何互相引用、哪些事实只追加不覆盖、哪些阻断会传递到下游，以及 reference 测试证据最终停在哪里。

## 固定上下文胶囊

- 工程：`cap4k-reference-payment`；本课追踪一条完整资金事实链：`Payment → Refund → Reconciliation → Merchant Settlement`。
- 本课重点：Payment success fact、Refund budget/fact、Reconciliation effective run、Settlement composition/settled fact、跨聚合 source identity、review/blocker、事件交接和持久化回读。
- 推荐主链场景：`PAY-AC-001 → PAY-AC-004 → PAY-AC-020/021 → PAY-AC-040 → PAY-AC-060 → PAY-AC-063 → PAY-AC-083 → PAY-AC-088`。
- 当前验收地图：53 个场景中 49 个 `verified`，4 个 `planned/not-built`：`PAY-AC-080`、`PAY-AC-081`、`PAY-AC-084`、`PAY-AC-086`。
- 本课仍不要求运行测试；已有源码、断言、H2/JPA 回读和证据目录足以作为阅读材料。reference 证据不能外推为生产银行、通知、broker、跨实例 exactly-once、完整 RBAC 或性能结论。

## 业务问题：一笔钱如何成为可追踪、可退款、可对账、可结算的事实

全链路要解决的不是“几个接口能否返回 200”，而是同一笔资金在不同业务阶段是否保持可解释、可追踪和不可重复计入：

```text
Payment intent
  → trusted payment success fact
  → Refund reservations and refund facts
  → Reconciliation platform/channel snapshot and effective run
  → Settlement lines, frozen composition and settled fact
  → completion event / downstream handoff
```

每个箭头都代表一个新的业务事实，而不是把上游对象直接改成下游状态：

- Payment 形成一次成功事实，Attempt/receipt 记录外部结果；
- Refund 在 Payment 上预占预算，成功转换、失败释放、UNKNOWN 保留并复核；
- Reconciliation 保存平台和渠道两侧快照，按 identity/revision 形成 effective run；
- Settlement 只采用 resolved、未阻断的 effective facts，冻结组成后再执行划拨；
- 事件记录和 completion handoff 具有稳定身份，但不自动等于下游已经处理成功。

## 总体阅读路线

1. [payment-teaching-index.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-index.md)：回看模块路线和证据边界。
2. [payment-teaching-m1-payment-main-chain.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-m1-payment-main-chain.md)：确认 Payment/Attempt 的基准主链。
3. [payment-teaching-m2-payment-exception-and-convergence.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-m2-payment-exception-and-convergence.md)：确认 review、conflict、终态保护和订单级竞争。
4. [payment-teaching-m3-refund-lifecycle-and-budget.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-m3-refund-lifecycle-and-budget.md)：确认退款预算和 Refund fact。
5. [payment-teaching-m4-reconciliation.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-m4-reconciliation.md)：确认平台/渠道 snapshot、effective run 和差异处置。
6. [payment-teaching-m5-merchant-settlement.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-m5-merchant-settlement.md)：确认 line、net、执行和 settled fact。
7. [payment-teaching-m6-http-event-analyzer-and-gaps.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-m6-http-event-analyzer-and-gaps.md)：确认入口、事件、静态 Flow 与缺口边界。
8. 对照三组主测试：`PaymentReferenceApplicationTests`、`ReconciliationReferenceApplicationTests`、`MerchantSettlementReferenceApplicationTests`，最后查阅 [payment-test-evidence-catalog.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-test-evidence-catalog.md)。

## 一、Payment：先形成唯一成功事实

阅读 [PaymentReferenceApplicationTests.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/PaymentReferenceApplicationTests.kt#L68) 与 [PaymentBehaviorTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/PaymentBehaviorTest.kt#L42)：创建返回 `PENDING`，发起 Attempt 后进入 `PROCESSING`，只有可信且匹配的 callback 才形成 `SUCCEEDED` 和 `successFact`。

全链路必须携带的 Payment 事实包括：

- `paymentId`、merchant order、金额、币种和幂等身份；
- Attempt identity、channel configuration snapshot、渠道交易号和发生时间；
- 每条 notification receipt 的 identity、payload、verification、decision 和 receive count；
- `successFactFormed`、成功时间、冻结的 fee snapshot、`merchantSuccessNotificationIntentCount`；
- review/conflict/settlement block 等决定下游资格的状态。

重复成功只增加 receipt 计数，不再形成第二份成功事实；成功后的 FAILED/UNKNOWN 追加冲突证据并阻断结算，不回退原成功。这是后面三层都要复用的“追加而非覆盖”原则。

## 二、Refund：把成功 Payment 转成预算受控的新事实

阅读 [payment-teaching-m3-refund-lifecycle-and-budget.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-m3-refund-lifecycle-and-budget.md) 和 `PaymentReferenceApplicationTests` 的退款段落。Refund 只能引用已确认成功的 Payment，创建时先增加 `reservedRefundAmount`：

```text
refundableAmount = paymentAmount - successfulRefundAmount - reservedRefundAmount
```

全链路检查的不是“Refund 金额小于 Payment 金额”这一条，而是三种预算变化：

- `PROCESSING`：预占增加，Refund 尚未成功；
- trusted `SUCCESS`：预占转换为 `successfulRefundAmount`，可退金额下降；
- trusted `FAILED`：预占释放，Payment 原成功事实不变；
- `UNKNOWN`/`REVIEW_REQUIRED`：预占保持，避免在渠道未知时重复退款。

同一 merchant refund number 重放必须复用原 Refund；关键内容变化返回 `REFUND_IDEMPOTENCY_CONFLICT`。并发申请和事务失败都不能让 Payment 超额预占，也不能留下没有对应 Refund 的 reservation。

## 三、Reconciliation：验证两侧事实是否可采用

阅读 [ReconciliationReferenceApplicationTests.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/ReconciliationReferenceApplicationTests.kt#L69) 与 [ReconciliationBatchBehaviorTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/reconciliation_batch/ReconciliationBatchBehaviorTest.kt#L17)。对账把 Payment/Refund 平台事实与渠道 statement 放进同一批次范围，保存两侧 snapshot，按稳定 identity 分类：

- `MATCHED`：身份和关键业务字段一致，但仍需检查 Payment review/blocker；
- `PLATFORM_ONLY` / `CHANNEL_ONLY`：一侧有记录，另一侧缺失；
- `AMOUNT_MISMATCH`、`CURRENCY_MISMATCH`、`STATUS_MISMATCH`：双方都有记录但关键内容不一致；
- `DUPLICATE_CHANNEL_RECORD` / `UNMATCHED`：无法直接采用，需要保留 evidence。

同一 statement identity + revision 重放不创建第二个 run；新 revision 成为 effective，旧 run 仍在历史中。DENIED disposition 只追加拒绝事实，差异继续 unresolved；AUTHORIZED disposition 必须有明确 conclusion 和 confirmation fact，解决后才允许 batch 完成并解除 settlement block。Settlement 后续读取的是 current effective run，而不是任意一个历史 run。

## 四、Merchant Settlement：从可采用事实形成冻结组成

阅读 [MerchantSettlementReferenceApplicationTests.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/MerchantSettlementReferenceApplicationTests.kt#L74) 与 [MerchantSettlementBehaviorTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/merchant_settlement/MerchantSettlementBehaviorTest.kt#L35)。Settlement prepare 只选择成功、周期内、当前 effective reconciliation 已匹配、未被其他有效结算消费且没有 blocker 的事实。

每条 settlement line 至少应能回溯：

- `sourceFactIdentity`：`PAYMENT:<id>` 或 `REFUND:<id>`；
- reconciliation batch/run/item identity；
- 外部交易 identity、gross、fee、signed net；
- `eligibilityBasis`，说明为什么当前 line 可以进入结算。

金额恒等式为：

```text
payment revenue - successful refunds - fees + confirmed adjustments = net
```

确认后 `compositionFrozen=true`，之后费率变化不能改写历史 line。正净额进入执行；零净额直接形成 settled fact 但不创建 transfer attempt；负净额进入 review，不得划拨。

## 五、执行、结果与事件：成功不是最后一个边界

Settlement execution 的 `PROCESSING` 和 provider accepted 只说明尝试已建立或被渠道接收。可信 SUCCESS callback 才形成 `settledFact` 和唯一 completion event。相同 callback 是 `ACCEPTED_DUPLICATE`；相同 identity 的载荷改变或成功后的 FAILED 是 `CONFLICT`，Settlement 保持 `SUCCEEDED`，Attempt 转 review/conflict 状态，历史 receipt 继续保留。

UNKNOWN 结果必须保留 Attempt 和 request identity，禁止通过新 identity 重试。达到复核阈值后，授权裁决追加 `MANUAL_ADJUDICATION` receipt，才把结果收敛为成功或失败。void/replacement 只允许在确认前调整，并通过 predecessor/replacement 和 effective ownership 保证同时有效的结算至多一个。

`PAY-AC-088` 的回滚测试把 settled fact 和 outbound event record 放在同一 UoW：subscriber 故障时二者一起回滚；HTTP non-2xx/timeout 时使用同一 event identity 重试。这里证明的是本地事务与 at-least-once handoff 的边界，不是下游已完成 exactly-once。

## 跨聚合证据：如何证明是一条 durable composition trail

`MerchantSettlementReferenceApplicationTests#payment refund reconciliation and settlement preserve one durable composition trail`（[L204](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/MerchantSettlementReferenceApplicationTests.kt#L204)）是综合阅读的核心样本。它在同一 H2 数据库中：

1. 创建成功 Payment；
2. 创建并成功确认部分 Refund；
3. 读取渠道 statement，形成 `COMPLETED` 且无差异的 effective reconciliation run；
4. prepare settlement，得到 Payment line `+98.00` 和 Refund line `-20.00`，净额 `78.00`；
5. 回读每条 line 的 Payment/Refund/Reconciliation source identity；
6. 执行可信 SUCCESS callback；
7. 回读唯一 settled fact 和 completion event 的 `eventIdentity`、`correlationIdentity`、`causationIdentity`。

这个测试证明的是“事实可以沿持久化图回溯”，不是把四个聚合合并成一个大聚合。每个聚合仍维护自己的状态、历史 evidence 和不变量；跨聚合一致性由 application/UoW、source identity 和查询回读共同证明。

## 全链路阻断规则

读完整链路时，优先检查阻断是否正确传播，而不是只看 happy path：

- Payment 未形成成功事实、`RESULT_UNKNOWN`、held review 或 conflict：Refund 入口不应放行，Settlement 不应采用；
- Refund UNKNOWN 或 review：退款预算仍被占用，对账和结算应看到未收敛事实；
- Reconciliation unresolved、INCOMPLETE statement 或 Payment review snapshot：batch/item 设置 `settlementBlocked`，Settlement 排除该候选；
- Settlement composition 已确认后再出现冲突：追加 receipt/evidence，保持终态并进入 review，不重复划拨；
- outbound event handoff 失败：本地 settled fact 与 event record 的 UoW 语义必须可解释，重试使用稳定 identity。

这些阻断不是额外的异常分支，而是“不能把尚未收敛的事实当成已完成事实”的统一规则。

## 测试证明了什么，未证明什么

**已证明**

- Payment、Refund、Reconciliation、Settlement 各自的领域状态、不变量、幂等和追加式 evidence 在 reference 工程中可复核。
- 三组 application tests 能在 H2/JPA 中回读一条从 Payment 到 Settlement 的 durable composition trail，包括金额、source identity、review/blocker、receipt 和事件记录。
- 事务级并发、revision/effective ownership、跨聚合回滚和稳定 event identity 具有针对性测试证据。
- Evidence catalog 和 Traceability contract 能把 PAY-AC、PAY-BR、测试方法和状态映射起来，并保留 planned/not-built 边界。

**未证明**

- H2/Fake Provider 不证明生产银行、清算机构、签名/证书、网络分区、真实异步延迟或渠道补偿协议。
- 事务级并发不证明跨实例 exactly-once、压力性能、分布式锁、broker 顺序或通用 Inbox/Outbox。
- 测试 receiver、普通 scheduler 和静态 Analyzer/Flow 不证明生产通知、durable scheduler、运行态跨入口 stitching。
- 最小 operator guard 不证明完整认证授权、RBAC、双人复核、租户隔离或生产审计闭环。
- `PAY-AC-080/081/084/086` 仍是 planned/not-built，不能因为主链 composition 已验证就宣称这些能力存在。

## 阅读检查点（非门槛）

1. Payment success fact、Refund successful amount、Reconciliation effective run、Settlement settled fact 分别解决什么问题？
2. 为什么 Refund 的 reservation 不能只靠最终 Refund 状态推导？
3. 为什么 `MATCHED` 仍可能被 review/blocker 排除出 Settlement？
4. 哪些 source identity 让一条 settlement line 可以回溯到 Payment、Refund 和 Reconciliation item？
5. 结算 composition freeze 如何防止费率变化重写历史金额？
6. 哪些结果是 duplicate，哪些结果必须变成 conflict/review？
7. 本地 UoW 回滚与 outbound event retry 各自证明到哪一层？
8. 49 个 verified 与 4 个 planned/not-built 的边界，为什么不能用“测试数量”抹平？

## 综合结论

验收整个支付项目时，最可靠的叙述不是“所有接口都通过了”，而是：Payment 先形成一次可信成功事实；Refund 在预算不变量下追加退款事实；Reconciliation 用双侧快照和 effective revision 判断哪些事实可采用；Settlement 冻结可采用组成并只形成一次结算事实；冲突、未知、迟到和交接失败都保留证据、阻断不安全的下游动作，并通过稳定身份等待收敛。

这条叙述同时包含能力和边界：reference 工程已经用代码和自动化测试证明了哪些资金事实与状态关系，也明确指出生产渠道、通知、调度、认证、租户隔离和分布式 exactly-once 仍不能从这些测试中推导出来。
