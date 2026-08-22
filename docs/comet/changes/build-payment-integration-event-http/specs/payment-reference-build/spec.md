# Payment Reference B1+B2+B3+B4+B5 可运行实现规格

## 1. 目标状态

`cap4k-reference-payment` 提供一个基于当前 cap4k mainline 合同的可运行支付、退款、日终对账、商户结算与最小可靠 HTTP Integration Event 系统。它在 B1 支付、B2 退款、B3 日终对账与差异处置、B4 单币种商户日结与资金划拨结果裁决之上增加 B5 入站账单 available event、出站 settlement completed event、可靠 Event/JPA 状态、HTTP transport、失败重试与业务幂等，证明独立 contract leaf、多个 Aggregate Root、Strong ID、Value Object、Command/Query/Capability/Endpoint、Domain Event、Integration Event、手写 HTTP binding、普通 `@Scheduled` reaction、Pipeline generation、Analyzer 和 AgentFacts 能在一条真实业务链中协同工作。

该状态是完整支付引用项目的前五个实现投影，不改变 `docs/requirements/**` 中的业务真源。B5 只声称 cap4k reliable Event/JPA 与 HTTP Integration Event transport 的最小可运行经验，不声称 broker、reliable Command、通用 Outbox/Inbox、持久化 scheduler、lease、跨实例 exactly-once、only-engine、生产银行/清算网络、大额退款审批、超期人工例外、负净额追偿或周结已经可用。

## 2. 工程与依赖合同

### 2.1 模块

项目必须包含：

- `contract`：dependency-leaf 对外契约模块，拥有 Endpoint operation、Request/Response 与稳定 Integration Event published language，只依赖轻量 `cap4k-contract-api` 及编译期分析 metadata，不依赖任何项目内模块、Spring、JPA 或 transport 实现。
- `domain`：Payment、Refund、Reconciliation 与 MerchantSettlement 领域模型、Value Object、生成 enum、领域事实与 Repository 契约。
- `application`：支付、退款、日终对账与商户结算的创建、渠道请求、结果确认、账单拉取、匹配、人工处置、重跑、确认、执行和查询输入/处理器；支付/退款 Gateway、结果验证、渠道账单、结算候选、资金划拨与划拨结果验证 Capability；入站 Integration Event listener 与 Domain Event 到 published Integration Event 的映射/发布编排。
- `adapter`：Endpoint Handler、手写业务 HTTP binding、Fake Payment/Refund Gateway、Fake Channel Statement Provider、Fake Settlement Transfer Provider、渠道/划拨结果验证器、普通 scheduled reaction 和 JPA 适配；Integration Event HTTP wire protocol 由 cap4k starter 拥有，不在 adapter 手写第二套协议。
- `start`：Spring Boot 组装、H2 配置、cap4k reliable Event/JPA owner 与 HTTP Integration Event starter、静态 event route、包含退款/对账/手续费/结算结果阈值的 fixture，以及动态 fake HTTP receiver 和端到端测试。

项目内依赖方向必须为：

- `application -> contract + domain`；
- `adapter -> contract + application + domain`；
- `start -> adapter`；
- `contract` 不依赖任何项目内模块；
- `domain` 不依赖 contract/application/adapter/start；
- application/domain 不依赖 start 或具体 Web 配置。

Pipeline 的 `contractModulePath` 必须解析到独立 `contract` project。所有 Endpoint contract 与 B5 Integration Event published language 必须物理位于该模块；既有 payment/refund FQN 保持不变，B3 reconciliation、B4 merchant-settlement 与 B5 event contract 使用同一 dependency-leaf 发布边界下的稳定 FQN。

### 2.2 版本与解析

- Java/Kotlin toolchain：17。
- Kotlin：2.2.20。
- Spring Boot：3.5.6。
- 默认声明的 cap4k/plugin/runtime 坐标：2.0.1。
- 默认仓库只允许 Gradle Plugin Portal 和 Maven Central。
- B1/B2/B3/B4/B5 必须通过用户显式提供的本机配置启用 Composite Build，对当前 cap4k mainline 完成构建、测试和分析验收。解析顺序固定为：先读取并规范化非空 Gradle property `cap4k.local.path`，再读取并规范化非空环境变量 `CAP4K_LOCAL_PATH`，二者均未提供时继续使用正式版 `2.0.1`；本地分支使用 `999.0.0-local` plugin marker 并对同一路径执行 `includeBuild`。仓库不得提交 sibling path、绝对路径、`mavenLocal()`、Snapshot、私服或机器本地 `gradle.properties`。published-coordinate cold start 不属于 B1/B2/B3/B4/B5 验收，保留给最终发布后验证。

### 2.3 Pipeline ownership

- cap4k 生成器产生的 generated source 由构建拥有，不进入手工编辑区。
- Endpoint contracts、Domain Value Object 和需要持续演进的 behavior skeleton 采用 checked-in ownership，首次生成后由业务代码维护，重复 generation 必须 SKIP 已演进文件。
- Endpoint Handler 与 HTTP binding 是 reference 手写 adapter 代码；不得伪造为本切片已有的 Generator capability。
- `cap4kPlan` 必须解释 generator、module role、output path、output kind、resolved root 和 conflict policy。
- 重复 plan/generation 在相同输入下保持确定性，不覆盖用户已修改的 checked-in source。

## 3. Generator 类型输入合同

### 3.1 Enum manifest

`design/enums.json` 是 B1/B2/B3/B4/B5 有限业务枚举的 authoring source，并通过 `types.enumManifest` 注册。至少声明以下 aggregate-owned enum：

- `PaymentStatus`：`PENDING(0)`、`PROCESSING(1)`、`SUCCEEDED(2)`、`FAILED(3)`、`CLOSED(4)`、`RESULT_UNKNOWN(5)`。
- `PaymentAttemptStatus`：`PROCESSING(0)`、`SUCCEEDED(1)`、`FAILED(2)`。
- `PaymentAttemptFinalResult`：`SUCCESS(0)`、`FAILED(1)`、`GATEWAY_REJECTED(2)`。
- `MerchantChannelConfigurationStatus`：`ACTIVE(0)`、`RETIRED(1)`。
- `ChannelResultDisposition`：`RECEIVED(0)`、`REJECTED(1)`、`SUCCESS_ACCEPTED(2)`、`FAILURE_ACCEPTED(3)`、`ACCEPTED_DUPLICATE(4)`、`REJECTED_DUPLICATE(5)`、`CONFLICT(6)`、`ATTEMPT_NOT_FOUND(7)`。

这些 numeric value 一经生成并进入持久化或可观察证据后保持稳定。

### 3.2 Schema type binding

以下内部有界状态列必须使用整数存储，并通过完整 `@Type=<EnumName>;` metadata 绑定：

- `payment.status -> PaymentStatus`；
- `payment_attempt.status -> PaymentAttemptStatus`；
- `payment_attempt.final_result -> PaymentAttemptFinalResult`；
- `payment_notification_receipt.decision -> ChannelResultDisposition`；
- `merchant_channel_configuration.status -> MerchantChannelConfigurationStatus`。

领域代码直接读写生成 enum，不使用重复手写 enum，也不通过 `.name` 把 enum 写回实体状态列。

以下开放 vocabulary 或外部证据保持标量，不加入 enum manifest：

- inbound Endpoint/Command 的渠道 raw `result`；
- `payment_notification_receipt.result` 原始/归一化渠道声明；
- currency 与 payment method；
- merchant、channel、request、notification、transaction、order、idempotency 等外部 identity；
- verification material、snapshot 与诊断 summary。

### 3.3 Non-persistent Domain Value Object

`design/value-objects.json` 必须声明 Payment aggregate-owned 的 `ChannelResultRecordingOutcome`，不包含 `persistence` 配置。生成结果是 checked-in Domain Value Object，不生成 JPA converter、不绑定 schema 列、不拥有独立实体身份。

