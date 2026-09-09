# Payment Reference 教学上下文

> 本文件是后续教学会话的启动上下文。它把项目事实、阅读顺序和最简教学 SOP 固定下来；它不替代业务需求、自动化测试证据目录或人工验收指南，也不要求在本文件中开始逐场景讲解。

## 1. 教学目标

带着学习者通过代码阅读和自动化测试证据，理解并验收 `cap4k-reference-payment` 的完整资金事实链：

```text
Payment → Refund → Reconciliation → Merchant Settlement
```

教学目标不是背诵类名，而是能够说明：

- 每个业务模块解决什么问题；
- 关键状态、尝试、成功事实、复核、通知意图、结算资格和持久化结果如何关联；
- 一个 PAY-AC 场景由哪些测试证据支撑；
- 测试明确证明了什么，以及没有证明什么；
- `verified` 与 `planned/not-built` 的边界在哪里；
- reference 工程能力与生产系统能力不能如何混同。

## 2. 当前项目地图

- 验收场景共 53 个；当前 `verified` 49 个，`planned/not-built` 4 个。
- `planned/not-built` 场景为 `PAY-AC-080`、`PAY-AC-081`、`PAY-AC-084`、`PAY-AC-086`。
- 业务断言真源：[payment-scenarios.md](payment-scenarios.md)。
- 规则、生命周期和术语真源：[docs/requirements/business/](../business/)。
- 当前 projection、Command、Capability、Endpoint 和聚合设计：[design/](../../../design/)。
- 状态和 evidence 的机器真源：[traceability.yaml](../traceability.yaml)。
- 阅读式教学文章总索引：[payment-teaching-index.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-index.md)。
- 主测试与辅助测试的证据索引：[payment-test-evidence-catalog.md](payment-test-evidence-catalog.md)。
- 业务入口、Flow、环境边界和人工观察点：[payment-acceptance-guide.md](payment-acceptance-guide.md)。

## 3. 新会话阅读顺序

新会话不要先扫描整个仓库，按以下顺序进入教学：

1. 本文件，了解教学目标、边界和 SOP。
2. [payment-teaching-syllabus.md](payment-teaching-syllabus.md)，了解模块顺序和每课阅读路线。
3. [payment-teaching-index.md](/c:/Users/LD_moxeii/Documents/code/only-workspace/cap4k-reference-payment/docs/requirements/acceptance/payment-teaching-index.md)，选择当前模块的独立文章。
4. [payment-test-evidence-catalog.md](payment-test-evidence-catalog.md)，定位 PAY-AC、PAY-BR、主测试和辅助测试。
5. 根据当前模块，再读取 `payment-scenarios.md`、对应的 business 文档、design 文件和测试源码。
6. 只有在需要解释 HTTP、Scheduler、Integration Event 或 Analyzer 时，才进入对应入口和 Flow 文件。

## 4. 最简教学 SOP

每一课都采用阅读式推进，不把测试执行或事前预测设为门槛。

1. **教师给方向**：说明本课要理解的业务问题、它在全链路中的位置，以及预期结论。
2. **给出阅读路线**：直接列出文件、测试类、关键测试方法和阅读目的；不要求学习者自行寻找入口。
3. **带着看证据**：指出 Arrange、Act、Assert 和关键断言，解释它们对应的业务事实与边界。
4. **按需验证**：测试命令和已有报告只是可选材料。学习者可以运行、只看源码、只看已有结果，或暂时跳过执行。
5. **阅读后提问**：学习者完成定向阅读后，教师再围绕刚看过的内容进行少量、具体的反问和纠偏。
6. **收束本课**：教师给出准确总结，明确本课已经理解的内容、仍然存在的边界，并指出下一课的连接点。

侧边聊天可以用于深入追问某个类、方法、断言、状态或数据表；主会话继续按大纲推进，不需要把所有细节都塞进主线。

## 5. 教学边界

- 不要求学习者在没有上下文时先预测 Given / When / Then；需要时由教师先解释，再让学习者回看代码确认。
- 不要求每个 PAY-AC 都重新运行一次测试；已有测试结果、测试源码和证据目录可以作为阅读材料。
- 不把测试通过自动解释成生产渠道、生产通知、跨实例 exactly-once、完整认证授权或真实清算网络已完成。
- 不把 fixture、配置对象、结构测试或 Analyzer Flow 当成完整业务能力证明。
- `planned/not-built` 场景只讲清需求和当前缺口，不寻找不存在的实现，也不升级状态。
- 教学阶段不擅自修改生产代码；发现疑问时先记录为证据边界或待确认规则。
- 本阶段默认先做自动化证据和代码阅读；只有学习者明确要求时，才进入正式人工 HTTP 或端到端验收。

## 6. 证据阅读习惯

每次阅读主测试时，按以下顺序定位信息：

1. 业务前提：商户、订单、金额、币种、渠道、时间、尝试、账单或 review 前提；
2. 真实入口：Domain behavior、Command、Capability、MockMvc、Scheduler 或 Integration Event listener；
3. 状态变化：Payment、Attempt、Refund、Reconciliation、Settlement 及其终态；
4. 历史事实：receipt、success fact、review、disposition、confirmation、execution attempt 或 event record；
5. 结果回读：Query、HTTP response、JPA/H2 回读和唯一约束；
6. 证据边界：该测试没有覆盖的生产环境、并发规模、网络语义或待确认规则。

## 7. 新会话启动约定

新会话读取本文件和教学大纲后：

1. 先用简短内容说明教学路线，不逐条朗读 53 个场景；
2. 从大纲第一模块开始，或按学习者指定的模块开始；
3. 每次只推进一个清晰的阅读主题；
4. 不先要求预测，不把命令执行设为必须，不在没有请求时开始人工验收操作；
5. 每课结束时用几句话总结理解结果和下一步，不额外引入复杂进度系统。
