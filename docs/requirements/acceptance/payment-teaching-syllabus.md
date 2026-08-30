# Payment Reference 教学大纲

> 本大纲服务于后续阅读式教学。教师负责给出方向、阅读顺序和解释；学习者按路线阅读代码和测试，是否运行测试自行决定。PAY-AC 只作为追踪索引，不要求机械地逐条讲解。

## 1. 总体路线

```text
M0 证据阅读方法
  → M1 Payment 主链
  → M2 Payment 异常与收敛
  → M3 Refund
  → M4 Reconciliation
  → M5 Merchant Settlement
  → M6 HTTP / Event / Analyzer / 缺口边界
  → M7 全链路综合理解
```

推荐先走完主链，再回看复杂异常；不从 PAY-AC-001 到 PAY-AC-088 机械排序。

## 2. 模块总表

| 模块 | 教学主题 | PAY-AC 追踪范围 | 主测试入口 |
|---|---|---|---|
| M0 | 如何从测试读取业务证据 | 横切，不新增场景 | `PaymentTestEvidenceCatalogContractTests`、`AcceptanceGuideContractTests`、`TraceabilityContractTests` |
| M1 | Payment 创建、受理、成功和基础幂等 | 001–005、012–014、016 | `PaymentReferenceApplicationTests`、`PaymentBehaviorTest` |
| M2 | Payment 异常、到期、复核、冲突和并发 | 006–011、015、017 | `PaymentReferenceApplicationTests`、`PaymentBehaviorTest` |
| M3 | Refund 生命周期和退款预算 | 020–029 | `PaymentReferenceApplicationTests`、`RefundResultRecordingOutcomeTest` |
| M4 | 对账快照、差异、revision 和追加式处置 | 040–047、082、085、087 | `ReconciliationReferenceApplicationTests`、`ReconciliationBatchBehaviorTest` |
| M5 | 结算组成、冻结、执行和结果收敛 | 060–068、014、083、085、088 | `MerchantSettlementReferenceApplicationTests`、`MerchantSettlementBehaviorTest` |
| M6 | HTTP、Scheduler、Integration Event、Analyzer 与 planned 边界 | 080、081、084、086，并串联 087、088 | Adapter contract tests、`TraceabilityContractTests`、`UserVisibleMessageContractTests` |
| M7 | Payment → Refund → Reconciliation → Settlement 综合追踪 | 001、004、020/021、040、060、063、083、088 | 三组 Application tests 加证据目录 |

## 3. 模块阅读路线

### M0：证据阅读方法

**要理解什么**

先建立“测试是证据，不是业务需求本身”的阅读方法，知道 Domain、Application/JPA/HTTP、Adapter/contract、Evidence guard 和 Analyzer/Flow 各自能证明什么。

**先看什么**

1. [payment-test-evidence-catalog.md](payment-test-evidence-catalog.md) 的“证据层次”和“主测试与辅助测试索引”；
2. [PaymentTestEvidenceCatalogContractTests.kt](../../../start/src/test/kotlin/com/only4/cap4k/reference/payment/PaymentTestEvidenceCatalogContractTests.kt)；
3. [AcceptanceGuideContractTests.kt](../../../start/src/test/kotlin/com/only4/cap4k/reference/payment/AcceptanceGuideContractTests.kt)；
4. [TraceabilityContractTests.kt](../../../start/src/test/kotlin/com/only4/cap4k/reference/payment/TraceabilityContractTests.kt)。

**教师重点**

解释如何从 PAY-AC 跟到 PAY-BR、traceability、设计入口、精确测试方法和持久化观察点；同时明确 `verified` 不等于生产能力完成。

### M1：Payment 主链

**要理解什么**

从创建 Payment 到渠道受理、可信成功、查询回读，建立后续所有异常场景都要回到的基准主链。

**先看什么**

