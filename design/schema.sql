drop table if exists operation;
drop table if exists payment_channel_result_receipt;
drop table if exists merchant_notification_delivery_attempt;
drop table if exists merchant_notification;
drop table if exists manual_review_resolution;
drop table if exists manual_review_item;
drop table if exists settlement_result_receipt;
drop table if exists settlement_execution_attempt;
drop table if exists settlement_line;
drop table if exists merchant_settlement;
drop table if exists bill_available_signal;
drop table if exists bill_revision_record;
drop table if exists bill_revision;
drop table if exists authoritative_bill;
drop table if exists reconciliation_confirmation_fact;
drop table if exists reconciliation_disposition;
drop table if exists reconciliation_item;
drop table if exists reconciliation_run;
drop table if exists reconciliation_batch;
drop table if exists refund_notification_receipt;
drop table if exists refund_attempt;
drop table if exists refund;
drop table if exists payment_review_decision;
drop table if exists payment_review_case;
drop table if exists payment_notification_receipt;
drop table if exists payment_submission_receipt;
drop table if exists payment_attempt;
drop table if exists merchant_channel_configuration;
drop table if exists payment;

create table operation (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    merchant_id varchar(64) not null comment '命令业务范围商户标识',
    command_type varchar(128) not null comment '规范化命令类型',
    idempotency_key varchar(128) not null comment '命令幂等键',
    canonical_request_hash varchar(128) not null comment '规范化请求哈希',
    resource_type varchar(64) not null comment '受理后关联资源类型',
    resource_id varchar(128) not null comment '受理后关联资源标识',
    status varchar(32) not null comment 'Operation 状态',
    finality varchar(32) not null comment 'Operation 最终性',
    read_after_mode varchar(32) not null comment '资源读取方式',
    read_after_resource_url varchar(512) comment '受理后资源读取地址',
    retry_after_ms bigint not null default 0 comment '受理时冻结的建议轮询间隔；READ_ONCE 为零',
    result_json varchar(8192) comment '成功结果的稳定 JSON 对象',
    error_code varchar(128) comment '异步失败的稳定业务错误码',
    error_message varchar(2048) comment '异步失败的受控错误说明',
    error_details_json varchar(8192) comment '异步失败的稳定 JSON details',
    error_correlation_id varchar(128) comment '异步失败的稳定关联标识',
    error_retryable boolean comment '异步失败的重试提示',
    review_id varchar(128) comment '停止自动处理时的人工核对引用',
    accepted_at timestamp with time zone not null comment '命令受理时间',
    completed_at timestamp with time zone comment 'Operation 完成时间',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_operation_idempotency unique (merchant_id, command_type, idempotency_key)
);
comment on table operation is '统一命令受理记录：按 merchant、命令类型和幂等键保存稳定受理、资源引用与读取语义';

create table payment_channel_result_receipt (
    id varchar(36) primary key comment '支付渠道结果收件唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    result_identity varchar(256) not null comment '渠道提供的稳定外部结果身份',
    payload_identity varchar(128) not null comment '服务端形成的 canonical payload 指纹',
    channel_id varchar(64) not null comment '渠道标识',
    payment_id varchar(36) comment '可选的关联支付标识 @RefAggregate=Payment;',
    payment_attempt_id varchar(36) comment '可选的关联支付 attempt 标识',
    channel_transaction_id varchar(256) not null comment '渠道交易标识',
    raw_evidence varchar(8192) not null comment '原始 canonical 渠道证据',
    verification varchar(32) not null comment '服务端 verifier 形成的核验结论',
    verification_summary varchar(2048) comment '服务端核验摘要',
    outcome varchar(32) not null comment '规范化渠道结果 SUCCESS、FAILURE 或 UNKNOWN',
    amount decimal(19, 8) not null comment '结果金额',
    currency varchar(3) not null comment '结果币种',
    occurred_at timestamp with time zone not null comment '渠道事实发生时间',
    recorded_at timestamp with time zone not null comment '服务端首次记录时间',
    last_received_at timestamp with time zone not null comment '服务端最近接收时间',
    receive_count integer not null default 1 comment '相同 external identity 与 payload 的接收次数',
    disposition varchar(64) not null comment '统一渠道结果处置',
    rejection_summary varchar(2048) comment '拒绝或未知引用摘要',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_payment_channel_result_receipt unique (channel_id, result_identity, payload_identity)
);
comment on table payment_channel_result_receipt is '支付渠道结果权威收件：独立于目标聚合保存全部外部结果，使未知 payment/attempt 引用仍可按 external identity 查询';

create table merchant_notification (
    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    notification_identity varchar(512) not null comment '来源业务事实的稳定通知身份',
    content_identity varchar(128) not null comment '首次形成时冻结的内容指纹',
    merchant_id varchar(64) not null comment '商户业务范围',
    source_kind varchar(64) not null comment '来源事实类型',
    source_fact_identity varchar(256) not null comment '来源权威事实的稳定身份',
    payment_id varchar(36) comment '可追踪的支付标识 @RefAggregate=Payment;',
    content varchar(4096) not null comment '冻结的 reference 通知内容',
    status integer not null comment '当前通知投递状态 @Type=MerchantNotificationStatus;',
    finality varchar(32) not null comment '通知投递最终性',
    max_attempts integer not null comment '通知形成时冻结的最多投递次数',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_merchant_notification_identity unique (notification_identity)
);
comment on table merchant_notification is '商户通知意图：冻结来源事实、身份和内容，投递失败不影响来源业务事实';

create table merchant_notification_delivery_attempt (
    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    merchant_notification_id varchar(36) not null comment '所属商户通知标识 @ParentRef;',
    attempt_sequence integer not null comment '投递尝试顺序',
    delivery_identity varchar(256) not null comment '本次投递的稳定请求身份',
    content_identity varchar(128) not null comment '冻结内容指纹快照',
    outcome integer not null comment '本次投递结果 @Type=MerchantNotificationDeliveryOutcome;',
    diagnostic varchar(2048) comment '受控发送诊断',
    recorded_at timestamp with time zone not null comment '本次投递结果的服务端记录时间',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_merchant_notification_attempt unique (merchant_notification_id, attempt_sequence),
    constraint uk_merchant_notification_delivery unique (delivery_identity)
);
comment on table merchant_notification_delivery_attempt is '商户通知投递尝试：同一通知与内容下追加每次投递结果 @Parent=merchant_notification;';

