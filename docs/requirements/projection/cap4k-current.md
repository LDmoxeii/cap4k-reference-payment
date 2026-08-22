# 当前 cap4k 实现投影

## 1. 文档性质

本文是支付业务需求到 **当前 cap4k 能力面** 的 current-only 投影，不是业务真源，也不保存历史版本副本。

当前状态：B1 支付、B2 退款、B3 日终对账、B4 商户日结结算、B5 最小 HTTP Integration Event 边界，以及 GitHub #4 Payment timeout/late-result/conflict-review lifecycle 均已有可运行实现与证据。精确状态以 `docs/requirements/traceability.yaml` 为准；宽于已实现切片的投影继续保持 `planned / not-built`，不因局部能力已验证而宣称完整 closure。

- `verified` 条目必须有实际代码、测试、Pipeline、Analyzer 或 AgentFacts 证据；
- `planned / not-built` 条目不代表代码已经生成或 Runtime 已经运行；
- 同步 Fake Provider、普通 `@Scheduled` 和本地 Domain Event 不等于 broker、reliable Command、generic Inbox、持久化业务调度、跨实例 exactly-once 或生产资金网络；B5 只证明两个指定事件的 HTTP Integration Event 路径；
- 本项目不构成对历史 cap4k 版本的兼容承诺。

本项目只维护这一份 current-only 投影，不建立 `<baseline>` 目录、不保留旧投影副本、不实现兼容层。历史由 Git 保存。

## 2. 投影状态词

| 状态 | 含义 |
|---|---|
| `planned` | 已确认目标映射，但完整投影尚未完成 |
| `not-built` | 没有实现、运行结果或可引用证据 |
| `verified` | 对应实现范围已有可复核证据 |

B1-B5 与 #4 已验证具体 acceptance 和 evidence；broker/generic Inbox、生产网络、完整配置维护、持久化/分布式 scheduler 或全局可靠异步 closure 的更宽投影仍保持 `planned / not-built`。

## 3. 领域模型投影

<a id="pay-cp-001"></a>
### PAY-CP-001 Payment 与 PaymentAttempt

- **当前实现**：Payment 聚合根、PaymentAttempt、notification receipt、PaymentReviewCase/Decision owned graph、UUID7 Strong ID、JPA Repository/UoW、幂等创建、渠道尝试、业务到期裁决、回调证据保全、授权 review、乐观锁与 merchant-order accepted-success 唯一约束；B2 增加退款预算，B4 在支付首次成功时原子冻结手续费事实。
- **资格与通知意图**：open blocking review 与授权 decision 共同派生当前 settlement eligibility；`settlementBlocked` 仅是派生摘要。Payment 保存稳定 merchant-success notification intent identity/state，但本项目不发送生产通知。
- **未完成边界**：持久化/分布式 timeout continuation、跨实例 lease/exactly-once、完整商户通知可靠性仍未实现。
- **状态**：已实现范围 `verified`；完整投影 closure 仍为 `planned`。

<a id="pay-cp-002"></a>
### PAY-CP-002 Refund

- **当前实现**：Refund 独立聚合，通过强类型 PaymentId 弱引用 Payment；支持全额/多次部分退款、防超退、预算占用与释放、回调去重/冲突/未知复核、真实并发 409 与跨聚合 UoW 回滚。
- **未完成边界**：真实渠道协议、可靠异步发起与出站通知 transport 未实现。
- **状态**：已实现范围 `verified`；完整投影 closure 仍为 `planned`。

<a id="pay-cp-003"></a>
### PAY-CP-003 ReconciliationBatch 与 ReconciliationItem

- **当前实现**：ReconciliationBatch 聚合根，包含 Run、Item、Disposition、ConfirmationFact 的 owned graph；保存平台/渠道双方快照、statement revision 历史、差异分类与追加式处置。
- **Runtime/Analyzer**：Asia/Shanghai 日终 scheduler、provider pull、重跑/处置 HTTP 入口、账单可用 Integration Event、Time/Actor/Integration Event Flow、H2/JPA 幂等和并发约束均有证据。
- **Push/Pull 边界**：`ChannelStatementAvailable` 只通知 exact statement identity/revision 已可用，权威完整账单仍由 provider pull；重复、较新 revision、迟到旧 revision、provider 暂不可读后的重投收敛均已验证。
- **未完成边界**：真实渠道账单协议、persistent scheduler/lease、跨实例 exactly-once 和通用规则引擎未实现。
- **状态**：`verified`。

