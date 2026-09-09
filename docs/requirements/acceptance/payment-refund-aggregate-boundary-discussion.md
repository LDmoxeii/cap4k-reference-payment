# Payment / Refund 聚合边界讨论稿

> **用途**：本文是一份可带给 DDD 研究者讨论的材料，不是对当前实现的最终裁决。文中把“项目约定”“cap4k 的机制能力”“当前代码事实”和“仍待讨论的设计判断”分开，避免把框架能做到的事直接等同于 DDD 上应该这样做的事。
>
> **讨论对象**：当前支付项目中的 `CreateRefundCmd`，以及 Payment 与 Refund 的聚合边界、命令边界和事务边界。
>
> **当前日期**：2026-09-03

---

## 一、先给出讨论摘要

本项目当前采用如下业务模型：

```text
一个 Payment 可以产生多笔 Refund；
每笔 Refund 只引用一个 Payment。

Payment 聚合负责：
  - 支付是否具备退款资格；
  - 可退款金额、已成功退款金额、已预占退款金额等预算不变量。

Refund 聚合负责：
  - 一笔退款自身的生命周期；
  - 退款 Attempt；
  - 渠道受理、渠道结果、通知收据、冲突和 review 等证据。
```

当前真正值得讨论的，不是“Refund 是否应该支持一笔退款对应多个 Payment”。现有模型并没有这个需求，代码和测试都表达的是 **Payment 1:N Refund**。

真正的争议是：

> **`CreateRefundCmd` 当前在一个 Handler 内直接写 Payment 和 Refund。这个命令应不应该被明确承认为一个跨聚合的 Workflow / Application Orchestrator 例外？还是应该改为：由一个只直接修改 Payment 的命令产生同步领域事件，再由嵌套的 Refund 命令完成 Refund 的创建和推进？**

讨论时需要同时承认以下三点：

1. **项目约定**： “一个命令只能操作一类聚合”限制的是命令处理器直接进行跨聚合同步写入；它不是说业务流程永远不能协调多个聚合。
2. **cap4k 机制**：cap4k 允许同步领域事件触发嵌套命令，嵌套命令复用外层 UoW，因此多个聚合可以在同一个数据库事务中一起提交或回滚。
3. **业务边界**：同一数据库事务的原子性，只能保证本地 Payment/Refund 状态的一起提交或回滚；它不能把本地数据库和外部退款渠道变成一个分布式原子事务。

本文的初步判断是：

- **Refund 继续作为独立聚合更符合当前业务事实**；
- **方案 A（显式声明 `CreateRefundCmd` 是 Workflow）和方案 B（同步领域事件 + 嵌套命令）都可能成立**；
- 方案 A 的关键是承认并治理这个例外，方案 B 的关键是证明事件确实表达了领域事实，而不是把隐式编排藏进事件处理器；
- 方案 C（把 Refund 改成 Payment 子实体）与当前的独立生命周期、证据归属和 Payment 1:N Refund 模型不匹配。

---

## 二、业务背景：一次退款不是 Payment 的一个字段变化

### 2.1 Payment 只代表支付意图及其支付生命周期

支付项目中的 Payment 聚合承担的是“一笔支付意图”的生命周期。它需要处理：

- 支付意图是否成立；
- Attempt 是否已发起；
- 渠道结果是否经过内部校验；
- 成功事实是否形成；
- 商户订单维度的唯一成功竞争；
- 后续是否具备退款资格及可退款预算。

这与“退款”不是同一个生命周期。退款通常发生在支付已经成功之后，且同一笔支付可能发生多次部分退款。例如：

```text
Payment P-001 金额 100.00
  ├─ Refund R-001：30.00，成功
  └─ Refund R-002：20.00，成功

Payment 的汇总：
  successfulRefundAmount = 50.00
  refundableAmount       = 50.00
```

因此，退款不能简单地理解为 Payment 上的一个 `refunded = true` 字段。

### 2.2 退款还需要独立处理外部事实

一笔退款有自己的外部交互和证据链，例如：

