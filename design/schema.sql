drop table if exists payment_notification_receipt;
drop table if exists payment_attempt;
drop table if exists merchant_channel_configuration;
drop table if exists payment;

create table payment (
    id varchar(36) primary key comment '@Managed=identifier.uuid7;',
    version bigint not null default 0 comment '@Managed=version;',
    merchant_id varchar(64) not null,
    merchant_order_number varchar(128) not null,
    idempotency_key varchar(128) not null,
    amount decimal(19, 4) not null,
    currency varchar(3) not null,
    payment_method varchar(64) not null,
    status integer not null comment '@Type=PaymentStatus;',
    created_at timestamp with time zone not null comment '@Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '@Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '@Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '@Managed=enrichment.audit-actor.updated-by;',
    expires_at timestamp with time zone not null,
    succeeded_at timestamp with time zone,
    channel_transaction_id varchar(128),
    success_fact_formed boolean not null default false,
    attempt_count integer not null default 0,
    notification_receive_count integer not null default 0,
    rejected_notification_count integer not null default 0,
    conflicting_notification_count integer not null default 0,
    last_notification_identity varchar(128),
    last_notification_received_at timestamp with time zone,
    last_rejection_summary varchar(1024),
    last_conflict_summary varchar(1024),
    merchant_success_notification_intent_count integer not null default 0,
    settlement_blocked boolean not null default false,
    constraint uk_payment_merchant_idempotency unique (merchant_id, idempotency_key)
);

create table payment_attempt (
    id varchar(36) primary key comment '@Managed=identifier.uuid7;',
    version bigint not null default 0 comment '@Managed=version;',
    payment_id varchar(36) not null comment '@ParentRef;',
    channel_id varchar(64) not null,
    channel_configuration_id varchar(36) not null,
    channel_configuration_snapshot varchar(2048) not null,
    request_identity varchar(128) not null,
    status integer not null comment '@Type=PaymentAttemptStatus;',
    initiated_at timestamp with time zone not null,
    channel_transaction_id varchar(128),
    final_result integer comment '@Type=PaymentAttemptFinalResult;',
    result_occurred_at timestamp with time zone,
    notification_identity varchar(128),
    notification_receive_count integer not null default 0,
    notification_first_received_at timestamp with time zone,
    notification_last_received_at timestamp with time zone,
    verified_notification_count integer not null default 0,
    rejected_notification_count integer not null default 0,
    conflicting_notification_count integer not null default 0,
    verdict_summary varchar(1024),
    rejection_summary varchar(1024),
    conflict_summary varchar(1024),
    created_at timestamp with time zone not null comment '@Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '@Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '@Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '@Managed=enrichment.audit-actor.updated-by;',
    constraint uk_payment_attempt_request unique (channel_id, request_identity)
);

comment on table payment_attempt is '@Parent=payment;';


create table payment_notification_receipt (
    id varchar(36) primary key comment '@Managed=identifier.uuid7;',
    version bigint not null default 0 comment '@Managed=version;',
    payment_attempt_id varchar(36) not null comment '@ParentRef;',
    notification_identity varchar(128) not null,
    channel_id varchar(64) not null,
    channel_transaction_id varchar(128) not null,
    amount decimal(19, 4) not null,
    currency varchar(3) not null,
    result varchar(32) not null,
    occurred_at timestamp with time zone not null,
    first_received_at timestamp with time zone not null,
    last_received_at timestamp with time zone not null,
    receive_count integer not null default 1,
    verified boolean not null default false,
    accepted boolean not null default false,
    decision integer not null comment '@Type=ChannelResultDisposition;',
    verdict_summary varchar(1024),
    rejection_summary varchar(1024),
    conflict_summary varchar(1024),
    created_at timestamp with time zone not null comment '@Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '@Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '@Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '@Managed=enrichment.audit-actor.updated-by;',
    constraint uk_payment_notification_receipt unique (payment_attempt_id, notification_identity)
);

comment on table payment_notification_receipt is '@Parent=payment_attempt;';

create table merchant_channel_configuration (
    id varchar(36) primary key comment '@Managed=identifier.uuid7;',
    version bigint not null default 0 comment '@Managed=version;',
    merchant_id varchar(64) not null,
    channel_id varchar(64) not null,
    currency varchar(3) not null,
    payment_method varchar(64) not null,
    minimum_amount decimal(19, 4) not null,
    maximum_amount decimal(19, 4) not null,
    status integer not null comment '@Type=MerchantChannelConfigurationStatus;',
    routing_priority integer not null default 100,
    channel_rule_summary varchar(2048) not null,
    activated_at timestamp with time zone not null,
    retired_at timestamp with time zone,
    created_at timestamp with time zone not null comment '@Managed=enrichment.audit-time.created-at;',
    created_by varchar(128) not null comment '@Managed=enrichment.audit-actor.created-by;',
    updated_at timestamp with time zone not null comment '@Managed=enrichment.audit-time.updated-at;',
    updated_by varchar(128) not null comment '@Managed=enrichment.audit-actor.updated-by;',
    constraint uk_merchant_channel_configuration unique (merchant_id, channel_id, currency, payment_method)
);
