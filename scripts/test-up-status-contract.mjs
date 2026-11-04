import assert from "node:assert/strict";
import {newStatusEvent,customerStatus,validateProgress} from "../bridge/status-contract.mjs";
const E=(sequence,state,eventId)=>{
 return newStatusEvent({
  eventId:eventId??("9f1a3a34-ded7-4839-8845-"+String(sequence).padStart(12,"0")),
  orderId:"a8de0011-2222-4444-8888-999900001111",
  jobId:"e4411100-2222-4444-8888-999900002222",
  sequence,state,occurredAt:"2026-11-04T13:30:00Z"
 });
};
const base={mode:"isolated",sequence:0};
const start=validateProgress(base,E(1,"WAITING_DRIVER"));
assert.equal(start.decision,"apply");
assert.equal(start.shopStatus,"AGUARDANDO_ENTREGADOR");
assert.equal(validateProgress(start.next,E(1,"WAITING_DRIVER")).decision,"duplicate","repetição idempotente");
assert.equal(validateProgress(start.next,E(1,"OFFERED")).decision,"stale","mesma sequência alterada é obsoleta");
const offer=validateProgress(start.next,E(2,"OFFERED"));
const accept=validateProgress(offer.next,E(3,"ACCEPTED"));
const toStore=validateProgress(accept.next,E(4,"TO_STORE"));
const atStore=validateProgress(toStore.next,E(5,"AT_STORE"));
const toClient=validateProgress(atStore.next,E(6,"TO_CUSTOMER"));
assert.equal(toClient.shopStatus,"EM_ENTREGA");
const atClient=validateProgress(toClient.next,E(7,"AT_CUSTOMER"));
const delivered=validateProgress(atClient.next,E(8,"DELIVERED"));
assert.equal(delivered.shopStatus,"ENTREGUE");
assert.throws(()=>validateProgress(delivered.next,E(9,"TO_STORE")),/terminal/);
assert.equal(customerStatus("FAILED"),null);
assert.equal(customerStatus("CANCELLED"),null);
assert.equal(customerStatus("AT_STORE"),"AGUARDANDO_ENTREGADOR");
const fail=validateProgress(atStore.next,E(6,"FAILED"));
assert.equal(fail.shopStatus,null,"Falha do motoboy não cancela pedido da loja");
assert.equal(validateProgress(delivered.next,E(7,"AT_CUSTOMER")).decision,"stale","eventos atrasados ignorados");
assert.throws(()=>validateProgress(toClient.next,E(7,"TO_STORE")),/out_of_order/);
assert.throws(()=>validateProgress({},E(1,"WAITING_DRIVER")),/isolated/);
assert.throws(()=>validateProgress(base,E(2,"OFFERED")),/missing_initial_event/);
assert.throws(()=>validateProgress({...start.next,jobId:"c0000000-1111-4444-8888-999900000000"},E(2,"OFFERED")),/job_mismatch/);
assert.throws(()=>newStatusEvent({eventId:"x",orderId:"x",jobId:"x",sequence:1,state:"WAITING_DRIVER",occurredAt:"2026-11-04"}),/identity/);
assert.throws(()=>newStatusEvent({eventId:"9f1a3a34-ded7-4839-8845-000000000001",orderId:"a8de0011-2222-4444-8888-999900001111",jobId:"e4411100-2222-4444-8888-999900002222",sequence:1,state:"GPS",occurredAt:"2026-11-04"}),/unknown/);
const safeJson=JSON.stringify(E(1,"WAITING_DRIVER"));
for(const sensitive of ["latitude","longitude","pix_key","cpf","payment_details","recipient_phone"])assert.ok(!safeJson.includes(sensitive));
console.log("PASS retorno UP: sequência, cancelamento seguro, duplicatas, isolamento e privacidade");
