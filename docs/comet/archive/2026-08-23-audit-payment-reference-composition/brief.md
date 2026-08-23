# Outcome

在最新 accepted `origin/main` lineage 上完成 Payment → Refund → Reconciliation → Merchant Settlement → reliable HTTP Integration Event 的最终组合审计，闭合父 Issue #2 的 REFPAY-6 与 REFPAY-7，并形成可复核、可重复、可供 cap4k#27 消费的最终证据。

本 change 以 composition verification 与 evidence closure 为主，不重新实现已完成 child 的业务能力。当前审计已经确认现有 focused tests 足以分别证明 Push/Pull/scheduler/rerun 收敛、Settlement completion once、同事务 reliable event、HTTP 503/response-timeout 重试；但 `PAY-EV-027` 仍为 `planned/not-built` 且被已 verified 的 PAY-AC-082/PAY-AC-083 引用，因此 Build 必须增加一个窄范围、仅串联既有公开应用路径的 composition test，并用它闭合最终业务追踪证据。

# Scope

- 审计并机械证明 B1、B2、B3、B4、B5 与 Payment hardening #4 的 accepted commits 位于同一 `origin/main` lineage；#4 必须绑定 accepted squash commit `e702e725674c4ab1271441cf1ed011bad3b75021`，不得把 pre-archive branch heads 当作 accepted evidence。
- 从隔离且无历史构建产物的 candidate checkout 运行完整依赖解析、generation、compile、test、boot/smoke、Analyzer、Drawing Board、AgentFacts 与 traceability 检查；candidate 必须直接后继最新 accepted `origin/main`。
- 重新验证全部当前 in-scope `PAY-AC-*` 与 B1-B5+#4 回归，不把分散 archived Verify 数字直接冒充当前 accepted-lineage evidence。
- 增加一个 composition-only Spring/H2/JPA 自动化测试，在同一数据库轨迹中串联既有 Payment success、partial Refund、authoritative statement/Reconciliation、Merchant Settlement completion 与唯一 reliable outbound event；该测试只能复用既有领域、Command、Capability、Endpoint/Integration Event 入口与持久化模型。
- 验证 Push notification、provider Pull、ordinary scheduler 与 manual rerun 共用同一 batch/run identity 和幂等边界；入站 event 只表示 statement available，完整账单仍由 provider Pull 提供。
- 验证 Settlement 首次 accepted success、稳定 completion event identity 与 cap4k reliable Event/JPA record 同 UoW 原子提交，并在 HTTP 非 2xx、连接/response timeout 后以同一 identity 恢复。
- 连续运行普通 plan/generation 与 Analyzer，验证 ownership、hash、generated/checked-in/handwritten 边界、Flow/Mermaid、Drawing Board partitions 与 AgentFacts 没有无法解释的漂移。
- 修正 traceability 的 `PAY-EV-027` 矛盾，增加 final composition/accepted-lineage/clean-checkout evidence；只把真实可执行且通过的 evidence 标记为 verified，保留 PAY-AC-080/081/084/086 和更宽 capability closure 的 planned 边界。
- 更新 README、current projection、traceability、Comet verification/archive 与 GitHub governance evidence；PR 合并后更新 Issue #8、父 Issue #2 和 cap4k#27。

## Source coverage

