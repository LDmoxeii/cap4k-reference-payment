# Outcome

建立 `cap4k-reference-payment` 的第一个可运行、可验证的 cap4k-only 实现切片：采用 `domain`、`application`、`adapter`、`start` 四个业务层模块和独立 dependency-leaf `contract` 模块，实现“创建支付 → 发起支付尝试 → 渠道结果回调 → 查询支付”的真实持久化链路，并产出 Generator、Runtime、Analyzer、Pipeline 与 AgentFacts 的首批证据。

本 change 完成后，仓库具备可启动应用、H2/JPA 持久化、独立 Endpoint contract、生成并类型化的业务枚举、非持久化领域 Outcome、手写 HTTP binding、自动化测试和可正确解析的 Analyzer Flow。后续退款、对账和结算继续按同一业务需求真源增量建设。

# Scope

- 采用 `domain`、`application`、`adapter`、`start` 四个业务层模块，并增加独立 dependency-leaf `contract` 模块；JDK 17、Kotlin 2.2.20、Spring Boot 3.5.6，保留公开 cap4k 2.0.1 作为默认声明基线。
- B1 使用显式本机配置启用 cap4k Composite Build 验证当前 mainline 合同：非空 Gradle property `cap4k.local.path` 优先，非空环境变量 `CAP4K_LOCAL_PATH` 作为后备，二者均未提供时继续使用正式版 `2.0.1`；仓库不得提交 sibling 绝对路径、`mavenLocal()` 或 Snapshot 仓库。公开坐标 cold start 明确后置到 B6。
- base package 固定为 `com.only4.cap4k.reference.payment`。
- 将四个 Endpoint contract 生成并归属到独立 `contract` 模块，保持现有 package/FQN；`adapter` 显式依赖 `contract`。
- Endpoint Handler 采用一类一文件和静态 `Mediator` 作为本 reference 的默认 authoring preference；HTTP method/path、request mapper、response policy 与 error mapping 继续由 adapter 手写 binding/configuration 维护。
- 建立 Payment 聚合根、PaymentAttempt 强引用子实体、Strong ID、Money Value Object，以及由 enum manifest 生成并通过 schema `@Type` 绑定的有限业务枚举。
- 引入 `ChannelResultDisposition`，以一个类型同时表达 receipt 的持久化处置和 `ChannelResultRecordingOutcome` 的最终处置；外部渠道 raw result 保持开放字符串证据。
- 在 `design/value-objects.json` 中声明无 persistence 的 `ChannelResultRecordingOutcome`，生成 checked-in Domain Value Object，并补充领域不变量与派生属性。
- Application 的 Command Response 可直接引用 Domain Value Object；只有跨越独立 `contract` 发布边界时，Endpoint Handler 才显式映射为 contract-owned Endpoint Response。
- 建立最小可用的 MerchantChannelConfiguration 持久化配置和测试种子，仅支持支付资格判断与渠道路由；本 change 不提供配置维护 API，也不宣称完成 PAY-CP-005 全生命周期。
- 提供创建支付、发起支付尝试、接收渠道支付结果和查询支付的 HTTP 操作。
- 使用确定性的 Fake Channel Gateway 与测试用渠道结果验证器，表达外部能力边界；不实现生产渠道 SDK 或生产密码学。
- 使用 H2 和 cap4k JPA Repository/UoW 完成真实持久化，不以 mock repository 替代。
- 消费已包含 Mermaid node-label quoting 修复的 cap4k mainline，通过 Composite Build 重新生成三个 Endpoint HTTP Flow 的 JSON 与 Mermaid 产物。
- 运行并保存 `cap4kPlan`、生成任务、测试、构建、Analyzer 和 Agent Snapshot 证据。
- 更新 current-only traceability，只把实际通过的场景与证据标记为已验证；未实现能力继续保持 `not-built`。

# Non-goals

