# CAP4K 统一支付参考后端完整目标规格

## 1. 目标、适用范围与一致性

本 capability 描述 `cap4k-reference-payment` 对 `payment-product-template@3b66db675356e77c720081e0410baf444b7baa9c` 统一学习版业务契约的完整实现状态。CAP4K 必须对相同规范化命令、查询、Money、状态、最终性、错误码、关联、排序和副作用给出与真源一致的业务结果；HTTP 路由、序列化、JPA schema、CAP4K authoring style 和内部聚合名称可以保持项目风格。

实现继续采用多 Aggregate Root、Strong ID、Value Object、Command/Query/Capability/Endpoint、JPA/Hibernate、事务 Unit of Work、领域事件、普通 scheduler reaction 与本地可靠事件。不得改造成 Saga、事件溯源或以最终一致性代替当前事务不变量。

公开对账执行资源统一称为 `ReconciliationRun`；内部 `ReconciliationBatch` 可以作为承载 bill scope、revision 与 runs 的事务型聚合，但不得暴露并列 Batch API、第二套执行身份或第二种权威列表。

## 2. 共同业务契约

### 2.1 稳定身份、时间、关联与业务范围

- 业务 ID 是不透明、稳定、非空字符串；不得把数据库主键格式或路由变成业务语义。
- `merchantId` 是命令、资源、筛选和交叉资源校验的显式业务字段，不宣称生产安全隔离。
- 外部事实同时保存 RFC3339 `occurredAt` 与服务端 `recordedAt`；内部事实至少保存 `recordedAt`。
- 业务日、支付到期、账单范围和结算周期由 `ReferencePolicy.businessTimezone` 与逻辑时钟计算。
- 关联使用 `ResourceRef(resourceType, resourceId)` 和 `EvidenceRef(evidenceType, evidenceId, uri?)`；不以内嵌副本取代权威资源。
- 人工动作的统一命令仍具有 `actorId`，但 CAP4K reference HTTP adapter 不信任 body 中的 `actorId`、`requestedBy`、`operatorRole`、`requestedAt` 或等价字段。HTTP 只通过可信 header `X-Reference-Actor-Context` 携带不透明 fixture/session alias；adapter 必须在服务端 `ReferenceActorRegistry` 中校验该 alias 并解析为不可变 `ReferenceActorContext(actorId, role)`，再把解析后的 actorId/role 注入规范化命令。非 HTTP fixture runner 使用同一 registry/session alias，不得直接伪造 actorId。`recordedAt` 由服务端逻辑时钟形成；业务请求只提供 outcome、reason 与 evidence refs。
- 最终 `DifferenceDisposition`、`FactConfirmation`、`ManualReview` 处置、Settlement void/replacement 等责任事实必须返回与该 context 一致的 `actorId`。缺失或未知 actor context 时同步返回 `VALIDATION_ERROR`，不创建 Operation、处置事实或其他副作用。

### 2.2 Money

统一 JSON 形态为 `{ "currency": "CNY", "amountMinor": "10000" }`。`currency` 为启用的 ISO 4217 代码，`amountMinor` 为十进制整数字符串；所有比较、预算、费率、舍入和汇总在最小单位中完成，禁止二进制浮点。支付和退款金额为正；执行正向结算时金额为正；构成项通过方向表达影响。已形成事实的金额与币种不可改写。

### 2.3 领域状态与最终性

所有权威领域资源同时返回领域 `status` 与独立 `finality`：

- `NON_FINAL`：允许自动推进；
- `FINAL`：事实确定，迟到证据不得回退；
- `REVIEW_REQUIRED`：自动推进暂停，需带责任事实的人工处置。

权威事实不物理删除。纠错、冲突、迟到、预算变化、处置与通知均追加记录。

### 2.4 命令幂等

商户或人工命令以 `merchantId + commandType + idempotencyKey` 为作用域。规范化 payload 相同的重放返回原 `operationId` 和资源引用，不创建新资源、副作用、预算变化、渠道提交或通知；payload 不同同步返回 `IDEMPOTENCY_CONFLICT` 且无副作用。渠道、账单和执行器收件使用稳定 submission/result/bill/execution identity 去重，每次接收仍留痕而业务效果只发生一次。

### 2.5 OperationReceipt、Operation 与 readAfter

同步校验、幂等冲突、资源不存在和当前可判定业务拒绝只返回 `ApiError`，不创建 `Operation`，也不返回 receipt。受理命令统一返回：

```text
OperationReceipt {
  operationId, commandType, resourceType, resourceId?,
  acceptanceStatus, acceptedAt, idempotentReplay,
  correlationId, readAfter
}
```

`acceptanceStatus` 只允许 `ACCEPTED | ALREADY_ACCEPTED`。`Operation` 从 receipt 返回时即可查询，状态为 `ACCEPTED | PROCESSING | SUCCEEDED | FAILED | REVIEW_REQUIRED`。`readAfter.mode` 为 `READ_ONCE | POLL`；Projection 暂不可见返回 retryable `RESOURCE_NOT_READY` 而非 `NOT_FOUND`。达到 observation timeout 只产生观察端 `OBSERVATION_TIMEOUT`，不改写 operation 或领域资源。

CAP4K 在同一事务内完成的命令创建稳定且可重放的 `SUCCEEDED/FINAL` Operation，返回 `READ_ONCE`。命令完成不等于支付、退款或结算业务终态；外部结果仍由资源 `status/finality` 表达。

### 2.6 ApiError

