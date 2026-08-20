# Outcome

交付第三个可运行支付引用切片 **B3：日终对账与差异处置**。在 B1 Payment 与 B2 Refund 已验收事实之上，按渠道、币种和业务日期创建唯一 ReconciliationBatch，通过固定的渠道账单拉取 Capability 获得不可变账单快照，逐笔核对平台支付/退款资金事实，持久化每次运行、双方原始证据、匹配依据、差异分类和追加式人工处置。

B3 必须继续以真实 H2/JPA、cap4k Repository/UoW、Command/Query/Capability/Endpoint、手写 HTTP binding、普通 `@Scheduled` reaction、Pipeline generation、Analyzer 与 AgentFacts 证明当前 cap4k 的实际能力，而不是以内存列表、mock-only 服务或覆盖原始事实的“修正”实现对账。

# Scope

- 新增 ReconciliationBatch 聚合根；同一 `channel + currency + reconciliationDate` 只存在一个有效批次。
- 使用 ReconciliationRun、ReconciliationItem 与 ReconciliationDisposition owned child 保存每次执行、双方事实快照、匹配依据、差异和人工处置；历史运行与原始证据不可改写。
- 以 Payment/Refund 的稳定业务身份、渠道交易/退款身份和业务发生时间形成平台侧 reconciliation facts；跨聚合只引用 Strong ID/稳定 identity，不建立 ORM 导航图。
- 使用 `PullChannelStatement` Capability 作为 B3 渠道日账单主路径；Fake provider 返回带 statement identity、revision、record identity、交易类型、金额、币种、状态、业务发生时间和平台接收时间的不可变账单。
- 提供普通 `@Scheduled -> Mediator.commands.send(...)` 日终入口；scheduled method 不直接操作 Repository。
- 自动覆盖完全匹配、平台单边、渠道单边、金额差异、币种差异、状态差异、重复渠道记录和无法关联；只允许稳定渠道交易/退款 identity 或明确记录的批准组合条件参与关联，禁止只按金额自动匹配。
- 同一 statement identity/revision 重放必须幂等；修订账单使用新 revision/identity，新增 ReconciliationRun 并保留旧运行，不产生重复有效差异。
- 提供批次查询、人工处置和显式重跑的独立 Endpoint contract；每个 Endpoint Handler 一类一文件并使用静态 Mediator，HTTP binding 保持手写。
- 对未决差异实施结算阻断；只有账单完整、所有记录已核对且全部差异已匹配或具有明确处置结论时，批次才能完成。
- 获授权人员可对差异追加负责人、证据、结论、时间和后续动作；未授权操作被拒绝并留痕。
- 对“平台结果待确认而渠道成功”或确认的漏记成功，形成独立、可追踪的新确认事实，不删除或覆盖 Payment、Refund、渠道账单或旧对账结果。
- 使用 Generator 生成 Reconciliation aggregate graph、Strong ID、Repository/Factory/Behavior skeleton、有限业务 enum 与必要的非持久化结构值；checked-in source 允许补充领域行为。
- 更新 README 与 traceability，使 B1/B2 当前完成状态、B3 evidence 和后续 B4-B6 路线与真实项目一致。
- 重新生成并验证 plan、Analyzer Flow/Drawing Board/Aggregate Structure 与 AgentFacts。

# Non-goals

- B4 商户结算、结算金额计算、SettlementLine 和真实资金划拨。
- B5 Outbox、可靠 Command/Event、Integration Event transport、持久化调度、lease/retry、跨实例 exactly-once 和 only-engine addon gate。
- 通过入站 bill-arrival Integration Event 作为 B3 主路径；该 transport 可在 B5 另行实现。
- 通用对账规则 DSL、通用匹配引擎、财务总账、会计分录、拒付/争议/欺诈调查。
- 保存真实敏感渠道凭据或完整生产账单文件；fixture 只保存完成业务验证所需的最小脱敏事实。
- 修改、删除或覆盖 Payment/Refund 已形成的原始成功/失败事实。
- 通用 RBAC、双人复核工作流或 Saga；B3 只证明授权/未授权运营动作与审计留痕。
- Jimmer/aggregateProjection、Endpoint Handler generator、published-coordinate cold start。

