# M5：Merchant Settlement 结算组成、执行与结果收敛

> 本文是面向阅读式教学的自包含材料。目标是沿对账后的可采用事实，理解结算如何形成可追溯的 line、冻结金额组成、执行渠道划拨，并在成功、失败、UNKNOWN、冲突和替换之间保持单一有效结算事实。

## 固定上下文胶囊

- 工程：`cap4k-reference-payment`；完整资金事实链为 `Payment → Refund → Reconciliation → Merchant Settlement`。
- 本课范围：结算候选筛选、gross/fee/net 组成、composition freeze、确认、执行 Attempt、callback 幂等与冲突、UNKNOWN 复核、零/负净额、void/replacement、完成事件与可靠交接。
- 对应验收场景：`PAY-AC-060`–`PAY-AC-068`，并回看 `PAY-AC-014`、`PAY-AC-083`、`PAY-AC-085`、`PAY-AC-088`。
- 当前证据状态：这些场景在证据目录中有 `verified` reference 证据；这不表示生产银行划拨、生产 outbox、跨实例 exactly-once、完整 RBAC 或真实清算网络已完成。
- 测试边界：应用测试使用 H2、Fake Provider、测试 event receiver 和真实 Command/UoW；领域测试证明金额和状态不变量，不能单独证明 HTTP/JPA wiring 或生产交接语义。

## 业务问题与全链路位置

Settlement 不是把某个 Payment 的金额直接打给商户，而是从一个结算周期内所有“成功、已对账、未被其他有效结算消费、没有阻断事实”的候选中，形成一组可追溯组成。其核心恒等式是：

```text
payment revenue
  - successful refunds
  - settlement fees
  + confirmed adjustments
  = settlement net amount
```

```text
Payment success fact + Refund fact
  → current effective Reconciliation match
  → MerchantSettlement candidate / line / frozen composition
  → execution attempt + channel result
  → settled fact + completion event
```

结算必须同时回答两个问题：金额是否可解释，划拨结果是否可收敛。组成确认后不能被配置变化静默改写；执行结果 UNKNOWN 不能通过换一个 request identity 规避；成功后的失败或载荷冲突只能追加 evidence 并进入复核，不能撤销已形成的 settled fact。

## 阅读路线

