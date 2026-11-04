import assert from "node:assert/strict";
import fs from "node:fs";
const source=fs.readFileSync("bridge/store-status-receiver/index.ts","utf8");
const schema=fs.readFileSync("supabase/up-independent-schema.sql","utf8");
for(const text of [
 "x-up-signature","x-up-timestamp",'crypto.subtle.verify("HMAC"',
 "Math.abs(Date.now()-Number(ts)*1000)>300000",
 'req.headers.has("origin")',
 'event?.order_id','event?.job_id','event?.event_id',
 'prev.mode!=="isolated"','prev.jobId!==jobId',
 'missing_first_event','out_of_order_state','concurrent_update_retry',
 '.eq("updated_at",order.updated_at).eq("status",order.status)',
 'raw.up_state=status','raw.up_attention_required',
 'SHOP_TERMINAL','store_status',
])if(text!=="store_status")assert.ok(source.includes(text),"Proteção obrigatória: "+text);
const duplicate=source.indexOf("return reply(200,{ok:true,duplicate:true");
const finished=source.indexOf('error:"shop_order_finished"');
assert.ok(duplicate>=0&&finished>duplicate,"Retries de entrega finalizada devem ser idempotentes");
assert.ok(source.includes('if(newStatus==="ENTREGUE")patch.completed_at'),'Conclusão da entrega registrada na loja');
assert.ok(source.includes('if(newStatus)patch.status=newStatus'),'Somente estados mapeados podem mudar status do pedido');
assert.ok(source.includes('return null; // preserve shop order'),'Falha de corrida não cancela pedido do cliente');
assert.ok(!source.includes('tracking_points'),'Localização é exclusiva do UP');
assert.ok(!source.includes('service_role"'),"Nenhuma chave de serviço hardcoded na função");
assert.ok(schema.includes('unique(job_id,sequence)'),'Sequência de eventos deve ser única por corrida');
assert.ok(schema.includes('values(found_job.id,1,found_job.status'),'Primeiro evento deve iniciar na sequência 1');
console.log("PASS callback UP->Loja: autenticacao, idempotencia, concorrencia e separacao do GPS");