create table manual_review_item (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    review_identity varchar(512) not null comment '跨聚合人工事项稳定身份',
    type varchar(64) not null comment '统一人工事项类型',
    status varchar(32) not null comment '统一人工事项状态',
    finality varchar(32) not null comment '统一最终性',
    merchant_id varchar(64) comment '可选商户业务范围',
    payment_id varchar(36) comment '用于 payment trace 的规范化支付引用 @RefAggregate=Payment;',
    origin_kind varchar(64) not null comment '触发事项的权威事实类型',
    origin_identity varchar(512) not null comment '触发事项的权威事实稳定身份',
    summary varchar(2048) not null comment '面向人工的事项摘要',
    related_refs_json varchar(8192) not null comment '关联权威资源引用 canonical JSON',
    blocking_scopes_json varchar(8192) not null comment '阻断范围 canonical JSON',
    evidence_refs_json varchar(8192) not null comment '证据引用 canonical JSON',
    sort_time timestamp with time zone not null comment '稳定列表排序时间',
    resolved_at timestamp with time zone comment '事项最终解决时间',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_manual_review_item_identity unique (review_identity)
);
comment on table manual_review_item is '统一人工核对事项：由支付、退款、对账和结算的原始事实稳定触发，保存阻断范围、关联和追加处置';

create table manual_review_resolution (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    manual_review_item_id varchar(36) not null comment '所属人工核对事项标识 @ParentRef;',
    resolution_identity varchar(512) not null comment '追加处置稳定身份',
    actor_id varchar(128) not null comment '可信 ReferenceActorContext 映射的责任人',
    actor_role varchar(128) not null comment '可信 ReferenceActorContext 映射的责任角色',
    outcome varchar(64) not null comment '人工处置结论',
    reason varchar(2048) not null comment '人工处置原因',
    evidence varchar(4096) not null comment '人工处置证据',
    resolved_at timestamp with time zone not null comment '处置责任时间',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_manual_review_resolution_identity unique (manual_review_item_id, resolution_identity)
);
comment on table manual_review_resolution is '人工核对事项处置历史：仅追加可信责任人、结论、原因和证据，不覆盖来源业务事实 @Parent=manual_review_item;';

create table payment (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    merchant_id varchar(64) not null comment '商户标识',
    merchant_order_number varchar(128) not null comment '商户订单号',
    idempotency_key varchar(128) not null comment '请求幂等键',
    amount decimal(19, 4) not null comment '本次业务金额',
    currency varchar(3) not null comment '业务币种',
    payment_method varchar(64) not null comment '支付方式',
    status integer not null comment '当前业务状态枚举 @Type=PaymentStatus;',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    expires_at timestamp with time zone not null comment '支付有效期截止时间',
    succeeded_at timestamp with time zone comment '支付成功事实形成时间',
    closed_at timestamp with time zone comment '支付关闭时间',
    close_reason varchar(256) comment '支付关闭原因',
    channel_transaction_id varchar(128) comment '渠道交易标识',
    success_fact_formed boolean not null default false comment '成功事实是否已形成',
    merchant_order_success_identity varchar(320) comment '商户订单成功稳定身份',
    attempt_count integer not null default 0 comment '支付尝试数量',
    notification_receive_count integer not null default 0 comment '通知累计接收次数',
    rejected_notification_count integer not null default 0 comment '通知累计拒绝次数',
    conflicting_notification_count integer not null default 0 comment '通知累计矛盾次数',
    last_notification_identity varchar(128) comment '最近通知身份',
    last_notification_received_at timestamp with time zone comment '最近通知接收时间',
    last_rejection_summary varchar(1024) comment '最近一次拒绝摘要',
    last_conflict_summary varchar(1024) comment '最近一次矛盾摘要',
    merchant_success_notification_intent_count integer not null default 0 comment '商户成功通知意图次数',
    merchant_success_notification_intent_identity varchar(160) comment '商户成功通知意图身份',
    merchant_success_notification_intent_state integer comment '商户成功通知意图状态 @Type=PaymentNotificationIntentState;',
    review_count integer not null default 0 comment '复核案件数量',
    blocking_review_count integer not null default 0 comment '阻断性复核数量',
    settlement_fee_fact_identity varchar(128) comment '结算手续费事实身份',
    settlement_fee_rate decimal(19, 8) comment '支付成功时冻结的 ReferencePolicy 手续费率',
    settlement_fee_basis_points integer comment '结算手续费基点',
    settlement_fixed_fee_amount decimal(19, 4) comment '结算固定手续费金额',
    settlement_fee_rounding_mode varchar(32) comment '结算手续费舍入模式',
    settlement_fee_currency_precision integer comment '手续费币种精度快照',
    settlement_fee_calculation_amount decimal(19, 4) comment '手续费计算基数快照',
    settlement_fee_amount decimal(19, 4) comment '手续费金额快照',
    settlement_fee_formed_at timestamp with time zone comment '手续费事实形成时间',
    settlement_blocked boolean not null default false comment '是否阻断进入结算',
    reserved_refund_amount decimal(19, 4) not null comment '已占用退款金额',
    successful_refund_amount decimal(19, 4) not null comment '已成功退款金额',
    constraint uk_payment_merchant_idempotency unique (merchant_id, idempotency_key),
    constraint uk_payment_merchant_order_success unique (merchant_order_success_identity),
    constraint uk_payment_notification_intent unique (merchant_success_notification_intent_identity)
);
comment on table payment is '支付主聚合：记录商户订单的金额、状态、成功事实、通知收敛、复核和退款预算';