该 VO 至少包含：

- `paymentStatus: PaymentStatus`；
- `attemptStatus: PaymentAttemptStatus?`；
- `notificationReceiveCount: Int`；
- `disposition: ChannelResultDisposition`；
- `rejectionSummary: String?`；
- `conflictSummary: String?`；
- `successFactFormedNow: Boolean`。

生成后的 checked-in VO 必须补充派生属性：

- `duplicate` 由 `ACCEPTED_DUPLICATE` 或 `REJECTED_DUPLICATE` 推导；
- `accepted` 由 `SUCCESS_ACCEPTED`、`FAILURE_ACCEPTED` 或 `ACCEPTED_DUPLICATE` 推导；
- `rejected` 由 `REJECTED`、`REJECTED_DUPLICATE` 或 `ATTEMPT_NOT_FOUND` 推导；
- `conflicting` 由 `CONFLICT` 推导。

VO 必须拒绝无效组合：

- `notificationReceiveCount` 小于 1；
- 最终 Outcome 使用 `RECEIVED`；
- `ATTEMPT_NOT_FOUND` 以外的 disposition 配置 null `attemptStatus`，或 `ATTEMPT_NOT_FOUND` 配置非空 `attemptStatus`；
- rejected disposition 没有 rejection summary，或非 rejected disposition 携带 rejection summary；
- `CONFLICT` 没有 conflict summary，或非 `CONFLICT` 携带 conflict summary；
- `successFactFormedNow=true` 但 disposition 不是 `SUCCESS_ACCEPTED`；
- duplicate outcome 再次形成成功事实；
- `SUCCESS_ACCEPTED` 未对应 `PaymentStatus.SUCCEEDED` 和 `PaymentAttemptStatus.SUCCEEDED`；
- `FAILURE_ACCEPTED` 未对应 `PaymentAttemptStatus.FAILED`。

## 4. 领域模型

### 4.1 Payment

Payment 是聚合根，至少包含：

- `PaymentId`；
- merchant identity；
- merchant order number；
- idempotency key；
- Money；
- payment method；
- typed `PaymentStatus`；
- createdAt、expiresAt；
- succeededAt、channelTransactionId（成功后）；
- PaymentAttempt 集合；
- 聚合版本和审计字段。

B1 支付路径只主动推进 `PENDING -> PROCESSING -> SUCCEEDED`，但模型不得允许成功回退。B2 在成功 Payment 上增加退款预算字段，不改变支付成功事实。

### 4.2 PaymentAttempt 与通知记录

PaymentAttempt 是 Payment 的强引用 owned child，至少包含：

- `PaymentAttemptId`；
- channel identity 与渠道配置快照；
- request identity；
- typed `PaymentAttemptStatus`；
- initiatedAt；
- channelTransactionId；
- typed `PaymentAttemptFinalResult` 与业务发生时间；
- 每个 notification identity 的接收计数、首次/最近接收时间、raw result、typed `ChannelResultDisposition` 和裁决摘要。

尝试之间不能覆盖。Payment 成功后不得创建新尝试。

`ChannelResultDisposition.RECEIVED` 只允许作为 receipt 保存后、裁决完成前的内部状态；最终 receipt 必须进入 accepted、duplicate、rejected 或 conflict disposition。Attempt 不属于当前 Payment 时，不伪造 `PaymentAttemptStatus.NOT_FOUND`，而由 Outcome 的 null `attemptStatus` 与 `ATTEMPT_NOT_FOUND` 表达。

### 4.3 Money 与身份

- Money 使用精确十进制金额和 ISO 风格币种代码；拒绝非正数、超过币种精度或应用资格配置未支持的币种。
- PaymentId 和 PaymentAttemptId 使用 cap4k Strong ID，应用侧按当前 UUID7 合同分配。
- merchant identity、channel identity 和外部业务号保持稳定、可追踪，不把完整凭据放入聚合。

### 4.4 MerchantChannelConfiguration

B1/B2 提供最小持久化模型：merchant、channel、currency、payment method、amount range、typed active/retired status、refundWindowDays、refundResultReviewAfterMinutes 及必要审计信息。start 层建立一条 M-001、CNY、测试支付方式可用、退款期限 180 天、退款结果核对阈值 30 分钟的配置。

配置只用于资格判断和渠道选择，不提供管理 API。PaymentAttempt 保存当时的渠道身份和规则摘要，因此以后配置退役不会改写交易历史。

## 5. 应用行为

### 5.1 创建支付

输入至少包括 merchant、merchant order number、idempotency key、amount、currency、payment method 和 expiresAt。

处理顺序：

1. 验证格式、金额、币种和到期时间；
2. 验证 merchant 至少存在一条合格的 active channel configuration；
3. 按 merchant + idempotency key 查询既有 Payment；
4. 内容相同则返回既有结果；关键内容不同则返回明确幂等冲突；
5. 不存在时创建 `PENDING` Payment 并持久化。

创建成功不自动形成 PaymentAttempt，也不表示支付成功。

### 5.2 发起支付尝试

输入 PaymentId。处理器装载 Payment，确认仍允许尝试，选择合格配置并在聚合内新增 PaymentAttempt，然后调用 Channel Gateway。

Fake Gateway 返回稳定 request identity 和 `ACCEPTED`。`ACCEPTED` 只让尝试/Payment 进入处理中，不得形成成功事实。Gateway 调用失败的完整重试策略后置，但失败必须留下可诊断信息而不能伪造成功。

### 5.3 确认渠道结果

入站结果至少包含 channel、notificationId、PaymentId/PaymentAttemptId、channelTransactionId、amount、currency、raw result、occurredAt 和验证材料。

适配层只把 HTTP 输入转换为稳定 Command；Command Handler 在 UoW 边界内调用结果验证 Capability，再把验证结果交给聚合。聚合按以下顺序裁决：

1. 记录接收时间和 notification identity；
2. 识别重复通知并增加计数；
3. 校验 attempt/channel/payment/amount/currency；
4. 不可信或不匹配时保留拒绝原因，不推进 Payment 状态；
5. 首次可信成功时把 attempt 和 Payment 确认为成功，记录渠道交易号和两个时间；
6. 重复成功形成 accepted-duplicate disposition，不产生第二个成功事实；
7. 重复未接受证据形成 rejected-duplicate disposition；
8. 成功后的失败结果不回退状态，形成 conflict disposition 并留存；
9. 返回 `ChannelResultRecordingOutcome`，其字段和派生属性来自最终持久化裁决，不建立第二套 accepted/duplicate 真源。

B1 记录“商户成功通知意图”领域事实，但不发送真实 Integration Event 或网络通知。

### 5.4 Command 与 Endpoint 结果边界

`Payment.recordChannelResult(...)` 返回 `ChannelResultRecordingOutcome`。

`ConfirmPaymentResultCmd.Response` 直接持有该 Domain Value Object；application 允许依赖 domain，因此不做无价值的逐字段复制。若 cap4k Request contract 固定要求 Response wrapper，则 wrapper 只包含 `outcome` 字段。

独立 `contract` 模块不得依赖 domain。`ConfirmPaymentResultEndpoint.Response` 继续使用 contract-owned scalar/DTO 字段；adapter Endpoint Handler 从 Command Response 中取得 Outcome，并在该发布边界显式映射 enum name、计数、派生布尔值和摘要。

### 5.5 查询

按 PaymentId 返回：

- Payment 身份、商户订单号、Money、状态和关键时间；
- 所有 PaymentAttempt 的身份、渠道、状态、请求/交易号；
- 渠道结果的接收计数、typed disposition、验证/拒绝/冲突摘要；
- 成功事实是否已经形成。

查询必须来自真实持久化数据，不返回 domain/JPA proxy 给 HTTP 层。Query/Endpoint 对外输出字符串状态时由 adapter 显式使用 enum name 映射，不把 contract 反向绑定到 domain enum。

