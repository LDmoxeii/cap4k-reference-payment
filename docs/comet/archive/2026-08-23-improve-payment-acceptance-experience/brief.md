# Outcome

把 `cap4k-reference-payment` 已完成的支付、退款、对账、商户结算和可靠事件能力，整理成验收者可以直接执行和理解的一体化验收体验：

- `payment-scenarios.md` 继续作为稳定的业务断言真源，但不再要求验收者自行跨仓库搜索实现；
- 新增一份人类可读的验收指南，将每个 `PAY-AC-*` 场景映射到状态、业务规则、生命周期、Design、聚合、Behavior、Command/Capability、HTTP/Time/Event 入口、Analyzer Flow、精确测试方法和可观察证据；
- 为核心聚合行为和复杂应用编排补充中文解释性注释，重点说明业务不变量、幂等、状态收敛、证据保留和不可回退原因；
- 用户可见 HTTP 错误和新写入的查询/审计摘要使用中文，稳定英文错误码和机器语义保持不变，内部数据库/provider 原始异常不再通过 API 泄漏；
- 文档、代码、测试和现有能力在一个 Native change、一次 Build/Verify 迭代目标中完整交付，不拆分为阶段或多个 child change。

# Scope

## Source coverage

用户明确指定 [payment-scenarios.md](../../../requirements/acceptance/payment-scenarios.md) 作为本次需求来源。该文件已完整读取；以下每个当前有效场景单元都保持原有 Given/When/Then 业务语义，并映射到完整目标规格和至少一个验收项。