统一错误形态为 `ApiError { code, message, details, correlationId, retryable }`。至少支持 `VALIDATION_ERROR`、`NOT_FOUND`、`RESOURCE_NOT_READY`、`IDEMPOTENCY_CONFLICT`、`BUSINESS_CONFLICT`、`INVALID_STATE_TRANSITION`、`REFUND_BUDGET_EXCEEDED`、`UNRESOLVED_RECONCILIATION_DIFFERENCE`、`SETTLEMENT_SCOPE_CONFLICT`、`RESULT_UNKNOWN_REEXECUTION_FORBIDDEN`、`INVALID_CURSOR`、`REFERENCE_EXECUTOR_UNAVAILABLE`，并保留已有更细的稳定业务 code。HTTP status 可按 CAP4K 映射，但 `code/details/retryable` 与无副作用语义必须稳定；内部 SQL、类名、堆栈和 provider raw cause 不泄漏。

## 3. 支付意图、attempt 与渠道证据

### 3.1 PaymentIntent

Payment 聚合继续承载 `PaymentIntent`，至少保存 payment/merchant/order/idempotency、Money、payment method、status/finality、created/expires/succeeded/closed 时间、费用规则快照、唯一成功事实、notification intent、review、退款预算和 owned attempts/receipts。状态规范化为 `PAYABLE | PROCESSING | RESULT_UNKNOWN | SUCCEEDED | FAILED | CLOSED`。

创建支付先校验 Money、merchant 资格和合格 active channel；同 idempotency payload 重放原 operation，同 key 冲突拒绝；同 merchant order 存在活动或成功支付时拒绝，只有前一支付明确失败/关闭后才能以新 key 重建。创建不自动发起 attempt。

### 3.2 PaymentAttempt 与渠道提交

每次付款形成独立 `PaymentAttempt`，保存 attempt/channel、渠道与费率/规则快照、稳定 request/submission identity、interaction information、created/submitted/accepted/completed 时间和状态 `CREATED | SUBMITTED | ACCEPTED | RESULT_UNKNOWN | SUCCEEDED | FAILED | REJECTED`。历史 attempt 不覆盖。成功支付后禁止新 attempt；存在 in-flight/unknown attempt 时新 attempt 需要显式风险原因并保持结算阻断。

`CreatePaymentAttempt` 只创建 attempt；`SubmitPaymentAttempt` 调用 reference channel 并追加 `ChannelSubmissionReceipt`。渠道受理只能推进 attempt/payment 为 accepted/processing，不能形成资金成功事实。

### 3.3 Trusted callback verifier

支付、退款和结算 callback 只携带 canonical raw payload、稳定 external identity、关联引用、Money、outcome 和 occurredAt，不允许业务调用方提供 `verified`、verification verdict、角色或服务端时间。reference executor 在服务端 evidence registry 中冻结 expected identity/payload fingerprint；统一 `ReferenceCallbackVerifier` 根据 registry、canonical payload、关联、Money 与 server-held proof 派生 verification 和 reason。验真结果写入 receipt，聚合不能信任调用方结论。

reference fixture 可以脚本化 invalid/tampered/unknown-ref 输入，但脚本控制面与业务 callback contract 分离。真实证书与 Secret Manager 不在范围。

### 3.4 ChannelResultReceipt 与单一成功事实

每次支付结果接收均追加 `ChannelResultReceipt(resultReceiptId, resultIdentity, attemptId?, channelId, rawEvidence, verification, outcome, occurredAt, recordedAt, disposition)`。outcome 为 `SUCCESS | FAILURE | UNKNOWN`；disposition 为 `ACCEPTED | DUPLICATE | REJECTED_INVALID | UNKNOWN_REFERENCE | LATE | CONFLICTING`。

- 首个可信成功把 attempt/payment 置为成功并创建唯一 `PaymentSuccessFact`；
- 重复收件可查询但不重复状态、收入、预算或通知；
- 验真、身份、关联、金额或币种失败不推动状态；
- 未知引用按外部 identity 可查，必要时建立 review；
- 关闭/失败后的可信成功为 LATE，不自动覆盖终态；
- 第二个 attempt 的可信成功或成功后的失败为 CONFLICTING，成功事实仍只有一份并阻断自动结算；
- UNKNOWN 保持可收敛，达到 policy 期限后保持领域状态并转 `REVIEW_REQUIRED`。

### 3.5 到期、费用与通知

到期且无未决 attempt 的 PAYABLE 支付可关闭；存在 processing/unknown attempt 时不得判失败或关闭，而进入结果收敛/review。支付成功时冻结当时实际生效的 `ReferencePolicy.feeRate`、固定费、`roundingMode`、币种精度及计算结果快照；默认 `feeRate=0.006`、`roundingMode=HALF_UP`，场景显式覆盖时必须使用覆盖值。后续 policy 或配置变化不追溯修改。首次成功形成稳定商户通知意图；重复/冲突结果不形成第二意图。

## 4. 退款申请、attempt、结果与预算

### 4.1 Refund 请求

`RequestRefund` 明确接收独立 `idempotencyKey`、`merchantRefundNo`、`paymentId`、`Money(amountMinor,currency)` 与非空 `reason`。merchant refund number 与 command idempotency 是两个独立约束。只有已成功支付、同 merchant/币种、退款窗口内且预算充足的请求才受理；同步非法请求只返回 ApiError。

Refund 是独立 Aggregate Root，保存 refund/payment/merchant、merchantRefundNo、idempotencyKey、Money、reason、status/finality、createdAt、attempts/receipts、预算裁决和 review。状态为 `REQUESTED | PROCESSING | RESULT_UNKNOWN | SUCCEEDED | FAILED | REJECTED`。退款申请只创建 Refund 并预占一次预算，不自动把创建、attempt 与渠道结果合并。

### 4.2 RefundBudget 与并发原子性

