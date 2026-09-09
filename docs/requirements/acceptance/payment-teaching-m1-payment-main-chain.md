# M1：Payment 主链阅读文章

> 本文是面向阅读式教学的自包含材料。目标不是背诵类名，而是沿真实入口和测试证据讲清一笔 Payment 如何从收款意图走到可信成功，并知道哪些结论不能外推到生产系统。

## 固定上下文胶囊

- 工程：`cap4k-reference-payment`，当前教学链路为 `Payment → Refund → Reconciliation → Merchant Settlement`。
- 本课范围：Payment 创建、渠道受理、可信成功、基础幂等、费用快照、通知意图和查询回读。
- 对应验收场景：`PAY-AC-001`–`PAY-AC-005`、`PAY-AC-012`–`PAY-AC-014`、`PAY-AC-016`。
- 当前证据状态：上述场景在证据目录中为 `verified`；`verified` 只表示已有可复核的 reference 证据，不表示生产银行网络、生产通知、跨实例 exactly-once 或完整认证授权已经完成。
- 测试边界：主测试使用 `@SpringBootTest + @AutoConfigureMockMvc`、H2、Fake Provider 和测试 receiver；领域测试证明状态机与不变量，不能单独证明 HTTP/JPA wiring。

## 业务问题与全链路位置

Payment 首先回答“商户是否接受了一次收款意图”，而不是“钱是否已经到账”。创建成功只产生 `PENDING` 支付记录；只有支付尝试获得可信、身份和金额匹配的最终成功结果，才形成一次成功事实。渠道同步返回 `ACCEPTED` 仅表示请求被受理，Payment 仍处于处理中或结果待确认，不得计入收入、通知意图或结算候选。

这条主链是后续模块的上游事实源：成功 Payment 才能进入退款资格计算、对账快照和商户结算组成。M2 将回到同一条链处理不可信、重复、迟到、矛盾、未知和并发结果；因此本课先建立“正常成功一次、重复不增效、终态不回退”的基准。

## 阅读路线