- 不实现退款、日终对账、差异处置、商户结算或其 API。
- 不实现支付超时关闭、迟到成功人工核对、可靠延迟 Command、可靠 Domain Event 或 Integration Event transport。
- 不接入 only-engine、Jimmer/aggregateProjection、独立读库、CDC、Event Projection、Saga 或 Event Sourcing。
- 不实现生产渠道签名算法、真实支付 SDK、商户认证授权、PCI 合规或敏感付款数据处理。
- 不把 Endpoint Handler 一类一文件或静态 `Mediator` 上升为 cap4k 对所有用户的强制规则。
- 不生成 HTTP binding，也不把手写 HTTP binding 伪装为 Generator ownership。
- 不让独立 `contract` 模块依赖 domain/application/adapter/start，也不把 Domain VO 直接放进 Endpoint contract 造成反向依赖。
- 不把外部渠道 raw result、currency、payment method 或外部 identity 错误封闭成内部 enum。
- 不为 `ChannelResultRecordingOutcome` 配置 persistence、JPA converter 或数据库列。
- 不把首切片未覆盖的 PAY-CP-001、PAY-CP-005、PAY-CP-006 或 PAY-CP-011 整体标记为完成。
- 不建立 cap4k 版本目录、兼容层或历史投影副本。

# Acceptance examples

- A1：项目包含四个业务层模块和独立 dependency-leaf `contract` 模块；`contract` 无项目内依赖，`application -> domain`、`adapter -> contract + application + domain`、`start -> adapter`，并可从干净工作区通过显式 Composite Build 完成编译、测试和 Spring Boot 启动；本地解析遵循非空 Gradle property `cap4k.local.path` 优先、非空环境变量 `CAP4K_LOCAL_PATH` 后备，二者均无时保持正式版 `2.0.1`，仓库不保存机器路径。
- A2：Pipeline 的 `contractModulePath` 解析到独立 `contract`；四个 Endpoint contract 位于该模块且保持现有 package/FQN，`adapter` 对 `contract` 有显式 Gradle dependency。
- A3：每个 Endpoint Handler 独立成文件，通过 `Mediator.commands.send(...)` 或 `Mediator.queries.ask(...)` 调用应用入口；HTTP binding 保持手写且路由、状态码和 mapper 语义不漂移。
- A4：`cap4kPlan` 与生成任务识别独立 contract、enum manifest、value-object manifest 及 generated/checked-in ownership；第二次生成不覆盖已演进的 checked-in source且无未解释差异。
- A5：Payment 作为聚合根、PaymentAttempt 作为强引用子实体持久化；PaymentId/PaymentAttemptId 使用 Strong ID；Money 保持金额精度与币种不变量。
- A6：`PaymentStatus`、`PaymentAttemptStatus`、`PaymentAttemptFinalResult`、`ChannelResultDisposition` 和 `MerchantChannelConfigurationStatus` 由 `design/enums.json` 生成；对应 schema 有界状态列使用整数存储与显式 `@Type` 绑定，领域行为不再依赖手写 enum 或 `.name` 写入。
- A7：外部渠道 raw result、currency、payment method 和外部 identity 继续保留开放标量语义，不被 enum generator 错误封闭。
- A8：`ChannelResultRecordingOutcome` 由 `design/value-objects.json` 生成 checked-in Domain Value Object，无 persistence、converter 或数据库列；其不变量阻止无效 disposition、计数、状态、摘要和成功事实组合。
- A9：`Payment.recordChannelResult(...)` 返回 `ChannelResultRecordingOutcome`；Command Response 直接持有该 VO，Endpoint Handler 在跨越独立 contract 边界时显式映射为 Endpoint Response。
- A10：有效商户以 `K-001`、`O-001`、`100.00 CNY` 创建支付时得到稳定 PaymentId 和 `PENDING` 状态，不产生支付成功事实；零金额、超精度金额、未支持币种或无有效渠道配置时不创建支付。
- A11：相同商户和幂等键重复提交相同内容时返回同一 PaymentId；关键内容冲突时明确失败，数据库中不新增 Payment 或 PaymentAttempt。
- A12：对 `PENDING` Payment 发起支付尝试时新增独立 PaymentAttempt；Fake Channel Gateway 只返回“已受理”，Payment 进入 `PROCESSING`，不得因此形成成功收入或成功通知事实。
- A13：来源不可信、支付尝试身份不匹配、金额不符或币种不符的渠道结果不能推进 Payment 状态；接收事实、`ChannelResultDisposition` 与拒绝原因仍可查询。
- A14：可信且匹配的成功结果只把 Payment 确认成功一次，并记录渠道交易号、业务发生时间和平台接收时间；相同通知重复到达形成 duplicate disposition 并增加接收次数，不重复形成成功事实。
- A15：Payment 成功后不得再主动创建新的 PaymentAttempt；后续失败结果不得回退成功状态，并以 conflict disposition 留痕。
- A16：查询 API 返回持久化的 Payment 当前状态、金额、币种和全部 PaymentAttempt/渠道结果摘要，足以验证创建、尝试、回调和重复接收链路。
- A17：自动化测试覆盖 PAY-AC-001、002、003、004、005、006、012、013、016、017；H2 集成测试执行完整 HTTP happy path，不以 controller/mock-only 测试代替。
- A18：Analyzer 基于已修复的 cap4k mainline 重新生成创建支付、发起支付尝试和渠道回调三个 Endpoint HTTP Actor Flow；每份 JSON 保持预期节点/边语义，每份 Mermaid 使用安全的 quoted label 并通过语法解析或渲染 smoke，不再出现嵌套方括号 parse error。
- A19：traceability 为本切片记录 contract/enum/VO/Flow、Composite 解析顺序的实际 plan、路径、命令、cap4k commit 和验证结果；PAY-EV-001、002、011、012、019、021、023、024 只在真实证据存在时转为 verified，其余能力继续为 `not-built`。