| 来源 | 读取状态 | 保留语义 | 目标 Spec | 验收 | 覆盖状态 |
|---|---|---|---|---|---|
| GitHub Issue #8 最新正文与评论 | complete | accepted-lineage composition、clean checkout、完整证据矩阵、Push/Pull/scheduler convergence、Settlement outbound event、cap4k#27 回传；不新增业务能力 | 10F.1-10F.9 | A1-A18 | covered |
| GitHub 父 Issue #2 最新正文与评论 | complete | 闭合 REFPAY-6/REFPAY-7；全部 required child + composition evidence accepted 后才可关闭 parent | 10F.1、10F.9 | A1、A18 | covered |
| B1-B5 与 #4 accepted commits/PR/archived changes | complete | 每个 child 只证明自身 slice；#8 在一条 accepted lineage 上重新组合验证 | 10F.2-10F.8 | A1-A16 | covered |
| canonical `payment-reference-build` Spec | complete | 10A-10E 的完整已接受业务/技术合同保持回归；新增 10F 只定义最终 composition/evidence closure | 完整目标 Spec、10F | A3-A17 | covered |
| framework-neutral requirements acceptance | complete | 当前 in-scope PAY-AC 保持 verified；planned 业务边界不被 #8 偷渡实现 | 10F.7 | A3、A16、A17 | covered |
| current projection 与 traceability | complete | 刷新真实 Runtime/Generator/Analyzer/AgentFacts/evidence；闭合 PAY-EV-027 自相矛盾 | 10F.7-10F.8 | A4-A8、A16 | covered |
| README 与 tracked Analyzer/Drawing Board/AgentFacts artifacts | complete | 保留可复现命令和可移植证据；`flows/index.json` 机器 locator 不升级为 portable evidence | 10F.5-10F.8 | A4-A8、A16 | covered |
| cap4k#27 最新正文与评论 | complete | 只在 payment #8 accepted merge 后追加最终 payment evidence；不由本 change 关闭跨仓总目标 | 10F.9 | A18 | covered |
| cap4k latest release / tag | complete | 截至 2026-08-22 最新正式版仍为 v2.0.1，未包含当前 mainline Pipeline DSL；published-coordinate cold start 保持 release-gated deferred | 10F.2、10F.9 | A2、A17、A18 | covered |
| repository GitHub Actions 状态 | complete | 当前无 `.github/workflows/**`；本 change 提供本地 clean-checkout evidence，不暗示存在持续 CI，也不偷渡新增 workflow | 10F.2、10F.9 | A2、A17、A18 | covered |

# Non-goals

- 不新增或修改 Payment、Refund、Reconciliation、Merchant Settlement 的业务规则、状态机、API、事件类型或持久化模型。
- 不重新实现 #3/#5/#6/#7/#12/#4，也不把其历史 brief 复制为新 implementation backlog。
- 不新增 broker、generic Inbox/Outbox、reliable Command、DLQ UI、动态 discovery、持久化 scheduler、lease、跨实例 exactly-once 或 workflow engine。
- 不实现 only-engine、Jimmer/aggregateProjection、Endpoint Handler generator、多版本 projection 或 historical compatibility。
- 不建设生产 merchant notification service、真实支付/退款/账单/银行/清算 provider、生产认证、签名、secret rotation、租户隔离或生产审计。
- 不在 reference 仓库修改 cap4k Public Docs/Skill/release workflow，也不自动关闭 cap4k#27。
- 不新增 GitHub Actions workflow；当前无 workflow 作为独立治理事实记录。
- 不用 Composite Build、Gradle cache、`mavenLocal()`、Snapshot、私服或已知不含当前 DSL 的 v2.0.1 冒充 published-coordinate cold start。
- 不把 ordinary scheduler 宣称为 durable、distributed 或 exactly-once。
- 不把 `flows/index.json` 的机器本地 locator 当作可移植提交证据。
- 不要求生产下游 business consumer：入站 statement consumer 的业务幂等与出站 at-least-once transport + stable identity 分开证明。

# Acceptance examples