Payment 是退款预算并发裁决权威，查询返回 `originalAmount`、`succeededAmount`、`reservedAmount`、`availableAmount`，始终满足：

```text
succeededAmount + reservedAmount <= originalAmount
availableAmount = originalAmount - succeededAmount - reservedAmount
```

请求受理时检查与预占在同一 JPA UoW 中原子完成；相同命令重放不再次预占。真实双事务、乐观锁/唯一约束和稳定冲突映射证明并发不超退。

### 4.3 RefundAttempt

`CreateRefundAttempt` 与 `SubmitRefundAttempt` 明确分离，attempt 独立保存 channel、request/submission identity、channelRefundId、状态与时间，并拥有提交回执和结果收件。attempt 重试不再次预占；在 policy 允许下，可重试失败仍占用原预算直到退款整体失败/拒绝或后续收敛。

### 4.4 退款结果与预算转换

退款结果使用 §3.3 trusted verifier 和 §3.4 相同的验真、去重、冲突、迟到、未知引用语义。退款整体 `FAILED/REJECTED` 时预占只释放一次；可信成功时只转换一次为 succeeded；UNKNOWN、冲突与 REVIEW_REQUIRED 继续占用；成功后迟到失败不回退成功事实。Refund 与 Payment 预算变化在同一 CAP4K Command/UoW 中提交，任一失败整体回滚。

## 5. 权威查询、筛选与稳定分页

五类权威列表仅为 `PaymentIntent`、`Refund`、`ReconciliationRun`、`Settlement`、`ManualReviewItem`。账单、revision、attempt、receipt、difference、item、execution 与 notification 通过父资源或稳定 identity 查询。

统一页面为 `Page<T> { items, nextCursor, pageSize }`，固定按创建时不可变的 `sortTime DESC, id DESC`：

- cursor 为绑定完整 filters、排序和末项复合键的不透明、校验过的 token；伪造或跨筛选复用返回 `INVALID_CURSOR`；
- 默认 pageSize 20、最大 100；权威资源不物理删除；
- 后续页只取严格小于 cursor 边界的项，cursor 生成前项目不重复不遗漏，翻页后新建的更靠前项目不回填；
- 无 snapshot token 或数据库级快照承诺。

所有列表至少支持 merchantId、status、finality、资源 ID、createdFrom/createdTo；额外筛选与统一 Contract 一致。查询由 adapter/application 中专用 JPA query/read model 执行，不通过 Controller、前端或加载全部聚合后拼装。详情与子资源查询返回稳定 identity、status/finality、关联和追加证据，不暴露 JPA proxy。

## 6. 权威账单、ReconciliationRun 与差异

### 6.1 AuthoritativeBill 与 BillRevision

reference bill authority 保存 `AuthoritativeBill(billId, channelId, businessDate, currency, currentRevision, createdAt)` 与不可变 `BillRevision`。`(channelId,billId,revision)` 唯一；高 revision 推进指针，重复/低/迟到 revision 不回退。每个 revision 保存完整性、稳定 record identity、原始证据和发布时间。暂不可读按同一 signal identity、policy 次数与逻辑 backoff 重试并保留诊断。

提供 reference 权威账单登记/发布能力、bill available signal、按 channel/currency/businessDate/bill/revision 发现或发起对账，以及人工重跑。登记与 signal 使用 reference fixture/control boundary；业务 run 始终从权威 provider 读取正文，signal payload 不替代账单内容。

### 6.2 ReconciliationRun 唯一资源

`RunReconciliation/RerunReconciliation` 对明确 bill revision 与 scope 形成 `ReconciliationRun`，状态 `PENDING | FETCHING_BILL | RUNNING | ACTION_REQUIRED | COMPLETED | FAILED`。同范围/revision 的并发相同触发复用稳定 run/operation；重跑保留旧 run 和证据。每个 scope/revision 同时只有一个 `effectiveRun`，且列表、详情、状态与重跑全部以 Run 为主资源。

### 6.3 匹配、差异与完成条件

每个 run 分别持久化平台事实快照、bill record、matching basis 和分类。优先稳定渠道交易/退款号；辅助匹配必须保留依据，禁止仅凭金额。结果至少区分 MATCHED、PLATFORM_ONLY、CHANNEL_ONLY、AMOUNT_MISMATCH、CURRENCY_MISMATCH、STATUS_MISMATCH、DUPLICATE_CHANNEL_RECORD、UNMATCHED。状态/金额/币种相关未决差异 blocking，并阻断对应事实自动结算。账单完整、全量核对且每个差异有明确结论后 run 才能完成。

### 6.4 处置与补录确认

`DisposeReconciliationDifference` 只追加 `DifferenceDisposition`；`ConfirmReconciliationFact` 只追加 `FactConfirmation`。reason/evidence 来自请求，actorId/role 必须由 §2.1 的可信 ReferenceActorContext 映射，recordedAt 来自服务端 clock；最终查询返回该 actorId。缺失或未知 context 同步 `VALIDATION_ERROR` 且无副作用。二者不改写平台事实、bill revision 或初始 difference；只有明确且可追踪的结论能解除对应阻断或形成独立后续资金事实。CHANNEL_ONLY 不自动形成支付成功。

## 7. Settlement 准备、执行、作废与替代

### 7.1 Scope 与范围唯一

Settlement scope 唯一由 `merchantId + currency + settlementPeriod(start,end,timezone)` 定义。`channelId` 只作为 source/item/evidence 维度；`settlementDate` 只可作为 period 的派生表示。数据库唯一约束、领域幂等与乐观锁共同保证任一时刻一 scope 只有一份有效 Settlement；不同 channel 或日期表达不得绕过该约束。

### 7.2 准备和候选解释

