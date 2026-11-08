import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {createClient} from "npm:@supabase/supabase-js@2.56.1";

// STAGING ONLY: deploy in STORE Supabase ONLY after independent UP is provisioned.
// The store never gets driver credentials or GPS data through this worker.
const enc=new TextEncoder();
const txt=(v:unknown)=>String(v??"").trim();
const UUID=/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;
function reply(code:number,data:Record<string,unknown>){
 return new Response(JSON.stringify(data),{
  status:code,headers:{"content-type":"application/json; charset=utf-8","cache-control":"no-store"}
 });
}
function safeToken(a:string,b:string){
 const x=enc.encode(a),y=enc.encode(b);
 if(!x.length||x.length!==y.length)return false;
 let mismatch=0;
 for(let i=0;i<x.length;i++)mismatch |= x[i]^y[i];
 return mismatch===0;
}
function endpoint(s:string,shopUrl:string){
 let url:URL,shop:URL;
 try{url=new URL(s);shop=new URL(shopUrl)}catch{throw Error("invalid_endpoint")}
 const sameHost=url.hostname===shop.hostname;
 if(url.protocol!=="https:"||
  url.hostname!=="gkwmqwtktedarodvqqtn.supabase.co"||
  shop.hostname!=="fdqqwdplprzpqufpgdrm.supabase.co"||
  sameHost||
  !url.pathname.endsWith("/functions/v1/up-order-bridge")||
  url.search||url.hash||url.username||url.password)throw Error("invalid_endpoint");
 return url.href;
}
async function send(envelope:unknown,destination:string,secret:string){
 const obj=envelope&&typeof envelope==="object"?envelope as Record<string,unknown>:{};
 const order=obj.order&&typeof obj.order==="object"?obj.order as Record<string,unknown>:{};
 if(obj.action!=="create_job"||!UUID.test(txt(obj.event_id))||!UUID.test(txt(order.id)))
  return {decision:"halt",reason:"invalid_payload"} as const;
 const body=JSON.stringify(envelope);
 if(enc.encode(body).byteLength>16384)return {decision:"halt",reason:"payload_too_large"} as const;
 const timestamp=String(Math.floor(Date.now()/1000));
 const key=await crypto.subtle.importKey("raw",enc.encode(secret),{name:"HMAC",hash:"SHA-256"},false,["sign"]);
 const signature=Array.from(new Uint8Array(await crypto.subtle.sign("HMAC",key,enc.encode(timestamp+"\n"+body))),
  x=>x.toString(16).padStart(2,"0")).join("");
 let response:Response;
 try{
  response=await fetch(destination,{
   method:"POST",headers:{"content-type":"application/json",
    "x-up-timestamp":timestamp,"x-up-signature":signature},
   body,signal:AbortSignal.timeout(9000)
  });
 }catch{return {decision:"retry",reason:"network_error"} as const;}
 let data:any;try{data=await response.json()}catch{data={}}
 const reason=txt(data?.error).slice(0,80);
 if(response.ok&&data?.ok===true&&UUID.test(txt(data.job_id)))
  return {decision:"ack",jobId:txt(data.job_id)} as const;
 if(response.status===401||response.status===403)return {decision:"halt",reason:"up_auth_failed"} as const;
 if(response.status===409)return {decision:"halt",reason:reason||"up_conflict"} as const;
 if(response.status===429||response.status===408||response.status>=500)
  return {decision:"retry",reason:reason||"up_unavailable"} as const;
 return {decision:"halt",reason:reason||"invalid_up_reply"} as const;
}
Deno.serve(async(req:Request)=>{
 if(req.method!=="POST")return reply(405,{ok:false,error:"method_not_allowed"});
 if(req.headers.has("origin"))return reply(403,{ok:false,error:"server_to_server_only"});
 if(Deno.env.get("UP_BRIDGE_ENABLED")!=="true")return reply(503,{ok:false,error:"bridge_disabled"});
 const shopUrl=Deno.env.get("SUPABASE_URL")||"",
  service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")||"",
  secret=Deno.env.get("UP_BRIDGE_SECRET")||"",
  workerToken=Deno.env.get("UP_WORKER_TOKEN")||"";
 if(!shopUrl||!service||secret.length<32||workerToken.length<32)
  return reply(503,{ok:false,error:"missing_server_configuration"});
 const token=txt(req.headers.get("authorization")).replace(/^Bearer\s+/i,"");
 if(!safeToken(token,workerToken))return reply(401,{ok:false,error:"unauthorized"});
 let dest:string;
 try{dest=endpoint(Deno.env.get("UP_BRIDGE_ENDPOINT")||"",shopUrl)}
 catch{return reply(503,{ok:false,error:"independent_project_not_configured"})}
 const admin=createClient(shopUrl,service,{auth:{persistSession:false,autoRefreshToken:false}});
 const claim=await admin.rpc("up_store_outbox_claim",{p_limit:5});
 if(claim.error){
  console.error("[UP worker] database claim failed",claim.error.code||"error");
  return reply(503,{ok:false,error:"claim_failed"});
 }
 const rows=Array.isArray(claim.data)?claim.data:[];
 const summary={claimed:rows.length,ack:0,retry:0,halt:0,failed:0};
 for(const row of rows){
  if(!UUID.test(txt(row?.outbox_id))||!UUID.test(txt(row?.lease_id))){
   summary.failed++;continue;
  }
  let decision:{decision:string;reason?:string;jobId?:string};
  try{decision=await send(row.payload,dest,secret)}
  catch{decision={decision:"retry",reason:"dispatch_error"}}
  const done=await admin.rpc("up_store_outbox_finish",{
   p_id:row.outbox_id,p_lease:row.lease_id,p_decision:decision.decision,
   p_error_code:decision.reason||null,p_job_id:decision.jobId||null
  });
  if(done.error||done.data!==true){
   // A missing ACK after the UP accepted a job must be retried with the
   // unchanged outbox payload. UP's idempotent receiver prevents duplicates.
   summary.failed++;
  }else if(decision.decision==="ack")summary.ack++;
  else if(decision.decision==="retry")summary.retry++;
  else summary.halt++;
 }
 return reply(summary.failed?503:200,{ok:summary.failed===0,...summary});
});
