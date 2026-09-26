# 目标

在单一 Comet Native change 中补齐 `cap4k-reference-payment` 对统一学习版业务契约的四个公开后端缺口：权威账单及完整不可变 revision 查询、绑定 `merchantId + paymentId` 的幂等 `CloseExpiredPayment`、可配置且被 `ExecuteSettlement` 实际消费的 reference settlement executor、以及调用方提供并贯穿全部结算事实的稳定 `executionId`。

业务真源固定为只读的 `payment-product-template@3b66db675356e77c720081e0410baf444b7baa9c`；`payment-reference-workbench/docs/backend-realignment.md` 是本 change 的详细缺口与验收来源。完成时只修改 `cap4k-reference-payment`，不得以现有 CAP4K surface、前端缓存、合成回执、全局维护动作、对账 Run 输入证据或内部对象替代公开业务 API。

# 范围

## Source coverage

### 覆盖边界与真源优先级

- 详细需求来源完整覆盖 `D:/code/payment-reference-workbench/docs/backend-realignment.md` 全文。CAP4K §1-§4 是必须实现范围；WOW §1-§4 是明确非目标；“工作台已完成的差异适配”用于防止重复改后端或以已适配差异缩减本次能力；“后端完成后的前端验收”作为下游背景，其中干净 H2、真实 HTTP、定向检查和独立 Verifier 要求由本仓库提供等价且不依赖工作台改动的证据。
- 统一业务真源固定为 `D:/code/GitHub/payment-product-template` 提交 `3b66db675356e77c720081e0410baf444b7baa9c`。本轮重新核对其正式 `reference-learning-business-contract` Spec 全文及 `reference-backend-contract.md` 全文；既有 canonical `docs/comet/specs/payment-reference-build/spec.md` 是已完成全量真源覆盖后的 CAP4K 目标基线。
- 优先级为：用户本轮明确要求与 `backend-realignment.md` 的 CAP4K 缺口 > 模板统一业务契约 > 当前 CAP4K canonical Spec > 现有实现。现有实现和工作台适配只能用于调查，不能降低目标或验收。

| 来源条目与位置 | 读取状态 | 需要保留的内容 | Spec 位置 | 验收 ID | 覆盖状态 | 理由或替代关系 |
|---|---|---|---|---|---|---|
| SRC-WB-00 `backend-realignment.md` L1-L5 | complete | 不取 WOW/CAP4K 现状交集，不让前端伪造权威事实；路径与序列化可保持 CAP4K 风格 | §1、§10.4、§13 | A15 | covered | 本 change 总边界 |
| SRC-WB-C1 CAP4K §1 | complete | `GetAuthoritativeBill`/`ListBillRevisions` 返回 current 与完整 immutable history、revision 元数据、逐记录 Money/raw status/time/raw evidence；幂等、冲突、迟到低 revision 和未知 bill 行为稳定 | §6.1、§10.4 | A1-A4 | covered | 当前有效需求 |
| SRC-WB-C2 CAP4K §2 | complete | 单 Payment 到期命令绑定 merchant/payment，返回真实 OperationReceipt/readAfter，幂等且遵守成功支付和未决 attempt 规则 | §3.5、§10.4 | A5-A8 | covered | 当前有效需求 |
| SRC-WB-C3 CAP4K §3 | complete | reference settlement executor 具有 configure/reset/read 控制面；Execute 实际消费 SUCCESS/FAILURE/UNKNOWN/NO_RESULT；同 execution identity 不产生第二效果 | §7.4、§9.2、§10.4 | A9-A12 | covered | 当前有效需求 |
| SRC-WB-C4 CAP4K §4 | complete | Execute 接受并持久化 caller-supplied `executionId`；重放/冲突稳定；详情、callback、ManualReview、timeline 使用同一 ID；channel 不能替代 ID | §7.4、§8.1、§8.3、§10.4 | A10-A14 | covered | 当前有效需求 |
| SRC-WB-WOW WOW §1-§4 | complete | WOW 渠道、账单读取、通知 sender、结算 executor 的缺口 | — | — | non-goal | 用户明确禁止修改 WOW；不移植为 CAP4K 额外范围 |
| SRC-WB-DIFF “工作台已完成的差异适配” | complete | itemId、actor header、review enum、READ_ONCE、POST search、replacement、Run scope、自动完成、CAP4K bill read/notification scripts 均无需重做 | §1、§12 | — | background | 防止重复实现或误判为本轮缺口 |
| SRC-WB-AFTER “后端完成后的前端验收” | complete | 下游将更新 adapter 并跑 check/live/paired smoke；本仓库必须先提供干净 H2、进程外真实 HTTP 与定向证据 | §9.4、§10.5、§13 | A15 | covered | 工作台修改本身为非目标 |
| SRC-TPL-SPEC `reference-learning-business-contract/spec.md` §4.2-4.4、§6、§8、§9、§10、§11-§12 | complete | Operation/readAfter、支付到期、Bill/revision、settlement execution、ManualReview/timeline、executor 与共享黑盒语义 | §2、§3.5、§6-§10、§13 | A1-A15 | covered | 统一业务真源必要依赖 |
| SRC-TPL-CON `reference-backend-contract.md` §2-§3、§4.3-4.4、§5、§6.1、§8.1、§9.2、§10.1/10.3、§11 | complete | 稳定身份、错误、对象最小字段、命令/查询目录、关闭规则、revision 单调、三结果、UNKNOWN 防重付与时间线 | §2-§10 | A1-A15 | covered | 公开业务合同必要依赖 |
| SRC-CAP-BASE `docs/comet/specs/payment-reference-build/spec.md` 全文 | complete | 保留完整现有 capability 与 67 个 PAY-AC；本 change 只增强缺口，不撤销既有业务或工程验收 | 完整目标 Spec 全文 | A15 及全部 PAY-AC | covered | 修改既有 capability，不建立平行规格 |

