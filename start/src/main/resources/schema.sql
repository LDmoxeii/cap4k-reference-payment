-- Runtime H2 projection of composite invariants that are present in design/schema.sql but are not
-- currently emitted as JPA @Table(uniqueConstraints = ...) by the aggregate entity generator.
alter table reconciliation_batch
    add constraint uk_reconciliation_batch_scope unique (channel_id, currency, reconciliation_date);

alter table reconciliation_run
    add constraint uk_reconciliation_run_statement unique (batch_id, statement_identity, statement_revision);
