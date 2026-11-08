import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {createClient} from "npm:@supabase/supabase-js@2.56.1";
import {SignJWT,importPKCS8} from "npm:jose@5.9.6";

// Staging: only in standalone UP Supabase, after schema + FCM secret setup.
// Sends data-only messages recognized by UPMessagingService Android.
// No customer name, phone, address, GPS, PIN or database token goes to FCM.
const enc=new TextEncoder(),txt=(x:unknown)=>String(x??"").trim();
const UUID=/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;
function reply(status:number,body:Record<string,unknown>){
 return new Response(JSON.stringify(body),{status,headers:{
  "content-type":"application/json; charset=utf-8","cache-control":"no-store"
 }});
}
function safeEqual(a:string,b:string){
 const aa=enc.encode(a),bb=enc.encode(b);
 if(!aa.length||aa.length!==bb.length)return false;
 let diff=0;for(let i=0;i<aa.length;i++)diff|=aa[i]^bb[i];
 return diff===0;
}
type Credentials={project_id:string;client_email:string;private_key:string};
function credentials(raw:string,expectedProject:string):Credentials{
 let v:any;try{v=JSON.parse(raw)}catch{throw Error("invalid_fcm_credentials")}
 if(!v||v.project_id!==expectedProject||
   !/^.+@.+\.iam\.gserviceaccount\.com$/.test(txt(v.client_email))||
   !txt(v.private_key).includes("BEGIN PRIVATE KEY"))
  throw Error("invalid_fcm_credentials");
 return {project_id:v.project_id,client_email:v.client_email,private_key:v.private_key};
}
async function googleAccessToken(c:Credentials){
 const key=await importPKCS8(c.private_key,"RS256");
 const claim=await new SignJWT({scope:"https://www.googleapis.com/auth/firebase.messaging"})
  .setProtectedHeader({alg:"RS256",typ:"JWT"})
  .setIssuer(c.client_email).setAudience("https://oauth2.googleapis.com/token")
  .setIssuedAt().setExpirationTime("55m").sign(key);
 const res=await fetch("https://oauth2.googleapis.com/token",{
  method:"POST",headers:{"content-type":"application/x-www-form-urlencoded"},
  body:new URLSearchParams({grant_type:"urn:ietf:params:oauth:grant-type:jwt-bearer",assertion:claim}),
  signal:AbortSignal.timeout(10000)
 });
 if(!res.ok)throw Error("fcm_oauth_unavailable");
 const json=await res.json();
 if(!txt(json?.access_token))throw Error("fcm_oauth_invalid");
 return txt(json.access_token);
}
async function sendFirebase(accessToken:string,project:string,token:string,jobId:string,code:string,offerId:string){
 const data={title:"Nova corrida UP",body:"Uma entrega está disponível. Abra o UP Entregas.",
  missionType:"rides",rideId:"supabase:"+jobId,offerId,numeroPedido:code,codigoPedido:code,
  upSource:"independent"};
 let res:Response;
 try{
  res=await fetch("https://fcm.googleapis.com/v1/projects/"+encodeURIComponent(project)+"/messages:send",{
   method:"POST",headers:{
    "authorization":"Bearer "+accessToken,"content-type":"application/json"
   },
   body:JSON.stringify({message:{token,data,android:{priority:"HIGH"}}}),
   signal:AbortSignal.timeout(9000)
  });
 }catch{return {ok:false,temporary:true,reason:"network_error"}}
 if(res.ok)return {ok:true};
 const payload=await res.json().catch(()=>({}));
 const status=txt(payload?.error?.status).toUpperCase();
 if(status==="UNREGISTERED"||status==="INVALID_ARGUMENT"||res.status===404)
  return {ok:false,temporary:false,invalidToken:true,reason:"fcm_unregistered"};
 return {ok:false,temporary:res.status===429||res.status>=500||res.status===401,
  reason:"fcm_"+(status||res.status)};
}
Deno.serve(async(req:Request)=>{
 if(req.method!=="POST")return reply(405,{ok:false,error:"method_not_allowed"});
 if(req.headers.has("origin"))return reply(403,{ok:false,error:"server_to_server_only"});
 if(Deno.env.get("UP_PUSH_ENABLED")!=="true")
  return reply(503,{ok:false,error:"push_disabled"});
 const url=Deno.env.get("SUPABASE_URL")||"",
  role=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")||"",
  token=Deno.env.get("UP_PUSH_WORKER_TOKEN")||"",
  project=Deno.env.get("UP_FIREBASE_PROJECT_ID")||"",
  fcm=Deno.env.get("UP_FCM_SERVICE_ACCOUNT_JSON")||"";
 if(!url||!role||token.length<32||!project||!fcm)
  return reply(503,{ok:false,error:"push_not_configured"});
 const auth=txt(req.headers.get("authorization")).replace(/^Bearer\s+/i,"");
 if(!safeEqual(auth,token))return reply(401,{ok:false,error:"unauthorized"});
 let account:Credentials;try{account=credentials(fcm,project)}
 catch{return reply(503,{ok:false,error:"invalid_fcm_config"})}
 const db=createClient(url,role,{auth:{persistSession:false,autoRefreshToken:false}});
 const claim=await db.rpc("up_offer_push_claim",{p_limit:5});
 if(claim.error)return reply(503,{ok:false,error:"push_claim_failed"});
 const queue=Array.isArray(claim.data)?claim.data:[];
 if(!queue.length)return reply(200,{ok:true,claimed:0,sent:0,retry:0,failed:0});
 let access:string;
 try{access=await googleAccessToken(account)}catch{
  // A worker crash/timeout safely releases after 60 seconds; the lease
  // stays owned until expiry, preventing accidental delivery by another worker.
  return reply(503,{ok:false,error:"fcm_auth_temporarily_unavailable"});
 }
 const outcome={claimed:queue.length,sent:0,retry:0,failed:0};
 for(const item of queue){
  if(!UUID.test(txt(item.notification_id))||!UUID.test(txt(item.lease_id))||
     !UUID.test(txt(item.offer_id))||!UUID.test(txt(item.job_id))){
   outcome.failed++;continue;
  }
  let decision:"sent"|"retry"|"halt"="sent",reason="";
  try{
   const [job,devices]=await Promise.all([
    db.from("up_jobs").select("merchant_order_code").eq("id",item.job_id).maybeSingle(),
    db.from("up_driver_devices").select("id,token").eq("courier_id",item.courier_id)
      .eq("active",true).order("updated_at",{ascending:false}).limit(8)
   ]);
   if(job.error||devices.error)throw Error("device_lookup_failed");
   if(!job.data||!devices.data?.length){decision="halt";reason="no_active_device"}
   else{
    let temporary=false,delivered=0;
    for(const device of devices.data){
     const output=await sendFirebase(access,project,device.token,item.job_id,
      txt(job.data.merchant_order_code),item.offer_id);
     if(output.ok){delivered++;continue}
     if("invalidToken" in output&&output.invalidToken){
      await db.from("up_driver_devices").update({active:false})
       .eq("id",device.id).eq("courier_id",item.courier_id);
     }else if(output.temporary){temporary=true}
    }
    if(delivered===0){decision=temporary?"retry":"halt";reason=temporary?"push_failed_temporarily":"no_valid_device"}
    // When any device receives a notification, the offer is discoverable
    // by the Android 'offer' API even if other devices failed.
   }
  }catch{decision="retry";reason="push_processing_failed"}
  const finish=await db.rpc("up_offer_push_finish",{
   p_notification:item.notification_id,p_lease:item.lease_id,
   p_decision:decision,p_error_code:reason||null
  });
  if(finish.error||finish.data!==true){outcome.failed++;continue}
  if(decision==="sent")outcome.sent++;
  else if(decision==="retry")outcome.retry++;
  else outcome.failed++;
 }
 return reply(outcome.failed?503:200,{ok:outcome.failed===0,...outcome});
});
