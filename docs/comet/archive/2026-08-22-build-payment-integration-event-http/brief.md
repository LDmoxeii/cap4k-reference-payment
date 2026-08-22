# Outcome

在已经验收并合入 `main` 的 Payment、Refund、Reconciliation 与 Merchant Settlement 链上增加一个最小但生产形态明确的 B5：使用 cap4k 的可靠 Event 状态与 HTTP Integration Event transport，同时展示入站 Integration Event 监听、出站 Integration Event 发布、事务原子性、失败重试和业务幂等，而不把支付示例扩张成通用分布式基础设施项目。

# Scope

- 新增 dependency-leaf published language：
  - `ChannelStatementAvailableIntegrationEvent`；
  - `MerchantSettlementCompletedIntegrationEvent`。
- 入站账单事件表达“账单已经可获取”，只携带稳定定位、revision、时间与追踪信息，不携带完整账单明细。
- application listener 接收账单事件后进入现有 Reconciliation 应用路径；完整账单继续通过 `PullChannelStatement` Capability 获取。
- `ChannelStatementAvailableIntegrationEvent`、provider Pull、日终 scheduler 与人工 rerun 同时存在，并汇合到同一 channel/currency/business-date/statement identity/revision 幂等模型。
- Merchant Settlement 仅在首次形成已接受的终态成功事实时形成稳定 completion event identity，并发布 `MerchantSettlementCompletedIntegrationEvent`。
- 出站 event record 与授权发布的业务事务原子提交；事务回滚不得留下可投递事件。
- 使用 cap4k 当前生产 HTTP Integration Event transport；入站接收使用其 canonical endpoint，出站按稳定 event name 配置 route。
- 以可控 HTTP fake receiver 验证出站 2xx、非 2xx、超时、重试与恢复；不要求新增第二个生产应用。
- 入站消费依赖既有 Reconciliation 唯一性和 revision 规则保持业务幂等；重复 envelope 不得形成重复 effective run。
- 更新 README、requirements projection/traceability、canonical target Spec、Pipeline/Analyzer/AgentFacts 证据和 GitHub child #12 的生命周期映射。

# Non-goals

- 不引入 only-engine，且不把 only-engine addon 作为 B5 gate。
- 不引入 RabbitMQ、RocketMQ、Kafka 或其他 broker。
- 不实现 reliable Command。
- 不实现通用 Outbox/Inbox 产品层；可靠 Event 持久化使用 cap4k Runtime owner，业务侧只实现稳定事件身份与必要幂等。
- 不实现广播、动态服务发现、框架级消费去重、全局顺序、DLQ 管理界面或端到端 exactly-once。
- 不实现持久化 scheduler、lease、跨实例 scheduler exactly-once。
- 不替换 `PullChannelStatement`，不删除现有 scheduler 或人工 rerun。
- 不把 Payment、Refund、Reconciliation、Settlement 的全部状态变化都事件化。
- 不处理 #4 的 Payment timeout、late-result 与 conflict-review 业务硬化，也不执行 #8 的最终组合收口。
- 不实现 production channel、merchant notification service、银行清算网络或真实认证密钥管理。

# Acceptance examples

- A1: 合同模块声明两个稳定、可序列化、transport-neutral 的 v1 Integration Event，且保持 dependency leaf，不依赖 Spring、JPA、项目内模块或具体 transport。
- A2: 入站 `ChannelStatementAvailableIntegrationEvent` 通过 cap4k canonical HTTP Integration Event endpoint 被解码并交给 application listener；listener 不直接操作 Repository，而是发送 checked-in Command/application operation。
- A3: listener 根据 channel、currency、business date、statement identity 与 revision 调用现有 Pull 路径取得完整账单；事件 payload 不成为账单真源。
- A4: 同一 statement event 重复投递、scheduler 同日触发和人工 rerun 可以并发或乱序发生，但同 identity/revision 只形成一次有效 run；更高 revision 追加历史并成为 effective run，旧 revision 不回退当前事实。
- A5: 事件先到而账单暂不可读时处理失败保持可重试；后续恢复可使用同一稳定 event identity 完成，不创建重复有效批次。
- A6: Merchant Settlement 只有首次形成 accepted `SUCCEEDED` settled fact 时产生 `MerchantSettlementCompletedIntegrationEvent`；FAILED、RESULT_UNKNOWN、REVIEW_REQUIRED、冲突和重复 callback 不产生新的 completion event。
- A7: settlement 成功事务与 reliable outbound event record 原子提交；强制业务回滚后两者都不存在，提交成功后两者都存在。
- A8: HTTP receiver 首次返回非 2xx 或超时时，可靠 event 保持待重试/失败可观察状态；receiver 恢复后以相同 event identity 重试并成功交付。
- A9: 重复 callback、publisher retry 或 transport replay 不重复形成业务 completion fact；下游测试接收器可证明 envelope identity 稳定。
- A10: HTTP transport 使用当前生产固定接收路径与静态 event-name route；项目不手写第二套 HTTP event protocol。
- A11: 项目只增加当前 cap4k reliable Event/JPA owner 与 HTTP Integration Event starter 所需依赖和配置；缺少必需 provider 时 fail fast，不降级成直接不可靠 HTTP。
- A12: Analyzer/Drawing Board/Flow 只展示真实 Integration Event producer、listener 与现有入口，不伪造跨入口 exactly-once stitching；AgentFacts ownership、analysis 与 diagnostics 保持有效。
- A13: B1-B4 全量回归、clean build、plan/generation determinism、Mermaid、Composite Build 和 contract leaf 均继续通过。
- A14: README 和 traceability 明确 Push notification + provider Pull + scheduler fallback 的共存关系，以及 HTTP transport 的 at-least-once/业务幂等边界；不得声称 broker、only-engine 或 exactly-once 已实现。

