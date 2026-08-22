# Outcome

在 accepted `main` 基线 `3fd59cda87e3f2430fea88092a08e1b1939936bb` 上完成 GitHub Issue #4 的 Payment 专属生命周期硬化：支付到期时按未决 attempt 裁决关闭或结果待确认；迟到成功、双成功、成功后失败/未知和 notification payload 冲突都追加保留证据；授权核对可以追加终局决定；未解决 review/conflict 会真实阻断 B3 对账事实和 B4 结算候选；支付成功事实、手续费快照和商户成功通知意图仍最多形成一次。

该 change 只闭合 #4 的剩余 REFPAY-2 与聚焦 REFPAY-6 证据。完成并合入后，#8 才能在 accepted `main` lineage 上执行最终 composition audit。

# Scope

## Source coverage

| Source unit | Read status | Preserved semantics | Target Spec | Acceptance | Coverage |
|---|---|---|---|---|---|
| GitHub #4 正文与 2026-08-22 scope-alignment 评论 | complete | timeout、late result、conflict、manual review、merchant-notification business intent、并发 callback 属于 #4；HTTP Integration Event transport 属于 #12；最终 composition 属于 #8 | 10E.1-10E.13 | A1-A18 | covered |
| GitHub 父 Issue #2 | complete | #4 是 #8 前仍 required 的独立 Payment hardening；承担 REFPAY-2 剩余部分及聚焦 REFPAY-6 | 目标状态、10E.13 | A17-A18 | covered |
| GitHub #8 | complete | 只负责 accepted-lineage composition/evidence closure，不新增 #4 业务能力 | Non-goals、10E.13 | A18 | covered |
| `PAY-AC-007` | complete | 到期、待支付、无 PROCESSING/RESULT_UNKNOWN attempt 时关闭并停止新尝试 | 10E.3 | A1-A2 | covered |
| `PAY-AC-008` | complete | 到期仍有未决 attempt 时不得关闭/直接失败，必须形成核对事项 | 10E.3-10E.4 | A3-A4 | covered |
| `PAY-AC-009` | complete | CLOSED/FAILED 后可信迟到成功保留证据、进入核对并阻断结算 | 10E.5-10E.8 | A5-A6、A10-A11 | covered |
| `PAY-AC-010` | complete | 多个 attempt 成功时收入、费用与通知意图最多形成一次，并保留冲突证据 | 10E.5-10E.8 | A7、A10-A12 | covered |
| `PAY-AC-011` | complete | 新 idempotency key 不能绕过 merchant + merchantOrderNumber 已成功约束；顺序与并发路径均不产生第二个可执行成功支付 | 10E.2、10E.9 | A13 | covered |
| `PAY-AC-015` | complete | 成功后的失败或未知不回退 SUCCEEDED，追加 conflict-review evidence | 10E.5-10E.8 | A8-A10 | covered |
| `PAY-BR-003/022/023/026/027/061/062/063` 与 Payment lifecycle 1.2/1.4/1.5 | complete | success-once、不可回退、到期核对、迟到成功处置、授权操作、不可删除和时间一致 | 10E.1-10E.10 | A1-A15 | covered |
| canonical Spec、current projection、traceability | complete | B1-B5 已接受行为保持完整；PAY-CP-007/AC-007..011/015 的缺口在本 change 闭合；#8 不在本 change | 全文与 10E | A16-A18 | covered |
| accepted B1-B5 实现与归档验证 | complete | Payment/Attempt/Receipt、B3/B4 eligibility、B5 HTTP event 保持回归；84 tests/22 suites/0 failures/0 skips 是进入本 change 的基线 | 全文、10E.8、10E.12-10E.13 | A10-A18 | covered |

## Business behavior