`PrepareSettlement` 枚举周期内所有候选成功支付、成功退款、冻结费用和确认调整，对每个候选创建带稳定 sourceRef/reasonCode 的 `INCLUDED | EXCLUDED` SettlementItem。未决对账、unknown 支付/退款、已结算事实和范围外事实必须显式排除；其他合格事实可继续纳入。相同 source fact 不得被两个 confirmed settlement 消费。

### 7.3 金额、确认冻结与负净额

净额固定为 `gross - refund - fee + adjustment`，并与 items 汇总完全一致。`ConfirmSettlement` 后冻结 merchant、currency、period、items、金额和 version；后到事实进入后续周期或独立 adjustment。负净额保留完整构成，进入 ManualReview 且不自动执行；零净额可完成但不调用 executor。

### 7.4 Execution identity 与三结果

`ExecuteSettlement` 使用稳定 execution identity。SUCCESS 只形成一次 settled fact；FAILURE 保存诊断，仅明确失败后按显式命令/policy 创建新的 attempt identity；UNKNOWN 保留原 identity，禁止新 identity、新 Settlement、void 或 replacement 重付，达到阈值后 `REVIEW_REQUIRED`。成功后迟到失败不回退；矛盾结果追加 receipt 与 review。

### 7.5 Void 与 replacement

只有未形成成功资金事实且不处于 UNKNOWN 的 Settlement 可带责任事实作废。replacement 只能引用已 VOIDED 原单，双向关联并保持同 scope 只有一份有效单；不能绕过冻结构成、范围唯一或原 execution 核对。

## 8. ManualReview、通知与 payment trace

### 8.1 ManualReviewItem

UNKNOWN、冲突、迟到、未知引用、未决差异、负净额等形成 `ManualReviewItem`，保存 type/status、related refs、blocking scopes、evidence、created/resolved 时间和全部追加处置。`ResolveManualReview` 由 §2.1 的可信 ReferenceActorContext 提供 actorId/role，由请求提供非空 reason/evidence/outcome；最终处置和查询暴露相同 actorId。context 缺失/未知或责任字段不完整时同步 `VALIDATION_ERROR` 且无副作用。提供权威列表、详情和处置历史。

### 8.2 MerchantNotification

支付、退款、对账和结算可形成 reference merchant notification intent。通知使用稳定 `notificationId + contentIdentity`，保留每个 delivery attempt；失败按 policy 以相同 identity/content 重试，不倒转业务事实。首次结算成功事实、稳定通知意图和既有本地 reliable event record 在同一 UoW 原子可见；不承诺持久化 Inbox、跨进程 exactly-once 或生产通知服务。

### 8.3 GetPaymentTimeline

按 paymentId 的权威 timeline 使用 `recordedAt ASC,eventId ASC` 稳定排序，同时保留外部 occurredAt，覆盖：支付创建与幂等重放、attempt、submission/result receipt、唯一成功/冲突/迟到、review/处置、退款/attempt/receipt/预算变化、通知/attempt、bill/revision/run/difference/disposition/confirmation、settlement item/execution/void/replacement 和人工动作。trace 来自后端跨聚合权威查询模型，不由 Controller 或前端拼装。

## 9. ReferencePolicy、逻辑时钟与 sandbox fixture

### 9.1 默认 policy

默认值为：`Asia/Shanghai`、`[CNY]`、paymentExpiry `PT30M`、unknownResultReviewAfter `PT5M`、operationPollRetryAfterMs `100`、operationObservationTimeout `PT30S`、refundWindow `P30D`、maxRefundAttempts `2`、billReadMaxAttempts `3`、billReadBackoff `PT1M`、feeRate `0.006`、roundingMode `HALF_UP`、merchantNotificationMaxAttempts `3`、negativeSettlementPolicy `MANUAL_REVIEW_NO_AUTO_EXECUTION`、largeRefundReviewThreshold `null`、page size `20/100`。每场景记录完整默认或显式覆盖；这些不是生产政策。

### 9.2 Reference executors

- channel 支持 `REJECT_ON_SUBMIT`、`ACCEPT_THEN_SUCCESS`、`ACCEPT_THEN_FAILURE`、`ACCEPT_THEN_UNKNOWN`、`NO_RESULT`，以及 duplicate/late/conflict/invalid/unknown-ref；
- bill provider 发布 immutable revision、stable record identity 并可模拟暂不可读；
- settlement executor 在稳定 executionId 下返回 SUCCESS/FAILURE/UNKNOWN、同 identity replay 与后续收敛；
- notification sender 在稳定 identity/content 下脚本化 delivery success/failure；
- 相同外部 identity 不产生第二业务效果。

### 9.3 Stable fixture 与干净运行

reference profile 提供稳定 merchant/channel/configuration、场景 alias、policy、actor context、logical clock 和 executor scripts。actor fixture 只向调用方暴露 opaque session alias；其 actorId/role 映射保存在服务端 ReferenceActorRegistry，HTTP runner 通过 `X-Reference-Actor-Context` 选择 alias 并可从最终责任事实复核解析结果。测试可通过 fixture API/runner 控制逻辑时间和 sandbox 输入，但不能直接改领域表、直接决定 verification 或伪造服务端责任事实。自动化和真实 HTTP 验收从干净 H2 运行启动；同 fixture 可使用不同随机内部 ID，但规范化 alias、状态、错误、关联、排序和副作用必须一致。

### 9.4 真实 HTTP 黑盒

67 个 PAY-AC 中的每一个都必须在启动实际 Spring Boot/CAP4K 服务后，由进程外 HTTP client 只经公开 reference/business HTTP surface 执行并产生独立可复核的真实 HTTP pass 证据。共享 runner 可以驱动这些 HTTP 场景，但 MockMvc、TestRestTemplate 同进程调用或进程内规范化测试均不得替代逐场景真实 HTTP 验收。支付、退款、账单登记/signal、Run/rerun、处置/确认、settlement、通知、review 和 trace 的所有必要观察必须通过真实 HTTP 读取。