create table payment_attempt (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    payment_id varchar(36) not null comment '关联支付标识 @ParentRef;',
    channel_id varchar(64) not null comment '渠道标识',
    channel_configuration_id varchar(36) not null comment '渠道配置标识',
    channel_configuration_snapshot varchar(2048) not null comment '发起时使用的渠道配置快照',
    request_identity varchar(128) not null comment '执行请求幂等身份',
    status integer not null comment '当前业务状态枚举 @Type=PaymentAttemptStatus;',
    initiated_at timestamp with time zone not null comment '创建支付尝试时间',
    submission_identity varchar(160) comment '提交到渠道前冻结的稳定提交身份',
    submitted_at timestamp with time zone comment '提交到渠道的服务端时间',
    accepted_at timestamp with time zone comment '渠道受理时间',
    completed_at timestamp with time zone comment '尝试终结或进入未知结果时间',
    interaction_information varchar(4096) comment '渠道交互信息快照，例如 redirect 或 reference 指令',
    risk_reason varchar(2048) comment '在已有 in-flight 或 unknown 尝试时新建尝试的显式风险说明',
    channel_transaction_id varchar(128) comment '渠道交易标识',
    final_result integer comment '最终结果枚举 @Type=PaymentAttemptFinalResult;',
    result_occurred_at timestamp with time zone comment '渠道结果发生时间',
    notification_identity varchar(128) comment '渠道通知幂等身份',
    notification_receive_count integer not null default 0 comment '通知累计接收次数',
    notification_first_received_at timestamp with time zone comment '首次收到通知时间',
    notification_last_received_at timestamp with time zone comment '最近收到通知时间',
    verified_notification_count integer not null default 0 comment '通知累计验证通过次数',
    rejected_notification_count integer not null default 0 comment '通知累计拒绝次数',
    conflicting_notification_count integer not null default 0 comment '通知累计矛盾次数',
    verdict_summary varchar(1024) comment '系统判定摘要',
    rejection_summary varchar(1024) comment '拒绝处理摘要',
    conflict_summary varchar(1024) comment '矛盾处理摘要',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_payment_attempt_request unique (channel_id, request_identity)
);
comment on table payment_attempt is '支付尝试：Payment 聚合内一次渠道请求及其最终结果与通知证据 @Parent=payment;';

create table payment_submission_receipt (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    payment_attempt_id varchar(36) not null comment '关联支付尝试标识 @ParentRef;',
    submission_identity varchar(160) not null comment '渠道提交稳定身份',
    request_identity varchar(128) not null comment '支付尝试请求身份快照',
    channel_id varchar(64) not null comment '渠道标识快照',
    submitted_at timestamp with time zone not null comment '渠道提交时间',
    outcome varchar(32) not null comment '渠道提交结果 ACCEPTED、REJECTED 或 RESULT_UNKNOWN',
    channel_reference varchar(256) comment '渠道受理或交互引用',
    diagnostic_summary varchar(2048) comment '受控渠道提交诊断摘要',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_payment_submission_receipt unique (payment_attempt_id, submission_identity)
);
comment on table payment_submission_receipt is '支付渠道提交收件：保存提交前冻结身份、一次渠道调用的受理/拒绝/未知证据，不形成支付成功事实 @Parent=payment_attempt;';

create table payment_notification_receipt (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    payment_attempt_id varchar(36) not null comment '关联支付尝试标识 @ParentRef;',
    notification_identity varchar(128) not null comment '渠道通知幂等身份',
    payload_identity varchar(128) not null comment '通知载荷指纹身份',
    channel_id varchar(64) not null comment '渠道标识',
    channel_transaction_id varchar(128) not null comment '渠道交易标识',
    amount decimal(19, 4) not null comment '本次业务金额',
    currency varchar(3) not null comment '业务币种',
    result varchar(32) not null comment '渠道原始结果',
    occurred_at timestamp with time zone not null comment '外部事实发生时间',
    first_received_at timestamp with time zone not null comment '首次收到通知时间',
    last_received_at timestamp with time zone not null comment '最近收到通知时间',
    receive_count integer not null default 1 comment '同一通知累计接收次数',
    verified boolean not null default false comment '通知是否验证通过',
    accepted boolean not null default false comment '通知是否被业务接受',
    decision integer not null comment '系统对通知的处理决策 @Type=ChannelResultDisposition;',
    verdict_summary varchar(1024) comment '系统判定摘要',
    rejection_summary varchar(1024) comment '拒绝处理摘要',
    conflict_summary varchar(1024) comment '矛盾处理摘要',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_payment_notification_receipt unique (payment_attempt_id, notification_identity, payload_identity)
);
comment on table payment_notification_receipt is '支付通知回执：保存渠道回调的原始身份、校验结果和重复/矛盾处理证据 @Parent=payment_attempt;';

create table payment_review_case (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    payment_id varchar(36) not null comment '关联支付标识 @ParentRef;',
    review_identity varchar(320) not null comment '复核案件身份',
    type integer not null comment '用于记录类型 @Type=PaymentReviewType;',
    status integer not null comment '当前业务状态枚举 @Type=PaymentReviewStatus;',
    opened_at timestamp with time zone not null comment '复核案件开立时间',
    triggering_payment_status integer not null comment '触发复核时的支付状态 @Type=PaymentStatus;',
    triggering_attempt_identities varchar(2048) comment '触发复核的尝试身份集合',
    triggering_receipt_identities varchar(2048) comment '触发复核的通知身份集合',
    summary varchar(2048) not null comment '复核案件业务摘要',
    settlement_impact integer not null comment '对结算资格的影响枚举 @Type=PaymentReviewSettlementImpact;',
    resolved_at timestamp with time zone comment '复核案件解决时间',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_payment_review_case unique (payment_id, review_identity)
);
comment on table payment_review_case is '支付复核案件：保存迟到、未知或矛盾支付结果的人工复核上下文 @Parent=payment;';

