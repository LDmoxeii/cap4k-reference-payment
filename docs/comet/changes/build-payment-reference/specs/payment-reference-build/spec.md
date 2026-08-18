# Payment Reference 首个可运行实现规格

## 1. 目标状态

`cap4k-reference-payment` 提供一个基于当前 cap4k mainline 合同的最小可运行支付系统。它证明四个业务层模块加独立 contract leaf、Aggregate/owned child/Strong ID/Value Object、生成并类型化的业务枚举、Repository/UoW、Command/Query/Capability/Endpoint、手写 HTTP binding、Pipeline generation、Analyzer 和 AgentFacts 能在同一真实业务链中协同工作。

该状态是完整支付引用项目的第一块实现投影，不改变 `docs/requirements/**` 中的业务真源，也不声称退款、对账或结算已经可用。

## 2. 工程与依赖合同

### 2.1 模块

项目必须包含：

- `contract`：dependency-leaf 对外契约模块，拥有 Endpoint operation、Request 与 Response，只依赖轻量 `cap4k-contract-api` 及编译期分析 metadata，不依赖任何项目内模块、Spring、JPA 或 transport 实现。
- `domain`：Payment 领域模型、Value Object、生成 enum、领域事实与 Repository 契约。
- `application`：创建支付、发起尝试、确认渠道结果、查询支付的应用输入和处理器；渠道 Gateway/结果验证 Capability。
- `adapter`：Endpoint Handler、手写 HTTP binding、Fake Channel Gateway、渠道结果验证器和 JPA 适配。
- `start`：Spring Boot 组装、H2 配置、最小渠道配置 seed 和端到端测试。

项目内依赖方向必须为：

- `application -> domain`；
- `adapter -> contract + application + domain`；
- `start -> adapter`；
- `contract` 不依赖任何项目内模块；
- `domain` 不依赖 contract/application/adapter/start；
- application/domain 不依赖 start 或具体 Web 配置。

Pipeline 的 `contractModulePath` 必须解析到独立 `contract` project。四个 Endpoint contract 必须物理位于该模块，并保持 `com.only4.cap4k.reference.payment.contract.endpoints.payment.api` 下的既有 FQN。

### 2.2 版本与解析

- Java/Kotlin toolchain：17。
- Kotlin：2.2.20。
- Spring Boot：3.5.6。
- 默认声明的 cap4k/plugin/runtime 坐标：2.0.1。
- 默认仓库只允许 Gradle Plugin Portal 和 Maven Central。
- B1 必须通过用户显式提供的本机配置启用 Composite Build，对当前 cap4k mainline 完成构建、测试和分析验收。解析顺序固定为：先读取并规范化非空 Gradle property `cap4k.local.path`，再读取并规范化非空环境变量 `CAP4K_LOCAL_PATH`，二者均未提供时继续使用正式版 `2.0.1`；本地分支使用 `999.0.0-local` plugin marker 并对同一路径执行 `includeBuild`。仓库不得提交 sibling path、绝对路径、`mavenLocal()`、Snapshot、私服或机器本地 `gradle.properties`。published-coordinate cold start 不属于 B1 验收，保留给 B6。

### 2.3 Pipeline ownership

- cap4k 生成器产生的 generated source 由构建拥有，不进入手工编辑区。
- Endpoint contracts、Domain Value Object 和需要持续演进的 behavior skeleton 采用 checked-in ownership，首次生成后由业务代码维护，重复 generation 必须 SKIP 已演进文件。
- Endpoint Handler 与 HTTP binding 是 reference 手写 adapter 代码；不得伪造为本切片已有的 Generator capability。
- `cap4kPlan` 必须解释 generator、module role、output path、output kind、resolved root 和 conflict policy。
- 重复 plan/generation 在相同输入下保持确定性，不覆盖用户已修改的 checked-in source。

## 3. Generator 类型输入合同

### 3.1 Enum manifest

`design/enums.json` 是 B1 有限业务枚举的 authoring source，并通过 `types.enumManifest` 注册。至少声明以下 aggregate-owned enum：

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

首切片只主动推进 `PENDING -> PROCESSING -> SUCCEEDED`，但模型不得允许成功回退。

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

首切片提供最小持久化模型：merchant、channel、currency、payment method、amount range、typed active/retired status 及必要审计信息。start 层建立一条 `M-001`、`CNY`、测试支付方式可用的配置。

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

首切片记录“商户成功通知意图”领域事实，但不发送真实 Integration Event 或网络通知。

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

首切片提供四个 transport-neutral Endpoint contract：

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
- B1 必须通过显式 Composite Build 消费包含 Mermaid quoted-label 修复的 cap4k mainline，重新运行 `cap4kAnalysisPlan` 与 `cap4kAnalysisGenerate`。
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

仓库业务 phase 保持 `build` 且 `build_authorized: true`。由于用户可见架构、类型输入和验收标准发生变化，B1 必须先刷新 Shape 和 acceptance；只有新实现与新证据验证通过时，才把对应 acceptance/evidence 状态更新为 verified。旧 plan item 数量、hash、Analyzer/AgentFacts 结果和旧 Verify pass 均不得继续作为当前候选证据。

## 11. 后续边界

退款、日终对账、商户结算、可靠异步、Integration Event transport、only-engine integration gate、Jimmer/aggregateProjection、Endpoint Handler generator 和 published-coordinate cold start 分别保留为后续可独立验收的 change。当前实现必须给这些后续链路保留稳定业务身份和事件语义，但不得预建无需求的通用框架。