在 67 项逐场景真实 HTTP 全部通过的基础上，还必须至少执行两次从干净 H2 运行开始的相同支付到结算完整闭环并比较规范化观察，作为额外可重复性证明；这两次闭环不能替代任何单项 PAY-AC 的 HTTP 证据。

## 10. CAP4K 工程、事务与对外契约

### 10.1 模块与 authoring ownership

保留 `contract` dependency-leaf、`domain`、`application`、`adapter`、`start` 模块及既有依赖方向。contract 不依赖 Spring/JPA/项目内模块；domain 不依赖 application/adapter/start。Endpoint/DTO/published event contract 为 checked-in source，Handler 与 HTTP binding 手写，schema/entity/repository/generated source 继续由既有 Pipeline ownership 管理。重复 generation 不覆盖已演进 checked-in source。

### 10.2 JPA 与 Unit of Work

所有写操作经 CAP4K Command/UoW；owned graph 使用同一 Hibernate persistence context、cascade、dirty checking 和 optimistic version。Refund+Payment budget、Run graph、Settlement graph、首次 settlement success+notification intent+reliable event 在明确事务边界原子提交。唯一性/乐观冲突映射稳定业务错误而非静默覆盖或 500。不得引入 detached merge、跨 ORM bridge 或 Controller 分散 save。

### 10.3 Domain event 与本地可靠事件

保留既有 `DomainEventSupervisor` 与 reliable Event/JPA owner。业务首次事实形成一次稳定 domain/integration event identity；回滚后业务事实与 event record 都不存在，提交后二者都可查询。at-least-once handoff 依赖业务幂等，不声称通用消息平台、Inbox 或 exactly-once。

### 10.4 Command 与 query catalog

规范化命令覆盖：CreatePaymentIntent、CreatePaymentAttempt、SubmitPaymentAttempt、ReceivePaymentChannelResult、CloseExpiredPayment、RequestRefund、Create/SubmitRefundAttempt、ReceiveRefundChannelResult、RefreshAuthoritativeBill、Run/RerunReconciliation、DisposeDifference、ConfirmFact、Prepare/Confirm/Execute/ReceiveResult/Void/ReplaceSettlement、ResolveManualReview、RetryMerchantNotification。

规范化查询覆盖：GetOperation；Payment 详情/最终状态/attempts/receipts/list；Refund 详情/attempts/budget/list；Bill/revisions；Run 详情/list/differences/history；Settlement 详情/list/items/executions；ManualReview 详情/list；notifications；payment timeline。CAP4K 路由可重整，不保留旧 API compatibility facade。

### 10.5 验证分层

必须提供 domain invariant tests、H2/JPA repository/UoW/rollback/concurrency tests、Endpoint contract tests、HTTP binding/integration tests、query/pagination/cursor tests、trusted verifier/fixture tests、scheduler/event tests、完整 composition tests、覆盖全部 67 个 PAY-AC 的进程外真实 HTTP runner，以及 clean build、boot startup、Pipeline generation、Analyzer、AgentFacts 与 traceability guards。每个 PAY-AC 同时具有自动化证据和真实服务 HTTP 证据；最终证据不得以 focused test、MockMvc 或进程内规范化测试替代全量回归或真实 HTTP。

## 11. Shared Backend Acceptance Scenarios

以下每个 `Scenario:` 都是独立验收项。未显式覆盖 policy 时使用 §9.1 全部默认值；均从干净运行或隔离 fixture 开始，并比较规范化业务观察而非随机内部 ID 字面值。每项必须同时由自动化测试和启动后进程外 HTTP client 通过公开 surface 执行；两类证据缺一不可。

### 支付

#### Scenario: PAY-AC-001 首次创建支付
给定合格商户与渠道，当以 K-001/O-001 创建 CNY 10000 支付时，则产生稳定 paymentId 的 PAYABLE 支付，且不显示已付款。

#### Scenario: PAY-AC-002 相同内容重复创建
给定 K-001 已创建支付，当完全相同请求重放时，则返回原 operation/payment，无第二资源或副作用。

#### Scenario: PAY-AC-003 幂等键冲突
给定 K-001 已用于 CNY 10000，当以相同 key 创建 CNY 12000 时，则同步 IDEMPOTENCY_CONFLICT，原金额不变且无新资源。

#### Scenario: PAY-AC-004 渠道成功通知
给定处理中支付及 attempt，当 trusted verifier 接受身份和 Money 匹配的成功结果时，则支付成功、记录交易号/时间并形成一次通知意图。

#### Scenario: PAY-AC-005 重复成功通知
给定 N-001 已令支付成功，当相同结果重复三次时，则只有一个成功事实/收入，所有接收与计数可查。

#### Scenario: PAY-AC-006 未验证通知
给定处理中支付，当收到验真失败或 Money 不匹配的成功声明时，则状态不成功，receipt 标记 REJECTED_INVALID 并保留原因。

#### Scenario: PAY-AC-007 支付到期关闭
给定支付到期、PAYABLE 且无未决 attempt，当到期扫描时，则支付 CLOSED 且不再主动尝试。

#### Scenario: PAY-AC-008 到期但结果未知
给定到期支付存在 unknown attempt，当扫描时，则不判失败/关闭并产生核对事项。

#### Scenario: PAY-AC-009 关闭后的迟到成功
给定支付 CLOSED，当可信成功迟到时，则 receipt 为 LATE、建立 review、阻断结算，且不静默丢弃或自动覆盖关闭事实。