<a id="pay-cp-004"></a>
### PAY-CP-004 MerchantSettlement 与 SettlementLine

- **当前实现**：MerchantSettlement 聚合根，包含 SettlementLine、SettlementExecutionAttempt、SettlementResultReceipt 的 owned graph；覆盖日结范围、有效单/有效消费约束、组成冻结、执行与结果裁决、作废/replacement 链。
- **业务证据**：消费 current effective reconciliation run 的已确认事实；交易粒度排除未决项；支付手续费快照；127.00 示例净额 124.46；负净额禁止划拨；未知结果禁止重付；重复/迟到冲突不回退成功终态。
- **B5 出站事实**：首个 accepted terminal success 形成一次 `MerchantSettlementCompleted`，业务变更与 JPA reliable-event 记录同事务；HTTP 503 或真实 response timeout 后均以同一 event UUID/type/payload durable retry/recovery。
- **边界**：同步 Fake Transfer/Verifier 不是生产银行网络；当前也不提供 broker、generic Inbox、生产商户通知或 exactly-once。
- **状态**：`verified`。

<a id="pay-cp-005"></a>
### PAY-CP-005 MerchantChannelConfiguration

- **当前实现**：独立配置聚合为支付/退款/手续费/结果复核提供 reference seed 与快照来源；交易事实保存配置身份或规则快照，后续配置变化不改写历史事实。
- **未完成边界**：配置维护 Endpoint、动态规则语言、通用配置中心及完整退役生命周期没有单独 closure 证据。
- **状态**：已使用范围 `verified`；完整投影仍为 `planned`。

## 4. 业务流程与入口投影

<a id="pay-cp-006"></a>
### PAY-CP-006 支付回调

- 当前通过 transport-neutral Endpoint contract、手写 HTTP binding、同步 VerifyPaymentResult Capability 和 Payment 聚合内裁决完成。
- 支付/退款 callback 均保存 receipt；Payment callback 进一步覆盖 RESULT_UNKNOWN 收敛、关闭/失败后的迟到成功、同 Payment 多 attempt success、成功后的失败/未知结果、同 notification identity 不同 payload，以及 append-only review evidence。
- 支付/退款 callback 仍是 Endpoint HTTP 路径，不是 Integration Event consumer；B5 的限定出站事件仅覆盖 merchant-settlement completion，不构成支付/退款生产商户通知。
- **状态**：HTTP callback reference 路径已验证；支付/退款通知与更宽 Integration Event 投影仍为 `planned`。

<a id="pay-cp-007"></a>
### PAY-CP-007 支付超时

- 当前实现 ordinary `PaymentExpiryScheduler -> ExpirePayments Command`，按 UTC `expiresAt` 裁决：无未决 attempt 的到期 Payment 幂等进入 `CLOSED`；存在 `PROCESSING/RESULT_UNKNOWN` attempt 时进入 `RESULT_UNKNOWN` 并创建稳定去重的 blocking review。
- 可信最终结果可从 RESULT_UNKNOWN 收敛；关闭/失败后的迟到成功保持原终态和证据，必须经过授权 review 才能改变 success/eligibility 决定。scheduler 保持薄壳，不直接访问 Repository。
- ordinary `@Scheduled` 不承诺 durable scheduling、分布式 lease、跨实例 exactly-once 或可靠消息 continuation。
- **状态**：当前 reference lifecycle `verified`；生产级可靠调度 closure 仍为 `planned`。

<a id="pay-cp-008"></a>
### PAY-CP-008 日终对账

- 当前 Pull 主路径保留 `@Scheduled` Time 入口 + PullChannelStatement provider pull + LoadPlatformReconciliationFacts projection；Push 路径增加 `ChannelStatementAvailable` HTTP Integration Event。
- event 仅传递 statement identity/revision 可用信号，正文仍由权威 provider pull；scheduler、人工 rerun 和 event 入口汇合到同一 batch/revision 幂等模型。
- 已验证自动匹配、差异分支、revision replay/历史、迟到旧 revision 不回退、provider 暂不可读后的重投、人工处置与 current effective run。Payment 有 unresolved blocking review 时，即使金额/渠道完全匹配，ReconciliationItem 仍保存 review identity/type/reason snapshot 并保持 settlement-blocking；旧 run 不随后改写。
- 持久化业务调度、lease 与跨实例 exactly-once 未实现。
- **状态**：`verified`。

