// Relay privado Loja -> UP. Uso exclusivo no servidor; nenhuma chave no APK.
// A fila (outbox) preserva a mensagem original; esta função faz uma tentativa.
import {signHandoff} from "./handoff-contract.mjs";

const text=v=>String(v??"").trim();
const permanentErrors=new Set([
 "invalid_handoff","invalid_order_id","invalid_delivery_address","missing_delivery_address",
 "online_payment_not_confirmed","order_already_received_with_different_payload",
 "invalid_collection_amount","invalid_action"
]);
export function retryDelaySeconds(attempts){
 const n=Number(attempts);
 if(!Number.isSafeInteger(n)||n<1)throw Error("invalid_attempts");
 return Math.min(900,Math.round(12*Math.pow(2,Math.min(n-1,7))));
}
export function validateBridgeUrl(endpoint){
 let url;
 try{url=new URL(endpoint)}catch{throw Error("invalid_bridge_url")}
 if(url.protocol!=="https:"||!url.hostname.endsWith(".supabase.co")||
  !url.pathname.endsWith("/functions/v1/up-order-bridge")||
  url.username||url.password||url.search||url.hash)
  throw Error("invalid_bridge_url");
 return url.href;
}
export async function dispatchHandoff({
 endpoint,envelope,secret,fetchImpl=fetch,epochSeconds=Math.floor(Date.now()/1000)
}={}){
 const url=validateBridgeUrl(endpoint);
 if(!envelope||envelope.action!=="create_job"||!envelope.event_id||!envelope.order?.id)
  throw Error("invalid_envelope");
 const signed=await signHandoff(envelope,secret,epochSeconds);
 let response;
 try{
  response=await fetchImpl(url,{
   method:"POST",headers:{"content-type":"application/json",
    "x-up-timestamp":signed.timestamp,"x-up-signature":signed.signature},
   body:signed.body,signal:AbortSignal.timeout(9000)
  });
 }catch{
  return {decision:"retry",reason:"network_error"};
 }
 let parsed;
 try{parsed=await response.json()}catch{parsed={}}
 if(response.ok&&parsed?.ok===true&&parsed?.job_id)
  return {decision:"ack",jobId:text(parsed.job_id),idempotent:parsed.idempotent===true};
 const error=text(parsed?.error).toLowerCase();
 if(response.status===401||response.status===403)
  return {decision:"halt",reason:"auth_failed"};
 if(response.status===409||permanentErrors.has(error))
  return {decision:"halt",reason:error||"handoff_conflict"};
 if(response.status===429||response.status===408||response.status>=500)
  return {decision:"retry",reason:error||"temporarily_unavailable"};
 return {decision:"halt",reason:error||"invalid_response"};
}
