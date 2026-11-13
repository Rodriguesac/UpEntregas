import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {createClient} from "npm:@supabase/supabase-js@2.56.1";
import {createRemoteJWKSet,jwtVerify} from "npm:jose@5.9.6";

// STAGING ONLY: for future independent UP Supabase. No store database calls.
// Browser never receives service_role, bridge secrets or unfiltered locations.
const project=Deno.env.get("UP_FIREBASE_PROJECT_ID")||"rodrigues-d6566";
const jwks=createRemoteJWKSet(new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"));
const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;
const txt=(v:unknown)=>String(v??"").trim();
const obj=(v:unknown):Record<string,any>=>v&&typeof v==="object"&&!Array.isArray(v)?v as Record<string,any>:{};
const money=(v:unknown)=>{const n=Number(v);return Number.isFinite(n)?n:NaN};
const allow=(role:string,roles:string[])=>roles.includes(role);
function cors(origin:string){
 return {"Access-Control-Allow-Origin":origin,"Access-Control-Allow-Methods":"POST, OPTIONS",
 "Access-Control-Allow-Headers":"authorization,content-type,apikey",
 "Access-Control-Max-Age":"3600","Vary":"Origin","Cache-Control":"no-store","Content-Type":"application/json; charset=utf-8"};
}
function reply(origin:string,data:Record<string,unknown>,status=200){
 return new Response(JSON.stringify(data),{status,headers:cors(origin)});
}
function fail(origin:string,status:number,error:string){
 return reply(origin,{ok:false,error},status);
}
function id(v:unknown){const x=txt(v);if(!uuid.test(x))throw Error("invalid_uuid");return x;}
function authorizeRole(role:string,allowed:string[]){
 if(!allow(role,allowed))throw Error("insufficient_permissions");
}
const privateRoles=["OWNER","ADMIN"];
const operatingRoles=["OWNER","ADMIN","DISPATCHER"];
const readingRoles=["OWNER","ADMIN","DISPATCHER","VIEWER"];
const maxPayload=12288;
Deno.serve(async req=>{
 const origin=txt(req.headers.get("origin"));
 const hosts=(Deno.env.get("UP_MANAGER_ALLOWED_ORIGINS")||"").split(",").map(x=>x.trim()).filter(Boolean);
 if(!hosts.includes("https://appassets.androidplatform.net"))hosts.push("https://appassets.androidplatform.net");
 // Site web oficial do UP Gestor; requer Firebase JWT e papel de gestor no banco independente.
 for(const allowed of ["https://upgestor-rac.web.app","https://upgestor-rac.firebaseapp.com"])
  if(!hosts.includes(allowed))hosts.push(allowed);
 if(!hosts.length||!origin||!hosts.includes(origin))return fail(origin,403,"origin_not_allowed");
 if(req.method==="OPTIONS")return new Response(null,{status:204,headers:cors(origin)});
 if(req.method!=="POST")return fail(origin,405,"method_not_allowed");
 
 const url=Deno.env.get("SUPABASE_URL"),key=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
 if(!url||!key)return fail(origin,503,"service_not_configured");
 const token=txt(req.headers.get("authorization")).replace(/^Bearer\s+/i,"");
 if(!token)return fail(origin,401,"firebase_token_required");
 let uid:string;
 try{
  const verified=await jwtVerify(token,jwks,{
   algorithms:["RS256"],audience:project,
   issuer:"https://securetoken.google.com/"+project,clockTolerance:5
  });
  uid=txt(verified.payload.sub);if(!uid)throw Error("invalid_uid");
 }catch{return fail(origin,401,"invalid_firebase_token")}
 const db=createClient(url,key,{auth:{persistSession:false,autoRefreshToken:false}});
 try{
  // Role must be read from a server-managed allow-list. NEVER trust the JWT
  // user_metadata, request role or any browser-supplied manager identifier.
  const actor=await db.from("up_manager_users").select("firebase_uid,display_name,role,active")
   .eq("firebase_uid",uid).maybeSingle();
  if(actor.error)throw actor.error;
  if(!actor.data?.active||!readingRoles.includes(actor.data.role))
   return fail(origin,403,"manager_not_authorized");
  const role=txt(actor.data.role);
  if(Deno.env.get("UP_INDEPENDENT_MANAGER_ENABLED")!=="true"){
   const gate=await db.from("up_settings").select("value").eq("id","runtime_manager").maybeSingle();
   if(gate.error)throw gate.error;
   if(gate.data?.value?.enabled!==true)return fail(origin,503,"independent_manager_disabled");
  }
  const raw=await req.text();
  if(new TextEncoder().encode(raw).length>maxPayload)return fail(origin,413,"payload_too_large");
  let body:any;try{body=JSON.parse(raw)}catch{return fail(origin,400,"invalid_json")}
  const action=txt(body?.action).toLowerCase();
  const own={id:uid,role,name:txt(actor.data.display_name)};
  if(action==="bootstrap")return reply(origin,{ok:true,user:own});
  // Sensitive readiness state is visible only AFTER verified Firebase manager JWT + server allowlist.
  if(action==="integration_health"){
   authorizeRole(role,privateRoles);
   const configured=(name:string,min=1)=>txt(Deno.env.get(name)).length>=min;
   const enabled=(name:string)=>Deno.env.get(name)==="true";
   const checks=[
    {id:"bridge",label:"Assinatura dos pedidos",ready:configured("UP_BRIDGE_SECRET",32)},
    {id:"tracking",label:"Assinatura de GPS",ready:configured("UP_TRACKING_SECRET",32)},
    {id:"status_endpoint",label:"Destino de retorno à loja",ready:txt(Deno.env.get("UP_STATUS_STORE_ENDPOINT"))==="https://fdqqwdplprzpqufpgdrm.supabase.co/functions/v1/up-status-receiver"},
    {id:"status_worker",label:"Autorização do retorno",ready:configured("UP_STATUS_WORKER_TOKEN",32)},
    {id:"manager_origin",label:"Domínio permitido do painel",ready:txt(Deno.env.get("UP_MANAGER_ALLOWED_ORIGINS")).split(",").some(s=>s.trim().startsWith("https://"))},
    {id:"firebase",label:"Firebase próprio do UP",ready:configured("UP_FIREBASE_PROJECT_ID",5)&&txt(Deno.env.get("UP_FIREBASE_PROJECT_ID"))!=="rodrigues-d6566"},
    {id:"customer_origin",label:"Domínio permitido do cliente",ready:txt(Deno.env.get("UP_CUSTOMER_ALLOWED_ORIGINS")).split(",").some(s=>s.trim()==="https://rodriguesacaiecia.web.app")},
    {id:"fcm",label:"Serviço de notificações",ready:configured("UP_FCM_SERVICE_ACCOUNT_JSON",50)&&configured("UP_PUSH_WORKER_TOKEN",32)}
   ];
   const flags=[
    {id:"manager",label:"UP Gestor",enabled:enabled("UP_INDEPENDENT_MANAGER_ENABLED")},
    {id:"driver",label:"Corridas no Android",enabled:enabled("UP_INDEPENDENT_DRIVER_ENABLED")},
    {id:"registration",label:"Cadastro independente",enabled:enabled("UP_INDEPENDENT_REGISTRATION_ENABLED")},
    {id:"relay",label:"Status para loja",enabled:enabled("UP_STATUS_ENABLED")},
    {id:"customer",label:"GPS para cliente",enabled:enabled("UP_CUSTOMER_TRACKING_ENABLED")},
    {id:"push",label:"Ofertas FCM",enabled:enabled("UP_PUSH_ENABLED")}
   ];
   return reply(origin,{ok:true,checks,flags,note:"Credenciais nunca são exibidas; configuração da loja deve ser validada separadamente."});
  }

  // Diagnóstico real e somente leitura UP -> loja, assinado com HMAC.
  // Não simula nem cria uma corrida, não muda nenhum pedido, não expõe chaves.
  if(action==="integration_probe"){
   authorizeRole(role,privateRoles);
   const expected="https://fdqqwdplprzpqufpgdrm.supabase.co/functions/v1/up-status-receiver";
   const destination=txt(Deno.env.get("UP_STATUS_STORE_ENDPOINT"));
   const secret=Deno.env.get("UP_BRIDGE_SECRET")||"";
   if(destination!==expected||secret.length<32)
    return reply(origin,{ok:true,probe:{
     connection_ok:false,callback_enabled:false,
     status:"missing_configuration",message:"Confira destino de retorno e chave compartilhada."}});
   const payload=JSON.stringify({
    version:1,action:"bridge_probe",nonce:crypto.randomUUID()
   });
   const timestamp=String(Math.floor(Date.now()/1000));
   const key=await crypto.subtle.importKey("raw",new TextEncoder().encode(secret),
    {name:"HMAC",hash:"SHA-256"},false,["sign"]);
   const signature=new Uint8Array(await crypto.subtle.sign("HMAC",key,
    new TextEncoder().encode(timestamp+"\n"+payload)));
   const hex=Array.from(signature,b=>b.toString(16).padStart(2,"0")).join("");
   let response:Response;
   try{
    response=await fetch(destination,{method:"POST",
     headers:{"content-type":"application/json","x-up-timestamp":timestamp,
      "x-up-signature":hex},
     body:payload,signal:AbortSignal.timeout(9000)});
   }catch{
    return reply(origin,{ok:true,probe:{
     connection_ok:false,callback_enabled:false,
     status:"network_error",message:"Não foi possível alcançar o banco da loja."}});
   }
   let result:any;try{result=await response.json()}catch{result={}}
   const connected=response.ok&&result?.ok===true&&result?.probe===true&&
    result?.signature_verified===true;
   return reply(origin,{ok:true,probe:{
    connection_ok:connected,
    callback_enabled:connected&&result?.callback_enabled===true,
    status:connected?"signed_connection_ok":response.status===401?
     "signature_mismatch":response.status===503?"store_not_ready":"store_rejected",
    message:connected?"Os dois bancos validaram a mesma chave de integração.":
     response.status===401?"As chaves dos dois projetos não coincidem.":
     "A loja não confirmou o teste seguro de comunicação."
   }});
  }

  if(action==="dashboard"){
   const [jobs,couriers,applications,payouts]=await Promise.all([
    db.from("up_jobs").select("id,status,assigned_courier_id").limit(1000),
    db.from("up_couriers").select("id,online,state").limit(500),
    db.from("up_courier_applications").select("id,state").limit(500),
    db.from("up_payouts").select("id,status,amount").limit(500)
   ]);
   for(const r of [jobs,couriers,applications,payouts])if(r.error)throw r.error;
   const all=jobs.data||[],staff=couriers.data||[],apps=applications.data||[],paid=payouts.data||[];
   const active=all.filter(x=>!["DELIVERED","CANCELLED","FAILED"].includes(x.status));
   return reply(origin,{ok:true,
    metrics:{active:active.length,awaiting:active.filter(x=>["WAITING_DRIVER","OFFERED"].includes(x.status)).length,
     online:staff.filter(x=>x.online&&x.state==="APPROVED").length,
     pending:apps.filter(x=>["PENDING","NEEDS_CORRECTION"].includes(x.state)).length,
     payout_pending:paid.filter(x=>x.status==="REQUESTED").length},
    sampled:all.length===1000});
  }
  // Authenticated UP dispatchers only; no keys or internal IDs from the browser are trusted.
  if(action==="merchant_locations"){
   authorizeRole(role,operatingRoles);
   const r=await db.from("up_merchants")
    .select("id,display_name,state,up_merchant_locations(id,label,city,state,is_default)")
    .eq("state","ACTIVE").order("display_name",{ascending:true}).limit(100);
   if(r.error)throw r.error;
   return reply(origin,{ok:true,merchants:(r.data||[]).map((m:any)=>({
    id:m.id,display_name:m.display_name,
    locations:(m.up_merchant_locations||[]).filter((loc:any)=>loc.state==="ACTIVE")
     .map((loc:any)=>({id:loc.id,label:loc.label,city:loc.city,is_default:loc.is_default}))
   }))});
  }
  if(action==="create_manual_job"){
   authorizeRole(role,operatingRoles);
   const merchantId=id(body.merchant_id),locationId=id(body.location_id);
   const requestId=id(body.request_id),code=txt(body.order_code);
   const src=obj(body.dropoff);
   const destination={
    street:txt(src.street).slice(0,180),number:txt(src.number).slice(0,20),
    neighborhood:txt(src.neighborhood).slice(0,120),city:txt(src.city).slice(0,120),
    state:txt(src.state).slice(0,2),complement:txt(src.complement).slice(0,150),
    reference:txt(src.reference).slice(0,150)
   };
   const name=txt(body.recipient_name).slice(0,150);
   const phone=txt(body.recipient_phone).replace(/\D/g,"").slice(0,15);
   if(!/^\d{3,32}$/.test(code)||name.length<2||phone.length<10||
      destination.street.length<3||destination.city.length<2)
    return fail(origin,422,"invalid_manual_request");
   const normalized={merchantId,locationId,requestId,code,destination,name,phone};
   const rawHash=await crypto.subtle.digest("SHA-256",
    new TextEncoder().encode(JSON.stringify(normalized)));
   const hash=Array.from(new Uint8Array(rawHash),x=>x.toString(16).padStart(2,"0")).join("");
   const r=await db.rpc("up_manager_manual_job",{
    p_merchant:merchantId,p_location:locationId,p_request_id:requestId,
    p_code:code,p_dropoff:destination,p_name:name,p_phone:phone,
    p_actor:uid,p_fingerprint:hash
   });
   if(r.error)throw r.error;
   return reply(origin,{ok:true,job:r.data?.[0]||null,
    billing_status:"NOT_QUOTED",note:"Nenhuma cobrança ou repasse foi gerado."});
  }
  if(action==="jobs_list"){
   const r=await db.from("up_jobs")
    .select("id,merchant_order_code,merchant_order_id,recipient_name,collection_amount,status,assigned_courier_id,received_at,updated_at,delivered_at")
    .order("updated_at",{ascending:false}).limit(250);
   if(r.error)throw r.error;
   return reply(origin,{ok:true,jobs:r.data||[]});
  }
  if(action==="job_detail"){
   const jobId=id(body.job_id);
   const [job,events,offers]=await Promise.all([
    db.from("up_jobs")
     .select("id,merchant_order_code,recipient_name,recipient_phone,pickup,dropoff,collection_amount,status,assigned_courier_id,received_at,updated_at,delivered_at,metadata")
     .eq("id",jobId).maybeSingle(),
    db.from("up_job_events").select("event_id,status,sequence,source,occurred_at,delivery_to_store_state")
     .eq("job_id",jobId).order("sequence",{ascending:true}).limit(100),
    db.from("up_dispatch_offers").select("id,courier_id,status,created_at,expires_at,responded_at")
     .eq("job_id",jobId).order("created_at",{ascending:false}).limit(30)
   ]);
   for(const r of [job,events,offers])if(r.error)throw r.error;
   if(!job.data)return fail(origin,404,"job_not_found");
   return reply(origin,{ok:true,job:job.data,events:events.data||[],offers:offers.data||[]});
  }
  if(action==="couriers_list"){
   const r=await db.from("up_couriers")
    .select("id,display_name,phone,state,online,tracking_enabled,last_location_at,vehicle,updated_at")
    .order("display_name",{ascending:true}).limit(500);
   if(r.error)throw r.error;
   return reply(origin,{ok:true,couriers:r.data||[]});
  }
  if(action==="applications_list"){
   const r=await db.from("up_courier_applications")
    .select("id,full_name,phone,email,state,vehicle,reviewed_at,reviewed_by,created_at")
    .order("created_at",{ascending:false}).limit(250);
   if(r.error)throw r.error;
   return reply(origin,{ok:true,applications:r.data||[]});
  }
  if(action==="application_detail"){
   authorizeRole(role,privateRoles);
   const r=await db.from("up_courier_applications")
    .select("id,full_name,phone,email,state,vehicle,documents,payment_details,reviewed_at,reviewed_by,created_at")
    .eq("id",id(body.application_id)).maybeSingle();
   if(r.error)throw r.error;
   if(!r.data)return fail(origin,404,"application_not_found");
   return reply(origin,{ok:true,application:r.data});
  }
  if(action==="review_application"){
   authorizeRole(role,privateRoles);
   const decision=txt(body.decision).toUpperCase(),reason=txt(body.reason).slice(0,500);
   if(!["APPROVED","NEEDS_CORRECTION","REJECTED"].includes(decision))
    return fail(origin,422,"invalid_review");
   if(decision!=="APPROVED"&&reason.length<5)return fail(origin,422,"reason_required");
   const r=await db.rpc("up_manager_review_application",{
    p_application:id(body.application_id),p_decision:decision,p_manager:uid,p_reason:reason
   });
   if(r.error)throw r.error;
   return reply(origin,{ok:true,result:r.data?.[0]||null});
  }
  if(action==="set_driver_state"){
   authorizeRole(role,privateRoles);
   const state=txt(body.state).toUpperCase();
   if(!["APPROVED","SUSPENDED"].includes(state))return fail(origin,422,"invalid_driver_state");
   const r=await db.rpc("up_manager_set_driver_state",{
    p_courier:id(body.courier_id),p_state:state,p_manager:uid
   });
   if(r.error)throw r.error;
   return reply(origin,{ok:true,updated:r.data===true});
  }
  if(action==="send_offer"){
   authorizeRole(role,operatingRoles);
   const mins=Number(body.minutes??3);
   if(!Number.isInteger(mins)||mins<1||mins>10)return fail(origin,422,"invalid_expiration");
   const r=await db.rpc("up_manager_create_offer",{
    p_job:id(body.job_id),p_courier:id(body.courier_id),p_manager:uid,p_minutes:mins
   });
   if(r.error)throw r.error;
   return reply(origin,{ok:true,offer:r.data?.[0]||null});
  }
  if(action==="finance"){
   authorizeRole(role,privateRoles);
   const [couriers,ledger,payouts]=await Promise.all([
    db.from("up_couriers").select("id,display_name").limit(500),
    db.from("up_wallet_transactions").select("courier_id,amount").limit(3000),
    db.from("up_payouts").select("id,courier_id,amount,status,requested_at,paid_at,payment_reference")
     .order("requested_at",{ascending:false}).limit(200)
   ]);
   for(const r of [couriers,ledger,payouts])if(r.error)throw r.error;
   const balances=(couriers.data||[]).map(c=>{
    const total=(ledger.data||[]).filter(x=>x.courier_id===c.id)
      .reduce((s,x)=>s+Number(x.amount||0),0);
    const reserved=(payouts.data||[]).filter(x=>x.courier_id===c.id&&
      ["REQUESTED","APPROVED","PAID"].includes(x.status))
      .reduce((s,x)=>s+Number(x.amount||0),0);
    return {courier_id:c.id,name:c.display_name,earned:total,available:total-reserved};
   });
   return reply(origin,{ok:true,balances,payouts:payouts.data||[],sampled:
    (ledger.data||[]).length===3000||(payouts.data||[]).length===200});
  }
  if(action==="payout_request"){
   authorizeRole(role,privateRoles);
   const amount=money(body.amount);
   if(!Number.isFinite(amount)||amount<=0||Math.abs(Math.round(amount*100)-amount*100)>0.000001)
    return fail(origin,422,"invalid_amount");
   const r=await db.rpc("up_manager_request_payout",{
    p_courier:id(body.courier_id),p_amount:amount,p_manager:uid
   });
   if(r.error)throw r.error;
   return reply(origin,{ok:true,payout_id:r.data});
  }
  if(action==="payout_confirm"){
   authorizeRole(role,privateRoles);
   const reference=txt(body.reference).slice(0,180);
   if(reference.length<5)return fail(origin,422,"reference_required");
   const r=await db.rpc("up_manager_confirm_payout",{
    p_payout:id(body.payout_id),p_manager:uid,p_reference:reference
   });
   if(r.error)throw r.error;
   return reply(origin,{ok:true,recorded:r.data===true});
  }
  if(action==="wallet"){
   authorizeRole(role,privateRoles);
   const r=await db.from("up_wallet_transactions").select("id,kind,amount,description,created_at,job_id")
    .eq("courier_id",id(body.courier_id)).order("created_at",{ascending:false}).limit(150);
   if(r.error)throw r.error;
   return reply(origin,{ok:true,transactions:r.data||[]});
  }
  if(action==="occurrences"){
   const r=await db.from("up_audit_log")
    .select("id,actor,action,entity_type,entity_id,metadata,created_at")
    .eq("action","driver_occurrence").order("created_at",{ascending:false}).limit(150);
   if(r.error)throw r.error;
   return reply(origin,{ok:true,occurrences:r.data||[]});
  }
  if(action==="audit"){
   authorizeRole(role,["OWNER"]);
   const r=await db.from("up_audit_log")
    .select("id,actor,action,entity_type,entity_id,metadata,created_at")
    .order("created_at",{ascending:false}).limit(150);
   if(r.error)throw r.error;
   return reply(origin,{ok:true,entries:r.data||[]});
  }
  return fail(origin,400,"unknown_action");
 }catch(error){
  const message=txt((error as any)?.message);
  const safe=[
   "invalid_uuid","insufficient_permissions","application_not_found",
   "invalid_review","incomplete_application","approved_courier_missing",
   "suspend_existing_driver_separately","courier_not_found","driver_has_active_job",
   "job_unavailable","job_already_offered","courier_not_available","courier_busy",
   "invalid_manual_request","invalid_delivery_address","manual_request_conflict",
   "merchant_not_active","pickup_not_active",
   "insufficient_wallet_balance","invalid_payout","payout_not_found",
   "payout_already_paid_different_reference","payout_cannot_be_paid"
  ];
  if(safe.includes(message))return fail(origin,message==="insufficient_permissions"?403:409,message);
  console.error("[UP manager independent]",message.slice(0,150));
  return fail(origin,500,"manager_operation_failed");
 }
});