# Constraints and invariants

- `contract` 仍是 dependency leaf；Integration Event published language 位于该模块。为构造和监听 published event，`application` 可以依赖 `contract + domain`，但 contract 反向不依赖任何业务模块。
- Domain model 不依赖 contract。若使用本地 Domain Event 表达 settlement completion，application subscriber 负责映射为 published Integration Event。
- Integration Event event name/version、event identity、correlation/causation identity 与 occurredAt 是稳定公开证据；业务对象、JPA Entity、Repository、Capability 实现不得进入 payload。
- `ChannelStatementAvailableIntegrationEvent` 是 availability signal，不是 statement content。statement provider 仍是完整渠道数据权威来源。
- Push、Pull、scheduler 和 rerun 必须共享既有 Reconciliation batch/run 唯一约束、revision 和 optimistic-lock 语义，不创建平行状态机。
- outbound event 采用 at-least-once handoff；业务幂等依赖稳定 event identity。不得把 provider handoff 成功解释为所有下游业务处理完成。
- HTTP Integration Event transport 一次只启用一个 outbound publisher；route 使用稳定 event name，不做 fallback publisher 链。
- 不提交机器绝对路径、用户级 Gradle 配置、broker 凭据或本机端口；测试 receiver 使用测试期动态配置。
- Composite Build 证据必须绑定 B5 实际使用的当前 cap4k accepted commit，不能继续复用 README 中旧的 `dcafcc...` 证据。

# Decisions

- B5 是一个紧耦合单 change，不拆为 transport、inbound 与 outbound 三个 change。
- 正式 GitHub child 为 #12；#4 继续独立拥有 Payment timeout/late-result/conflict-review，#8 继续拥有最终 composition。
- 入站采用 `ChannelStatementAvailableIntegrationEvent`，并与 Pull/scheduler/rerun 共存，不替换其中任何入口。
- 出站采用 `MerchantSettlementCompletedIntegrationEvent`，只绑定首次 accepted settlement-success fact。
- 两个 published event 使用显式 v1 stable event name；具体 Kotlin package/FQN 由 Build 在 contract leaf 下确定并由 contract tests 锁定。
- HTTP transport 是 B5 唯一 Integration Event transport；不引入 broker。
- reliable Event/outbox 使用 cap4k Runtime/JPA owner；业务仓不实现通用 Outbox。
- 出站跨边界由测试期可控 fake HTTP receiver 证明；不为此新增第二个生产 Spring Boot 应用。
- only-engine 不属于当前项目依赖和验收。
- B5 完成后仍需分别处理 #4 与 #8；published-coordinate cold start 继续后置到可用正式版本。

# Open questions

- 无。

# Verification expectations

- contract dependency-leaf、事件序列化、event name/version 与 payload 兼容性测试；
- Reconciliation 入站事件重复、乱序、并发、higher revision、provider 暂不可用后重试测试；
- Merchant Settlement completion single-fact/single-event、重复 callback、late conflict、unknown/adjudication 与 rollback 原子性测试；
- cap4k reliable Event/JPA record、HTTP non-2xx/timeout/retry/restart 与稳定 envelope identity 的真实 H2/HTTP 验证；
- 当前项目全量 `clean build`、既有 77+ tests 回归及新增 suite；
- `cap4kPlan`、连续 generation hash/无 checked-in 漂移、`cap4kAnalysisPlan`、`cap4kAnalysisGenerate`、Mermaid smoke 和 `cap4kAgentSnapshot`；
- Agent Snapshot 允许 live DB freshness UNKNOWN 导致 PARTIAL，但 ownership 不为空、analysis 为 ok、diagnostics 无 INVALID/error/plan-evidence-invalid；
- README、requirements projection、traceability 与 canonical target Spec 的 current-only 同步；
- `git diff --check`，且仓库不出现 sibling path、绝对路径、`mavenLocal()`、Snapshot 或机器本地配置。