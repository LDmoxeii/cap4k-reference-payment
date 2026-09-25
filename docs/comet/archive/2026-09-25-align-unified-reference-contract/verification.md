---
generated_from_state_version: 19
---

# 验证

## 当前结果

- 结果: **已归档**
- 验证情况: **已完成检查，验证结果已确认**
- 目标周期: 2
- 迭代: 4
- 验证器尝试次数: 2
- 完成时间: 2026-09-25T03:47:10.388Z
- 摘要: 独立只读验证完成：A1-A81 全部通过。Runtime 当前候选全量测试 204/204 通过；CAP4K agent 0 diagnostics；v4 真实 HTTP 为 67/67 PAY-AC、915 条断言、1537 次交互，另有两次干净完整闭环且规范化 digest 一致。未发现需要返回 Build 的统一业务契约缺口。

## 验收

| 编号 | 结果 | 来源 | 验收项 | 原因 |
| --- | --- | --- | --- | --- |
| A1 | passed | brief.md | **A1 真源覆盖**：固定提交下指定的业务文档、62 条 PAY-BR、67 条 PAY-AC 与 traceability 已完整读取并逐项映射；工作台文档只标为 background。 | 已核对固定真源提交的八份业务文档；62/62 PAY-BR 与 67/67 PAY-AC 均在 brief/Spec 中完整映射，工作台文档仅作背景。 |
| A2 | passed | brief.md | **A2 统一基础契约**：Money、稳定 ID/时间、status/finality、命令幂等、OperationReceipt/Operation/readAfter、ApiError 与同步拒绝边界符合统一契约。 | Money、Finality、OperationReceipt/Operation、readAfter、ApiError、同步拒绝和幂等冲突实现及测试符合统一基础契约。 |
| A3 | passed | brief.md | **A3 支付闭环**：显式 PaymentAttempt、提交回执、可信结果、重复/冲突/迟到/未知、到期、单一成功事实、费用快照与查询完整可验。 | 代码与测试覆盖显式支付 attempt、提交/结果收件、可信 verifier、重复/冲突/迟到/UNKNOWN、到期、单一成功事实及费用政策快照。 |
| A4 | passed | brief.md | **A4 退款闭环**：退款申请具有独立 idempotencyKey、merchantRefundNo、Money、reason；Refund、RefundAttempt、结果收件和预算预占/释放/转换分离且并发守恒。 | 退款申请、RefundAttempt、渠道结果及预算预占/释放/成功转换已分离；独立幂等字段、并发守恒与 UNKNOWN 占用均有实现和测试。 |
| A5 | passed | brief.md | **A5 权威查询**：支付、退款、ReconciliationRun、Settlement、ManualReview 五列表支持规定筛选、不可变排序键与绑定筛选的 opaque keyset cursor。 | 五类权威列表均由后端原生 SQL 查询模型提供，并使用绑定筛选的 HMAC opaque keyset cursor 与稳定排序键。 |
| A6 | passed | brief.md | **A6 账单与对账**：权威账单/revision、bill available/范围发现、Run/rerun、差异、追加处置和补录确认事实完整，Run 是唯一公开执行资源。 | 权威账单/revision、bill available、范围发现、ReconciliationRun/rerun、差异处置及补录确认事实均已实现，Run 为唯一公开执行资源。 |
| A7 | passed | brief.md | **A7 结算闭环**：scope 只由 merchant+currency+period 定义；候选 included/excluded、确认冻结、稳定 execution、三结果、作废/替代与范围唯一完整可验。 | 结算 scope 仅由 merchant+currency+period 定义，included/excluded、确认冻结、稳定 execution、SUCCESS/FAILURE/UNKNOWN、void/replacement 与范围唯一均已核验。 |
| A8 | passed | brief.md | **A8 人工、通知与 trace**：统一命令的 `actorId` 由可信 `ReferenceActorContext` 映射，所有处置事实返回同一 actorId，缺失 context 同步拒绝且无副作用；记录时间来自服务端逻辑时钟；通知稳定重试；payment timeline 覆盖全部跨聚合事实并稳定排序。 | 可信 ReferenceActorContext 映射 actorId；缺失/未知 alias 同步拒绝，人工事实返回映射 actor；通知重试与跨聚合 timeline 均可查询并稳定排序。 |
| A9 | passed | brief.md | **A9 稳定 sandbox**：默认/覆盖 ReferencePolicy、逻辑时钟、channel/bill/settlement/notification 脚本和 fixture 从干净运行产生确定结果。 | ReferencePolicy 默认和覆盖、逻辑时钟及渠道/账单/结算/通知脚本化 fixture 可从干净环境确定性运行。 |
| A10 | passed | brief.md | **A10 CAP4K 风格与事务**：聚合、JPA、Command/UoW、乐观锁、领域事件和本地可靠事件保留；关键跨聚合更新、结算完成与通知意图具备同 UoW 证据。 | 实现保留 CAP4K 聚合、JPA、Command/UoW、乐观锁、领域事件和本地可靠事件风格；CAP4K agent 全部 partition fresh/ok 且 0 diagnostics。 |
| A11 | passed | brief.md | **A11 分层验证**：domain、事务/JPA、Endpoint contract、HTTP binding、并发、查询分页、fixture 与 composition 测试全部通过。 | Runtime 当前候选全量 Gradle 测试通过：57 suites、204 tests、0 failures/errors/skipped；覆盖领域、事务/JPA、端点、HTTP binding、分页、并发、fixture 与 composition。 |
| A12 | passed | brief.md | **A12 共享黑盒**：§11 的 67 个同名 PAY-AC Scenario 每一项都必须同时拥有自动化证据，以及启动真实 CAP4K 服务后由进程外 HTTP client 只经公开 reference/business HTTP surface 执行的真实 HTTP 证据；MockMvc 或进程内规范化测试不得替代逐场景 HTTP 验收。在 67 项逐场景 HTTP 全通过的基础上，真实服务还必须至少完成两次干净的支付到结算闭环，作为额外可重复性证明。 | v4 证据复算为 67/67 PAY-AC、915 条通过断言、1537 次 /api 真实 HTTP、每场景公开查询观察；两次独立干净 JVM/H2 闭环均通过且 digest 一致。 |
| A13 | passed | brief.md | **A13 学习版边界诚实**：不把 reference actor、merchantId、H2、sandbox、ordinary scheduler 或本地可靠事件描述成生产认证、隔离、资金或 exactly-once 保证。 | 文档明确 reference actor、merchantId、H2、sandbox、普通 scheduler 与本地可靠事件的学习版边界，未宣称生产认证、隔离、资金或 exactly-once。 |
| A14 | passed | brief.md | **A14 可追溯交付**：最终报告列出全部 PAY-BR/PAY-AC、实现位置、自动化与真实 HTTP 证据，模板的 planned 状态不被当作本仓库实现结论。 | traceability 已列全量 PAY-BR/PAY-AC、实现位置及自动化/真实 HTTP 证据；PAY-EV-037 的 67 个槽位路径、ID、SHA 与 revision 全部复算一致。 |
| A15 | passed | specs/payment-reference-build/spec.md | PAY-AC-001 首次创建支付 给定合格商户与渠道，当以 K-001/O-001 创建 CNY 10000 支付时，则产生稳定 paymentId 的 PAYABLE 支付，且不显示已付款。 | PAY-AC-001 的自动化及进程外真实 HTTP 证据为 PASSED，公开查询和断言验证首次创建支付语义。 |
| A16 | passed | specs/payment-reference-build/spec.md | PAY-AC-002 相同内容重复创建 给定 K-001 已创建支付，当完全相同请求重放时，则返回原 operation/payment，无第二资源或副作用。 | PAY-AC-002 的自动化及进程外真实 HTTP 证据为 PASSED，验证相同内容重放返回原 operation/payment 且无第二副作用。 |
| A17 | passed | specs/payment-reference-build/spec.md | PAY-AC-003 幂等键冲突 给定 K-001 已用于 CNY 10000，当以相同 key 创建 CNY 12000 时，则同步 IDEMPOTENCY_CONFLICT，原金额不变且无新资源。 | PAY-AC-003 的自动化及进程外真实 HTTP 证据为 PASSED，验证幂等冲突同步拒绝且原资源不变。 |
| A18 | passed | specs/payment-reference-build/spec.md | PAY-AC-004 渠道成功通知 给定处理中支付及 attempt，当 trusted verifier 接受身份和 Money 匹配的成功结果时，则支付成功、记录交易号/时间并形成一次通知意图。 | PAY-AC-004 的自动化及进程外真实 HTTP 证据为 PASSED，验证可信成功结果、交易证据与单一通知意图。 |
| A19 | passed | specs/payment-reference-build/spec.md | PAY-AC-005 重复成功通知 给定 N-001 已令支付成功，当相同结果重复三次时，则只有一个成功事实/收入，所有接收与计数可查。 | PAY-AC-005 的自动化及进程外真实 HTTP 证据为 PASSED，验证重复成功收件可查且成功事实/收入仅一次。 |
| A20 | passed | specs/payment-reference-build/spec.md | PAY-AC-006 未验证通知 给定处理中支付，当收到验真失败或 Money 不匹配的成功声明时，则状态不成功，receipt 标记 REJECTED_INVALID 并保留原因。 | PAY-AC-006 的自动化及进程外真实 HTTP 证据为 PASSED，验证验真失败或 Money 不匹配被 REJECTED_INVALID。 |
| A21 | passed | specs/payment-reference-build/spec.md | PAY-AC-007 支付到期关闭 给定支付到期、PAYABLE 且无未决 attempt，当到期扫描时，则支付 CLOSED 且不再主动尝试。 | PAY-AC-007 的自动化及进程外真实 HTTP 证据为 PASSED，验证无未决 attempt 的到期支付关闭。 |
| A22 | passed | specs/payment-reference-build/spec.md | PAY-AC-008 到期但结果未知 给定到期支付存在 unknown attempt，当扫描时，则不判失败/关闭并产生核对事项。 | PAY-AC-008 的自动化及进程外真实 HTTP 证据为 PASSED，验证未知 attempt 阻止误关单并创建核对事项。 |
| A23 | passed | specs/payment-reference-build/spec.md | PAY-AC-009 关闭后的迟到成功 给定支付 CLOSED，当可信成功迟到时，则 receipt 为 LATE、建立 review、阻断结算，且不静默丢弃或自动覆盖关闭事实。 | PAY-AC-009 的自动化及进程外真实 HTTP 证据为 PASSED，验证关闭后迟到成功保留为 LATE 并阻断结算。 |
| A24 | passed | specs/payment-reference-build/spec.md | PAY-AC-010 两个尝试同时成功 给定同一支付两个在途 attempt，当二者分别可信成功时，则最多一个成功事实/收入，另一个为冲突证据并阻断结算。 | PAY-AC-010 的自动化及进程外真实 HTTP 证据为 PASSED，验证两个尝试同时成功时仅一个成功事实，冲突进入 review。 |
| A25 | passed | specs/payment-reference-build/spec.md | PAY-AC-011 已成功订单不得重复收款 给定 merchant order 已有成功支付，当以新 key 再次创建时，则同步 BUSINESS_CONFLICT 且无第二可执行支付。 | PAY-AC-011 的自动化及进程外真实 HTTP 证据为 PASSED，验证成功订单的重复收款被同步拒绝。 |
| A26 | passed | specs/payment-reference-build/spec.md | PAY-AC-012 无效金额或币种被拒绝 给定 CNY 精度与启用币种 policy，当金额为零、超过分精度或 USD 未启用时，则 VALIDATION_ERROR 且无 payment/attempt/operation。 | PAY-AC-012 的自动化及进程外真实 HTTP 证据为 PASSED，验证零金额、非法精度和未启用币种无资源副作用。 |
| A27 | passed | specs/payment-reference-build/spec.md | PAY-AC-013 已创建支付的金额不可修改 给定 CNY 10000 支付，当尝试改为 CNY 12000 时，则拒绝且原 Money 不变。 | PAY-AC-013 的自动化及进程外真实 HTTP 证据为 PASSED，验证已创建支付 Money 不可修改。 |
| A28 | passed | specs/payment-reference-build/spec.md | PAY-AC-014 费用规则使用成功时快照 给定支付成功时 feeRate 0.006 后改为 0.008，当进入结算时，则仍使用成功时快照与舍入规则并可查。 | PAY-AC-014 的自动化及进程外真实 HTTP 证据为 PASSED，验证成功时生效 feeRate、roundingMode 与币种精度快照被冻结。 |
| A29 | passed | specs/payment-reference-build/spec.md | PAY-AC-015 成功后的失败结果不回退支付 给定支付已可信成功，当收到失败结果时，则 SUCCEEDED 不回退，冲突 receipt/review 保留。 | PAY-AC-015 的自动化及进程外真实 HTTP 证据为 PASSED，验证成功后的失败结果不回退支付并保留冲突证据。 |
| A30 | passed | specs/payment-reference-build/spec.md | PAY-AC-016 渠道受理不等于支付成功 给定 reference channel 仅 ACCEPTED，当同步受理时，则支付 PROCESSING/RESULT_UNKNOWN，不形成收入、成功通知或结算候选。 | PAY-AC-016 的自动化及进程外真实 HTTP 证据为 PASSED，验证渠道 ACCEPTED 不等于支付成功。 |
| A31 | passed | specs/payment-reference-build/spec.md | PAY-AC-017 支付成功后不再发起新尝试 给定支付 SUCCEEDED，当再次创建/提交 attempt 时，则同步拒绝且历史不变。 | PAY-AC-017 的自动化及进程外真实 HTTP 证据为 PASSED，验证成功后禁止新支付 attempt。 |
| A32 | passed | specs/payment-reference-build/spec.md | PAY-AC-020 全额退款 给定 CNY 10000 成功支付且无退款，当申请、attempt 并成功退款 CNY 10000 时，则 succeeded=10000、available=0，退款进入对账与结算扣减。 | PAY-AC-020 的自动化及进程外真实 HTTP 证据为 PASSED，验证全额退款及对账/结算扣减。 |
| A33 | passed | specs/payment-reference-build/spec.md | PAY-AC-021 多次部分退款 给定 CNY 10000 成功支付，当先后成功退款 3000 与 2000 时，则 succeeded=5000、available=5000，两退款独立可追踪。 | PAY-AC-021 的自动化及进程外真实 HTTP 证据为 PASSED，验证多次部分退款独立追踪与预算核算。 |
| A34 | passed | specs/payment-reference-build/spec.md | PAY-AC-022 超额退款被拒绝 给定已成功退款 6000，当申请 5000 时，则 REFUND_BUDGET_EXCEEDED，available 仍 4000，且无 refund/attempt/channel submission。 | PAY-AC-022 的自动化及进程外真实 HTTP 证据为 PASSED，验证超额退款同步拒绝且无 refund/attempt/submission。 |
| A35 | passed | specs/payment-reference-build/spec.md | PAY-AC-023 并发退款防超退 给定 available=6000，当两个 4000 请求并发时，则最多一笔受理，另一笔稳定冲突，reserved+succeeded 不超 original。 | PAY-AC-023 的自动化及进程外真实 HTTP 证据为 PASSED，并使用独立并发 worker 验证退款预算不超额。 |
| A36 | passed | specs/payment-reference-build/spec.md | PAY-AC-024 退款失败释放占用 给定退款 4000 已预占且整体明确失败，当结果受理时，则 Refund FAILED，预占只释放一次，available 恢复。 | PAY-AC-024 的自动化及进程外真实 HTTP 证据为 PASSED，验证明确失败只释放一次退款预占。 |
| A37 | passed | specs/payment-reference-build/spec.md | PAY-AC-025 退款结果待确认 给定退款已提交但结果未知，当 review 期限到达时，则 Refund 保持 RESULT_UNKNOWN/REVIEW_REQUIRED，预算继续占用并建立 review，不得同额重退。 | PAY-AC-025 的自动化及进程外真实 HTTP 证据为 PASSED，验证退款 UNKNOWN/review 继续占用预算。 |
| A38 | passed | specs/payment-reference-build/spec.md | PAY-AC-026 重复退款申请 给定 merchantRefundNo R-001 与独立 idempotencyKey 已受理 2000 退款，当相同 payload 重放时，则返回原 operation/refund 且不再次预占。 | PAY-AC-026 的自动化及进程外真实 HTTP 证据为 PASSED，验证重复退款申请返回原资源且不再次预占。 |
| A39 | passed | specs/payment-reference-build/spec.md | PAY-AC-027 非成功支付不得退款 给定支付为 processing/unknown/failed/closed，当请求退款时，则同步拒绝，无预占、refund 或 channel submission。 | PAY-AC-027 的自动化及进程外真实 HTTP 证据为 PASSED，验证非成功支付不得退款且无资金副作用。 |
| A40 | passed | specs/payment-reference-build/spec.md | PAY-AC-028 超过退款期限的申请被拒绝 给定成功支付超过 P30D 且无 reference 例外，当申请退款时，则同步拒绝、说明超期且预算不变。 | PAY-AC-028 的自动化及进程外真实 HTTP 证据为 PASSED，验证超过退款期限同步拒绝且预算不变。 |
| A41 | passed | specs/payment-reference-build/spec.md | PAY-AC-029 成功退款后的失败结果不回退退款 给定退款已可信成功，当收到失败结果时，则 SUCCEEDED 与预算成功金额不回退，冲突证据/review 保留。 | PAY-AC-029 的自动化及进程外真实 HTTP 证据为 PASSED，验证成功退款后的失败结果不回退预算事实。 |
| A42 | passed | specs/payment-reference-build/spec.md | PAY-AC-040 完全匹配 给定平台 CNY 10000 成功事实和同交易号/Money/status 的完整账单记录，当运行对账时，则结果 MATCHED 且无差异。 | PAY-AC-040 的自动化及进程外真实 HTTP 证据为 PASSED，验证平台与完整账单完全匹配。 |
| A43 | passed | specs/payment-reference-build/spec.md | PAY-AC-041 平台单边 给定平台成功但完整账单无对应记录，当运行时，则产生 PLATFORM_ONLY，双方证据保留并阻断该交易结算。 | PAY-AC-041 的自动化及进程外真实 HTTP 证据为 PASSED，验证 PLATFORM_ONLY 差异与结算阻断。 |
| A44 | passed | specs/payment-reference-build/spec.md | PAY-AC-042 渠道单边 给定账单成功记录无平台 payment/attempt，当运行时，则产生 CHANNEL_ONLY 与 review，不自动创建支付成功事实。 | PAY-AC-042 的自动化及进程外真实 HTTP 证据为 PASSED，验证 CHANNEL_ONLY 不自动补造支付成功事实。 |
| A45 | passed | specs/payment-reference-build/spec.md | PAY-AC-043 金额差异 给定平台 10000、账单 9900 且 identity 可关联，当运行时，则 AMOUNT_MISMATCH，双方原值保留并阻断结算。 | PAY-AC-043 的自动化及进程外真实 HTTP 证据为 PASSED，验证金额差异保留双方原值并阻断结算。 |
| A46 | passed | specs/payment-reference-build/spec.md | PAY-AC-044 状态差异收敛 给定平台 unknown、账单可信成功且身份/Money 匹配，当以可信 actor session alias 建立 ReferenceActorContext 并提交 reason/evidence 确认时，则追加 FactConfirmation，返回 context 对应 actorId，原事实与账单不改写且责任时间可查；缺失/未知 context 时同步拒绝且无副作用。 | PAY-AC-044 的自动化及进程外真实 HTTP 证据为 PASSED，验证可信 actor 的追加 FactConfirmation 及缺失 context 无副作用拒绝。 |
| A47 | passed | specs/payment-reference-build/spec.md | PAY-AC-045 对账重跑不重复 给定某 scope 已运行并有三项差异，当相同或修订账单重跑时，则旧/新 run 可区分、无重复有效差异且只有一个 effectiveRun。 | PAY-AC-045 的自动化及进程外真实 HTTP 证据为 PASSED，验证对账重跑保留历史且仅一个 effectiveRun。 |
| A48 | passed | specs/payment-reference-build/spec.md | PAY-AC-046 未决差异阻断完成 给定 run 有未处置金额差异，当尝试完成时，则保持 ACTION_REQUIRED/REVIEW_REQUIRED 并返回阻断详情。 | PAY-AC-046 的自动化及进程外真实 HTTP 证据为 PASSED，验证未决差异阻止错误完成。 |
| A49 | passed | specs/payment-reference-build/spec.md | PAY-AC-047 差异处置不改写原始证据 给定金额差异，当以可信 actor session alias 建立 ReferenceActorContext 后处置时，则平台事实、账单记录、初始差异仍可查，DifferenceDisposition 追加并返回 context 对应 actorId；缺失/未知 context 时同步拒绝且无副作用。 | PAY-AC-047 的自动化及进程外真实 HTTP 证据为 PASSED，验证差异处置追加事实、actor 映射及原证据不可改写。 |
| A50 | passed | specs/payment-reference-build/spec.md | PAY-AC-060 正常结算计算 给定收入 10000+5000、退款 2000、费用 200+100，当准备商户 CNY period 结算时，则 gross=15000、refund=2000、fee=300、net=12700 且逐 item 可追溯。 | PAY-AC-060 的自动化及进程外真实 HTTP 证据为 PASSED，验证收入、退款、费用和净额逐项可追溯计算。 |
| A51 | passed | specs/payment-reference-build/spec.md | PAY-AC-061 未决差异不进入结算 给定一个支付有 blocking difference，当准备结算时，则该候选 EXCLUDED 并有 reasonCode，其他合格候选可 INCLUDED。 | PAY-AC-061 的自动化及进程外真实 HTTP 证据为 PASSED，验证 blocking difference 对候选的明确 EXCLUDED。 |
| A52 | passed | specs/payment-reference-build/spec.md | PAY-AC-062 防止重复结算 给定支付收入已计入 confirmed settlement，当准备后续 period 时，则该 source fact 不再作为 INCLUDED 收入。 | PAY-AC-062 的自动化及进程外真实 HTTP 证据为 PASSED，验证已确认结算的 source fact 不会重复纳入。 |
| A53 | passed | specs/payment-reference-build/spec.md | PAY-AC-063 结算执行成功 给定 confirmed CNY 12700 settlement 和稳定 executionId，当收到匹配 SUCCESS 时，则 SETTLED/FINAL，重复成功不形成第二事实。 | PAY-AC-063 的自动化及进程外真实 HTTP 证据为 PASSED，验证稳定 executionId 成功结算且重复成功无第二事实。 |
| A54 | passed | specs/payment-reference-build/spec.md | PAY-AC-064 结算执行结果未知 给定已执行但结果 UNKNOWN，当 review 期限到达时，则保持原 execution identity、进入 REVIEW_REQUIRED，禁止新 identity/新单/重付。 | PAY-AC-064 的自动化及进程外真实 HTTP 证据为 PASSED，验证 UNKNOWN 保持原执行身份并禁止重付。 |
| A55 | passed | specs/payment-reference-build/spec.md | PAY-AC-065 结算结果矛盾 给定 settlement 已成功，当同 execution 收到失败时，则 SETTLED 不回退，receipt/review 保留矛盾证据。 | PAY-AC-065 的自动化及进程外真实 HTTP 证据为 PASSED，验证成功后的矛盾失败不回退结算。 |
| A56 | passed | specs/payment-reference-build/spec.md | PAY-AC-066 负结算额 给定 net=-3000，当准备时，则保存全部构成、进入 manual review 且不调用 executor。 | PAY-AC-066 的自动化及进程外真实 HTTP 证据为 PASSED，验证负净额保留构成、进入人工核对且不执行。 |
| A57 | passed | specs/payment-reference-build/spec.md | PAY-AC-067 结算确认后构成冻结 给定 settlement 已 confirmed，当发现后到退款/费用时，则原 scope/items/Money/version 不变，影响进入后续 period/adjustment。 | PAY-AC-067 的自动化及进程外真实 HTTP 证据为 PASSED，验证确认后结算 scope/items/Money/version 冻结。 |
| A58 | passed | specs/payment-reference-build/spec.md | PAY-AC-068 同一周期不得存在两份有效结算单 给定 merchant+currency+period 已有有效单，当从不同 channel/date 表达再次 prepare 时，则 SETTLEMENT_SCOPE_CONFLICT；原单作废后替代单双向关联且只有一份有效单。 | PAY-AC-068 的自动化及进程外真实 HTTP 证据为 PASSED，验证 merchant+currency+period 范围唯一及 void/replacement 双向关系。 |
| A59 | passed | specs/payment-reference-build/spec.md | PAY-AC-080 商户业务范围与筛选 给定 M-A/M-B 各有支付，当按 M-A list 和退款时，则列表只返回 M-A，跨 merchant 引用同步拒绝且无资金副作用；不宣称生产隔离。 | PAY-AC-080 的自动化及进程外真实 HTTP 证据为 PASSED，验证 merchant 业务筛选和跨 merchant 引用拒绝。 |
| A60 | passed | specs/payment-reference-build/spec.md | PAY-AC-081 商户通知失败重试 给定支付成功且首次通知失败，当后续重试成功时，则成功事实不重复，同一 notification/content 的全部 attempts 与最终状态可查。 | PAY-AC-081 的自动化及进程外真实 HTTP 证据为 PASSED，验证通知稳定重试且不重复成功事实。 |
| A61 | passed | specs/payment-reference-build/spec.md | PAY-AC-082 资金事实更正留痕 给定核实的渠道单边漏记成功，当补录确认时，则原平台/渠道证据、reason、actor 与 server time 全部可查，无删除覆盖。 | PAY-AC-082 的自动化及进程外真实 HTTP 证据为 PASSED，验证资金事实更正保留原证据、reason、actor 与服务端时间。 |
| A62 | passed | specs/payment-reference-build/spec.md | PAY-AC-083 支付全链路追踪 给定支付经历两 attempt、receipts、成功、部分退款/预算、通知、bill/revision、run/difference/disposition、settlement，当按 paymentId 查询时，则完整 timeline 按 recordedAt/eventId 稳定排序，occurredAt/Money/identity 可核算。 | PAY-AC-083 的自动化及进程外真实 HTTP 证据为 PASSED，验证支付全链路 timeline 完整并按 recordedAt/eventId 稳定排序。 |
| A63 | passed | specs/payment-reference-build/spec.md | PAY-AC-084 退役渠道不再参与新支付 给定历史使用的 channel config 已 RETIRED，当创建新支付/attempt 时，则不再选择该渠道，历史 channel/fee snapshot 仍可查。 | PAY-AC-084 的自动化及进程外真实 HTTP 证据为 PASSED，验证退役渠道不参与新支付且历史快照仍可查。 |
| A64 | passed | specs/payment-reference-build/spec.md | PAY-AC-085 业务时区决定日界线 给定 Asia/Shanghai 23:59 和次日 00:01 两事实，当形成 businessDate/period 时，则分别归日并保留原 occurredAt 与 timezone。 | PAY-AC-085 的自动化及进程外真实 HTTP 证据为 PASSED，验证业务时区对日界线与期间归属的确定语义。 |
| A65 | passed | specs/payment-reference-build/spec.md | PAY-AC-086 人工动作责任字段完整并留痕 给定迟到成功需解除阻断，当可信 `X-Reference-Actor-Context` session alias 缺失/未知或 reason/evidence 不完整时，则同步 ApiError 且无 Operation/业务副作用；alias 可解析且责任字段完整时，追加并返回 registry 映射的 actorId、server time、reason/evidence。该场景不验证真实认证/RBAC。 | PAY-AC-086 的自动化及进程外真实 HTTP 证据为 PASSED，验证人工动作责任字段、actor 映射、server time 与缺失 context 无副作用拒绝。 |
| A66 | passed | specs/payment-reference-build/spec.md | PAY-AC-087 账单可用信号与权威账单收敛 给定 bill/revision 可用且 scheduler、signal 重投、人工重跑并发乱序，当 provider 暂不可读后恢复时，则同 revision 只有一个有效 run，高 revision 前进、低 revision 不回退、同 signal identity 可诊断重试。 | PAY-AC-087 的自动化及进程外真实 HTTP 证据为 PASSED，验证账单可用信号乱序/重投下 revision 单调前进与单一有效 run。 |
| A67 | passed | specs/payment-reference-build/spec.md | PAY-AC-088 结算完成通知在当前运行期间原子可见并稳定重试 给定 settlement 首次 accepted success 且通知首次失败，当事务完成、重复 callback 或通知重试时，则成功事实与通知意图同 UoW 可见、identity/content 稳定、无第二成功事实；不宣称持久化 Inbox/跨进程 exactly-once。 | PAY-AC-088 的自动化及进程外真实 HTTP 证据为 PASSED，验证结算成功事实与通知意图同 UoW 可见并稳定重试。 |
| A68 | passed | specs/payment-reference-build/spec.md | PAY-AC-090 同步拒绝与命令受理边界 给定非法 Money、幂等冲突、预算不足与正常命令，当分别提交并重放正常命令时，则同步拒绝只有 ApiError/无 Operation，正常为 ACCEPTED，重放原 operation 为 ALREADY_ACCEPTED，receipt 不等于领域成功。 | PAY-AC-090 的自动化及进程外真实 HTTP 证据为 PASSED，验证同步拒绝、ACCEPTED 与 ALREADY_ACCEPTED 的清晰边界。 |
| A69 | passed | specs/payment-reference-build/spec.md | PAY-AC-091 已受理 Operation 按 readAfter 收敛 给定 receipt 为 POLL 且资源 projection 暂不可见，当先读 resourceUrl 再轮询 operationUrl 时，则前者 RESOURCE_NOT_READY 含 operationId/retryAfter，Operation 始终可读并仅在 SUCCEEDED/FAILED/REVIEW_REQUIRED 结束。 | PAY-AC-091 的自动化及进程外真实 HTTP 证据为 PASSED，验证 RESOURCE_NOT_READY/readAfter 与 Operation 收敛。 |
| A70 | passed | specs/payment-reference-build/spec.md | PAY-AC-092 Operation 观察超时不是业务失败 给定 operation 持续 ACCEPTED/PROCESSING，当观察达到 PT30S 时，则只报告 OBSERVATION_TIMEOUT，operation 与领域状态不被改为失败且仍可查询。 | PAY-AC-092 的自动化及进程外真实 HTTP 证据为 PASSED，验证观察超时不改写 Operation 或领域状态为失败。 |
| A71 | passed | specs/payment-reference-build/spec.md | PAY-AC-093 五类权威列表以无快照 keyset 稳定分页 给定五列表跨 merchant/status/time 且有相同 timestamp，当小页翻页、首屏后新建更靠前记录并跨 filters 复用 cursor 时，则旧集合不重复不遗漏、新记录不回填后续页、跨筛选 INVALID_CURSOR。 | PAY-AC-093 的自动化及进程外真实 HTTP 证据为 PASSED，验证五列表稳定 keyset 分页、首屏后插入隔离及跨筛选 cursor 拒绝。 |
| A72 | passed | specs/payment-reference-build/spec.md | PAY-AC-094 ReconciliationRun 是唯一可重跑的对账执行资源 给定 bill revision 1/2、重复并发与迟到信号，当读取 revision 链并运行/重跑/查询时，则 Bill 只作输入证据，Run 是唯一主资源，高 revision 不回退、旧 run 保留且只有一个 effectiveRun，无并列 Batch API。 | PAY-AC-094 的自动化及进程外真实 HTTP 证据为 PASSED，验证 ReconciliationRun 唯一执行资源及 revision/effectiveRun 语义。 |
| A73 | passed | specs/payment-reference-build/spec.md | PAY-AC-095 差异处置和补录确认追加且可解除阻断 给定 blocking difference，当可信 actor session alias 建立 ReferenceActorContext 并提交完整 disposition/confirmation 时，则原事实/revision/difference 保留、历史返回映射后的 actorId，只有明确结论解除阻断；缺失/未知 context 或责任字段时同步拒绝且无副作用。 | PAY-AC-095 的自动化及进程外真实 HTTP 证据为 PASSED，验证处置/确认追加留痕、可信 actor 与明确结论解除阻断。 |
| A74 | passed | specs/payment-reference-build/spec.md | PAY-AC-096 结算准备记录纳入排除并在确认后冻结 给定 period 内含可结算、退款、费用、调整、未决、unknown 与已结算事实，当 prepare/confirm 时，则每候选有 INCLUDED/EXCLUDED、source/reason，净额仅用 included，确认后 scope/items/Money/version 冻结。 | PAY-AC-096 的自动化及进程外真实 HTTP 证据为 PASSED，验证所有候选 INCLUDED/EXCLUDED、原因及确认冻结。 |
| A75 | passed | specs/payment-reference-build/spec.md | PAY-AC-097 结算执行成功失败未知的稳定身份处置 给定 confirmed settlement 和可脚本化 executor，当分别处理 duplicate SUCCESS、明确 FAILURE 后显式重试、UNKNOWN 时，则成功一次、失败诊断/新 attempt 受控、unknown 保持原 identity 并禁重付。 | PAY-AC-097 的自动化及进程外真实 HTTP 证据为 PASSED；prepare/execute 无 actor header，稳定执行身份覆盖成功、失败重试与 UNKNOWN 禁重付。 |
| A76 | passed | specs/payment-reference-build/spec.md | PAY-AC-098 结算作废与替代不绕过未知执行 给定一份可作废 settlement 与一份 RESULT_UNKNOWN，当作废前者并 replacement、尝试替代后者时，则前者双向关联且 scope 唯一，后者拒绝 void/replacement 并继续核对原 execution。 | PAY-AC-098 的自动化及进程外真实 HTTP 证据为 PASSED；可信 actor 仅用于 void，验证 replacement 双向关联且 UNKNOWN execution 不可绕过。 |
| A77 | passed | specs/payment-reference-build/spec.md | PAY-AC-099 退款 attempt 结果只转换一次预算 给定 refund 已预占且 reference channel 注入 replay/retryable failure/unknown/success/late/conflict，当处理并查询时，则预占一次、释放/成功转换至多一次、unknown/review 保持占用且所有 receipts 可查。 | PAY-AC-099 的自动化及进程外真实 HTTP 证据为 PASSED，验证退款 attempt 各类结果对预算至多转换一次且 receipts 全可查。 |
| A78 | passed | specs/payment-reference-build/spec.md | PAY-AC-100 支付渠道收件可查询且不改写单一成功事实 给定多 attempt、submission receipts、首个成功及 duplicate/invalid/unknown-ref/late/second-success，当查询与按外部 identity 查未知引用时，则每收件有稳定 disposition、事实不覆盖且支付最多一个 accepted success。 | PAY-AC-100 的自动化及进程外真实 HTTP 证据为 PASSED，验证支付渠道全部收件可查且不覆盖单一成功事实。 |
| A79 | passed | specs/payment-reference-build/spec.md | PAY-AC-101 Reference Policy 覆盖保持本地结果确定 给定相同 fixture/clock 使用默认与显式覆盖 policy，当从干净运行重复场景时，则每组输入得到确定相同观察，policy 与 fixture 被记录且不宣称生产政策。 | PAY-AC-101 的自动化及进程外真实 HTTP 证据为 PASSED，验证默认/覆盖 policy 在固定 fixture/clock 下确定可重复。 |
| A80 | passed | specs/payment-reference-build/spec.md | PAY-AC-102 reference sandbox 可重复完成支付到结算闭环 给定固定 channel/bill/executor/notification scripts、fixture、policy 与 clock，当两个干净运行各完成同一闭环时，则状态、错误、关联、排序和副作用一致，同 identity replay 无第二效果。 | PAY-AC-102 主场景 41 次 HTTP/29 条断言通过；两次额外干净 JVM/H2 各 41 次 HTTP/18 条断言，规范化观察与 digest 完全一致。 |
| A81 | passed | specs/payment-reference-build/spec.md | PAY-AC-103 两个参考后端执行同一黑盒场景 给定 CAP4K adapter 接受统一契约，当以同一 PAY-AC/fixture/policy/clock/executor 执行时，则 CAP4K 的规范化 Money、状态、finality、错误/details、关联、排序和副作用符合统一真源；本 change 不修改或声称 WOW 的验收状态。 | PAY-AC-103 的 14 次真实 HTTP 与 14 条断言通过；规范化 Money、状态、finality、ApiError、关联、排序和副作用符合统一真源，且未声称 WOW 状态。 |

