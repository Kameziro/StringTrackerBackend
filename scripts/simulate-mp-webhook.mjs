#!/usr/bin/env node
// Simula, em dev, a notificação de pedido que o Mercado Pago envia ao webhook, assinada como ele assina
// (x-signature: ts=...,v1=HMAC-SHA256 hex de "id:{data.id};request-id:{x-request-id};ts:{ts};").
// O backend valida a assinatura e lê o pedido no Mercado Pago: o estado vem do provedor, nunca daqui.
//
// Uso:
//   MP_WEBHOOK_SECRET=... node scripts/simulate-mp-webhook.mjs <ORDER_ID>
//   MP_WEBHOOK_SECRET=... node scripts/simulate-mp-webhook.mjs --latest   (pedido do pagamento mais recente)
// Opcional: WEBHOOK_URL (padrão http://localhost:8080/api/payments/mercadopago/webhook).
//
// O sandbox aprova o Pix com payer.first_name "APRO" (MP_SANDBOX_PAYER_FIRST_NAME no backend) alguns segundos
// depois de criar o pedido: enquanto o pagamento local seguir PENDING, a notificação é reenviada por até 30 s.

import { execFileSync } from 'node:child_process';
import { createHmac, randomUUID } from 'node:crypto';

const secret = process.env.MP_WEBHOOK_SECRET;
const url = process.env.WEBHOOK_URL ?? 'http://localhost:8080/api/payments/mercadopago/webhook';
const arg = process.argv[2];
const RETRY_MS = 3_000;
const TIMEOUT_MS = 30_000;

if (!secret) fail('Defina MP_WEBHOOK_SECRET com a mesma chave que o backend usa.');
if (!arg) fail('Informe o id do pedido (ORD...) ou --latest.');

const orderId = arg === '--latest' ? sql('select provider_payment_id from payments order by id desc limit 1') : arg;
if (!orderId) fail('Nenhum pagamento encontrado no banco local.');

const deadline = Date.now() + TIMEOUT_MS;
for (;;) {
  const response = await notify(orderId);
  if (!response.ok) fail(`Pedido ${orderId}: webhook respondeu ${response.status} ${await response.text()}`.trim());
  const status = sql(`select status from payments where provider_payment_id = '${orderId.replace(/'/g, '')}'`);
  console.log(`Pedido ${orderId}: webhook respondeu ${response.status}; pagamento ${status || 'não encontrado no banco local'}`);
  if (status !== 'PENDING' || Date.now() > deadline) break;
  await new Promise((resolve) => setTimeout(resolve, RETRY_MS));
}

function notify(id) {
  const requestId = randomUUID();
  const ts = String(Date.now());
  const v1 = createHmac('sha256', secret).update(`id:${id.toLowerCase()};request-id:${requestId};ts:${ts};`).digest('hex');
  return fetch(`${url}?data.id=${encodeURIComponent(id)}&type=order`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-signature': `ts=${ts},v1=${v1}`, 'x-request-id': requestId },
    body: JSON.stringify({ id: randomUUID(), type: 'order', action: 'order.updated', data: { id } }),
  });
}

/** Consulta o container Postgres de dev (stringtracker-postgres); vazio se não houver resultado. */
function sql(query) {
  return execFileSync('docker', ['exec', 'stringtracker-postgres', 'sh', '-c', `psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "${query}"`], {
    encoding: 'utf8',
  }).trim();
}

function fail(message) {
  console.error(message);
  process.exit(1);
}
