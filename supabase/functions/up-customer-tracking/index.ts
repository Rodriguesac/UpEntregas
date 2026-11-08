import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {createClient} from "npm:@supabase/supabase-js@2.56.1";

// Read-only UP API for a customer who owns the receipt capability.
// UP itself never receives the store receipt_token or reads the store DB.
const enc=new TextEncoder(),dec=new TextDecoder();
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const txt=(x:unknown)=>String(x??"").trim();
const phases=new Set(["TO_CUSTOMER","AT_CUSTOMER"]);
function parseBase64url(raw:string){
 if(!/^[a-zA-Z0-9_-]+$/.test(raw)||raw.length>2048)throw Error("invalid_ticket");
 let s=raw.replace(/-/g,"+").replace(/_/g,"/");
 s+="=".repeat((4-s.length%4)%4);
 const binary=atob(s),array=new Uint8Array(binary.length);
 for(let i=0;i<binary.length;i++)array[i]=binary.charCodeAt(i);
 return array;
}
function reply(status:number,body:Record<string,unknown>,origin:string){
 const headers:Record<string,string>={
  "content-type":"application/json; charset=utf-8",
  "cache-control":"no-store","vary":"Origin"
 };
 if(origin)headers["access-control-allow-origin"]=origin;
 return new Response(JSON.stringify(body),{status,headers});
}
async function verify(raw:string,secret:string){
 const pieces=raw.split(".");
 if(pieces.length!==2)return null;
 try{
  const [payload,signature]=pieces;
  if(payload.length>1500)return null;
  const key=await crypto.subtle.importKey("raw",enc.encode(secret),
   {name:"HMAC",hash:"SHA-256"},false,["verify"]);
  if(!await crypto.subtle.verify("HMAC",key,parseBase64url(signature),enc.encode(payload)))
   return null;
  const value=JSON.parse(dec.decode(parseBase64url(payload)));
  const now=Math.floor(Date.now()/1000);
  if(value?.version!==1||value?.aud!=="up-customer-tracking"||
     !UUID.test(txt(value.order_id))||!UUID.test(txt(value.job_id))||
     !Number.isSafeInteger(value.exp)||!Number.isSafeInteger(value.iat)||
     value.iat>now+5||value.exp<=now||value.exp>now+90||
     value.exp-value.iat>90||!UUID.test(txt(value.jti)))return null;
  return value;
 }catch{return null}
}
Deno.serve(async(req:Request)=>{
 const origin=txt(req.headers.get("origin"));
 const origins=(Deno.env.get("UP_CUSTOMER_ALLOWED_ORIGINS")||"").split(",")
  .map(s=>s.trim()).filter(Boolean);
 if(!origin||!origins.includes(origin))
  return reply(403,{ok:false,error:"origin_not_allowed"},"");
 if(req.method==="OPTIONS")return new Response(null,{status:204,headers:{
  "access-control-allow-origin":origin,"access-control-allow-headers":"content-type,apikey",
  "access-control-allow-methods":"POST,OPTIONS","vary":"Origin"
 }});
 if(req.method!=="POST")return reply(405,{ok:false,error:"method_not_allowed"},origin);
 if(Deno.env.get("UP_CUSTOMER_TRACKING_ENABLED")!=="true")
  return reply(503,{ok:false,error:"tracking_disabled"},origin);
 const url=Deno.env.get("SUPABASE_URL")||"",
  service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")||"",
  secret=Deno.env.get("UP_TRACKING_SECRET")||"";
 if(!url||!service||secret.length<32)
  return reply(503,{ok:false,error:"tracking_not_configured"},origin);
 let raw:string;try{raw=await req.text()}catch{return reply(400,{ok:false,error:"bad_request"},origin)}
 if(enc.encode(raw).length>4000)return reply(413,{ok:false,error:"request_too_large"},origin);
 let input:any;try{input=JSON.parse(raw)}catch{return reply(400,{ok:false,error:"invalid_json"},origin)}
 const claims=await verify(txt(input?.ticket),secret);
 if(!claims)return reply(401,{ok:false,error:"invalid_or_expired_ticket"},origin);
 const db=createClient(url,service,{auth:{persistSession:false,autoRefreshToken:false}});
 try{
  const job=await db.from("up_jobs")
    .select("id,merchant_order_id,status,assigned_courier_id,updated_at")
    .eq("id",claims.job_id).eq("merchant_order_id",claims.order_id).maybeSingle();
  if(job.error)throw job.error;
  if(!job.data)return reply(404,{ok:false,error:"job_not_found"},origin);
  if(!phases.has(job.data.status)||!job.data.assigned_courier_id)
   return reply(200,{ok:true,tracking:null,status:job.data.status,reason:"not_on_customer_route"},origin);
  const recent=await db.from("up_tracking_points")
    .select("lat,lng,recorded_at,accuracy_m")
    .eq("job_id",claims.job_id).eq("courier_id",job.data.assigned_courier_id)
    .order("recorded_at",{ascending:false}).limit(1).maybeSingle();
  if(recent.error)throw recent.error;
  const point=recent.data,lat=Number(point?.lat),lng=Number(point?.lng),
   age=Date.now()-Date.parse(point?.recorded_at||"");
  if(!point||!Number.isFinite(lat)||!Number.isFinite(lng)||
     !Number.isFinite(age)||age< -10000||age>120000||
     lat===0&&lng===0||Math.abs(lat)>90||Math.abs(lng)>180){
   return reply(200,{ok:true,tracking:null,status:job.data.status,reason:"no_recent_visible_gps"},origin);
  }
  return reply(200,{ok:true,status:job.data.status,
   tracking:{lat,lng,recorded_at:point.recorded_at}},origin);
 }catch(error){console.error("[UP private tracking]",(error as Error)?.message?.slice(0,120)||"error");
  return reply(503,{ok:false,error:"tracking_temporarily_unavailable"},origin)}
});
