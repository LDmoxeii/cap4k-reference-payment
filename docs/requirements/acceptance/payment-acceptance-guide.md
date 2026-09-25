# CAP4K 统一支付 Reference 验收指南

本指南是 `align-unified-reference-contract` 的验收入口。业务真源固定为 `payment-product-template` 提交 `3b66db675356e77c720081e0410baf444b7baa9c`；本仓库的[完整目标 Spec](../../comet/changes/align-unified-reference-contract/specs/payment-reference-build/spec.md)是 CAP4K 实现约束，[traceability](../traceability.yaml)维护 62 条 PAY-BR 与 67 条 PAY-AC 的映射。

## 1. 验收结论与边界

| 项目 | 结果 |
|---|---:|
| PAY-BR | 62/62 verified |
| PAY-AC 自动化 | 67/67 verified |
| PAY-AC 真实服务 HTTP | 67/67 passed |
| 额外干净完整闭环 | 2/2 passed，digest 相同 |
| MockMvc/进程内测试替代真实 HTTP | 不允许 |

真实 HTTP 证据目录由 runner 的 `-EvidenceRoot` 指定；省略时生成 `build/reference-http-evidence/<runId>/`。当前候选的最终目录、revision 与哈希只能在完整 HTTP run 结束后写入 PAY-EV-037，不能沿用更早候选的静态路径。`summary.json` 记录 source commit、build revision、boot jar SHA-256、Java 17 launcher、client PID、67 个逐项结果及两次 clean loop；每个 `PAY-AC-*.json` 记录 fixture、请求、响应、断言、公开查询观察、CAP4K service PID 与进程外 client PID。

本次不宣称登录、JWT/OIDC、生产 RBAC/双人授权、生产租户隔离、生产数据库部署/迁移、持久化 Inbox、多实例 lease、跨服务 exactly-once、生产 gateway/CORS/CSRF/限流/Secret Manager、真实证书/账单下载/资金移动。上述边界不能削弱领域幂等、去重、冲突、迟到、UNKNOWN、退款预算、对账阻断、结算防重复执行和责任事实。

## 2. 一键真实 HTTP 验收

入口由[run-pay-ac-http.ps1](../../../scripts/acceptance/run-pay-ac-http.ps1)、[PayAcHttpSuite.psm1](../../../scripts/acceptance/PayAcHttpSuite.psm1)、[PayAcScenarios.psm1](../../../scripts/acceptance/PayAcScenarios.psm1)和[67 项 registry](../../../start/src/test/resources/reference-http/pay-ac-scenarios.json)组成。

```powershell
$evidenceRoot = "build\reference-http-evidence\candidate-$((Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ'))"
.\scripts\acceptance\run-pay-ac-http.ps1 `
  -Port 18100 `
  -EvidenceRoot $evidenceRoot
```

runner 会构建 bootJar，启动独立 Java 17 CAP4K JVM，以进程外 PowerShell HTTP client 逐项执行 67 个场景，停止主服务后再启动两套新的 JVM/H2 运行相同完整闭环。任一场景失败、未执行、证据缺失或两轮 digest 不同都会返回非零退出码。

## 3. 可信 reference context

- HTTP body 中的 `actorId`、`requestedBy`、`operatorRole`、`requestedAt` 不作为系统事实。
- 人工命令通过 `X-Reference-Actor-Context` session alias 建立 `ReferenceActorContext`；服务端 registry 映射出 actorId/role，时间来自服务端逻辑时钟。
- DifferenceDisposition、FactConfirmation、ManualReview、结算确认/作废/替代事实返回映射后的 actorId。
- 缺失、未知或错误角色在 Operation/业务事实形成前同步拒绝；PAY-AC-044、047、086、095 都覆盖无副作用断言。
- 支付成功冻结当时实际生效的 ReferencePolicy `feeRate`、`roundingMode` 与币种精度；默认 `0.006/HALF_UP`，场景覆盖时使用覆盖值。

## 4. 67 项逐场景矩阵

所有行的自动化状态均为 `verified`；逐项真实 HTTP 状态来自当前候选所选 `-EvidenceRoot` 下的同名 JSON，不能由旧目录推断。