create table payment_review_decision (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    payment_review_case_id varchar(36) not null comment '关联支付复核案件标识 @ParentRef;',
    decision_identity varchar(320) not null comment '用于记录决策稳定身份',
    decision integer not null comment '系统对通知的处理决策 @Type=PaymentReviewDecisionType;',
    operator_identity varchar(128) not null comment '操作人身份',
    operator_role varchar(128) not null comment '操作人角色',
    authorization_outcome boolean not null comment '授权校验结果',
    reason varchar(2048) not null comment '业务原因说明',
    evidence varchar(4096) not null comment '业务证据文本',
    decided_at timestamp with time zone not null comment '裁决时间',
    eligibility_impact integer not null comment '对后续资格的影响枚举 @Type=PaymentReviewEligibilityImpact;',
    remediation_reference varchar(512) comment '补救动作引用',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_payment_review_decision unique (payment_review_case_id, decision_identity)
);
comment on table payment_review_decision is '支付复核决定：保存授权操作人对复核案件作出的追加式裁决证据 @Parent=payment_review_case;';

create table refund (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    payment_id varchar(36) not null comment '关联支付标识 @RefAggregate=Payment;',
    merchant_id varchar(64) not null comment '商户标识',
    merchant_refund_number varchar(128) not null comment '商户退款单号',
    idempotency_key varchar(128) not null comment '退款申请幂等键',
    amount decimal(19, 4) not null comment '本次业务金额',
    currency varchar(3) not null comment '业务币种',
    reason varchar(2048) not null comment '退款申请原因',
    payment_method varchar(64) not null comment '支付方式',
    status integer not null comment '当前业务状态枚举 @Type=RefundStatus;',
    requested_at timestamp with time zone not null comment '退款申请时间',
    refund_deadline_at timestamp with time zone not null comment '退款期限截止时间',
    channel_accepted_at timestamp with time zone comment '渠道受理时间',
    finalized_at timestamp with time zone comment '退款最终完成时间',
    review_required_at timestamp with time zone comment '进入退款结果复核时间',
    channel_id varchar(64) comment '最近一次退款尝试使用的渠道标识',
    channel_configuration_id varchar(36) comment '最近一次退款尝试使用的渠道配置标识',
    channel_configuration_snapshot varchar(2048) comment '最近一次退款尝试使用的渠道配置快照',
    request_identity varchar(128) comment '最近一次退款尝试的执行请求幂等身份',
    channel_refund_id varchar(128) comment '渠道退款标识',
    reservation_active boolean not null default true comment '退款预算占用是否有效',
    reservation_released boolean not null default false comment '退款预算占用是否已释放',
    reservation_converted_to_success boolean not null default false comment '退款预算是否已转换为成功退款事实',
    success_fact_formed boolean not null default false comment '成功事实是否已形成',
    notification_receive_count integer not null default 0 comment '通知累计接收次数',
    rejected_notification_count integer not null default 0 comment '通知累计拒绝次数',
    conflicting_notification_count integer not null default 0 comment '通知累计矛盾次数',
    last_notification_identity varchar(128) comment '最近通知身份',
    last_notification_received_at timestamp with time zone comment '最近通知接收时间',
    last_rejection_summary varchar(1024) comment '最近一次拒绝摘要',
    last_conflict_summary varchar(1024) comment '最近一次矛盾摘要',
    settlement_blocked boolean not null default false comment '是否阻断进入结算',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_refund_merchant_number unique (merchant_id, merchant_refund_number),
    constraint uk_refund_merchant_idempotency unique (merchant_id, idempotency_key)
);
comment on table refund is '退款主聚合：记录退款申请、退款预算占用、渠道结果和最终退款事实';

create table refund_attempt (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    refund_id varchar(36) not null comment '关联退款标识 @ParentRef;',
    channel_id varchar(64) not null comment '渠道标识',
    channel_configuration_id varchar(36) not null comment '渠道配置标识',
    channel_configuration_snapshot varchar(2048) not null comment '发起时使用的渠道配置快照',
    request_identity varchar(128) not null comment '执行请求幂等身份',
    status integer not null comment '当前业务状态枚举 @Type=RefundAttemptStatus;',
    initiated_at timestamp with time zone not null comment '渠道请求发起时间',
    accepted_at timestamp with time zone comment '渠道受理时间',
    review_after_at timestamp with time zone not null comment '进入结果复核的时间',
    channel_refund_id varchar(128) comment '渠道退款标识',
    final_result integer comment '最终结果枚举 @Type=RefundAttemptFinalResult;',
    result_occurred_at timestamp with time zone comment '渠道结果发生时间',
    notification_receive_count integer not null default 0 comment '通知累计接收次数',
    notification_first_received_at timestamp with time zone comment '首次收到通知时间',
    notification_last_received_at timestamp with time zone comment '最近收到通知时间',
    verified_notification_count integer not null default 0 comment '通知累计验证通过次数',
    rejected_notification_count integer not null default 0 comment '通知累计拒绝次数',
    conflicting_notification_count integer not null default 0 comment '通知累计矛盾次数',
    verdict_summary varchar(1024) comment '系统判定摘要',
    rejection_summary varchar(1024) comment '拒绝处理摘要',
    conflict_summary varchar(1024) comment '矛盾处理摘要',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_refund_attempt_request unique (channel_id, request_identity)
);
comment on table refund_attempt is '退款尝试：Refund 聚合内一次渠道退款请求及其等待、结果和通知证据 @Parent=refund;';