- **A1 Accepted lineage**：审计 checkout 的 `origin/main` 包含 B1 `6a40c5d…`、B2 `43a5982…`、B3 `8750a4b…`、B4 `4e34765…`、B5 `3fd59cd…` 和 #4 `e702e72…`；每个 commit、PR、Issue、archived change 映射可机械复核，且没有未接受旁支被当作 evidence。
- **A2 Clean-checkout reproducibility**：从无历史 build outputs 的隔离 candidate checkout，以显式 local Composite 指向 cap4k commit `6575866043ad34008843d7245c49563e15b38b54` 完成 dependency resolution、generation、compile、test、bootJar 与短生命周期 Spring Boot startup smoke；仓库不依赖 committed machine path、sibling path、`mavenLocal()`、Snapshot 或私服。
- **A3 Full regression**：完整 `clean build` 通过，原 accepted 100 tests / 23 suites 全部保持通过；新增 composition test 后 test/suite 数量增加或保持可解释，0 failures、0 errors、0 skips，不能用 focused tests 代替最终全量回归。
- **A4 Plan/generation determinism**：连续两轮 ordinary plan/generate/generated-source generation 的 plan hash 与 build-owned generated contents 稳定；197-item accepted ownership（137 SKIP、60 OVERWRITE）无无法解释漂移，tracked checked-in source 无 generation diff。
- **A5 Generation ownership safety**：删除 build-owned generated outputs 后可从 canonical inputs 重建；checked-in Endpoint/event contracts、VO、Behavior、Handler/listener/subscriber 与 handwritten HTTP binding 不被覆盖，plan 能解释 generator/module/output/conflict policy。
- **A6 Analyzer observability**：连续两轮 analysis plan/generate 稳定并至少保持 46 outputs、19 independent roots（13 HTTP、5 Time、1 Integration Event）；真实 HTTP Actor、Time 与 Integration Event roots/edges 与静态 reachability 一致，不伪造跨入口 exactly-once stitching。
- **A7 Drawing Board separation**：Command、Query、Capability、Endpoint、Domain Event、Integration Event 与 Aggregate Structure partitions 均可解析且包含真实 anchors；19 份 Mermaid quoted labels 通过 parser/render smoke，runtime 状态机与可靠投递状态不伪装为默认 Flow。
- **A8 AgentFacts validity**：Agent Snapshot ownership 为 197 且与 plan 对齐，analysis 为 `ok`、available outputs 至少 46，diagnostics 无 `error`、`INVALID` 或 `plan-evidence-invalid`；overall `partial` 只允许由 live DB freshness `UNKNOWN` 导致。
- **A9 Push/Pull authority**：`ChannelStatementAvailableIntegrationEvent` 只表示账单可获取，薄 listener dispatch 到既有 application path，完整 statement 仍由 `PullChannelStatement` 获取；event payload 不成为账单真源。
- **A10 Scheduler/rerun convergence**：相同 channel+currency+business-date 与 statement identity+revision 经 Push、provider Pull、ordinary scheduler、manual rerun 的重复、乱序或并发入口最终只形成一个有效 run；higher revision 成为 effective run，late lower revision 不回退 current pointer。
- **A11 Provider recovery convergence**：event 先于 statement 可读取或 provider 暂时失败时，失败/回滚可观察；provider 恢复后以相同业务/event identity 重试成功，不产生重复 batch/run/effective fact。
- **A12 Settlement completion once**：只有 MerchantSettlement 首次形成 accepted `SUCCEEDED` fact 时形成一次 local completion fact 和稳定 `MerchantSettlementCompletedIntegrationEvent` intent；FAILED、RESULT_UNKNOWN、review/conflict、void、重复或迟到 callback 不形成第二事件。
- **A13 Outbound transaction atomicity**：Settlement accepted success、稳定 event identity 与 cap4k reliable Event/JPA record 在同一 UoW 原子提交；强制 rollback 后两者都不存在，commit 后两者都存在。
- **A14 HTTP retry/recovery**：fake receiver 首次非 2xx、连接失败或 response timeout 时 reliable event 保持可观察失败/待重试；恢复后使用同一 event identity 与稳定 payload fingerprint 成功交付，不创建新业务事件逃避失败记录。
- **A15 End-to-end idempotency boundary**：重复 inbound statement event、scheduler/rerun、settlement callback、publisher retry 与 HTTP replay 不形成第二个有效 reconciliation run、Settlement completion fact 或 outbound business event；明确区分入站业务幂等与出站 at-least-once transport，测试 receiver 可收到重复 envelope。
- **A16 Composition trace and traceability closure**：新增一个 composition-only Spring/H2/JPA test，在同一数据库轨迹内完成 Payment success → partial Refund → authoritative Reconciliation → Merchant Settlement success → unique reliable outbound event，并验证 settlement lines 可回溯 Payment/Refund/Reconciliation identities；`PAY-EV-027` 因该真实测试转为 verified，所有 verified acceptance 引用的 evidence 均存在且 verified。
- **A17 Planned/deferred boundaries**：PAY-AC-080/081/084/086、PAY-EV-025/026、更宽 PAY-CP closure、production consumer、GitHub Actions 与 published-coordinate cold start 不被伪装为完成；截至 2026-08-22 latest release v2.0.1 不含当前 DSL 时，cold start 记录为 release-gated deferred 而非本次失败或通过。
- **A18 Governance closure**：Verify/Archive/PR merge 后，Issue #8、父 #2 与 cap4k#27 获得 accepted commit、archive、test/plan/analyzer/agent/traceability 摘要；#2 仅在 #8 merge 和 composition evidence accepted 后完成 child/composition checklist，cap4k#27 继续保留其 Public Docs/Skill、only-engine gate 与 published cold-start 独立责任。