1. [payment-scenarios.md](payment-scenarios.md) 的支付创建与处理部分；
2. [payment-lifecycle.md](../business/payment-lifecycle.md) 的支付生命周期；
3. [PaymentReferenceApplicationTests.kt](../../../start/src/test/kotlin/com/only4/cap4k/reference/payment/PaymentReferenceApplicationTests.kt) 的创建、attempt、confirm、查询主链；
4. [PaymentBehaviorTest.kt](../../../domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/PaymentBehaviorTest.kt) 的成功事实和状态语义；
5. `ConfirmPaymentResultCmdContractTest` 与 Payment HTTP configuration contract。

**重点观察**

Payment/Attempt 状态、Gateway ACCEPTED 与最终成功的区别、幂等键、成功事实、fee snapshot、通知意图和 JPA 回读。

**对应场景**

`PAY-AC-001`–`PAY-AC-005`、`PAY-AC-012`–`PAY-AC-014`、`PAY-AC-016`。

### M2：Payment 异常与收敛

**要理解什么**

理解“不可信、重复、迟到、矛盾、未知和并发”为什么不能简单覆盖原状态，而要追加证据并收敛到可处理结果。

**先看什么**

1. `PaymentBehaviorTest` 的 mismatch、duplicate、late、unknown、review 和不可回退测试；
2. `PaymentReferenceApplicationTests` 的到期、review、重复订单、并发和查询测试；
3. `PaymentExpirySchedulerStructureTest` 与 `AdjudicatePaymentReviewCmdContractTest`；
4. 证据目录中 PAY-AC-006–011、015、017 的证明边界。

**重点观察**

拒绝 receipt、重复接收计数、冲突 evidence、stable review identity、终态保护、订单级成功竞争、尝试级成功事实和结算阻断。

**对应场景**

`PAY-AC-006`–`PAY-AC-011`、`PAY-AC-015`、`PAY-AC-017`。

### M3：Refund 生命周期和退款预算

**要理解什么**

理解退款不是简单的金额扣减，而是由 Payment 的退款预算、Refund/Attempt 状态、callback 证据和失败/未知处置共同决定。

**先看什么**

1. [payment-scenarios.md](payment-scenarios.md) 的退款部分；
2. [payment-rules.md](../business/payment-rules.md) 的退款资格和预算规则；
3. [PaymentReferenceApplicationTests.kt](../../../start/src/test/kotlin/com/only4/cap4k/reference/payment/PaymentReferenceApplicationTests.kt) 的全额、部分、超额、窗口、并发、失败、未知和 callback 测试；
4. [RefundResultRecordingOutcomeTest.kt](../../../domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/refund/RefundResultRecordingOutcomeTest.kt)；
5. Refund endpoint/HTTP configuration contract。

**重点观察**

`reservedRefundAmount`、`successfulRefundAmount`、`refundableAmount`，以及 Refund attempt、receipt、review、冲突和跨聚合事务回滚。

**对应场景**

`PAY-AC-020`–`PAY-AC-029`。

### M4：Reconciliation

**要理解什么**

理解对账如何保留平台事实和渠道 statement 的双侧快照，如何处理差异、revision、effective run、人工 disposition 和 confirmation。

**先看什么**

1. [payment-lifecycle.md](../business/payment-lifecycle.md) 的对账生命周期；
2. [ReconciliationBatchBehaviorTest.kt](../../../domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/reconciliation_batch/ReconciliationBatchBehaviorTest.kt)；
3. [ReconciliationReferenceApplicationTests.kt](../../../start/src/test/kotlin/com/only4/cap4k/reference/payment/ReconciliationReferenceApplicationTests.kt)；
4. reconciliation endpoint contract、`DailyReconciliationSchedulerStructureTest` 和 inbound event 测试；
5. 证据目录中 PAY-AC-040–047、082、085、087 的边界说明。

**重点观察**

MATCHED、PLATFORM_ONLY、CHANNEL_ONLY、AMOUNT_MISMATCH、unresolved、revision/effective ownership、追加式证据、时区半开区间和 Pull 权威性。

