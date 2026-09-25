# CAP4K 统一支付自动化与真实 HTTP 证据目录

本目录对应固定真源提交 `3b66db675356e77c720081e0410baf444b7baa9c`，列出全部 67 个 PAY-AC。最终判定以[验收指南](payment-acceptance-guide.md)、[traceability](../traceability.yaml)和真实 HTTP `summary.json` 为准。

## 1. 证据层次

| 层次 | 入口 | 证明内容 |
|---|---|---|
| Domain | `domain/src/test` | 状态机、Money、幂等、冲突、迟到、UNKNOWN、退款预算、冻结和追加式证据 |
| Application/JPA/UoW | `start/src/test` | H2 持久化、事务回滚、并发、跨聚合协作、可靠事件当前运行期原子可见 |
| Contract/Adapter | `contract`、`adapter/src/test` | Endpoint DTO、HTTP binding、ApiError、OperationReceipt、actor context、fixture/verifier |
| 自动化真实 HTTP | `scripts/acceptance/run-pay-ac-http.ps1` | 独立 JVM + 进程外 client 的 67 个公开 HTTP 场景与两次 clean loop |

读取每份场景 evidence 时按同一结构核对：Given / Arrange 是 fixture、policy、clock 和 trusted evidence；When / Act 是真实 HTTP 请求；Then / Assert 同时观察支付状态、尝试状态、成功事实、复核、通知意图、结算资格、持久化结果对应的公开查询模型。

## 2. 执行命令

```powershell
# 最终自动化真实 HTTP；focused test 不能替代这一条
$evidenceRoot = "build\reference-http-evidence\candidate-$((Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ'))"
.\scripts\acceptance\run-pay-ac-http.ps1 `
  -Port 18100 `
  -EvidenceRoot $evidenceRoot

# 分层回归
.\gradlew.bat :domain:test --console=plain
.\gradlew.bat :application:test :contract:test :adapter:test --console=plain
```

不同 `@SpringBootTest` class 可能受 CAP4K 全局 IoC provider 生命周期影响，聚焦验证时使用独立 Gradle invocation；最终仍需执行完整候选回归。

## 3. 67 项证据矩阵

所有行的分层自动化状态均为 `verified`。自动化入口从[registry](../../../start/src/test/resources/reference-http/pay-ac-scenarios.json)解析，并由[场景模块](../../../scripts/acceptance/PayAcScenarios.psm1)执行；HTTP 文件名相对于当前运行选择的 `-EvidenceRoot`（省略时为 `build/reference-http-evidence/<runId>/`），其 PASSED 状态必须由该候选的 evidence 文件证明。