#### Scenario: PAY-AC-010 两个尝试同时成功
给定同一支付两个在途 attempt，当二者分别可信成功时，则最多一个成功事实/收入，另一个为冲突证据并阻断结算。

#### Scenario: PAY-AC-011 已成功订单不得重复收款
给定 merchant order 已有成功支付，当以新 key 再次创建时，则同步 BUSINESS_CONFLICT 且无第二可执行支付。

#### Scenario: PAY-AC-012 无效金额或币种被拒绝
给定 CNY 精度与启用币种 policy，当金额为零、超过分精度或 USD 未启用时，则 VALIDATION_ERROR 且无 payment/attempt/operation。

#### Scenario: PAY-AC-013 已创建支付的金额不可修改
给定 CNY 10000 支付，当尝试改为 CNY 12000 时，则拒绝且原 Money 不变。

#### Scenario: PAY-AC-014 费用规则使用成功时快照
给定支付成功时 feeRate 0.006 后改为 0.008，当进入结算时，则仍使用成功时快照与舍入规则并可查。

#### Scenario: PAY-AC-015 成功后的失败结果不回退支付
给定支付已可信成功，当收到失败结果时，则 SUCCEEDED 不回退，冲突 receipt/review 保留。

#### Scenario: PAY-AC-016 渠道受理不等于支付成功
给定 reference channel 仅 ACCEPTED，当同步受理时，则支付 PROCESSING/RESULT_UNKNOWN，不形成收入、成功通知或结算候选。

#### Scenario: PAY-AC-017 支付成功后不再发起新尝试
给定支付 SUCCEEDED，当再次创建/提交 attempt 时，则同步拒绝且历史不变。

### 退款

#### Scenario: PAY-AC-020 全额退款
给定 CNY 10000 成功支付且无退款，当申请、attempt 并成功退款 CNY 10000 时，则 succeeded=10000、available=0，退款进入对账与结算扣减。

#### Scenario: PAY-AC-021 多次部分退款
给定 CNY 10000 成功支付，当先后成功退款 3000 与 2000 时，则 succeeded=5000、available=5000，两退款独立可追踪。

#### Scenario: PAY-AC-022 超额退款被拒绝
给定已成功退款 6000，当申请 5000 时，则 REFUND_BUDGET_EXCEEDED，available 仍 4000，且无 refund/attempt/channel submission。

#### Scenario: PAY-AC-023 并发退款防超退
给定 available=6000，当两个 4000 请求并发时，则最多一笔受理，另一笔稳定冲突，reserved+succeeded 不超 original。

#### Scenario: PAY-AC-024 退款失败释放占用
给定退款 4000 已预占且整体明确失败，当结果受理时，则 Refund FAILED，预占只释放一次，available 恢复。

#### Scenario: PAY-AC-025 退款结果待确认
给定退款已提交但结果未知，当 review 期限到达时，则 Refund 保持 RESULT_UNKNOWN/REVIEW_REQUIRED，预算继续占用并建立 review，不得同额重退。

#### Scenario: PAY-AC-026 重复退款申请
给定 merchantRefundNo R-001 与独立 idempotencyKey 已受理 2000 退款，当相同 payload 重放时，则返回原 operation/refund 且不再次预占。

#### Scenario: PAY-AC-027 非成功支付不得退款
给定支付为 processing/unknown/failed/closed，当请求退款时，则同步拒绝，无预占、refund 或 channel submission。

#### Scenario: PAY-AC-028 超过退款期限的申请被拒绝
给定成功支付超过 P30D 且无 reference 例外，当申请退款时，则同步拒绝、说明超期且预算不变。

#### Scenario: PAY-AC-029 成功退款后的失败结果不回退退款
给定退款已可信成功，当收到失败结果时，则 SUCCEEDED 与预算成功金额不回退，冲突证据/review 保留。

### 对账

#### Scenario: PAY-AC-040 完全匹配
给定平台 CNY 10000 成功事实和同交易号/Money/status 的完整账单记录，当运行对账时，则结果 MATCHED 且无差异。

#### Scenario: PAY-AC-041 平台单边
给定平台成功但完整账单无对应记录，当运行时，则产生 PLATFORM_ONLY，双方证据保留并阻断该交易结算。

#### Scenario: PAY-AC-042 渠道单边
给定账单成功记录无平台 payment/attempt，当运行时，则产生 CHANNEL_ONLY 与 review，不自动创建支付成功事实。

#### Scenario: PAY-AC-043 金额差异
给定平台 10000、账单 9900 且 identity 可关联，当运行时，则 AMOUNT_MISMATCH，双方原值保留并阻断结算。

#### Scenario: PAY-AC-044 状态差异收敛
给定平台 unknown、账单可信成功且身份/Money 匹配，当以可信 actor session alias 建立 ReferenceActorContext 并提交 reason/evidence 确认时，则追加 FactConfirmation，返回 context 对应 actorId，原事实与账单不改写且责任时间可查；缺失/未知 context 时同步拒绝且无副作用。

#### Scenario: PAY-AC-045 对账重跑不重复
给定某 scope 已运行并有三项差异，当相同或修订账单重跑时，则旧/新 run 可区分、无重复有效差异且只有一个 effectiveRun。

#### Scenario: PAY-AC-046 未决差异阻断完成
给定 run 有未处置金额差异，当尝试完成时，则保持 ACTION_REQUIRED/REVIEW_REQUIRED 并返回阻断详情。

#### Scenario: PAY-AC-047 差异处置不改写原始证据
给定金额差异，当以可信 actor session alias 建立 ReferenceActorContext 后处置时，则平台事实、账单记录、初始差异仍可查，DifferenceDisposition 追加并返回 context 对应 actorId；缺失/未知 context 时同步拒绝且无副作用。

