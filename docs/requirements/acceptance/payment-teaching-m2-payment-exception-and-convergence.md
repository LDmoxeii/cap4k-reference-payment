# M2 Payment 异常与收敛：把不可信结果变成可处理事实

> 本课是 Payment 主链之后的阅读式教材。目标不是背状态名，而是理解：异常输入不会覆盖历史事实，而是通过 receipt、evidence、review 和稳定身份逐步收敛。阅读、测试执行均非门槛。

## 1. 固定上下文胶囊

- 全链路：`Payment → Refund → Reconciliation → Merchant Settlement`。
- 当前验收场景 53 个，`verified` 49 个；`PAY-AC-080/081/084/086` 仍为 `planned/not-built`。
- 业务断言真源：[payment-scenarios.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-scenarios.md)；状态和证据机器真源：[traceability.yaml](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/traceability.yaml)。
- 本课只修改阅读理解，不要求运行测试；Fake Provider、H2、普通 `@Scheduled` 和测试 receiver 不能外推为生产网络、跨实例 exactly-once 或完整 RBAC。

## 2. 业务问题与全链路位置

Payment 的正常路径是“创建意图→发起 Attempt→渠道受理→可信最终结果→成功事实→通知/结算候选”。异常来自六类不确定性：结果不可信、时间已过期、结果迟到、结果相互矛盾、通知重复或关键内容冲突、多个订单请求并发竞争。共同原则：验证后再推进；追加而非覆盖；终态保护；稳定收敛；异常阻断下游结算。Reconciliation 读取 Payment 成功事实和 review 快照，Settlement 依据阻断标记筛选候选；Refund 只接受已确认成功的 Payment。

## 3. 推荐阅读路线

1. [payment-teaching-context.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-context.md) → [payment-teaching-syllabus.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-syllabus.md)。
2. [payment-lifecycle.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/business/payment-lifecycle.md) 第 1 节状态图。
3. [PaymentBehaviorTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/PaymentBehaviorTest.kt)。
4. [PaymentReferenceApplicationTests.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/PaymentReferenceApplicationTests.kt)。
5. [payment-test-evidence-catalog.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-test-evidence-catalog.md) PAY-AC-006–011、015、017；以及 `PaymentExpirySchedulerStructureTest`、`AdjudicatePaymentReviewCmdContractTest`。

## 4. 异常类型逐项阅读

### 4.1 到期：无未决 Attempt 才能关闭；有处理中则转未知

**真实入口**：`PaymentReferenceApplicationTests#payment expiry closes without pending attempts and repeated scans stay idempotent`（约 L258）；领域对应 `PaymentBehaviorTest#expired payment without pending attempt closes idempotently and cannot start`（约 L168）。

**状态变化**：过期且没有处理中/待确认 Attempt：`PENDING → CLOSED`，后续不得新建 Attempt，重复扫描幂等。有处理中 Attempt：转 `RESULT_UNKNOWN`，创建一个稳定 review，不能直接失败。

**历史事实与断言**：保留 Attempt、扫描结果和 review identity；应用测试回读状态、review 数量及重复扫描结果。PAY-AC-007/008（证据目录 L59–60）明确两个分支。

**边界**：超时升级时长待确认；普通 Scheduler 测试不证明 durable scheduler、跨实例抢占。

### 4.2 未知/不可信通知：拒绝推进，但保留 receipt

**真实入口**：`PaymentBehaviorTest#verified channel mismatch is rejected and retained as a notification receipt`（约 L132）；应用层 `#attempt identity and currency mismatches remain queryable without advancing payment`（约 L854）。

**状态变化**：来源无法验证、Attempt/支付身份不匹配或金额/币种不符时，Payment 与 Attempt 不推进，通知 disposition 为拒绝。

**历史事实与断言**：写入 notification receipt、拒绝原因摘要和接收时间；查询仍可见。PAY-AC-006/017 断言状态不变且异常可追踪。

**边界**：不等于真实签名验证器、密钥托管或生产认证授权。

### 4.3 迟到：终态不回退，转 held review 并阻断结算

**真实入口**：`PaymentReferenceApplicationTests#late success after closed payment preserves terminal evidence until authorized review`（约 L441）；领域对应 `PaymentBehaviorTest#late success after closed terminal preserves terminal state and opens held review`（约 L203）。

**状态变化**：已 `CLOSED` 收到可信成功仍保持 CLOSED；创建稳定 review/held disposition，不形成可结算成功。