| 单元 | 来源定位 | 读取状态 | 保留语义与本次处理 | Spec 位置 | 验收 | 覆盖状态 |
|---|---|---|---|---|---|---|
| SRC-000 | 文件标题与使用说明 | complete | 保留“只描述可观察业务前提、动作和结果”；补充如何进入验收指南、状态真源和阅读方式 | 3.1 | A1 | covered |
| SRC-001 | `PAY-AC-001` 首次创建支付 | complete | 原业务断言不变；增加支付架构、执行与观察导航 | 3.2 | A2 | covered |
| SRC-002 | `PAY-AC-002` 相同内容重复创建 | complete | 原业务断言不变；增加幂等证据导航 | 3.2 | A2 | covered |
| SRC-003 | `PAY-AC-003` 幂等键冲突 | complete | 原业务断言不变；增加稳定错误码和中文消息观察点 | 3.2, 5 | A2, A9 | covered |
| SRC-004 | `PAY-AC-004` 渠道成功通知 | complete | 原业务断言不变；增加 callback、聚合结果和 receipt 导航 | 3.2 | A2 | covered |
| SRC-005 | `PAY-AC-005` 重复成功通知 | complete | 原业务断言不变；增加 notification identity、receive count 和重复证据导航 | 3.2 | A2 | covered |
| SRC-006 | `PAY-AC-006` 未验证通知 | complete | 原业务断言不变；增加拒绝摘要、状态不推进和中文错误观察点 | 3.2, 5 | A2, A9 | covered |
| SRC-007 | `PAY-AC-007` 支付到期关闭 | complete | 原业务断言不变；增加 expiry scheduler、CLOSED 与幂等扫描导航 | 3.2 | A2 | covered |
| SRC-008 | `PAY-AC-008` 到期但结果未知 | complete | 原业务断言不变；增加 RESULT_UNKNOWN、review case 和后续收敛导航 | 3.2 | A2 | covered |
| SRC-009 | `PAY-AC-009` 关闭后的迟到成功 | complete | 原业务断言不变；增加迟到证据、held review 与裁决导航 | 3.2 | A2 | covered |
| SRC-010 | `PAY-AC-010` 两个尝试同时成功 | complete | 原业务断言不变；增加成功一次、冲突 review 和 fee/intent 唯一性导航 | 3.2 | A2 | covered |
| SRC-011 | `PAY-AC-011` 已成功订单不得重复收款 | complete | 原业务断言不变；增加 merchant-order success identity 与并发冲突导航 | 3.2, 5 | A2, A9 | covered |
| SRC-012 | `PAY-AC-012` 无效金额或币种被拒绝 | complete | 原业务断言不变；增加输入错误中文消息及未占用幂等键观察点 | 3.2, 5 | A2, A9 | covered |
| SRC-013 | `PAY-AC-013` 已创建支付的金额不可修改 | complete | 原业务断言不变；增加幂等内容冲突导航 | 3.2 | A2 | covered |
| SRC-014 | `PAY-AC-014` 费用规则使用成功时快照 | complete | 原业务断言不变；增加 Payment fee snapshot 到 SettlementLine 的追踪导航 | 3.2, 3.5 | A2, A5 | covered |
| SRC-015 | `PAY-AC-015` 成功后的失败结果不回退支付 | complete | 原业务断言不变；增加 conflict receipt、review blocker 和成功事实不可回退导航 | 3.2 | A2 | covered |
| SRC-016 | `PAY-AC-016` 渠道受理不等于支付成功 | complete | 原业务断言不变；增加 Gateway ACCEPTED 与最终 callback 分界导航 | 3.2 | A2 | covered |
| SRC-017 | `PAY-AC-017` 支付成功后不再发起新尝试 | complete | 原业务断言不变；增加状态冲突错误观察点 | 3.2, 5 | A2, A9 | covered |
| SRC-020 | `PAY-AC-020` 全额退款 | complete | 原业务断言不变；增加 Payment 预算与 Refund 聚合原子路径导航 | 3.3 | A3 | covered |
| SRC-021 | `PAY-AC-021` 多次部分退款 | complete | 原业务断言不变；增加 reserved/successful/refundable 观察点 | 3.3 | A3 | covered |
| SRC-022 | `PAY-AC-022` 超额退款被拒绝 | complete | 原业务断言不变；增加预算冲突 code/message 导航 | 3.3, 5 | A3, A9 | covered |
| SRC-023 | `PAY-AC-023` 并发退款防超退 | complete | 原业务断言不变；增加真实双事务与 409 证据导航 | 3.3, 5 | A3, A9 | covered |
| SRC-024 | `PAY-AC-024` 退款失败释放占用 | complete | 原业务断言不变；增加跨聚合回滚和预算释放导航 | 3.3 | A3 | covered |
| SRC-025 | `PAY-AC-025` 退款结果待确认 | complete | 原业务断言不变；增加 scheduler、REVIEW_REQUIRED 与继续占用导航 | 3.3 | A3 | covered |
| SRC-026 | `PAY-AC-026` 重复退款申请 | complete | 原业务断言不变；增加 merchant refund number 幂等导航 | 3.3 | A3 | covered |
| SRC-027 | `PAY-AC-027` 非成功支付不得退款 | complete | 原业务断言不变；增加资格拒绝中文消息导航 | 3.3, 5 | A3, A9 | covered |
| SRC-028 | `PAY-AC-028` 超过退款期限的申请被拒绝 | complete | 原业务断言不变；增加 succeededAt + refundWindowDays 观察点 | 3.3, 5 | A3, A9 | covered |
| SRC-029 | `PAY-AC-029` 成功退款后的失败结果不回退退款 | complete | 原业务断言不变；增加 receipt 冲突和成功预算不可回退导航 | 3.3 | A3 | covered |
| SRC-040 | `PAY-AC-040` 完全匹配 | complete | 原业务断言不变；增加 statement/platform facts、run/item 导航 | 3.4 | A4 | covered |
| SRC-041 | `PAY-AC-041` 平台单边 | complete | 原业务断言不变；增加 PLATFORM_ONLY 与结算阻断导航 | 3.4 | A4 | covered |
| SRC-042 | `PAY-AC-042` 渠道单边 | complete | 原业务断言不变；增加 CHANNEL_ONLY 与授权 confirmation 导航 | 3.4 | A4 | covered |
| SRC-043 | `PAY-AC-043` 金额差异 | complete | 原业务断言不变；增加双方快照和 AMOUNT_MISMATCH 导航 | 3.4 | A4 | covered |
| SRC-044 | `PAY-AC-044` 状态差异收敛 | complete | 原业务断言不变；增加 disposition/confirmation 与不改写原始事实导航 | 3.4 | A4 | covered |
| SRC-045 | `PAY-AC-045` 对账重跑不重复 | complete | 原业务断言不变；增加 statement revision、effective run 与并发唯一性导航 | 3.4 | A4 | covered |
| SRC-046 | `PAY-AC-046` 未决差异阻断完成 | complete | 原业务断言不变；增加 completion blocker 中文摘要导航 | 3.4, 5 | A4, A10 | covered |
| SRC-047 | `PAY-AC-047` 差异处置不改写原始证据 | complete | 原业务断言不变；增加 append-only disposition/confirmation 导航 | 3.4 | A4 | covered |
| SRC-060 | `PAY-AC-060` 正常结算计算 | complete | 原业务断言不变；增加 lines、gross/fee/net 恒等式导航 | 3.5 | A5 | covered |
| SRC-061 | `PAY-AC-061` 未决差异不进入结算 | complete | 原业务断言不变；增加 transaction-level exclusion 与 blocker 摘要导航 | 3.5, 5 | A5, A10 | covered |
| SRC-062 | `PAY-AC-062` 防止重复结算 | complete | 原业务断言不变；增加 scope/source fact ownership 与并发导航 | 3.5 | A5 | covered |
| SRC-063 | `PAY-AC-063` 结算执行成功 | complete | 原业务断言不变；增加 execution attempt、receipt、completed fact 导航 | 3.5 | A5 | covered |
| SRC-064 | `PAY-AC-064` 结算执行结果未知 | complete | 原业务断言不变；增加禁止重付、人工核对和中文状态冲突消息导航 | 3.5, 5 | A5, A9 | covered |
| SRC-065 | `PAY-AC-065` 结算结果矛盾 | complete | 原业务断言不变；增加 conflict receipt 与不回退终态导航 | 3.5 | A5 | covered |
| SRC-066 | `PAY-AC-066` 负结算额 | complete | 原业务断言不变；增加 NEGATIVE_REVIEW_REQUIRED 与不调用 transfer 导航 | 3.5, 5 | A5, A9 | covered |
| SRC-067 | `PAY-AC-067` 结算确认后构成冻结 | complete | 原业务断言不变；增加 composition snapshot 和后续 adjustment 导航 | 3.5 | A5 | covered |
| SRC-068 | `PAY-AC-068` 同一周期不得存在两份有效结算单 | complete | 原业务断言不变；增加 effective ownership/replacement chain 导航 | 3.5 | A5 | covered |
| SRC-080 | `PAY-AC-080` 商户数据隔离 | complete | 保持 `planned`；只导航到 gap/not-built evidence，不伪装为可运行能力 | 3.6 | A6 | covered |
| SRC-081 | `PAY-AC-081` 商户通知失败重试 | complete | 保持 `planned`；明确现有 Settlement Integration Event receiver 不是生产商户通知 | 3.6 | A6 | covered |
| SRC-082 | `PAY-AC-082` 资金事实更正留痕 | complete | 原业务断言不变；增加 revision/disposition/confirmation 历史导航 | 3.4, 3.6 | A4, A6 | covered |
| SRC-083 | `PAY-AC-083` 支付全链路追踪 | complete | 原业务断言不变；增加 composition test 和跨聚合 identity 轨迹导航 | 3.6 | A6 | covered |
| SRC-084 | `PAY-AC-084` 退役渠道不再参与新支付 | complete | 保持 `planned`；配置聚合存在不等于场景完成 | 3.6 | A6 | covered |
| SRC-085 | `PAY-AC-085` 业务时区决定日界线 | complete | 原业务断言不变；增加 Asia/Shanghai 半开区间与 Instant 观察点 | 3.4, 3.5, 3.6 | A4, A5, A6 | covered |
| SRC-086 | `PAY-AC-086` 敏感人工动作必须授权并留痕 | complete | 保持 `planned`；领域 fixture 不能升级为完整认证授权能力 | 3.6 | A6 | covered |
| SRC-087 | `PAY-AC-087` 账单可用事件与权威拉取收敛 | complete | 原业务断言不变；增加 Push/Pull/Scheduler/Rerun 收敛导航 | 3.4, 3.6 | A4, A6 | covered |
| SRC-088 | `PAY-AC-088` 结算完成事件原子提交并保持稳定身份重试 | complete | 原业务断言不变；增加可靠事件记录、失败重试和稳定 identity 导航 | 3.5, 3.6 | A5, A6 | covered |