## 检查

| 检查 | 命令 | 工作目录 | 状态 | 退出码 | 耗时 |
| --- | --- | --- | --- | ---: | ---: |
| Current candidate full Gradle test suite | test --no-daemon --console=plain | . | passed | 0 | 166708 ms |

### Builder 报告的证据

以下为 Builder 报告，不等同于 Runtime 检查凭据或独立验收结果。

- settlement endpoint HTTP configuration contract: passed — .\gradlew.bat :adapter:test --tests *MerchantSettlementEndpointHttpConfigurationContractTest; BUILD SUCCESSFUL
- full Gradle build and CAP4K guard: passed — full build passed; four-module non-incremental projection rebuilt; final CAP4K diagnostics status ok with 0 diagnostics
- 67-scenario process-out real HTTP acceptance v4: passed — 67/67 PAY-AC, 915 assertions, 1537 HTTP interactions; clean loops 2/2 repeatable with identical digest 28ff20853d607ff63fdf600b64e9f904ac314315a1082cb465f93468d89617d6
- v4 evidence and traceability integrity audit: passed — 67 hashes/revisions match; actual BootJar and summary SHA match; all HTTP/public query/assertion collections non-empty; client/service PIDs distinct
- governance, PowerShell AST and diff hygiene: passed — three governance test classes passed; four acceptance PowerShell files parse with 0 errors; git diff --check exit 0
- 已知限制: 登录、JWT/OIDC、生产 RBAC、真实双人授权与生产商户隔离不在本 reference 交付中
- 已知限制: 生产数据库部署、历史迁移、重启恢复、持久化 Inbox、多实例 scheduler lease 与跨服务 exactly-once 未交付
- 已知限制: 生产 gateway、正式 CORS/CSRF、限流、TLS、Secret Manager、真实渠道证书、真实账单下载和真实资金移动未交付
- 已知限制: 本地可靠事件未扩展为通用消息平台；H2、reference policy、逻辑时钟与脚本化 fixture 仅用于确定性学习和验收

