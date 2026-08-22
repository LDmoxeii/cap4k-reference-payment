# 当前 cap4k 实现投影

## 1. 文档性质

本文是支付业务需求到 **当前 cap4k 能力面** 的 current-only 投影，不是业务真源，也不保存历史版本副本。

当前状态：B1 支付、B2 退款、B3 日终对账和 B4 商户日结结算均已有可运行实现与证据。精确状态以 `docs/requirements/traceability.yaml` 为准；宽于已实现切片的投影继续保持 `planned / not-built`，不因局部能力已验证而宣称完整 closure。

- `verified` 条目必须有实际代码、测试、Pipeline、Analyzer 或 AgentFacts 证据；
- `planned / not-built` 条目不代表代码已经生成或 Runtime 已经运行；
- 同步 Fake Provider、普通 `@Scheduled` 和本地 Domain Event 不等于可靠异步、Integration Event transport 或生产资金网络；
- 本项目不构成对历史 cap4k 版本的兼容承诺。

本项目只维护这一份 current-only 投影，不建立 `<baseline>` 目录、不保留旧投影副本、不实现兼容层。历史由 Git 保存。

## 2. 投影状态词

| 状态 | 含义 |
|---|---|
| `planned` | 已确认目标映射，但完整投影尚未完成 |
| `not-built` | 没有实现、运行结果或可引用证据 |
| `verified` | 对应实现范围已有可复核证据 |

B1-B4 已验证具体 acceptance 和 evidence；包含超时、可靠 transport、生产网络或完整配置维护的更宽投影仍保持 `planned / not-built`。

## 3. 领域模型投影

<a id="pay-cp-001"></a>
### PAY-CP-001 Payment 与 PaymentAttempt

- **当前实现**：Payment 聚合根、PaymentAttempt 与通知 receipt owned graph、UUID7 Strong ID、JPA Repository/UoW、幂等创建、渠道尝试、回调裁决、乐观锁；B2 增加退款预算，B4 在支付首次成功时原子冻结手续费事实。
- **未完成边界**：支付超时可靠 continuation、人工终局处置、完整商户通知可靠性仍未实现。
- **状态**：已实现范围 `verified`；完整投影 closure 仍为 `planned`。

<a id="pay-cp-002"></a>
### PAY-CP-002 Refund

- **当前实现**：Refund 独立聚合，通过强类型 PaymentId 弱引用 Payment；支持全额/多次部分退款、防超退、预算占用与释放、回调去重/冲突/未知复核、真实并发 409 与跨聚合 UoW 回滚。
- **未完成边界**：真实渠道协议、可靠异步发起与出站通知 transport 未实现。
- **状态**：已实现范围 `verified`；完整投影 closure 仍为 `planned`。

<a id="pay-cp-003"></a>
### PAY-CP-003 ReconciliationBatch 与 ReconciliationItem

- **当前实现**：ReconciliationBatch 聚合根，包含 Run、Item、Disposition、ConfirmationFact 的 owned graph；保存平台/渠道双方快照、statement revision 历史、差异分类与追加式处置。
- **Runtime/Analyzer**：Asia/Shanghai 日终 scheduler、provider pull、重跑/处置 HTTP 入口、Time/Actor Flow、H2/JPA 幂等和并发约束均有证据。
- **边界**：账单到达 Integration Event、真实渠道账单协议和通用规则引擎未实现。
- **状态**：`verified`。

<a id="pay-cp-004"></a>
### PAY-CP-004 MerchantSettlement 与 SettlementLine

- **当前实现**：MerchantSettlement 聚合根，包含 SettlementLine、SettlementExecutionAttempt、SettlementResultReceipt 的 owned graph；覆盖日结范围、有效单/有效消费约束、组成冻结、执行与结果裁决、作废/replacement 链。
- **业务证据**：消费 current effective reconciliation run 的已确认事实；交易粒度排除未决项；支付手续费快照；127.00 示例净额 124.46；负净额禁止划拨；未知结果禁止重付；重复/迟到冲突不回退成功终态。
- **边界**：同步 Fake Transfer/Verifier 不是生产银行网络，也不提供可靠出站事件或 exactly-once。
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
- 支付/退款 callback 均保存 receipt，覆盖重复、矛盾、不可信和终态不可回退。
- 当前不是 Integration Event consumer，也没有可靠商户通知或 outbox。
- **状态**：HTTP reference 路径已验证；包含 Integration Event 的完整投影仍为 `planned`。

<a id="pay-cp-007"></a>
### PAY-CP-007 支付超时

- 目标仍是延迟可靠动作 continuation，执行时重新确认 Payment 状态并显式裁决迟到结果。
- 当前没有延迟消息、可靠调度或超时处理器证据。
- **状态**：`planned / not-built`。

<a id="pay-cp-008"></a>
### PAY-CP-008 日终对账

- 当前主路径固定为 `@Scheduled` Time 入口 + PullChannelStatement provider pull + LoadPlatformReconciliationFacts projection。
- 已验证自动匹配、差异分支、revision 幂等/历史、人工处置与 current effective run。
- bill-arrival Integration Event、持久化调度、lease/retry 和跨实例 exactly-once 未实现。
- **状态**：`verified`。

<a id="pay-cp-009"></a>
### PAY-CP-009 商户结算