配套事实来源已完整审查：`traceability.yaml`、requirements README、lifecycle、rules、current projection、Design manifests、Analyzer flows、README、核心代码和测试。它们作为验收导航与实现参考，不改变 `payment-scenarios.md` 的业务真源角色。

## Included work

- 新增 `docs/requirements/acceptance/payment-acceptance-guide.md`，作为人工验收主入口。
- 在 `payment-scenarios.md` 顶部增加简短的“如何验收”、状态图例和 guide/traceability 链接；不重写 53 个 Given/When/Then。
- 更新 `docs/requirements/README.md` 和项目 `README.md` 的验收导航，使 guide 可发现。
- Guide 覆盖环境、启动、复位策略、验收顺序、五个场景族、53 个场景状态、规则/lifecycle、Design、聚合、Behavior、Command/Capability、入口、Flow、精确测试方法、观察点与非目标。
- Guide 明确 H2 内存库、Fake Provider、普通 Scheduler、测试 Receiver、Analyzer 静态入口 Flow 的边界；没有公共 reset API 时只说明重启或使用新业务 identity，不伪造已有能力。
- 为 Payment、Refund、ReconciliationBatch、MerchantSettlement 核心 Behavior 的复杂公共行为和关键反直觉私有分支补充中文 KDoc/块注释。
- 为复杂 Application Command 编排补充中文阶段说明，至少覆盖支付结果确认、支付复核裁决、退款创建/结果确认、对账运行/重跑/差异处置、结算准备/确认/执行/结果裁决，以及事件收敛和可靠事件发布边界。
- 建立显式业务错误模型或等价机制，使稳定 `code` 与中文 `message` 解耦；消除从 `Throwable.message` 解析 `REVIEW_*` code 的实现。
- HTTP 错误响应保留 `status/code/message`，可增加可选、安全、结构化 `details`；所有现有稳定 code 和 HTTP status 保持不变。
- 中文化所有 HTTP 可达的应用/领域业务错误，以及新写入并经查询暴露的 rejection/conflict/review/blocking/failure summary。
- 数据库、框架、provider 原始异常仅进入日志/开发诊断；API 返回稳定 code、中文安全消息，不包含 SQL、类名、堆栈或 raw cause。
- 更新和新增单元、HTTP、集成、静态守卫测试，证明导航完整、code 稳定、中文消息、安全映射、审计文本与业务回归。

