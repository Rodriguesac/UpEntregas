import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const worker=readFileSync("bridge/store-outbox-worker/index.ts","utf8");
const sql=readFileSync("bridge/store-outbox.sql","utf8");
const triggers=[
 'UP_BRIDGE_ENABLED',
 'UP_BRIDGE_ENDPOINT',
 'UP_BRIDGE_SECRET',
 'UP_WORKER_TOKEN',
 'SUPABASE_SERVICE_ROLE_KEY',
 'req.headers.has("origin")',
 'safeToken(token,workerToken)',
 '.rpc("up_store_outbox_claim"',
 '.rpc("up_store_outbox_finish"',
 'p_limit:5',
 'AbortSignal.timeout(9000)',
 'crypto.subtle.sign("HMAC"',
 'url.hostname===shop.hostname',
 'status:code',
 'summary.failed?',
];
for(const trigger of triggers)assert.ok(worker.includes(trigger),"Worker missing security control: "+trigger);
assert.ok(worker.includes('UP_BRIDGE_ENABLED")!=="true"'),'Bridge must be disabled unless explicitly enabled');
assert.ok(worker.includes('workerToken.length<32')&&worker.includes('secret.length<32'),'Short server secrets forbidden');
assert.ok(!/fdqqwdplprzpqufpgdrm/.test(worker),'No hardcoded existing customer Supabase URLs');
assert.ok(!worker.includes('tracking_points'),'UP does not stream GPS to the store');
assert.ok(!worker.includes('p_decision:"ack"'),'ACK cannot be sent before checking upstream');
assert.ok(sql.includes("if p_decision='ack' and p_job_id is null"),'Cannot ACK UP without a valid job ID');
assert.ok(sql.includes("'RECONCILE'"),'Unknown cross-system status must be preserved for review');
assert.ok(sql.includes("where s.id=ticket.order_id and upper(s.status)='PRONTO'"),'Order revalidated at ACK');
assert.ok(sql.includes("'{up_bridge}',jsonb_build_object("),'Store order must record isolated UP bridge state');
assert.ok(sql.includes("if upper(s.status)<>'PRONTO'"),'Enqueue requires a ready order');
assert.ok(sql.includes("p_payload#>>'{order,id}'<>p_order_id::text"),'Reject tampered order IDs');
assert.ok(sql.includes('online_payment_not_confirmed'),'Payment must be checked on enqueue');
assert.ok(sql.includes('o.attempts>=9'),'No infinite retries');
console.log("PASS worker UP: desativado por padrão, autenticação, replay, confirmação e reconciliação");