create table refund_notification_receipt (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    refund_attempt_id varchar(36) not null comment '关联退款尝试标识 @ParentRef;',
    notification_identity varchar(128) not null comment '渠道通知幂等身份',
    channel_id varchar(64) not null comment '渠道标识',
    channel_refund_id varchar(128) not null comment '渠道退款标识',
    amount decimal(19, 4) not null comment '本次业务金额',
    currency varchar(3) not null comment '业务币种',
    result varchar(32) not null comment '渠道原始结果',
    occurred_at timestamp with time zone not null comment '外部事实发生时间',
    first_received_at timestamp with time zone not null comment '首次收到通知时间',
    last_received_at timestamp with time zone not null comment '最近收到通知时间',
    receive_count integer not null default 1 comment '同一通知累计接收次数',
    verified boolean not null default false comment '通知是否验证通过',
    accepted boolean not null default false comment '通知是否被业务接受',
    decision integer not null comment '系统对通知的处理决策 @Type=RefundResultDisposition;',
    verdict_summary varchar(1024) comment '系统判定摘要',
    rejection_summary varchar(1024) comment '拒绝处理摘要',
    conflict_summary varchar(1024) comment '矛盾处理摘要',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_refund_notification_receipt unique (refund_attempt_id, notification_identity)
);
comment on table refund_notification_receipt is '退款通知回执：保存渠道退款回调的身份、金额、校验和冲突证据 @Parent=refund_attempt;';

create table merchant_channel_configuration (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    merchant_id varchar(64) not null comment '商户标识',
    channel_id varchar(64) not null comment '渠道标识',
    currency varchar(3) not null comment '业务币种',
    payment_method varchar(64) not null comment '支付方式',
    minimum_amount decimal(19, 4) not null comment '渠道可受理最小金额',
    maximum_amount decimal(19, 4) not null comment '渠道可受理最大金额',
    status integer not null comment '当前业务状态枚举 @Type=MerchantChannelConfigurationStatus;',
    routing_priority integer not null default 100 comment '路由优先级',
    channel_rule_summary varchar(2048) not null comment '渠道路由规则摘要',
    refund_window_days integer not null default 180 comment '退款窗口天数',
    refund_result_review_after_minutes integer not null default 30 comment '退款结果复核等待分钟数',
    settlement_fee_basis_points integer not null default 60 comment '结算手续费基点；reference 默认费率 0.006',
    settlement_fixed_fee_amount decimal(19, 4) not null comment '结算固定手续费金额',
    settlement_fee_rounding_mode varchar(32) not null default 'HALF_UP' comment '结算手续费舍入模式',
    settlement_result_review_after_minutes integer not null default 30 comment '结算结果复核等待分钟数',
    activated_at timestamp with time zone not null comment '配置生效时间',
    retired_at timestamp with time zone comment '配置退役时间',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_merchant_channel_configuration unique (merchant_id, channel_id, currency, payment_method)
);
comment on table merchant_channel_configuration is '商户渠道配置：保存商户在渠道、币种和支付方式维度上的路由与费用快照来源';

create table authoritative_bill (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    channel_id varchar(64) not null comment '渠道标识',
    bill_identity varchar(128) not null comment '权威账单稳定身份',
    business_date date not null comment '账单业务日期',
    currency varchar(3) not null comment '业务币种',
    business_timezone varchar(64) not null comment '账单业务时区',
    current_revision varchar(64) comment '当前权威修订版本；只允许高版本前进',
    current_revision_id varchar(36) comment '当前权威修订记录标识',
    last_fetch_diagnostic varchar(2048) comment '最近一次账单读取诊断',
    read_attempt_count integer not null default 0 comment '账单读取尝试累计次数',
    last_read_attempt_at timestamp with time zone comment '最近账单读取尝试时间',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_authoritative_bill_identity unique (channel_id, bill_identity)
);
comment on table authoritative_bill is '权威账单：按渠道和稳定账单身份保存当前不可回退修订指针、读取诊断和信号历史';

create table bill_revision (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    authoritative_bill_id varchar(36) not null comment '所属权威账单标识 @ParentRef;',
    revision varchar(64) not null comment '不可变账单修订版本',
    completeness integer not null comment '账单完整性枚举 @Type=com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.StatementCompleteness;',
    raw_evidence varchar(8192) not null comment 'provider 原始账单证据引用或 canonical payload',
    payload_fingerprint varchar(128) not null comment '不可变账单正文指纹',
    published_at timestamp with time zone not null comment 'provider 发布账单修订时间',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_bill_revision_identity unique (authoritative_bill_id, revision)
);
comment on table bill_revision is '不可变权威账单修订：保存完整性、原始证据、指纹、发布时间及逐行证据 @Parent=authoritative_bill;';

create table bill_revision_record (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    bill_revision_id varchar(36) not null comment '所属账单修订标识 @ParentRef;',
    record_identity varchar(128) not null comment '账单行稳定身份',
    channel_transaction_identity varchar(128) not null comment '渠道交易稳定身份',
    transaction_kind integer not null comment '交易类型枚举 @Type=com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind;',
    amount decimal(19, 4) not null comment '账单行金额',
    currency varchar(3) not null comment '账单行币种',
    raw_status varchar(64) not null comment '账单行原始状态',
    occurred_at timestamp with time zone comment '渠道事实发生时间',
    received_at timestamp with time zone not null comment 'provider 记录或拉取时间',
    raw_evidence varchar(4096) not null comment '账单行原始证据',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_bill_revision_record_identity unique (bill_revision_id, record_identity)
);
comment on table bill_revision_record is '账单修订逐行不可变证据：稳定记录身份、交易身份、金额、状态、时间和原始证据 @Parent=bill_revision;';

create table bill_available_signal (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    authoritative_bill_id varchar(36) not null comment '所属权威账单标识 @ParentRef;',
    signal_identity varchar(128) not null comment '账单可用信号稳定身份',
    announced_revision varchar(64) not null comment '信号声明的账单版本',
    published_at timestamp with time zone not null comment '信号发布时间',
    received_at timestamp with time zone not null comment '服务端接收时间',
    fetch_attempt_count integer not null default 0 comment '该信号驱动的读取尝试次数',
    diagnostic varchar(2048) comment '信号读取诊断',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_bill_available_signal_identity unique (authoritative_bill_id, signal_identity)
);
comment on table bill_available_signal is '账单可用信号：保存稳定信号身份、版本声明、读取次数和可诊断失败，不以信号正文替代权威账单 @Parent=authoritative_bill;';