## Included work

- 新增公开只读 `GetAuthoritativeBill` 与 `ListBillRevisions` Endpoint/Query/HTTP binding。查询返回稳定 `billId` 和 `billIdentity`、channel、currency、businessDate、businessTimezone、createdAt、`currentRevision`、按 `revision ASC` 返回的完整历史；每版返回 revision、publishedAt、completeness、revision rawEvidence、payloadFingerprint 和完整 records；每条 record 返回稳定 identity、transaction kind、external transaction identity、`Money`、raw status、occurredAt、recorded/received time 与 rawEvidence。
- 新增公开 `CloseExpiredPayment` 命令与 HTTP endpoint。请求必须显式包含 `merchantId`、路径 `paymentId` 与 `idempotencyKey`；只装载并裁决该 Payment，返回真实 `OperationReceipt` 与 `READ_ONCE` readAfter。相同规范化 payload 重放原 operation，改变绑定字段稳定冲突；全局 `PAYMENT_EXPIRY` 可保留为 scheduler/reference maintenance，但不得作为该命令的实现或验收替代。
- 新增 reference-only settlement executor script configure/reset/read 控制面，脚本按 caller-supplied `executionId` 选择并只消费一次，暴露配置、是否已消费和已形成的稳定观察；reset 不删除已经形成的 execution、receipt 或业务事实。
- 支持 `SUCCESS`、`FAILURE`、`UNKNOWN`、`NO_RESULT` 四类脚本：SUCCESS/FAILURE/UNKNOWN 在 Execute 提交边界形成对应可信 executor 结果与持久化 evidence；NO_RESULT 只证明提交已被接受但未返回结果，不合成 callback。UNKNOWN 与 NO_RESULT 均不得允许换 identity 重付，并按既有逻辑时钟/review policy 使用同一 executionId 收敛。
- `ExecuteSettlement` 请求显式包含 `merchantId`、`settlementId`、`executionId`、`executionChannelId` 与 `idempotencyKey`。caller-supplied `executionId` 是公开执行身份并持久化到 execution attempt；内部数据库 ID 或 channel ID 不得替代。相同完整绑定重放返回同一 execution/operation；同 key 改变绑定字段返回 `IDEMPOTENCY_CONFLICT`，同 executionId 改绑 merchant/settlement/channel/key 返回稳定 execution identity 冲突且无副作用。
- 结算详情/执行列表、可信 result callback、脚本消费结果、ManualReview 来源/关联和 payment timeline 都暴露同一个 caller-supplied `executionId`；既有内部 attempt ID 如继续存在，只能作为不公开的持久化细节。
- 更新必要的 schema、生成模型输入、聚合行为、Command/Query/Capability、Endpoint contract、HTTP binding、错误映射、read model、fixture 与 trace 投影，同时保持 CAP4K module 依赖、JPA/UoW、领域事件、本地可靠事件和 generator ownership。
- 增加 domain/application/H2/HTTP 测试与进程外真实 HTTP 验收；最终还要运行与候选实现匹配的全量回归和既有 67 个 PAY-AC 真实 HTTP runner，确保本轮不破坏已归档统一能力。

# 非目标

