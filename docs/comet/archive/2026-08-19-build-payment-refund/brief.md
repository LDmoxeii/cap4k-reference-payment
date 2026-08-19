# Outcome

交付第二个可运行支付引用切片 **B2：退款与部分退款**。在 B1 已确认成功的 Payment 基础上，实现 Refund 独立聚合、原子可退款额度占用、全额/多次部分退款、渠道退款受理与最终结果裁决、重复/矛盾通知留痕，以及 Payment/Refund 查询闭环。

B2 必须继续以真实 H2/JPA、cap4k Repository/UoW、Command/Query/Capability/Endpoint、手写 HTTP binding、Pipeline generation、Analyzer 与 AgentFacts 证明当前 cap4k 的实际能力，而不是 mock-only 示例。

# Scope

- 新增 Refund 独立聚合，以强类型 PaymentId 弱引用 Payment，不把 Payment 对象图嵌入 Refund。
- Refund 记录商户退款号、原 Payment/商户/金额/币种/原渠道快照、状态、占用与成功事实、渠道退款号、业务发生时间、平台接收时间和待核对状态。
- 使用 RefundAttempt 与 RefundNotificationReceipt owned child 保存渠道请求、通知 identity、重复次数、拒绝/冲突裁决和完整可查询证据。
- Payment 继续作为退款预算权威，在同一事务中原子维护已占用退款金额和已成功退款金额；并发创建 Refund 时依赖 Payment 乐观版本与聚合不变量防止超退。
- 覆盖全额退款、多次部分退款、超额拒绝、并发防超退、失败释放占用、结果待确认继续占用、商户退款号幂等、非成功支付拒绝、超过退款期限拒绝、成功退款不可回退。
- 提供真实 HTTP 入口、Endpoint contract、每个 Endpoint 一类一文件 Handler、静态 Mediator、Fake Refund Gateway、渠道结果验证 Capability、Query 和 H2/JPA 集成测试。
- 使用 Generator 生成 Refund 聚合结构、Strong ID、Repository/Factory/Behavior skeleton、业务 enum 与必要的非持久化 Domain Outcome Value Object；checked-in source 允许补充领域行为。
- 重新生成并验证 plan、Analyzer Flow/Drawing Board/Aggregate Structure 与 AgentFacts，写回 traceability 的实际证据。

# Non-goals

- B3 日终对账、差异处置和渠道账单导入。
- B4 商户结算和真实资金划拨。
- B5 可靠异步基础设施、Outbox、Integration Event transport 和 only-engine addon gate。
- 真实支付渠道 SDK、真实签名密钥、PCI/敏感数据处理。
- 通用退款规则引擎、通用人工工作流/Saga、错误退款追偿或会计调整。
- 大额退款人工审批、超期退款人工例外或 override；二者后续分别作为独立可验收切片。
- Jimmer/aggregateProjection、Endpoint Handler generator、published-coordinate cold start。
- 以 Payment↔Refund 双向 ORM 关联、跨聚合整取或共享 mutable object graph 表达退款关系。

# Acceptance examples

- A1：B1 的 contract leaf、四业务层依赖方向、Endpoint Handler/静态 Mediator/手写 HTTP binding、支付 HTTP 链路和全部已验收测试保持通过；本地 cap4k 解析继续遵循 Gradle property `cap4k.local.path` > 环境变量 `CAP4K_LOCAL_PATH` > 正式版 `2.0.1`，仓库不保存机器路径。
- A2：真实 schema/design/enum/value-object 输入经 cap4k plan/generate 产生 Refund Entity、Strong ID、Repository、Factory/Behavior skeleton、业务 enum 与 Outcome；checked-in source 首次物化后重复 generation 必须 SKIP，build-owned source 可重建且 plan ownership 可解释。
- A3：Refund 是独立 Aggregate Root，仅以 Strong PaymentId 弱引用 Payment；RefundAttempt 与 RefundNotificationReceipt 是 owned child，查询能观察每次请求、notification identity、接收时间/次数和裁决证据，不存在 Payment↔Refund ORM 对象图。
- A4：Payment 持久化 `reservedRefundAmount` 与 `successfulRefundAmount` 并派生可退款余额；任何时刻二者非负且之和不超过支付成功金额，只有 SUCCEEDED Payment 可以占用退款额度。
- A5：MerchantChannelConfiguration 持久化 `refundWindowDays=180` 与 `refundResultReviewAfterMinutes=30` 的 B2 seed；退款窗口从 `Payment.succeededAt` 起算，超过窗口自动拒绝。
- A6：Refund 有限状态和裁决由 enum manifest 生成并通过 schema `@Type` 整数绑定；`RefundResultRecordingOutcome` 由 value-object manifest 生成、无 persistence/converter，可直接进入 Command Response，并在 contract 边界显式投影。
- A7：100.00 CNY 成功支付可完成 100.00 全额退款；也可先后成功退款 30.00 与 20.00。两笔 Refund 独立可追踪，Payment 查询分别显示成功退款累计与正确可退款余额。
- A8：同一 merchant refund number 的相同内容重复提交返回同一 RefundId；关键内容冲突明确失败，不产生第二笔 Refund、第二次额度占用或重复渠道请求。
- A9：超出可退款余额、原 Payment 非 SUCCEEDED、merchant/币种不匹配、无有效渠道资格或超过 `succeededAt + 180 days` 时明确拒绝，且不创建渠道退款请求。
- A10：可退款余额 60.00 时，两笔真实并发 40.00 申请最多一笔进入处理；H2/JPA 双事务与乐观锁证据证明任何时刻成功额与占用额之和不超过支付金额，并发冲突映射为稳定 409。
- A11：渠道明确失败/拒绝时 Refund 失败并释放占用；可信未知结果继续占用。已受理但 30 分钟内无最终结果的 Refund 经普通 `@Scheduled -> Mediator.commands.send(...)` 扫描转为 `REVIEW_REQUIRED`，仍保留占用。
- A12：首次可信成功只形成一次成功退款事实并把占用转为成功额；重复通知只增加 receipt 接收次数；成功后的失败/未知不得回退状态，必须形成 conflict evidence。
- A13：Payment 查询返回支付金额、占用退款额、成功退款额与可退款余额；Refund 查询返回状态、关键时间、渠道 identity、额度裁决和全部 attempt/notification 摘要，且来自真实持久化数据。
- A14：`CreateRefundEndpoint`、`ConfirmRefundResultEndpoint`、`GetRefundEndpoint` 位于独立 contract；每个 Handler 一类一文件并使用静态 Mediator，HTTP binding 保持手写且路由、mapper、状态码与错误映射有自动化合同证据。
- A15：Payment 预算占用/释放/转成功与 Refund 创建/推进运行在同一 cap4k JPA UoW 和 Hibernate persistence context；任一步失败不留下悬空占用或无预算 Refund，不使用跨 ORM bridge 或 detached merge。
- A16：Analyzer 生成退款申请、退款渠道结果的 Endpoint HTTP Actor Flow 和超时核对 scheduled Time root；Mermaid 可解析，Drawing Board/Refund Aggregate Structure 分别提供 Command/Query 与聚合证据。
- A17：自动化测试覆盖 PAY-AC-020..029，包括 HTTP happy path、失败/未知/重复/矛盾回调、180 天期限、30 分钟待核对、两事务并发；AgentFacts 无 INVALID/error/plan-evidence-invalid，traceability 只把有真实路径与命令结果的 B2 evidence 转为 verified。

