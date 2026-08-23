# Payment Reference 验收体验与中文可理解性完整规格

## 1. 目标

`cap4k-reference-payment` 必须为项目验收者提供一条从业务场景到可执行证据的直接路径，并让核心业务代码与用户可见错误在不改变既有支付业务合同的前提下可理解、可观察、可复核。

本 capability 只改进现有已实现能力的验收导航、代码解释和错误展示。支付、退款、对账、商户结算、Integration Event、Pipeline、Analyzer 与 AgentFacts 的业务和工程合同继续由 canonical `payment-reference-build` 规格定义。

## 2. 基本边界

- `docs/requirements/acceptance/payment-scenarios.md` 是业务断言真源，继续只描述可观察的 Given/When/Then。
- `docs/requirements/traceability.yaml` 是场景状态、projection 与 evidence 的机器真源。
- `docs/requirements/acceptance/payment-acceptance-guide.md` 是人工验收主入口，负责 How，不反向定义业务行为或实现状态。
- 当前 53 个场景中，49 个 `verified`，`PAY-AC-080/081/084/086` 保持 `planned/not-built`。
- 本 capability 不新增支付业务能力，不升级 planned 场景，不改变已有稳定错误码、HTTP status、enum numeric value、event name、聚合边界或持久化语义。

## 3. 验收导航

### 3.1 入口与状态

`payment-scenarios.md` 顶部必须提供：

- 进入人工验收指南的链接；
- `verified`、`planned/not-built` 的含义；
- `traceability.yaml` 为状态真源的说明；
- 业务断言、人工 Guide、机器 evidence 三者的职责分工。

requirements README 与项目 README 必须能够一跳进入验收指南。所有文档链接必须是仓库相对路径，不提交本机绝对路径。

Guide 首屏必须明确：

- 总场景数 53；
- verified 49；
- planned 4；
- `PAY-AC-080/081/084/086` 不进入已实现能力验收；
- acceptance 状态与更宽 projection closure 状态不是同一层级；
- Fake Provider、H2、普通 Scheduler、测试 Receiver、at-least-once transport 和 Analyzer 静态入口 Flow 的边界。

### 3.2 支付场景族

Guide 必须覆盖 `PAY-AC-001..017`，并提供以下共享与逐场景导航：

- lifecycle 与对应 `PAY-BR-*`；
- Design 输入：schema、enum、value object、Command、Endpoint、Capability、Aggregate Structure；
- Payment、PaymentAttempt、PaymentNotificationReceipt、PaymentReviewCase、PaymentReviewDecision owned graph；
- Payment Behavior 及 Create/Start/Confirm/Expire/Adjudicate 应用路径；
- HTTP callback、查询、PaymentExpiryScheduler 与 Analyzer Flow；
- 精确到测试方法的 domain/application/HTTP/JPA/并发证据；
- HTTP status/code/message、Payment/Attempt 状态、receipt receive count/disposition、review/decision、fee snapshot、merchant-order success identity、notification intent 与结算资格观察点。

对 `PAY-AC-003/006/011/012/017` 必须同时列出稳定错误码和中文 message 的验收方式。

### 3.3 退款场景族

Guide 必须覆盖 `PAY-AC-020..029`，并提供：

- Payment 作为退款预算权威以及 Refund 独立聚合边界；
- Refund、RefundAttempt、RefundNotificationReceipt owned graph；
- Create/Confirm/ReviewPending、HTTP/Time 入口和相关 Capability/Flow；
- 精确测试方法；
- reservedRefundAmount、successfulRefundAmount、refundableAmount、attempt/receipt、退款期限、预算释放/转换、跨聚合 UoW 与真实并发观察点。

### 3.4 对账场景族

Guide 必须覆盖 `PAY-AC-040..047/082/085/087`，并提供：

- ReconciliationBatch、Run、StatementRevision、Item、Disposition、ConfirmationFact owned graph；
- statement/platform facts、匹配优先级、revision 幂等、currentEffectiveRun、append-only disposition/confirmation 和 completion blocker；
- Daily Scheduler、manual rerun、difference disposition、Integration Event listener 与 Pull Capability；
- Analyzer Time/HTTP/Integration Event Flow 和精确测试方法；
- batch/run/item、双方快照、difference kind、revision、blocking reason、disposition、confirmation、Asia/Shanghai 业务日观察点。

