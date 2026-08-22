# Outcome

在已验收的 B1 支付、B2 退款和 B3 日终对账基础上，实现可运行的 B4 单币种商户周期结算闭环。系统按稳定的商户、渠道、币种和结算周期形成 `MerchantSettlement`，从当前有效对账结果中选择可结算的 Payment、Refund 与授权确认事实，冻结每条 `SettlementLine` 的来源、金额、费用和证据，经过人工确认后以稳定执行身份调用 reference 级资金划拨 Capability，并对成功、失败、结果未知、重复与冲突进行可审计裁决。

B4 必须证明 PAY-AC-060..068、PAY-CP-004、PAY-CP-009 以及 PAY-AC-014/083/085 在结算段的真实业务含义，同时继续展示 cap4k 的 Aggregate/owned child、Strong ID、Repository/UoW、Value Object、checked-in enum、Command/Query/Capability/Endpoint、手写 HTTP binding、普通 `@Scheduled` Time root、Pipeline generation、Analyzer 和 AgentFacts 能力。

# Scope

- 新增独立 `MerchantSettlement` Aggregate Root，并至少包含强引用 owned child：`SettlementLine`、`SettlementExecutionAttempt`、`SettlementResultReceipt`。
- 以 `merchantId + channelId + currency + periodStart + periodEnd` 形成稳定范围身份；同一范围任一时刻最多一份有效结算单，作废重建保留 predecessor/successor、原因、操作人和时间。
- 结算候选只读取 B3 当前有效 run 的不可变资金证据：已核对的 Payment/Refund 原始事实，以及授权产生的 `ReconciliationConfirmationFact`；不得建立跨聚合 ORM graph，也不得反写 Payment/Refund/Reconciliation 历史。
- PAY-AC-061 按交易粒度阻断：仍有未决差异的资金项不进入本次 Settlement，其他已核对且不阻断的候选仍可结算；查询必须展示 excluded/blocker 数量与原因。
- 在 Payment 首次形成成功事实时冻结手续费事实。reference fixture 使用 200 basis points、`HALF_UP` 到币种精度、无固定费用；退款不返还已冻结的支付手续费。B4 结算只消费冻结结果，不按当前配置重算。正常示例为 `100 + 50 - 20 - 2 - 1 = 127`。
- `ReconciliationConfirmationFact` 增加稳定 merchant/channel 归属。存在 Payment/Refund 弱引用时必须与原始聚合 merchant/channel 一致；CHANNEL_ONLY 且无平台弱引用时必须由已授权处置显式给出并永久保存。
- 支持 prepare、review/confirm、submit execution、channel result callback、result review、query 与 controlled void/replacement。确认后 merchant、channel、currency、period、line composition 和 totals 冻结。
- 确认后新增退款、费用或对账影响不得原位改写已确认结算；它们进入后续周期 adjustment。B4 只保存稳定调整来源和追踪语义，不建设通用账务引擎。
- 执行请求使用稳定 `execution identity`。重复提交不得形成第二次资金效果；结果未知时禁止更换结算单或执行身份重付；成功后迟到失败不回退成功事实，而是追加冲突证据并进入人工核对。
- 负净额完整保存构成并进入 `NEGATIVE_REVIEW_REQUIRED`，不调用资金划拨 Capability；顺延抵扣、商户补款或追偿产品流程后置。
- 提供 reference 级 operator fixture，证明确认、作废、未知结果核对的授权边界；不建设通用 RBAC、双人复核或审批引擎。
- contract leaf 增加 transport-neutral Endpoint contracts；每个 Endpoint Handler 一类一文件并使用静态 `Mediator`；HTTP binding 继续手写。
- 增加普通 `@Scheduled` 日结 reaction。scheduled method 只发送 Command，不直接访问 Repository/Query/Capability。
- 使用 Fake Settlement Transfer Capability 与 Fake Result Verifier 形成可运行的执行与 callback 闭环，不接真实银行或清算网络。
- 更新 schema/design/enum/value-object manifests、生成产物、README、requirements traceability、Analyzer/AgentFacts evidence，并保持 B1/B2/B3 全回归。

# Non-goals

- 不实现真实银行划拨、清分清算网络、商户余额账户或总账系统。
- 不实现 Outbox、可靠 Command/Event、Integration Event transport、持久化调度、lease/retry、跨实例 exactly-once；这些属于 B5。
- 不实现负净额追偿、商户补款、顺延抵扣的完整产品流程。
- 不实现通用 RBAC、双人复核、审批工作流或人工任务中心。
- 不实现跨币种结算、换汇、净额跨币种抵扣或多币种合并。
- 不实现 aggregateProjection/Jimmer、Endpoint Handler generator、only-engine gate 或 published-coordinate cold start。
- 不把 Query、Capability、运行时状态推进或跨入口流程伪造成默认 Analyzer Flow 能力。