1. [payment-teaching-context.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-context.md)：确认全链路、证据层次和测试可选原则。
2. [payment-teaching-syllabus.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-syllabus.md#m5merchant-settlement)：确认 M5 的主题、场景范围和重点观察项。
3. [payment-lifecycle.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/business/payment-lifecycle.md#4-商户结算生命周期)：阅读结算形成范围、状态、金额构成和未知结果规则。
4. [MerchantSettlementBehaviorTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/merchant_settlement/MerchantSettlementBehaviorTest.kt#L35)：先看正/零/负净额、成功幂等、冲突、UNKNOWN、替换和最小权限守卫。
5. [MerchantSettlementReferenceApplicationTests.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/MerchantSettlementReferenceApplicationTests.kt#L74)：再看真实 Payment + Refund + Reconciliation → prepare → confirm → execute → callback → GET 回读。
6. 同一应用测试中的 `payment refund reconciliation and settlement preserve one durable composition trail`（[L204](/c:/Users/LD_moxeii/Documents/code/only4/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/MerchantSettlementReferenceApplicationTests.kt#L204)）和 `unknown result blocks retry until review and manual adjudication`（[L325](/c:/Users/LD_moxeii/Documents/code/only4/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/MerchantSettlementReferenceApplicationTests.kt#L325)）。
7. 最后查看 settlement endpoint/scheduler/Integration Event contract 和 [payment-test-evidence-catalog.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-test-evidence-catalog.md) 的 `PAY-AC-060`–`068`、`083`、`085`、`088`。

## 按业务时间顺序阅读结算链

### 1. 从 effective reconciliation 事实形成候选和 line

应用主测试 `merchant settlement lifecycle produces net 127 and preserves callback evidence`（[MerchantSettlementReferenceApplicationTests.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/MerchantSettlementReferenceApplicationTests.kt#L74)）先准备两个成功 Payment（`100.00 + 50.00`）、一笔成功 Refund（`20.00`）和匹配的对账 statement。`prepare` 返回 `PREPARED`，断言 `eligibleCount=3`、`paymentGrossAmount=150.00`、`refundGrossAmount=20.00`、`feeTotalAmount=3.00`、`netAmount=127.00`，并持久化三条 line。

每条 line 不是孤立金额：它保留 `sourceFactIdentity`、Payment/Refund identity、reconciliation batch/run/item、外部交易身份、gross、fee、signedNet 和 `eligibilityBasis`。因此 Settlement 可以解释“这 127.00 来自哪些上游事实”，而不是只保存一个总数。

同一结算业务身份再次 prepare 时返回原 `settlementId`，`idempotentReplay=true`。这证明重复准备不会产生第二个同时有效的结算单。

### 2. 确认时冻结 composition，配置变化不能改写历史

领域测试 `confirmation freezes positive composition while zero completes without transfer and negative remains review-only`（[MerchantSettlementBehaviorTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/merchant_settlement/MerchantSettlementBehaviorTest.kt#L35)）断言正净额确认后 `compositionFrozen=true`、记录 `confirmedBy`，但尚未形成 settled fact。

应用测试 `configuration changes do not rewrite frozen payment fees or settlement lines` 进一步把 Payment 成功时冻结的费率带入结算 line：即使商户配置从 200 basis points 改为 800，历史 Payment fee 仍是 `2.00`，line 的 `feeBasisPoints=200`、舍入模式和计算金额保持原快照。冻结保护的是历史资金事实，而不是阻止未来新交易使用新配置。

### 3. 零净额和负净额是不同的业务结果

零净额确认后直接进入 `SUCCEEDED`，形成 settled fact 和 completion event，但没有 execution attempt，也不能调用 transfer provider；再次 `startAttempt` 会因“没有可划拨的正净额”被拒。

负净额确认后进入 `NEGATIVE_REVIEW_REQUIRED`，不能执行划拨。应用测试 `negative and zero net settlements never invoke transfer provider` 回读负净额 settlement 的 line 和状态，确认没有 attempt，执行请求返回 `INVALID_REQUEST`。因此“零”是无需划拨的完成，“负”是需要人工处理的阻断，不能把两者都当作普通成功。

### 4. 执行 Attempt、渠道受理与可信成功

确认后的正净额通过执行入口创建 Attempt，状态为 `PROCESSING`，响应保留 `executionGroupIdentity` 和 `requestIdentity`；provider 返回 accepted 只说明执行请求被受理，不等于 settled fact。

可信 `SUCCESS` callback 返回 `SUCCESS_ACCEPTED` 和 `settledFactFormedNow=true`，Settlement 变为 `SUCCEEDED`，Attempt 留下外部 identity、receipt 和成功时间，并只形成一个 `MerchantSettlementCompletedDomainEvent`。应用主链在同一测试中同时断言净额、line/source identity、Attempt/receipt、settled fact 和完成事件数量。

### 5. 重复 callback 和矛盾结果：追加 evidence，不重复划拨

领域测试 `first verified success forms one settled fact and exact replay only increments receipt counters`（[MerchantSettlementBehaviorTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/merchant_settlement/MerchantSettlementBehaviorTest.kt#L65)）证明相同通知 identity + fingerprint 的精确重放返回 `ACCEPTED_DUPLICATE`，`settledFactFormedNow=false`，receipt `receiveCount=2`，完成事件仍只有 1 个。

若相同 identity 的 payload 改变，或成功后收到新的 `FAILED`，领域测试 `same notification with another payload and late opposite final result are conflicts without success rollback` 将两者都判为 `CONFLICT`：Settlement 仍为 `SUCCEEDED`，Attempt 转为 `CONFLICT_REVIEW_REQUIRED`，原 `finalResult=SUCCESS`，两个 receipt 和两个冲突计数都保留，完成事件不重复。

### 6. UNKNOWN：禁止换身份重试，等待稳定复核

`unknown result blocks retry until review and manual adjudication`（[MerchantSettlementReferenceApplicationTests.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/MerchantSettlementReferenceApplicationTests.kt#L325)）先接收 `UNKNOWN`，Settlement 变为 `RESULT_UNKNOWN`，Attempt 的 `finalResult=UNKNOWN`。随后用新的执行请求重试会返回 409；系统不能因为结果未知就再创建一笔等额划拨。

当达到 `reviewAfterAt`，scheduler/Command 将 Attempt 标为 `REVIEW_REQUIRED`。授权 operator 通过 `AdjudicateMerchantSettlementResultCmd` 提供最终结果和证据，成功裁决追加 `MANUAL_ADJUDICATION` receipt，形成 settled fact，Settlement 进入 `SUCCEEDED`，完成事件仍只有一个。重复裁决会被拒绝。

### 7. 未确认结算的 void/replacement 与 effective ownership

未确认的结算可以 `RETURN_FOR_ADJUSTMENT` 或 void，再创建带 predecessor/replacement 链的新结算。领域测试 `unconfirmed settlement can return for adjustment and link a fresh predecessor chain` 断言旧结算为 `VOIDED`，清除 `effectiveScopeIdentity` 与 line consumption identity，同时保留 predecessor/replacement 关系；确认后的结算不能再退回调整。

`replacement activation claims canonical effective ownership idempotently` 证明 replacement 激活 effective ownership 是幂等的：重复激活保持同一 `effectiveScopeIdentity` 和 line consumption identity；void 后不能再次激活。应用测试进一步证明 prepare replay 返回同一个 replacement，旧结算不会与新结算同时有效。

### 8. 完成事件与可靠交接

`PAY-AC-088` 的应用测试让 completion subscriber 在事件入队后故障，随后回读 Settlement 仍为 `PROCESSING`、`settledFactFormed=false`、Attempt 仍处理中且完成事件记录数为 0，证明 completion 与 durable event record 在同一 UoW 中回滚，不留下孤儿成功事实。

当 outbound HTTP 返回 non-2xx 或超时，重试使用相同 event identity/UUID 和稳定 payload，delivery attempt 递增；这证明当前 reference 实现具备可追踪的 at-least-once handoff。它不等于下游已经 exactly-once 处理。

## 关键断言速查

- 组成：`payment revenue - refunds - fees + adjustments = net`，每条 line 都能回溯上游 source identity。
- 冻结：确认后 `compositionFrozen=true`；Payment 成功时 fee snapshot 进入 line，后续配置变化不重算历史金额。
- 资格：只有当前 effective reconciliation 中 resolved 且未阻断的事实进入候选；review/unresolved 会排除或阻断。
- 执行：provider accepted/Attempt `PROCESSING` 不等于 settled fact；可信 callback 才形成 `settledFact`。
- 幂等：相同 callback 只增加 receive count；相同 identity 的 payload 改变或成功后失败会形成 `CONFLICT`。
- UNKNOWN：结果未知时禁止换身份重试；达到阈值后需授权裁决，裁决证据追加且只形成一次完成事件。
- 金额边界：零净额直接完成但不划拨；负净额进入 review，不能执行。
- 替换：未确认结算可 void/replacement；effective ownership 和 line consumption identity 至多一份。

## 测试证明了什么，未证明什么

**已证明**

- `PAY-AC-060`–`068`、`083`、`085`、`088` 覆盖候选筛选、净额恒等式、组成冻结、执行状态、成功/重复/冲突/UNKNOWN、零/负净额、void/replacement、时区和可靠事件记录。
- 领域测试证明金额、状态、receipt、settled fact、review 和 effective ownership 的不变量；应用测试证明真实 Payment/Refund/Reconciliation 组成能在 H2/JPA 中回读为可追溯 settlement line。
- 完成事件与业务状态在当前 UoW 中共同提交或共同回滚；失败 handoff/timeout 保持稳定 event identity 并可重试。

**未证明**

- Fake Provider/H2 不证明生产银行划拨、清算网络、证书/签名、网络分区、真实 provider 撤销或补偿协议。
- at-least-once HTTP handoff 不证明下游消费 exactly-once、通用 outbox、broker 广播或跨实例一致性。
- 普通 scheduler 只证明 Command 入口结构，不证明 durable scheduler、重启恢复或人工审核 SLA。
- 最小 operator role guard 不等于完整 RBAC、双人复核、租户隔离和生产审计闭环。
- 结算测试没有证明资金已在真实商户账户到账；它证明的是 reference 工程内的 settled fact、composition trail 和可追踪交接。

## 阅读检查点（非门槛）

1. 为什么结算必须从 current effective reconciliation 事实生成 line，而不是直接扫描所有成功 Payment？
2. `compositionFrozen` 保护的是什么？它与实时读取费率有什么区别？
3. 为什么零净额可以直接完成，负净额却必须进入 review？
4. provider accepted、Attempt `PROCESSING`、`settledFactFormedNow=true` 分别证明哪一层事实？
5. UNKNOWN 为什么不能通过新 request identity 重试？
6. void/replacement 如何保证旧结算不再有效但历史链仍可追踪？
7. `PAY-AC-088` 的 UoW 回滚证明了什么，还没有证明什么？

## 与 M6 / M7 的连接

M5 结束时，结算已经把 Payment、Refund 和 Reconciliation 的事实组合成可冻结、可执行、可审计的 Settlement。M6 将把注意力移到 HTTP 错误契约、scheduler、Integration Event、Analyzer/Flow 和 planned 缺口，区分“业务行为已经证明”与“入口或静态图只是可观察”。M7 再把一条 Payment 到 Settlement 的完整 composition trail 串回去，确认每个聚合保留自己的事实和边界。