# Constraints and invariants

- 业务需求真源 `docs/requirements/**` 不因实现方便被改写；仅修正 evidence/status 映射与当前投影。
- 所有 financial facts、callback/review/reconciliation/settlement/event evidence 保持 append-preserving；不得覆盖历史或回退 accepted success。
- scheduled adapter 保持薄壳，只向应用层发送 Command；ordinary scheduler 不宣称持久化、分布式或 exactly-once。
- unresolved Payment review/conflict 继续通过真实 review evidence 影响 B3/B4 eligibility，不能以单一 `payment.settlementBlocked` 布尔字段为资格真源。
- Build 只能增加最小 composition test、evidence validation、projection/traceability/README 与必要的非业务测试辅助；若发现真实业务或框架缺陷，必须回到 owning surface 修复并重新执行完整 Verify。
- canonical active Spec 是归档后的完整目标合同：10A-10E 业务语义保持不变，10F 只增加 final composition/evidence closure，并修正“#8 仍 deferred”的历史边界描述。
- 本 change 保持单一 Native change；代码测试、证据文档与治理结论共享同一组合验收，拆分会破坏同一 candidate 的可复核性。

# Decisions

1. 采用 evidence-first + minimal composition test，而不是只写文档或新增业务能力。`PAY-EV-027` 当前 `not-built` 且被 verified acceptance 引用，必须实体化。
2. active Spec 以当前 canonical `payment-reference-build` 完整复制为基础，只新增 10F final composition/evidence closure，并更新与 #8 deferred 相关的标题/交叉引用/后续边界；不重写 10A-10E 业务合同。
3. Verify 必须在 candidate commit 的独立 clean checkout/worktree 运行；Archive/PR merge 后再用 accepted merge commit 完成最终 GitHub lineage 治理确认。
4. published-coordinate cold start 是 release-gated follow-up。截至 2026-08-22 latest cap4k release 为 v2.0.1 且不含当前 mainline DSL，因此本 change 不以 Composite Build 冒充，也不把不可执行项作为 composition pass 的无条件 blocker。
5. 仓库当前没有 GitHub Actions。#8 保存本地 clean-checkout 命令与结果，不新增 workflow，也不暗示存在 CI check。
6. cap4k#27 的最终 payment evidence 只在 #8 accepted merge 后回写；该更新不自动关闭跨仓总目标。

# Open questions

- 无。用户已于 2026-08-23 明确确认目标、范围、10F 完整目标 Spec、18 项 brief 验收、最小 composition test、release/CI 边界与治理顺序，并授权进入 Build。

# Verification expectations

- Git/lineage：`git status --short --branch`、`git rev-parse HEAD origin/main`，以及对六个 required commits 逐一执行 `git merge-base --is-ancestor` / contains 检查。
- 解析/构建/启动：显式 `-Pcap4k.local.path=<cap4k@657586...>` 运行 `:contract:compileKotlin`、`clean build`、`bootJar` 与短生命周期 `:start:bootRun` startup smoke，完成后主动终止进程。
- generation：连续两轮 `cap4kPlan cap4kGenerate cap4kGenerateSources`，比较 plan hash、197/137/60 计数、tracked diff 与 build-owned generated hashes。
- Analyzer：连续两轮 `cap4kAnalysisPlan cap4kAnalysisGenerate`，比较 analysis hash、46 outputs、19 roots、Mermaid parse 与 Drawing Board partitions。
- AgentFacts：运行 `cap4kAgentSnapshot`，检查 ownership/analysis/diagnostics/DB freshness 边界。
- focused regression：运行 `ReconciliationReferenceApplicationTests`、`MerchantSettlementReferenceApplicationTests` 与新增 composition test。
- traceability consistency：解析 YAML，验证所有 verified acceptance 的 evidence 存在、status 为 verified、路径存在；planned acceptance/evidence 保持 planned/not-built。
- 最终再次执行完整 `clean build`、`git diff --check`、non-goal dependency/workflow scan，并从独立 clean checkout 保存可复核摘要。
