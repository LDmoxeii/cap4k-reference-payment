# 支付自动化测试证据目录（可执行教材）

> 本文件只整理**自动化测试证据**，不替代人工验收指南，也不启动人工验收教学。业务预期以 [`payment-scenarios.md`](payment-scenarios.md) 为 What，状态以 [`traceability.yaml`](../traceability.yaml) 为机器真源；本目录说明如何用测试理解 Given / When / Then、证据边界与未覆盖内容。

## 1. 使用方式

```powershell
# 先定位单个证据（失败时保留完整输出）
.\gradlew.bat :domain:test --tests '*PaymentBehaviorTest' --no-daemon --console=plain
.\gradlew.bat :start:test --tests '*PaymentReferenceApplicationTests' --no-daemon --console=plain
.\gradlew.bat :start:test --tests '*ReconciliationReferenceApplicationTests' --no-daemon --console=plain
.\gradlew.bat :start:test --tests '*MerchantSettlementReferenceApplicationTests' --no-daemon --console=plain

# 最终回归：focused test 不能替代这一条
.\gradlew.bat clean build --no-daemon --console=plain
```

测试运行在 H2、Fake Provider、动态测试 receiver 和普通 `@Scheduled` 触发器边界内。通过不等于生产银行/清算网络、生产通知、跨实例 exactly-once 或完整认证授权已完成。

## 2. 测试阅读法（每个主测试都遵守）

- **Given / Arrange**：fixture 明确商户、订单、金额、币种、渠道、到期时间、尝试/账单/review 前提；关键前提不藏在通用 helper 中。
- **When / Act**：只走真实业务入口（Domain behavior、Command/Capability、MockMvc HTTP、scheduler 或 integration-event listener），不调用测试专用旁路改变业务事实。
- **Then / Assert**：按以下维度读取断言：支付状态、尝试状态、成功事实、复核、通知意图、结算资格、持久化结果、外部/原始证据。
- **辅助测试**：只补充结果对象不变量、HTTP/契约/结构、Analyzer/traceability 以及持久化回读；它们不能把局部证据升级成完整场景。

### 2.1 证据层次

| 层次 | 典型测试 | 证明 | 不证明 |
|---|---|---|---|
| Domain behavior | `PaymentBehaviorTest`、`MerchantSettlementBehaviorTest`、`ReconciliationBatchBehaviorTest` | 状态机、幂等、不可回退、追加式证据和金额/资格不变量 | JPA/HTTP wiring、真实并发调度、生产 provider |
| Application/JPA/HTTP | `PaymentReferenceApplicationTests`、`ReconciliationReferenceApplicationTests`、`MerchantSettlementReferenceApplicationTests` | 真实 Command + UoW + H2 持久化、HTTP 入口、并发收敛和查询回读 | 生产网络、跨实例 exactly-once、完整 RBAC/租户隔离 |
| Adapter/contract | Endpoint/HTTP configuration、`PaymentHttpErrorAdviceTest`、Integration Event contract | 入口路径、mapper、稳定 code/status、契约和薄 scheduler 边界 | 业务规则本身已被执行 |
| Evidence guard | `AcceptanceGuideContractTests`、`TraceabilityContractTests`、本目录契约测试 | 场景/证据/状态映射完整、planned/not-built 不被误宣称、引用可定位 | 场景运行时行为 |
| Analyzer/Flow | `TraceabilityContractTests`、生成的 `flows/*.json` 与 Analysis/AgentFacts 检查 | 静态入口可观察、文档和 evidence 可追踪 | 跨入口运行时 exactly-once stitching |

## 3. 主测试与辅助测试索引