- 新增 Payment 到期扫描 Command 与聚合裁决：每次执行重新读取当前 Payment，不以 scheduler 触发时看到的旧状态作决定。
- Payment 到期且没有 `PROCESSING` 或 `RESULT_UNKNOWN` attempt 时进入 `CLOSED`；重复扫描幂等，之后禁止创建新 attempt。
- Payment 到期且存在未决 attempt 时，Payment/attempt 进入结果待确认语义并创建稳定、可去重的 Payment review case；不得仅因到期进入 `FAILED` 或 `CLOSED`。
- `CLOSED` 或 `FAILED` 后到达的可信成功、同一 Payment 的第二个成功 attempt、成功后的失败/未知、同 notification identity 的冲突 payload 都追加 receipt 与 review/conflict evidence，不覆盖先前 receipt、终态、成功事实或手续费快照。
- Payment review 是 Payment-owned 持久化事实。review case 与其 decision/note append-preserving；更正通过追加新决定完成，不删除或原位改写历史证据。
- 授权核对支持：确认未知结果为成功、确认未知结果为失败、接受迟到成功、保留当前终态、确认单一成功并记录额外扣款/补救证据。任何路径都不能形成第二个 accepted success fact。
- Payment 查询返回 review cases、decisions、关联 attempt/receipt、当前 settlement eligibility 与 merchant notification intent/state。
- 商户成功通知保持业务层稳定 intent identity。普通首次成功进入 `READY`；有未决 conflict 时进入或保持 `HELD_FOR_REVIEW`；授权核对后可恢复 `READY` 或进入 `CANCELLED`。本 change 不实现 transport、retry 或真实通知服务。
- B3 平台事实投影保存未决 Payment review 的身份与阻断依据；即使渠道账单匹配，未决 review 也不得成为自动可结算项。
- B4 候选加载除 current effective reconciliation run 外，还必须重新核对 Payment 当前 review eligibility；不得依赖 `payment.settlementBlocked` 单一布尔字段，也不得让 review 在对账之后新建时绕过结算阻断。
- `PAY-AC-011` 纳入本 change：保留当前顺序拒绝行为，并补足数据库/事务级并发不变量，使新的 idempotency key 不能为已成功的 merchant order 形成第二个可执行成功支付。失败或关闭后是否允许创建新支付不在本 change 扩张，继续保留现行规则边界。
- 更新 schema/design/enums/value objects、current projection、traceability、README、Analyzer/Flow、AgentFacts 和 focused evidence；B1-B5 的 accepted 84-test 基线保持全量回归。

# Non-goals

- 不把本 change 扩为 #8 的最终 accepted-lineage composition audit，也不关闭父 Issue #2。
- 不修改 Refund、Reconciliation 或 Merchant Settlement 各自的领域生命周期；只在其事实投影/候选资格边界消费 Payment review evidence。
- 不引入 broker、generic Inbox/Outbox、reliable Command、only-engine、Jimmer、Saga、Event Sourcing 或通用 workflow/task engine。
- 不实现持久化/分布式 scheduler、lease、跨实例 exactly-once 或“普通 scheduler 永不漏跑”的承诺。
- 不修改 B5 的 HTTP response timeout 语义；本 change 的 timeout 只表示 Payment 业务 `expiresAt` 生命周期到期。
- 不实现生产渠道、生产认证、生产商户通知 transport、merchant notification retry、真实退款/追偿或额外扣款自动补偿。
- 不实现 published-coordinate cold start，不修改 cap4k public docs，不处理 cap4k release governance。
- 不通过删除、覆盖 receipt 或回退 `SUCCEEDED` 修正资金事实。

# Acceptance examples