**历史事实与断言**：保留 external evidence、渠道交易号、原关闭事实和 review identity；检查查询结果、`settlementBlocked` 与证据回读。PAY-AC-009（目录 L61）明确不得静默丢弃或覆盖。

**边界**：人工处理时效未被证明。

### 4.4 矛盾：成功后的失败、双成功均追加冲突，不撤销成功

**真实入口**：领域 `#accepted channel result forms success once and later failure cannot roll it back`（约 L44）、`#two trustworthy attempt successes form revenue fee and notification intent only once`（约 L230）；应用 `#review and callback race preserves the authorized decision and later conflict evidence`（约 L538）、`#second attempt success preserves both successes while revenue and intent remain once only`（约 L694）。

**状态变化**：第一次可信成功形成 success fact；随后失败或第二 Attempt 成功不回退、不重复计收入，仅标记 conflict/review 并阻断结算。

**历史事实与断言**：每个 Attempt 成功证据保留；revenue、fee snapshot、merchant notification intent 仅一份；冲突 evidence 和 review 可查询。PAY-AC-010、015（目录 L62、67）。

**边界**：不覆盖渠道撤销、补偿协议或真实争议处理。

### 4.5 重复/冲突通知：identity 相同则幂等，payload 改变则冲突

**真实入口**：应用 `#create attempt confirm duplicate conflict and query form one durable payment chain`（约 L70）；领域 `#notification identity reuse with another payload appends evidence without overwriting first receipt`（约 L259）。

**状态变化**：相同 identity 与内容重放返回 `ACCEPTED_DUPLICATE`，成功事实不新增，仅接收计数递增；同 identity 不同关键 payload 拒绝或标记 conflict，原 receipt 不覆盖。

**历史事实与断言**：receipt identity、首次 payload、重复次数、冲突 evidence 持久化；`successFactFormedNow=false`。PAY-AC-005/003（目录 L57、55）。

**边界**：不证明跨实例共享幂等存储或通知 transport 至少一次投递。

### 4.6 并发订单竞争：订单级成功 claim 至多一个，失败方证据保留

**真实入口**：`PaymentReferenceApplicationTests#concurrent payments for one merchant order retain loser evidence and only one accepted success claim`（约 L754），使用真实事务与数据库约束。

**状态变化**：同一订单不同幂等键并发创建/确认时，最多一个 Payment 获得 accepted success claim；竞争失败 Attempt/receipt/review 保留并返回稳定冲突。

**历史事实与断言**：订单成功唯一约束、胜者成功事实、败者证据和查询结果共同证明收敛。PAY-AC-011（目录 L63）明确仅证明事务级竞争。

**边界**：PAY-BR-003 关于失败后是否允许重付仍待确认；没有线程压力、跨节点锁竞争或性能曲线。

## 5. 测试证明了什么，未证明什么

**已证明**：状态机不可回退、重复通知计数、receipt/evidence 追加、稳定 review、到期分支、Attempt 多成功但收入/通知意图一次、真实 H2/JPA 回读、事务级订单竞争；PAY-AC-006–011、015、017 均为 `verified`。

**未证明**：生产渠道和签名系统、跨实例 exactly-once、网络分区重试、durable scheduler、人工处置 SLA、渠道撤销/补偿、完整认证授权和高并发性能。证据目录“缺口与边界证据”明确不可外推。

## 6. 阅读检查点（非门槛）

- 能区分 Gateway ACCEPTED 与成功事实，并指出成功事实只形成一次的位置。
- 能区分 `CLOSED` 与 `RESULT_UNKNOWN` 的到期前提。
- 能解释迟到/矛盾结果为何保留 evidence、保持终态并阻断结算。
- 能从测试断言找到 receipt、review identity、settlement block 和 JPA 回读。
- 能说出订单并发只证明数据库事务级唯一，不等于跨实例 exactly-once。

## 7. 与 M3 Refund 的连接

Refund 入口资格是“已确认成功的 Payment”；`RESULT_UNKNOWN`、held review、conflict 或未形成 success fact 的 Payment 不能直接退款。M3 复用同一收敛思想：退款 UNKNOWN 保留 `reservedRefundAmount` 并进入 review，失败才释放占用；重复/冲突 callback 追加 receipt 而不回退成功预算。下一课对照 [PaymentReferenceApplicationTests.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/PaymentReferenceApplicationTests.kt) 退款测试（约 L1083 之后）与 [RefundResultRecordingOutcomeTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/refund/RefundResultRecordingOutcomeTest.kt)。