| 业务族 | 主测试文件 | 辅助测试文件 | 阅读重点 |
|---|---|---|---|
| 支付 | `start/src/test/.../PaymentReferenceApplicationTests.kt`、`domain/src/test/.../PaymentBehaviorTest.kt` | `ChannelResultRecordingOutcomeTest`、`ConfirmPaymentResultCmdContractTest`、Payment endpoint/HTTP configuration、`PaymentExpirySchedulerStructureTest` | Payment/Attempt/Receipt 状态、成功事实只形成一次、review/notification intent、HTTP/JPA 回读 |
| 退款 | `start/src/test/.../PaymentReferenceApplicationTests.kt` | `RefundResultRecordingOutcomeTest`、Refund endpoint/HTTP configuration | reserved/successful/refundable 预算、退款 attempt/receipt、失败释放、未知继续占用、并发冲突 |
| 对账 | `start/src/test/.../ReconciliationReferenceApplicationTests.kt`、`domain/src/test/.../ReconciliationBatchBehaviorTest.kt` | reconciliation endpoint/HTTP configuration、`DailyReconciliationSchedulerStructureTest`、`TraceabilityContractTests` | 双方事实快照、difference、revision/effective run、append-only disposition/confirmation、时区 |
| 结算 | `start/src/test/.../MerchantSettlementReferenceApplicationTests.kt`、`domain/src/test/.../MerchantSettlementBehaviorTest.kt` | settlement endpoint/HTTP configuration、scheduler structure、Integration Event contract | candidate/line/net、composition freeze、execution/review、completion once、可靠事件记录 |
| 横切/缺口 | `AcceptanceGuideContractTests`、本目录契约测试、`TraceabilityContractTests`、`UserVisibleMessageContractTests` | `ContractDependencyLeafTest`、`DatabaseSchemaCommentContractTests`、Analyzer/Flow 产物 | 证据目录完整性、planned/not-built、HTTP/Analyzer/设计边界；不能替代业务行为测试 |

## 4. PAY-AC → PAY-BR → 测试证据矩阵

> 每行都明确“证明了什么”和“没有证明什么”。同一主测试覆盖多个场景时只复用证据，不复制业务测试。