# Non-goals

- 不实现 `PAY-AC-080/081/084/086`；它们继续保持 `planned/not-built`。
- 不改变任何现有 `PAY-AC-*` 的 Given/When/Then、业务状态机、金额公式、幂等规则、聚合边界或 Integration Event 语义。
- 不实现生产商户通知、tenant isolation、完整 RBAC、真实支付/退款/账单/银行协议、broker、generic Inbox/Outbox、persistent scheduler、跨实例 exactly-once、周结或负净额追偿。
- 不新增管理 UI、测试数据管理 API、fixture/reset HTTP Endpoint；人工重复验收通过重启 H2 应用或使用新业务 identity 完成。
- 不翻译稳定错误码、enum、event name、JSON 字段、identity discriminator、matching basis、渠道 raw result/code、用户输入的 reason/evidence/follow-up 或日志检索 key。
- 不迁移历史数据库中已经存在的英文 summary；本次保证 change 后新写入的本地生成 summary 为中文。
- 不通过逐行注释、getter 注释或代码语句翻译制造噪音；只解释业务目的、不变量、幂等/并发边界、证据保留和副作用。
- 不把 Analyzer 独立入口 Flow 描述成跨入口端到端 stitching、运行时状态机或 exactly-once 证明。

# Acceptance examples

