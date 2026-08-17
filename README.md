# cap4k-reference-payment

`cap4k-reference-payment` 是一个 **requirements-first** 的支付领域引用项目，业务范围覆盖支付、退款、日终对账与商户结算。

## 当前阶段

仓库目前只建设可复用的业务需求、验收口径与 cap4k 当前实现投影，**尚未进入 Build**。当前不存在可运行应用、生成代码、测试结果或框架能力证明；所有实现证据均明确标记为 `not-built`。

## 资产边界

- `docs/requirements/`：框架无关的业务真源和验收口径。
- `docs/requirements/projection/cap4k-current.md`：业务需求到当前 cap4k 能力的计划映射。
- `docs/requirements/traceability.yaml`：需求、投影与证据的机器可读追踪关系。

业务真源可以长期复用；cap4k 投影只说明**当前目标方案**，不维护版本目录、兼容层或历史实现副本。历史变化由 Git 保存。若未来确有多基线并行维护需求，再通过独立架构变更决定隔离方式。

## 目标业务模型

计划覆盖以下核心模型：

- Payment 与 PaymentAttempt；
- Refund；
- ReconciliationBatch 与 ReconciliationItem；
- MerchantSettlement 与 SettlementLine；
- MerchantChannelConfiguration。

计划覆盖以下核心业务链：

- 支付创建、渠道发起与支付回调；
- 支付超时关闭与迟到回调处理；
- 全额及部分退款；
- 日终对账与差异处置；
- 商户周期结算；
- 入站与出站 Integration Event；
- 对外 Endpoint。

## 下一阶段入口

进入 Build 之前，必须先完成：

1. 框架无关业务需求及验收标准评审；
2. `traceability.yaml` 中业务 ID、投影 ID 和计划证据的一致性检查；
3. 当前 cap4k Runtime、Generator、Analyzer、Pipeline 和 AgentFacts 能力边界确认；
4. 明确哪些能力由引用项目证明，哪些病态并发或 ORM 极端场景继续由 cap4k focused fixtures 负责。