# Acceptance examples

- A1：B1/B2 的 contract leaf、依赖方向、Payment/Refund 行为、Endpoint Handler/静态 Mediator/手写 HTTP binding、Composite Build 解析合同和全部已验收测试保持通过。
- A2：真实 schema/design/enum/value-object 输入经 cap4k plan/generate 产生 ReconciliationBatch、ReconciliationRun、ReconciliationItem、ReconciliationDisposition、Strong ID、Repository、Factory/Behavior、业务 enum、Command/Query/Capability/Endpoint；checked-in source 重复 generation 必须 SKIP，build-owned source可重建且 ownership 可解释。
- A3：ReconciliationBatch 是独立 Aggregate Root；PaymentId、RefundId、PaymentAttemptId、RefundAttemptId 等仅作为稳定弱引用或事实 identity 保存，不存在跨聚合 ORM relation、lazy proxy 或整取对象图。
- A4：普通日终 scheduler 只发送 Command；Command 按业务时区计算 reconciliationDate，并按 `channel + currency + date` 幂等创建/装载唯一批次，再通过 PullChannelStatement Capability 拉取账单。
- A5：同渠道交易号、同币种、同金额、同最终状态的 100.00 CNY 支付判定 MATCHED；成功退款同样参与核对，平台和渠道两侧快照均可查询。
- A6：自动测试分别证明 PLATFORM_ONLY、CHANNEL_ONLY、AMOUNT_MISMATCH、CURRENCY_MISMATCH、STATUS_MISMATCH、DUPLICATE_CHANNEL_RECORD 与 UNMATCHED，且任何差异都不以一方值覆盖另一方。
- A7：相同 statement identity/revision 重放不产生第二次有效运行或重复有效差异；新 revision 形成新的 ReconciliationRun，旧 run/items 保留，当前有效结果可区分。
- A8：批次存在未决差异、账单不完整或账单未取得时不能完成；全部明细匹配或差异已有明确 disposition 后才能完成，并公开未决数量和阻断原因。
- A9：未授权运营人员处置被拒绝并留下审计记录；获授权人员的追加式 disposition 包含负责人、处置时间、证据、结论和 follow-up，不修改原平台事实、渠道记录和最初差异。
- A10：对于平台结果待确认但渠道成功且身份/金额/币种匹配的状态差异，授权确认形成新的 ReconciliationConfirmationFact；原 Payment/Refund 状态记录和渠道证据保持可见，确认事实可被后续 B4 作为有效资金事实消费。
- A11：独立 contract 至少提供 GetReconciliationBatchEndpoint、DisposeReconciliationDifferenceEndpoint 与 RerunReconciliationBatchEndpoint；每个 Handler 一类一文件、静态 Mediator、手写 HTTP binding 的 method/path/status/mapper 有合同测试。
- A12：H2/JPA/UoW 测试证明同范围唯一批次、运行/明细/处置聚合图原子保存、重复触发幂等以及并发创建或重跑不产生两个有效结果；典型并发冲突映射为稳定 409。
- A13：Analyzer 至少生成日终 scheduler 的 Time root、人工处置与重跑的 Endpoint HTTP Actor flow；Mermaid 可解析，Command/Query/Capability 与 Reconciliation Aggregate Structure 由各自 projection 提供证据，不伪造跨入口 stitching。
- A14：自动化测试覆盖 PAY-AC-040..047、PAY-AC-082 和 PAY-AC-085；PAY-AC-083 只增加 B3 增量轨迹证据，不在 B4 结算完成前宣称全链路已验证。
- A15：AgentFacts 无 INVALID/error/plan-evidence-invalid，ownership 非空；live DB freshness UNKNOWN 可导致 PARTIAL。traceability 只把有真实路径与命令结果的 B3 evidence 转为 verified，并同步 README 的当前状态和数量。