1. [payment-teaching-context.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-context.md)：确认教学边界、证据层次和阅读习惯。
2. [payment-teaching-syllabus.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-syllabus.md#m1payment-主链)：确认 M1 的目标、场景范围和重点观察项。
3. [payment-scenarios.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-scenarios.md)：阅读 `PAY-AC-001`–`005`、`012`–`014`、`016` 的 Given/When/Then 业务断言。
4. [payment-lifecycle.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/business/payment-lifecycle.md#1-支付生命周期)：对照状态图、主路径和外部交换语义。
5. [PaymentReferenceApplicationTests.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/PaymentReferenceApplicationTests.kt#L68)：阅读 `create attempt confirm duplicate conflict and query form one durable payment chain`，观察真实 HTTP → Command/UoW → H2 回读。
6. [PaymentBehaviorTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/PaymentBehaviorTest.kt#L42)：阅读 `accepted channel result forms success once and later failure cannot roll it back`，观察成功事实、重复回执、费用快照和终态保护。
7. 继续查看 `ConfirmPaymentResultCmdContractTest`、Payment endpoint/HTTP configuration contract，确认入口路径、稳定错误码和 mapper 契约；这些是辅助证据，不替代主测试。
8. [payment-test-evidence-catalog.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-test-evidence-catalog.md#4-PAY-AC--PAY-BR--测试证据矩阵)：逐行核对 M1 场景的“证明了什么/没有证明什么”。

## 按业务时间顺序阅读主链

### 1. 创建收款意图：`PENDING`

`PaymentReferenceApplicationTests` 在真实 HTTP 入口提交商户订单 `O-001`、幂等键 `K-001`、金额 `100.00`（CNY），断言 HTTP 201、稳定 `paymentId`、状态 `PENDING`，且 `idempotentReplay=false`（测试文件约第 68–83 行）。这一步证明的是“收款意图被接受”，不是付款完成；金额、币种和支付号可查询，尝试数为 0。

领域测试的 `money keeps exact cents and rejects invalid payment amounts`（[PaymentBehaviorTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/PaymentBehaviorTest.kt#L30)）进一步证明金额必须精确到分，零金额、三位小数、非法或未开通币种会被拒绝。对应 `PAY-AC-012` 的关键边界是：拒绝发生在占用幂等键和创建渠道尝试之前。

### 2. 幂等重放与冲突

同一请求再次提交时，应用测试断言返回原 `paymentId` 且 `idempotentReplay=true`，不产生第二笔 Payment（约第 85–87 行）。若仍使用 `K-001` 却把金额改为 `120.00`，返回 409 和稳定错误码 `IDEMPOTENCY_CONFLICT`（约第 89–94 行）；随后 GET 仍显示原金额 `100.00`、CNY、`PENDING`、`attemptCount=0`（约第 96–100 行）。这组断言同时覆盖 `PAY-AC-001`、`002`、`003`、`013`：幂等键绑定的是完整关键内容，已创建支付不能被改写。

### 3. 发起支付尝试：`PROCESSING`

调用 `POST /api/payments/{paymentId}/attempts` 后，测试断言选择渠道 `C-001`，Payment 和 Attempt 均为 `PROCESSING`（约第 102–106 行）。生命周期文档的状态图对应“待支付 → 发起尝试 → 处理中”。每次尝试拥有独立身份、渠道和时间；此时尚无成功事实，也没有可结算收入。

### 4. 渠道受理不是成功：`PAY-AC-016`

渠道只返回 `ACCEPTED` 时，业务语义仍是处理中或结果待确认。它不能形成成功收入、商户成功通知或结算候选。阅读时要把“请求已被渠道接收”与“可信最终结果”分开；这正是后续 callback 处理能够安全收敛的前提。M1 的应用主测试随后还发送不可信 callback，展示同一原则：不可信结果不能推进状态。

### 5. 可信成功：一次性形成成功事实

领域测试先建立处理中 Attempt，再调用 `recordChannelResult`，传入 `verified=true`、匹配的渠道、Attempt、金额、币种、通知身份和渠道交易号。首次 `SUCCESS` 的断言是 `accepted=true`、`successFactFormedNow=true`；Payment 变为 `SUCCEEDED`，Attempt 留下渠道交易号和成功时间，并形成一次成功事实（[PaymentBehaviorTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/PaymentBehaviorTest.kt#L56)）。

成功时还冻结手续费规则快照：测试断言费率基点、舍入模式 `HALF_UP`、精度 2、计算金额 `100.00`、手续费 `2.00` 及形成时间（约第 104–113 行）。这对应 `PAY-AC-014`：之后配置从 0.60% 调整为 0.80%，该支付结算仍使用成功时快照，而不是动态重算。

### 6. 重复成功通知：留痕但不重复产生资金效果

在领域测试 `PaymentBehaviorTest#accepted channel result forms success once and later failure cannot roll it back` 中，首次成功与重复成功使用通知身份 `N-001`；结果标记 `duplicate=true`、`successFactFormedNow=false`。测试断言成功事实仍只有一份，但 Attempt 的 receipt 数量为 2，第一条 receipt 的 `receiveCount=2`（约第 71–83、114–116 行）。应用主测试使用另一组 HTTP 夹具：首次成功和后续三次重复都使用 `N-003`，并回读 `receiveCount=4`（[PaymentReferenceApplicationTests.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/PaymentReferenceApplicationTests.kt#L139)）。两组证据表达的是同一业务规则，但不是同一条通知序列。因此系统同时满足两点：重复通知可观察、可审计；收入、手续费和成功通知意图不重复计算。支付成功通知意图计数保持 1。

### 7. 成功后的失败结果：不回退

随后以新的通知身份发送可信 `FAILED`，领域测试断言该结果不被接受为状态回退，Payment 仍为 `SUCCEEDED`，成功事实保留；冲突证据进入 receipt/后续复核路径。测试还断言 `settlementBlocked=true`（约第 84–117 行），说明矛盾结果会阻断自动结算，而不是静默覆盖历史成功。完整的迟到、双成功、未知和 review 收敛属于 M2。

### 8. 查询回读：证明 durable chain

应用主测试通过 GET `/api/payments/{paymentId}` 回读金额、币种、Payment 状态和尝试数，并继续检查 callback 响应中的 accepted/rejected、disposition、paymentStatus 和拒绝摘要。其 Arrange/Act/Assert 明确写出“真实 HTTP binding → Command/UoW → H2 持久化入口”（约第 71–81 行）。这使主链不是只在内存聚合中成立，而是能在 JPA/H2 中回读同一条持久化事实链。

## 测试证明与未证明

**已证明（本课范围）**

- 首次创建返回稳定支付号，状态为 `PENDING`；相同幂等内容重放原结果，冲突内容返回 `IDEMPOTENCY_CONFLICT`。
- 金额/精度/币种输入边界在创建前拒绝，不占用幂等键、不创建尝试。
- 真实 HTTP、Command/UoW、H2 持久化和查询回读串成一条 Payment/Attempt 链。
- `Gateway ACCEPTED` 不等于成功；可信匹配 callback 才形成一次成功事实。
- 重复成功通知只增加接收计数，不重复收入、手续费或通知意图。
- 成功时手续费规则快照冻结；成功后的失败结果不回退成功事实。

**未证明（必须保留边界）**

- Fake Provider/H2 不证明生产银行或清算网络、真实签名/证书轮换、渠道异步延迟和生产通知投递。
- 事务级并发或唯一约束证据不等于跨实例 exactly-once、压力曲线、网络分区下的可靠性。
- 普通 Scheduler、测试 receiver、静态 Analyzer/Flow 不证明 durable scheduler、生产商户通知服务或通用 outbox。
- 完整认证授权、租户隔离、双人复核和审计闭环不在 M1 证明范围；证据目录中的 `PAY-AC-080/081/084/086` 仍为 `planned/not-built`。
- 重复支付是否允许重付、超时升级时长、通知重试截止等待确认规则不能从本课测试擅自推导。

## 阅读检查点（非门槛）

完成定向阅读后，可用以下问题自检，不要求先预测，也不要求运行测试：

1. 为什么创建返回 201 仍是 `PENDING`？哪一个事实才代表可信成功？
2. `K-001` 重放与金额改为 120.00 的请求，分别返回什么，为什么？
3. `ACCEPTED`、`SUCCESS`、重复 `SUCCESS` 在 Payment、Attempt、receipt 和成功事实上的差异是什么？
4. 成功后为什么还要保留失败结果，并将 `settlementBlocked` 置为真？
5. 手续费快照冻结解决了什么历史一致性问题？
6. 哪些断言来自领域测试，哪些必须依赖应用/JPA/HTTP 测试才能成立？

## 与 M2 的连接

M1 建立“正常主链”的基准：`PENDING → PROCESSING → SUCCEEDED`，成功事实、手续费快照和通知意图各自只形成一次，并可查询回读。M2 将在这条基准上解释非理想时间序列：不可信通知被拒绝并留 receipt；到期时有处理中尝试进入 `RESULT_UNKNOWN`；关闭后的迟到成功保留终态并创建 review；两个尝试都成功时保留双方证据但只计一次收入；成功后的失败/未知结果追加冲突 evidence，不回退 Payment。

因此进入 M2 时，始终先问三个问题：当前 Payment/Attempt 状态是什么？已有的成功事实、receipt、review 和通知意图是什么？新结果是可接受的推进、重复，还是必须追加并阻断的矛盾证据？
