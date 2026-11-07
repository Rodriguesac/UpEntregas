import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const worker=readFileSync("supabase/functions/up-status-relay/index.ts","utf8");
const schema=readFileSync("supabase/up-independent-schema.sql","utf8");
for(const tag of [
 'UP_STATUS_ENABLED")!=="true"',
 'UP_STATUS_WORKER_TOKEN',
 'UP_STATUS_STORE_ENDPOINT',
 'UP_BRIDGE_SECRET',
 'SUPABASE_SERVICE_ROLE_KEY',
 'validatedShopEndpoint',
 'dest.hostname===up.hostname',
 'dest.pathname!=="/functions/v1/up-status-receiver"',
 'req.headers.has("origin")',
 'equalToken(token,workerToken)',
 'crypto.subtle.sign("HMAC"',
 'x-up-timestamp',
 'x-up-signature',
 '.rpc("up_status_callback_claim"',
 '.rpc("up_status_callback_finish"',
 'p_limit:5',
 'signal:AbortSignal.timeout(9000)',
 'concurrent_update_retry',
 'missing_first_event',
 'independent_bridge_not_enabled',
 'data.accepted===true||data.duplicate===true||data.stale===true',
]){
 assert.ok(worker.includes(tag),"UP relay missing: "+tag);
}
assert.ok(!worker.includes("tracking_points"),"Status callback may never include GPS");
assert.ok(!worker.includes("service_role: "), "Never hardcode privileged credentials");
assert.ok(schema.includes("and prior.sequence<e.sequence"),"Deliver events in sequence");
assert.ok(schema.includes("and prior.delivery_to_store_state<>'DELIVERED'"),"Block higher events until prior ACK");
assert.ok(schema.includes("for update of e skip locked"),"Lease callbacks without racing workers");
assert.ok(schema.includes("e.delivery_attempts<9"),"No unbounded callback retry");
assert.ok(schema.includes("up_status_callback_finish"),"Persist callback result");
assert.ok(schema.includes("delivery_to_store_state='SENDING'"),"ACK only the worker owning the lease");
assert.ok(schema.includes("revoke all on function public.up_job_advance"),"Disallow unprivileged status updates");
assert.ok(schema.includes("to service_role"),"Only server executes change RPC");
console.log("PASS UP status relay: disabled default, HMAC, sequence, retry, isolation and privileged calls");
