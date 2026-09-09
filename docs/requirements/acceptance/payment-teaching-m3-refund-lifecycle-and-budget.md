# M3：Refund 生命周期与退款预算阅读文章

> 本文是面向阅读式教学的自包含材料。目标是沿 Payment 成功事实、Refund 预占预算、渠道 callback 与复核证据，讲清退款如何安全收敛，并明确测试证据不能外推的边界。

## 固定上下文胶囊

- 工程：`cap4k-reference-payment`；完整资金事实链为 `Payment → Refund → Reconciliation → Merchant Settlement`。
- 本课范围：已成功 Payment 的退款资格、退款窗口、全额/部分退款、预算预占与转换、Refund/Attempt 状态、callback 幂等与冲突、失败释放、UNKNOWN 复核、并发与事务回滚。
- 对应验收场景：`PAY-AC-020`–`PAY-AC-029`。
- 当前证据状态：这些场景在证据目录中为 `verified`；`verified` 仅表示存在可复核的 reference 证据，不表示生产渠道、通知投递、跨实例 exactly-once 或完整认证授权已经完成。
- 测试边界：主测试采用 `@SpringBootTest + @AutoConfigureMockMvc`、H2、Fake Provider 和测试 scheduler/receiver；领域测试证明 Refund 状态与结果不变量，不能单独证明 HTTP/JPA wiring。

## 业务问题与全链路位置

Refund 不是对 Payment 金额做一次简单减法，而是对“已确认成功的收款事实”创建一笔独立退款意图，并先在 Payment 上占用预算，再等待渠道结果决定预算是转换为成功退款、释放，还是保持占用并进入复核。其在全链路中的位置是：

```text
Payment(SUCCEEDED + success fact)
  → Refund(PROCESSING / RESULT_UNKNOWN / REVIEW_REQUIRED / SUCCEEDED|FAILED)
  → Reconciliation(读取支付与退款事实快照)
  → Merchant Settlement(依据有效、未阻断事实计算结算组成)
```

只有 `SUCCEEDED` Payment 才具备退款资格；`PENDING`、`PROCESSING`、`FAILED`、`CLOSED`、`RESULT_UNKNOWN` 或存在未收敛冲突的 Payment 不得直接创建 Refund。退款成功或失败不会覆盖 Payment 的原成功事实；它追加新的资金事实，供后续对账和结算读取。

## 阅读路线