| PAY-AC | 状态 | 自动化 fixture | 真实 HTTP 证据 |
|---|---|---|---|
| PAY-AC-001 | verified | payment-create | `PAY-AC-001.json` |
| PAY-AC-002 | verified | payment-create-replay | `PAY-AC-002.json` |
| PAY-AC-003 | verified | payment-create-conflict | `PAY-AC-003.json` |
| PAY-AC-004 | verified | payment-success | `PAY-AC-004.json` |
| PAY-AC-005 | verified | payment-duplicate | `PAY-AC-005.json` |
| PAY-AC-006 | verified | payment-invalid-callback | `PAY-AC-006.json` |
| PAY-AC-007 | verified | payment-expiry | `PAY-AC-007.json` |
| PAY-AC-008 | verified | payment-unknown-expiry | `PAY-AC-008.json` |
| PAY-AC-009 | verified | payment-late-success | `PAY-AC-009.json` |
| PAY-AC-010 | verified | payment-double-success | `PAY-AC-010.json` |
| PAY-AC-011 | verified | payment-order-unique | `PAY-AC-011.json` |
| PAY-AC-012 | verified | payment-invalid-money | `PAY-AC-012.json` |
| PAY-AC-013 | verified | payment-immutable | `PAY-AC-013.json` |
| PAY-AC-014 | verified | payment-fee-snapshot | `PAY-AC-014.json` |
| PAY-AC-015 | verified | payment-success-then-failure | `PAY-AC-015.json` |
| PAY-AC-016 | verified | payment-accepted | `PAY-AC-016.json` |
| PAY-AC-017 | verified | payment-success-no-retry | `PAY-AC-017.json` |
| PAY-AC-020 | verified | refund-full | `PAY-AC-020.json` |
| PAY-AC-021 | verified | refund-partial | `PAY-AC-021.json` |
| PAY-AC-022 | verified | refund-over-budget | `PAY-AC-022.json` |
| PAY-AC-023 | verified | refund-concurrent | `PAY-AC-023.json` |
| PAY-AC-024 | verified | refund-failure | `PAY-AC-024.json` |
| PAY-AC-025 | verified | refund-unknown | `PAY-AC-025.json` |
| PAY-AC-026 | verified | refund-replay | `PAY-AC-026.json` |
| PAY-AC-027 | verified | refund-ineligible | `PAY-AC-027.json` |
| PAY-AC-028 | verified | refund-expired | `PAY-AC-028.json` |
| PAY-AC-029 | verified | refund-success-then-failure | `PAY-AC-029.json` |
| PAY-AC-040 | verified | reconciliation-matched | `PAY-AC-040.json` |
| PAY-AC-041 | verified | reconciliation-platform-only | `PAY-AC-041.json` |
| PAY-AC-042 | verified | reconciliation-channel-only | `PAY-AC-042.json` |
| PAY-AC-043 | verified | reconciliation-amount-mismatch | `PAY-AC-043.json` |
| PAY-AC-044 | verified | reconciliation-fact-confirmation | `PAY-AC-044.json` |
| PAY-AC-045 | verified | reconciliation-rerun | `PAY-AC-045.json` |
| PAY-AC-046 | verified | reconciliation-blocked | `PAY-AC-046.json` |
| PAY-AC-047 | verified | reconciliation-disposition | `PAY-AC-047.json` |
| PAY-AC-060 | verified | settlement-normal | `PAY-AC-060.json` |
| PAY-AC-061 | verified | settlement-excluded | `PAY-AC-061.json` |
| PAY-AC-062 | verified | settlement-no-double-count | `PAY-AC-062.json` |
| PAY-AC-063 | verified | settlement-success | `PAY-AC-063.json` |
| PAY-AC-064 | verified | settlement-unknown | `PAY-AC-064.json` |
| PAY-AC-065 | verified | settlement-conflict | `PAY-AC-065.json` |
| PAY-AC-066 | verified | settlement-negative | `PAY-AC-066.json` |
| PAY-AC-067 | verified | settlement-freeze | `PAY-AC-067.json` |
| PAY-AC-068 | verified | settlement-scope | `PAY-AC-068.json` |
| PAY-AC-080 | verified | merchant-scope | `PAY-AC-080.json` |
| PAY-AC-081 | verified | notification-retry | `PAY-AC-081.json` |
| PAY-AC-082 | verified | fact-correction | `PAY-AC-082.json` |
| PAY-AC-083 | verified | payment-timeline | `PAY-AC-083.json` |
| PAY-AC-084 | verified | retired-channel | `PAY-AC-084.json` |
| PAY-AC-085 | verified | business-timezone | `PAY-AC-085.json` |
| PAY-AC-086 | verified | actor-context | `PAY-AC-086.json` |
| PAY-AC-087 | verified | bill-revision | `PAY-AC-087.json` |
| PAY-AC-088 | verified | settlement-notification | `PAY-AC-088.json` |
| PAY-AC-090 | verified | operation-boundary | `PAY-AC-090.json` |
| PAY-AC-091 | verified | operation-poll | `PAY-AC-091.json` |
| PAY-AC-092 | verified | operation-timeout | `PAY-AC-092.json` |
| PAY-AC-093 | verified | authority-lists | `PAY-AC-093.json` |
| PAY-AC-094 | verified | run-authority | `PAY-AC-094.json` |
| PAY-AC-095 | verified | reconciliation-decisions | `PAY-AC-095.json` |
| PAY-AC-096 | verified | settlement-candidates | `PAY-AC-096.json` |
| PAY-AC-097 | verified | settlement-outcomes | `PAY-AC-097.json` |
| PAY-AC-098 | verified | settlement-replacement | `PAY-AC-098.json` |
| PAY-AC-099 | verified | refund-attempt-budget | `PAY-AC-099.json` |
| PAY-AC-100 | verified | payment-receipts | `PAY-AC-100.json` |
| PAY-AC-101 | verified | policy-override | `PAY-AC-101.json` |
| PAY-AC-102 | verified | clean-loop | `PAY-AC-102.json` + `clean-loop-1/2.json` |
| PAY-AC-103 | verified | cross-backend-input | `PAY-AC-103.json`（不声称 WOW 通过） |

## 4. 聚焦自动化索引

以下证据用于定位分层行为，不能替代最终真实 HTTP：

| 族 | 精确测试 |
|---|---|
| 支付 | `PaymentReferenceApplicationTests#create attempt confirm duplicate conflict and query form one durable payment chain` |
| 退款 | `PaymentReferenceApplicationTests#a successful payment can be refunded in full` |
| 对账 | `ReconciliationReferenceApplicationTests#daily reconciliation matches payment and refund facts and exposes one effective run` |
| 结算 | `MerchantSettlementReferenceApplicationTests#merchant settlement lifecycle produces net 127 and preserves callback evidence` |
| 跨聚合 | `MerchantSettlementReferenceApplicationTests#payment refund reconciliation and settlement preserve one durable composition trail` |

此外，本 change 新增或扩展 PaymentAttemptLifecycle、AuthoritativeBill、ReconciliationOperation、MerchantSettlement、MerchantNotification、OperationProtocol、PaymentReviewOperation、PaymentTimeline 与 reference fixture tests，覆盖显式 attempt、trusted verifier、actor context、权威列表、Operation 观察和 trace。

## 5. 判定边界

- H2、Fake/reference provider、逻辑时钟、脚本化账单/executor/notification 只证明 reference 可重复性，不宣称生产能力。
- 独立 204 sink 证明当前运行期 integration-event identity/retry，不证明下游业务 exactly-once。
- Analyzer Flow 是静态证据；MockMvc 与进程内测试不能替代启动后真实 HTTP。
- 测试 receiver 不是生产商户通知服务。