# Constraints and invariants

- 有效批次唯一键：`channel + currency + reconciliationDate`；同范围重跑属于同一批次的新 run，不创建并行有效批次。
- 渠道 statement identity/revision 与 record identity 必须稳定；同 revision 幂等，新 revision 追加，不覆盖。
- Payment/Refund 与渠道记录是不可改写的双方证据；ReconciliationItem 只保存快照、关联依据和差异结论。
- 只允许稳定渠道交易/退款 identity 优先关联；批准的辅助组合条件必须持久化说明，禁止仅凭金额相等自动关联。
- 批次完成条件同时要求 statement 完整、run 完成、全部记录已核对、所有差异已匹配或已有明确 disposition。
- 未决且影响成功状态、金额或币种的差异阻断后续自动结算。
- 人工处置、状态确认和资金事实补录均采用追加事实，不得修改旧 run/item、Payment/Refund 原始事实或渠道证据。
- 所有原始业务发生时间与平台接收时间均保留；reconciliationDate 由统一业务时区决定。
- checked-in enum、VO、Endpoint contract、Behavior 和手写 adapter 重复 generation 必须 SKIP；build-owned entity/schema/repository source可重建。
- 仓库继续使用 Gradle property `cap4k.local.path` > environment `CAP4K_LOCAL_PATH` > released 2.0.1 的解析合同，不提交机器路径。

# Decisions

- B3 是单一可独立验收的 Native change，不拆 Supervisor；批次、账单拉取、自动匹配、差异处置和重跑共同构成一个不可分割的业务闭环。
- ReconciliationBatch 独立于 Payment/Refund 聚合，只保存 Strong ID/稳定 identity 与不可变事实快照，不引入跨聚合 ORM graph。
- 每个对账范围只有一个批次；每次执行形成独立 ReconciliationRun，当前有效 run 与历史 run 明确区分。
- B3 提供最小运营 HTTP 面：查询批次、处置差异、显式重跑；不为 scheduler 伪造 HTTP 入口。
- Authorization 使用 reference 级显式 operator identity/role fixture 验证允许与拒绝，不建设通用 RBAC 或双人复核引擎。
- B3 对 PAY-AC-083 只写入支付→退款→对账的增量轨迹，不在 B4 完成前标记整个验收为 verified。

# Open questions

- 无。Q1-Q4 已于 2026-08-19 获得用户确认，B3 完整 Shape 已确认。

# Verification expectations

- 在最新 main 与显式本地 cap4k Composite Build 上运行 clean build，并保持仓库不含机器路径。
- 首次 plan/generate 物化 Reconciliation checked-in source；重复 generation 不覆盖已演进领域逻辑；build-owned graph 可 clean 后重建。
- domain tests 验证批次唯一、匹配分类、run/revision 幂等、完成条件、处置/确认追加式不变量。
- H2/JPA/UoW tests 验证批次/运行/明细/处置完整聚合图、并发唯一性、rollback 和 409 冲突。
- HTTP integration tests 覆盖 PAY-AC-040..047、PAY-AC-082、PAY-AC-085 以及查询/处置/重跑 contracts。
- Analyzer 至少产生一个 reconciliation scheduled Time root 和两个 reconciliation Endpoint HTTP Actor flow，Mermaid parser smoke 通过。
- AgentFacts ownership/analysis/diagnostics 与 plan 一致；live DB freshness UNKNOWN 可以导致 PARTIAL，但不得丢失 ownership 或产生 INVALID/error。
- traceability 写回 PAY-EV-006/007/014/015 的实际路径、命令、数量与结果；README 更新为 B1+B2 当前完成、B3 本轮完成事实，B4-B6 继续后置。