## 6. Endpoint 与 HTTP authoring 合同

B1 提供四个 transport-neutral Endpoint contract：

- `CreatePaymentEndpoint`；
- `StartPaymentAttemptEndpoint`；
- `ConfirmPaymentResultEndpoint`；
- `GetPaymentEndpoint`。

每个 Endpoint Handler 在 adapter 中独立成文件，并采用：

- 写操作：`Mediator.commands.send(...)`；
- 查询操作：`Mediator.queries.ask(...)`。

一类一文件和静态 `Mediator` 是本 reference 的默认 authoring preference，不是 cap4k 对所有项目的强制规则。

HTTP binding 保持手写，继续提供：

- `POST /api/payments`：创建 Payment；
- `POST /api/payments/{paymentId}/attempts`：发起一次 PaymentAttempt；
- `POST /api/channel/payment-results`：接收测试渠道最终结果；
- `GET /api/payments/{paymentId}`：查询 Payment 和尝试摘要。

HTTP method/path、request mapper、response policy、status code 和 error mapping 不因 Handler 拆分或 contract 搬迁而漂移。错误结果至少区分输入校验失败、幂等冲突、无合格渠道、Payment 不存在、状态不允许、回调被拒绝和并发冲突。测试用验证器的可信凭据通过配置提供，任何凭据不得提交为生产秘密。

## 7. Runtime 与持久化合同

- 所有写操作通过 cap4k Command/UoW 边界运行。
- Command 聚合 Repository 使用同一 Hibernate/JPA 持久化上下文；已有聚合更新依赖 managed entity dirty checking，新聚合/删除遵循 cap4k persistence intents。
- PaymentAttempt 和 notification receipt 生命周期由 Payment 聚合维护，JPA cascade/orphan 语义与 owned graph 一致。
- enum converter 与整数 schema 列必须一致，H2/JPA round-trip 后仍得到相同 enum value。
- 使用乐观版本防止并发回调静默覆盖；典型并发由集成测试证明，极端 ORM/并发压力仍由 cap4k focused fixtures 负责。
- H2 用于 reference 的可重复执行，不宣称生产数据库兼容矩阵。

## 8. Generator、Analyzer 与 AgentFacts 合同

- DB/schema、Design JSON、enum manifest 和 value-object manifest 共同提供真实 canonical input，不接受纯手写项目冒充 Generator 证据。
- plan 和输出必须展示 Aggregate、Strong ID、Repository、Behavior、Enum、Value Object、Command/Query/Endpoint 等本切片实际采用的能力；未采用能力不得仅为覆盖率制造空壳。
- contract module 必须启用 analysis compiler，并加入 root `irAnalysis.inputDirs`；移动 Endpoint contract 后不得丢失 design metadata。
- B1/B2 必须通过显式 Composite Build 消费包含 Mermaid quoted-label 修复的 cap4k mainline，重新运行 `cap4kAnalysisPlan` 与 `cap4kAnalysisGenerate`。
- Analyzer 为创建支付、发起支付尝试和渠道回调的真实 Endpoint HTTP Actor 入口分别生成 entry-centered Flow，展示入口到对应 Command 的静态 causal reachability；Command 与 Query anchors 由 Drawing Board Design Projection 提供，Payment/PaymentAttempt 的 Aggregate element 由独立 Aggregate Structure output 提供。
- 三份 Flow JSON 保持预期 entry、node 与 edge 语义；三份 Mermaid 节点 label 必须被安全引用，并通过 Mermaid parser 或 renderer smoke，不再出现内部 `[...]` 触发的 parse error。
- Query、CommandHandler、Entity Method、聚合运行时状态推进和跨真实入口 process stitching 不属于默认 Flow 验收；不把支付超时伪造成已实现 Time 根。
- Agent Snapshot 必须能读取项目有效状态、输入、生成器、任务、输出和 diagnostics；没有构建或运行证据的能力保持 planned/not-built。

## 9. 测试与证据

必须覆盖以下业务场景：

- PAY-AC-001：首次创建支付；
- PAY-AC-002：相同内容重复创建；
- PAY-AC-003：幂等键冲突；
- PAY-AC-004：可信成功通知；
- PAY-AC-005：重复成功通知；
- PAY-AC-006：未验证、attempt/channel/amount/currency 不匹配通知；
- PAY-AC-012：无效金额/币种拒绝；
- PAY-AC-013：已创建金额不可修改；
- PAY-AC-016：渠道受理不等于成功；
- PAY-AC-017：成功后不再发起尝试。

证据至少包括：

- contract leaf 的依赖与编译证据；
- domain enum generation、schema type binding、converter round-trip 与 Outcome invariant tests；
- H2/JPA Repository/UoW integration tests；
- Endpoint contract、每类一文件 Handler、静态 Mediator 和 handwritten HTTP binding tests；
- HTTP happy path 与错误/重复/冲突 integration tests；
- plan/generation deterministic evidence；
- Analyzer plan、三个 Endpoint HTTP Actor-to-Command Flow JSON/Mermaid、Mermaid parse/render smoke，以及独立 Command/Query Drawing Board 和 Aggregate Structure outputs；
- Agent manifest 与 diagnostics；
- actual evidence path/command/cap4k commit/status 写回 traceability，并包含 tracked `settings.gradle.kts` 的 property > environment > released `2.0.1` 解析合同及“仓库无默认机器路径”的 smoke 证据。

## 10. 状态投影

仓库业务 phase 保持 build 且 build_authorized: true。B1 已在 mainline 验收并保持回归基线。B2 的新增聚合、配置、Endpoint、scheduled reaction、类型输入和验收必须使用本 change 的新证据；只有新实现与新证据验证通过时，才把 PAY-AC-020..029 及对应 evidence 更新为 verified。B1 的旧证据不能替代 B2 当前候选验证。

## 10A. B2 退款与部分退款目标合同

### 10A.1 聚合边界与预算权威

- Refund 是独立 Aggregate Root，以 Strong RefundId 标识，通过 Strong PaymentId 弱引用 Payment；Refund 不持有 Payment 对象、ORM relation、lazy proxy 或可导航聚合图。
- Payment 继续是支付成功事实和退款预算的并发裁决权威。Payment 至少增加 reservedRefundAmount、successfulRefundAmount，并派生 refundableAmount。
- 原子不变量为：reservedRefundAmount >= 0、successfulRefundAmount >= 0，且 reservedRefundAmount + successfulRefundAmount <= paymentAmount。
- 只有 SUCCEEDED Payment 可以占用退款额度。创建 Refund 时先在 Payment 聚合内占用额度，再在同一 cap4k Command/UoW 与同一 Hibernate/JPA persistence context 中创建 Refund；任一步失败必须整体回滚。
- Refund 明确失败或拒绝时释放占用；结果未知或进入待核对时继续占用；成功时将对应占用原子转换为 successfulRefundAmount。Refund 成功事实不可回退。

### 10A.2 Refund 结构与证据

Refund 至少保存 RefundId、PaymentId、merchant identity、merchant refund number、Money、原渠道/支付方式/配置快照、typed RefundStatus、请求/受理/最终/核对时间、渠道 request/refund identity、额度裁决和成功事实。

Refund 使用 RefundAttempt 与 RefundNotificationReceipt owned child 保存渠道请求、notification identity、首次/最近接收时间、重复次数、raw result、typed disposition、验证/拒绝/冲突摘要。查询必须能够观察这些持久化事实，不能只返回最后一条瞬时摘要。

### 10A.3 退款期限与待核对阈值

MerchantChannelConfiguration 是规则配置真源：

- refundWindowDays：B2 seed/default fixture 固定为 180；申请最后期限从 Payment.succeededAt 起算，而不是 Payment.createdAt 或渠道受理时间。
- refundResultReviewAfterMinutes：B2 seed/default fixture 固定为 30；渠道已经受理但超过该阈值仍无最终结果时，Refund 转为 REVIEW_REQUIRED 并继续保留占用。

