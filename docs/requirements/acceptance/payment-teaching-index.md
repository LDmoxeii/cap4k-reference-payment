# Payment Reference 阅读式教学索引

> 这是一组可以脱离主会话独立阅读的文章入口。每篇文章都带有业务上下文、全链路位置、文件与测试阅读路线、关键断言、证明边界和总结；不要求读者先自行探索仓库，也不要求先预测 Given / When / Then。

## 使用方式

先把本索引当作地图，再打开当前模块的文章。阅读时按文章给出的文件、测试类和方法顺序走，测试命令只作为可选材料；可以只读源码和已有报告，也可以自行运行测试。文末检查点用于自检，不是进入下一篇文章的门槛。

每篇文章都回答五个问题：

1. 本课要解决的业务问题是什么，它在资金事实链的哪一段？
2. 真实业务入口、状态变化和历史事实分别在哪里？
3. 哪些断言证明了当前结论？
4. 这些证据没有证明什么，哪些能力仍是边界或缺口？
5. 下一篇文章会复用哪些事实和术语？

## 全链路地图

```text
Payment
  → Refund
  → Reconciliation
  → Merchant Settlement
```

这不是四个彼此独立的 CRUD 模块，而是一条资金事实链：Payment 形成可确认的收款成功事实；Refund 使用该事实和退款预算；Reconciliation 对比平台与渠道的事实快照；Merchant Settlement 从可结算候选和冻结后的组成形成结算执行。异常结果、review、冲突 evidence 和 settlement block 会沿链路影响下游资格，但不会把上游历史事实静默覆盖。

## 当前验收状态

- 总场景数：`53`
- 当前 `verified`：`49`
- 当前 `planned/not-built`：`4`
- planned/not-built 场景：`PAY-AC-080`、`PAY-AC-081`、`PAY-AC-084`、`PAY-AC-086`

这里的 `verified` 表示项目已经有可定位、可复核的 reference 证据，不表示生产银行或清算网络、生产商户通知、跨实例 exactly-once、完整认证授权、压力与网络分区可靠性已经完成。四个 planned 场景只说明需求和当前缺口，不能在教学中被升级为已实现能力。

## 模块路线

### M0：证据阅读方法

建立“测试是证据，不是业务需求本身”的阅读方法，区分 Domain behavior、Application/JPA/HTTP、Adapter/contract、Evidence guard 和 Analyzer/Flow 各自能证明的范围。

入口文档和测试：