Guide 必须明确 `ChannelStatementAvailableIntegrationEvent` 只表达账单可获取，完整账单仍以 `PullChannelStatement` 为权威；Push、Pull、Scheduler 与 Rerun 共享同一收敛边界。

### 3.5 商户结算场景族

Guide 必须覆盖 `PAY-AC-060..068/014/083/085/088`，并提供：

- MerchantSettlement、SettlementLine、SettlementExecutionAttempt、SettlementResultReceipt owned graph；
- candidate eligibility、transaction-level exclusion、fee snapshot、gross/fee/net 汇总、composition freeze、execution/result/review、replacement/effective ownership；
- HTTP、Daily/UnknownReview Scheduler、Domain Event 与 outbound Integration Event 路径；
- Analyzer Flow 与精确测试方法；
- eligible/excluded counts、lines/source identities、attempt/receipt、negative/zero、unknown 禁止重付、completion fact、reliable event record、delivery attempt 和稳定 payload identity 观察点。

### 3.6 横切场景与 planned 边界

- `PAY-AC-082/083/085/087/088` 必须链接到真实可执行证据。
- `PAY-AC-080/081/084/086` 只能展示 planned/not-built gap、当前已存在但不足以升级场景的局部机制，以及不得误宣称的能力。
- 现有 Settlement Integration Event HTTP receiver 不得被描述为生产商户通知服务。
- 领域 operator fixture 不得被描述为完整认证、授权或租户隔离。
- 渠道配置聚合存在不得被描述为已完成退役渠道验收。

### 3.7 执行环境、顺序和复位

Guide 必须提供：

- Java 17、Composite Build、构建、测试、应用启动和 Analyzer/traceability 检查命令；
- H2 内存库、application lifecycle 与 fixture 边界；
- 当前没有公共 reset API 的事实；重复人工验收使用重启应用或全新业务 identity；
- 推荐主链冒烟顺序，再执行异常、并发、迟到、矛盾和审计场景；
- 不能通过公共 HTTP 准备的 statement/provider fixture，优先指向现有 Spring/H2/JPA 集成测试，不伪造手工 API；
- focused tests 只用于定位，最终验收必须包含完整回归。

## 4. 中文解释性代码注释

### 4.1 注释原则

- 注释使用中文，代码符号、枚举、错误码、协议名和 identity 保持原文。
- 注释解释业务目的、关键不变量、状态/版本收敛、幂等与并发、证据为何追加而不覆盖、结算资格和事件副作用。
- 不对 getter、简单赋值、显然的 `require`、数据搬运和每一行语句做翻译式注释。
- 注释不得扩大能力边界：普通 Scheduler 不是持久化调度，at-least-once 不是 exactly-once，Analyzer Flow 不是运行态端到端 stitching。

### 4.2 聚合行为覆盖

至少覆盖：

- Payment：start attempt、expiry、channel result 总入口及四类当前状态分流、review adjudication、unknown 自动收敛、fee snapshot、refund reserve/release/convert；
- Refund：创建、渠道请求、结果幂等/冲突、失败释放、成功预算转换、unknown/review 与成功不可回退；
- ReconciliationBatch：fetch failure、run/revision append、effective run、difference classify、disposition/confirmation、completion/settlement blocker 重算；
- MerchantSettlement：composition 确认、execution attempt、result receipt/冲突、unknown adjudication、void/replacement、effective ownership 和 settled fact once。

### 4.3 应用编排覆盖

复杂 Handler 必须通过方法级 KDoc、阶段注释或不改变行为的私有函数提取，使以下路径可以按业务阶段阅读：

- payment create/start/result/expiry/review；
- refund create/result/review；
- reconciliation daily/rerun/available-event/disposition；
- merchant settlement prepare/confirm/start/result/review/void；
- Domain Event 到 reliable Integration Event 发布和 inbound event 到权威 Pull 的收敛边界。