| PAY-AC | 状态 | 主测试 / 辅助测试 | PAY-BR | 测试证明了什么 | 测试没有证明什么 |
|---|---|---|---|---|---|
| PAY-AC-001 | verified | `PaymentReferenceApplicationTests#create attempt confirm duplicate conflict and query form one durable payment chain` | PAY-BR-001, PAY-BR-020 | 首次创建、发起尝试、可信成功、查询回读和持久化链路存在 | 生产渠道受理或异步网络可靠性 |
| PAY-AC-002 | verified | 同上；`PaymentBehaviorTest#accepted channel result forms success once and later failure cannot roll it back` | PAY-BR-001, PAY-BR-002 | 相同幂等内容复用既有 Payment，重复通知只增接收计数 | 不同部署实例间的幂等存储 |
| PAY-AC-003 | verified | `PaymentReferenceApplicationTests#create attempt confirm duplicate conflict and query form one durable payment chain`；Payment HTTP configuration | PAY-BR-001, PAY-BR-002 | 幂等键内容冲突返回稳定错误并可观察 | 业务方重试策略和网关错误码一致性 |
| PAY-AC-004 | verified | 同上；`PaymentBehaviorTest#accepted channel result forms success once and later failure cannot roll it back` | PAY-BR-021 | 可信成功推进 Payment/Attempt、形成 success fact 与 fee snapshot | 签名/证书生产轮换 |
| PAY-AC-005 | verified | 同上；`ChannelResultRecordingOutcomeTest#accepted success exposes derived domain semantics` | PAY-BR-001, PAY-BR-006 | 同 notification identity 精确重放为 duplicate，不形成第二成功事实 | 通知 transport 至少一次投递本身 |
| PAY-AC-006 | verified | `PaymentBehaviorTest#verified channel mismatch is rejected and retained as a notification receipt`；PaymentHttpErrorAdviceTest | PAY-BR-024 | 不可信/渠道不匹配通知被拒绝，receipt 和拒绝摘要保留，状态不推进 | 真实签名验证器和密钥托管 |
| PAY-AC-007 | verified | `PaymentReferenceApplicationTests#payment expiry closes without pending attempts and repeated scans stay idempotent`；PaymentExpirySchedulerStructureTest | PAY-BR-026, PAY-BR-063 | 到期无处理中尝试进入 CLOSED，重复扫描幂等且不再新建尝试 | durable scheduler、跨实例抢占 |
| PAY-AC-008 | verified | `PaymentReferenceApplicationTests#expired processing payment enters one stable review and trustworthy success resolves it`；`PaymentBehaviorTest#expired payment with processing attempt becomes result unknown with one stable review` | PAY-BR-026 | 到期存在处理中尝试进入 RESULT_UNKNOWN，review 稳定且后续可信结果可收敛 | 超时升级时长这一待确认参数 |
| PAY-AC-009 | verified | `PaymentReferenceApplicationTests#late success after closed payment preserves terminal evidence until authorized review`；`PaymentBehaviorTest#late success after closed terminal preserves terminal state and opens held review` | PAY-BR-027 | 终态后的迟到成功保留 external evidence、保持终态并阻断结算，等待 review | 人工团队实际处理时效 |
| PAY-AC-010 | verified | `PaymentReferenceApplicationTests#second attempt success preserves both successes while revenue and intent remain once only`；`PaymentBehaviorTest#two trustworthy attempt successes form revenue fee and notification intent only once` | PAY-BR-021, PAY-BR-022 | 多 attempt 成功全部留痕，但 revenue/fee/notification intent 只有一份 | 渠道侧撤销/补偿协议 |
| PAY-AC-011 | verified | `PaymentReferenceApplicationTests#concurrent payments for one merchant order retain loser evidence and only one accepted success claim` | PAY-BR-003 | 同订单竞争最多一个 accepted success，失败竞争保留 receipt/review | PAY-BR-003 更宽“失败后是否允许重付”产品策略 |
| PAY-AC-012 | verified | `PaymentReferenceApplicationTests#invalid amount precision and unsupported currency never reserve an idempotency key`；PaymentHttpErrorAdviceTest | PAY-BR-010, PAY-BR-012 | 金额/精度/币种输入拒绝且不占用幂等键，HTTP 错误可读 | 生产币种资格配置发布流程 |
| PAY-AC-013 | verified | `PaymentReferenceApplicationTests#create attempt confirm duplicate conflict and query form one durable payment chain` | PAY-BR-011 | 已创建 Payment 的关键内容不可被不同幂等请求改写 | 管理后台修改权限 |
| PAY-AC-014 | verified | `MerchantSettlementReferenceApplicationTests#configuration changes do not rewrite frozen payment fees or settlement lines`；`PaymentBehaviorTest#accepted channel result forms success once and later failure cannot roll it back` | PAY-BR-014 | 成功时费率快照冻结，并在结算 line 中可追踪 | 费率配置中心的生产一致性 |
| PAY-AC-015 | verified | `PaymentReferenceApplicationTests#review and callback race preserves the authorized decision and later conflict evidence`；`PaymentBehaviorTest#accepted channel result forms success once and later failure cannot roll it back` | PAY-BR-023 | 成功后的失败/未知不回退成功事实，冲突 evidence 与 review 保留并阻断资格 | 外部争议处理协议 |
| PAY-AC-016 | verified | `PaymentReferenceApplicationTests#create attempt confirm duplicate conflict and query form one durable payment chain` | PAY-BR-025 | Gateway ACCEPTED 只表示处理中，最终成功依赖可信 callback | 渠道异步通知延迟分布 |
| PAY-AC-017 | verified | `PaymentReferenceApplicationTests#attempt identity and currency mismatches remain queryable without advancing payment`；Payment endpoint tests | PAY-BR-028 | 成功后新尝试和不匹配输入被拒绝，原 payment 可查询 | 生产认证和操作员授权 |
| PAY-AC-020 | verified | `PaymentReferenceApplicationTests#a successful payment can be refunded in full` | PAY-BR-012, PAY-BR-014, PAY-BR-030, PAY-BR-031 | 成功支付可全额退款，Payment 预算与 Refund 持久化一致 | 真实退款渠道结算 |
| PAY-AC-021 | verified | `PaymentReferenceApplicationTests#multiple partial refunds remain independently queryable and update payment budget` | PAY-BR-031 | 多次部分退款独立可查询，reserved/successful/refundable 预算正确 | 退款批量清分 |
| PAY-AC-022 | verified | `PaymentReferenceApplicationTests#refund beyond the exact remaining amount is rejected without a channel request` | PAY-BR-031 | 超额退款在渠道调用前拒绝，不改变预算 | 人工例外审批 |
| PAY-AC-023 | verified | `PaymentReferenceApplicationTests#two concurrent refund HTTP applications persist one refund and return stable conflict`；`PaymentReferenceApplicationTests#two real transactions cannot over-reserve one payment refund budget` | PAY-BR-032 | 真实事务并发下最多一个预算占用成功，另一方得到稳定冲突 | 高并发压力曲线和跨节点锁竞争 |
| PAY-AC-024 | verified | `PaymentReferenceApplicationTests#trusted failed refund result releases its payment reservation`；`PaymentReferenceApplicationTests#refund creation database failure rolls back payment reservation and refund aggregate together` | PAY-BR-034 | 退款失败/回滚释放预占，跨聚合 UoW 不留下半成品 | 数据库灾备与人工补偿 |
| PAY-AC-025 | verified | `PaymentReferenceApplicationTests#unknown refund result remains reserved and scheduled review marks it`；`RefundResultRecordingOutcomeTest#outcome rejects contradictory or incomplete evidence` | PAY-BR-034 | UNKNOWN 保留预占、进入 review，不得按失败释放 | review_after_at 参数尚未冻结 |
| PAY-AC-026 | verified | `PaymentReferenceApplicationTests#merchant refund number replay rejects changed critical content without a second refund`；`PaymentReferenceApplicationTests#refund accepted callback is idempotent and queryable` | PAY-BR-005 | 退款申请与 callback 幂等，关键内容改变产生冲突证据 | 跨系统幂等键治理 |
| PAY-AC-027 | verified | `PaymentReferenceApplicationTests#refund rejects non-success payments and requests after the refund window` | PAY-BR-012, PAY-BR-030 | 非成功支付或不合格渠道资格不能退款 | 退款期限产品参数的最终确认 |
| PAY-AC-028 | verified | `PaymentReferenceApplicationTests#refund rejects non-success payments and requests after the refund window` | PAY-BR-033 | succeededAt + refundWindowDays 形成期限判断并拒绝逾期申请 | 退款期限阈值（业务规则待确认） |
| PAY-AC-029 | verified | `PaymentReferenceApplicationTests#merchant refund number replay rejects changed critical content without a second refund`；`RefundResultRecordingOutcomeTest#outcome rejects contradictory or incomplete evidence` | PAY-BR-035 | 成功退款后的冲突结果不回退成功预算/状态，receipt 追加 | 渠道撤销的真实资金回收 |
| PAY-AC-040 | verified | `ReconciliationReferenceApplicationTests#daily reconciliation matches payment and refund facts and exposes one effective run`；`ReconciliationBatchBehaviorTest#classifies matched payment and every required difference without overwriting either snapshot` | PAY-BR-012, PAY-BR-040, PAY-BR-041, PAY-BR-042, PAY-BR-063 | 完全匹配形成 run/item，双方金额和状态快照可追踪 | 生产账单格式兼容性 |
| PAY-AC-041 | verified | `ReconciliationReferenceApplicationTests#matched payment with blocking review stays unresolved and snapshots review evidence`；`ReconciliationBatchBehaviorTest#matched payment with blocking review remains unresolved and preserves the run snapshot` | PAY-BR-041, PAY-BR-043, PAY-BR-045 | 平台单边/阻断 review 保持 unresolved 并阻断结算 | 人工处置 SLA |
| PAY-AC-042 | verified | `ReconciliationReferenceApplicationTests#unknown refund and successful channel statement form confirmation without rewriting original refund` | PAY-BR-041, PAY-BR-043 | 渠道单边可经授权 confirmation 收敛，原退款事实不改写 | 账单供应商真实性 |
| PAY-AC-043 | verified | `ReconciliationReferenceApplicationTests#authorized amount mismatch disposition preserves both amounts and appends an independent conclusion`；`ReconciliationBatchBehaviorTest#classifies matched payment and every required difference without overwriting either snapshot` | PAY-BR-041, PAY-BR-043 | 金额差异保留双方金额并追加独立结论 | 财务最终裁量标准 |
| PAY-AC-044 | verified | `ReconciliationBatchBehaviorTest#same statement identity and revision is idempotent while a new revision supersedes and retains history`；`ReconciliationReferenceApplicationTests#statement replay revision history and disposition preserve immutable evidence` | PAY-BR-041, PAY-BR-042, PAY-BR-044, PAY-BR-061 | 同 identity+revision 幂等，新 revision 成为 effective，旧 revision 历史保留 | 账单延迟最大等待时间 |
| PAY-AC-045 | verified | `ReconciliationReferenceApplicationTests#inbound statement event replays once and a newer revision becomes effective without late rollback`；`ReconciliationBatchBehaviorTest#same statement identity and revision is idempotent while a new revision supersedes and retains history` | PAY-BR-040, PAY-BR-041, PAY-BR-044 | Push/Pull/rerun 共享 run identity，重复不新增有效 run | 通用 Inbox/跨实例 exactly-once |
| PAY-AC-046 | verified | `ReconciliationReferenceApplicationTests#matched payment with blocking review stays unresolved and snapshots review evidence`；`ReconciliationBatchBehaviorTest#incomplete statement and unresolved differences block completion` | PAY-BR-046 | 未决差异和不完整账单阻断完成/结算 | 运营团队补证流程 |
| PAY-AC-047 | verified | `ReconciliationReferenceApplicationTests#statement replay revision history and disposition preserve immutable evidence`；`ReconciliationBatchBehaviorTest#authorized disposition resolves difference and appends a confirmation fact` | PAY-BR-041, PAY-BR-044, PAY-BR-062 | disposition/confirmation 追加式保存，原始 statement/platform fact 不被覆盖 | 审计系统外部归档 |
| PAY-AC-060 | verified | `MerchantSettlementReferenceApplicationTests#merchant settlement lifecycle produces net 127 and preserves callback evidence` | PAY-BR-012, PAY-BR-013, PAY-BR-014, PAY-BR-015, PAY-BR-050, PAY-BR-052 | eligible lines、gross/fee/net 恒等式与结算金额可回读 | 多币种结算 |
| PAY-AC-061 | verified | `MerchantSettlementReferenceApplicationTests#one unresolved reconciliation item is excluded while another matched payment settles`；`ReconciliationBatchBehaviorTest#matched payment with blocking review remains unresolved and preserves the run snapshot` | PAY-BR-045 | unresolved item 按交易粒度排除，不因旧 matched 摘要绕过 blocker | 更宽 projection closure |
| PAY-AC-062 | verified | `MerchantSettlementReferenceApplicationTests#concurrent HTTP prepare converges on one effective settlement`；`MerchantSettlementBehaviorTest#replacement activation claims canonical effective ownership idempotently` | PAY-BR-051 | 同周期只有一个 effective settlement，重复 prepare/ownership 激活收敛 | 分布式 scheduler/lease |
| PAY-AC-063 | verified | `MerchantSettlementReferenceApplicationTests#merchant settlement lifecycle produces net 127 and preserves callback evidence`；`MerchantSettlementBehaviorTest#first verified success forms one settled fact and exact replay only increments receipt counters` | PAY-BR-055 | execution attempt、可信成功、settled fact 和完成事件形成一次 | 下游商户业务是否完成 |
| PAY-AC-064 | verified | `MerchantSettlementReferenceApplicationTests#unknown result blocks retry until review and manual adjudication`；`MerchantSettlementBehaviorTest#unknown result blocks retry until frozen threshold and authorized adjudication appends final evidence` | PAY-BR-054, PAY-BR-055 | UNKNOWN 阻断重试，达到阈值后授权裁决追加证据 | 双人复核阈值仍待确认 |
| PAY-AC-065 | verified | `MerchantSettlementReferenceApplicationTests#merchant settlement lifecycle produces net 127 and preserves callback evidence`；`MerchantSettlementBehaviorTest#same notification with another payload and late opposite final result are conflicts without success rollback` | PAY-BR-055 | 成功后的矛盾结果为 CONFLICT，不回退 settled fact | 真实银行撤销/追偿 |
| PAY-AC-066 | verified | `MerchantSettlementReferenceApplicationTests#negative and zero net settlements never invoke the transfer provider`；`MerchantSettlementBehaviorTest#confirmation freezes positive composition while zero completes without transfer and negative remains review-only` | PAY-BR-056 | 零净额不划拨，负净额进入 review 且不调用 provider | 负结算额追偿策略（待确认） |
| PAY-AC-067 | verified | `MerchantSettlementReferenceApplicationTests#configuration changes do not rewrite frozen payment fees or settlement lines`；`MerchantSettlementBehaviorTest#confirmation freezes positive composition while zero completes without transfer and negative remains review-only` | PAY-BR-053 | confirm 后 composition 冻结，后续配置/调整不改写已确认 line | 生产账务更正工作流 |
| PAY-AC-068 | verified | `MerchantSettlementReferenceApplicationTests#void replacement keeps a single effective settlement and preserves source evidence`；`MerchantSettlementBehaviorTest#replacement activation claims canonical effective ownership idempotently` | PAY-BR-006, PAY-BR-050 | void/replacement 链保持单一 effective ownership，source evidence 留存 | 周结/跨周期汇总 |
| PAY-AC-080 | planned/not-built | `AcceptanceGuideContractTests#guide covers every acceptance id and preserves traceability statuses`；`TraceabilityContractTests#verified traceability is internally consistent and preserves deferred boundaries` | PAY-BR-060（映射存在但未实现） | 证明商户隔离仍是 planned，目录不把 fixture 当成实现 | 不能证明 tenant isolation 或生产数据隔离 |
| PAY-AC-081 | planned/not-built | 同上；`UserVisibleMessageContractTests#user visible messages are Chinese and provider raw causes are never projected` 仅证明消息边界 | （traceability 未正向回填 PAY-BR；待确认通知重试规则） | 证明测试 receiver 不是生产商户通知服务，场景保持缺口 | 不能证明商户通知失败重试/截止策略 |
| PAY-AC-082 | verified | `ReconciliationReferenceApplicationTests#statement replay revision history and disposition preserve immutable evidence`；`ReconciliationBatchBehaviorTest#authorized disposition resolves difference and appends a confirmation fact` | PAY-BR-036, PAY-BR-041, PAY-BR-044, PAY-BR-061, PAY-BR-062 | 更正通过 revision/disposition/confirmation 追加，原始证据不覆盖 | 外部审计系统签章 |
| PAY-AC-083 | verified | `MerchantSettlementReferenceApplicationTests#payment refund reconciliation and settlement preserve one durable composition trail`；`AcceptanceGuideContractTests#guide names real precise test methods for all four business families` | PAY-BR-021, PAY-BR-052 | 一条 H2/JPA 轨迹串起 Payment→Refund→Reconciliation→Settlement，并回溯 source identities | 生产跨系统全链路追踪 |
| PAY-AC-084 | planned/not-built | `TraceabilityContractTests#verified traceability is internally consistent and preserves deferred boundaries`；`AcceptanceGuideContractTests#guide covers every acceptance id and preserves traceability statuses` | PAY-BR-020, PAY-BR-064（EV-010 not-built） | 证明 retired-channel 场景没有可执行 evidence，配置存在不等于完成 | 不能证明退役渠道管理 API/发布流程 |
| PAY-AC-085 | verified | `ReconciliationReferenceApplicationTests#Asia Shanghai business day boundary separates 2359 and 0001 while preserving instants`；`MerchantSettlementReferenceApplicationTests#configuration changes do not rewrite frozen payment fees or settlement lines` | PAY-BR-063 | Asia/Shanghai 业务日半开区间和 Instant 持久化边界明确 | 其他时区运营策略 |
| PAY-AC-086 | planned/not-built | `TraceabilityContractTests#verified traceability is internally consistent and preserves deferred boundaries`；`MerchantSettlementBehaviorTest#only authorized operators may confirm adjudicate or void` 只证明领域角色守卫 | PAY-BR-061（完整认证授权未实现） | 证明领域 fixture 的最小 operator guard，不升级为完整 RBAC | 不能证明生产认证、双人复核、审计留痕闭环 |
| PAY-AC-087 | verified | `ReconciliationReferenceApplicationTests#inbound statement event replays once and a newer revision becomes effective without late rollback`；`ReconciliationReferenceApplicationTests#inbound statement event retries after provider recovery and converges with scheduler and rerun` | （traceability 未正向回填 PAY-BR；依据 statement availability/Pull 语义） | event 只触发可获取信号，权威账单仍由 Pull，重复/恢复/高 revision 收敛 | 不证明 generic Inbox 或 broker exactly-once |
| PAY-AC-088 | verified | `MerchantSettlementReferenceApplicationTests#outbound HTTP event keeps one identity across failed handoff and durable retry`；`MerchantSettlementReferenceApplicationTests#outbound HTTP event remains retryable after response timeout and recovers with the same identity`；`MerchantSettlementReferenceApplicationTests#settlement execution database failure rolls back root and owned attempt together` | （traceability 未正向回填 PAY-BR；依据 PAY-BR-054/055 与事件原子性） | failed handoff/timeout 后同 event identity 重试，UoW rollback 不留下孤儿 completion/event record | 不证明下游业务 exactly-once、生产通知或通用 outbox |