| PAY-AC | 场景 | PAY-BR | HTTP 证据 |
|---|---|---|---|
| PAY-AC-001 | 首次创建支付 | 001,020 | `PAY-AC-001.json` |
| PAY-AC-002 | 相同内容重复创建 | 001,002 | `PAY-AC-002.json` |
| PAY-AC-003 | 幂等键冲突 | 001,002 | `PAY-AC-003.json` |
| PAY-AC-004 | 渠道成功通知 | 021 | `PAY-AC-004.json` |
| PAY-AC-005 | 重复成功通知 | 004,023 | `PAY-AC-005.json` |
| PAY-AC-006 | 未验证通知 | 024 | `PAY-AC-006.json` |
| PAY-AC-007 | 支付到期关闭 | 026,063 | `PAY-AC-007.json` |
| PAY-AC-008 | 到期但结果未知 | 026 | `PAY-AC-008.json` |
| PAY-AC-009 | 关闭后的迟到成功 | 027 | `PAY-AC-009.json` |
| PAY-AC-010 | 两个尝试同时成功 | 021,022 | `PAY-AC-010.json` |
| PAY-AC-011 | 已成功订单不得重复收款 | 003 | `PAY-AC-011.json` |
| PAY-AC-012 | 无效金额或币种被拒绝 | 010,012 | `PAY-AC-012.json` |
| PAY-AC-013 | 已创建支付的金额不可修改 | 011 | `PAY-AC-013.json` |
| PAY-AC-014 | 费用规则使用成功时快照 | 014 | `PAY-AC-014.json` |
| PAY-AC-015 | 成功后的失败结果不回退支付 | 023 | `PAY-AC-015.json` |
| PAY-AC-016 | 渠道受理不等于支付成功 | 025 | `PAY-AC-016.json` |
| PAY-AC-017 | 支付成功后不再发起新尝试 | 028 | `PAY-AC-017.json` |
| PAY-AC-020 | 全额退款 | 012,030,031 | `PAY-AC-020.json` |
| PAY-AC-021 | 多次部分退款 | 031 | `PAY-AC-021.json` |
| PAY-AC-022 | 超额退款被拒绝 | 031 | `PAY-AC-022.json` |
| PAY-AC-023 | 并发退款防超退 | 031,032 | `PAY-AC-023.json` |
| PAY-AC-024 | 退款失败释放占用 | 034 | `PAY-AC-024.json` |
| PAY-AC-025 | 退款结果待确认 | 034 | `PAY-AC-025.json` |
| PAY-AC-026 | 重复退款申请 | 005 | `PAY-AC-026.json` |
| PAY-AC-027 | 非成功支付不得退款 | 012,030 | `PAY-AC-027.json` |
| PAY-AC-028 | 超过退款期限的申请被拒绝 | 033 | `PAY-AC-028.json` |
| PAY-AC-029 | 成功退款后的失败结果不回退退款 | 035 | `PAY-AC-029.json` |
| PAY-AC-040 | 完全匹配 | 012,040,041,042,063 | `PAY-AC-040.json` |
| PAY-AC-041 | 平台单边 | 041,043,045 | `PAY-AC-041.json` |
| PAY-AC-042 | 渠道单边 | 041,043 | `PAY-AC-042.json` |
| PAY-AC-043 | 金额差异 | 041,042,043,045 | `PAY-AC-043.json` |
| PAY-AC-044 | 状态差异收敛 | 041,042,044,061 | `PAY-AC-044.json` |
| PAY-AC-045 | 对账重跑不重复 | 040,041,044,071 | `PAY-AC-045.json` |
| PAY-AC-046 | 未决差异阻断完成 | 046 | `PAY-AC-046.json` |
| PAY-AC-047 | 差异处置不改写原始证据 | 041,044,062 | `PAY-AC-047.json` |
| PAY-AC-060 | 正常结算计算 | 012,013,014,015,050,052,063 | `PAY-AC-060.json` |
| PAY-AC-061 | 未决差异不进入结算 | 045 | `PAY-AC-061.json` |
| PAY-AC-062 | 防止重复结算 | 051 | `PAY-AC-062.json` |
| PAY-AC-063 | 结算执行成功 | 074 | `PAY-AC-063.json` |
| PAY-AC-064 | 结算执行结果未知 | 054,074 | `PAY-AC-064.json` |
| PAY-AC-065 | 结算结果矛盾 | 055 | `PAY-AC-065.json` |
| PAY-AC-066 | 负结算额 | 056 | `PAY-AC-066.json` |
| PAY-AC-067 | 结算确认后构成冻结 | 053 | `PAY-AC-067.json` |
| PAY-AC-068 | 同一周期不得存在两份有效结算单 | 006,050,075 | `PAY-AC-068.json` |
| PAY-AC-080 | 商户业务范围与筛选 | 060 | `PAY-AC-080.json` |
| PAY-AC-081 | 商户通知失败重试 | 076 | `PAY-AC-081.json` |
| PAY-AC-082 | 资金事实更正留痕 | 036,041,044,061,062 | `PAY-AC-082.json` |
| PAY-AC-083 | 支付全链路追踪 | 021,052,077 | `PAY-AC-083.json` |
| PAY-AC-084 | 退役渠道不再参与新支付 | 020,064 | `PAY-AC-084.json` |
| PAY-AC-085 | 业务时区决定日界线 | 063 | `PAY-AC-085.json` |
| PAY-AC-086 | 人工动作责任字段完整并留痕 | 061 | `PAY-AC-086.json` |
| PAY-AC-087 | 账单可用信号与权威账单收敛 | 070 | `PAY-AC-087.json` |
| PAY-AC-088 | 结算完成通知原子可见并稳定重试 | 076 | `PAY-AC-088.json` |
| PAY-AC-090 | 同步拒绝与命令受理边界 | 065 | `PAY-AC-090.json` |
| PAY-AC-091 | 已受理 Operation 按 readAfter 收敛 | 066 | `PAY-AC-091.json` |
| PAY-AC-092 | Operation 观察超时不是业务失败 | 066 | `PAY-AC-092.json` |
| PAY-AC-093 | 五类权威列表稳定分页 | 067 | `PAY-AC-093.json` |
| PAY-AC-094 | ReconciliationRun 是唯一可重跑执行资源 | 070,071 | `PAY-AC-094.json` |
| PAY-AC-095 | 差异处置和补录确认追加且可解除阻断 | 072 | `PAY-AC-095.json` |
| PAY-AC-096 | 结算准备记录纳入排除并冻结 | 073 | `PAY-AC-096.json` |
| PAY-AC-097 | 结算执行三结果稳定身份处置 | 074 | `PAY-AC-097.json` |
| PAY-AC-098 | 结算作废替代不绕过未知执行 | 075 | `PAY-AC-098.json` |
| PAY-AC-099 | 退款 attempt 结果只转换一次预算 | 069 | `PAY-AC-099.json` |
| PAY-AC-100 | 支付渠道收件可查询且成功事实唯一 | 068 | `PAY-AC-100.json` |
| PAY-AC-101 | Reference Policy 覆盖结果确定 | 078 | `PAY-AC-101.json` |
| PAY-AC-102 | reference sandbox 可重复完整闭环 | 078 | `PAY-AC-102.json` |
| PAY-AC-103 | 两个参考后端执行同一黑盒场景 | 079 | `PAY-AC-103.json` |