create table reconciliation_batch (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    channel_id varchar(64) not null comment '渠道标识',
    currency varchar(3) not null comment '业务币种',
    reconciliation_date date not null comment '对账业务日期',
    business_timezone varchar(64) not null comment '业务日计算时区',
    status integer not null comment '当前业务状态枚举 @Type=ReconciliationBatchStatus;',
    current_effective_run_id varchar(36) comment '当前生效对账运行标识',
    statement_wait_deadline_at timestamp with time zone not null comment '等待账单截止时间',
    matched_count integer not null default 0 comment '匹配数量',
    difference_count integer not null default 0 comment '差异数量',
    unresolved_difference_count integer not null default 0 comment '未解决差异数量',
    settlement_blocked boolean not null default true comment '是否阻断进入结算',
    blocking_reason varchar(2048) comment '阻断完成的原因',
    completed_at timestamp with time zone comment '完成时间',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_reconciliation_batch_scope unique (channel_id, currency, reconciliation_date)
);
comment on table reconciliation_batch is '对账批次：以渠道、币种和业务日为范围维护对账周期、有效运行和结算阻断状态';

create table reconciliation_run (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    batch_id varchar(36) not null comment '所属对账批次标识 @ParentRef;',
    statement_identity varchar(128) not null comment '账单稳定身份',
    statement_revision varchar(64) not null comment '账单修订版本',
    statement_completeness integer not null comment '账单完整性枚举 @Type=StatementCompleteness;',
    status integer not null comment '当前业务状态枚举 @Type=ReconciliationRunStatus;',
    fetched_at timestamp with time zone not null comment '账单拉取时间',
    started_at timestamp with time zone not null comment '运行开始时间',
    completed_at timestamp with time zone comment '完成时间',
    channel_record_count integer not null default 0 comment '渠道记录数量',
    platform_fact_count integer not null default 0 comment '平台事实数量',
    matched_count integer not null default 0 comment '匹配数量',
    difference_count integer not null default 0 comment '差异数量',
    unresolved_difference_count integer not null default 0 comment '未解决差异数量',
    failure_summary varchar(2048) comment '运行失败摘要',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_reconciliation_run_statement unique (batch_id, statement_identity, statement_revision)
);
comment on table reconciliation_run is '对账运行：保存某一账单身份与修订版本的拉取、匹配和完成结果 @Parent=reconciliation_batch;';

create table reconciliation_item (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    reconciliation_run_id varchar(36) not null comment '所属对账运行标识 @ParentRef;',
    difference_identity varchar(128) not null comment '差异稳定身份',
    transaction_kind integer not null comment '交易类型枚举 @Type=ReconciliationTransactionKind;',
    difference_type integer not null comment '差异类型枚举 @Type=ReconciliationDifferenceType;',
    channel_record_identity varchar(128) comment '渠道记录身份',
    channel_transaction_identity varchar(128) comment '渠道交易身份',
    channel_amount decimal(19, 4) comment '渠道侧交易金额',
    channel_currency varchar(3) comment '渠道侧币种',
    channel_raw_status varchar(64) comment '渠道原始状态',
    channel_occurred_at timestamp with time zone comment '渠道事实发生时间',
    channel_received_at timestamp with time zone comment '渠道事实接收时间',
    platform_fact_identity varchar(128) comment '平台事实身份',
    payment_id varchar(36) comment '关联支付标识 @RefAggregate=Payment;',
    payment_attempt_id varchar(36) comment '关联支付尝试标识',
    refund_id varchar(36) comment '关联退款标识 @RefAggregate=Refund;',
    refund_attempt_id varchar(36) comment '关联退款尝试标识',
    platform_transaction_identity varchar(128) comment '平台交易身份',
    platform_amount decimal(19, 4) comment '平台事实金额',
    platform_currency varchar(3) comment '平台事实币种',
    platform_raw_status varchar(64) comment '平台原始状态',
    platform_occurred_at timestamp with time zone comment '平台事实发生时间',
    platform_recorded_at timestamp with time zone comment '平台事实记录时间',
    payment_review_identity_snapshot varchar(2048) comment '支付复核身份快照',
    payment_review_summary varchar(2048) comment '支付复核摘要',
    matching_basis varchar(2048) not null comment '匹配依据说明',
    auxiliary_match_approved boolean not null default false comment '是否允许辅助匹配',
    resolved boolean not null default false comment '差异是否已解决',
    settlement_blocked boolean not null default true comment '是否阻断进入结算',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_reconciliation_item_difference unique (reconciliation_run_id, difference_identity)
);
comment on table reconciliation_item is '对账差异项：保存渠道事实与平台事实的双方快照、匹配依据和处置状态 @Parent=reconciliation_run;';

create table reconciliation_disposition (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    reconciliation_item_id varchar(36) not null comment '所属对账差异项标识 @ParentRef;',
    operator_identity varchar(128) not null comment '操作人身份',
    operator_role varchar(128) not null comment '操作人角色',
    authorization_result integer not null comment '授权校验结果枚举 @Type=DispositionAuthorization;',
    status integer not null comment '当前业务状态枚举 @Type=ReconciliationDispositionStatus;',
    conclusion integer comment '处置结论 @Type=ReconciliationDispositionConclusion;',
    settlement_impact integer not null comment '对结算资格的影响枚举 @Type=SettlementImpact;',
    reason varchar(2048) not null comment '处置业务原因',
    evidence varchar(4096) not null comment '业务证据文本',
    follow_up varchar(2048) comment '后续处理说明',
    disposed_at timestamp with time zone not null comment '差异处置时间',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;'
);
comment on table reconciliation_disposition is '对账差异处置：保存操作人对差异项的授权、结论、影响和证据 @Parent=reconciliation_item;';