## 5. 缺口与边界证据

1. **真实 HTTP 入口已有**：`start` 的三组 `@SpringBootTest + @AutoConfigureMockMvc` 是业务 round-trip 证据；`adapter` 的结构/配置测试只是薄壳契约，不能单独证明业务执行。
2. **并发证据是事务级收敛**：支付、退款、对账、结算均有真实双事务/optimistic-lock/唯一约束测试；当前没有线程压力、跨实例、网络分区或性能基准。
3. **Analyzer Flow 是静态证据**：`TraceabilityContractTests`、`AcceptanceGuideContractTests` 和 generated `flows`/AgentFacts 检查能证明入口可观察与引用可追踪，不证明运行时跨入口 exactly-once。
4. **planned/not-built**：`PAY-AC-080/081/084/086` 保持 planned；`PAY-EV-010/025/026` 是缺口证据。不得因为有 operator fixture、channel configuration、测试 receiver 就升级状态。
5. **待确认规则不写死**：重复支付（PAY-BR-003）、超时升级、退款期限阈值、账单最大等待、负结算处理、双人复核阈值、周结、通知重试截止策略仍以业务规则文档的待确认项为准。
6. **不宣称生产能力**：Fake Provider、H2、普通 Scheduler、动态测试 receiver、at-least-once HTTP handoff、静态 Analyzer Flow 均是本 reference 的可执行边界。

## 6. 维护约定

- 新增或重命名主测试时，先更新本目录对应 PAY-AC 行，再更新 `AcceptanceGuideContractTests` 的精确方法引用（若 Guide 引用受影响）。
- 只在测试真正证明了场景后才将目录/traceability 标成 `verified`；planned 场景必须保留“证明了什么/没有证明什么”。
- 不复制整条主链来“凑覆盖”；优先在现有主测试中补充按维度组织的断言，或新增窄范围 focused test。
- 任何生产代码修改都必须在交付说明中单列原因；本次整理不修改生产代码。

## 7. 后续会话如何用测试做教学（本会话不执行逐场景讲解）

后续阅读式教学的启动入口是[教学上下文](payment-teaching-context.md)，模块顺序见[教学大纲](payment-teaching-syllabus.md)。本目录只负责提供 PAY-AC → PAY-BR → 测试证据和边界映射，不规定学习者必须运行命令。

教学时先由教师给出业务方向、文件路线和观察重点，再让学习者阅读主测试与辅助测试；是否运行测试由学习者自行决定。阅读完成后，教师围绕刚看过的证据进行针对性提问，并明确哪些结论来自源码、已有结果或本次运行。
