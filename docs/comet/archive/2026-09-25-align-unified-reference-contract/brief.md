# 目标

在单一 Comet Native change 中，把 CAP4K 支付参考后端完整对齐到 `payment-product-template` 提交 `3b66db675356e77c720081e0410baf444b7baa9c` 冻结的统一学习版业务契约。最终系统继续采用 CAP4K 的聚合、JPA、Command/Query/Capability、事务 Unit of Work、领域事件与本地可靠事件风格，同时提供支付、退款、权威账单与对账、商户结算、人工核对、通知和 payment trace 的完整 reference 闭环，并能用同一 Reference Policy、fixture、逻辑时钟和 sandbox 脚本在自动化测试与真实 HTTP 下重复执行全部共享 PAY-AC 场景。

完成时仅修改 `cap4k-reference-payment`；不修改 WOW、`payment-product-template`、前端工作台或 `payment-reference-workbench`。

# 范围

## Source coverage

### 覆盖边界与真源优先级

- 统一业务真源固定为 `payment-product-template@3b66db675356e77c720081e0410baf444b7baa9c`。
- 完整覆盖：`docs/business/overview.md`、`glossary.md`、`rules.md`、`lifecycle.md`、`reference-learning-profile.md`、`reference-backend-contract.md`、`docs/acceptance/scenarios.md` 与 `docs/traceability.yaml`。
- 字段、命令、查询、错误与分页以 Backend Contract 为准；稳定不变量以 Rules 为准；黑盒结果以 Scenarios 为准；Profile 决定本次范围、非目标与默认 policy；Lifecycle 与 Overview 负责跨域解释；Traceability 负责 BR/AC 映射，不把模板中的 `planned` 误解为 CAP4K 的实现状态。
- `payment-reference-workbench/docs/backend-alignment.md` 只作为 CAP4K/WOW 现状调查背景，不是需求来源，不能替代上述固定提交，也不用于缩小统一业务范围。

| 来源单元 | 读取状态 | 保留内容 | Spec 位置 | 验收 | 覆盖状态与分类 |
|---|---|---|---|---|---|
| SRC-OV-01 `overview.md` §1-2 | complete | 产品真源职责、编号稳定性、文档优先级 | §1、§13 | A1、A14 | covered；真源治理 |
| SRC-OV-02 `overview.md` §3-5 | complete | 支付到结算闭环、参与者、上下文边界、只追加事实、学习版边界 | §1、§3-§9 | A3-A9 | covered；业务上下文 |
| SRC-GLO-01 `glossary.md` 全文 | complete | Money、Operation、finality、attempt、receipt、bill/run、settlement、timeline 等统一术语 | §2-§9 | A2-A9 | covered；必要依赖 |
| SRC-LC-01 `lifecycle.md` §1 | complete | 同步拒绝与 Operation 受理/观察边界 | §2.4-§2.6 | A2 | covered |
| SRC-LC-02 `lifecycle.md` §2 | complete | 支付创建、attempt、未知、到期、重复、迟到、冲突 | §3 | A3 | covered |
| SRC-LC-03 `lifecycle.md` §3 | complete | 退款申请、attempt、预算预占/释放/转换与未知 | §4 | A4 | covered |
| SRC-LC-04 `lifecycle.md` §4 | complete | AuthoritativeBill、revision、ReconciliationRun、差异与处置 | §6 | A6 | covered |
| SRC-LC-05 `lifecycle.md` §5 | complete | settlement scope、构成、冻结、执行、未知、作废和替代 | §7 | A7 | covered |
| SRC-LC-06 `lifecycle.md` §6-7 | complete | payment 全链路与外部交换身份/时间/重复语义 | §8-§9 | A8-A9 | covered |
| SRC-PRO-01 `reference-learning-profile.md` §1-2 | complete | 不取两个后端能力交集；十类完整学习版能力 | §1、§3-§9 | A1、A3-A9 | covered |
| SRC-PRO-02 `reference-learning-profile.md` §2.1 | complete | 单进程内仍必须保留领域幂等、去重、UNKNOWN、预算、阻断、冻结、责任字段与 trace | §2-§9 | A2-A9 | covered |
| SRC-PRO-03 `reference-learning-profile.md` §3-4 | complete | 生产强化非目标与不得混淆的领域义务 | §12 | A13 | covered；non-goal |
| SRC-PRO-04 `reference-learning-profile.md` §5 | complete | ReferencePolicy 默认值与覆盖方式 | §9.1 | A9 | covered |
| SRC-PRO-05 `reference-learning-profile.md` §6 | complete | reference channel/bill/executor、逻辑时钟、fixture、双后端共同验收 | §9.2-§9.4、§11 | A9、A12 | covered |
| SRC-CON-01 `reference-backend-contract.md` §1-2 | complete | 稳定 ID、RFC3339 时间、Money、status/finality、引用与审计字段 | §2 | A2 | covered |
| SRC-CON-02 `reference-backend-contract.md` §3 | complete | 命令幂等、OperationReceipt、Operation、readAfter、ApiError | §2.4-§2.6 | A2 | covered |
| SRC-CON-03 `reference-backend-contract.md` §4 | complete | 支付/退款/账单/对账/结算/通知/人工核对对象与状态 | §3-§8 | A3-A8 | covered |
| SRC-CON-04 `reference-backend-contract.md` §5.1 | complete | 规范化命令全集 | §3-§8、§10 | A2-A8 | covered |
| SRC-CON-05 `reference-backend-contract.md` §5.2-5.3 | complete | 查询全集、五权威列表、筛选与 opaque keyset | §5、§10 | A5 | covered |
| SRC-CON-06 `reference-backend-contract.md` §6-10 | complete | 支付、退款、对账、结算、人工/通知/timeline 完整流程 | §3-§8 | A3-A8 | covered |
| SRC-CON-07 `reference-backend-contract.md` §11 | complete | 确定性 sandbox 执行器与干净运行可重复性 | §9 | A9、A12 | covered |
| SRC-TRACE-01 `traceability.yaml` 全文 | complete | 62 条 PAY-BR 与 67 条 PAY-AC 双向映射；模板状态全为 planned | §13 | A14 | covered；追踪真源 |
| SRC-WB-01 `backend-alignment.md` | complete | 仅用于识别现状缺口，不作为目标、约束或验收替代物 | — | — | background |

