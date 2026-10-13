-- CANC-05: o admin do clube pode marcar um reembolso FAILED como resolvido por fora (RESOLVED_MANUALLY),
-- registrando quem e quando. O nome do novo estado não cabe em VARCHAR(8).

ALTER TABLE bookings DROP CONSTRAINT ck_bookings_refund_status;

ALTER TABLE bookings
    ALTER COLUMN refund_status TYPE VARCHAR(20),
    ADD COLUMN refund_resolved_by BIGINT,
    ADD COLUMN refund_resolved_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT fk_bookings_refund_resolved_by FOREIGN KEY (refund_resolved_by) REFERENCES users (id),
    ADD CONSTRAINT ck_bookings_refund_status
        CHECK (refund_status IN ('NONE', 'PENDING', 'DONE', 'FAILED', 'RESOLVED_MANUALLY'));