**对应场景**

`PAY-AC-040`–`PAY-AC-047`、`PAY-AC-082`、`PAY-AC-085`、`PAY-AC-087`。

### M5：Merchant Settlement

**要理解什么**

理解结算如何从可结算候选形成 line，冻结手续费和组成，计算净额，再通过 execution、review、结果通知和 replacement 收敛。

**先看什么**

1. [payment-lifecycle.md](../business/payment-lifecycle.md) 的结算生命周期；
2. [MerchantSettlementBehaviorTest.kt](../../../domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/merchant_settlement/MerchantSettlementBehaviorTest.kt)；
3. [MerchantSettlementReferenceApplicationTests.kt](../../../start/src/test/kotlin/com/only4/cap4k/reference/payment/MerchantSettlementReferenceApplicationTests.kt)；
4. settlement endpoint contract、scheduler structure 和 Integration Event contract；
5. 证据目录中 PAY-AC-060–068、014、083、085、088 的证明边界。

**重点观察**

candidate、line、gross、fee、net，composition freeze，effective settlement，execution attempt，UNKNOWN/review，zero/negative net，void/replacement 和可靠事件记录。

**对应场景**

`PAY-AC-060`–`PAY-AC-068`，并回看 `PAY-AC-014`、`PAY-AC-083`、`PAY-AC-085`、`PAY-AC-088`。

### M6：横切入口、Analyzer 与缺口

**要理解什么**

区分业务行为证据、入口契约、静态 Flow 和未实现能力，避免把局部机制误认为完整产品能力。

**先看什么**

1. Payment、Refund、Reconciliation、Settlement 的 endpoint/HTTP configuration contract；
2. `PaymentHttpErrorAdviceTest`、`UserVisibleMessageContractTests`；
3. Scheduler structure tests；
4. Integration Event contract 和 `TraceabilityContractTests`；
5. 证据目录的 planned/not-built 与“不能证明”部分。

**重点观察**

HTTP status/code/message 边界、普通 `@Scheduled` 的限制、at-least-once handoff、Analyzer Flow 的静态性质，以及四个 planned 场景不能升级为 verified 的原因。

**对应场景**

重点为 `PAY-AC-080`、`PAY-AC-081`、`PAY-AC-084`、`PAY-AC-086`；串联 `PAY-AC-087`、`PAY-AC-088`。

### M7：全链路综合理解

**要理解什么**

把四个业务族放回同一条资金事实链，确认上游事实如何被对账和结算引用，同时保留各自聚合的历史事实和证据边界。

**先看什么**

1. 证据目录的主链冒烟顺序；
2. `PaymentReferenceApplicationTests` 的支付和退款回读；
3. `ReconciliationReferenceApplicationTests` 的匹配和 effective run；
4. `MerchantSettlementReferenceApplicationTests` 的 composition trail；
5. PAY-AC-083、PAY-AC-088 及四个 planned 场景的最终边界。

**重点观察**

Payment 成功事实如何进入 Refund、Reconciliation 和 Settlement；哪些事实只追加不覆盖；哪些 review 或 unresolved 会阻断结算；哪些 reference 证据不能外推到生产环境。

**对应主链**

`PAY-AC-001 → PAY-AC-004 → PAY-AC-020/021 → PAY-AC-040 → PAY-AC-060 → PAY-AC-063 → PAY-AC-083 → PAY-AC-088`。

## 4. 模块完成标准

每个模块完成时，学习者不需要背出所有代码，但应能：

- 说清本模块的业务目的和它在全链路中的位置；
- 根据路线找到主测试和辅助测试；
- 解释主要状态、事实和持久化断言；
- 指出当前证据的边界，以及哪些内容仍是 planned 或不能宣称；
- 对教师提出的针对性问题给出基本正确的回答。

是否重新运行测试由学习者决定，不作为模块完成的硬条件。最终综合理解以“能沿证据链讲清业务和边界”为准，而不是以重复执行命令的数量为准。