- 退款 Attempt 是否创建；
- 是否获得渠道受理；
- 渠道返回的 transaction id；
- 渠道异步结果及其通知 identity；
- 重复通知的 receive count；
- 冲突结果是否被保留；
- 是否形成成功事实；
- 是否需要 review；
- review 未解决前是否阻断自动结算。

这些信息并不是 Payment 的支付成功事实。把它们都塞入 Payment，会让 Payment 同时承担支付、退款、渠道通知、冲突审查和结算阻断等多个变化原因，扩大聚合的生命周期和并发竞争面。

---

## 三、术语和边界：本次讨论到底在约束什么

### 3.1 “一个命令只能操作一类聚合”的精确定义

本项目当前采用的解释是：

> **普通命令处理器不能直接进行跨聚合同步写入。**

这里的“操作”主要指：

- 在同一个 Handler 中加载多个聚合根；
- 直接调用多个聚合根的改变状态方法；
- 由该 Handler 负责跨聚合写入顺序、失败补偿和提交语义。

这个约定约束的是**命令处理器的直接写入责任**，并不否定以下能力：

- 应用层 Workflow 协调多个聚合；
- 聚合发布领域事件；
- 同步事件处理器触发嵌套命令；
- 多个嵌套命令复用同一个 UoW。

所以，争议不是“多个聚合能不能在同一业务流程中变化”，而是：

> **跨聚合协调应该以显式 Workflow 的形式出现，还是通过同步领域事件和嵌套命令重新分配直接写入责任？**

### 3.2 Workflow、领域事件和嵌套命令不是同一个概念

本文使用以下区分：

- **Workflow / Application Orchestrator**：应用层流程协调者，明确知道要依次驱动哪些聚合和外部端口。
- **领域事件**：描述某个领域事实已经发生，例如“Payment 退款预算已预占”。
- **流程信号**：为了驱动下一步动作而发出的消息。它可以很有用，但不一定是领域事实。
- **嵌套命令**：在当前命令执行期间，再发送一个命令；如果框架复用当前 UoW，它仍属于同一数据库事务上下文。
- **UoW**：Unit of Work。本文重点讨论的是本地数据库事务范围，不把它误称为外部渠道事务。

一个同步事件如果只是“请现在创建 Refund”，它可能更像流程信号；如果它表达的是“Payment 已经成功预占退款预算”，那它才更接近领域事实。这个语义差异是方案 B 必须回答的问题。

---

## 四、当前聚合设计

### 4.1 Payment 聚合

Payment 是支付聚合根。它不持有 `refunds` 集合，而是保存与退款预算有关的汇总状态，例如：

- `reservedRefundAmount`：已被进行中的退款预占的金额；
- `successfulRefundAmount`：已形成成功退款事实的金额；
- 可退款金额等派生结果。

Payment 的职责是维护“这笔支付最多还能退多少”的不变量，而不是保存每笔退款的完整历史。

代码证据：

