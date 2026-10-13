-- Reservas de aula, pagamentos Pix e eventos de webhook.
-- bookings.registration_date (BaseEntity) faz o papel de "criada em".

CREATE TABLE bookings (
    id                   BIGSERIAL PRIMARY KEY,
    lesson_slot_id       BIGINT NOT NULL,
    seat                 SMALLINT NOT NULL,
    student_user_id      BIGINT,
    guest_name           VARCHAR(120),
    guest_phone          VARCHAR(20),
    partner_name         VARCHAR(120),
    lesson_type          VARCHAR(8) NOT NULL,
    price_cents          BIGINT NOT NULL,
    payment_mode         VARCHAR(8) NOT NULL,
    status               VARCHAR(10) NOT NULL,
    hold_expires_at      TIMESTAMP WITH TIME ZONE,
    created_by           BIGINT NOT NULL,
    cancelled_by         BIGINT,
    cancelled_at         TIMESTAMP WITH TIME ZONE,
    refund_status        VARCHAR(8) NOT NULL DEFAULT 'NONE',
    refund_amount_cents  BIGINT,
    reminder_sent_at     TIMESTAMP WITH TIME ZONE,
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    registration_date    TIMESTAMP WITH TIME ZONE NOT NULL,
    update_date          TIMESTAMP WITH TIME ZONE NOT NULL,
    exclusion_date       TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_bookings_lesson_slot FOREIGN KEY (lesson_slot_id) REFERENCES lesson_slots (id),
    CONSTRAINT fk_bookings_student FOREIGN KEY (student_user_id) REFERENCES users (id),
    CONSTRAINT fk_bookings_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_bookings_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES users (id),
    CONSTRAINT ck_bookings_lesson_type CHECK (lesson_type IN ('SINGLES', 'DOUBLES', 'GROUP')),
    CONSTRAINT ck_bookings_payment_mode CHECK (payment_mode IN ('PIX', 'OFFLINE')),
    CONSTRAINT ck_bookings_status CHECK (status IN ('HELD', 'CONFIRMED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_bookings_refund_status CHECK (refund_status IN ('NONE', 'PENDING', 'DONE', 'FAILED')),
    CONSTRAINT ck_bookings_student_or_guest CHECK (student_user_id IS NOT NULL OR guest_name IS NOT NULL)
);

-- BOOK-05: cada vaga (seat) de um horário tem no máximo uma reserva ativa; aula particular = capacidade 1
CREATE UNIQUE INDEX uk_bookings_active_seat ON bookings (lesson_slot_id, seat)
    WHERE status IN ('HELD', 'CONFIRMED');

CREATE INDEX idx_bookings_hold_expiry ON bookings (hold_expires_at) WHERE status = 'HELD';
CREATE INDEX idx_bookings_student_user_id ON bookings (student_user_id);

CREATE TABLE payments (
    id                   BIGSERIAL PRIMARY KEY,
    booking_id           BIGINT NOT NULL,
    provider             VARCHAR(16) NOT NULL,
    provider_payment_id  VARCHAR(64),
    amount_cents         BIGINT NOT NULL,
    status               VARCHAR(16) NOT NULL,
    expires_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    refund_attempts      INT NOT NULL DEFAULT 0,
    next_refund_at       TIMESTAMP WITH TIME ZONE,
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    registration_date    TIMESTAMP WITH TIME ZONE NOT NULL,
    update_date          TIMESTAMP WITH TIME ZONE NOT NULL,
    exclusion_date       TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT uk_payments_booking UNIQUE (booking_id),
    CONSTRAINT uk_payments_provider_payment UNIQUE (provider_payment_id),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'APPROVED', 'EXPIRED', 'REFUNDED'))
);

-- BOOK-09: cada notificação do provedor é processada uma vez
CREATE TABLE payment_events (
    provider_event_id  VARCHAR(64) PRIMARY KEY,
    received_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