# Acceptance examples

- A1：真实 schema/design 输入生成 MerchantSettlement aggregate graph、Strong IDs、Repository、Factory/Behavior、有限业务 enum、非持久化 Domain VO、Command/Query/Capability/Endpoint；checked-in/build-owned ownership 与 plan 可解释且重复 generation 不覆盖作者代码。
- A2：Payment 成功时冻结 200 bps、HALF_UP 的手续费规则和结果；后续修改 MerchantChannelConfiguration 不改变既有 Payment 费用事实或 SettlementLine。
- A3：日结使用 `Asia/Shanghai` 的 `[periodStart, periodEnd)`；相同 merchant/channel/currency/period 的 scheduler 与 HTTP 并发只能形成一个有效 Settlement。
- A4：候选读取 B3 current effective run；未决差异只排除受影响交易，其他 eligible facts 继续结算；授权 confirmation 可进入，且不反写原始 Payment/Refund/Reconciliation。
- A5：两个成功支付 100、50，一个成功退款 20，冻结手续费 2、1，形成可追踪 lines 和净额 127；root 汇总精确等于 lines 之和。
- A6：同一 Payment、Refund、fee fact 或 confirmation identity 不得重复进入同一或后续有效 Settlement；重跑和并发不形成双 line/双结算。
- A7：确认前允许退回调整；确认后范围、lines、totals 与来源证据冻结；后续影响只能形成下一周期 adjustment 或受控替代，不能原位修改。
- A8：作废重建只允许在尚未提交外部执行的状态；replacement chain 完整，且不得通过作废绕过 PROCESSING、RESULT_UNKNOWN 或 SUCCEEDED 历史。
- A9：提交资金划拨使用稳定 execution identity；Gateway accepted 只表示处理中，不表示结算成功。
- A10：匹配成功 callback 只形成一次 settled fact；同 notification 重放只增加接收证据，不产生第二次资金效果。
- A11：结果未知进入 `RESULT_UNKNOWN/REVIEW_REQUIRED`，保留同一 execution identity，禁止新结算或新身份重付；授权核对可追加最终成功或失败结论。
- A12：成功后迟到失败保持 SUCCEEDED，追加冲突 receipt、双方原始结果、时间和裁决摘要，并冻结后续自动动作。
- A13：净额为负时进入人工状态、完整展示构成且 Transfer Capability 调用次数为 0。
- A14：商户查询返回周期、状态、构成、每条来源 identity/金额/费用/资格依据、排除阻断原因、执行 attempt/receipt、冲突与 replacement 轨迹，不暴露 JPA aggregate/proxy。
- A15：MerchantSettlement 完整 owned graph 在同一 cap4k JPA UoW 原子保存；失败整体回滚；双事务/HTTP 并发产生稳定 409 `CONCURRENT_MODIFICATION` 而非静默覆盖或 500。
- A16：Analyzer 产生结算 scheduler 的独立 Time root，以及确认/提交/结果核对等真实 HTTP Command Actor roots；Query 只进入 graph/design projection，Mermaid 可解析。
- A17：Agent Snapshot 保留非空 B4 ownership、analysis 与零 error diagnostics；live DB freshness UNKNOWN 可导致 PARTIAL，但不得 INVALID、空 ownership 或 `plan-evidence-invalid`。
- A18：PAY-AC-060..068、PAY-AC-014、PAY-AC-083 和 PAY-AC-085 的实际测试/路径/命令写回 traceability；PAY-EV-008/009/016 只有在真实证据存在时标 verified，EV-017 不借同步 fake 冒充可靠 transport。
- A19：B1/B2/B3 支付、退款、对账、contract leaf、Composite Build、Mermaid、Analyzer 和 AgentFacts 回归保持通过。

# Constraints and invariants

