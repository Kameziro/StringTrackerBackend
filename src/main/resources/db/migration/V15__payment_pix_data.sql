-- Dados do Pix guardados na cobrança para a tela de pagamento ser reaberta enquanto a reserva está HELD.
-- Nulos nas cobranças anteriores, que já venceram.

ALTER TABLE payments
    ADD COLUMN pix_copia_e_cola    TEXT,
    ADD COLUMN pix_qr_code_base64  TEXT,
    ADD COLUMN pix_ticket_url      TEXT;
