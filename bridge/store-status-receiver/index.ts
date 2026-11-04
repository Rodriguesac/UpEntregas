import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {createClient} from "npm:@supabase/supabase-js@2.56.1";

// STAGED ONLY. Deploy in the *store* Supabase after the separate UP project
// and two-way bridge are configured. Never use this as a public browser API.
const UUID=/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;
const VALID=["WAITING_DRIVER","OFFERED","ACCEPTED","TO_STORE","AT_STORE","TO_CUSTOMER","AT_CUSTOMER","DELIVERED","CANCELLED","FAILED"];
const RANK=new Map(VALID.map((status,index)=>[status,index]));
const TERMINAL=new Set(["DELIVERED","CANCELLED","FAILED"]);
const SHOP_TERMINAL=new Set(["CANCELADO","CANCELADA","CANCELLED","FINALIZADO","ENTREGUE","RETIRADO","CONCLUIDO","COMPLETED"]);
const enc=new TextEncoder();
const clean=(v:unknown)=>String(v??"").trim();
function reply(status:number,payload:Record<string,unknown>){
 return new Response(JSON.stringify(payload),{status,headers:{"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store"}});
}
function decodeHex(s:string){
 if(!/^[a-f0-9]{64}$/i.test(s))return null;
 return Uint8Array.from(s.match(/../g)!.map(part=>parseInt(part,16)));
}
async function authenticated(req:Request,body:string,secret:string){
 const ts=clean(req.headers.get("x-up-timestamp")),sig=decodeHex(clean(req.headers.get("x-up-signature")));
 if(!sig||!/^\d{10}$/.test(ts)||Math.abs(Date.now()-Number(ts)*1000)>300000)return false;
 const key=await crypto.subtle.importKey("raw",enc.encode(secret),{name:"HMAC",hash:"SHA-256"},false,["verify"]);
 return await crypto.subtle.verify("HMAC",key,sig,enc.encode(ts+"\n"+body));
}
function stage(status:string){
 if(status==="DELIVERED")return "ENTREGUE";
 if(["TO_CUSTOMER","AT_CUSTOMER"].includes(status))return "EM_ENTREGA";
 if(["FAILED","CANCELLED"].includes(status))return null; // preserve shop order for manual action
 return "AGUARDANDO_ENTREGADOR";
}
Deno.serve(async(req:Request)=>{
 if(req.method!=="POST")return reply(405,{ok:false,error:"method_not_allowed"});
 if(req.headers.has("origin"))return reply(403,{ok:false,error:"server_to_server_only"});
 const url=Deno.env.get("SUPABASE_URL"),service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
 const secret=Deno.env.get("UP_BRIDGE_SECRET");
 if(!url||!service||!secret||secret.length<32)return reply(503,{ok:false,error:"service_not_configured"});
 const size=Number(req.headers.get("content-length")||0);
 if(size>8192)return reply(413,{ok:false,error:"payload_too_large"});
 let body:string;try{body=await req.text()}catch{return reply(400,{ok:false,error:"invalid_request"})}
 if(enc.encode(body).length>8192)return reply(413,{ok:false,error:"payload_too_large"});
 if(!await authenticated(req,body,secret))return reply(401,{ok:false,error:"invalid_signature"});
 let event:any;try{event=JSON.parse(body)}catch{return reply(400,{ok:false,error:"invalid_json"})}
 const {version,action}=event||{};
 const orderId=clean(event?.order_id),jobId=clean(event?.job_id),eventId=clean(event?.event_id);
 const status=clean(event?.state).toUpperCase(),seq=Number(event?.sequence);
 const t=Date.parse(clean(event?.occurred_at));
 if(version!==1||action!=="status_changed"||![orderId,jobId,eventId].every(x=>UUID.test(x))||
  !RANK.has(status)||!Number.isSafeInteger(seq)||seq<1||!Number.isFinite(t)||t>Date.now()+300000)
  return reply(422,{ok:false,error:"invalid_delivery_event"});
 const admin=createClient(url,service,{auth:{persistSession:false,autoRefreshToken:false}});
 try{
  const result=await admin.from("orders")
   .select("id,status,fulfillment_type,raw_payload,updated_at")
   .eq("id",orderId).maybeSingle();
  if(result.error)throw result.error;
  const order=result.data;
  if(!order)return reply(404,{ok:false,error:"order_not_found"});
  if(!["ENTREGA","DELIVERY"].includes(clean(order.fulfillment_type).toUpperCase()))
   return reply(409,{ok:false,error:"not_delivery"});
  const raw=order.raw_payload&&typeof order.raw_payload==="object"&&!Array.isArray(order.raw_payload)?{...order.raw_payload}:{};
  const prev=raw.up_bridge&&typeof raw.up_bridge==="object"?raw.up_bridge:{};
  if(prev.mode!=="isolated")return reply(409,{ok:false,error:"independent_bridge_not_enabled"});
  if(prev.jobId&&prev.jobId!==jobId)return reply(409,{ok:false,error:"job_mismatch"});
  const lastSeq=Number(prev.sequence||0),old=clean(prev.state);
  // A delivery acknowledgment must be idempotent even when the shop is already marked ENTREGUE.
  if(lastSeq===seq&&prev.eventId===eventId&&old===status)
   return reply(200,{ok:true,duplicate:true,sequence:seq});
  if(seq<=lastSeq)return reply(200,{ok:true,stale:true,sequence:lastSeq});
  if(SHOP_TERMINAL.has(clean(order.status).toUpperCase()))return reply(409,{ok:false,error:"shop_order_finished"});
  if(TERMINAL.has(old))return reply(409,{ok:false,error:"job_already_terminal"});
  if(old&&!TERMINAL.has(status)&&(RANK.get(status)??-1)<(RANK.get(old)??-1))
   return reply(409,{ok:false,error:"out_of_order_state"});
  if(lastSeq===0&&seq!==1)return reply(409,{ok:false,error:"missing_first_event"});
  const newStatus=stage(status);
  raw.up_bridge={mode:"isolated",jobId,sequence:seq,eventId,state:status,occurredAt:new Date(t).toISOString()};
  raw.up_state=status;
  raw.up_attention_required=status==="FAILED"||status==="CANCELLED";
  const patch:any={raw_payload:raw};
  if(newStatus)patch.status=newStatus;
  if(newStatus==="ENTREGUE")patch.completed_at=new Date().toISOString();
  const changed=await admin.from("orders").update(patch)
   .eq("id",orderId).eq("updated_at",order.updated_at).eq("status",order.status)
   .select("id,status").maybeSingle();
  if(changed.error)throw changed.error;
  if(!changed.data)return reply(409,{ok:false,error:"concurrent_update_retry",retry:true});
  return reply(200,{ok:true,accepted:true,sequence:seq,status:changed.data.status,attention:raw.up_attention_required});
 }catch(err){
  console.error("[up-status-receiver]",(err as Error)?.message||"unknown_error");
  return reply(500,{ok:false,error:"callback_failed"});
 }
});