B2 不实现大额退款人工审批，也不实现超期退款人工例外或 override；二者保留为后续独立 change。

### 10A.4 生成类型与 Domain Outcome

设计输入必须新增并生成/类型绑定 Refund 有限业务枚举，至少包括 RefundStatus、RefundAttemptStatus、RefundAttemptFinalResult 与 RefundResultDisposition。内部有限状态使用整数列和完整 @Type 绑定；外部 raw code、message、identity 继续保持开放标量。numeric value 一经用于持久化或证据后保持稳定。

value-objects.json 必须声明不配置 persistence 的 RefundResultRecordingOutcome。它是聚合行为产生的瞬时 Domain Value Object，可直接成为 Command Response 的字段；独立 contract leaf 不依赖 domain，因此 Endpoint Response 由 adapter 在发布边界显式投影。Outcome 至少表达最终 Refund/attempt 状态、notification 接收计数、typed disposition、额度是否释放或转成功、是否进入待核对及拒绝/冲突摘要，并拒绝互相矛盾的组合。

checked-in enum、VO、Endpoint contract、Behavior 和手写 adapter 重复 generation 必须 SKIP；build-owned Entity、Schema、Repository 可重建。

### 10A.5 应用行为

创建退款输入至少包括 merchant、merchant refund number、PaymentId、退款金额、币种和请求时间：

1. merchant + merchant refund number 相同内容重复提交返回同一 RefundId；关键内容冲突明确失败。
2. 装载 Payment，确认 SUCCEEDED、merchant/币种匹配，且请求时间不晚于 succeededAt + refundWindowDays。
3. 在 Payment 内原子占用退款额度；超出 refundableAmount 时不创建渠道请求。
4. 在同一事务创建 Refund/RefundAttempt 并保存规则快照。
5. Fake Refund Gateway 受理只进入处理中；明确拒绝或失败释放占用；未知结果继续占用并留痕。

退款结果 callback 至少携带 channel、notificationId、RefundId/RefundAttemptId、channelRefundId、amount、currency、raw result、occurredAt 和验证材料。Handler 在 UoW 内调用验证 Capability，并按首次成功、首次失败、可信未知、重复、冲突、不可信或不匹配进行幂等裁决。成功后的失败或未知不得回退成功事实。

B2 提供普通 @Scheduled reaction。scheduled method 只通过 Mediator.commands.send(...) 发送扫描 Command，不直接操作 Repository。扫描将已受理、超过 refundResultReviewAfterMinutes 且仍无最终结果的 Refund 转为 REVIEW_REQUIRED，并保留 Payment 占用。该入口证明 Analyzer Time root，不承诺 B5 的可靠 enqueue、持久化调度、lease/retry 或跨实例 exactly-once。

### 10A.6 Endpoint、Handler 与查询

B2 至少增加 CreateRefundEndpoint、ConfirmRefundResultEndpoint 与 GetRefundEndpoint，继续位于独立 contract module。对应 HTTP binding 保持手写：

- POST /api/refunds；
- POST /api/channel/refund-results；
- GET /api/refunds/{refundId}。

每个 Endpoint Handler 一类一文件并使用静态 Mediator；这是 reference authoring preference，不是 cap4k 强制规则。scheduled scan 不是 HTTP Endpoint，不为覆盖率伪造 transport contract。

Payment 查询增加 reservedRefundAmount、successfulRefundAmount 与 refundableAmount。Refund 查询返回身份、PaymentId、merchant refund number、Money、状态、关键时间、渠道 identity、额度裁决、attempt 与 notification 摘要；不得把 JPA aggregate 或 proxy 暴露给 contract/HTTP。

### 10A.7 并发、错误与运行时边界

- 两笔并发 Refund 对同一 Payment 的额度占用必须由真实双事务、H2/JPA、乐观锁测试证明不会超退。
- 典型乐观冲突映射为稳定 409 CONCURRENT_MODIFICATION，而不是静默覆盖或 500。
- Payment 与 Refund 的预算变更和状态推进必须共用 cap4k JPA UoW；不引入跨 ORM bridge、detached merge 或补偿事务来伪造原子性。
- B2 使用 Fake Gateway 与 HTTP callback，不引入真实渠道 SDK、Outbox、Integration Event transport、Saga 或 only-engine gate。

### 10A.8 Analyzer、AgentFacts 与证据

Analyzer 必须生成退款申请与退款渠道结果的 Endpoint HTTP Actor-to-Command Flow，并为普通 @Scheduled 到 Mediator.commands.send(...) 的超时核对入口生成独立 Time root。Command/Query anchors 与 Refund Aggregate Structure 分别由 Drawing Board 和 Aggregate Structure output 证明；不把跨入口流程拼接或运行时状态变化伪造成默认 Flow 能力。

Agent Snapshot 必须保留 B2 plan ownership、analysis 与 diagnostics。live DB freshness UNKNOWN 可以导致 PARTIAL，但 ownership 不得为空，且不得出现 INVALID、error 或 plan-evidence-invalid。

B2 验收必须覆盖 PAY-AC-020..029：全额退款、多次部分退款、超额拒绝、并发防超退、失败释放、未知或 30 分钟后待核对仍占用、merchant refund number 幂等、非成功支付拒绝、180 天 seed/default 期限拒绝、成功后失败通知不回退。证据至少包括 domain invariant tests、H2/JPA/UoW 跨聚合测试、双事务并发测试、HTTP 集成测试、scheduled review test、enum/converter/Outcome tests、plan/generation determinism、Analyzer Flow/Time root、AgentFacts 与 traceability 实际路径。

B1 的 PAY-AC-001..017 已验收行为是回归基线，B2 不得破坏既有支付创建、支付尝试、支付回调、查询、contract leaf、Mermaid flow 和 Composite Build 解析合同。

## 10B. B3 日终对账与差异处置目标合同

### 10B.1 聚合边界与范围身份

- ReconciliationBatch 是独立 Aggregate Root，以 Strong ReconciliationBatchId 标识；同一 `channel + currency + reconciliationDate` 只能存在一个有效批次。
- Payment、Refund、PaymentAttempt、RefundAttempt 不进入 Reconciliation ORM graph。对账侧只保存 Strong ID、稳定外部 identity 和运行时复制的不可变事实快照。
- ReconciliationBatch 至少拥有 ReconciliationRun、ReconciliationItem、ReconciliationDisposition 和 ReconciliationConfirmationFact。Run/Item/Disposition/Confirmation 均属于批次强引用 owned graph，并由同一 JPA UoW 原子保存。
- 每次执行形成独立 ReconciliationRun；批次记录当前有效 run，但历史 run 与 item 永久保留。重跑不创建第二个有效批次，也不覆盖旧运行。

### 10B.2 账单拉取与时间边界

- B3 的主路径是普通 `@Scheduled` Time root 发送日终对账 Command；scheduled method 只调用静态 Mediator，不直接操作 Repository。
- Command 通过 PullChannelStatement Capability 拉取渠道日账单。B3 不实现 bill-arrival Integration Event、Outbox、持久化调度、lease/retry 或跨实例 exactly-once。
- 账单必须携带 channel、currency、reconciliationDate、statement identity、revision、完整性状态，以及每条 record identity、交易类型、渠道交易/退款 identity、金额、币种、状态、业务发生时间和平台接收时间。
- 业务时区固定为 `Asia/Shanghai`。reconciliationDate 以该时区的自然日边界计算；原始业务发生时间、平台接收时间与业务时区均保留。
- 渠道账单最长等待期限为业务日结束后 24 小时。期限内允许同一批次重试拉取；超过后批次进入 FETCH_FAILED/REVIEW_REQUIRED 且不得完成。迟到账单到达后在同一批次追加新 run 恢复处理。

### 10B.3 平台事实与匹配