1. [payment-teaching-context.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-context.md)：确认证据层次、教学边界和“不要求运行测试”的 SOP。
2. [payment-teaching-syllabus.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-syllabus.md#m3refund-生命周期和退款预算)：确认 M3 目标、场景范围和重点观察项。
3. [payment-scenarios.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-scenarios.md)：阅读退款场景 `PAY-AC-020`–`029` 的 Given/When/Then。
4. [payment-rules.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/business/payment-rules.md)：核对退款资格、窗口、预算和 callback 规则。
5. [PaymentReferenceApplicationTests.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/PaymentReferenceApplicationTests.kt#L1083)：按本文时间顺序阅读退款应用测试。
6. [RefundResultRecordingOutcomeTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/refund/RefundResultRecordingOutcomeTest.kt#L12)：核对结果对象的不变量和 disposition 语义。
7. 继续查看 Refund endpoint/HTTP configuration contract，确认创建、查询和 callback 路径；这些是入口辅助证据，不替代应用主测试。
8. [payment-test-evidence-catalog.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-test-evidence-catalog.md)：逐行核对 `PAY-AC-020`–`029` 的“证明了什么/没有证明什么”。

## 按业务时间顺序阅读退款链

### 1. 资格检查：先确认成功 Payment 与窗口

退款请求必须绑定商户、Payment、金额、币种、商户退款号和请求时间。应用测试 `refund rejects non-success payments and requests after the refund window`（[PaymentReferenceApplicationTests.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/PaymentReferenceApplicationTests.kt#L1293)）将 Payment 状态设为 `PENDING`、`PROCESSING`、`FAILED`、`CLOSED`、`RESULT_UNKNOWN`，逐一断言返回 `INVALID_REQUEST`，`reservedRefundAmount=0.00` 且数据库没有 Refund。对 `SUCCEEDED` Payment 使用窗口外时间（`2027-02-14T00:00:00Z`）同样拒绝并不预占。

`refund application rejects merchant currency and channel eligibility mismatches without reservation`（约 L1152）补充证明：商户不匹配、币种不匹配或没有可用退款渠道时，请求分别返回 `INVALID_REQUEST` 或 `NO_ELIGIBLE_CHANNEL`，不创建 Refund、不改变预占。资格失败必须发生在预算预占和渠道请求之前。

### 2. 创建 Refund：预占预算并进入 `PROCESSING`

资格通过后，创建独立 Refund 与 Refund Attempt；响应状态为 `PROCESSING`，Payment 的 `reservedRefundAmount` 增加请求金额。退款预算可用量遵循：

```text
refundableAmount = paymentAmount - successfulRefundAmount - reservedRefundAmount
```

`reservedRefundAmount` 表示尚未得到最终结果、暂时锁定的金额；`successfulRefundAmount` 只累计可信 `SUCCESS`；`refundableAmount` 是下一笔退款可申请的剩余上限。创建失败或资格失败都不能留下预占。

### 3. 全额退款：预占转换为成功

`a successful payment can be refunded in full`（`PAY-AC-020`，[PaymentReferenceApplicationTests.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/PaymentReferenceApplicationTests.kt#L1103)）先创建 `100.00` 的成功 Payment，再创建全额 Refund。可信 callback 返回 `SUCCESS_ACCEPTED`，Refund 与 Attempt 均为 `SUCCEEDED`；`reservationActive=false`、`reservationConvertedToSuccess=true`。Payment 回读断言 `reservedRefundAmount=0.00`、`successfulRefundAmount=100.00`、`refundableAmount=0.00`。这证明预算占用是从 reservation 转换为成功退款，而非重复扣减。

领域 `RefundResultRecordingOutcomeTest#accepted success outcome exposes a coherent conversion`（[RefundResultRecordingOutcomeTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/refund/RefundResultRecordingOutcomeTest.kt#L14)）进一步断言 `SUCCESS_ACCEPTED` 必须是 accepted、非 duplicate，并且只在一次结果中声明 `reservationConvertedToSuccessNow=true`。

### 4. 部分退款：多笔独立事实共享同一预算

`multiple partial refunds remain independently queryable and update payment budget`（`PAY-AC-021`，约 L1209）创建并成功确认 `30.00` 与 `20.00` 两笔 Refund。两笔记录都可独立查询且为 `SUCCEEDED`；Payment 最终为 `reservedRefundAmount=0.00`、`successfulRefundAmount=50.00`、`refundableAmount=50.00`。这说明每笔 Refund 保留自己的 Attempt/receipt 历史，同时 Payment 提供聚合预算视图。

### 5. 超额与并发：预算上限在创建前保护

先成功退款 `60.00` 后申请 `50.00`，`refund beyond the exact remaining amount is rejected without a channel request`（`PAY-AC-022`，约 L1228）断言返回 `CONCURRENT_MODIFICATION`，Payment 仍为 `successfulRefundAmount=60.00`、`reservedRefundAmount=0.00`、`refundableAmount=40.00`，被拒请求没有 Refund 行，也没有渠道请求。

`two concurrent refund HTTP applications persist one refund and return stable conflict`（`PAY-AC-023`，约 L1337）用两个真实 HTTP 事务同时申请各 `40.00`：结果稳定为一个 201、一个 409 `CONCURRENT_MODIFICATION`；数据库仅一笔 Refund，Payment 仅预占 `40.00`，剩余 `20.00`。同文件后续的 `two real transactions cannot over-reserve one payment refund budget`（约 L1435）把竞争下沉到事务/约束层，证明不能因竞态超额预占。

### 6. 失败结果：释放 reservation，可再次退款

`trusted failed refund result releases its payment reservation`（`PAY-AC-024`，约 L1254）创建 `40.00` Refund 后先确认 Payment 预占为 `40.00`。可信 `FAILED` callback 返回 `FAILURE_ACCEPTED`，Refund/Attempt 为 `FAILED`，`reservationReleasedNow=true`；持久化记录显示 `reservationActive=false`、`reservationReleased=true`。Payment 回到 `reservedRefundAmount=0.00`、`successfulRefundAmount=0.00`、`refundableAmount=100.00`。失败是追加的退款结果，不会改变原 Payment 成功事实。

### 7. UNKNOWN：保留 reservation，进入复核

`unknown refund result remains reserved and scheduled review marks it`（`PAY-AC-025`，约 L1275）对 `20.00` Refund 接收 `UNKNOWN`，立即返回 `UNKNOWN_ACCEPTED` 与 `RESULT_UNKNOWN`。执行 `refundReviewScheduler.review()` 后 Refund 转为 `REVIEW_REQUIRED`，`reservationActive=true`，Payment 仍预占 `20.00`。未知不能当作失败释放，也不能当作成功转换；它等待后续授权处置。

### 8. callback 幂等、冲突与终态保护

`refund accepted callback is idempotent and queryable`（约 L1083）展示完整 callback 证据：首次相同通知身份返回 `SUCCESS_ACCEPTED`，原样重放返回 `ACCEPTED_DUPLICATE`；相同 Refund/Attempt 但金额改为 `29.99`，或结果改为 `FAILED`，均返回 `CONFLICT`，最终 Refund 仍为 `SUCCEEDED`。receipt/冲突 evidence 被追加查询，成功预算不回退。

领域结果对象测试 `outcome rejects contradictory or incomplete evidence`（约 L34）明确禁止矛盾组合：成功 disposition 不能 `notificationReceiveCount=0`；`ATTEMPT_NOT_FOUND` 必须有 rejection summary；`CONFLICT` 必须有 conflict summary；失败结果不能同时声明 reservation 转成功。这些是结果证据不变量，不是渠道真实性证明。

### 9. 退款幂等与跨聚合事务回滚

`merchant refund number replay rejects changed critical content without a second refund`（`PAY-AC-026/029`，约 L1125）证明同一商户退款号改金额会返回 409 `REFUND_IDEMPOTENCY_CONFLICT`，数据库仍只有一笔 Refund，原 Attempt 保留，Payment 的 reservation 不被改写。

`refund creation database failure rolls back payment reservation and refund aggregate together`（`PAY-AC-024`，约 L1397）通过超长退款号触发持久化失败，随后断言 Payment 的 reservation/successful/refundable 分别为 `0.00/0.00/50.00`，Refund 行数为 0。跨 Payment 与 Refund 聚合的事务必须原子回滚。

## 关键断言速查

- 退款资格：只有已形成可信成功事实的 `SUCCEEDED` Payment，且请求在退款窗口内、商户/币种/渠道匹配。
- 预算：`refundableAmount = paymentAmount - successfulRefundAmount - reservedRefundAmount`；创建先预占，成功转换，失败释放，UNKNOWN 保留。
- 状态：Refund/Attempt 从 `PROCESSING` 收敛到 `SUCCEEDED`、`FAILED`、`RESULT_UNKNOWN` 或 `REVIEW_REQUIRED`；终态不被矛盾 callback 覆盖。
- 幂等：相同 merchant refund number + 关键内容重放不新建；关键内容改变返回 `REFUND_IDEMPOTENCY_CONFLICT`。
- callback：首次可信结果接受；相同通知重放为 `ACCEPTED_DUPLICATE`；关键 payload 或结果冲突为 `CONFLICT`，并追加 receipt/evidence。
- 并发/事务：同一 Payment 的竞争申请至多一笔成功预占；跨聚合写入失败时 reservation 与 Refund 一起回滚。

## 测试证明了什么，未证明什么

**已证明**

- `PAY-AC-020`–`029` 所覆盖的资格、窗口、预算、全额/部分/超额、失败、UNKNOWN、callback、幂等、并发和事务回滚在 reference 工程中有可回读证据。
- H2/JPA 应用测试证明 HTTP → Command/UoW → 持久化回读的一致行为；领域测试证明 RefundResultRecordingOutcome 的证据不变量。
- Payment 的成功退款累计、预占释放/转换和剩余可退金额保持可解释关系。

**未证明**

- Fake Provider 不证明生产银行/渠道退款、签名校验、证书轮换、网络分区、真实异步延迟或渠道撤销/补偿协议。
- 两事务测试只证明当前数据库事务与唯一约束下的竞争结果，不证明跨实例 exactly-once、压力性能或分布式锁行为。
- 普通 scheduler 的 UNKNOWN 复核调用不证明 durable scheduler、重启恢复、人工审核 SLA 或生产通知可靠投递。
- 测试没有证明退款后对账 statement 已匹配，也没有证明结算批次已采用退款事实；这些属于 M4/M5 的独立证据。

## 阅读检查点（非门槛）

1. 为什么退款入口要求 Payment 已 `SUCCEEDED`，而 `RESULT_UNKNOWN` 不能直接退款？
2. 一笔 Refund 创建后，`reservedRefundAmount`、`successfulRefundAmount`、`refundableAmount` 各自代表什么？
3. 全额成功、部分成功、可信失败和 UNKNOWN callback 分别如何改变三个金额？
4. 为什么相同 callback 是 `ACCEPTED_DUPLICATE`，金额或结果改变却是 `CONFLICT`？
5. 并发申请为什么一个返回 201、另一个返回 `CONCURRENT_MODIFICATION`，且数据库只有一笔 Refund？
6. 哪些结论来自领域不变量，哪些必须依赖应用/JPA 回读？
7. 测试没有证明哪些生产渠道、调度和跨实例语义？

## 与 M4 Reconciliation 的连接

M3 结束时，Refund 已留下可供对账读取的独立事实：请求金额、Attempt、渠道交易号、成功/失败/UNKNOWN/review 状态、reservation 转换或释放证据，以及 callback receipt/conflict evidence。M4 不会重新计算退款流程，而是把 Payment 与 Refund 的平台事实和渠道 statement 放入同一对账快照，判断 `MATCHED`、金额差异或 `unresolved`，并通过 revision/effective run 保留历史处置。

因此进入 M4 时要带着三个问题：退款事实是否已经可信收敛？`RESULT_UNKNOWN`/`REVIEW_REQUIRED` 是否仍阻断下游采用？对账运行引用的是哪一个 effective snapshot？M3 的预算不变量保证“可退多少钱”可解释；M4 再验证“平台记录与渠道记录是否一致”，两者共同决定 Merchant Settlement 是否能安全采用该事实。
