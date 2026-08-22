-- Runtime H2 projection of composite invariants that are present in design/schema.sql but are not
-- currently emitted as JPA @Table(uniqueConstraints = ...) by the aggregate entity generator.
-- The reliable-event runtime owns __event; these alterations preserve its published text-column schema
-- when Hibernate create-drop derives shorter varchar columns from the current entity annotations.
alter table __event alter column data text;
alter table __event alter column execution_context text;
alter table __event alter column retry_policy text;

alter table reconciliation_batch
    add constraint uk_reconciliation_batch_scope unique (channel_id, currency, reconciliation_date);

alter table reconciliation_run
    add constraint uk_reconciliation_run_statement unique (batch_id, statement_identity, statement_revision);

alter table merchant_settlement
    add constraint uk_merchant_settlement_effective_scope unique (effective_scope_identity);

alter table settlement_line
    add constraint uk_settlement_line_identity unique (merchant_settlement_id, line_identity);

alter table settlement_line
    add constraint uk_settlement_line_source unique (merchant_settlement_id, source_kind, source_fact_identity);

alter table settlement_line
    add constraint uk_settlement_line_effective_consumption unique (effective_consumption_identity);

alter table settlement_execution_attempt
    add constraint uk_settlement_execution_attempt_request unique (channel_id, request_identity);

alter table settlement_execution_attempt
    add constraint uk_settlement_execution_attempt_sequence unique (merchant_settlement_id, attempt_sequence);

alter table settlement_result_receipt
    add constraint uk_settlement_result_receipt unique (settlement_execution_attempt_id, notification_identity);


alter table payment
    add constraint uk_payment_merchant_order_success unique (merchant_order_success_identity);

alter table payment
    add constraint uk_payment_notification_intent unique (merchant_success_notification_intent_identity);

alter table payment_notification_receipt
    add constraint uk_payment_notification_receipt_payload unique (payment_attempt_id, notification_identity, payload_identity);

alter table payment_review_case
    add constraint uk_payment_review_case unique (payment_id, review_identity);

alter table payment_review_decision
    add constraint uk_payment_review_decision unique (payment_review_case_id, decision_identity);