- **A1 验收入口可独立使用**：从 `docs/requirements/acceptance/payment-scenarios.md` 或 requirements/project README 一跳进入 `payment-acceptance-guide.md`；Guide 显示 53 个场景、状态图例、49 个 verified 与 4 个 planned，状态与 `traceability.yaml` 一致，且没有把 `PAY-AC-080/081/084/086` 写成已实现。
- **A2 支付场景导航完整**：`PAY-AC-001..017` 每个场景都能定位到对应规则/lifecycle、Payment owned graph、关键 Behavior/Command/Capability、HTTP 或 Time 入口、相关 Flow、精确测试方法和可观察结果；AC-003/006/011/012/017 同时给出稳定 code 与中文 message 观察点。
- **A3 退款场景导航完整**：`PAY-AC-020..029` 每个场景都能定位到 Payment 退款预算权威、Refund owned graph、Command/Capability/HTTP/Time 入口、精确测试方法和 reserved/successful/refundable、attempt/receipt、事务与并发观察点。
- **A4 对账场景导航完整**：`PAY-AC-040..047/082/085/087` 能定位到 statement/platform facts、ReconciliationBatch owned graph、revision/current-effective-run、append-only disposition/confirmation、HTTP/Time/Integration Event 入口、Flow、精确测试方法和 completion blocker；Guide 明确 event 只是 availability signal、账单正文仍由 Pull 获取。
- **A5 结算场景导航完整**：`PAY-AC-060..068/014/083/085/088` 能定位到 candidate/line/fee/net、composition freeze、execution/receipt/review、replacement ownership、Domain/Integration Event、可靠记录、HTTP/Time 入口、Flow、精确测试方法和同 UoW/稳定 identity 观察点。
- **A6 横切与 planned 边界诚实**：Guide 为 `PAY-AC-080/081/084/086` 只展示 gap/not-built evidence 和“不应看到什么误宣称”；为 `082/083/085/087/088` 提供可执行证据；明确 Fake Provider、H2、测试 Receiver、普通 Scheduler、at-least-once 与 Analyzer 静态 Flow 边界。
- **A7 验收可执行与可复位**：Guide 提供 Java/Composite Build 前提、构建/测试/启动命令、H2 生命周期、固定 fixture、推荐冒烟顺序、无 reset API 时的重启/新 identity 策略，以及 focused/full/Analyzer/traceability 检查命令；所有仓库链接均为相对可移植路径，不提交本机绝对路径。
- **A8 复杂业务逻辑可理解**：Payment、Refund、ReconciliationBatch、MerchantSettlement 核心 Behavior 和列入范围的复杂 Command/事件编排具有中文解释性注释，覆盖业务目的、关键不变量、状态/版本收敛、幂等与并发、证据为何追加而不覆盖、结算资格和事件副作用；静态审查确认没有逐行复述式注释或能力夸大。
- **A9 稳定错误码与中文 HTTP 消息**：400/404/409 及 payment/refund/reconciliation/settlement/review 的现有稳定 `code` 和 HTTP status 不变；HTTP `message` 为中文；review code 不再从异常 message 推导；可选 `details` 只含安全白名单上下文。
- **A10 查询与审计文本中文化**：change 后新形成的 notification rejection/conflict、payment review、reconciliation blocking/failure、settlement blocker/result/review summary 经 GET/查询可见中文；enum/type/code/identity/matchingBasis/raw external result 和用户输入 evidence 保持原值，历史数据无需迁移。
- **A11 内部异常不泄漏**：数据库、Hibernate、唯一约束、provider 或未知 runtime 异常的 HTTP 响应不包含 SQL、SQLState、异常类名、堆栈或原始英文 cause；日志仍保存可诊断信息，API 返回稳定 code 和中文安全说明。
- **A12 测试与静态守卫闭环**：更新所有绑定旧英文 summary 的测试；新增/扩展 HTTP error advice、review code 解耦、安全异常映射、中文 query round-trip 和用户可见纯英文消息扫描测试；focused tests 与最终 `clean build` 全通过，0 failure、0 skip，既有支付业务、生成、Analyzer、AgentFacts 与 traceability 合同不回退。

