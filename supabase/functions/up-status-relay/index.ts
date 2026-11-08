import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {createClient} from "npm:@supabase/supabase-js@2.56.1";

// STAGING ONLY. Deploy in the independent UP Supabase after cutover.
// Never expose HMAC secrets, service-role credentials or GPS to mobile clients.
const enc=new TextEncoder();
const txt=(v:unknown)=>String(v??"").trim();
const UUID=/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;
function reply(code:number,data:Record<string,unknown>){
 return new Response(JSON.stringify(data),{
  status:code,headers:{"content-type":"application/json; charset=utf-8","cache-control":"no-store"}
 });
}
function equalToken(left:string,right:string){
 const a=enc.encode(left),b=enc.encode(right);
 if(!a.length||a.length!==b.length)return false;
 let mismatch=0;
 for(let i=0;i<a.length;i++)mismatch|=a[i]^b[i];
 return mismatch===0;
}
function validatedShopEndpoint(endpoint:string,upUrl:string){
 let dest:URL,up:URL;
 try{dest=new URL(endpoint);up=new URL(upUrl)}catch{throw Error("invalid_store_endpoint")}
 if(dest.protocol!=="https:"||
  dest.hostname!=="fdqqwdplprzpqufpgdrm.supabase.co"||
  up.hostname!=="gkwmqwtktedarodvqqtn.supabase.co"||
  dest.hostname===up.hostname||dest.pathname!=="/functions/v1/up-status-receiver"||
  dest.username||dest.password||dest.search||dest.hash)
  throw Error("invalid_store_endpoint");
 return dest.href;
}
async function callback(row:any,dest:string,secret:string){
 const ids=[row?.event_id,row?.order_id,row?.job_id].map(txt);
 const sequence=Number(row?.sequence),status=txt(row?.status).toUpperCase();
 const when=new Date(row?.occurred_at);
 if(ids.some(x=>!UUID.test(x))||!Number.isSafeInteger(sequence)||sequence<1||
    !["WAITING_DRIVER","OFFERED","ACCEPTED","TO_STORE","AT_STORE",
      "TO_CUSTOMER","AT_CUSTOMER","DELIVERED","CANCELLED","FAILED"].includes(status)||
    Number.isNaN(when.getTime()))return {decision:"halt",reason:"invalid_event"} as const;
 const message={
   version:1,action:"status_changed",event_id:ids[0],order_id:ids[1],job_id:ids[2],
   sequence,state:status,occurred_at:when.toISOString()
 };
 const body=JSON.stringify(message);
 if(enc.encode(body).length>8192)return {decision:"halt",reason:"payload_too_large"} as const;
 const timestamp=String(Math.floor(Date.now()/1000));
 const key=await crypto.subtle.importKey("raw",enc.encode(secret),{name:"HMAC",hash:"SHA-256"},false,["sign"]);
 const signature=Array.from(new Uint8Array(await crypto.subtle.sign("HMAC",key,enc.encode(timestamp+"\n"+body))),
  x=>x.toString(16).padStart(2,"0")).join("");
 let res:Response;
 try{
  res=await fetch(dest,{method:"POST",headers:{
   "content-type":"application/json","x-up-timestamp":timestamp,"x-up-signature":signature
  },body,signal:AbortSignal.timeout(9000)});
 }catch{return {decision:"retry",reason:"network_error"} as const}
 let data:any;try{data=await res.json()}catch{data={}}
 if(res.ok&&data.ok===true&&
    (data.accepted===true||data.duplicate===true||data.stale===true))
  return {decision:"ack"} as const;
 const reason=txt(data?.error).slice(0,80);
 if(res.status===409&&["concurrent_update_retry","missing_first_event",
   "independent_bridge_not_enabled"].includes(reason))
  return {decision:"retry",reason} as const;
 if(res.status===408||res.status===429||res.status>=500)
  return {decision:"retry",reason:reason||"store_unavailable"} as const;
 return {decision:"halt",reason:reason||"store_rejected_event"} as const;
}
Deno.serve(async(req:Request)=>{
 if(req.method!=="POST")return reply(405,{ok:false,error:"method_not_allowed"});
 if(req.headers.has("origin"))return reply(403,{ok:false,error:"server_to_server_only"});
 if(Deno.env.get("UP_STATUS_ENABLED")!=="true")return reply(503,{ok:false,error:"status_relay_disabled"});
 const upUrl=Deno.env.get("SUPABASE_URL")||"",
  service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")||"",
  secret=Deno.env.get("UP_BRIDGE_SECRET")||"",
  workerToken=Deno.env.get("UP_STATUS_WORKER_TOKEN")||"";
 if(!upUrl||!service||secret.length<32||workerToken.length<32)
  return reply(503,{ok:false,error:"missing_server_configuration"});
 const token=txt(req.headers.get("authorization")).replace(/^Bearer\s+/i,"");
 if(!equalToken(token,workerToken))return reply(401,{ok:false,error:"unauthorized"});
 let destination:string;
 try{destination=validatedShopEndpoint(Deno.env.get("UP_STATUS_STORE_ENDPOINT")||"",upUrl)}
 catch{return reply(503,{ok:false,error:"store_endpoint_not_configured"})}
 const admin=createClient(upUrl,service,{auth:{persistSession:false,autoRefreshToken:false}});
 const claimed=await admin.rpc("up_status_callback_claim",{p_limit:5});
 if(claimed.error){console.error("[UP status relay] claim failed",claimed.error.code||"error");
  return reply(503,{ok:false,error:"claim_failed"})}
 const events=Array.isArray(claimed.data)?claimed.data:[];
 const results={claimed:events.length,delivered:0,retry:0,rejected:0,errors:0};
 for(const row of events){
  if(!UUID.test(txt(row?.event_id))||!UUID.test(txt(row?.lease_id))){results.errors++;continue}
  let outcome:{decision:string;reason?:string};
  try{outcome=await callback(row,destination,secret)}
  catch{outcome={decision:"retry",reason:"unexpected_callback_error"}}
  const final=await admin.rpc("up_status_callback_finish",{
   p_event_id:row.event_id,p_lease:row.lease_id,
   p_decision:outcome.decision,p_error_code:outcome.reason||null
  });
  if(final.error||final.data!==true)results.errors++;
  else if(outcome.decision==="ack")results.delivered++;
  else if(outcome.decision==="retry")results.retry++;
  else results.rejected++;
 }
 return reply(results.errors?503:200,{ok:results.errors===0,...results});
});