### PAY-BR 完整覆盖

下表逐项保留 62 条规则；“验收”同时给出本 change 的分组验收和真源 PAY-AC 别名，后者在目标 Spec 中拥有同名 `Scenario:`。

| 来源规则 | 保留的不变量 | Spec | 验收 |
|---|---|---|---|
| PAY-BR-001 | 同商户创建支付的幂等身份唯一 | §2.4、§3 | A2/A3；AC-001/002/003 |
| PAY-BR-002 | 同 payload 重放原结果，冲突 payload 无副作用 | §2.4、§3 | A2/A3；AC-002/003 |
| PAY-BR-003 | 同订单活动或成功支付拒绝再次收款 | §3 | A3；AC-011 |
| PAY-BR-004 | 渠道结果只生效一次但每次接收留痕 | §3.4 | A3；AC-005 |
| PAY-BR-005 | 商户退款号唯一且冲突内容拒绝 | §4 | A4；AC-026 |
| PAY-BR-006 | merchant+currency+period 最多一份有效结算 | §7 | A7；AC-068 |
| PAY-BR-010 | 支付/退款 Money 正值且精确合法 | §2.2 | A2；AC-012 |
| PAY-BR-011 | 支付金额和币种创建后不可改写 | §3 | A3；AC-013 |
| PAY-BR-012 | 支付、attempt、退款、对账、结算同币种 | §2.2、§3-§7 | A2；AC-012/020/027/040/060 |
| PAY-BR-013 | 金额、费率与舍入使用最小单位 | §2.2、§7 | A2/A7；AC-060 |
| PAY-BR-014 | 支付成功时冻结费用规则快照 | §3.5、§7 | A3/A7；AC-014/060 |
| PAY-BR-015 | 净额恒等于收入-退款-费用+调整 | §7.3 | A7；AC-060 |
| PAY-BR-020 | 仅可收款商户与合格渠道可创建支付 | §3.1 | A3；AC-001/084 |
| PAY-BR-021 | 每次渠道付款形成独立 attempt 且不覆盖 | §3.2 | A3；AC-004/010/083 |
| PAY-BR-022 | 一支付最多一份被接受成功事实 | §3.4 | A3；AC-010 |
| PAY-BR-023 | 支付成功事实不被失败/关闭结果回退 | §3.4 | A3；AC-005/015 |
| PAY-BR-024 | 未验真或身份/金额/币种不符不得生效 | §3.3-§3.4 | A3；AC-006 |
| PAY-BR-025 | 渠道受理、发送或跳转不等于成功 | §3.2 | A3；AC-016 |
| PAY-BR-026 | 到期且无未决 attempt 才可关闭 | §3.5 | A3；AC-007/008 |
| PAY-BR-027 | 终态后迟到成功进入核对并阻断结算 | §3.4、§8 | A3/A8；AC-009 |
| PAY-BR-028 | 支付成功后禁止新 attempt | §3.2 | A3；AC-017 |
| PAY-BR-030 | 仅成功支付、同商户同币种可退款 | §4.1 | A4；AC-020/027 |
| PAY-BR-031 | 成功+预占+新申请不得超原支付 | §4.2 | A4；AC-020/021/022/023 |
| PAY-BR-032 | 并发退款检查与预占原子防超退 | §4.2、§10.2 | A4/A10；AC-023 |
| PAY-BR-033 | 超退款窗口同步拒绝；例外责任字段完整 | §4.1、§8 | A4/A8；AC-028 |
| PAY-BR-034 | 整体失败/拒绝释放；UNKNOWN 继续占用 | §4.2-§4.4 | A4；AC-024/025 |
| PAY-BR-035 | 退款成功事实不回退 | §4.4 | A4；AC-029 |
| PAY-BR-036 | 错误退款以独立事实更正而不篡改 | §4.4、§6.4 | A4/A6；AC-082 |
| PAY-BR-040 | 对账范围/revision 只有一个有效 run | §6.2 | A6；AC-040/045 |
| PAY-BR-041 | 平台事实、账单和 run 分别不可改写 | §6 | A6；AC-040-045/047/082 |
| PAY-BR-042 | 优先稳定渠道交易号，辅助匹配保留依据 | §6.3 | A6；AC-040/043/044 |
| PAY-BR-043 | 差异至少覆盖七类并保留匹配结果 | §6.3 | A6；AC-041/042/043 |
| PAY-BR-044 | 差异处置记录责任、时间、证据、结论和动作 | §6.4 | A6/A8；AC-044/045/047/082 |
| PAY-BR-045 | 未决状态/金额/币种差异阻断结算 | §6.3、§7.2 | A6/A7；AC-041/043/061 |
| PAY-BR-046 | 账单完整且每项差异有结论才能完成 | §6.3 | A6；AC-046 |
| PAY-BR-050 | 结算按商户和币种隔离 | §7.1 | A7；AC-060/068 |
| PAY-BR-051 | 资金事实不得被两个 confirmed settlement 重算 | §7.2 | A7；AC-062 |
| PAY-BR-052 | 每个结算项追到支付、退款、费用或调整 | §7.2-§7.3 | A7/A8；AC-060/083 |
| PAY-BR-053 | confirm 冻结 scope、items、金额和版本 | §7.3 | A7；AC-067 |
| PAY-BR-054 | UNKNOWN 执行禁止换 identity 或新单重付 | §7.4 | A7；AC-064 |
| PAY-BR-055 | 结算成功不被迟到失败回退 | §7.4 | A7；AC-065 |
| PAY-BR-056 | 负净额进入人工核对且不自动执行 | §7.3 | A7；AC-066 |
| PAY-BR-060 | merchantId 用于业务范围、校验和筛选 | §2.1、§5 | A2/A5；AC-080 |
| PAY-BR-061 | 敏感人工动作责任字段完整 | §8.1 | A8；AC-044/082/086 |
| PAY-BR-062 | 资金事实不删除，纠错追加关联事实 | §2.3、§6.4、§8 | A2/A8；AC-047/082 |
| PAY-BR-063 | 原始时间与业务时区统一计算边界 | §2.1、§9.1 | A2/A9；AC-007/040/060/085 |
| PAY-BR-064 | 退役渠道禁新尝试但保留历史快照 | §3.1-§3.2 | A3；AC-084 |
| PAY-BR-065 | 同步拒绝只返回 ApiError，receipt 只表示受理 | §2.5 | A2；AC-090 |
| PAY-BR-066 | Operation 可查并按 readAfter 收敛 | §2.5 | A2；AC-091/092 |
| PAY-BR-067 | 五权威列表以绑定筛选的 keyset 稳定分页 | §5 | A5；AC-093 |
| PAY-BR-068 | 支付提交/结果收件全量追加并可查询 | §3.3-§3.4 | A3；AC-100 |
| PAY-BR-069 | refund 预占属于申请，attempt 不重复转换 | §4.2-§4.4 | A4；AC-099 |
| PAY-BR-070 | 权威 bill revision 单调且不可改写 | §6.1 | A6；AC-087/094 |
| PAY-BR-071 | ReconciliationRun 是唯一执行/列表/重跑资源 | §6.2 | A6；AC-045/094 |
| PAY-BR-072 | DifferenceDisposition/FactConfirmation 只追加 | §6.4 | A6；AC-095 |
| PAY-BR-073 | settlement prepare 为每个候选给 included/excluded 原因 | §7.2 | A7；AC-096 |
| PAY-BR-074 | execution identity 稳定，三结果语义明确 | §7.4 | A7；AC-063/064/097 |
| PAY-BR-075 | 作废/替代不得绕过 UNKNOWN | §7.5 | A7；AC-068/098 |
| PAY-BR-076 | 通知 identity/content 稳定且首次结算成功原子可见 | §8.2、§10.3 | A8/A10；AC-081/088 |
| PAY-BR-077 | payment timeline 覆盖全部关联事实并稳定排序 | §8.3 | A8；AC-083 |
| PAY-BR-078 | policy+fixture+clock+sandbox 从干净运行确定复现 | §9 | A9；AC-101/102 |
| PAY-BR-079 | 两后端执行同一规范化黑盒语义 | §11、§13 | A12/A14；AC-103 |

