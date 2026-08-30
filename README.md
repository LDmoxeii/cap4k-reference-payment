# cap4k-reference-payment

`cap4k-reference-payment` 是一个 **requirements-first、reference-first** 的支付领域引用项目。长期业务范围覆盖支付、退款、日终对账、商户结算与最小 Integration Event 边界；当前已完成 B1-B5 五个可运行切片、GitHub #4 Payment timeout/late-result/conflict-review lifecycle，以及 GitHub #8 accepted-lineage final composition/evidence closure：

- **B1**：创建支付 → 发起支付尝试 → 渠道结果回调 → 查询支付；
- **B2**：全额/部分退款 → 渠道退款结果 → 超期复核 → 查询退款；
- **B3**：日终账单拉取 → 支付/退款事实核对 → 差异处置/确认 → 查询与重跑；
- **B4**：商户日结准备 → 确认冻结 → 同步 Fake 资金划拨 → 结果裁决/未知复核 → 查询与受控作废；
- **B5**：渠道账单可用 Integration Event → 权威账单拉取/版本收敛；商户结算完成事实 → JPA reliable-event → HTTP 投递与失败重试；
- **#4 lifecycle**：Payment 业务到期 → `CLOSED` 或 `RESULT_UNKNOWN` review；迟到/重复/冲突回执 append-preserving；授权 review；merchant-order success 唯一约束；review eligibility 传播到 B3/B4；
- **#8 composition**：在同一 accepted `origin/main` lineage 上，以一条真实 H2/JPA 业务轨迹贯通 Payment → partial Refund → authoritative Reconciliation → MerchantSettlement → 唯一 durable completion event，并用机器可读 guard 锁定 traceability 与 deferred boundaries。

## 当前状态

当前项目具备：

- `domain`、`application`、`adapter`、`start` 四个业务层模块，以及独立 dependency-leaf `contract` 模块；
- Payment、Refund、ReconciliationBatch、MerchantSettlement 与 MerchantChannelConfiguration 聚合；Payment owned graph 进一步包含 append-only PaymentReviewCase/Decision；
- PaymentAttempt、RefundAttempt、NotificationReceipt、ReconciliationRun、ReconciliationItem、Disposition、ConfirmationFact、SettlementLine、SettlementExecutionAttempt、SettlementResultReceipt 等 owned graph；
- UUID7 Strong ID、Money 及多个非持久化 Domain Value Object；
- 由 enum manifest 生成、可承载领域逻辑并通过整数列绑定的 checked-in 业务枚举；
- cap4k Command、Query、Capability、Endpoint、Repository/UoW；
- 每个 Endpoint Handler 一类一文件并默认使用静态 Mediator；HTTP binding 始终手写；
- H2/JPA 真实持久化、乐观锁、复合唯一约束、回滚与 Spring Boot HTTP 集成测试；
- Payment `expiresAt` 业务生命周期裁决、RESULT_UNKNOWN、迟到成功/双成功/成功后失败冲突保全、授权 review、稳定 merchant-success notification intent，以及 merchant + merchantOrderNumber accepted-success 唯一约束；
- B3 保存 unresolved Payment review identity/type/reason snapshot，B4 在准备候选时重新读取当前 review eligibility；兼容 `settlementBlocked` 布尔摘要不是资格真源；
- B4 支付成功时原子冻结手续费快照、日结候选投影、结算确认冻结、同步 Fake Transfer、回调裁决与未知结果复核；
- B5 两份稳定 Integration Event contract、canonical HTTP receiver、入站 subscriber、JPA-backed reliable-event 记录、HTTP handoff，以及非 2xx/response-timeout 后的 durable retry/recovery；
- ordinary plan/generation、Analyzer Flow/Drawing Board/Aggregate Structure 和 Agent Snapshot 证据；
- accepted-lineage composition test 把 Payment/Refund 身份、Reconciliation batch/run/item、Settlement line、CNY 78.00 净额与唯一 `MerchantSettlementCompleted` durable record 串成同一数据库证据链；`TraceabilityContractTests` 同时拒绝重复 YAML key、悬空引用、伪 verified evidence、非便携路径和 deferred capability 的误晋级。

B5 只验证两条最小 HTTP Integration Event 路径：入站账单可用信号仍通过 `PullChannelStatement` 获取权威正文，出站结算完成事实通过 cap4k reliable Event/JPA 与 HTTP transport 投递。#4 的 timeout 是 Payment `expiresAt` 业务生命周期超时，与 B5 HTTP publisher response timeout 不同；`PaymentExpiryScheduler` 只是向应用层发送 Command 的 ordinary scheduler。可靠 Command、broker transport、generic Inbox、持久化业务 scheduler/lease、跨实例 exactly-once、only-engine、生产商户通知、B6 published-coordinate cold start、Jimmer projection、真实银行/清算网络与生产级资金划拨仍未实现。普通 `@Scheduled` 与同步 Fake Provider 只证明 reference 闭环，不应推断为生产资金或全局可靠调度能力。