- B3 从 Payment/Refund 持久化事实投影平台侧 reconciliation facts，至少覆盖支付成功与退款成功；平台查询可以使用专用 Query/Capability/JPA projection，但不得滥用聚合 Repository 返回 detached 写模型或建立跨聚合关系图。
- 首选匹配键是稳定渠道交易号或渠道退款号。只有明确记录并经批准的组合条件可以作为辅助匹配；关联依据必须写入 ReconciliationItem。禁止只因金额相同自动认定同一交易。
- 每次 run 必须分别持久化平台侧快照、渠道侧快照、关联依据和自动裁决结果。双方原始值不得被另一方覆盖。
- 自动分类至少包括：MATCHED、PLATFORM_ONLY、CHANNEL_ONLY、AMOUNT_MISMATCH、CURRENCY_MISMATCH、STATUS_MISMATCH、DUPLICATE_CHANNEL_RECORD、UNMATCHED。
- 支付和退款使用稳定的 transaction kind/type 区分；金额和币种保持精确语义，首期不支持换汇或跨币种抵扣。

### 10B.4 重放、修订与有效结果

- 同一 statement identity + revision 的重复处理幂等：不产生第二个有效 run、不重复形成有效差异、不重复追加自动结论。
- 渠道修订账单必须使用新 revision 或新 statement identity。修订触发同一批次的新 ReconciliationRun，旧 run/items 保留并可查询。
- 批次明确记录 currentEffectiveRunId。旧 run 不再是当前结果，但仍是不可变历史证据。
- 新 run 不得静默删除既有人工 disposition 或 confirmation。应用层以稳定 difference identity 关联历史处置，并明确显示其是否仍适用于当前 run。

### 10B.5 完成条件与结算阻断

- 批次状态至少表达 PENDING、FETCHING、RECONCILING、AWAITING_DISPOSITION、COMPLETED、FETCH_FAILED/REVIEW_REQUIRED。
- 批次只有在 statement 完整、当前 run 完成、所有平台/渠道记录均已核对，且每个差异已匹配或存在明确处置结论时才能完成。
- PLATFORM_ONLY、CHANNEL_ONLY、AMOUNT_MISMATCH、CURRENCY_MISMATCH、STATUS_MISMATCH、DUPLICATE_CHANNEL_RECORD 和 UNMATCHED 默认阻断自动结算，除非授权 disposition 明确说明不影响结算及依据。
- 查询必须返回匹配/差异数量、未决数量、statement/run identity、当前有效 run、完成阻断原因和每项双方证据。

### 10B.6 人工处置与新确认事实

- ReconciliationDisposition 是追加式审计记录，至少包含 operator identity、authorization result/role、disposedAt、evidence、conclusion、follow-up、settlement impact 和关联的 difference identity/item identity。
- 未授权动作必须拒绝并保存审计结果；B3 使用 reference 级 operator fixture 证明授权边界，不建设通用 RBAC 或双人复核引擎。
- 处置不能删除或覆盖原始 Payment/Refund、渠道记录、run/item 或早期 disposition。
- 当平台结果待确认而渠道账单显示成功，或获授权人员确认 CHANNEL_ONLY 实际为平台漏记成功时，B3 追加 ReconciliationConfirmationFact。该事实至少保存 source difference、确认原因、operator、confirmedAt、evidence、资金类型、金额、币种和稳定外部 identity。
- ReconciliationConfirmationFact 不反向改写 Payment/Refund 原始状态；后续 B4 读取“原始资金事实 + 有效授权确认事实”形成结算候选视图。

### 10B.7 生成类型与 ownership

- design/schema 必须生成 ReconciliationBatch aggregate graph、Strong IDs、Repository、Factory/Behavior、Command/Query/Capability/Endpoint 与有限业务 enum。
- 业务 enum 至少覆盖 batch status、run status、transaction kind、difference type、disposition status/conclusion 和 statement completeness。内部有限状态使用整数列与完整 `@Type` 绑定；渠道 raw status/code/identity 保持开放标量。
- 对账过程中需要跨层传递的 immutable structured values 可以由 value-object manifest 生成且不配置 persistence；是否作为 Command/Endpoint 返回值不限制其 Domain Value Object 身份。

### 10B.8 应用行为与 Endpoint

B3 至少包含以下应用入口：

1. `RunDailyReconciliation`：按 channel/currency/date 幂等创建或装载批次、拉取账单、读取平台 facts、创建 run/items 并推进批次。
2. `RerunReconciliationBatch`：对既有批次显式创建新 run；相同 revision 重放幂等，新 revision 保留历史。
3. `DisposeReconciliationDifference`：验证 operator 授权并追加 disposition；必要时追加 confirmation fact，然后重新评估批次完成条件。
4. `GetReconciliationBatch`：返回批次、全部 runs、当前有效结果、双方证据、差异、处置、确认事实和阻断原因。

独立 contract module 至少增加：

- `GET /api/reconciliation-batches/{batchId}`；
- `POST /api/reconciliation-batches/{batchId}/reruns`；
- `POST /api/reconciliation-items/{itemId}/dispositions`。

每个 Endpoint Handler 一类一文件并使用静态 Mediator；HTTP binding 保持手写。scheduled Time root 不为覆盖率伪造 HTTP Endpoint。

### 10B.9 并发、UoW 与错误合同

- 同范围批次必须有数据库唯一约束与聚合幂等共同保护。并发 scheduler/HTTP rerun 不得形成两个有效批次或同 revision 双 run。
- ReconciliationBatch 及完整 owned graph 的状态推进共用 cap4k JPA UoW 与同一 Hibernate persistence context；不引入跨 ORM bridge、detached merge 或分散 save。
- 典型乐观锁/唯一性并发冲突映射为稳定 409 CONCURRENT_MODIFICATION，而不是静默覆盖或 500。
- Provider 不可用、账单不完整和匹配失败必须形成可查询运行证据；不得把账单不可用伪装成“空账单并完成”。

### 10B.10 Analyzer、AgentFacts 与证据

- Analyzer 必须生成日终 scheduler 到 Command 的独立 Time root，以及人工处置和显式重跑的 Endpoint HTTP Actor-to-Command Flow。Command/Query/Capability anchors 与 Reconciliation Aggregate Structure 分别由 Drawing Board/Aggregate Structure output 证明。
- Mermaid 必须可解析；不把 Query、隐藏 handler、运行时状态变化或跨入口业务链伪造成默认 Flow stitching。
- Agent Snapshot 必须保留 B3 plan ownership、analysis 与 diagnostics。live DB freshness UNKNOWN 可以导致 PARTIAL，但 ownership 不得为空，且不得出现 INVALID、error 或 plan-evidence-invalid。
- B3 验收必须覆盖 PAY-AC-040..047、PAY-AC-082 和 PAY-AC-085。PAY-AC-083 只写入 B3 增量轨迹，不在 B4 结算完成前宣称全链路 verified。
- 证据至少包括 domain invariant tests、H2/JPA/UoW aggregate graph/rollback/concurrency tests、HTTP integration tests、scheduler/provider tests、enum/converter/VO tests、plan/generation determinism、Analyzer Flow/Time root、AgentFacts 和 traceability 实际路径。
- B1/B2 已验收行为是回归基线，B3 不得破坏支付、退款、contract leaf、Mermaid flow 或 Composite Build 解析合同。

## 10C. B4 商户日结与资金划拨目标合同

### 10C.1 聚合边界与范围身份

