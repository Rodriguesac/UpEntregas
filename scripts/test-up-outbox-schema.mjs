import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const outbox=readFileSync("bridge/store-outbox.sql","utf8");
const up=readFileSync("supabase/up-independent-schema.sql","utf8");
const relay=readFileSync("bridge/relay-outbox.mjs","utf8");
for(const part of [
 "create schema if not exists up_bridge_private",
 "revoke all on schema up_bridge_private from public, anon, authenticated",
 "order_id uuid not null unique",
 "event_id uuid not null unique",
 "payload jsonb not null",
 "for update skip locked",
 "lease_id=gen_random_uuid()",
 "leased_until=now()+interval '90 seconds'",
 "o.state='SENDING'",
 "up_store_outbox_claim",
 "up_store_outbox_finish",
 "revoke all on function public.up_store_outbox_claim",
 "revoke all on function public.up_store_outbox_finish",
 "grant execute on function public.up_store_outbox_claim",
 "grant execute on function public.up_store_outbox_finish"
])assert.ok(outbox.includes(part),"Outbox requires "+part);
for(const s of ["grant select,insert,update,delete on public.up_couriers",
 "to service_role;","grant usage,select on sequence public.up_tracking_points_id_seq",
 "unique(job_id,sequence)"]){
 assert.ok(up.includes(s),"Independent UP schema requires "+s);
}
assert.ok(!outbox.includes("tracking_points"),"Outbox cannot contain GPS points");
assert.ok(!relay.includes("setInterval"),"No client polling");
assert.ok(relay.includes("retryDelaySeconds"),"Relay must back off");
assert.ok(relay.includes('Math.min(900'),"Relay limits retry interval");
console.log("PASS outbox: privacidade, tentativas, concorrencia, RLS e service role");