<a id="pay-cp-009"></a>
### PAY-CP-009 商户结算

- 当前以 Asia/Shanghai Time 入口准备 MerchantSettlement，汇总已确认的支付、退款、手续费和对账确认事实。
- 已验证确认冻结、正/负净额分支、同步 Fake Transfer、回调去重/冲突/未知复核、受控作废/replacement 与完整查询。候选准备会重新读取 Payment 当前 review eligibility；即使 review 在 reconciliation run 之后才出现、兼容布尔摘要被篡改为 false，也不能绕过排除。
- 首个成功事实会形成 `MerchantSettlementCompleted`，通过 JPA reliable-event 与 HTTP transport 投递；业务/事件共同回滚，以及 HTTP 503、真实 response timeout 后的同一 event UUID/type/payload retry/recovery 已验证。
- 生产资金划拨、broker、generic Inbox、生产商户通知、持久化业务调度与 exactly-once 未实现。
- **状态**：`verified`。

<a id="pay-cp-010"></a>
### PAY-CP-010 Integration Event 边界

B5 已验证两个稳定 v1 published contract：入站 `payment.reconciliation.channel-statement-available.v1` 与出站 `payment.merchant-settlement.completed.v1`。入站通过 canonical `/cap4k/integration-events` HTTP receiver 分派到同步 subscriber，再进入普通 Command 与权威 Pull；出站从本地完成 Domain Event 映射后调用 `Mediator.events.enqueue`，由 JPA reliable-event 记录与 HTTP provider 负责 handoff/retry。

当前证据证明 at-least-once transport、稳定 event identity、业务幂等收敛、事务共同回滚，以及 HTTP non-2xx 与 response-timeout 后保持稳定 event identity 的 durable retry/recovery；不证明 generic Inbox、broker/broadcast、全局事件组合、跨实例 exactly-once 或生产商户通知。支付/退款渠道结果仍是原有 HTTP callback，对账差异等更广 event portfolio 仍后置。

- **状态**：B5 指定 HTTP Integration Event 范围 `verified`；完整 Integration Event closure 仍为 `planned`。

<a id="pay-cp-011"></a>
### PAY-CP-011 Endpoint 边界

- B1-B5 与 #4 已生成 transport-neutral contract module，Adapter 每个 Endpoint Handler 一类一文件、默认静态 Mediator，业务 HTTP binding 手写；Integration Event HTTP receiver 由 cap4k starter 提供。
- 当前覆盖支付、退款、对账与商户结算的创建/命令/回调/查询入口，以及 Payment review adjudication；contract 保持 dependency leaf。
- 配置维护 API、Endpoint RPC 和 published consumer artifact 尚未实现。
- **状态**：已实现 Endpoint 集合 `verified`；完整投影仍为 `planned`。

## 5. cap4k 能力面投影

<a id="pay-cp-012"></a>
### PAY-CP-012 Runtime

当前 B1-B5 与 #4 实际验证 Repository/UoW、Strong ID、乐观并发、本地 Domain Event、同步 Request/Capability、Endpoint HTTP、普通定时入口、Payment timeout/review lifecycle、append-preserving conflict evidence、merchant-order success serialization、B3/B4 eligibility 传播、JPA-backed reliable Integration Event enqueue/record、HTTP sender 对 non-2xx/response-timeout 的 retry/recovery、HTTP receiver dispatch、幂等与聚合内/跨聚合事务行为。

可靠 Command、broker transport、generic Inbox、持久化业务 scheduling、跨实例 lease/exactly-once、Endpoint RPC 与生产 provider 不在当前闭环。

状态：已使用 Runtime 面有证据；完整 capability closure 仍为 `planned`。

<a id="pay-cp-013"></a>
### PAY-CP-013 Generator