- `MerchantSettlement` 是独立 Aggregate Root，以 Strong `MerchantSettlementId` 标识。Payment、Refund、ReconciliationBatch 不进入 Settlement ORM graph；结算侧只保存 Strong ID、稳定外部 identity、当前有效对账 identity 和形成结算时的不可变快照。
- B4 只实现日结。业务时区固定为 `Asia/Shanghai`，周期使用明确的 `[periodStart, periodEnd)`；周期模型可保存 type/start/end/timezone，但不提供周结 scheduler、周结 API 或周结验收。
- 同一 `merchantId + channelId + currency + periodStart + periodEnd` 任一时刻最多存在一个有效 Settlement。数据库唯一约束、领域幂等和 optimistic version 共同保护；scheduler、HTTP 和重试并发不得形成双有效单。
- MerchantSettlement 至少拥有 `SettlementLine`、`SettlementExecutionAttempt` 和 `SettlementResultReceipt`。三类 child 均属于强引用 owned graph，并由同一 cap4k JPA UoW 原子保存；历史 attempt/receipt 不得覆盖。
- Settlement 保存 predecessor/replacement identity、作废原因、operator 和时间。作废重建只允许在尚未提交外部执行的 PREPARED/REVIEW_REQUIRED 状态；PROCESSING、RESULT_UNKNOWN、SUCCEEDED 不得通过作废规避历史。

### 10C.2 支付手续费事实

- `MerchantChannelConfiguration` 增加 settlement fee basis points 与 settlement result review threshold。reference seed 使用 `200` basis points、零固定费用、`HALF_UP` 到币种精度，result review threshold 为 30 分钟。
- Payment 首次形成成功事实时必须冻结 fee rule 与 fee result，至少保存 rate/basis points、固定费用、舍入模式、币种精度、计算金额和形成时间。配置以后修改或退役不得改变既有 Payment fee fact。
- 退款不返还已冻结的支付手续费。Settlement 不在日结时读取当前配置重新计算；每个 PAYMENT SettlementLine 复制 Payment 成功时冻结的规则和结果，REFUND line 不产生负手续费返还。
- 正常示例必须得到：支付 100 + 支付 50 - 退款 20 - 手续费 2 - 手续费 1 = 净结算 127。

### 10C.3 候选资格与对账消费

- B4 通过结算专用 Capability/JPA projection 读取 B3 当前有效 run，形成 `SettlementCandidateFact`。它必须携带 merchant/channel/currency、source kind、source fact identity、Payment/Refund/Attempt/Confirmation 弱引用、reconciliation batch/run/item identity、外部交易 identity、金额、费用、业务发生时间、记录时间与 eligibility basis。
- 只消费 current effective run 中已核对且不再阻断结算的事实。Payment/Refund 的原始状态、current run item、authorized disposition 与 confirmation 是资格依据；不得扫描旧 run 作为当前结果，也不得让 Payment/Refund 上的单一 `settlementBlocked` 字段成为完整资格真源。
- PAY-AC-061 采用交易粒度排除：有未决差异的资金项不进入本次 Settlement，其他已匹配或获授权明确放行的候选继续结算。Settlement 记录 eligible/excluded counts 和 blocker summaries；不得因一个受影响交易阻断整个商户周期。
- `ReconciliationConfirmationFact` 必须保存 merchantId 和 channelId。存在 Payment/Refund 弱引用时，归属必须与原始聚合一致；CHANNEL_ONLY 且没有平台弱引用时，只允许已授权处置显式给出 merchant/channel，并永久保存依据。
- confirmation 作为独立 source fact 进入 SettlementLine，不反向改写 Payment/Refund。PAYMENT confirmation 为正向收入，REFUND confirmation 为负向扣减。
- 相同 source fact identity 不得重复进入一个 Settlement，也不得被两个有效 Settlement 消费。SettlementLine 的稳定 identity 至少区分 PAYMENT、REFUND 与 RECONCILIATION_CONFIRMATION。

### 10C.4 SettlementLine 与金额不变量

- 每条 SettlementLine 至少保存：line/source identity、source/transaction kind、Payment/PaymentAttempt/Refund/RefundAttempt 弱引用、reconciliation batch/run/item/confirmation identity、external transaction identity、gross amount、fee amount、signed net amount、currency、occurredAt、recordedAt、fee rule snapshot、eligibility basis、confirmation reason/evidence。
- Payment line 的 signed net 为 `amount - frozen fee`；Refund line 为 `-amount`；Payment confirmation 为 `amount - frozen fee`；Refund confirmation 为 `-amount`。
- Root 的 payment gross、refund gross、fee total、adjustment total 与 net amount 必须精确等于 lines 的对应汇总；所有 line 币种必须与 Settlement currency 一致，不支持换汇或跨币种抵扣。
- 净额小于零时完整保存组成并进入 `NEGATIVE_REVIEW_REQUIRED`；不得创建或提交资金划拨 attempt。顺延抵扣、商户补款与追偿产品流程后置。
- 净额为零时允许完成准备和确认，但不得调用外部 Transfer；以零金额结算完成事实结束，并保留组成证据。

### 10C.5 准备、复核、确认与冻结

- 日结 scheduler 只发送 `RunDailyMerchantSettlement` Command；scheduled method 不直接访问 Repository、Query 或 Capability。
- Prepare/Run Command 按 merchant/channel/currency/date 幂等装载或创建 Settlement，读取候选，冻结 SettlementLine 和汇总。没有 eligible candidate 时返回明确 no-op，不创建空 Settlement。
- 状态至少表达 PREPARING、REVIEW_REQUIRED、PREPARED、CONFIRMED、PROCESSING、SUCCEEDED、FAILED、RESULT_UNKNOWN、NEGATIVE_REVIEW_REQUIRED、VOIDED 与 CONFLICT_REVIEW_REQUIRED。
- 确认前允许授权人员退回并重新准备；确认后 merchant、channel、currency、period、line composition、fee facts 和 totals 冻结。后续退款、费用或对账变化不得原位修改已确认 Settlement，而应进入下一周期 adjustment 或受控 replacement。
- reference 使用单一授权 operator fixture 证明确认、作废和结果核对边界；不实现通用 RBAC、双人复核、审批工作流或人工任务中心。

### 10C.6 资金划拨执行与重试

- 确认后的正净额 Settlement 才能提交 `StartSettlementTransfer` Capability。每个 Settlement 拥有稳定 `executionGroupIdentity`；每次请求新增独立 ExecutionAttempt 与 request identity，历史失败不覆盖。
- Gateway `ACCEPTED` 只表示请求已受理并进入 PROCESSING，不等于资金到账或结算成功。
- 外部明确失败后允许授权人员在同一个 Settlement/execution group 下人工重试。重试新增 attempt 与 request identity，保留旧 attempt 的失败 code、summary 和时间；不得创建新 Settlement 逃避原执行历史。
- RESULT_UNKNOWN 状态禁止自动或人工创建新 attempt、禁止更换 request/execution identity、禁止新 Settlement 或等额重付，直至授权核对追加最终结论。
- B4 使用同步 Fake Transfer Capability 证明边界，不声称真实银行网络、可靠 enqueue、自动 retry 或 transport delivery。

### 10C.7 执行结果、未知与冲突裁决

- callback 至少携带 channel、notification identity、SettlementId、ExecutionAttemptId、executionGroupIdentity、request identity、external settlement identity、amount、currency、raw result、occurredAt 和验证材料。
- Handler 在 UoW 内调用 `VerifySettlementResult` Capability，再由聚合追加 SettlementResultReceipt 并裁决 attempt/root。
- 同 notification identity 同 payload 重放只增加 receive count，不产生第二次资金效果；同 identity 不同 payload 形成 CONFLICT。
- 首次可信成功把 attempt 与 Settlement 确认为 SUCCEEDED，记录 external identity 和 completedAt，并且只形成一次 settled fact。
- 明确失败把当前 attempt 置为 FAILED；是否重试只由显式人工 Command 触发，不自动创建 attempt。
- 可信未知把 attempt/root 置为 RESULT_UNKNOWN。MerchantChannelConfiguration 的 `settlementResultReviewAfterMinutes` 为规则真源，reference seed 为 30；attempt 冻结当次阈值。普通 scheduled scan 在超过阈值后进入 `CONFLICT_REVIEW_REQUIRED/REVIEW_REQUIRED`，但仍不得重付。
- 成功后迟到失败或未知不得回退 SUCCEEDED；必须追加双方 raw result、接收/发生时间和 conflict disposition，冻结自动动作并进入人工核对视图。
- 授权核对可在同一 Settlement 上追加最终成功或失败结论，不删除原 receipt；最终事实与全部历史可查询。

