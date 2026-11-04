import assert from "node:assert/strict";
import {createHmac} from "node:crypto";
import {dispatchHandoff,retryDelaySeconds,validateBridgeUrl} from "../bridge/relay-outbox.mjs";
const endpoint="https://test-ref123.supabase.co/functions/v1/up-order-bridge";
const secret="server-only-bridge-secret-0123456789abcdef";
const envelope={action:"create_job",event_id:"d3e7b77f-fd73-4ab2-bb37-a503ef8e6c9f",order:{
 id:"cff10d4a-a789-4398-a6ac-d68b68f5cd44",code:"711572",
 fulfillment_type:"ENTREGA",status:"PRONTO",
 dropoff:{street:"Teste",number:"20"},pickup:{street:"Loja"},
 recipient_name:"Cliente teste",collection_amount:0
}};
const epochSeconds=1793779200;
const captures=[];
const fake=(status,payload)=>async (url,request)=>{
 captures.push({url,request});
 return {status,ok:status>=200&&status<300,json:async()=>payload};
};
const result=await dispatchHandoff({endpoint,envelope,secret,epochSeconds,
 fetchImpl:fake(200,{ok:true,job_id:"7297230f-282b-4e06-abcd-49840948e2a3"})});
assert.equal(result.decision,"ack");
assert.equal(captures.length,1);
const {request}=captures[0];
assert.equal(request.method,"POST");
assert.equal(request.body,JSON.stringify(envelope));
assert.equal(request.headers["x-up-signature"],createHmac("sha256",secret)
 .update(String(epochSeconds)+"\n"+request.body).digest("hex"),"HMAC validado no mesmo corpo enviado");
assert.equal((await dispatchHandoff({endpoint,envelope,secret,epochSeconds,
 fetchImpl:fake(200,{ok:true,job_id:result.jobId,idempotent:true})})).idempotent,true);
assert.deepEqual(await dispatchHandoff({endpoint,envelope,secret,epochSeconds,
 fetchImpl:async()=>{throw Error("offline")}}),{decision:"retry",reason:"network_error"});
assert.equal((await dispatchHandoff({endpoint,envelope,secret,epochSeconds,
 fetchImpl:fake(503,{ok:false,error:"up_bridge_failed"})})).decision,"retry");
assert.equal((await dispatchHandoff({endpoint,envelope,secret,epochSeconds,
 fetchImpl:fake(429,{ok:false})})).decision,"retry");
assert.equal((await dispatchHandoff({endpoint,envelope,secret,epochSeconds,
 fetchImpl:fake(409,{ok:false,error:"order_already_received_with_different_payload"})})).decision,"halt");
assert.equal((await dispatchHandoff({endpoint,envelope,secret,epochSeconds,
 fetchImpl:fake(401,{ok:false,error:"invalid_signature"})})).decision,"halt");
assert.equal(retryDelaySeconds(1),12);
assert.equal(retryDelaySeconds(2),24);
assert.equal(retryDelaySeconds(100),900);
for(const u of ["http://example.com/functions/v1/up-order-bridge",
 "https://example.com/functions/v1/up-order-bridge",
 "https://test-ref123.supabase.co/functions/v1/up-order-bridge?secret=abc"])
 assert.throws(()=>validateBridgeUrl(u),/invalid_bridge_url/);
await assert.rejects(()=>dispatchHandoff({endpoint,envelope,secret:"weak",epochSeconds}),/bridge_secret_too_short/);
console.log("PASS relay: HMAC, ACK, duplicidade, retries de rede, bloqueio de falhas permanentes");
