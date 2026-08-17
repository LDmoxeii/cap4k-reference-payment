# 当前 cap4k 实现投影

## 1. 文档性质

本文是支付业务需求到 **当前 cap4k 能力面** 的计划投影，不是业务真源，也不是已经完成的实现说明。

当前状态：`not-built`。

以下内容均表示预期采用的建模与验证方向：

- 不代表代码已经生成；
- 不代表 Runtime 已经运行；
- 不代表 Analyzer 已经产生事实；
- 不代表 Pipeline 或 AgentFacts 已经验证；
- 不构成对历史 cap4k 版本的兼容承诺。

本项目只维护这一份 current-only 投影，不建立 `<baseline>` 目录、不保留旧投影副本、不实现兼容层。历史由 Git 保存。

## 2. 投影状态词

| 状态 | 含义 |
|---|---|
| `planned` | 已确认目标映射，但尚未进入 Build |
| `not-built` | 没有实现、运行结果或可引用证据 |
| `verified` | 后续 Build 完成并有可复核证据后才能使用 |

本文所有投影当前均为 `planned`，所有对应证据当前均为 `not-built`。

## 3. 领域模型投影

<a id="pay-cp-001"></a>
### PAY-CP-001 Payment 与 PaymentAttempt

- **业务来源**：具体业务规则和验收来源以 `traceability.yaml` 中 `PAY-CP-001` 的显式 ID 列表为准。
- **Generator 目标**：Payment 作为聚合根；PaymentAttempt 作为强引用子实体，由 Payment 维护渠道、请求标识、渠道交易号、状态、失败原因及回调时间。
- **Runtime 目标**：Repository/UoW 保存完整聚合；重复或乱序渠道回调必须在聚合边界内幂等处理；并发回调、超时关闭和人工确认通过乐观并发及聚合不变量裁决。
- **Analyzer 目标**：从创建支付与渠道回调等真实入口追踪 Payment 状态推进；支付超时作为既有支付链的延迟可靠动作继续展开，不伪装成独立 Time 根。
- **边界**：引用项目验证典型并发与幂等行为，不承担病态高并发或 ORM 极限图压力证明。
- **状态**：`planned / not-built`。

<a id="pay-cp-002"></a>
### PAY-CP-002 Refund

- **业务来源**：具体业务规则和验收来源以 `traceability.yaml` 中 `PAY-CP-002` 的显式 ID 列表为准。
- **Generator 目标**：Refund 作为独立聚合，通过强类型 PaymentId 弱引用 Payment，不整取 Payment 对象图。
- **Runtime 目标**：支持全额和多次部分退款；可靠发起渠道退款；重复退款回调幂等；累计退款不得超过成功支付金额。
- **Analyzer 目标**：追踪退款申请、渠道结果、结算扣减与商户通知。
- **边界**：跨聚合一致性通过明确命令、事件与读取契约表达，不建立双向对象引用网络。
- **状态**：`planned / not-built`。

<a id="pay-cp-003"></a>
### PAY-CP-003 ReconciliationBatch 与 ReconciliationItem

- **业务来源**：具体业务规则和验收来源以 `traceability.yaml` 中 `PAY-CP-003` 的显式 ID 列表为准。
- **Generator 目标**：ReconciliationBatch 为聚合根，ReconciliationItem 为强引用子实体，覆盖批次状态、平台侧/渠道侧金额、差异类型、处置状态和意见。
- **Runtime 目标**：定时创建日终批次；可靠导入或接收渠道账单；保持批次与明细的一致状态推进。
- **Analyzer 目标**：以时间入口和账单到达事件入口形成批次处理、自动匹配、差异分支及人工处置 Flow。
- **边界**：第一期不建设通用对账规则引擎，也不保存真实敏感渠道账单。
- **状态**：`planned / not-built`。

<a id="pay-cp-004"></a>
### PAY-CP-004 MerchantSettlement 与 SettlementLine

- **业务来源**：具体业务规则和验收来源以 `traceability.yaml` 中 `PAY-CP-004` 的显式 ID 列表为准。
- **Generator 目标**：MerchantSettlement 为聚合根，SettlementLine 为强引用子实体，覆盖结算明细与汇总不变量。
- **Runtime 目标**：按周期可靠创建结算；防止同一业务项重复入账；完成后发布可靠结算事件。
- **Analyzer 目标**：追踪周期触发、明细汇总、失败重试、结算完成及出站通知。
- **边界**：第一期不实现真实清分清算网络或银行资金划拨，只保留外部能力契约。
- **状态**：`planned / not-built`。

<a id="pay-cp-005"></a>
### PAY-CP-005 MerchantChannelConfiguration

- **业务来源**：具体业务规则和验收来源以 `traceability.yaml` 中 `PAY-CP-005` 的显式 ID 列表为准。
- **Generator 目标**：独立配置聚合，覆盖 Strong ID、Value Object、枚举、审计字段与自然的软删除/退役场景。
- **Runtime 目标**：为渠道路由 Capability 提供当前有效配置；交易记录本身不允许通过软删除规避审计保留。
- **Analyzer 目标**：记录配置变更对后续支付路由的影响，但不将配置变更伪装成已发生的支付流程。
- **边界**：不建设动态规则语言或通用配置中心。
- **状态**：`planned / not-built`。

## 4. 业务流程与入口投影

<a id="pay-cp-006"></a>
### PAY-CP-006 支付回调

- 入站 Integration Event 或 Endpoint 接收渠道回调。
- 回调适配层验证渠道身份并转换为稳定的应用输入。
- Runtime 在 Payment 聚合内执行幂等和状态裁决。
- 成功后计划产生领域事实，并可靠触发商户通知、对账期望记录与结算候选记录。
- 当前没有回调 Endpoint、签名验证、事件消费者或运行证据。

