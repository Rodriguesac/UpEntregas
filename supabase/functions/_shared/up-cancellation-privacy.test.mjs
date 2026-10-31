import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import {canRecordLocation,nextStage,canPickup,canFinish} from "./up-operations.mjs";

const canceled={status:"CANCELADO",raw_payload:{up_state:"CANCELED",up_pickup_at:null}};
const review={status:"CANCELAMENTO_EM_ANALISE",raw_payload:{
 up_state:"CANCELLATION_REVIEW",up_pickup_at:"2026-10-31T14:00:00Z"
}};

test("não é possível publicar GPS ou progredir após cancelamento/suporte",()=>{
 for(const order of [canceled,review]){
  assert.equal(canRecordLocation(order),false);
  assert.equal(canPickup(order),false);
  assert.equal(canFinish(order),false);
  assert.equal(nextStage(order,"COLETANDO"),null);
  assert.equal(nextStage(order,"NO_CLIENTE"),null);
 }
});

test("telemetria, presença, retirada, conclusão e GPS não restauram metadata antiga após cancelamento",()=>{
 const src=fs.readFileSync("supabase/functions/driver-api/index.ts","utf8");
 for(const action of ["presence","telemetry","equipment"]){
  const start=src.indexOf('if (action === "'+action+'")');
  const end=src.indexOf('if (action === "',start+10);
  assert.ok(start>=0&&end>start);
  const segment=src.slice(start,end);
  assert.match(segment,/\.eq\("updated_at",courier\.updated_at\)/,action);
 }
 for(const name of ["courierStage","courierPickup","courierFinish","courierUpdate"]){
  assert.match(src,new RegExp(name+"=await admin\\.from\\(\\\"couriers\\\"\\)\\.update\\("));
 }
 assert.ok((src.match(/\.eq\("updated_at",courier\.updated_at\)/g)||[]).length>=7);
 assert.match(src,/if \(!courierUpdate\.data\) return json\(\{error:"mission_changed"/);
 assert.match(src,/upAtomicDispatch\(admin,/);
});

test("RPC de cancelamento usa o mesmo procedimento atômico do Gestor e Gadministra",()=>{
 const src=fs.readFileSync("supabase/functions/_shared/up-atomic-dispatch.mjs","utf8");
 assert.match(src,/p_cancel_reason:params\.reason\|\|null/);
 assert.match(src,/cancel_reason_required/);
 assert.match(src,/forbidden_cancel_source/);
});