## 5. 自动化证据层

真实 HTTP 证据之外，候选实现还必须通过：

- Domain invariant tests：支付/退款/对账/结算状态机、预算、冲突、迟到和 UNKNOWN；
- H2/JPA/UoW tests：回滚、并发防超退、唯一约束、可靠事件原子可见；
- Endpoint/HTTP tests：binding、ApiError、OperationReceipt、actor context、Money；
- Query tests：五类权威列表、filter、无快照 keyset cursor 和跨筛选拒绝；
- composition tests：Payment → Refund → Bill/Run → Settlement → Notification → Timeline。

精确测试索引见[自动化测试证据目录](payment-test-evidence-catalog.md)。focused test、MockMvc 和进程内规范化测试用于定位，不替代最终 67/67 真实 HTTP。

## 6. 证据判定规则

- `PASSED` 必须来自启动后的真实 CAP4K 服务；失败、blocked、skip、timeout、未运行和过期证据均不算通过。
- PAY-AC-102 的场景证据、额外两次 clean loop、PAY-AC-103 的 CAP4K 规范化对比输入是三个独立义务。
- PAY-AC-103 不修改或声称 WOW 的验收状态。
- 每次候选实现改变后必须重新生成最终 summary，旧分组/调试 evidence 只能用于诊断。

## 7. CAP4K schema 与机器元数据守卫

- `design/schema.sql` 是 Pipeline 的设计输入，保留 `@Managed`、`@Type`、`@Parent`、`@RefAggregate` 等机器元数据；面向学习者的中文说明使用独立 SQL `COMMENT`，不得覆盖机器注解。
- `start/src/main/resources/schema.sql` 是 reference 运行时投影，其表注释与列注释必须和设计输入保持一致；它用于本地 H2 验证，不是生产迁移脚本。
- `design/design.json`、`design/value-objects.json` 的说明、结构哈希以及 Pipeline/Analyzer/AgentFacts 输出由自动化守卫复核，统一契约扩展不得破坏既有 CAP4K authoring ownership。