### 10C.8 生成类型、Domain Outcome 与 ownership

- schema/design 必须生成 MerchantSettlement aggregate graph、Strong IDs、Repository、Factory/Behavior、Command/Query/Capability/Endpoint 与有限业务 enum。
- B4 enum 至少覆盖 settlement status、line source kind、execution attempt status/final result 与 result disposition。内部有限状态使用整数列和完整 `@Type` 绑定；numeric value 进入持久化后保持稳定。外部 raw status/code/identity 保持开放标量。
- 跨层 immutable values 至少包括 SettlementCandidateFact、SettlementPreparationOutcome 与 SettlementResultRecordingOutcome；value-object manifest 不配置 persistence。Domain VO 可以直接成为 Command Response 字段，contract leaf 由 adapter 显式投影。
- generated Entity/Schema/Repository 是 build-owned；enum、VO、Endpoint contract、Behavior、Factory/Creation、Command/Query/Capability skeleton 是 checked-in/skip。Endpoint Handler、HTTP binding、Scheduler、Capability Handler 与业务投影手写维护。

### 10C.9 应用入口、Endpoint 与查询

B4 至少包含以下应用入口：

1. `RunDailyMerchantSettlement` / `PrepareMerchantSettlement`：按日结范围幂等形成 eligible lines 与 totals。
2. `ConfirmMerchantSettlement`：授权确认并冻结组成。
3. `StartMerchantSettlementExecution`：提交或在明确失败后人工重试。
4. `ConfirmMerchantSettlementResult`：验证并裁决渠道 callback。
5. `ReviewUnknownMerchantSettlements`：普通 scheduled scan，把超过 30 分钟阈值的未知结果送入人工核对状态，不重付。
6. `VoidMerchantSettlement`：仅在未提交外部执行的允许状态作废，并可形成 replacement chain。
7. `GetMerchantSettlement`：返回范围、状态、构成、lines、excluded/blockers、fee facts、attempts、receipts、冲突和 replacement 轨迹。

独立 contract module 至少提供：

- `POST /api/merchant-settlements`：显式准备/运行日结；
- `POST /api/merchant-settlements/{settlementId}/confirmations`：确认冻结；
- `POST /api/merchant-settlements/{settlementId}/executions`：首次提交或明确失败后的人工重试；
- `POST /api/channel/settlement-results`：接收 reference 渠道结果；
- `POST /api/merchant-settlements/{settlementId}/voids`：受控作废；
- `GET /api/merchant-settlements/{settlementId}`：查询完整结算证据。

每个 Endpoint Handler 一类一文件并使用静态 Mediator；HTTP binding 保持手写。该风格是 reference authoring preference，不是 cap4k 对全部项目的强制规则。Query 不返回 domain/JPA aggregate 或 proxy。

### 10C.10 UoW、并发与错误合同

- MerchantSettlement root 与完整 owned graph 的创建、状态推进、attempt/receipt 追加共用 cap4k JPA UoW 和同一 Hibernate persistence context；不使用 detached merge、跨 ORM bridge 或分散 save。
- root/lines 任一 invariant、费用计算、source identity 或唯一约束失败时整体回滚，不留下空 root、部分 lines 或幽灵 PROCESSING。
- 同范围 prepare、相同 source fact consumption、execution start 与 callback 由真实双事务/H2/JPA/MockMvc 测试证明不会双写。
- 典型 optimistic/unique conflict 映射为稳定 409 `CONCURRENT_MODIFICATION`，而不是静默覆盖或 500。
- 候选不可用、B3 current run 不存在、merchant attribution 不完整、fee snapshot 缺失或币种不一致时返回明确业务错误，不把异常数据当作零金额结算。

### 10C.11 Analyzer、AgentFacts 与证据

- Analyzer 必须生成日结 scheduler 到 Command 的独立 Time root，以及确认、提交、callback、作废等真实 HTTP Command Actor-to-Command Flow。Query 只在 graph/design projection 中出现，不要求默认 Flow。
- Command/Query/Capability/Endpoint anchors 与 MerchantSettlement Aggregate Structure 分别由 Drawing Board/Aggregate Structure output 证明；不把运行时状态、可靠投递或跨入口过程伪造成默认 Flow stitching。
- Mermaid 必须可解析。B4 新 Flow 不能复用 B1-B3 输出冒充证据。
- Agent Snapshot 必须保留 B4 plan ownership、analysis 与 diagnostics。live DB freshness UNKNOWN 可以导致 PARTIAL，但 ownership 不得为空，且不得出现 INVALID、error 或 plan-evidence-invalid。
- B4 验收覆盖 PAY-AC-060..068，并补齐 PAY-AC-014 的 fee snapshot、PAY-AC-083 的支付→退款→对账→结算轨迹和 PAY-AC-085 的业务时区边界。
- 证据至少包括 enum/converter/VO tests、domain invariant tests、H2/JPA/UoW aggregate graph/rollback/concurrency tests、HTTP integration tests、scheduler/fake provider tests、plan/generation determinism、Analyzer Time/Actor Flow、AgentFacts 和 traceability 实际路径。
- B1/B2/B3 已验收行为是回归基线，B4 不得破坏支付、退款、对账、contract leaf、Composite Build、Mermaid、Analyzer 或 AgentFacts 合同。

## 10D. B5 最小可靠 HTTP Integration Event

### 10D.1 B5 目标与 published language

- B5 只实现两个业务 Integration Event：入站 `ChannelStatementAvailableIntegrationEvent` 与出站 `MerchantSettlementCompletedIntegrationEvent`。
- 两者是 contract module 的 dependency-leaf published language，并使用显式稳定 v1 event name。payload 只包含标量、业务 Strong ID 的公开表示、金额/币种、statement identity/revision、event/correlation/causation identity 和 occurrence time；不得暴露 JPA Entity、聚合对象、Repository、Capability 实现或 transport 配置。
- `ChannelStatementAvailableIntegrationEvent` 表达“指定渠道账单版本已经可获取”，不是完整账单内容。`MerchantSettlementCompletedIntegrationEvent` 表达“指定 Settlement 首次形成 accepted terminal success fact”，不是资金网络最终全局完成承诺。
- contract 不依赖 domain/application/adapter/start、Spring、JPA 或 HTTP starter。application 为发布和监听 event 可以依赖 `contract + domain`；domain 不依赖 contract。

### 10D.2 入站账单 available event

- cap4k HTTP Integration Event receiver 解码 `ChannelStatementAvailableIntegrationEvent` 后交给 checked-in application listener。listener 必须保持薄壳，只做输入规范化、追踪信息传递和 Command/application operation dispatch，不直接访问 Repository、EntityManager 或 HTTP client。
- event 至少包含稳定 event identity、channelId、currency、reconciliationDate、statementIdentity、revision、publishedAt，以及可选 correlation/causation identity；空 identity、非法 revision、未知 channel/currency 组合必须被明确拒绝或进入可诊断失败，不得静默创建批次。
- listener 进入既有 Reconciliation 应用路径，完整 statement 仍通过 `PullChannelStatement` Capability 取得。event payload 不得成为账单真源，也不得绕过 statement completeness、identity/revision、match/disposition 和 current effective run 规则。
- 同 event identity 重放、不同 event identity 指向同 statement/revision、scheduler 同日触发、人工 rerun 和并发请求都必须汇合到同一 batch/run 幂等边界。同 identity/revision 只形成一次有效 run；更高 revision 追加历史并成为 effective run；旧 revision 迟到不回退当前 effective run。
- event 先于 statement 可读取时，处理保持失败可诊断并允许相同 event identity 后续重试；恢复后不得创建重复有效批次。