- 不修改 `payment-reference-workbench`、`wow-reference-payment`、`payment-product-template`；它们仅可只读调查。
- 不更新工作台 adapter、页面、契约测试、paired smoke 的代码；可读取其现状以理解公开契约，但本 change 的通过证据必须在 CAP4K 仓库内独立成立。
- 不以全局 maintenance、Controller/MockMvc 测试、前端内存/缓存、合成 receipt、ReconciliationRun 输入快照或后端内部对象冒充公开 API。
- 不删除或缩减既有支付、退款、对账、结算、通知、人工核对、timeline、67 个 PAY-AC 或 CAP4K 工程能力。
- 不交付生产认证/授权/租户隔离、真实资金移动、生产数据库迁移、跨进程 exactly-once、真实渠道证书或生产运维控制面。

# 验收示例

- Scenario: A1 新会话读取权威账单和完整 revision 历史。GIVEN 干净 H2 中通过公开 reference API 为同一 bill 注册 revision 1 和 2；WHEN 独立进程外 HTTP client 在不运行对账、不复用前端缓存的情况下仅凭 bill ID 调用 `GetAuthoritativeBill` 和 `ListBillRevisions`；THEN 两个查询都返回稳定 billId/billIdentity、channel/currency/businessDate/businessTimezone、currentRevision=2，以及严格按 `revision ASC` 返回 revision 1/2 的完整不可变历史。

- Scenario: A2 revision 与逐记录原始证据完整可读。GIVEN 两个 revision 具有不同 publishedAt、completeness、payload fingerprint、revision evidence 和多条 PAYMENT/REFUND record；WHEN 通过公开账单查询读取任意 revision；THEN 每版完整返回 revision/publishedAt/completeness/rawEvidence/payloadFingerprint，每条 record 返回 identity、transaction kind、external transaction identity、统一 Money、raw status、occurredAt、received/recorded time 与 rawEvidence。

- Scenario: A3 相同与冲突 revision 不改写事实。GIVEN revision 2 已登记；WHEN 以相同 fingerprint 和内容重放 revision 2，再以相同 revision 但不同 fingerprint 或绑定内容登记；THEN 相同内容得到幂等结果且没有第二版，冲突内容返回稳定 ApiError 且原 revision/current pointer/records 均不改变。

- Scenario: A4 迟到低 revision 与未知 bill 行为稳定。GIVEN currentRevision=2；WHEN revision 1 迟到重放并再次查询，且另行查询未知 bill ID；THEN currentRevision 仍为 2、历史不丢失，未知 bill 返回含稳定 code/details/correlationId/retryable 的 ApiError，且两者都不依赖 Run 输入或前端状态。

- Scenario: A5 单 Payment 到期关闭返回真实 receipt。GIVEN 指定商户的 Payment 已到期、处于 PAYABLE 且没有未决 attempt；WHEN 通过公开 HTTP 以 merchantId、paymentId、idempotencyKey 调用 `CloseExpiredPayment`；THEN 仅该 Payment 变为 CLOSED，响应为真实 `OperationReceipt`，operation 可查，readAfter 指向公开 Payment 资源且为 READ_ONCE。

- Scenario: A6 CloseExpiredPayment 幂等与资源绑定。GIVEN A5 的关闭命令已受理；WHEN 完全相同请求重放，并以相同 key 改变 merchantId 或 paymentId 重放；THEN 相同请求返回同一 operation/resource 且为 ALREADY_ACCEPTED、无第二业务效果，改变绑定字段返回稳定 `IDEMPOTENCY_CONFLICT` 或业务范围冲突且无副作用。

- Scenario: A7 到期但存在未决 attempt 不被关闭。GIVEN 指定 Payment 已到期且存在 PROCESSING 或 RESULT_UNKNOWN attempt；WHEN 调用同一个单资源到期命令；THEN Payment 不进入 CLOSED/FAILED，而按统一规则保持/进入 RESULT_UNKNOWN，达到 policy 阈值时创建引用该 Payment/attempt 的 ManualReview，且 receipt/readAfter 仍对应本次真实命令。

- Scenario: A8 非到期或已成功 Payment 同步拒绝。GIVEN Payment 尚未到期或已经 SUCCEEDED；WHEN 调用 `CloseExpiredPayment`；THEN 返回稳定 ApiError，不创建 Operation，不改变 Payment、attempt、成功事实、通知或结算资格。

