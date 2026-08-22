# cap4k-reference-payment

`cap4k-reference-payment` 是一个 **requirements-first、reference-first** 的支付领域引用项目。长期业务范围覆盖支付、退款、日终对账与商户结算；当前已完成四个可运行切片：

- **B1**：创建支付 → 发起支付尝试 → 渠道结果回调 → 查询支付；
- **B2**：全额/部分退款 → 渠道退款结果 → 超期复核 → 查询退款；
- **B3**：日终账单拉取 → 支付/退款事实核对 → 差异处置/确认 → 查询与重跑；
- **B4**：商户日结准备 → 确认冻结 → 同步 Fake 资金划拨 → 结果裁决/未知复核 → 查询与受控作废。

## 当前状态

当前项目具备：

- `domain`、`application`、`adapter`、`start` 四个业务层模块，以及独立 dependency-leaf `contract` 模块；
- Payment、Refund、ReconciliationBatch、MerchantSettlement 与 MerchantChannelConfiguration 聚合；
- PaymentAttempt、RefundAttempt、NotificationReceipt、ReconciliationRun、ReconciliationItem、Disposition、ConfirmationFact、SettlementLine、SettlementExecutionAttempt、SettlementResultReceipt 等 owned graph；
- UUID7 Strong ID、Money 及多个非持久化 Domain Value Object；
- 由 enum manifest 生成、可承载领域逻辑并通过整数列绑定的 checked-in 业务枚举；
- cap4k Command、Query、Capability、Endpoint、Repository/UoW；
- 每个 Endpoint Handler 一类一文件并默认使用静态 Mediator；HTTP binding 始终手写；
- H2/JPA 真实持久化、乐观锁、复合唯一约束、回滚与 Spring Boot HTTP 集成测试；
- B4 支付成功时原子冻结手续费快照、日结候选投影、结算确认冻结、同步 Fake Transfer、回调裁决与未知结果复核；
- ordinary plan/generation、Analyzer Flow/Drawing Board/Aggregate Structure 和 Agent Snapshot 证据。

B5 可靠异步/Outbox/Integration Event transport/only-engine gate、B6 published-coordinate cold start、Jimmer projection、真实银行/清算网络与生产级资金划拨均未实现。B4 的普通 `@Scheduled` 与同步 Fake Provider 只证明 reference 闭环，不应推断为可靠调度、跨实例 exactly-once 或生产资金能力。

## 业务真源

- `docs/requirements/business/`：框架无关业务规则；
- `docs/requirements/acceptance/`：可复用业务验收场景；
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

渠道 `ACCEPTED` 只表示受理，不代表支付成功。只有可信且匹配的最终成功结果才能形成成功事实；重复或矛盾通知不会回退终态。

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

## 技术基线

- JDK 17
- Kotlin 2.2.20
- Spring Boot 3.5.6
- 默认声明 cap4k 2.0.1
- base package：`com.only4.cap4k.reference.payment`

当前四个切片使用尚未随 2.0.1 发布的 mainline Pipeline DSL/Analyzer 合同，因此通过显式 Composite Build 对 cap4k mainline 提交 `dcafcc43928aa47a0613bb839ba5f6010efa3414` 验证。本地解析顺序为非空 Gradle property `cap4k.local.path`、非空环境变量 `CAP4K_LOCAL_PATH`、正式版 `2.0.1`；仓库不提交 sibling path、绝对路径、`mavenLocal()`、Snapshot、私服或机器本地 Gradle 配置。published-coordinate cold start 属于 B6。

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

- ordinary plan：`build/cap4k/plan.json`，170 items（116 checked-in `SKIP`、54 generated `OVERWRITE`）；
- 生成确定性：连续 generation 后 checked-in manifest SHA-256 均为 `ca7fe3202aece6524f2472da44137ccaa7601dab25b0d4d677168ae22578b59f`，generated manifest SHA-256 均为 `aa1a35ed59d13ee4f57458d1313dec42203dd98095b9b8aa881dafd0ff6b7ebb`，Kotlin source differences 为 0；
- clean build：77 tests / 21 suites / 0 failures；
- B4 HTTP/H2/JPA：`start/src/test/kotlin/com/only4/cap4k/reference/payment/MerchantSettlementReferenceApplicationTests.kt`；
- Analyzer plan：`build/cap4k/analysis-plan.json`，39 outputs/items；
- Flow：16 条独立入口（12 个 Endpoint HTTP Actor roots、4 个 Time roots），B4 新增 5 个 HTTP Actor roots 与 2 个 Time roots；
- Mermaid：16 份 `.mmd` 均使用 quoted label，并通过静态语法 smoke；
- Drawing Board：`design/drawing_board_*.json`；
- Agent Snapshot：`partial` 仅因为 live DB source freshness 为 `UNKNOWN`；ownership 保留 170 个 plan items，analysis 为 `ok`、39 个 available outputs，diagnostics 为 0 且无 INVALID/error/plan-evidence-invalid。

`flows/index.json` 当前仍包含本机 IR input locator，只作为本地可再生产物，不作为可移植提交证据；上游稳定 identity 修复由 `cap4k#215` 跟踪。默认 Flow 不把隐藏的 CommandHandler、聚合运行时状态推进和 Query 串成跨入口流程，项目不伪造这种证据。

## 后续切片

1. B5：可靠异步、Outbox、消息 transport 与 only-engine addon gate；
2. B6：公开坐标 cold start、完整 consumer E2E 与发布/宣传证据。