### PAY-AC 完整覆盖

下表逐项保留 67 个共享场景；目标 Spec 使用同名 `Scenario:`，Runtime 确认 Shape 后生成的 A 编号与这些稳定别名共同构成验收索引。

| 来源场景 | 需要保留的可观察结果 | Spec Scenario | 分组验收 |
|---|---|---|---|
| PAY-AC-001 | 首创支付为待支付且未付款 | §11 PAY-AC-001 | A3/A12 |
| PAY-AC-002 | 相同支付请求返回原资源且无第二支付 | §11 PAY-AC-002 | A3/A12 |
| PAY-AC-003 | 幂等冲突明确拒绝且无副作用 | §11 PAY-AC-003 | A2/A3/A12 |
| PAY-AC-004 | 可信成功形成唯一成功事实和通知意图 | §11 PAY-AC-004 | A3/A12 |
| PAY-AC-005 | 重复通知只增加收件不重复收入 | §11 PAY-AC-005 | A3/A12 |
| PAY-AC-006 | 未验真或金额不符不得生效且留拒绝证据 | §11 PAY-AC-006 | A3/A12 |
| PAY-AC-007 | 到期且无未决 attempt 关闭 | §11 PAY-AC-007 | A3/A12 |
| PAY-AC-008 | 到期但结果未知不判失败并建 review | §11 PAY-AC-008 | A3/A8/A12 |
| PAY-AC-009 | 关闭后迟到成功留痕、核对并阻断 | §11 PAY-AC-009 | A3/A8/A12 |
| PAY-AC-010 | 双 attempt 成功最多接受一次并核对 | §11 PAY-AC-010 | A3/A12 |
| PAY-AC-011 | 成功订单拒绝第二支付 | §11 PAY-AC-011 | A3/A12 |
| PAY-AC-012 | 非法金额/币种无资源无 attempt | §11 PAY-AC-012 | A2/A3/A12 |
| PAY-AC-013 | 已建支付金额币种不可修改 | §11 PAY-AC-013 | A3/A12 |
| PAY-AC-014 | 成功时费用规则快照用于结算 | §11 PAY-AC-014 | A3/A7/A12 |
| PAY-AC-015 | 成功后失败不回退且形成冲突证据 | §11 PAY-AC-015 | A3/A12 |
| PAY-AC-016 | 渠道受理不形成资金成功 | §11 PAY-AC-016 | A3/A12 |
| PAY-AC-017 | 成功后禁止新 attempt | §11 PAY-AC-017 | A3/A12 |
| PAY-AC-020 | 全额退款预算与结算扣减正确 | §11 PAY-AC-020 | A4/A12 |
| PAY-AC-021 | 多次部分退款独立且预算正确 | §11 PAY-AC-021 | A4/A12 |
| PAY-AC-022 | 超额退款同步拒绝且不发渠道 | §11 PAY-AC-022 | A4/A12 |
| PAY-AC-023 | 并发申请最多一笔占用，预算不超额 | §11 PAY-AC-023 | A4/A10/A12 |
| PAY-AC-024 | 整体失败只释放一次预占 | §11 PAY-AC-024 | A4/A12 |
| PAY-AC-025 | UNKNOWN/待核对继续占用且禁重复 | §11 PAY-AC-025 | A4/A8/A12 |
| PAY-AC-026 | 重复退款返回原 operation/refund 不再占用 | §11 PAY-AC-026 | A2/A4/A12 |
| PAY-AC-027 | 非成功支付退款同步拒绝 | §11 PAY-AC-027 | A4/A12 |
| PAY-AC-028 | 超退款窗口同步拒绝且预算不变 | §11 PAY-AC-028 | A4/A12 |
| PAY-AC-029 | 成功退款不被迟到失败回退 | §11 PAY-AC-029 | A4/A12 |
| PAY-AC-040 | 完全匹配无差异 | §11 PAY-AC-040 | A6/A12 |
| PAY-AC-041 | 平台单边保留证据并阻断结算 | §11 PAY-AC-041 | A6/A12 |
| PAY-AC-042 | 渠道单边不自动造成功事实 | §11 PAY-AC-042 | A6/A12 |
| PAY-AC-043 | 金额差异双方原值保留并阻断 | §11 PAY-AC-043 | A6/A12 |
| PAY-AC-044 | 状态差异以可信 ReferenceActorContext 的 actorId 和 reason/evidence 形成确认事实；缺 context 无副作用拒绝 | §11 PAY-AC-044 | A6/A8/A12 |
| PAY-AC-045 | 重跑区分历史且无重复有效差异 | §11 PAY-AC-045 | A6/A12 |
| PAY-AC-046 | 未决差异阻止 run 完成 | §11 PAY-AC-046 | A6/A12 |
| PAY-AC-047 | 处置追加且不改写原证据，并暴露与可信 context 一致的 actorId；缺 context 无副作用拒绝 | §11 PAY-AC-047 | A6/A8/A12 |
| PAY-AC-060 | 150-20-3=127 且逐项可追溯 | §11 PAY-AC-060 | A7/A12 |
| PAY-AC-061 | 未决交易被排除，其他可结算 | §11 PAY-AC-061 | A6/A7/A12 |
| PAY-AC-062 | 已确认资金事实不重复结算 | §11 PAY-AC-062 | A7/A12 |
| PAY-AC-063 | 稳定 identity 成功且重复不二次结算 | §11 PAY-AC-063 | A7/A12 |
| PAY-AC-064 | UNKNOWN 保留 identity、禁止重付并建 review | §11 PAY-AC-064 | A7/A8/A12 |
| PAY-AC-065 | 成功后失败不回退且核对 | §11 PAY-AC-065 | A7/A12 |
| PAY-AC-066 | 负净额保留构成但不自动执行 | §11 PAY-AC-066 | A7/A8/A12 |
| PAY-AC-067 | confirm 后 scope/items/amount/version 冻结 | §11 PAY-AC-067 | A7/A12 |
| PAY-AC-068 | 同 scope 只一有效单，作废替代双向关联 | §11 PAY-AC-068 | A7/A12 |
| PAY-AC-080 | merchant 列表与命令业务范围隔离 | §11 PAY-AC-080 | A5/A12/A13 |
| PAY-AC-081 | 通知稳定 identity/content 重试且不重复事实 | §11 PAY-AC-081 | A8/A12 |
| PAY-AC-082 | 资金更正保留全部原始与责任事实 | §11 PAY-AC-082 | A6/A8/A12 |
| PAY-AC-083 | timeline 覆盖全链并稳定排序 | §11 PAY-AC-083 | A8/A12 |
| PAY-AC-084 | 退役渠道禁新尝试且历史快照保留 | §11 PAY-AC-084 | A3/A12 |
| PAY-AC-085 | Asia/Shanghai 日界线与原始时间均保留 | §11 PAY-AC-085 | A9/A12 |
| PAY-AC-086 | 可信 actor session/context 缺失或责任字段不完整同步拒绝；完整时追加并返回解析后的 actorId | §11 PAY-AC-086 | A8/A12/A13 |
| PAY-AC-087 | bill signal/revision/retry 并发乱序收敛 | §11 PAY-AC-087 | A6/A9/A12 |
| PAY-AC-088 | 首次结算成功与通知意图同 UoW 原子可见 | §11 PAY-AC-088 | A8/A10/A12 |
| PAY-AC-090 | 同步拒绝无 Operation；受理/重放回执边界正确 | §11 PAY-AC-090 | A2/A12 |
| PAY-AC-091 | POLL、RESOURCE_NOT_READY 与 operation 收敛正确 | §11 PAY-AC-091 | A2/A12 |
| PAY-AC-092 | observation timeout 不改写业务状态 | §11 PAY-AC-092 | A2/A12 |
| PAY-AC-093 | 五列表筛选绑定 keyset 分页稳定 | §11 PAY-AC-093 | A5/A12 |
| PAY-AC-094 | Run 是唯一执行资源，revision/effectiveRun 正确 | §11 PAY-AC-094 | A6/A12 |
| PAY-AC-095 | 处置/确认只追加、返回可信 context actorId，且仅明确结论解除阻断；缺 context 无副作用拒绝 | §11 PAY-AC-095 | A6/A8/A12 |
| PAY-AC-096 | 每候选 included/excluded，确认后冻结 | §11 PAY-AC-096 | A7/A12 |
| PAY-AC-097 | execution SUCCESS/FAILURE/UNKNOWN 身份与重试正确 | §11 PAY-AC-097 | A7/A12 |
| PAY-AC-098 | void/replacement 不绕过 UNKNOWN | §11 PAY-AC-098 | A7/A12 |
| PAY-AC-099 | refund attempt 只转换预算一次且所有收件可查 | §11 PAY-AC-099 | A4/A12 |
| PAY-AC-100 | payment submission/result receipt 全量可查且成功一次 | §11 PAY-AC-100 | A3/A12 |
| PAY-AC-101 | policy 覆盖作为输入且结果确定 | §11 PAY-AC-101 | A9/A12 |
| PAY-AC-102 | 两次干净 sandbox 闭环观察一致 | §11 PAY-AC-102 | A9/A12 |
| PAY-AC-103 | CAP4K 规范化观察与统一契约等价 | §11 PAY-AC-103 | A12/A14 |