- Scenario: A9 settlement executor script 控制面可配置、读取和重置。GIVEN 干净 reference profile、未使用与已消费的 executionId；WHEN 通过公开 reference-only HTTP 对 SUCCESS/FAILURE/UNKNOWN/NO_RESULT 脚本执行 configure/read/reset、以空 executionId 或未知 script 调用、重复 configure/reset，并在 identity 已消费后尝试 reconfigure/reset 与重放旧 identity；THEN 空 identity/未知 script 返回稳定 ApiError，重复 configure/reset 幂等，读取稳定显示 executionId、有效脚本、配置/消费状态与诊断，已消费后的 reconfigure/reset 不改写首次冻结的 consumption、observation、execution 或 evidence，旧 identity 重放仍返回首次 observation 且不产生第二业务效果。

- Scenario: A10 SUCCESS 脚本被 Execute 实际消费且只生效一次。GIVEN confirmed 正净额 Settlement 与为 caller executionId 配置的 SUCCESS 脚本；WHEN 通过公开 HTTP 执行 settlement，并以相同 merchant/settlement/execution/channel/idempotency 重放；THEN 第一次消费脚本并形成一次 SUCCESS execution/settled fact/通知意图，重放返回同一 execution 和 operation，不再次消费脚本、不产生第二资金或通知事实。

- Scenario: A11 FAILURE 脚本保留诊断并允许受控新执行。GIVEN confirmed Settlement 与为 executionId-1 配置的 FAILURE 脚本；WHEN Execute 消费脚本后以相同 identity 重放，再按显式命令使用新的 executionId-2 重试；THEN executionId-1 稳定为明确失败并保留诊断、重放无第二效果，且只有明确失败后才允许 executionId-2，冻结 scope/items/Money 不变。

- Scenario: A12 UNKNOWN 与 NO_RESULT 保持原 identity 并禁止重付。GIVEN 分别为稳定 executionId 配置 UNKNOWN 与 NO_RESULT；WHEN Execute 消费脚本、推进逻辑时钟并尝试改用新 executionId、void 或 replacement；THEN UNKNOWN 保留可信未知结果证据，NO_RESULT 保留已提交但无 callback 的诊断，二者均使用原 executionId 进入或等待 review，并稳定拒绝新 identity、作废、替代或第二资金效果。

- Scenario: A13 caller-supplied executionId 贯穿所有公开事实。GIVEN 调用方以明确 executionId 执行 settlement 并随后接收可信 result 或进入 ManualReview；WHEN 查询 Settlement 详情/执行列表、result receipt、ManualReview 和关联 payment timeline；THEN 所有公开表示和 callback 输入引用完全相同的 executionId，executionChannelId 仅作为独立渠道字段，服务端内部 attempt ID 不泄漏为替代身份。

- Scenario: A14 execution identity 与幂等绑定冲突稳定。GIVEN 一个 executionId/idempotencyKey 已绑定 merchantId、settlementId 和 executionChannelId；WHEN 相同完整绑定重放，或复用同 key 改 executionId/channel/settlement/merchant，或复用同 executionId 改 key/channel/settlement/merchant；THEN 完整重放返回同一 execution/operation，任一改绑均返回稳定 ApiError 与冲突 details，且不会创建第二 execution、消费第二脚本或改变结算事实。

- Scenario: A15 干净 H2 与进程外真实 HTTP 是正式验收证据。GIVEN 候选实现已冻结且测试从全新 H2 启动实际 Spring Boot 服务；WHEN 独立进程 HTTP client 仅通过公开 reference/business surface 执行 A1-A14，并运行既有 67 个 PAY-AC HTTP runner 与必要自动化回归；THEN A1-A14 每项都有可复核请求/响应和副作用断言且全部通过、既有 PAY-AC 无回归，且 Controller/MockMvc、合成 receipt、Run 输入证据、内部对象或外部仓库修改均不被计为替代证据。

# 约束与不变量