# Constraints and invariants

- 业务需求文档继续作为技术中立真源；实现不得反向修改业务规则以迎合框架。
- Payment 创建后金额、币种、商户订单号和幂等意图不可变。
- 渠道受理不等于支付成功；Payment 最多形成一次被接受的成功事实。
- 每次支付尝试必须独立留痕；重复、拒绝和矛盾渠道结果不得覆盖历史证据。
- `ChannelResultDisposition.RECEIVED` 仅表示 receipt 已保存但尚未完成裁决，不得作为 `ChannelResultRecordingOutcome` 的最终 disposition。
- Attempt 不属于当前 Payment 时，Outcome 的 `attemptStatus` 为 null，并以 `ATTEMPT_NOT_FOUND` disposition 表达；不得伪造 `PaymentAttemptStatus.NOT_FOUND`。
- 外部结果同时记录业务发生时间和平台接收时间。
- 使用亚洲/上海作为业务时区；持久化时间使用明确时区语义。
- 交易事实不可软删除；渠道配置可以退役，但退役不得改写历史 PaymentAttempt 快照。
- Repository/UoW 保持 Hibernate/JPA 同一持久化上下文，不在 Command 写模型中混用 Jimmer 或另一 ORM。
- generated source 不手工修改；checked-in Value Object、behavior、Endpoint handler 和 handwritten adapter 的 ownership 必须可从 plan 和 Git diff 解释。
- 单 change 足够：contract 调整、enum/VO 生成、领域裁决和 Flow 再生成共享同一 Payment B1 实现与验收闭环，拆分会制造中间不可运行状态，当前不采用 Supervisor Change。

# Decisions

