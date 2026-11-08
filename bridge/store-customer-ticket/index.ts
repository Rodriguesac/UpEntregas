import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {createClient} from "npm:@supabase/supabase-js@2.56.1";

// STAGING ONLY — deploy to Rodrigues Açaí e Cia store Supabase only after
// independent UP is fully connected. The receipt token never reaches UP.
const enc=new TextEncoder();
const txt=(x:unknown)=>String(x??"").trim();
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
function b64url(data:Uint8Array){
 let binary="";for(const b of data)binary+=String.fromCharCode(b);
 return btoa(binary).replace(/\+/g,"-").replace(/\//g,"_").replace(/=/g,"");
}
function send(status:number,obj:Record<string,unknown>,origin:string){
 const headers:Record<string,string>={
 "content-type":"application/json; charset=utf-8","cache-control":"no-store",
 "vary":"Origin"
 };
 if(origin)headers["access-control-allow-origin"]=origin;
 return new Response(JSON.stringify(obj),{status,headers});
}
const publicWebsite=new Set([
 "https://rodriguesacaiecia.web.app",
 "https://rodriguesacaiecia.firebaseapp.com"
]);
Deno.serve(async(req:Request)=>{
 const origin=txt(req.headers.get("origin"));
 if(!origin||!publicWebsite.has(origin))return send(403,{ok:false,error:"origin_not_allowed"},"");
 if(req.method==="OPTIONS")return new Response(null,{status:204,headers:{
  "access-control-allow-origin":origin,"access-control-allow-headers":"apikey,authorization,content-type",
  "access-control-allow-methods":"POST,OPTIONS","vary":"Origin"
 }});
 if(req.method!=="POST")return send(405,{ok:false,error:"method_not_allowed"},origin);
 if(Deno.env.get("UP_CUSTOMER_TRACKING_ENABLED")!=="true")
  return send(503,{ok:false,error:"independent_tracking_disabled"},origin);
 const url=Deno.env.get("SUPABASE_URL")||"",
   service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")||"",
   secret=Deno.env.get("UP_TRACKING_SECRET")||"";
 if(!url||!service||secret.length<32)
  return send(503,{ok:false,error:"tracking_service_not_configured"},origin);
 let raw:string;
 try{raw=await req.text()}catch{return send(400,{ok:false,error:"invalid_request"},origin)}
 if(enc.encode(raw).length>3000)return send(413,{ok:false,error:"payload_too_large"},origin);
 let input:any;try{input=JSON.parse(raw)}catch{return send(400,{ok:false,error:"invalid_json"},origin)}
 const receipt=txt(input?.receipt_token);
 if(receipt.length<20||receipt.length>300)
  return send(400,{ok:false,error:"invalid_receipt"},origin);
 const db=createClient(url,service,{auth:{persistSession:false,autoRefreshToken:false}});
 try{
  const result=await db.from("orders")
   .select("id,status,receipt_token,fulfillment_type,tracking_enabled,raw_payload")
   .eq("receipt_token",receipt).maybeSingle();
  if(result.error)throw result.error;
  const order=result.data;
  if(!order)return send(404,{ok:false,error:"order_not_found"},origin);
  const bridge=order.raw_payload?.up_bridge;
  const jobId=txt(bridge?.jobId);
  if(bridge?.mode!=="isolated"||!UUID.test(jobId)||
     !["ENTREGA","DELIVERY"].includes(txt(order.fulfillment_type).toUpperCase())||
     order.tracking_enabled!==true||
     ["ENTREGUE","CANCELADO","CANCELADA","CANCELLED","FINALIZADO"].includes(txt(order.status).toUpperCase()))
    return send(409,{ok:false,error:"live_tracking_not_available"},origin);
  // UP will itself check that its job is in the customer-facing route phase.
  // No personally identifying information is embedded in this short ticket.
  const now=Math.floor(Date.now()/1000);
  const payload={version:1,aud:"up-customer-tracking",
   order_id:order.id,job_id:jobId,iat:now,exp:now+75,jti:crypto.randomUUID()};
  const data=b64url(enc.encode(JSON.stringify(payload)));
  const key=await crypto.subtle.importKey("raw",enc.encode(secret),
   {name:"HMAC",hash:"SHA-256"},false,["sign"]);
  const signature=new Uint8Array(await crypto.subtle.sign("HMAC",key,enc.encode(data)));
  return send(200,{ok:true,ticket:data+"."+b64url(signature),expires_in:75},origin);
 }catch(e){console.error("[UP customer ticket]",(e as Error)?.message?.slice(0,120)||"internal");
  return send(503,{ok:false,error:"tracking_temporarily_unavailable"},origin)}
});