## Included work

- 调整现有 Payment、Refund、ReconciliationBatch、MerchantSettlement 聚合及其 JPA owned graph；保留 CAP4K transactional style，不改造成 Saga 或事件溯源。
- 增加 Operation、AuthoritativeBill/BillRevision、ManualReview、MerchantNotification 与 payment timeline 所需的权威事实/查询模型；内部聚合命名可保留，但公开对账主资源统一为 `ReconciliationRun`。
- 重整 reference API、Endpoint contract、HTTP binding 与统一 DTO；不保留旧接口兼容层。
- 建立后端权威 list/filter/keyset 查询，不允许 Controller 或前端读取全部聚合临时拼装。
- 建立 trusted reference/sandbox callback verifier、ReferencePolicy、逻辑时钟、稳定 fixture/channel/bill/executor/notification 脚本。
- 补齐 domain、JPA/UoW/事务、Endpoint contract、HTTP、并发、fixture、共享黑盒和真实服务 HTTP E2E。

# 非目标

- 登录、JWT、OIDC、真实 RBAC、双人授权；学习版只验证 reference actor context 的责任字段完整性。
- 生产级商户安全隔离；`merchantId` 仍是业务范围、筛选与校验字段。
- 生产数据库部署、重启恢复、历史迁移或兼容旧 schema/API。
- 通用消息平台、持久化 Inbox、跨进程 exactly-once、多实例 scheduler lease。
- 生产网关、正式 CORS/CSRF、限流、TLS、Secret Manager、真实渠道证书、真实账单下载和真实资金移动。
- 生产 observability/SLA/长期审计/脱敏、周结、负净额追偿或其他产品扩展。
- 修改 WOW、统一模板、工作台或把它们当前能力当作目标上限。