create table reconciliation_confirmation_fact (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    reconciliation_item_id varchar(36) not null comment '所属对账差异项标识 @ParentRef;',
    source_difference_identity varchar(128) not null comment '来源差异身份',
    merchant_id varchar(64) not null comment '商户标识',
    channel_id varchar(64) not null comment '渠道标识',
    operator_identity varchar(128) not null comment '操作人身份',
    confirmation_reason varchar(2048) not null comment '确认原因',
    evidence varchar(4096) not null comment '业务证据文本',
    transaction_kind integer not null comment '交易类型枚举 @Type=ReconciliationTransactionKind;',
    amount decimal(19, 4) not null comment '本次业务金额',
    currency varchar(3) not null comment '业务币种',
    external_transaction_identity varchar(128) not null comment '外部交易身份',
    payment_id varchar(36) comment '关联支付标识 @RefAggregate=Payment;',
    refund_id varchar(36) comment '关联退款标识 @RefAggregate=Refund;',
    confirmed_at timestamp with time zone not null comment '确认时间',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;'
);
comment on table reconciliation_confirmation_fact is '对账确认事实：保存经授权确认后的外部交易事实，不覆盖原始差异证据 @Parent=reconciliation_item;';

create table merchant_settlement (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    merchant_id varchar(64) not null comment '商户标识',
    execution_channel_id varchar(64) comment '结算执行渠道快照；不参与 merchant/currency/period scope、唯一性或幂等判定',
    currency varchar(3) not null comment '业务币种',
    period_type varchar(32) not null default 'DAILY' comment '结算周期类型',
    period_start timestamp with time zone not null comment '结算周期开始时间',
    period_end timestamp with time zone not null comment '结算周期结束时间',
    business_timezone varchar(64) not null comment '业务日计算时区',
    scope_identity varchar(512) not null comment '结算范围身份：merchantId + currency + settlementPeriod(start,end,timezone)，不含渠道或单日表达',
    effective_scope_identity varchar(512) comment '当前生效范围身份：仅 merchantId + currency + settlementPeriod(start,end,timezone)',
    status integer not null comment '当前业务状态枚举 @Type=MerchantSettlementStatus;',
    eligible_count integer not null default 0 comment '纳入结算的交易数量',
    excluded_count integer not null default 0 comment '排除交易数量',
    blocker_summary varchar(4096) comment '结算阻断摘要',
    payment_gross_amount decimal(19, 4) not null comment '支付毛额',
    refund_gross_amount decimal(19, 4) not null comment '退款毛额',
    fee_total_amount decimal(19, 4) not null comment '手续费合计',
    adjustment_total_amount decimal(19, 4) not null comment '调整金额合计',
    net_amount decimal(19, 4) not null comment '结算净额',
    composition_frozen boolean not null default false comment '结算构成是否冻结',
    execution_group_identity varchar(128) comment '执行批次身份',
    predecessor_settlement_id varchar(36) comment '前序结算单标识 @RefAggregate=MerchantSettlement;',
    replacement_settlement_id varchar(36) comment '替代结算单标识 @RefAggregate=MerchantSettlement;',
    confirmed_by varchar(128) comment '确认操作人',
    confirmed_at timestamp with time zone comment '确认时间',
    confirmed_reason varchar(2048) comment '确认原因',
    confirmed_evidence varchar(4096) comment '确认证据',
    voided_by varchar(128) comment '作废操作人',
    void_reason varchar(2048) comment '作废原因',
    void_evidence varchar(4096) comment '作废证据',
    voided_at timestamp with time zone comment '作废时间',
    settled_fact_formed boolean not null default false comment '结算完成事实是否已形成',
    external_settlement_identity varchar(128) comment '外部结算身份',
    completed_at timestamp with time zone comment '完成时间',
    last_rejection_summary varchar(2048) comment '最近一次拒绝摘要',
    last_conflict_summary varchar(2048) comment '最近一次矛盾摘要',
    last_review_summary varchar(2048) comment '最近一次复核摘要',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_merchant_settlement_effective_scope unique (effective_scope_identity)
);
comment on table merchant_settlement is '商户结算单：按 merchant+currency+settlementPeriod 唯一冻结跨渠道结算构成；渠道仅保留在线项和执行证据中';

create table settlement_line (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    merchant_settlement_id varchar(36) not null comment '所属商户结算单标识 @ParentRef;',
    line_identity varchar(128) not null comment '结算明细稳定身份',
    source_kind integer not null comment '结算来源类型枚举 @Type=SettlementLineSourceKind;',
    transaction_kind integer not null comment '交易类型枚举 @Type=com.only4.cap4k.reference.payment.domain.aggregates.reconciliation_batch.enums.ReconciliationTransactionKind;',
    source_fact_identity varchar(128) not null comment '结算来源事实身份',
    decision varchar(16) not null comment '候选判断：INCLUDED 或 EXCLUDED',
    reason_code varchar(64) not null comment '稳定的纳入或排除原因代码',
    effective_consumption_identity varchar(512) comment '来源事实消费身份',
    fee_fact_identity varchar(128) comment '手续费事实身份',
    payment_id varchar(36) comment '关联支付标识 @RefAggregate=Payment;',
    payment_attempt_id varchar(36) comment '关联支付尝试标识',
    refund_id varchar(36) comment '关联退款标识 @RefAggregate=Refund;',
    refund_attempt_id varchar(36) comment '关联退款尝试标识',
    reconciliation_batch_id varchar(36) comment '关联对账批次标识 @RefAggregate=ReconciliationBatch;',
    reconciliation_run_id varchar(36) comment '所属对账运行标识',
    reconciliation_item_id varchar(36) comment '所属对账差异项标识',
    reconciliation_confirmation_fact_id varchar(36) comment '关联对账确认事实标识',
    external_transaction_identity varchar(128) not null comment '外部交易身份',
    gross_amount decimal(19, 4) not null comment '结算明细毛额',
    fee_amount decimal(19, 4) not null comment '结算明细手续费',
    signed_net_amount decimal(19, 4) not null comment '带符号结算净额',
    currency varchar(3) not null comment '业务币种',
    occurred_at timestamp with time zone not null comment '外部事实发生时间',
    recorded_at timestamp with time zone not null comment '记录时间',
    fee_basis_points integer comment '手续费基点',
    fee_fixed_amount decimal(19, 4) comment '固定手续费金额',
    fee_rounding_mode varchar(32) comment '手续费舍入模式',
    fee_currency_precision integer comment '手续费币种精度',
    fee_calculation_amount decimal(19, 4) comment '手续费计算金额',
    eligibility_basis varchar(2048) not null comment '纳入结算的资格依据',
    confirmation_reason varchar(2048) comment '确认原因',
    confirmation_evidence varchar(4096) comment '确认证据',
    adjustment_source_identity varchar(128) comment '调整来源身份',
    adjustment_evidence varchar(4096) comment '调整证据',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_settlement_line_identity unique (merchant_settlement_id, line_identity),
    constraint uk_settlement_line_source unique (merchant_settlement_id, source_kind, source_fact_identity),
    constraint uk_settlement_line_effective_consumption unique (effective_consumption_identity)
);
comment on table settlement_line is '结算候选快照：保存纳入或排除判断、来源事实及费用证据；只有 INCLUDED 参与结算金额与消费身份 @Parent=merchant_settlement;';