## 5. 错误和审计文本合同

### 5.1 稳定 code、中文 message、安全 details

- 所有既有稳定英文 `code` 和对应 HTTP status 保持不变。
- 用户可见 HTTP `message` 必须是简洁中文，使用一致业务术语。
- 错误传播必须显式携带 code；禁止从 `Throwable.message` 或字符串前缀解析 `REVIEW_*` 等 code。
- `PaymentErrorResponse` 保留 `status/code/message`，允许增加可选 `details`。
- `details` 只允许安全、结构化、白名单字段，例如业务 ID、字段名、期望/实际状态；不得自动包含 cause、rawMessage、SQL、SQLState、异常类名或堆栈。
- 未知 runtime、数据库、Hibernate、唯一约束和 provider 异常返回稳定 code 与固定中文安全消息，原始 cause 只进入日志。

### 5.2 新写入审计文本

change 后由本地代码新生成并经 Query/GET 暴露的以下文本必须中文化：

- payment/refund notification rejection/conflict summary；
- payment review summary、system decision reason、settlement eligibility blocker；
- reconciliation fetch failure、statement incomplete、unresolved difference/blocking reason；
- settlement candidate blocker、result conflict/review/failure summary。

以下内容不得翻译或改写：

- stable code、enum/type/event name；
- identity discriminator、matching basis、failure code；
- channel/provider raw result/code；
- 用户输入的 reason、evidence、follow-up；
- 历史数据库中已经存在的英文 summary。

### 5.3 一致性与安全

- 新的业务错误类型不得让 domain 依赖 adapter/HTTP。
- application/adapter 在发布边界将显式 code、中文 message 与 safe details 投影为 HTTP response。
- 同一错误在 domain/application/HTTP 测试中使用同一稳定 code，不通过 message 相等维持机器合同。
- provider 原始诊断如果需要保留，应进入日志或专门 raw diagnostic 字段；对外 message 使用受控中文摘要。

## 6. 测试与守卫

必须验证：

1. 53 个场景全部出现在 Guide 导航中，状态与 traceability 一致，四个 planned 场景未升级。
2. scenarios、requirements README、project README 到 Guide 的链接可解析，Guide 不含本机绝对路径。
3. Guide 的 Design、代码、Flow 和测试路径存在；精确测试方法引用可由静态检查定位。
4. 现有 400/404/409 和业务错误 code/status 保持不变，message 为中文。
5. `REVIEW_UNAUTHORIZED`、`REVIEW_NOT_FOUND`、`REVIEW_DECISION_IDEMPOTENCY_CONFLICT`、`REVIEW_DECISION_NOT_ALLOWED` 不再依赖 exception message 解析。
6. 模拟数据库/provider/未知英文异常时，HTTP response 不包含原始文本、SQL、类名或堆栈。
7. 新写入的 rejection/conflict/review/blocking/failure summary 通过 Query/GET round-trip 后为中文，机器 token 和用户输入保持原值。
8. 用户可见主源码字符串静态扫描不新增纯英文自然语言；code、enum、identity、协议、raw diagnostic 使用显式白名单。
9. 复杂 Behavior/Command 注释覆盖本规格 4.2/4.3 的关键边界，不出现逐行复述或能力夸大。
10. domain、application、adapter、start focused tests 通过；最终 `clean build` 全通过，0 failure、0 skip。
11. traceability contract、Analyzer plan/generate 和既有 capability/evidence guards 通过；Generator ownership 与 checked-in source 不漂移。

## 7. 非目标

- 不实现 `PAY-AC-080/081/084/086`。
- 不改变任何现有业务场景、状态机、金额公式、聚合边界、Integration Event 或 transport 语义。
- 不新增生产通知、租户隔离、完整 RBAC、真实外部协议、broker、generic Inbox/Outbox、persistent scheduler、跨实例 exactly-once、周结或负净额追偿。
- 不新增 UI、reset/fixture HTTP API 或历史数据迁移。
- 不把原始外部诊断强制翻译为中文，也不把机器字段改成展示文本。