# 验收示例

- **A1 真源覆盖**：固定提交下指定的业务文档、62 条 PAY-BR、67 条 PAY-AC 与 traceability 已完整读取并逐项映射；工作台文档只标为 background。
- **A2 统一基础契约**：Money、稳定 ID/时间、status/finality、命令幂等、OperationReceipt/Operation/readAfter、ApiError 与同步拒绝边界符合统一契约。
- **A3 支付闭环**：显式 PaymentAttempt、提交回执、可信结果、重复/冲突/迟到/未知、到期、单一成功事实、费用快照与查询完整可验。
- **A4 退款闭环**：退款申请具有独立 idempotencyKey、merchantRefundNo、Money、reason；Refund、RefundAttempt、结果收件和预算预占/释放/转换分离且并发守恒。
- **A5 权威查询**：支付、退款、ReconciliationRun、Settlement、ManualReview 五列表支持规定筛选、不可变排序键与绑定筛选的 opaque keyset cursor。
- **A6 账单与对账**：权威账单/revision、bill available/范围发现、Run/rerun、差异、追加处置和补录确认事实完整，Run 是唯一公开执行资源。
- **A7 结算闭环**：scope 只由 merchant+currency+period 定义；候选 included/excluded、确认冻结、稳定 execution、三结果、作废/替代与范围唯一完整可验。
- **A8 人工、通知与 trace**：统一命令的 `actorId` 由可信 `ReferenceActorContext` 映射，所有处置事实返回同一 actorId，缺失 context 同步拒绝且无副作用；记录时间来自服务端逻辑时钟；通知稳定重试；payment timeline 覆盖全部跨聚合事实并稳定排序。
- **A9 稳定 sandbox**：默认/覆盖 ReferencePolicy、逻辑时钟、channel/bill/settlement/notification 脚本和 fixture 从干净运行产生确定结果。
- **A10 CAP4K 风格与事务**：聚合、JPA、Command/UoW、乐观锁、领域事件和本地可靠事件保留；关键跨聚合更新、结算完成与通知意图具备同 UoW 证据。
- **A11 分层验证**：domain、事务/JPA、Endpoint contract、HTTP binding、并发、查询分页、fixture 与 composition 测试全部通过。
- **A12 共享黑盒**：§11 的 67 个同名 PAY-AC Scenario 每一项都必须同时拥有自动化证据，以及启动真实 CAP4K 服务后由进程外 HTTP client 只经公开 reference/business HTTP surface 执行的真实 HTTP 证据；MockMvc 或进程内规范化测试不得替代逐场景 HTTP 验收。在 67 项逐场景 HTTP 全通过的基础上，真实服务还必须至少完成两次干净的支付到结算闭环，作为额外可重复性证明。
- **A13 学习版边界诚实**：不把 reference actor、merchantId、H2、sandbox、ordinary scheduler 或本地可靠事件描述成生产认证、隔离、资金或 exactly-once 保证。
- **A14 可追溯交付**：最终报告列出全部 PAY-BR/PAY-AC、实现位置、自动化与真实 HTTP 证据，模板的 planned 状态不被当作本仓库实现结论。

