# M6：HTTP、Event、Analyzer 与缺口边界

> 本文是面向阅读式教学的自包含材料。目标是把“业务行为证据”“入口契约”“调度/事件交接”和“静态 Analyzer/Flow”分层阅读，并明确哪些 PAY-AC 仍是 planned/not-built，不能被局部测试误报为已完成。

## 固定上下文胶囊

- 工程：`cap4k-reference-payment`；主业务链为 `Payment → Refund → Reconciliation → Merchant Settlement`。
- 本课范围：HTTP status/code/message、错误安全边界、普通 `@Scheduled`、Integration Event、at-least-once handoff、Analyzer/Flow 静态可达性，以及 planned/not-built 场景。
- 重点场景：`PAY-AC-080`、`PAY-AC-081`、`PAY-AC-084`、`PAY-AC-086`；并串联已验证的 `PAY-AC-087`、`PAY-AC-088`。
- 当前状态：总场景 `53` 个，`verified` `49` 个，`planned/not-built` `4` 个。`PAY-AC-080/081/084/086` 仍不能升级为 verified。
- 测试边界：契约测试和结构测试主要证明入口形状、稳定错误和静态追踪；H2/Fake Provider/测试 receiver/普通 scheduler/HTTP retry 不等于生产网络、通用 outbox、跨实例 exactly-once 或完整认证授权。

## 业务问题与全链路位置

前几课已经证明 Payment、Refund、Reconciliation 和 Settlement 的业务状态及持久化事实。M6 处理的是另一个容易混淆的问题：这些事实如何通过 HTTP、scheduler 和 integration event 被触发、交接和观察？

```text
HTTP / Scheduler / Integration Event
  → Command / Capability / UoW
  → domain state + durable evidence
  → HTTP response / event record / Flow trace
```

入口层可以证明“请求被正确映射、错误可稳定识别、命令被发送”，但不能自动证明业务规则已经执行。Analyzer/Flow 可以证明静态入口存在一条可达边，但不能证明运行时跨入口 exactly-once stitching。planned 场景则要求我们明确承认缺口，而不是从 fixture 或局部 guard 推断生产能力。

## 阅读路线