- [payment-teaching-context.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-context.md)
- [payment-teaching-syllabus.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-syllabus.md#m0证据阅读方法)
- [payment-test-evidence-catalog.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-test-evidence-catalog.md)
- `PaymentTestEvidenceCatalogContractTests`、`AcceptanceGuideContractTests`、`TraceabilityContractTests`

用户已选择跳过 M0；需要回看证据方法时再打开这些材料即可。

### M1：Payment 主链

理解一笔 Payment 如何从收款意图走到渠道受理、可信成功、成功事实、手续费快照、通知意图和持久化查询。基准状态链是：

```text
PENDING → PROCESSING → SUCCEEDED
```

对应场景：`PAY-AC-001`–`PAY-AC-005`、`PAY-AC-012`–`PAY-AC-014`、`PAY-AC-016`。

独立文章：[payment-teaching-m1-payment-main-chain.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-m1-payment-main-chain.md)

核心证据：`PaymentReferenceApplicationTests`、`PaymentBehaviorTest`、`ConfirmPaymentResultCmdContractTest`、Payment endpoint/HTTP configuration contract。

### M2：Payment 异常与收敛

理解不可信、重复、迟到、矛盾、未知和并发结果为什么要追加 receipt/evidence、创建稳定 review、保护终态并阻断自动结算，而不是覆盖旧状态。

对应场景：`PAY-AC-006`–`PAY-AC-011`、`PAY-AC-015`、`PAY-AC-017`。

独立文章：[payment-teaching-m2-payment-exception-and-convergence.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-m2-payment-exception-and-convergence.md)

核心证据：`PaymentBehaviorTest`、`PaymentReferenceApplicationTests`、`PaymentExpirySchedulerStructureTest`、`AdjudicatePaymentReviewCmdContractTest`。

### M3：Refund 生命周期和退款预算

文章主题：已确认成功的 Payment 如何提供退款资格和预算；Refund/Attempt 如何处理全额、部分、超额、窗口、并发、失败、未知和 callback；`reservedRefundAmount`、`successfulRefundAmount`、`refundableAmount` 如何保持一致。

阅读路线：`payment-scenarios.md` 退款部分、`payment-rules.md` 退款规则、`PaymentReferenceApplicationTests` 退款测试、`RefundResultRecordingOutcomeTest`、Refund endpoint/HTTP configuration contract。

对应场景：`PAY-AC-020`–`PAY-AC-029`。

独立文章：[payment-teaching-m3-refund-lifecycle-and-budget.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-m3-refund-lifecycle-and-budget.md)

### M4：Reconciliation

文章主题：对账如何同时保留平台事实和渠道 statement 快照，如何表达 `MATCHED`、差异、revision、effective run、人工 disposition 与 confirmation，并通过追加式证据维持历史可追溯性。

阅读路线：`payment-lifecycle.md` 对账生命周期、`ReconciliationBatchBehaviorTest`、`ReconciliationReferenceApplicationTests`、reconciliation endpoint contract、`DailyReconciliationSchedulerStructureTest`、inbound event tests。

对应场景：`PAY-AC-040`–`PAY-AC-047`、`PAY-AC-082`、`PAY-AC-085`、`PAY-AC-087`。

独立文章：[payment-teaching-m4-reconciliation.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-m4-reconciliation.md)

### M5：Merchant Settlement

文章主题：结算如何从可结算候选形成 line，冻结 gross/fee/net 组成，通过 execution、review、结果通知和 replacement 收敛，并记录可靠事件事实。

阅读路线：`payment-lifecycle.md` 结算生命周期、`MerchantSettlementBehaviorTest`、`MerchantSettlementReferenceApplicationTests`、settlement endpoint contract、scheduler structure、Integration Event contract。

对应场景：`PAY-AC-060`–`PAY-AC-068`，并回看 `PAY-AC-014`、`PAY-AC-083`、`PAY-AC-085`、`PAY-AC-088`。

独立文章：[payment-teaching-m5-merchant-settlement.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-m5-merchant-settlement.md)

### M6：HTTP / Event / Analyzer / 缺口边界

文章主题：区分业务行为证据、入口契约、普通 `@Scheduled` 的结构限制、Integration Event 的交接语义、Analyzer/Flow 的静态性质，以及四个 planned/not-built 场景为什么不能被误报为 verified。

阅读路线：Payment/Refund/Reconciliation/Settlement endpoint 与 HTTP configuration contract、`PaymentHttpErrorAdviceTest`、`UserVisibleMessageContractTests`、scheduler structure tests、Integration Event contract、`TraceabilityContractTests`。

重点场景：`PAY-AC-080`、`PAY-AC-081`、`PAY-AC-084`、`PAY-AC-086`；并串联 `PAY-AC-087`、`PAY-AC-088`。

独立文章：[payment-teaching-m6-http-event-analyzer-and-gaps.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-m6-http-event-analyzer-and-gaps.md)

### M7：全链路综合理解

文章主题：把四个业务族放回同一条资金事实链，追踪 Payment 成功事实如何进入 Refund、Reconciliation 和 Settlement，同时区分只追加的历史 evidence、会阻断下游的 review/unresolved，以及不能外推的 reference 环境能力。

建议主链：

```text
PAY-AC-001
  → PAY-AC-004
  → PAY-AC-020/021
  → PAY-AC-040
  → PAY-AC-060
  → PAY-AC-063
  → PAY-AC-083
  → PAY-AC-088
```

核心材料：`PaymentReferenceApplicationTests`、`ReconciliationReferenceApplicationTests`、`MerchantSettlementReferenceApplicationTests`、证据目录及四个 planned 场景的边界说明。

独立文章：[payment-teaching-m7-full-chain-synthesis.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-m7-full-chain-synthesis.md)

## 文章与主会话的关系

文章是主会话的稳定载体：每篇都包含足够上下文，可以在较长时间间隔后单独打开阅读，不依赖前一轮问答缓存。主会话只需要做三件事：说明当前阅读主题、在需要时回答对某个类/方法/断言的追问、最后把当前理解连接到下一篇文章。测试运行始终由学习者自行决定，不作为课程开始、完成或继续的硬条件。

## 当前阅读建议

从 [M1：Payment 主链阅读文章](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-m1-payment-main-chain.md) 开始，按 M1 → M2 → M3 → M4 → M5 → M6 → M7 逐篇推进；每篇阅读中若某个断言、状态或类需要展开，直接在侧边聊天中针对该符号追问即可，不需要重新讲整篇课程。