- A1: 到期扫描读取一笔 `PENDING` Payment，确认不存在 `PROCESSING/RESULT_UNKNOWN` attempt 后将其置为 `CLOSED`，记录关闭时间/依据；重复扫描不产生第二次关闭事实。
- A2: `expiresAt` 到达后，`StartPaymentAttempt` 不得创建新 attempt；`CLOSED`、`FAILED`、`SUCCEEDED` 或存在未决 review 的 Payment 也不得通过竞态绕过该限制。
- A3: 到期扫描遇到 `PROCESSING` attempt 时，不把 Payment 置为 `CLOSED/FAILED`，而是进入 `RESULT_UNKNOWN` 并创建一个稳定去重的 open review case。
- A4: 已为同一 expiry/attempt 集合创建 review 后重复扫描、scheduler 重复触发或与 callback 并发，最终只保留一个对应 review case，且 receipt/attempt 历史完整。
- A5: `CLOSED` 或 `FAILED` Payment 收到可信且金额/币种/身份匹配的成功结果时，追加 receipt 与 late-success review；在授权裁决前保持原终态、阻断自动结算且不形成可发送的 merchant-success notification。
- A6: 授权人员接受迟到成功时，系统在同一 UoW 中形成唯一 success fact、唯一 merchant-order success identity、唯一手续费快照与稳定 notification intent；已有其他成功 claim 时拒绝接受并保留冲突。
- A7: 同一 Payment 的两个 attempt 分别返回可信成功时，第一份 accepted success 仍是唯一收入/手续费/通知意图；第二份成功作为真实冲突证据保存，Payment 保持 `SUCCEEDED` 并进入 review/settlement block。
- A8: `SUCCEEDED` 后收到可信失败或未知结果时，Payment、成功时间、渠道交易号、手续费快照与 success fact 均不回退；新 receipt 进入 conflict disposition 并创建或关联 review case。
- A9: 同 notification identity 重放同 payload 只增加接收次数；同 identity 不同 payload 追加冲突证据并进入 review，不覆盖首次 payload。
- A10: review case、receipt、attempt final evidence 与 review decision 均可查询；任何纠正通过追加 decision/note 完成，不删除历史。
- A11: B3 对账投影在 Payment 有未决 blocking review 时保存 review identities/reasons，并使对应 item 即使金额与渠道完全匹配也保持 settlement-blocking，直到新 run/授权处置形成可观察闭环。
- A12: B4 候选在准备结算时重新读取 Payment 当前 review eligibility；review 在旧 reconciliation run 之后才创建时仍会被排除。`payment.settlementBlocked` 即使存在也只能是派生摘要，不能单独授权结算。
- A13: 商户订单已经存在成功 Payment 后，使用新的 idempotency key 创建同订单支付返回稳定 `ORDER_ALREADY_PAID`；并发创建/成功确认不能形成两个 merchant-order success claims 或两个可执行成功支付。
- A14: ordinary scheduler 只向应用层发送 Command，不直接访问 Repository；Analyzer 产生真实 Time root，但文档明确不承诺持久化调度、分布式 lease 或 exactly-once。
- A15: Payment review adjudication 要求 operator identity、role/authorization、reason、evidence 与时间；未授权操作明确拒绝且不改变 eligibility。
- A16: HTTP/contract 查询和 review adjudication 路径、H2/JPA round-trip、唯一约束、乐观并发、callback race、scheduler/callback race 与完整 persistence 都有 focused tests。
- A17: current projection、traceability 与 canonical target Spec 将 `PAY-AC-007/008/009/010/011/015` 逐项映射到真实 evidence；`PAY-AC-015` 既有 no-rollback 证据与本 change 新增 review/eligibility 证据合并收口，不重复宣称未实现。
- A18: B1-B5 accepted 行为及进入本 change 前的 84 tests/22 suites/0 failures/0 skips 全量回归；#12 HTTP Integration Event 语义不被重新定义；#8 仍保持未开始的最终 composition slice。

# Constraints and invariants

- Payment accepted success fact、merchant-order success identity、手续费快照和 merchant-success notification intent identity 对每个允许范围最多形成一次。
- `SUCCEEDED` 不因失败、未知、关闭、取消、重复或冲突通知回退。
- attempt、receipt、review case、review decision 和资金事实 append-preserving；不得 soft-delete 或以最后回执覆盖历史。
- `expiresAt` 由创建请求的 `Instant` 转换为 UTC 持久化时间；所有到期比较使用注入 `Clock` 的同一时间语义，业务展示继续标明 `Asia/Shanghai` 业务时区边界。
- 到期检查是可重复的业务裁决，不依赖 scheduler exactly-once。scheduler 只提供普通 Time reaction；Command/aggregate 的幂等、唯一约束与乐观锁负责收敛。
- review settlement eligibility 的真源是当前有效 review cases 与授权 decisions；`settlementBlocked` 只允许作为可重建摘要或对外便利字段。
- B3 保存投影时的 review evidence 快照；B4 在候选时重新读取当前 review eligibility，避免旧对账结果绕过新冲突。
- contract 继续 dependency leaf；domain 不依赖 contract/application/adapter/start；scheduled adapter 不直接访问 Repository。
- 现有 enum numeric values 不改变；新增 enum value 只追加。
- B5 的 at-least-once HTTP Integration Event 边界保持原样，本 change 不把 Payment business intent 误称为已可靠发送。

# Decisions