### 商户结算

#### Scenario: PAY-AC-060 正常结算计算
给定收入 10000+5000、退款 2000、费用 200+100，当准备商户 CNY period 结算时，则 gross=15000、refund=2000、fee=300、net=12700 且逐 item 可追溯。

#### Scenario: PAY-AC-061 未决差异不进入结算
给定一个支付有 blocking difference，当准备结算时，则该候选 EXCLUDED 并有 reasonCode，其他合格候选可 INCLUDED。

#### Scenario: PAY-AC-062 防止重复结算
给定支付收入已计入 confirmed settlement，当准备后续 period 时，则该 source fact 不再作为 INCLUDED 收入。

#### Scenario: PAY-AC-063 结算执行成功
给定 confirmed CNY 12700 settlement 和稳定 executionId，当收到匹配 SUCCESS 时，则 SETTLED/FINAL，重复成功不形成第二事实。

#### Scenario: PAY-AC-064 结算执行结果未知
给定已执行但结果 UNKNOWN，当 review 期限到达时，则保持原 execution identity、进入 REVIEW_REQUIRED，禁止新 identity/新单/重付。

#### Scenario: PAY-AC-065 结算结果矛盾
给定 settlement 已成功，当同 execution 收到失败时，则 SETTLED 不回退，receipt/review 保留矛盾证据。

#### Scenario: PAY-AC-066 负结算额
给定 net=-3000，当准备时，则保存全部构成、进入 manual review 且不调用 executor。

#### Scenario: PAY-AC-067 结算确认后构成冻结
给定 settlement 已 confirmed，当发现后到退款/费用时，则原 scope/items/Money/version 不变，影响进入后续 period/adjustment。

#### Scenario: PAY-AC-068 同一周期不得存在两份有效结算单
给定 merchant+currency+period 已有有效单，当从不同 channel/date 表达再次 prepare 时，则 SETTLEMENT_SCOPE_CONFLICT；原单作废后替代单双向关联且只有一份有效单。

### 业务范围、通知与追踪

#### Scenario: PAY-AC-080 商户业务范围与筛选
给定 M-A/M-B 各有支付，当按 M-A list 和退款时，则列表只返回 M-A，跨 merchant 引用同步拒绝且无资金副作用；不宣称生产隔离。

#### Scenario: PAY-AC-081 商户通知失败重试
给定支付成功且首次通知失败，当后续重试成功时，则成功事实不重复，同一 notification/content 的全部 attempts 与最终状态可查。

#### Scenario: PAY-AC-082 资金事实更正留痕
给定核实的渠道单边漏记成功，当补录确认时，则原平台/渠道证据、reason、actor 与 server time 全部可查，无删除覆盖。

#### Scenario: PAY-AC-083 支付全链路追踪
给定支付经历两 attempt、receipts、成功、部分退款/预算、通知、bill/revision、run/difference/disposition、settlement，当按 paymentId 查询时，则完整 timeline 按 recordedAt/eventId 稳定排序，occurredAt/Money/identity 可核算。

#### Scenario: PAY-AC-084 退役渠道不再参与新支付
给定历史使用的 channel config 已 RETIRED，当创建新支付/attempt 时，则不再选择该渠道，历史 channel/fee snapshot 仍可查。

#### Scenario: PAY-AC-085 业务时区决定日界线
给定 Asia/Shanghai 23:59 和次日 00:01 两事实，当形成 businessDate/period 时，则分别归日并保留原 occurredAt 与 timezone。

#### Scenario: PAY-AC-086 人工动作责任字段完整并留痕
给定迟到成功需解除阻断，当可信 `X-Reference-Actor-Context` session alias 缺失/未知或 reason/evidence 不完整时，则同步 ApiError 且无 Operation/业务副作用；alias 可解析且责任字段完整时，追加并返回 registry 映射的 actorId、server time、reason/evidence。该场景不验证真实认证/RBAC。

#### Scenario: PAY-AC-087 账单可用信号与权威账单收敛
给定 bill/revision 可用且 scheduler、signal 重投、人工重跑并发乱序，当 provider 暂不可读后恢复时，则同 revision 只有一个有效 run，高 revision 前进、低 revision 不回退、同 signal identity 可诊断重试。

#### Scenario: PAY-AC-088 结算完成通知在当前运行期间原子可见并稳定重试
给定 settlement 首次 accepted success 且通知首次失败，当事务完成、重复 callback 或通知重试时，则成功事实与通知意图同 UoW 可见、identity/content 稳定、无第二成功事实；不宣称持久化 Inbox/跨进程 exactly-once。

### 统一受理、读取与 reference 闭环

#### Scenario: PAY-AC-090 同步拒绝与命令受理边界
给定非法 Money、幂等冲突、预算不足与正常命令，当分别提交并重放正常命令时，则同步拒绝只有 ApiError/无 Operation，正常为 ACCEPTED，重放原 operation 为 ALREADY_ACCEPTED，receipt 不等于领域成功。

#### Scenario: PAY-AC-091 已受理 Operation 按 readAfter 收敛
给定 receipt 为 POLL 且资源 projection 暂不可见，当先读 resourceUrl 再轮询 operationUrl 时，则前者 RESOURCE_NOT_READY 含 operationId/retryAfter，Operation 始终可读并仅在 SUCCEEDED/FAILED/REVIEW_REQUIRED 结束。

#### Scenario: PAY-AC-092 Operation 观察超时不是业务失败
给定 operation 持续 ACCEPTED/PROCESSING，当观察达到 PT30S 时，则只报告 OBSERVATION_TIMEOUT，operation 与领域状态不被改为失败且仍可查询。