- 只修改 `D:/code/cap4k-reference-payment`。`D:/code/payment-reference-workbench`、`D:/code/GitHub/payment-product-template`、`wow-reference-payment` 全程只读，已有脏文件不得触碰、格式化、清理或提交。
- 统一业务真源提交固定为 `3b66db675356e77c720081e0410baf444b7baa9c`；当前 canonical Spec 的其他行为继续有效，本轮不得以“只修四个 gap”为由跳过受影响回归。
- Bill revision 与 record 是不可变追加事实；相同 revision 只能同内容幂等，低 revision 不能回退 current，高 revision 不能删除历史。
- `CloseExpiredPayment` 必须复用 Payment 聚合的到期不变量，但只能裁决一个显式绑定资源；全局扫描继续是 scheduler/maintenance，不属于该公开命令。
- 同步校验、资源不存在、幂等/identity 冲突和当前可判定业务拒绝只返回 ApiError，不创建 Operation；受理命令才返回 OperationReceipt。CAP4K 同事务完成使用稳定 `READ_ONCE/SUCCEEDED` operation，领域资源终态仍由自身 status/finality 表达。
- settlement script 是 reference-only 验收控制面，不能进入 production contract、不能允许调用方自报 verification，也不能绕过 trusted result semantics。
- caller-supplied `executionId` 与 `executionChannelId` 是两个独立字段；前者是稳定执行身份，后者是渠道证据。所有重放、callback、review、timeline 与冲突检查以 executionId 为公开关联键。
- UNKNOWN/NO_RESULT、重复、冲突、迟到和已成功事实不回退继续适用；reset 或相同 identity 重放不得删除、覆盖或重复已形成事实。
- 所有写操作继续经 CAP4K Command/UoW；schema/generated ownership、乐观锁、唯一约束、领域事件和本地可靠事件保持既有项目风格。

# 决策

- 使用普通单一 Native change `align-authoritative-payment-contract`，工作区为干净的当前 `main`。不建立 Supervisor children：四项缺口共享同一个 canonical capability、Operation 协议、结算 owned graph、HTTP black-box runner 和 trace 投影，拆分会反复修改同一核心区域；用户也明确要求一个新 change 一次完成。
- 修改既有 capability `payment-reference-build`，完整目标规格位于本 change 的 `specs/payment-reference-build/spec.md`；不建立第二套 payment capability。
- Bill 公共读取采用 CAP4K 风格的 `GET /api/authoritative-bills/{billId}` 与 `GET /api/authoritative-bills/{billId}/revisions`。查询以稳定 public billId 为主，并返回 billIdentity；实现可兼容解析既有 billIdentity，但不得产生两套权威资源。
- 单资源到期命令采用 `POST /api/payments/{paymentId}/close-expired`，body 明确包含 `merchantId` 与 `idempotencyKey`。
- settlement executor 控制面采用 reference-fixture 路径，按 executionId 配置、读取和 reset；有效脚本枚举为 `SUCCESS | FAILURE | UNKNOWN | NO_RESULT`，未配置默认 `NO_RESULT`，避免未声明配置自动形成资金结果。
- `ExecuteSettlement` 保留现有 path，但请求补全 `merchantId + executionId + executionChannelId + idempotencyKey`。公开 response、详情、callback、review 与 timeline 一律称 `executionId`；旧 `attemptId`/`executionGroupIdentity`/`requestIdentity` 可作为兼容诊断字段存在，但不能替代或改写 executionId。
- `UNKNOWN` 形成可信 UNKNOWN 结果证据；`NO_RESULT` 形成“提交已接受但未收到结果”的稳定执行诊断而不合成 callback，二者都按同一 executionId 和 review policy 防重付。
- 本 change 不修改工作台；公开 API 完成后由下游单独更新 adapter。当前工作台的 alternative/unavailable 标记是现状证据，不是验收标准。

# 待解决问题

- 无。用户已明确业务真源、四项完整范围、不可接受的替代方案、只读仓库边界与真实 HTTP 验收；路由、内部模型、script storage 与错误细分属于不改变用户可见目标的实现选择。

# 验证预期

- 静态：Endpoint/DTO/Command/Query/Capability、schema 与生成输入、稳定错误映射、executionId 全链引用、模块依赖和设计元数据一致。
- Domain/application：Bill revision 单调/不可变/冲突；单 Payment 到期；Operation 幂等；script 一次消费；settlement SUCCESS/FAILURE/UNKNOWN/NO_RESULT；identity/key 改绑冲突；callback/review/timeline 同 ID。
- H2/JPA：从干净 H2 验证 revision/record 完整持久化、executionId 唯一与冲突、Operation 与业务事实同 UoW、回滚后无残留、重放无第二效果。
- Contract/HTTP：公开 bill detail/revisions、close-expired、script configure/read/reset、Execute/request/result/detail/review/timeline 的 JSON 与 ApiError/receipt/readAfter 契约。
- 真实 HTTP：启动实际 CAP4K 进程，由进程外 client 从干净 H2 执行 A1-A14；每项保存请求/响应、状态与副作用断言。MockMvc、TestRestTemplate 同进程或 Controller 单测不能替代。
- 回归：运行定向自动化、必要模块/全量构建、boot startup、generator/Analyzer/AgentFacts/traceability guards，以及既有 67 个 PAY-AC 的真实 HTTP runner；失败、skip、超时、未执行或与旧 candidate 绑定的记录均不算通过。