<a id="pay-cp-007"></a>
### PAY-CP-007 支付超时

- 支付创建后计划登记延迟可靠命令，在到期时继续原支付因果链；该延迟执行不是 Analyzer 的独立 Time 根。
- Runtime 必须在执行时重新确认 Payment 仍为待支付状态。
- 迟到成功回调不得静默覆盖关闭结果，必须进入明确的差异处置路径。
- 当前没有调度、延迟消息、超时处理器或运行证据。

<a id="pay-cp-008"></a>
### PAY-CP-008 日终对账

- Time 入口计划创建对账批次。
- 渠道账单既可通过外部能力拉取，也可通过入站 Integration Event 声明到达；Build 时必须选择并固定一种主路径。
- Analyzer 目标覆盖自动一致、金额差异、状态差异、平台单边和渠道单边分支。
- 当前没有 Scheduler、账单 Provider、对账代码或运行证据。

<a id="pay-cp-009"></a>
### PAY-CP-009 商户结算

- Time 入口按商户结算周期创建 MerchantSettlement。
- 汇总已确认的支付、退款和手续费，生成 SettlementLine。
- 完成后计划发布出站 Integration Event；失败通过可靠执行机制重试或进入人工处置。
- 当前没有结算作业、资金 Provider、出站消息或运行证据。

<a id="pay-cp-010"></a>
### PAY-CP-010 Integration Event 边界

计划的入站事件：

- 支付渠道结果；
- 退款渠道结果；
- 渠道对账账单到达；
- 外部结算或资金处理结果。

计划的出站事件：

- 支付成功/失败；
- 退款成功/失败；
- 对账差异待处理；
- 商户结算完成/失败。

边界要求：Domain Event 表达聚合内已经发生的事实；Integration Event 只在系统边界发布稳定契约。当前没有任何事件 schema、transport、outbox 或消费证据。

<a id="pay-cp-011"></a>
### PAY-CP-011 Endpoint 边界

计划的 Endpoint 最小集合：

- 商户创建并查询支付；
- 用户或商户申请并查询退款；
- 渠道支付/退款回调；
- 运营人员查询和处置对账差异；
- 商户查询结算结果；
- 运营人员维护商户渠道配置。

Endpoint 只负责协议转换、身份上下文和应用调用，不承担聚合不变量。当前没有 API、RPC 或契约测试证据。

## 5. cap4k 能力面投影

<a id="pay-cp-012"></a>
### PAY-CP-012 Runtime

计划验证 Repository、UoW、Strong ID、乐观并发、Domain Event、可靠命令、可靠事件、Integration Event transport、Endpoint HTTP、定时入口和幂等处理；Endpoint RPC 仅在出现真实远端 published contract 时加入，不为覆盖率硬造。极端 ORM 对象图、高频撮合式并发及框架内部 provider 冲突继续由 cap4k focused fixtures 负责。

状态：`planned / not-built`。

<a id="pay-cp-013"></a>
### PAY-CP-013 Generator

计划从业务模型生成或辅助维护聚合根、Owned Entity、枚举、Value Object/Converter、Strong ID、Repository、行为骨架及入口相关产物。具体生成清单必须以 Build 时 cap4k 当前生产 descriptor 和任务注册为准，不在本文手写承诺不存在的能力。

状态：`planned / not-built`。

<a id="pay-cp-014"></a>
### PAY-CP-014 Analyzer

计划验证三类入口：

- Actor：创建支付、申请退款、人工处置差异、维护渠道配置；
- Event：支付/退款回调、账单到达、外部结算结果；
- Time：日终对账、周期结算；支付超时属于延迟可靠动作 continuation，不计为独立 Time 根。

计划审查分支、后续命令、可靠执行和跨边界事件链。当前没有 Analyzer 输出或基准快照。

状态：`planned / not-built`。

<a id="pay-cp-015"></a>
### PAY-CP-015 Pipeline

Build 前必须读取 cap4k 当前固定阶段契约，以 repository-level source/generator 配置驱动；项目只启停受支持的 source/generator，不定制 stage 顺序、不注入任意 runtime logic。模板 helper 不承担类型解析所有权。

状态：`planned / not-built`。

<a id="pay-cp-016"></a>
### PAY-CP-016 AgentFacts

Build 后只接受由 cap4k 当前生产契约和项目实际观察生成的 AgentFacts。Public Docs、Skill 或本文不得反向伪造能力事实。每个 Runtime/Generator/Analyzer/Pipeline 映射必须以生成 facts、测试或可复核输出作为证据后，才能从 `not-built` 改为 `verified`。

状态：`planned / not-built`。

## 6. 明确不进入第一期的能力

- Jimmer/aggregateProjection 高级扩展；
- only-engine addon 集成；
- 读库物化投影、CDC 或事件投影读模型；
- Event Sourcing、Saga 或通用工作流引擎；
- 支持多个 cap4k 历史版本的投影目录；
- 为兼容旧 API、旧 DSL 或旧生成器路径编写桥接层。

这些边界不影响当前业务需求真源；若以后授权实施，必须新增独立投影 ID 和可验证证据。

## 7. 进入 Build 的证据门槛

进入 Build 后，每项能力至少需要一种实际证据：

- 生成文件及来源配置；
- 聚焦自动化测试；
- Runtime 运行日志或可重复测试结果；
- Analyzer 输出；
- Pipeline 任务/descriptor 事实；
- AgentFacts 导出和一致性验证结果。

在这些证据产生之前，`docs/requirements/traceability.yaml` 中所有 `PAY-EV-*` 必须保持 `not-built`。