1. [payment-teaching-context.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-context.md) 和 [payment-teaching-syllabus.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-syllabus.md#m6横切入口analyzer-与缺口)：确认证据层次和 M6 范围。
2. [payment-test-evidence-catalog.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-test-evidence-catalog.md)：阅读 Adapter/contract、Evidence guard、Analyzer/Flow 的证明边界。
3. [PaymentHttpErrorAdviceTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/adapter/src/test/kotlin/com/only4/cap4k/reference/payment/adapter/endpoints/payment/PaymentHttpErrorAdviceTest.kt#L15) 和各模块 endpoint HTTP configuration contract：先看错误响应如何稳定化。
4. [UserVisibleMessageContractTests.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/UserVisibleMessageContractTests.kt#L8)：再看用户可见消息与 provider 诊断隔离。
5. [PaymentExpirySchedulerStructureTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/adapter/src/test/kotlin/com/only4/cap4k/reference/payment/adapter/start/PaymentExpirySchedulerStructureTest.kt#L10)、`DailyReconciliationSchedulerStructureTest`、`MerchantSettlementSchedulerStructureTest`：阅读 scheduler 只发 Command 的结构约束。
6. [ChannelStatementAvailableIntegrationEventSubscriber.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/application/src/main/kotlin/com/only4/cap4k/reference/payment/application/subscribers/integration/ChannelStatementAvailableIntegrationEventSubscriber.kt#L22)、Integration Event contract 和 [inbound Flow](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/flows/com_only4_cap4k_reference_payment_contract_events_integration_inbound_reconcilia.json)：看事件触发与 Pull 权威性。
7. [TraceabilityContractTests.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/TraceabilityContractTests.kt#L121) 和 [cap4k-current.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/projection/cap4k-current.md#L107)：最后核对 planned/not-built 与 Integration Event 证明边界。

## 按证据层次阅读

### 1. HTTP 契约：稳定错误，不泄露 provider 细节

`PaymentHttpErrorAdviceTest#application errors preserve stable code while exposing Chinese message and safe details`（[PaymentHttpErrorAdviceTest.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/adapter/src/test/kotlin/com/only4/cap4k/reference/payment/adapter/endpoints/payment/PaymentHttpErrorAdviceTest.kt#L15)）用异常直接调用 advice，断言：

- 不存在支付单返回 `404`、`PAYMENT_NOT_FOUND`，message 含“未找到支付单”，details 只带 `paymentId`；
- 非法请求返回稳定的 `INVALID_REQUEST`；
- 状态冲突使用 `PAYMENT_STATE_CONFLICT`；
- 并发修改使用 `CONCURRENT_MODIFICATION`，message 表达“其他请求修改”；
- 未预期异常返回 `500`、`INTERNAL_ERROR`，不把 raw provider failure 放进用户响应。

这些断言证明错误映射和安全字段边界，不证明对应业务场景一定从 HTTP 入口完整执行；完整行为仍需回到 Payment/Refund/Reconciliation/Settlement application tests。

### 2. 用户消息与诊断隔离

`UserVisibleMessageContractTests` 扫描 domain/application/adapter/start 源码，禁止把 `error.message`、`failure.message` 或 raw gateway diagnostics 投影给用户。`gateway.diagnosticSummary` 只能留在日志/安全诊断路径，并经过 `safeDiagnostic` 隔离；用户可见 message/summary 必须符合项目的中文消息约定。

这是一条“不会泄露内部细节”的契约证据，不是完整错误处理流程的行为证据。它也不证明日志系统、脱敏策略或生产多语言发布流程已经完成。

### 3. Scheduler：薄入口只负责发 Command

`PaymentExpirySchedulerStructureTest` 要求只有一个 `@Scheduled` 入口，调用 `Mediator.commands.send(ExpirePaymentsCmd.Request(clock.instant()))`，并禁止在 scheduler 中直接访问 Query、Repository 或自行实现分布式 exactly-once。`DailyReconciliationSchedulerStructureTest`、`MerchantSettlementSchedulerStructureTest` 遵守同一原则：scheduler 只产生 `RunDailyReconciliationCmd` 或 `RunDailyMerchantSettlementCmd`，结算调度明确使用 `Asia/Shanghai`。

结构测试证明依赖方向和入口薄度，不能证明任务一定按生产时间触发、重启后不丢失、跨实例只执行一次或人工复核按 SLA 完成。普通 `@Scheduled` 是触发器，不是 durable scheduler。

### 4. Inbound Integration Event：availability signal，不替代 Pull

`ChannelStatementAvailableIntegrationEventSubscriber` 的代码只提取事件中的标量和追踪身份，再发送 `ProcessAvailableChannelStatement` Command；statement 内容仍由应用路径通过 Pull provider 获取。对应 Flow 是静态的两节点一条边：`ChannelStatementAvailableIntegrationEvent → ProcessAvailableChannelStatementCmd.Request`。

因此 inbound event 证明的是“账单可能可用，启动一次处理”，不是“事件携带的数据就是权威账单”。重复 event、恢复和高 revision 的最终收敛仍依赖对账 identity、revision 幂等和 Pull 逻辑。

### 5. Outbound Event：稳定身份与 at-least-once handoff

`MerchantSettlementCompletedDomainEvent` 由 Settlement 成功事实驱动，subscriber 将其写入 durable event record，再通过 HTTP handoff 发送。`MerchantSettlementReferenceApplicationTests` 的 `PAY-AC-088` 证据覆盖两类失败：下游返回 non-2xx，以及首次响应超时。

断言重点是：同一个 `eventUuid`/`eventIdentity` 在重试中保持不变，delivery attempt 从 1 增加到 2，payload 不被改写；如果 subscriber 在同一 UoW 中失败，Settlement 成功事实和 event record 一起回滚，不留下孤儿事件。

这证明当前 reference 实现的 at-least-once 交接、稳定身份和事务共同回滚，不证明下游消费 exactly-once、通用 outbox、broker 广播、跨实例一致性或生产商户通知已经完成。

## Planned/not-built：要把“缺口”读成证据

`TraceabilityContractTests` 强制以下场景保持 `planned`，同时将相关 evidence 标记为 `not-built`：

### `PAY-AC-080`：商户隔离

当前目录和证据 guard 可以防止把 fixture 当作实现，但没有 tenant isolation 或生产数据隔离的行为证据。不能从 repository 查询范围或测试 merchant id 推断完整租户边界。

### `PAY-AC-081`：商户通知策略

已有消息和事件边界不等于通知服务能力。当前没有证明通知失败重试、截止策略、投递顺序、生产渠道或商户侧确认。

### `PAY-AC-084`：retired channel

配置中可以看到渠道状态，并不等于已经存在管理 API、发布流程、历史数据迁移和运行时切换的完整证据。

### `PAY-AC-086`：完整认证授权

领域测试只有最小 operator role guard，可拒绝不合适角色；这不等于生产认证、RBAC、双人复核、租户隔离或审计闭环。不能把 `operatorRole` 字段当成身份系统。

## 测试证明了什么，未证明什么

**已证明**

- HTTP 错误有稳定 status/code/message/details 边界，provider 原始诊断不会直接泄露。
- Scheduler 是薄 Command 入口；Integration Event 有明确 subscriber 和静态 Flow；Settlement outbound handoff 在失败/超时时保留稳定 event identity 并可重试。
- Evidence guard 能阻止 planned/not-built 场景被误标为 verified，并能定位相应 traceability/evidence 记录。

**未证明**

- 入口契约不证明业务状态机、金额不变量、JPA 持久化或跨聚合事务；这些要回看各模块主测试。
- 普通 `@Scheduled` 不证明 durable scheduler、重启恢复、跨节点抢占或 exactly-once。
- at-least-once event handoff 不证明下游 exactly-once、通用 Inbox/Outbox、消息 broker 语义或生产通知成功。
- Analyzer/Flow 只证明静态入口可达和文档可追踪，不证明运行态跨入口 stitching。
- 四个 planned 场景仍是当前缺口，不能通过增加 fixture、contract 或字段数量升级状态。

## 阅读检查点（非门槛）

1. `PAYMENT_NOT_FOUND` 的 details 为什么只允许出现 `paymentId`？
2. 为什么 scheduler 只发 Command，不能在其中直接查询 Repository？
3. inbound event 为什么只是 availability signal，而 Pull 才是 statement 权威？
4. `eventUuid` 在两次 handoff 中保持不变，证明了什么？
5. Analyzer Flow 的一条边为什么不能当作运行时 exactly-once 证据？
6. `PAY-AC-086` 的最小 operator guard 与完整认证授权之间缺了什么？
7. planned/not-built 场景为什么需要保留，而不是用局部测试“补齐”？

## 与 M7 全链路综合理解的连接

M6 把边界重新画清：业务事实由聚合和 application tests 证明，入口契约证明映射和安全表面，scheduler/event 证明交接形状，Analyzer/Flow 证明静态可达性，planned 场景证明当前仍有缺口。M7 将把这些证据重新放回同一条 Payment → Refund → Reconciliation → Settlement 轨迹，逐项确认上游事实如何被下游引用，以及每层证据不能跨越到哪里。