- 使用一个紧耦合 Native change，不拆 Supervisor/child changes；timeout、late result、review、eligibility 与 AC-011 都修改同一 Payment 核心状态与证据边界，拆分会增加跨分支状态机漂移。
- `PAY-AC-011` 纳入 #4。理由：它是仍 planned 的 Payment reliability 行为，现有实现只有顺序查询保护；#8 明确不得新增业务能力，因此必须在 #4 完成并发/持久化 closure。
- Payment 到期本身就是 `PAY-BR-026` 在本 reference 的核对升级阈值；不额外引入 merchant-specific “到期后再等待 N 分钟”配置。到期时存在未决 attempt 即创建 review。
- 使用普通 scheduler + application Command + 执行时重读 Payment；不引入 durable scheduler、lease 或 exactly-once 声明。
- Payment 拥有 review case 与 append-only decision evidence；B3/B4 只消费稳定 identity/eligibility 投影，不建立跨聚合 ORM graph。
- `CLOSED/FAILED` 后可信成功先进入 held review，不自动覆盖终态；只有授权 `ACCEPT_SUCCESS` 决定可形成成功事实。
- `RESULT_UNKNOWN` 状态收到可信最终结果时可以追加系统裁决并关闭对应 review；成功形成一次 success fact，失败形成 `FAILED`，历史 review 仍保留。
- 双成功与成功后失败/未知永远保留首个 accepted success；review 只能决定 eligibility/补救记录，不能接受第二份收入。
- merchant notification 只建模稳定业务 intent/state，不实现 transport：正常成功 `READY`，未决冲突 `HELD_FOR_REVIEW`，授权处置后 `READY` 或 `CANCELLED`。
- 既有 `settlementBlocked` 字段不得继续作为资格真源；若为兼容保留，必须由 review evidence 派生并由测试证明 B3/B4 不单独读取它授权结算。
- 同一 merchant + merchantOrderNumber 的 accepted success 使用稳定数据库约束身份保护；失败或关闭后创建新支付的更宽规则保持本 change 之外，不借 AC-011 扩大行为。

# Open questions

- [blocking] CONFIRM: 是否确认以上 Shape，尤其确认把 `PAY-AC-011` 的顺序与并发 closure 一并纳入 #4，并采用“普通 scheduler + Payment-owned append-only review + B3/B4 双层 eligibility guard + merchant notification business intent only”的边界后进入 Build？

# Verification expectations

- Payment domain tests：到期关闭、未决 attempt 转 unknown/review、迟到成功、双成功、成功后失败/未知、receipt replay/conflict、review 决策与 success-once/fee-once/intent-once 不变量。
- H2/JPA/UoW tests：review owned graph round-trip、append-only decisions、merchant-order success 唯一约束、事务回滚、scheduler/callback 与双 callback 并发收敛、稳定 409 映射。
- HTTP/contract tests：创建支付 `ORDER_ALREADY_PAID`、review adjudication 授权/拒绝、Payment 查询完整 evidence 与 eligibility。
- B3/B4 tests：未决 Payment review 进入 reconciliation blocking snapshot；review 在 reconciliation 后创建仍被 settlement candidate direct guard 排除；授权处置与 rerun 后才恢复资格。
- Scheduler/Analyzer tests：adapter 只发送 Command，真实 Payment expiry Time root 可见，不宣称持久化/分布式/exactly-once。
- Pipeline/Generator：schema/design/enum/value-object plan 与 generation deterministic，checked-in ownership 不漂移，连续 generation 无 source difference。
- Analyzer/Drawing Board/AgentFacts：新增 Command/Endpoint/Time/Aggregate evidence，ownership 非空、analysis ok、diagnostics 无 INVALID/error/plan-evidence-invalid；live DB freshness UNKNOWN 可继续导致 PARTIAL。
- 文档与治理：README、current projection、traceability、canonical target Spec 精确区分 Payment lifecycle timeout 与 B5 HTTP response timeout；#4/#8 责任边界保持不变。
- 全量回归：至少保持进入本 change 前的 84 tests/22 suites 全通过，并运行 `clean build`、适用的 plan/generation/analysis/Mermaid/Agent Snapshot、`git diff --check` 与仓库机器路径/`mavenLocal()`/Snapshot smoke。