# Constraints and invariants

- Refund 是独立 Aggregate Root；PaymentId 是弱引用身份，不是 ORM relation 或懒加载代理。
- 原子不变量：`successfulRefundAmount + reservedRefundAmount <= paymentSucceededAmount`。
- 创建 Refund 时先在 Payment 内占用额度，再创建 Refund；任一步失败都由同一 UoW 事务回滚。
- Refund 明确失败/拒绝时释放占用；结果未知或待核对时继续占用；成功时将占用转换为成功退款累计额。
- Refund 成功事实不可回退；错误退款只能由后续独立业务事实处置。
- 外部渠道 raw code/message/identity 保持开放标量；有限生命周期状态和裁决分类使用生成 enum。
- B1 的 Payment 创建/支付尝试/支付回调/查询行为与全部验收不得回归。
- checked-in enum、VO、Endpoint contract、Behavior 和手写 adapter 文件重复 generation 必须 SKIP；build-owned entity/schema/repository source 可重建。
- 仓库继续使用 Gradle property `cap4k.local.path` > environment `CAP4K_LOCAL_PATH` > released 2.0.1 的 Composite 解析合同，不提交机器路径。

# Decisions

- B2 是单一可独立验收的 Native change，不拆 Supervisor；Refund 聚合、Payment 退款预算、渠道回调与证据闭环高度耦合。
- Refund 独立于 Payment 聚合，通过 PaymentId 弱引用；不引入双向关联网络。
- Payment 聚合作为退款额度并发裁决权威，Refund 保存退款自身生命周期和渠道证据。
- MerchantChannelConfiguration 增加 `refundWindowDays`；B2 seed/default fixture 使用 180 天。退款申请期限从 `Payment.succeededAt` 起算，而不是从 Payment 创建或渠道受理时间起算。
- MerchantChannelConfiguration 增加 `refundResultReviewAfterMinutes`；B2 seed/default fixture 使用 30 分钟。
- B2 提供普通 `@Scheduled` 扫描入口：渠道已受理但在配置阈值内未形成最终结果的 Refund 转入待人工核对状态，继续保留退款额度占用，并形成 Analyzer Time root。可靠调度、持久化投递与 lease/retry 仍属于 B5。
- B2 只完成 PAY-AC-020..029 的自动正常路径；大额退款人工审批和超期退款人工例外不进入本切片，后续分别作为独立可验收 change。
- B2 沿用 B1 的 Fake Gateway + HTTP callback 模式；可靠 transport 和 Outbox 留给 B5。
- 正常退款结果必须可供 B3 对账消费，但 B2 不提前实现对账聚合或消息 transport。

# Open questions

- 无。B2 Shape 已于 2026-08-18 获得用户确认。

# Verification expectations

- 在最新 mainline 与显式本地 cap4k Composite Build 上运行 clean build/check，并保持工作区干净。
- 首次 plan/generate 物化 Refund 相关 checked-in source；重复 generation 不覆盖已演进领域逻辑。
- domain tests 验证退款额度、状态机、Outcome/enum 不变量和成功不可回退。
- H2/JPA/UoW tests 验证跨 Payment/Refund 聚合的同事务占用、失败释放、并发防超退和乐观冲突。
- HTTP integration tests 验证 PAY-AC-020..029 的真实入口、持久化和查询结果。
- Analyzer 至少生成退款申请 Actor flow、退款渠道结果 Actor/Event flow，以及普通 scheduled scan 的独立 Time root。
- AgentFacts ownership/analysis/diagnostics 与 plan 一致；live DB freshness UNKNOWN 可导致 PARTIAL，但不得丢失 ownership 或产生 INVALID/error。
- traceability 只把有真实路径、命令和结果的 B2 acceptance/evidence 转为 verified，其余后续能力继续 not-built。