# 约束与不变量

- 只修改 `cap4k-reference-payment`；固定外部真源只读。
- 不删除或降级现有 JPA、事务边界、领域事件、Unit of Work 与本地可靠事件能力。
- 不用 WOW 接口、事件溯源、最终一致方案或两个后端能力交集替代统一契约。
- 不为兼容旧接口删减统一业务能力；可以直接调整现有 reference API。
- `requestedBy`、`operatorRole`、`requestedAt` 及 body 自报的 `actorId` 不是可信系统事实。reference HTTP adapter 只接收可信 header `X-Reference-Actor-Context` 中的不透明 fixture/session alias，并在服务端 `ReferenceActorRegistry` 中校验、解析为 `ReferenceActorContext(actorId, role)`；非 HTTP fixture runner 使用同一 registry/session alias。adapter 将解析后的 actorId 注入统一命令，最终 DifferenceDisposition、FactConfirmation、ManualReview 处置和 settlement void/replacement 事实均返回同一 actorId，`recordedAt` 来自服务端逻辑时钟。缺失或未知 alias 同步 `VALIDATION_ERROR`，不创建 Operation 或业务副作用。
- callback 请求不能携带可直接决定验真结论的 `verified` 或等价字段；可信 verifier 根据服务端持有的 reference evidence 和 canonical payload 派生结论。
- 所有列表来自后端 JPA 权威查询模型或 projection；Controller/前端不得全量加载后临时拼装。
- settlement scope 的唯一性只基于 `merchantId + currency + settlementPeriod`；`channelId` 是明细证据而不是 scope 维度，`settlementDate` 只能作为 period 表达的一部分。
- UNKNOWN、重复、冲突、迟到、未知引用、退款预算、对账阻断与结算防重付均属于本次领域范围，不得列为生产强化后续。