## 业务真源

- `docs/requirements/business/`：框架无关业务规则；
- `docs/requirements/acceptance/payment-scenarios.md`：稳定的业务验收断言；
- `docs/requirements/acceptance/payment-acceptance-guide.md`：面向人工验收的执行顺序、架构导航、精确测试方法与观察点；
- `docs/requirements/acceptance/payment-test-evidence-catalog.md`：自动化测试到 PAY-AC/PAY-BR 的证据目录（本次前置整理，不替代人工教学）；
- `docs/requirements/projection/cap4k-current.md`：业务需求到当前 cap4k 能力的 current-only 投影；
- `docs/requirements/traceability.yaml`：需求、投影、实现切片与证据的机器可读追踪关系。

项目不建立 cap4k 版本目录、兼容层或历史投影副本。文档只说明当前状态，历史变化由 Git 保存。

## 业务链

### B1 支付

```text
POST /api/payments
  -> CreatePayment Command
  -> Payment(PENDING)

POST /api/payments/{paymentId}/attempts
  -> StartPaymentAttempt Command
  -> StartChannelPayment Capability
  -> PaymentAttempt(PROCESSING)

POST /api/channel/payment-results
  -> ConfirmPaymentResult Command
  -> VerifyPaymentResult Capability
  -> 通知去重、验证、冲突裁决和成功事实

GET /api/payments/{paymentId}
  -> GetPayment Query
```

渠道 `ACCEPTED` 只表示受理，不代表支付成功。只有可信且匹配的最终成功结果才能形成成功事实；重复或矛盾通知不会回退终态。新的 idempotency key 不能绕过 merchant + merchantOrderNumber 已成功支付约束。

#### Payment timeout 与 conflict review

```text
@Scheduled
  -> ExpirePayments Command
  -> 到期且无 PROCESSING/RESULT_UNKNOWN attempt: Payment(CLOSED)
  -> 到期且有未决 attempt: Payment/Attempt(RESULT_UNKNOWN) + blocking review

POST /api/channel/payment-results
  -> 追加 receipt/attempt evidence
  -> late success / multiple success / failure-or-unknown after success: review + settlement block

POST /api/payments/{paymentId}/reviews/{reviewId}/decisions
  -> AdjudicatePaymentReview Command
  -> 追加授权 decision，不删除或覆盖旧证据
```

RESULT_UNKNOWN 的可信最终回执可以幂等收敛；CLOSED/FAILED 后的可信成功在授权前保持原终态并持有通知意图；SUCCEEDED 后的失败或未知结果不得回退成功事实。未解决 review 会使 B3 matched item 仍不可结算，并使 B4 即使面对旧 reconciliation run 或错误的 false 布尔摘要也重新排除候选。

### B2 退款

```text
POST /api/refunds
  -> CreateRefund Command
  -> Payment 退款预算占用 + Refund/RefundAttempt 原子保存
  -> StartChannelRefund Capability

POST /api/channel/refund-results
  -> ConfirmRefundResult Command
  -> VerifyRefundResult Capability
  -> 重复、矛盾、未知与最终结果裁决

@Scheduled
  -> ReviewPendingRefunds Command

GET /api/refunds/{refundId}
  -> GetRefund Query
```

支持全额退款、多次部分退款、防超退、失败释放预算、未知结果保留预算、真实 HTTP 并发 409、跨聚合同 UoW 回滚和成功终态不可回退。

### B3 日终对账

```text
@Scheduled (Asia/Shanghai business date)
  -> RunDailyReconciliation Command
  -> PullChannelStatement Capability
  -> LoadPlatformReconciliationFacts Capability
  -> ReconciliationBatch / ReconciliationRun / ReconciliationItem

POST /api/reconciliation-batches/{batchId}/reruns
  -> RerunReconciliationBatch Command

POST /api/reconciliation-items/{itemId}/dispositions
  -> DisposeReconciliationDifference Command
  -> 追加 Disposition / ConfirmationFact

GET /api/reconciliation-batches/{batchId}
  -> GetReconciliationBatch Query
```

B3 保留平台和渠道双方原始快照，支持完全匹配、平台单边、渠道单边、金额/币种/状态差异、重复渠道记录与无法关联。相同 statement revision 幂等，新 revision 追加历史；未决或不完整账单阻断完成。授权处置和确认事实只追加，不改写 Payment、Refund、旧 run 或最初差异。