当前 DB schema、Design JSON、enum manifest 与 value-object manifest 生成/物化聚合、Owned Entity、Strong ID、Repository、Factory/Behavior、枚举、VO、Command、Query、Capability、Endpoint 与 Integration Event contract/subscriber scaffold。最终 ordinary plan 为 197 items：137 checked-in `SKIP`、60 generated `OVERWRITE`，连续 generation 无新增 source difference；HTTP transport runtime 仍由 starter 装配，不是 Generator 生成物。

状态：当前项目生成面有证据；全局 Generator capability closure 仍为 `planned`。

<a id="pay-cp-014"></a>
### PAY-CP-014 Analyzer

当前 Analyzer 产生 46 个 outputs/items、19 条独立入口 Flow（13 个 Endpoint HTTP Actor roots、5 个 Time roots、1 个 Integration Event root）以及 Drawing Board/Aggregate Structure；新增 Payment expiry Time root 与 review adjudication HTTP Actor root。`drawing_board_integration_event.json` 单独投影 published event。Query/Capability/聚合结构保持独立 projection，不伪造跨入口 process stitching；Time/Integration Event Flow 只证明静态入口关系，不证明 durable scheduler、delivery 状态机或 exactly-once。

状态：当前项目 Analyzer 面有证据；包含所有计划事件/超时入口的完整 closure 仍为 `planned`。

<a id="pay-cp-015"></a>
### PAY-CP-015 Pipeline

当前项目使用固定阶段、repository-level source/generator 配置和 6 个公开 Pipeline tasks。显式 Composite Build 的解析顺序为 Gradle property、环境变量、正式版 2.0.1；仓库不提交机器路径、Snapshot、私服或 `mavenLocal()`。

状态：当前 Pipeline 使用面有证据；published-coordinate cold start 仍后置到 B6，完整 closure 保持 `planned`。

<a id="pay-cp-016"></a>
### PAY-CP-016 AgentFacts

当前 Agent Snapshot ownership 保留 197 个 plan items，analysis 为 `ok` 且有 46 个 available outputs，diagnostics 为 0。Snapshot overall 为 `partial` 的唯一原因是 live DB source freshness 为 `UNKNOWN`，不是 INVALID、error 或 plan evidence 解析失败。

状态：当前 AgentFacts evidence 已验证；依赖 B6 与更宽 B5 后续能力的完整 capability closure 仍为 `planned`。

## 6. 当前 B1-B5 与 #4 之外的能力

- reliable Command；
- RabbitMQ/RocketMQ/Kafka 等 broker transport、generic Inbox、broadcast/discovery 与完整事件组合；
- 持久化业务调度、跨实例 lease 与 exactly-once；
- B5 两个事件之外的渠道结果、对账差异、支付/退款通知等 Integration Event portfolio；
- 生产商户通知服务、真实支付/退款/账单/银行/清算 provider、生产认证和敏感数据保护；
- 负净额追偿、商户补款或后续周期抵扣；
- 周结及其他结算周期；
- published-coordinate cold start 与完整 consumer E2E；
- Jimmer/aggregateProjection、读库物化投影、CDC 或事件投影读模型；
- only-engine addon 集成（当前 B5 不需要）；
- Event Sourcing、Saga 或通用工作流引擎；
- 多 cap4k 历史版本投影目录及兼容层。

这些边界不影响当前业务需求真源；若以后授权实施，必须新增独立投影 ID 和可验证证据。

## 7. 当前证据与后续升级门槛

当前可复核证据包括：

- ordinary plan 197 items（137 checked-in `SKIP`、60 generated `OVERWRITE`）；
- clean build 100 tests / 23 suites / 0 failures / 0 errors / 0 skips（进入 #4 前为 84 tests / 22 suites）；
- Analyzer 46 outputs/items、19 independent flows（13 HTTP + 5 Time + 1 Integration Event）；
- Agent ownership 197、analysis `ok`、diagnostics 0；overall `partial` 仅因 live DB freshness `UNKNOWN`。

只有在代码、自动化测试、生成计划、Analyzer 输出或 AgentFacts 中产生对应证据后，才可将 `PAY-EV-*` 标记为 `verified`。同步 Fake Provider、普通 scheduler 或本地 Domain Event 不能替代尚未实现的 B5 后续、生产级 scheduling 或 B6 能力。