- 业务主题与完整长期范围采用支付、退款、日终对账和商户结算；本 change 只实施支付首链。
- 工程拓扑采用四业务层加独立 contract leaf；未来 RPC 复用该 contract，并可另增 endpoint-client packaging module，本 change 不提前实现 RPC。
- Endpoint contract 保持 transport-neutral；HTTP binding 明确由 adapter 手写。
- 本 reference 采用一类一文件的 Endpoint Handler 与静态 `Mediator`，作为展示性 authoring preference，不定义全局框架政策。
- 业务有限状态由 enum manifest 管理；schema 内部有界状态使用 `@Type` 绑定，外部开放 vocabulary 保持 raw scalar。
- `ChannelResultDisposition` 的固定值为 `RECEIVED`、`REJECTED`、`SUCCESS_ACCEPTED`、`FAILURE_ACCEPTED`、`ACCEPTED_DUPLICATE`、`REJECTED_DUPLICATE`、`CONFLICT`、`ATTEMPT_NOT_FOUND`。
- `ChannelResultRecordingOutcome` 是 Payment aggregate-owned、非持久化 Domain Value Object；Command Response 可直接引用它，Endpoint contract 不依赖 domain，因此 adapter 在发布边界映射为 contract Response。
- 首个实现链为“创建 Payment → 显式发起 PaymentAttempt → 渠道回调确认 → 查询 Payment”。显式发起操作用于保持 PAY-AC-001 的 `PENDING` 创建结果，并避免首切片提前引入可靠 Command。
- base package 使用 `com.only4.cap4k.reference.payment`。
- MerchantChannelConfiguration 在首切片中采用真实持久化最小模型并通过 start fixture/seed 提供数据，不暴露管理 API。
- 默认声明公开 cap4k 2.0.1；B1 对当前主线的验收使用显式 opt-in Composite Build，解析顺序为非空 Gradle property `cap4k.local.path`、非空环境变量 `CAP4K_LOCAL_PATH`、正式版 `2.0.1`，不把本机路径写入仓库；公开坐标 cold start 由 B6 验收。
- Fake Channel Gateway 的“受理”结果与渠道最终结果严格分开；回调验证器仅用于可重复测试，不宣称生产安全。
- current-only 投影继续成立，历史由 Git 保存。

# Open questions

- 无。用户于 2026-08-17 确认上述补充目标、范围、关键决定、验收项和非目标，并授权 B1 返回 Shape 后重新进入 Build；用户于 2026-08-18 进一步确认本机 Composite 的 Gradle property 优先、环境变量后备和正式版回退合同。

# Verification expectations

- 先对 `settings.gradle.kts` 执行 Composite 解析 smoke：验证 Gradle property 优先、空白 property 可回退环境变量、环境变量缺失时可由用户级 Gradle property 驱动本机 Composite，并静态确认二者均无时仍声明正式版 `2.0.1`；机器本地 `gradle.properties` 不进入仓库或 Comet evidence。随后执行 `:contract:compileKotlin`、`cap4kAgentSnapshot`、`cap4kPlan`、checked-in generation、generated-source generation、`test`、`build`、`cap4kAnalysisPlan` 和 `cap4kAnalysisGenerate`。
- 保存 plan、生成目录、测试报告、Analyzer JSON/Mermaid 输出和 Agent manifest 的实际路径，并在 traceability 中引用。
- 对同一输入至少重复运行一次 plan/generation，确认确定性和 ownership 安全。
- domain tests 验证 typed enum 与 `ChannelResultRecordingOutcome` 不变量；application/adapter tests 验证 VO 直用、contract mapping、静态 Mediator 与 HTTP 合同；start integration test 验证 H2/JPA 完整链路。
- 验证 `contract` 不含项目内、Spring、JPA 或 transport 依赖，adapter 显式依赖 contract，四个 Endpoint FQN 搬迁前后不变。
- 对三个 `.mmd` 执行 Mermaid syntax parse/render smoke，并核对三个 JSON Flow 的 entry、node 和 edge 语义。
- Verify 必须逐项核验 A1-A19；B1 的 published-coordinate cold start 继续由 B6 承接。