create table settlement_execution_attempt (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    merchant_settlement_id varchar(36) not null comment '所属商户结算单标识 @ParentRef;',
    attempt_sequence integer not null comment '结算执行尝试序号',
    execution_id varchar(128) not null comment '调用方提供的全局稳定执行身份',
    idempotency_key varchar(128) not null comment '本次执行命令幂等键快照',
    executor_script varchar(32) not null comment '首次消费的 reference executor 脚本',
    executor_observation varchar(32) not null comment '首次冻结的 executor 观察结果',
    execution_group_identity varchar(128) not null comment '执行批次身份',
    request_identity varchar(128) not null comment '执行请求幂等身份',
    channel_id varchar(64) not null comment '渠道标识',
    status integer not null comment '当前业务状态枚举 @Type=SettlementExecutionAttemptStatus;',
    initiated_at timestamp with time zone not null comment '渠道请求发起时间',
    accepted_at timestamp with time zone comment '渠道受理时间',
    review_after_minutes_snapshot integer not null comment '结果复核等待分钟数快照',
    review_after_at timestamp with time zone not null comment '进入结果复核的时间',
    amount decimal(19, 4) not null comment '本次业务金额',
    currency varchar(3) not null comment '业务币种',
    external_settlement_identity varchar(128) comment '外部结算身份',
    final_result integer comment '最终结果枚举 @Type=SettlementExecutionFinalResult;',
    result_occurred_at timestamp with time zone comment '渠道结果发生时间',
    notification_receive_count integer not null default 0 comment '通知累计接收次数',
    notification_first_received_at timestamp with time zone comment '首次收到通知时间',
    notification_last_received_at timestamp with time zone comment '最近收到通知时间',
    verified_notification_count integer not null default 0 comment '通知累计验证通过次数',
    rejected_notification_count integer not null default 0 comment '通知累计拒绝次数',
    conflicting_notification_count integer not null default 0 comment '通知累计矛盾次数',
    verdict_summary varchar(2048) comment '系统判定摘要',
    rejection_summary varchar(2048) comment '拒绝处理摘要',
    conflict_summary varchar(2048) comment '矛盾处理摘要',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_settlement_execution_id unique (execution_id),
    constraint uk_settlement_execution_attempt_request unique (channel_id, request_identity),
    constraint uk_settlement_execution_attempt_sequence unique (merchant_settlement_id, attempt_sequence)
);
comment on table settlement_execution_attempt is '结算执行尝试：保存一次出款请求、等待窗口、结果和重试/冲突证据 @Parent=merchant_settlement;';

create table settlement_result_receipt (

    id varchar(36) primary key comment '记录唯一标识 @Managed=identifier.uuid7;',
    version bigint not null default 0 comment '并发更新版本号 @Managed=version;',
    settlement_execution_attempt_id varchar(36) not null comment '关联结算执行尝试标识 @ParentRef;',
    notification_identity varchar(128) not null comment '渠道通知幂等身份',
    payload_fingerprint varchar(128) not null comment '通知载荷指纹',
    channel_id varchar(64) not null comment '渠道标识',
    execution_group_identity varchar(128) not null comment '执行批次身份',
    request_identity varchar(128) not null comment '执行请求幂等身份',
    external_settlement_identity varchar(128) not null comment '外部结算身份',
    amount decimal(19, 4) not null comment '本次业务金额',
    currency varchar(3) not null comment '业务币种',
    result varchar(32) not null comment '渠道原始结果',
    result_code varchar(128) comment '渠道原始结果码',
    occurred_at timestamp with time zone not null comment '外部事实发生时间',
    first_received_at timestamp with time zone not null comment '首次收到通知时间',
    last_received_at timestamp with time zone not null comment '最近收到通知时间',
    receive_count integer not null default 1 comment '同一通知累计接收次数',
    verified boolean not null default false comment '通知是否验证通过',
    accepted boolean not null default false comment '通知是否被业务接受',
    decision integer not null comment '系统对通知的处理决策 @Type=SettlementResultDisposition;',
    verdict_summary varchar(2048) comment '系统判定摘要',
    rejection_summary varchar(2048) comment '拒绝处理摘要',
    conflict_summary varchar(2048) comment '矛盾处理摘要',
    created_at timestamp with time zone not null comment '记录创建时间 @Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '记录创建者 @Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '记录最后更新时间 @Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '记录最后更新者 @Managed=enrichment.audit-actor.updated-by;',
    constraint uk_settlement_result_receipt unique (settlement_execution_attempt_id, notification_identity)
);
comment on table settlement_result_receipt is '结算结果回执：保存渠道出款回调的稳定身份、金额、结果和重复/矛盾证据 @Parent=settlement_execution_attempt;';