# Constraints and invariants

- `docs/requirements/**` 继续作为长期业务真源；本 change 只提高可发现性、可执行性和可理解性。
- `traceability.yaml` 是 acceptance/evidence/status 的机器真源；Guide 不建立脱离 guard 的第二份独立状态目录。
- 现有 canonical `payment-reference-build` Spec 的业务行为、英文错误 code、HTTP status、模块依赖、聚合边界和非目标继续有效。
- `PaymentErrorResponse` 保留 `status/code/message`；若增加 `details`，它必须是 additive、可选、安全字段，不能自动承载 `Throwable.message`。
- 对外中文 message 与内部诊断分离；未知异常采用固定中文 fallback，原始 cause 只记录日志。
- 新写入的本地生成 summary 使用统一中文术语；外部 raw 字段与用户输入文本不得被翻译或改写。
- 所有注释保持中文，代码符号、协议、错误码和日志示例保持原语言。
- 只修改本 change 所需文档、业务错误模型/映射、注释和对应测试；不借机改变业务行为。
- 单一 Native change，不创建 `children.yaml`；需求紧耦合且用户明确要求一次迭代完成。

# Decisions

- 使用独立 worktree：`comet/improve-payment-acceptance-experience`，目标分支 `main`。
- 保留 `payment-scenarios.md` 为 What；新增 `payment-acceptance-guide.md` 承载 How，并从 scenarios/requirements README/project README 链接。
- Guide 按支付、退款、对账、结算、横切五个场景族组织，共享架构路径后逐场景补差异，避免 53 次复制冗长路径。
- 状态继续以 `traceability.yaml` 为真源；planned 场景只导航 gap，不升级状态。
- 一次迭代同时完成文档、核心注释、异常中文化、审计摘要中文化和测试，不拆 Supervisor/child change。
- 错误合同采用稳定英文 `code` + 中文 `message` + 可选安全 `details`；不从 message 解析 code。
- 历史英文 summary 不迁移；只保证新写入记录。
- 不新增 reset API；Guide 明确 H2 重启或新 identity 的复位方式。
- 本 change 的所有写操作由主线程执行；只读子代理仅用于独立审查、事实核对和最终 Verifier，不使用任何可写子代理角色。

# Open questions

- 无。用户已于 2026-08-23 明确确认以上目标、范围、关键决定、A1-A12 和 Non-goals。

# Verification expectations

- 文档静态检查：53 个 `PAY-AC-*` 均可在 Guide 定位；状态与 `traceability.yaml` 对齐；仓库内链接可解析；无本机绝对路径。
- 代码静态检查：复杂 Behavior/Command 注释覆盖；不再从 `Throwable.message` 解析 code；用户可见新字符串纯英文扫描仅允许机器值/raw 诊断白名单。
- HTTP/应用测试：稳定 code/status 不变，中文 message，可选 details 安全；数据库/provider/未知异常不泄漏。
- Domain/查询测试：新写入的 payment/refund/reconciliation/settlement 审计 summary 为中文，业务状态与 machine token 不变。
- 回归测试：至少运行 `:domain:test`、`:application:test`、`:adapter:test`、`:start:test` 和最终 `clean build`；不得用 focused tests 替代全量回归。
- 项目合同检查：运行 traceability contract tests、Analyzer plan/generate 及现有 capability/evidence guards；生成输出和 checked-in source ownership 不得漂移。