## 阻塞项

_无。_

## 风险与跳过的工作

- 本交付是 reference/H2/fixture 学习环境；reference actor context、商户字段、脚本化渠道与本地可靠事件不构成生产认证、隔离、真实资金或跨服务 exactly-once。
- v4 证据生成后，traceability.yaml 才绑定该证据的路径、hash 与 revision，因而当前全工作树 dirty fingerprint 与运行时记录不同；BootJar SHA-256、summary SHA-256、67 个场景文件及业务候选均未漂移。
- 部分 settlement HTTP binding 契约测试包含结构断言；v4 进程外真实 HTTP 已额外验证 prepare/execute 不传 actor、confirm/void 传可信 actor 的实际行为。

## 之前的迭代

| 目标周期 | 迭代 | 尝试 | 结果 | 未解决项 | 摘要 | 完成时间 |
| ---: | ---: | ---: | --- | --- | --- | --- |
| 1 | 0 | 0 | recovery | — | Native confirmed acceptance criteria changed | 2026-09-22T08:00:59.355Z |
| 2 | 1 | 0 | recovery | — | Native check input changed after the candidate was built; a new Builder candidate is required before checks can run again. | 2026-09-24T08:32:44.370Z |
| 2 | 2 | 1 | fail | A2, A7, A12, A81 | Independent verification completed for all 81 items: 77 passed and 4 failed (A2, A7, A12, A81). Runtime build/governance/agent/diff checks and the 67-scenario HTTP evidence are valid for the tested candidate, but the public settlement adapter wrongly requires actor context for the non-human PrepareSettlement and ExecuteSettlement commands, and the shared runner masks that mismatch by always supplying a settlement actor alias. | 2026-09-24T08:56:18.484Z |
| 2 | 3 | 0 | recovery | — | Native check input changed after the candidate was built; a new Builder candidate is required before checks can run again. | 2026-09-25T03:12:20.378Z |
| 2 | 4 | 1 | execution-error | — | The independently spawned read-only Verifier failed before startup confirmation because its upstream model request returned HTTP 503 Service Unavailable. It produced no acceptance findings and made no project changes. | 2026-09-25T03:18:27.879Z |
| 2 | 4 | 2 | pass | — | 独立只读验证完成：A1-A81 全部通过。Runtime 当前候选全量测试 204/204 通过；CAP4K agent 0 diagnostics；v4 真实 HTTP 为 67/67 PAY-AC、915 条断言、1537 次交互，另有两次干净完整闭环且规范化 digest 一致。未发现需要返回 Build 的统一业务契约缺口。 | 2026-09-25T03:47:10.388Z |



## 结论

独立只读验证完成：A1-A81 全部通过。Runtime 当前候选全量测试 204/204 通过；CAP4K agent 0 diagnostics；v4 真实 HTTP 为 67/67 PAY-AC、915 条断言、1537 次交互，另有两次干净完整闭环且规范化 digest 一致。未发现需要返回 Build 的统一业务契约缺口。