### B4 商户结算

```text
@Scheduled (Asia/Shanghai previous business day)
  -> RunDailyMerchantSettlement Command
  -> PrepareMerchantSettlement Command
  -> LoadMerchantSettlementCandidates Capability
  -> MerchantSettlement / SettlementLine

POST /api/merchant-settlements
  -> PrepareMerchantSettlement Command

POST /api/merchant-settlements/{settlementId}/confirmations
  -> ConfirmMerchantSettlement Command
  -> 冻结组成与净额

POST /api/merchant-settlements/{settlementId}/executions
  -> StartMerchantSettlementExecution Command
  -> StartSettlementTransfer Capability

POST /api/channel/settlement-results
  -> ConfirmMerchantSettlementResult Command
  -> VerifySettlementResult Capability
  -> 去重、冲突、未知与最终结果裁决

@Scheduled
  -> ReviewUnknownMerchantSettlements Command

POST /api/merchant-settlements/{settlementId}/voids
  -> VoidMerchantSettlement Command
  -> 受控 replacement 链

GET /api/merchant-settlements/{settlementId}
  -> GetMerchantSettlement Query
```

B4 以 `Asia/Shanghai` 半开日结区间消费当前 effective reconciliation run 的已确认事实，并在交易粒度排除未决项。支付首次成功时原子冻结手续费规则与结果快照；reference 配置为 200 bps、固定费 0、`HALF_UP`，例如 127.00 的手续费为 2.54、净额为 124.46。确认后组成冻结；正净额才允许发起划拨，负净额进入人工处理；`ACCEPTED` 不等于成功，`RESULT_UNKNOWN` 禁止重付，重复通知只形成一次 settled fact，成功后的迟到失败只记冲突而不回退。

### B5 最小可靠 HTTP Integration Event

```text
POST /cap4k/integration-events
  -> ChannelStatementAvailableIntegrationEvent
  -> thin @EventListener subscriber
  -> ProcessAvailableChannelStatement Command
  -> PullChannelStatement Capability（权威正文）
  -> 同一 ReconciliationBatch / revision 幂等边界

MerchantSettlement 首次 accepted terminal success
  -> MerchantSettlementCompletedDomainEvent
  -> application subscriber
  -> Mediator.events.enqueue(MerchantSettlementCompletedIntegrationEvent)
  -> __event durable record
  -> HTTP handoff / non-2xx or response-timeout retry/recovery
```

入站 Push 与 Pull 不是二选一：Integration Event 只声明某个 statement identity/revision 已可用，`PullChannelStatement` 仍提供权威完整账单；日终 scheduler 和人工 rerun 继续存在，并与事件入口汇合到同一领域/数据库幂等模型。入站重复投递依赖 statement identity/revision 与唯一约束收敛，不声称 generic Inbox。出站只在结算首次成功事实形成时注册一次可靠事件；测试覆盖业务与事件记录共同回滚、HTTP 503 与真实 response timeout 两类首投失败、同一 event UUID/type/payload 的 durable retry，以及 receiver 恢复后的最终 2xx handoff。

## 技术基线

- JDK 17
- Kotlin 2.2.20
- Spring Boot 3.5.6
- 默认声明 cap4k 2.0.1
- base package：`com.only4.cap4k.reference.payment`

当前 B1-B5、#4 与 #8 composition verification 使用尚未随 2.0.1 发布的 mainline Pipeline DSL/Analyzer 与 reliable Integration Event 合同，因此通过显式 Composite Build 对 cap4k mainline 提交 `6575866043ad34008843d7245c49563e15b38b54` 验证。本地解析顺序为非空 Gradle property `cap4k.local.path`、非空环境变量 `CAP4K_LOCAL_PATH`、正式版 `2.0.1`；仓库不提交 sibling path、绝对路径、`mavenLocal()`、Snapshot、私服或机器本地 Gradle 配置。published-coordinate cold start 仍受 B6/release gate 约束，#8 不以本地 Composite 冒充公开坐标可用性。

## 本地运行

推荐把以下属性写入用户级 Gradle 配置（实际 `GRADLE_USER_HOME/gradle.properties`，不提交到本仓库）：

```properties
cap4k.local.path=C:/path/to/cap4k
```

单次命令也可以显式传入：

```powershell
.\gradlew.bat build -Pcap4k.local.path='C:/path/to/cap4k' --no-daemon --console=plain
```

环境变量 `CAP4K_LOCAL_PATH` 仅作为后备方式。随后执行：