# 决策

- 复用 active change `align-unified-reference-contract`，工作区为当前 `main`；当前仅有 Comet 初始化产生的 `.gitignore` 与 change 文档改动，无需另建分支/worktree。
- 使用一个普通 Native change，不建立 Supervisor children；需求跨域但共享基础契约、查询模型、fixture 和 E2E 高度耦合，且用户明确要求一次性交付。
- 修改既有 capability `payment-reference-build`，目标规格位于 `specs/payment-reference-build/spec.md`；不建立平行业务能力树。
- 保留 `Payment`、`Refund`、`ReconciliationBatch`、`MerchantSettlement` 内部聚合风格；公开 contract 使用 `PaymentIntent`、`Refund`、`ReconciliationRun`、`Settlement`、`ManualReviewItem` 的统一语义。
- 事务内同步完成的 CAP4K command 也创建稳定 Operation，通常返回 `READ_ONCE/SUCCEEDED`；外部结果尚未收敛不影响 command operation 已完成，领域资源以自身 status/finality 表达后续状态。
- trusted sandbox verifier 使用服务端 reference evidence registry/canonical payload 派生 verification；业务请求方不提供 verdict。
- 统一命令要求的 `actorId` 在 CAP4K reference 环境中由 `X-Reference-Actor-Context` 携带的 opaque fixture/session alias 经服务端 `ReferenceActorRegistry` 解析；不接受 body 自报 actorId，最终人工事实暴露解析后的 actorId。
- 对账与结算保留 channel 证据和明细，但结算 scope 去除 channel 维度。
- 稳定 E2E 通过 reference profile、逻辑时钟、fixture alias 与干净 H2 运行实现；不引入生产 reset/admin 面。
- `PAY-AC-103` 在 CAP4K 中证明对统一规范化观察的符合性；不修改或宣称 WOW 已通过。