- `MerchantSettlement` 是独立 Aggregate Root；Payment、Refund、ReconciliationBatch 不进入其 ORM graph，只保存 Strong ID/稳定 identity 与形成结算时的不可变快照。
- `SettlementLine` 必须保存 source kind、source fact identity、Payment/Refund/Attempt/Confirmation 弱引用、reconciliation batch/run/item identity、金额、币种、发生/记录时间、手续费规则与金额、eligibility basis。
- `SettlementExecutionAttempt` 和 `SettlementResultReceipt` 追加保存，不能覆盖历史。notification identity 在 attempt 内唯一；同 payload 重放和冲突 payload 必须可区分。
- 同范围最多一个有效 Settlement；同 source fact 不得被两个有效 Settlement 消费。数据库唯一约束、领域幂等和 optimistic version 共同保护。
- 内部有限状态使用生成 enum 与整数 schema 列完整 `@Type` 绑定；numeric value 进入持久化后保持稳定。外部 raw result/code/identity 保持开放标量。
- Domain Outcome 可以直接成为 Command Response 字段；独立 contract leaf 不依赖 domain，Endpoint Response 由 adapter 显式投影。
- contract 继续保持 dependency leaf；Endpoint Handler/HTTP binding/Scheduler/Capability Handler 是 checked-in 手写边界。
- 普通 scheduler 和同步 Fake Capability 只证明 reference 业务闭环，不提升为 B5 的可靠异步或生产 transport 声明。
- current Agent Snapshot 的 ownership freshness 因 live DB 合法为 UNKNOWN；验收看非空 ownership、identity 对齐、无 error/INVALID 和实际 plan/generation evidence。

# Decisions

- B4 以 PAY-AC-060..068 为核心，补齐 PAY-AC-014 的手续费快照消费、PAY-AC-083 的结算段全链路和 PAY-AC-085 的业务时区边界。
- PAY-AC-061 采用交易粒度排除，而不是因一个差异阻断整个商户周期。
- reference 手续费规则采用成功支付金额的 200 bps、HALF_UP 到币种精度、无固定费用；退款不返还已冻结手续费。
- 费用事实形成于 Payment 首次成功时，结算时不得读取当前配置重新计算。
- CHANNEL_ONLY confirmation 必须有已授权的 merchant/channel attribution；若存在平台弱引用，归属必须一致。
- B4 使用独立 MerchantSettlement root + SettlementLine/ExecutionAttempt/ResultReceipt owned graph。
- 确认后冻结，后续影响进入未来 adjustment；作废替代仅用于尚未提交外部执行的结算。
- 负净额不自动执行，只进入人工状态；追偿策略后置。
- B4 使用同步 Fake Transfer/Verifier 与普通 `@Scheduled`；B5 能力不进入本轮声明。
- Endpoint contracts 生成到独立 contract leaf；Handler 一类一文件、静态 Mediator、HTTP binding 手写仍是 reference 偏好。
- B4 只实现日结；周期模型保存显式 start/end/timezone，但不提供周结 scheduler 或周结验收。
- 结果未知阈值属于 MerchantChannelConfiguration，reference seed 为 30 分钟；ExecutionAttempt 冻结当次阈值快照。
- 外部执行明确失败后允许人工重试：保留同一 Settlement 与稳定 execution group identity，新增 ExecutionAttempt 与 request identity；RESULT_UNKNOWN 不允许重试。

# Open questions

- 无。Shape 已于 2026-08-20 获用户最终确认。

# Verification expectations

- 先运行 `cap4kPlan` 审阅新增 aggregate/enum/VO/design artifacts 的 generator、module、path、kind、root 和 conflict policy，再运行 `cap4kGenerate` 与 `cap4kGenerateSources`。
- 运行 domain invariant/Outcome/enum converter tests、application handler tests、adapter structure/binding/scheduler tests，以及 start 层 H2/JPA/UoW/MockMvc/双事务并发/rollback/full lifecycle integration tests。
- 至少证明 Payment fee snapshot、eligible/blocked candidate selection、127 净额示例、source fact 防重、确认冻结、replacement、负净额、execution success/unknown/conflict、完整查询。
- 运行 `clean build` 并保持 B1/B2/B3 全回归。
- 运行 `cap4kAnalysisPlan`、`cap4kAnalysisGenerate`，检查 Aggregate Structure、Drawing Board、Time/Actor Flow 和 Mermaid parse smoke。
- 最后运行 `cap4kAgentSnapshot`；允许 live DB freshness 导致 PARTIAL，但 capabilities/inputs/runtime/diagnostics 应为 ok，ownership 非空，analysis outputs 当前，diagnostics 无 error/INVALID。
- 重复 plan/generation 并比较 tracked diff/hash，证明 generated roots 可重建且 checked-in 作者代码不被覆盖。
- 更新 README 和 `docs/requirements/traceability.yaml`，只把有真实证据的 AC/CP/EV 标为 verified，不把 B5、真实 transport 或生产资金网络写成当前能力。