### 10D.3 Push、Pull、scheduler 与 rerun 共存

- B5 不用 Integration Event 替换 provider Pull、日终 scheduler 或人工 rerun。四个入口可以同时存在：Push 提高时效性，Pull 提供权威完整数据，scheduler 提供漏通知/停机/不支持推送渠道的最终发现，manual rerun 提供运维恢复。
- 四个入口必须共享 channel + currency + business date 的 batch scope、statement identity + revision 的 run identity、数据库唯一约束与 optimistic-lock 语义；不得创建 event-only 聚合、event-only effective pointer 或第二套对账状态机。
- Analyzer/Flow 可以分别展示真实 HTTP Integration Event listener root、Time scheduler root 与显式 HTTP rerun root，但不得把它们伪造成单一 exactly-once stitched flow。

### 10D.4 Settlement completed 事实与出站事件

- MerchantSettlement 首次形成 accepted `SUCCEEDED` settled fact 时，必须同时形成稳定 completion event identity 与 completion occurrence time。推荐身份由 event type + settlementId 稳定派生或显式冻结；重放后保持不变。
- FAILED、RESULT_UNKNOWN、REVIEW_REQUIRED、CONFLICT_REVIEW_REQUIRED、NEGATIVE_REVIEW_REQUIRED、VOIDED、gateway rejection、未验证 callback、重复 callback 和成功后的迟到失败不得形成新的 completed event。
- 如果 Settlement 可以通过渠道 SUCCESS callback或受控 unknown adjudication首次形成 accepted success，两条路径必须调用同一领域事实形成逻辑，保证只产生一次 local completion fact/event intent。
- domain 可以形成本地 `MerchantSettlementCompletedDomainEvent`，application Domain Event subscriber 负责映射为 published `MerchantSettlementCompletedIntegrationEvent` 并调用 cap4k Integration Event supervisor；不得让 domain 依赖 contract。

### 10D.5 可靠 Event/JPA 与事务原子性

- published outbound event 必须通过当前 cap4k `Mediator.events.enqueue/schedule/delay` 合同及其 reliable Event/JPA owner记录；不得退化成 Command Handler 中直接调用 `RestClient`、`WebClient` 或自定义线程发送。
- settlement success business transition、稳定 event identity 与 reliable outbound event record 必须在同一 UoW 中原子提交。强制业务事务回滚后，Settlement success 与可投递 event record 都不存在；提交成功后两者都可从真实 H2/JPA 状态验证。
- cap4k HTTP publisher 缺少 `ReliableEventCoordinator`、`EventRecordRepository` 或其他必需 provider 时必须 fail fast；项目不得注入内存/no-op fallback 冒充可靠投递。
- reliable provider 的 handoff 成功只表示 provider 接受/HTTP 2xx，不表示所有下游业务处理完成。B5 只承诺 at-least-once transport handoff + stable identity + 业务幂等，不承诺端到端 exactly-once。

### 10D.6 HTTP Integration Event transport

- B5 只启用 cap4k 当前生产 HTTP Integration Event publisher/receiver；不启用 RabbitMQ、RocketMQ 或多 publisher fallback。应用任一时刻只允许一个 active outbound Integration Event publisher。
- 入站使用 cap4k canonical fixed receive path `/cap4k/integration-events` 与 canonical envelope。项目不得在业务 adapter 中定义第二套 `/api/...` event envelope、反序列化器或 provider identity。
- 出站 route 以稳定 event name映射到 URI；仓库不提交机器绝对地址、端口或凭据。端到端测试通过动态配置指向可控 fake HTTP receiver；生产默认配置通过环境/部署注入目标 URI。
- fake receiver 必须能够按测试脚本返回 2xx、非 2xx、延迟/超时并记录 envelope identity。它是测试证据，不作为第二个生产 Spring Boot 应用或真实 merchant notification service。
- 首次非 2xx、连接失败或超时后，reliable event 保持可观察的待重试/失败状态；receiver 恢复后使用相同 event identity 重试并成功交付。不得通过创建新业务 event 绕过失败记录。

### 10D.7 幂等、重复与安全边界

- outbound settlement event 的重复 callback、publisher retry 与 HTTP replay不得形成第二个业务 completion fact；测试 receiver 可以接收重复 envelope，但 identity 与 payload fingerprint 必须稳定。
- inbound statement event 至少一次投递依赖 Reconciliation 业务唯一性实现幂等。B5 不新增通用 Inbox，但必须验证重复 event envelope、scheduler 和 rerun不会重复 effective run。
- transport 不拥有商户授权、渠道签名、银行密钥或敏感凭据。生产级认证、签名和 secret rotation 后置；B5 测试仅使用明确的本地 fixture，不把假 secret 写入 published contract。
- event payload 的原始外部状态保持开放标量；有限领域状态继续使用现有生成 enum。不得因 transport 需要创建重复业务 enum 或把错误码全部封闭化。

### 10D.8 Generator、Analyzer、AgentFacts 与证据

- design 输入必须登记两个 Integration Event published contracts/anchors，并保持 event contract 为 checked-in authoring source；具体 runtime reliable-event record 和 HTTP protocol 由 cap4k Runtime/starter 拥有，不由业务 schema 伪造第二套 Outbox。
- Endpoint Handler、Integration Event listener、Domain Event subscriber 和业务 Command Handler 继续是一类一文件的 checked-in source；HTTP Integration Event wire handler 属于 starter，不在项目手写。
- Pipeline plan/generation 必须保持确定性；B1-B4 已演进的 checked-in 文件不得被覆盖。clean 后 build-owned generated source 可重建。
- Analyzer 必须能观察真实 outbound Integration Event send 与 inbound listener/event-handler关系；如果当前 Analyzer合同不能完整表达某条 transport边，必须如实记录 verified gap，不通过虚构 generic sender node或跨入口 stitching规避。
- Agent Snapshot 必须保留 plan ownership、analysis 和 diagnostics。live DB freshness UNKNOWN 可以导致 PARTIAL，但 ownership不得为空，analysis不得 INVALID，diagnostics不得出现 error或 plan-evidence-invalid。
- B5 证据至少包括 contract leaf/serialization、入站 replay/revision、Push+Pull+scheduler/rerun convergence、settlement single event、UoW rollback、HTTP failure/retry/recovery、stable envelope identity、full B1-B4 regression、plan/generation determinism、Mermaid、Analyzer 和 AgentFacts。

### 10D.9 B5 非目标与后续边界

- B5 不实现 reliable Command、通用 Outbox/Inbox 产品层、broker transport、广播、动态服务发现、全局顺序、框架级 DLQ UI、持久化 scheduler、lease 或跨实例 scheduler/exactly-once。
- B5 不依赖 only-engine，也不执行 only-engine addon gate。only-engine、Jimmer/aggregateProjection、Endpoint Handler generator 与 published-coordinate cold start 分别保留为独立后续验证。
- B5 不处理 Payment timeout/late-result/conflict-review（GitHub #4），不完成最终 composition audit（GitHub #8），不实现大额退款审批、超期人工例外、负净额追偿、周结、真实银行/清分清算网络或生产 merchant notification service。
- 当前实现必须保留稳定业务身份、event version 与可替换 transport边界，但不得预建无需求的通用分布式基础设施。

## 11. 后续边界

Payment timeout/late-result/conflict-review、最终 accepted-lineage composition、published-coordinate cold start、大额退款人工审批、超期退款人工例外、负净额追偿、周结、only-engine addon verification、Jimmer/aggregateProjection、Endpoint Handler generator 和生产 transport/auth 分别保留为后续可独立验收的 change。B5 仅证明最小 reliable Event/JPA + HTTP Integration Event 体验。