# 待解决问题

- 无。用户已明确固定真源、业务范围、非目标、单 change 交付、可调整 API、事务风格与最终验证方式；调查未发现需要改变用户可见结果的歧义。

# 验证预期

- 静态：所有真源条目、BR/AC 映射、Endpoint contract、HTTP binding、JPA query/read model、schema/unique constraint、generator ownership、Analyzer/AgentFacts 不漂移。
- Domain：Money、finality、attempt/receipt disposition、refund budget、bill revision、effectiveRun、settlement freeze/execution/void/replacement、timeline ordering 的不变量测试。
- 事务/JPA：真实 H2 UoW、回滚、乐观锁、唯一约束和双事务并发；退款预算与结算 scope 不出现双写。
- Contract/HTTP：统一 OperationReceipt、ApiError、列表 cursor、trusted callback、责任 context、全部 command/query/endpoint contract。
- 共享黑盒：67 个 PAY-AC 使用相同默认或显式覆盖 policy、fixture、logical clock、sandbox scripts，并逐项具有自动化 pass 证据。
- 真实 HTTP：67 个 PAY-AC 每项均须在启动后的实际 CAP4K 服务上由进程外 HTTP client 执行并记录 pass 证据；共享 runner 可以驱动 HTTP，但 MockMvc 或进程内规范化测试不能替代。全部逐场景通过后，再额外执行至少两次支付、退款、账单/对账、结算、通知、人工核对和 trace 的干净完整闭环，证明重复运行结果一致。
- 回归：按项目可用命令执行 focused tests、模块测试、最终 `clean build`、boot/startup smoke、生成/Analyzer/AgentFacts/traceability 守卫；失败、跳过、未执行或过期记录不算通过。
- 交付报告必须给出已覆盖 PAY-BR/PAY-AC、统一契约实现映射、全部测试与真实 HTTP 证据，以及仅限生产强化的剩余项。