```powershell
.\gradlew.bat cap4kAgentSnapshot --no-daemon --console=plain
.\gradlew.bat cap4kPlan --no-daemon --console=plain
.\gradlew.bat cap4kGenerate cap4kGenerateSources --no-daemon --console=plain
.\gradlew.bat clean build --no-daemon --console=plain
.\gradlew.bat cap4kAnalysisPlan cap4kAnalysisGenerate --no-daemon --console=plain
.\gradlew.bat cap4kAgentSnapshot --no-daemon --console=plain
```

## 当前证据

- accepted lineage：`origin/main=e702e725674c4ab1271441cf1ed011bad3b75021` 同时包含 B1 `6a40c5da`、B2 `43a59828`、B3 `8750a4b7`、B4 `4e347650`、B5 `3fd59cda` 与 #4 `e702e725`；
- clean candidate checkout：提交 `75006a9df642af696b2904a0fb5deb5e222fbbef` 在初始 0 个 build 目录的独立 worktree 中完成 `clean build bootJar`，111 tests / 28 suites / 0 failures / 0 errors / 0 skips（#8 composition 基线为 102/24，#4 accepted 基线为 100/23，进入 #4 前为 84/22）；
- composition trace：新增 1 个 Spring/H2/JPA 端到端测试，把 100.00 Payment、20.00 partial Refund、同一 authoritative reconciliation run、两条 settlement line、2.00 fee、78.00 net amount 和恰好 1 条 durable `MerchantSettlementCompleted` record 关联起来；
- ordinary plan：`build/cap4k/plan.json`，197 items（137 checked-in `SKIP`、60 generated `OVERWRITE`）；同一 clean checkout 两轮 plan SHA-256 均为 `8f8104352238e9759f64b0eeeab9a1693e9205ec706df7280a791c0e535b36c8`，60 个 generated files 的便携树 SHA-256 均为 `d7da82618706355eb89803f0cfa1a1f7cf7f529d8927f21c7a2829c30bb0b388`；plan 含 checkout locator，因此只用同一检出内的重复结果证明确定性；
- B5 HTTP/H2/JPA：`ReconciliationReferenceApplicationTests.kt` 覆盖入站重投与 revision 收敛，`MerchantSettlementReferenceApplicationTests.kt` 通过测试期可控 JDK `HttpServer` fake receiver 分别覆盖 HTTP 503 与真实 response timeout 后的 durable retry/recovery，并证明 event UUID、event type 与 payload 保持稳定；该接收器只证明 transport 行为，不是生产商户通知服务；
- Analyzer：46 outputs/items、19 条独立入口（13 个 Endpoint HTTP Actor roots、5 个 Time roots、1 个 Integration Event root）；同一 clean checkout 两轮 analysis plan SHA-256 均为 `21a51e47471936d376a5c2aaa1f102b91fa2f419fe7a862294978dec6a6a530a`，排除 machine-local `flows/index.json` 后的 45 个 Flow/Mermaid/Drawing Board 文件树 SHA-256 均为 `4c701b84771e0d233941b334bf5dab5c8cde84acbc0a49974109361c83c9e2e9`；
- Mermaid：19 份 `.mmd` 均使用 quoted label，并全部通过实际 SVG renderer smoke；新增 Payment expiry Time root 与 review adjudication HTTP Actor root，不伪造 durable scheduling 或跨入口 stitching；
- Spring Boot：Java 17 `start.jar --server.port=0` 在独立 clean checkout 中成功启动后受控停止；仓库没有 `.github/workflows/**`，因此不虚构远端 CI 证据；
- traceability guard：1 个 `TraceabilityContractTests` / 0 failures，锁定 ID/status/reference/source/evidence/build-slice closure 与明确 deferred boundaries；
- Agent Snapshot：snapshot `fd1fb0cfd3b094c9ce0b15177ebb2c8de997e89e5769a58d23213a4052f5998c`；`partial` 仅因为 live DB source freshness 为 `UNKNOWN`，ownership 保留 197 个 plan items，analysis 为 `ok`、46 个 available outputs，diagnostics 为 0 且无 INVALID/error/plan-evidence-invalid。

`flows/index.json` 当前仍包含本机 IR input locator，只作为本地可再生产物，不作为可移植提交证据；上游稳定 identity 修复由 `cap4k#215` 跟踪。默认 Flow 不把隐藏的 CommandHandler、聚合运行时状态推进和 Query 串成跨入口流程，项目不伪造这种证据。

## 后续切片

1. B5 后续扩展：reliable Command、broker transport、generic Inbox、持久化业务 scheduler/lease、跨实例 exactly-once、only-engine 与生产商户通知；
2. B6：公开坐标 cold start、完整 consumer E2E 与发布/宣传证据。