- 当前以 Asia/Shanghai Time 入口准备 MerchantSettlement，汇总已确认的支付、退款、手续费和对账确认事实。
- 已验证确认冻结、正/负净额分支、同步 Fake Transfer、回调去重/冲突/未知复核、受控作废/replacement 与完整查询。
- 可靠出站 Integration Event、生产资金划拨、持久化重试与 exactly-once 未实现。
- **状态**：`verified`。

<a id="pay-cp-010"></a>
### PAY-CP-010 Integration Event 边界

计划的入站/出站边界仍包括渠道结果、账单到达、对账差异和商户结算结果，但当前 B1-B4 只使用 HTTP callback、本地 Domain Event、同步 Capability 与普通 scheduler。

Domain Event 表达聚合内已经发生的事实；Integration Event 需要稳定契约、transport、outbox/投递与消费证据。当前没有这些可靠 transport 证据。

- **状态**：`planned / not-built`。

<a id="pay-cp-011"></a>
### PAY-CP-011 Endpoint 边界

- B1-B4 已生成 transport-neutral contract module，Adapter 每个 Handler 一类一文件、默认静态 Mediator，HTTP binding 手写。
- 当前覆盖支付、退款、对账与商户结算的创建/命令/回调/查询入口；contract 保持 dependency leaf。
- 配置维护 API、Endpoint RPC 和 published consumer artifact 尚未实现。
- **状态**：已实现 Endpoint 集合 `verified`；完整投影仍为 `planned`。

## 5. cap4k 能力面投影

<a id="pay-cp-012"></a>
### PAY-CP-012 Runtime

当前 B1-B4 实际验证 Repository/UoW、Strong ID、乐观并发、本地 Domain Event、同步 Request/Capability、Endpoint HTTP、普通定时入口、幂等与聚合内/跨聚合事务行为。

可靠 Command/Event、Integration Event transport、持久化 scheduling、lease/retry、跨实例 exactly-once、Endpoint RPC 与生产 provider 不在当前闭环。

状态：已使用 Runtime 面有证据；完整 capability closure 仍为 `planned`。

<a id="pay-cp-013"></a>
### PAY-CP-013 Generator

当前 DB schema、Design JSON、enum manifest 与 value-object manifest 生成/物化聚合、Owned Entity、Strong ID、Repository、Factory/Behavior、枚举、VO、Command、Query、Capability 与 Endpoint contract。最终 ordinary plan 为 170 items：116 checked-in `SKIP`、54 generated `OVERWRITE`，连续 generation 无 source difference。

状态：当前项目生成面有证据；全局 Generator capability closure 仍为 `planned`。

<a id="pay-cp-014"></a>
### PAY-CP-014 Analyzer

当前 Analyzer 产生 39 个 outputs/items、16 条独立入口 Flow（12 个 Endpoint HTTP Actor roots、4 个 Time roots）以及 Drawing Board/Aggregate Structure。B4 新增 5 个 HTTP Actor roots 和 2 个 Time roots；Query/Capability/聚合结构保持独立 projection，不伪造跨入口 process stitching。

状态：当前项目 Analyzer 面有证据；包含所有计划事件/超时入口的完整 closure 仍为 `planned`。

<a id="pay-cp-015"></a>
### PAY-CP-015 Pipeline

当前项目使用固定阶段、repository-level source/generator 配置和 6 个公开 Pipeline tasks。显式 Composite Build 的解析顺序为 Gradle property、环境变量、正式版 2.0.1；仓库不提交机器路径、Snapshot、私服或 `mavenLocal()`。

状态：当前 Pipeline 使用面有证据；published-coordinate cold start 仍后置到 B6，完整 closure 保持 `planned`。

<a id="pay-cp-016"></a>
### PAY-CP-016 AgentFacts

当前 Agent Snapshot ownership 保留 170 个 plan items，analysis 为 `ok` 且有 39 个 available outputs，diagnostics 为 0。Snapshot overall 为 `partial` 的唯一原因是 live DB source freshness 为 `UNKNOWN`，不是 INVALID、error 或 plan evidence 解析失败。

状态：当前 AgentFacts evidence 已验证；依赖 B5/B6 的完整 capability closure 仍为 `planned`。

## 6. 当前 B1-B4 之外的能力

- Outbox、可靠 Command/Event、Integration Event transport；
- 持久化调度、lease/retry 与跨实例 exactly-once；
- 真实支付/退款/账单/银行/清算 provider、生产认证和敏感数据保护；
- 负净额追偿、商户补款或后续周期抵扣；
- 周结及其他结算周期；
- published-coordinate cold start 与完整 consumer E2E；
- Jimmer/aggregateProjection、读库物化投影、CDC 或事件投影读模型；
- only-engine addon 集成；
- Event Sourcing、Saga 或通用工作流引擎；
- 多 cap4k 历史版本投影目录及兼容层。

这些边界不影响当前业务需求真源；若以后授权实施，必须新增独立投影 ID 和可验证证据。

## 7. 当前证据与后续升级门槛

当前可复核证据包括：

- ordinary plan 170 items（116 checked-in `SKIP`、54 generated `OVERWRITE`）；
- clean build 77 tests / 21 suites / 0 failures；
- Analyzer 39 outputs/items、16 independent flows；
- Agent ownership 170、analysis `ok`、diagnostics 0；overall `partial` 仅因 live DB freshness `UNKNOWN`。

只有在代码、自动化测试、生成计划、Analyzer 输出或 AgentFacts 中产生对应证据后，才可将 `PAY-EV-*` 标记为 `verified`。同步 Fake Provider、普通 scheduler 或本地 Domain Event 不能替代 B5/B6 所需证据。