- [Payment.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/build/generated/cap4k/main/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/payment/Payment.kt#L372) 附近可看到退款预算字段；
- [schema.sql](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/design/schema.sql#L50) 附近的 Payment 表也保存 `reserved_refund_amount`、`successful_refund_amount` 等汇总字段。

### 4.2 Refund 聚合

Refund 被标记为独立聚合根。它持有一个单值 `paymentId`，并保存自己的生命周期和证据，例如：

- 退款状态；
- 退款金额；
- Attempt；
- 渠道请求和渠道受理；
- 渠道 transaction id；
- 成功事实；
- 通知收据；
- 冲突结果；
- review 和结算阻断相关信息。

代码证据：

- [Refund.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/domain/build/generated/cap4k/main/kotlin/com/only4/cap4k/reference/payment/domain/aggregates/refund/Refund.kt#L24) 附近标记了 Refund 聚合根；
- 同一文件构造参数中只有一个 `paymentId`，并有退款自己独立的状态和证据字段；
- [schema.sql](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/design/schema.sql#L180) 附近的 Refund 表包含退款状态、Attempt、渠道、预占和成功事实等字段。

### 4.3 两者的关系

当前关系应表达为：

```text
Payment 1 ───── N Refund
Refund  N ───── 1 Payment
```

这不是：

```text
Refund 1 ───── N Payment
```

创建退款的接口只接收一个 `paymentId`：

- [CreateRefundEndpoint.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/contract/src/main/kotlin/com/only4/cap4k/reference/payment/contract/endpoints/refund/api/CreateRefundEndpoint.kt#L23) 附近的请求模型只有单个 Payment 引用。

测试也验证了同一 Payment 可以产生多笔独立退款：

- [PaymentReferenceApplicationTests.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/start/src/test/kotlin/com/only4/cap4k/reference/payment/PaymentReferenceApplicationTests.kt#L1209) 附近验证同一 Payment 先后成功退款 `30.00` 和 `20.00`。

---

## 五、当前实现现状：`CreateRefundCmd` 是直接跨聚合写入的

### 5.1 命令元数据已经暴露了跨聚合性质

[CreateRefundCmd.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/application/src/main/kotlin/com/only4/cap4k/reference/payment/application/commands/refund/create/CreateRefundCmd.kt#L36) 的设计元数据明确列出：

```kotlin
aggregates = ["Payment", "Refund", "MerchantChannelConfiguration"]
```

这至少说明作者已经意识到该命令同时依赖多个聚合类型。这里需要区分：

- `MerchantChannelConfiguration` 主要是渠道配置依赖；
- 当前真正被 Handler 直接改变状态的核心聚合是 `Payment` 和 `Refund`。

### 5.2 当前 Handler 的实际时序

当前实现可以概括为：

```text
1. 查询 Payment
2. 校验支付是否允许退款
3. 调用 payment.reserveRefund(...)
4. 创建 Refund
5. 在 Refund 上创建并启动 Attempt
6. 调用 StartChannelRefund 外部渠道端口
7. 渠道异常：拒绝 Refund Attempt，并释放 Payment 退款预占
8. 渠道拒绝：释放 Payment 退款预占
9. 渠道受理：在 Refund 上标记渠道已受理
10. 持久化本地状态
```

关键代码范围：

- [CreateRefundCmd.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/application/src/main/kotlin/com/only4/cap4k/reference/payment/application/commands/refund/create/CreateRefundCmd.kt#L88)–`#L112`：加载 Payment、校验和 `reserveRefund`；
- 同文件 `#L125`–`#L149`：创建 Refund；
- 同文件 `#L151`–`#L158`：创建/启动 Refund Attempt；
- 同文件 `#L159`–`#L168`：调用渠道退款；
- 同文件 `#L169`–`#L194`：异常或渠道拒绝时释放退款预占；
- 同文件 `#L203` 附近：渠道受理后标记 Refund。

因此，若“普通 CommandHandler 只能直接修改一个聚合”是不可破坏的约定，那么当前 `CreateRefundCmd` 确实是一个违反约定的现状。

### 5.3 这不是一个孤立的边界问题

[ConfirmRefundResultCmd.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/application/src/main/kotlin/com/only4/cap4k/reference/payment/application/commands/refund/result/ConfirmRefundResultCmd.kt) 也存在类似模式：

- 先处理 Refund 的渠道结果；
- 再更新 Payment 的退款预算汇总。

因此，讨论不能只问“CreateRefundCmd 要不要改”，还要问：

> **退款流程中哪些跨聚合变化是 Workflow 的合法职责，哪些应该由领域事件/嵌套命令分拆？**

---

## 六、cap4k 的 UoW 与同步领域事件事实

### 6.1 顶层命令和嵌套命令的事务关系

cap4k 的 [DefaultCommandSupervisor.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/command/impl/DefaultCommandSupervisor.kt#L15) 注释和实现表达了以下语义：

- 每个顶层 `Mediator.commands.send()` 进入 provider-owned 的 REQUIRED UoW；
- 如果当前已经存在 active UoW，嵌套命令复用当前上下文；
- 命令成功完成后由外层 UoW 统一提交；
- 异常向外传播，外层事务可以整体回滚；
- 两个彼此独立、先后执行的顶层命令，不会因为业务上相关就自动共享同一个 UoW。

所以，下列结构在 cap4k 中是有机制基础的：

```text
外层 Payment Command
  → 修改 Payment
  → 发布同步领域事件
      → 事件处理器发送嵌套 Refund Command
  → 嵌套 Refund Command 复用外层 UoW
  → Payment 与 Refund 一起提交或一起回滚
```

但必须准确表述为：

> **多个嵌套命令可以共享外层 UoW；不是所有顶层命令天然共享事务。**

### 6.2 同步领域事件的执行语义

cap4k 的 [DefaultDomainEventSupervisor.kt](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/impl/DefaultDomainEventSupervisor.kt#L20) 附近说明即时、非持久化事件会同步发布；其 `release`/`publishLocal` 逻辑会在当前 UoW 稳定化阶段分发事件。

当前可据此确认：

- 同步事件处理器可以在当前命令的 UoW 中运行；
- 事件处理器触发的嵌套命令可以复用这个 UoW；
- 事件处理器异常不会被悄悄吞掉，可以向外传播并影响外层命令结果。

这证明的是 **cap4k 有能力支持方案 B**，但不证明 **方案 B 在 DDD 语义上一定优于方案 A**。

### 6.3 UoW 的保证边界

如果 Payment 退款预算占用和 Refund 创建都在同一个本地 UoW 内，则可以要求：

```text
本地成功：Payment 预占 + Refund 创建一起提交；
本地失败：Payment 预占 + Refund 创建一起回滚。
```

但这不能推出：

```text
渠道已接受退款 + 本地数据库状态一定一起提交；
```

外部渠道调用已经产生副作用时，本地事务随后失败，可能出现：

```text
渠道认为退款已受理；
本地 Payment/Refund 状态却回滚；
```

这属于外部副作用与本地状态之间的一致性问题，通常需要幂等、重试、补偿、对账或人工 review 处理，不能靠数据库 UoW 单独解决。

---

## 七、核心争点：Workflow 例外，还是同步事件 + 嵌套命令

### 7.1 方案 A：明确承认 `CreateRefundCmd` 是 Workflow 例外

保留当前总体流程，但重新命名和标注其架构角色：

```text
CreateRefundWorkflow / CreateRefundOrchestrator
  → 读取并修改 Payment
  → 创建并推进 Refund
  → 调用渠道端口
  → 处理跨聚合失败语义
```

这种方案的辩护是：

- 退款创建本来就是一个跨聚合业务流程；
- Payment 的退款预算和 Refund 的创建存在一次业务操作中的原子要求；
- 将协调代码显式放在 Application Workflow 中，比把协调隐藏到多个事件处理器里更容易阅读、审计和追踪；
- Handler 内的跨聚合写入不是“普通单聚合命令”，而是一个显式、受治理的例外。

如果选择方案 A，至少应补充：

1. 明确的命名，例如 `CreateRefundWorkflow`，不要伪装成普通单聚合 CommandHandler；
2. 元数据或架构规则，明确该类是允许跨聚合协调的例外；
3. 跨聚合本地事务的测试证据；
4. 外部渠道调用成功后本地失败时的重试、幂等、补偿、对账和 review 策略；
5. 失败分支中 Payment 预算释放和 Refund 状态变化的可审计事实。

方案 A 的风险是：如果“Workflow 例外”没有明确边界，久而久之任何 Handler 都可以声称自己是流程编排器，“一个命令一类聚合”就失去可执行性。

### 7.2 方案 B：单聚合命令 + 同步领域事件 + 嵌套命令

一种可能的重新划分是：

```text
Payment 命令
  → 只加载并修改 Payment
  → Payment 产生“退款预算已预占”领域事实
  → 同步事件处理器发送 CreateRefundCmd
  → CreateRefundCmd 只创建/修改 Refund
  → 嵌套命令复用外层 UoW
```

这种方案的辩护是：

- 普通 CommandHandler 直接写入的聚合类型保持单一；
- Payment 的预算不变量仍由 Payment 自己维护；
- Refund 的状态机仍由 Refund 自己维护；
- cap4k 可以在同一个本地 UoW 中保证两者一起提交或回滚。

但方案 B 不是天然更 DDD，必须回答：

1. “退款预算已预占”是已经发生的领域事实，还是“请创建 Refund”的流程信号？
2. 如果 Refund 创建失败，Payment 预占是否应该回滚？同步事件异常是否必然让外层命令失败？
3. 事件处理器中再发送命令，是否只是把显式 Workflow 隐藏成了隐式 Workflow？
4. 处理顺序、失败传播、幂等和可观测性是否仍然足够清晰？
5. 外部 `StartChannelRefund` 应该在 Refund 命令内调用，还是由更高层 Workflow 管理？

方案 B 的最大风险是“隐式编排”：代码表面上每个 Handler 只写一个聚合，但实际业务依赖被分散到事件订阅关系中，阅读者需要追踪更多间接调用才能理解一次退款的完整时序。

### 7.3 方案 C：把 Refund 改成 Payment 的子实体

这个方案要求 Payment 聚合直接拥有 Refund 集合，并由 Payment 统一负责每笔退款的状态和证据。

它在当前模型下的问题是：

- 当前业务是一个 Payment 对多 Refund，而不是一个 Refund 对多 Payment；
- Refund 有独立的 Attempt、渠道回调、通知收据、冲突、review 和结算阻断生命周期；
- 每次退款回调都可能迫使系统加载和锁定整个 Payment 聚合；
- Payment 会保存所有退款的细节，聚合体积和并发竞争面都会扩大；
- Payment 的核心不变量本来只需要维护退款预算汇总，不需要持有退款证据全集。

因此，不能仅用“Payment 1:N Refund”就断言 Refund 必须独立，也不能仅用“Refund 有 paymentId”就断言它必须是子实体。真正的判断依据应是：

> **哪些不变量必须在同一个一致性边界内？哪些状态和证据拥有独立生命周期？并发写入应该在哪个聚合上竞争？**

在当前证据下，Refund 独立聚合的解释力更强。

---

## 八、为什么 Refund 独立聚合是可辩护的

### 8.1 辩护重点不是“一 Refund 对多 Payment”

需要明确纠正一个可能的误解：

> Refund 独立聚合不是为了支持“一笔 Refund 对多个 Payment”。当前模型没有这个需求。

当前关系是：

```text
一个 Payment 可以有多笔 Refund；
每笔 Refund 只属于一个 Payment。
```

### 8.2 Payment 维护预算，Refund 维护单笔生命周期

这是一种职责分离：

```text
Payment：
  “总共还能退多少？”

Refund：
  “这一笔退款现在处于什么状态？经历了哪些渠道交互和证据变化？”
```

两者之间存在跨聚合业务不变量：

```text
successfulRefundAmount
+ reservedRefundAmount
+ 新退款金额
≤ Payment 金额
```

Payment 负责维护预算边界；Refund 负责维护单笔退款的过程性事实。Workflow 或事件协调层负责把这两个聚合的变化放进一次业务流程中。

### 8.3 Refund 的证据会继续增长，而不是一次性完成

Refund 不只是一个金额记录。它会随着渠道交互继续积累：

- Attempt 启动；
- 渠道受理；
- 结果通知；
- 重复通知；
- 矛盾通知；
- 成功事实；
- review；
- 结算阻断和解除。

这些事实具有独立的审计价值和生命周期。如果 Refund 是 Payment 的子实体，Payment 就会成为所有退款异步事件的共同写入热点。

### 8.4 独立聚合降低不必要的并发竞争

同一 Payment 可能存在多笔退款，甚至不同退款处于不同状态：

```text
R-001 正在等待渠道结果；
R-002 正在接收重复通知；
R-003 已进入 review。
```

如果所有变化都必须加载并锁定 Payment 整体，退款之间会产生不必要的竞争。将 Refund 独立出来，可以让每笔退款的证据变化在自己的聚合边界内演进，而把 Payment 的写入缩小为必要的预算变化。

### 8.5 独立聚合不等于不需要跨聚合协调

Refund 独立聚合并不意味着 Payment 与 Refund 完全没有事务关系。恰恰相反：

- 创建退款时，Payment 可能需要预占预算；
- 渠道结果确认时，Payment 可能需要把预占转为成功退款金额；
- 失败或取消时，Payment 可能需要释放预占。

这说明的是“两个聚合之间存在业务流程协调”，不是“两个聚合必须合并成一个聚合”。聚合边界和 Workflow 边界是两个不同问题。

---

## 九、测试和代码证据能证明什么，不能证明什么

### 9.1 已有证据支持的结论

当前代码和测试至少支持：

1. `CreateRefundCmd` 的 Handler 当前直接修改 Payment 和 Refund；
2. Refund 数据模型持有单个 `paymentId`；
3. Payment 不持有 Refund 集合，而是保存退款预算汇总；
4. 同一 Payment 可以先后成功多笔 Refund；
5. cap4k 支持同步本地领域事件和嵌套命令复用外层 UoW；
6. Payment 预算与 Refund 创建可以被设计为同一本地事务中的状态变化。

### 9.2 现有证据没有自动证明的结论

不能因为测试通过，就直接推出：

- 当前 Handler 已经符合“一个命令只能操作一类聚合”；
- 方案 A 或方案 B 在架构上一定优于另一方案；
- 外部退款渠道和本地数据库具备分布式原子性；
- 所有并发场景下的退款预算都已经被证明安全；
- 失败重试、渠道超时、重复回调、人工 review 和对账策略已经完整；
- 将 Refund 改成 Payment 子实体一定更简单或更一致。

测试在这里的角色，是提供“当前实现做了什么”的证据，不是替研究者完成聚合边界的规范性判断。

---

## 十、建议带给 DDD 研究者的具体问题

### 10.1 关于命令边界

1. “一个命令只能操作一类聚合”是否是一条绝对规则，还是允许显式、可审计的 Workflow 例外？
2. 如果允许例外，例外应通过什么方式表达：命名、类型、元数据、包结构、静态检查，还是架构测试？
3. `CreateRefundCmd` 是否应该保留 Command 名称，还是应该改名为 `CreateRefundWorkflow` / `CreateRefundOrchestrator`？
4. `ConfirmRefundResultCmd` 是否也应纳入同一 Workflow 模式，还是应拆成多个单聚合命令？

### 10.2 关于同步领域事件

1. “退款预算已预占”是领域事实，还是驱动下一步 Refund 创建的流程信号？
2. 如果同步事件处理器失败，外层 Payment 命令是否必须失败并回滚？
3. 事件处理器触发嵌套命令后，如何保证调用顺序、幂等和可观测性？
4. 方案 B 是否真正降低了职责耦合，还是只是把 Workflow 从显式代码转移到了隐式订阅关系？

### 10.3 关于聚合和不变量

1. “退款累计不超过支付金额”是否应由 Payment 的汇总字段维护，还是必须把所有 Refund 放进同一个聚合？
2. 在并发退款下，Payment 的预算预占是否是唯一需要串行化的部分？
3. Refund 的 Attempt、receipt、conflict、review 是否具有足够独立的生命周期，足以支持独立聚合？
4. 如果把 Refund 作为 Payment 子实体，所有退款异步回调是否都必须竞争 Payment 聚合？这是可接受的吗？

### 10.4 关于事务和外部副作用

1. “Payment 预算占用 + Refund 创建”需要保证到同一个本地数据库事务，还是允许最终一致？
2. 同一个 UoW 的边界是否只保证本地状态一起提交/回滚？
3. 渠道已受理而本地事务失败时，系统采用什么补偿、重试、幂等、对账或 review 策略？
4. 外部渠道调用是否应该从聚合命令中移出，由显式 Workflow 负责？

---

## 十一、可供讨论的评估标准

无论最后选择方案 A 还是方案 B，都建议用以下标准评估，而不是只看 Handler 行数：

### 11.1 语义清晰度

阅读者能否从代码中直接看出：

- 哪个聚合拥有哪个不变量；
- 哪个步骤是领域事实；
- 哪个步骤是流程编排；
- 外部渠道调用发生在什么阶段。

### 11.2 事务正确性

能否证明并测试：

- Payment 预占和 Refund 创建在本地失败时一起回滚；
- 嵌套命令确实复用外层 UoW；
- 两个独立顶层命令不会被误认为共享事务。

### 11.3 外部副作用可恢复性

能否说明：

- 渠道超时怎么办；
- 渠道已受理、本地提交失败怎么办；
- 重试是否幂等；
- 重复回调如何处理；
- 矛盾结果如何保留并进入 review；
- 结算阻断如何解除。

### 11.4 并发和锁竞争

能否说明：

- 多笔 Refund 并发创建时，Payment 预算如何串行化；
- 同一 Refund 的回调是否不必要地锁住整个 Payment；
- Payment 是否会因为保存所有退款历史而成为高竞争聚合。

### 11.5 可审计性和可观测性

能否从日志、领域事件、命令和持久化事实中复原：

```text
谁预占了预算；
谁创建了 Refund；
谁调用了渠道；
谁接受了渠道结果；
哪一步失败；
哪一笔外部事实导致了 review。
```

---

## 十二、讨论时可以直接使用的立场陈述

可以用下面这段作为讨论开场，而不必先争论实现细节：

> 我们目前并不讨论“一笔 Refund 对多个 Payment”的需求。现有业务和代码表达的是一个 Payment 可以有多笔 Refund，每笔 Refund 只属于一个 Payment。Payment 维护退款预算，Refund 维护单笔退款的 Attempt、渠道结果、通知收据、冲突和 review 等独立证据。
>
> 当前 `CreateRefundCmd` 在一个 Handler 内直接写 Payment 和 Refund，这与“普通命令处理器只能直接修改一类聚合”的约定存在张力。cap4k 又确实支持同步领域事件触发嵌套命令，并复用外层 UoW，因此技术上可以把职责重新拆分，同时保持本地事务原子性。
>
> 所以我们真正想讨论的是：当前流程应不应该被明确承认为一个跨聚合 Workflow 例外；如果不承认，应该如何使用同步领域事件和嵌套命令重新划分职责，而不把显式编排隐藏成难以追踪的隐式编排。无论采用哪种方案，Refund 独立聚合的辩护重点都是独立生命周期、独立证据和并发隔离，而不是支持一笔退款对应多个支付。

---

## 十三、当前待形成的决策

这次讨论最好最终形成以下几条明确决策，而不是只留下“看情况”：

1. **命令规则**：普通 CommandHandler 是否禁止直接跨聚合同步写入？
2. **例外机制**：如果允许 Workflow 例外，如何命名、标记、审计和测试？
3. **事件语义**：同步事件是领域事实还是流程信号？失败是否回滚外层 UoW？
4. **事务边界**：Payment 预算和 Refund 创建是否必须同一数据库事务提交/回滚？
5. **外部副作用**：渠道调用后的本地失败、重试、幂等、补偿、对账和 review 由谁负责？
6. **聚合边界**：是否继续保持 Payment 1:N Refund；哪些字段和不变量分别归属哪一方？
7. **一致性证据**：需要哪些架构测试、事务测试、并发测试和外部副作用测试，才能证明选择不是口头约定？

在这些问题得到回答之前，不宜简单地把当前实现判定为“违反 DDD”，也不宜简单地把同步领域事件判定为“更纯粹”。更准确的结论是：

> **当前实现明确存在跨聚合协调；当前聚合拆分有充分业务依据；剩下的核心设计选择，是把协调显式化为受治理的 Workflow，还是通过同步事件/嵌套命令重新分配直接写入职责。**