#### Scenario: PAY-AC-093 五类权威列表以无快照 keyset 稳定分页
给定五列表跨 merchant/status/time 且有相同 timestamp，当小页翻页、首屏后新建更靠前记录并跨 filters 复用 cursor 时，则旧集合不重复不遗漏、新记录不回填后续页、跨筛选 INVALID_CURSOR。

#### Scenario: PAY-AC-094 ReconciliationRun 是唯一可重跑的对账执行资源
给定 bill revision 1/2、重复并发与迟到信号，当读取 revision 链并运行/重跑/查询时，则 Bill 只作输入证据，Run 是唯一主资源，高 revision 不回退、旧 run 保留且只有一个 effectiveRun，无并列 Batch API。

#### Scenario: PAY-AC-095 差异处置和补录确认追加且可解除阻断
给定 blocking difference，当可信 actor session alias 建立 ReferenceActorContext 并提交完整 disposition/confirmation 时，则原事实/revision/difference 保留、历史返回映射后的 actorId，只有明确结论解除阻断；缺失/未知 context 或责任字段时同步拒绝且无副作用。

#### Scenario: PAY-AC-096 结算准备记录纳入排除并在确认后冻结
给定 period 内含可结算、退款、费用、调整、未决、unknown 与已结算事实，当 prepare/confirm 时，则每候选有 INCLUDED/EXCLUDED、source/reason，净额仅用 included，确认后 scope/items/Money/version 冻结。

#### Scenario: PAY-AC-097 结算执行成功失败未知的稳定身份处置
给定 confirmed settlement 和可脚本化 executor，当分别处理 duplicate SUCCESS、明确 FAILURE 后显式重试、UNKNOWN 时，则成功一次、失败诊断/新 attempt 受控、unknown 保持原 identity 并禁重付。

#### Scenario: PAY-AC-098 结算作废与替代不绕过未知执行
给定一份可作废 settlement 与一份 RESULT_UNKNOWN，当作废前者并 replacement、尝试替代后者时，则前者双向关联且 scope 唯一，后者拒绝 void/replacement 并继续核对原 execution。

#### Scenario: PAY-AC-099 退款 attempt 结果只转换一次预算
给定 refund 已预占且 reference channel 注入 replay/retryable failure/unknown/success/late/conflict，当处理并查询时，则预占一次、释放/成功转换至多一次、unknown/review 保持占用且所有 receipts 可查。

#### Scenario: PAY-AC-100 支付渠道收件可查询且不改写单一成功事实
给定多 attempt、submission receipts、首个成功及 duplicate/invalid/unknown-ref/late/second-success，当查询与按外部 identity 查未知引用时，则每收件有稳定 disposition、事实不覆盖且支付最多一个 accepted success。

#### Scenario: PAY-AC-101 Reference Policy 覆盖保持本地结果确定
给定相同 fixture/clock 使用默认与显式覆盖 policy，当从干净运行重复场景时，则每组输入得到确定相同观察，policy 与 fixture 被记录且不宣称生产政策。

#### Scenario: PAY-AC-102 reference sandbox 可重复完成支付到结算闭环
给定固定 channel/bill/executor/notification scripts、fixture、policy 与 clock，当两个干净运行各完成同一闭环时，则状态、错误、关联、排序和副作用一致，同 identity replay 无第二效果。

#### Scenario: PAY-AC-103 两个参考后端执行同一黑盒场景
给定 CAP4K adapter 接受统一契约，当以同一 PAY-AC/fixture/policy/clock/executor 执行时，则 CAP4K 的规范化 Money、状态、finality、错误/details、关联、排序和副作用符合统一真源；本 change 不修改或声称 WOW 的验收状态。

## 12. 明确非目标与生产强化边界

本 capability 不交付登录、JWT/OIDC、真实 RBAC/双人授权、生产租户隔离、生产数据库部署/迁移/重启恢复、通用消息平台、持久化 Inbox、跨进程 exactly-once、多实例 scheduler lease、生产 gateway/CORS/CSRF/限流/TLS、Secret Manager、真实证书/账单下载/资金移动、生产 SLA/长期审计/脱敏或旧 API 兼容迁移。

这些非目标不得用于删除或延期领域级幂等、外部收件去重/冲突/迟到/未知引用/UNKNOWN、退款预算、对账阻断、结算冻结/UNKNOWN 防重付、责任字段、通知稳定身份、权威列表或当前运行期完整 trace。

## 13. Traceability 与交付证据

- 62 条 PAY-BR 通过 brief source coverage 与 §2-§9 实现约束映射到相应 PAY-AC。
- §11 的 67 个 `Scenario:` 必须逐项同时获得自动化 pass 证据与启动真实服务后的进程外 HTTP pass 证据，并记录实现位置、命令、请求/响应或规范化观察；MockMvc 与进程内测试不算真实 HTTP。失败、blocked、未运行、skip、超时或过期证据不算通过。两次干净完整闭环是额外重复性证据，不替代逐场景 HTTP 证据。
- 模板 `traceability.yaml` 的 `planned` 是外部模板状态，不修改，也不能用来否定或宣称本仓库实现；CAP4K 证据保存在本 change verification 与项目测试/evidence 中。
- 最终交付同时给出 domain/transaction/Endpoint/HTTP/black-box 证据、真实 CAP4K 服务 HTTP 运行证据和仅限 §12 的生产强化剩余项。
- 现有 Generator/Pipeline/Analyzer/AgentFacts、contract leaf、模块依赖、JPA/UoW 与本地 reliable event 的既有回归能力必须继续通过；新增统一契约不得以破坏这些 CAP4K 学习目标为代价。
