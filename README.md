# cap4k-reference-payment

`cap4k-reference-payment` 是一个 **requirements-first、reference-first** 的支付领域引用项目。长期业务范围覆盖支付、退款、日终对账与商户结算；当前只实现首个可运行切片 **B1：创建支付 → 发起支付尝试 → 渠道结果回调 → 查询支付**。

## 当前状态

B1 已进入 Build，并已具备：

- `domain`、`application`、`adapter`、`start` 四个业务层模块，以及独立 dependency-leaf `contract` 模块；
- Payment 聚合根、PaymentAttempt 与 PaymentNotificationReceipt 两级强引用子实体、MerchantChannelConfiguration 聚合；
- UUID7 Strong ID、Money Value Object、非持久化 `ChannelResultRecordingOutcome` Domain Value Object；
- 由 enum manifest 生成并通过整数列类型绑定的支付、尝试、渠道结果和渠道配置状态；
- cap4k Command、Query、Capability、Endpoint、Repository/UoW；
- H2/JPA 真实持久化与 Spring Boot HTTP 应用；
- domain tests 与完整 HTTP/JPA 集成测试；
- cap4k plan/generation、Analyzer Flow/Drawing Board 和 Agent Snapshot 证据。

退款、对账、结算、可靠异步、Integration Event transport、only-engine、Jimmer projection 和 published-coordinate cold start 均未在 B1 中实现，不应从当前代码推断为已支持。

## 业务真源

- `docs/requirements/business/`：框架无关业务规则；
- `docs/requirements/acceptance/`：可复用业务验收场景；
- `docs/requirements/projection/cap4k-current.md`：业务需求到当前 cap4k 能力的计划映射；
- `docs/requirements/traceability.yaml`：需求、投影、B1 实现与证据的机器可读追踪关系。

项目不建立 cap4k 版本目录、兼容层或历史投影副本。文档只说明当前状态，历史变化由 Git 保存。

## B1 业务链

```text
POST /api/payments
  -> CreatePayment Command
  -> Payment(PENDING)

POST /api/payments/{paymentId}/attempts
  -> StartPaymentAttempt Command
  -> StartChannelPayment Capability
  -> PaymentAttempt(PROCESSING)
  -> Payment(PROCESSING)

POST /api/channel/payment-results
  -> ConfirmPaymentResult Command
  -> VerifyPaymentResult Capability
  -> 按 notification identity 去重、验证与冲突裁决
  -> 持久化每个通知的接收次数、裁决与拒绝/冲突摘要
  -> Payment(SUCCEEDED 或保持原状态)

GET /api/payments/{paymentId}
  -> GetPayment Query
  -> 持久化 Payment、全部 attempt 与 notification receipt 明细
```

Fake Channel Gateway 的 `ACCEPTED` 只表示渠道受理，不代表支付成功。只有可信且匹配的最终成功结果才能形成一次成功事实与一次商户成功通知意图；同一成功通知重复三次仍只形成一次事实，成功后的失败结果不会回退状态。Gateway 异常会把尝试持久化为失败并留下诊断；两事务并发回调由乐观版本冲突测试证明不会静默覆盖。

## 技术基线

- JDK 17
- Kotlin 2.2.20
- Spring Boot 3.5.6
- 默认声明 cap4k 2.0.1
- base package：`com.only4.cap4k.reference.payment`

当前 B1 使用了尚未随 2.0.1 发布的 mainline Pipeline DSL/Analyzer 合同，因此本轮验收通过显式 Composite Build 对 cap4k mainline 提交 `dcafcc43928aa47a0613bb839ba5f6010efa3414` 执行。该基线包含 Mermaid quoted-label 修复（PR #211）、Agent Snapshot plan ownership 修复（PR #212）与 checked-in extensible enum 修复（PR #214）。本地解析顺序为非空 Gradle property `cap4k.local.path`、非空环境变量 `CAP4K_LOCAL_PATH`、正式版 `2.0.1`；仓库默认仍只声明 Gradle Plugin Portal 与 Maven Central，且不提交 sibling path、绝对路径、`mavenLocal()`、Snapshot、私服或机器本地 Gradle 配置。published-coordinate cold start 属于后续 B6。

## 本地运行

PowerShell：

推荐把以下属性写入用户级 Gradle 配置（实际 `GRADLE_USER_HOME/gradle.properties`，不提交到本仓库）：

```properties
cap4k.local.path=C:/path/to/cap4k
```

单次命令也可以显式传入同一 Gradle property：

```powershell
.\gradlew.bat build -Pcap4k.local.path='C:/path/to/cap4k' --no-daemon --console=plain
```

环境变量仅作为后备方式：

```powershell
$env:CAP4K_LOCAL_PATH = 'C:/path/to/cap4k'
```

然后执行：

```powershell
.\gradlew.bat cap4kAgentSnapshot --no-daemon --console=plain
.\gradlew.bat cap4kPlan --no-daemon --console=plain
.\gradlew.bat cap4kGenerate cap4kGenerateSources --no-daemon --console=plain
.\gradlew.bat build --no-daemon --console=plain
.\gradlew.bat cap4kAnalysisPlan cap4kAnalysisGenerate --no-daemon --console=plain
.\gradlew.bat cap4kAgentSnapshot --no-daemon --console=plain
```

首次联调 Analyzer compiler 或本机 cap4k compiler 代码刚发生变化时，可先在 cap4k 仓库执行：

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:jar --no-daemon --console=plain
```

然后重新执行本项目的 `contract/domain/application/adapter` 编译与 analysis 任务。

## 证据位置

可复现的主要证据：

- ordinary plan：`build/cap4k/plan.json`（41 items）；
- 独立 Endpoint contracts：`contract/src/main/kotlin/com/only4/cap4k/reference/payment/contract/endpoints/payment/api/`；
- build-owned generated source：`domain` 与 `adapter` 的 `build/generated/cap4k/main/kotlin`；
- domain tests：`domain/src/test/kotlin/com/only4/cap4k/reference/payment/domain/`（7 tests）；
- HTTP/JPA integration test：`start/src/test/kotlin/com/only4/cap4k/reference/payment/PaymentReferenceApplicationTests.kt`（5 tests）；
- Analyzer plan：`build/cap4k/analysis-plan.json`（12 items）；
- Actor flows：`flows/`；
- Drawing Board：`design/drawing_board_*.json`；
- Agent Snapshot：`build/cap4k/agent/manifest.json`、`ownership.json`、`analysis.json` 与 `diagnostics.json`。

当前 Agent Snapshot 状态为 `partial`，唯一原因是 live DB source 的 freshness 按现行合同为 `UNKNOWN`；ownership 保留 41 个真实 plan items，Analyzer 状态为 `ok`，diagnostics 为 0。Analyzer 生成 3 个 endpoint-http Actor-to-Command flow、Command/Query Drawing Board 与独立 Aggregate Structure；三份 Mermaid 已使用 quoted label，并通过 Mermaid 11.16.1 parser smoke。默认 Flow 不把 CommandHandler、聚合运行时状态推进与 Query 串成同一条因果链，项目不伪造这种更深证据。

## 后续切片

1. B2：退款与部分退款；
2. B3：日终对账与差异处置；
3. B4：商户结算；
4. B5：可靠异步、消息 transport 与 only-engine addon gate；
5. B6：公开坐标 cold start、完整 consumer E2E 与发布/宣传证据。
