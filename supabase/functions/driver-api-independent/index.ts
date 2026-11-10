import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {createClient} from "npm:@supabase/supabase-js@2.56.1";
import {createRemoteJWKSet,jwtVerify} from "npm:jose@5.9.6";

// STAGING ONLY. Independent UP project; NEVER deploy over the production
// driver-api until its Firebase, app, manager and delivery tests are approved.
const firebaseProject=Deno.env.get("UP_FIREBASE_PROJECT_ID")||"rodrigues-d6566";
const jwks=createRemoteJWKSet(new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"));
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const txt=(x:unknown)=>String(x??"").trim();
const obj=(x:any):Record<string,any>=>x&&typeof x==="object"&&!Array.isArray(x)?x:{};
const num=(x:unknown)=>{const n=Number(x);return Number.isFinite(n)?n:NaN};
const isActive=(status:string)=>!["DELIVERED","CANCELLED","FAILED"].includes(status);
function reply(body:unknown,status=200){
 return new Response(JSON.stringify(body),{status,headers:{
  "content-type":"application/json; charset=utf-8","cache-control":"no-store"
 }});
}
function loc(address:any,field:string){
 const x=obj(address)[field];
 const y=Number(x??NaN);
 return Number.isFinite(y)?y:0;
}
function addressText(address:any){
 const a=obj(address);
 return [ [txt(a.street),txt(a.number)].filter(Boolean).join(", "),
  txt(a.neighborhood),txt(a.city)].filter(Boolean).join(" • ");
}
function document(job:any,offered:boolean){
 const origin=obj(job.pickup),dest=obj(job.dropoff);
 const state=offered?"OFFER_PENDING":txt(job.status);
 const merchantName=txt(obj(job.up_merchants).display_name)||txt(origin.name)||txt(origin.business_name)||"Estabelecimento";
 const data={
  source:"supabase",missionType:"SUPABASE_ORDER",
  pedidoId:job.merchant_order_id,orderId:job.id,
  codigoPedido:job.merchant_order_code,numeroPedido:job.merchant_order_code,
  clienteNome:txt(job.recipient_name),nomeCliente:txt(job.recipient_name),
  clienteTelefone:txt(job.recipient_phone),telefoneCliente:txt(job.recipient_phone),
  clienteEnderecoCompleto:addressText(dest),deliveryAddress:addressText(dest),
  destinoEndereco:addressText(dest),enderecoCliente:addressText(dest),
  enderecoLoja:addressText(origin),coletaEndereco:addressText(origin),
  coletaNome:merchantName,lojaNome:merchantName,
  clienteLat:loc(obj(dest.location),"lat"),clienteLng:loc(obj(dest.location),"lng"),
  destinoLat:loc(obj(dest.location),"lat"),destinoLng:loc(obj(dest.location),"lng"),
  lojaLat:loc(obj(origin.location),"lat"),lojaLng:loc(obj(origin.location),"lng"),
  valorReceberCliente:Number(job.collection_amount||0),
  totalPedido:Number(job.collection_amount||0),
  ofertaAtiva:offered,ofertaAceita:!offered,
  status:offered?"OFERTA":job.status,statusEntrega:job.status,
  statusCorrida:offered?"OFERTA":job.status,upState:state,
  upProtocolVersion:4,createdAt:job.received_at,updatedAt:job.updated_at,
  pickupConfirmed:["TO_CUSTOMER","AT_CUSTOMER","DELIVERED"].includes(state)
 };
 return {id:"supabase:"+job.id,data};
}
function sanitizedError(e:any){
 const msg=txt(e?.message);
 const errs=["job_unavailable","offer_expired_or_used","offer_not_found",
  "not_assigned_driver","invalid_driver_transition","incorrect_confirmation_code",
  "collection_not_confirmed","location_not_allowed","invalid_location","courier_not_approved"];
 return errs.find(x=>msg.includes(x))||"operation_failed";
}
const parseId=(v:unknown)=>txt(v).replace(/^supabase:/i,"");
Deno.serve(async(req:Request)=>{
 if(req.method!=="POST")return reply({ok:false,error:"method_not_allowed"},405);
 if(req.headers.has("origin"))return reply({ok:false,error:"native_app_only"},403);
 if(Deno.env.get("UP_INDEPENDENT_DRIVER_ENABLED")!=="true")
  return reply({ok:false,error:"independent_driver_disabled"},503);
 const url=Deno.env.get("SUPABASE_URL"),key=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
 if(!url||!key)return reply({ok:false,error:"service_not_configured"},503);
 const bearer=txt(req.headers.get("authorization")).replace(/^Bearer\s+/i,"");
 if(!bearer)return reply({ok:false,error:"firebase_token_required"},401);
 let uid:string;
 try{
  const verified=await jwtVerify(bearer,jwks,{
   algorithms:["RS256"],audience:firebaseProject,
   issuer:"https://securetoken.google.com/"+firebaseProject,clockTolerance:5
  });
  uid=txt(verified.payload.sub);
  if(!uid)throw Error("missing_uid");
 }catch{return reply({ok:false,error:"invalid_firebase_token"},401)}
 const raw=await req.text();
 if(new TextEncoder().encode(raw).length>8192)return reply({ok:false,error:"payload_too_large"},413);
 let body:any;try{body=JSON.parse(raw)}catch{return reply({ok:false,error:"invalid_json"},400)}
 const action=txt(body.action).toLowerCase();
 const admin=createClient(url,key,{auth:{persistSession:false,autoRefreshToken:false}});
 try{
  // Use only the UID in the cryptographically verified Firebase JWT.
  // Never accept courier IDs, roles or owner fields supplied by the client.
  const lookup=await admin.from("up_couriers").select("*").eq("firebase_uid",uid).maybeSingle();
  if(lookup.error)throw lookup.error;
  const courier=lookup.data;
  if(!courier||courier.state!=="APPROVED")return reply({ok:false,error:"driver_not_approved"},403);
  const missionId=parseId(body.mission_id||body.order_id);
  const now=new Date().toISOString();
  // Read-only mobile session: never trusts courier identifiers supplied by the caller.
  if(action==="session"){
   return reply({ok:true,online:courier.online===true,state:courier.state});
  }
  if(action==="equipment_status"){
   const eq=obj(obj(courier.metadata).equipment);
   const cash=Number(eq.cash_available);
   return reply({ok:true,equipment:{
    has_cash:eq.has_cash===true,
    cash_available:Number.isFinite(cash)?Math.max(0,cash):0,
    has_machine:eq.has_machine===true,
    machine_types:txt(eq.machine_types).slice(0,80)
   }});
  }
  if(action==="presence"){
   const upd=await admin.from("up_couriers").update({online:body.online===true,updated_at:now})
    .eq("id",courier.id);if(upd.error)throw upd.error;
   return reply({ok:true});
  }
  if(["telemetry","equipment"].includes(action)){
   const meta={...obj(courier.metadata)};
   if(action==="telemetry"){
    meta.telemetry={battery_level:Math.max(0,Math.min(100,num(body.battery_level)||0)),
      charging:body.charging===true,app_version:txt(body.app_version).slice(0,48),at:now};
   }else{
    meta.equipment={has_cash:body.has_cash===true,
      cash_available:Math.max(0,num(body.cash_available)||0),
      has_machine:body.has_machine===true,machine_types:txt(body.machine_types).slice(0,80),at:now};
   }
   const saved=await admin.from("up_couriers").update({metadata:meta,updated_at:now}).eq("id",courier.id);
   if(saved.error)throw saved.error;
   return reply({ok:true});
  }
  if(action==="token"){
   const token=txt(body.token);
   if(token.length<32||token.length>4096)return reply({ok:false,error:"invalid_device_token"},422);
   const saved=await admin.from("up_driver_devices").upsert({
    token,courier_id:courier.id,platform:"android",active:true,updated_at:now
   },{onConflict:"token"});if(saved.error)throw saved.error;
   return reply({ok:true});
  }
  if(action==="offer"){
   const current=await admin.from("up_jobs").select("*,up_merchants!up_jobs_merchant_id_fkey(display_name)").eq("assigned_courier_id",courier.id)
    .not("status","in",'("DELIVERED","CANCELLED","FAILED")')
    .order("updated_at",{ascending:false}).limit(1);
   if(current.error)throw current.error;
   if(current.data?.length)return reply({ok:true,document:document(current.data[0],false)});
   const offers=await admin.from("up_dispatch_offers").select("job_id,expires_at")
    .eq("courier_id",courier.id).eq("status","PENDING").gt("expires_at",now)
    .order("created_at",{ascending:true}).limit(20);
   if(offers.error)throw offers.error;
   if(!offers.data?.length)return reply({ok:true,document:null});
   const ids=offers.data.map((x:any)=>x.job_id);
   const jobs=await admin.from("up_jobs").select("*,up_merchants!up_jobs_merchant_id_fkey(display_name)").in("id",ids)
    .eq("status","OFFERED").is("assigned_courier_id",null);
   if(jobs.error)throw jobs.error;
   const job=offers.data.map((o:any)=>jobs.data?.find((j:any)=>j.id===o.job_id)).find(Boolean);
   return reply({ok:true,document:job?document(job,true):null});
  }
  if(action==="history"){
   const hist=await admin.from("up_jobs").select("*,up_merchants!up_jobs_merchant_id_fkey(display_name)").eq("assigned_courier_id",courier.id)
    .order("updated_at",{ascending:false}).limit(40);
   if(hist.error)throw hist.error;
   return reply({ok:true,documents:(hist.data||[]).map((x:any)=>document(x,false))});
  }
  // Financial records are scoped exclusively to the UID-verified courier.
  // Read only: no payouts or money movements can be created by the mobile app.
  if(action==="wallet"){
   const [entries,payouts]=await Promise.all([
    admin.from("up_wallet_transactions")
     .select("id,kind,amount,description,created_at")
     .eq("courier_id",courier.id).order("created_at",{ascending:false}).limit(100),
    admin.from("up_payouts")
     .select("id,amount,status,requested_at,paid_at")
     .eq("courier_id",courier.id).order("requested_at",{ascending:false}).limit(50)
   ]);
   if(entries.error)throw entries.error;
   if(payouts.error)throw payouts.error;
   return reply({ok:true,transactions:entries.data||[],payouts:payouts.data||[]});
  }
  if(!uuid.test(missionId))return reply({ok:false,error:"mission_required"},400);
  const getJob=async()=>{
   const r=await admin.from("up_jobs").select("*,up_merchants!up_jobs_merchant_id_fkey(display_name)").eq("id",missionId).maybeSingle();
   if(r.error)throw r.error;return r.data;
  };
  const ownJob=async()=>{
   const job=await getJob();
   if(!job)return null;
   if(job.assigned_courier_id!==courier.id)return null;
   return job;
  };
  if(action==="mission"){
   const job=await getJob();
   if(!job)return reply({ok:false,error:"mission_not_found"},404);
   if(job.assigned_courier_id===courier.id)
    return reply({ok:true,document:document(job,false)});
   const offered=await admin.from("up_dispatch_offers").select("id")
    .eq("job_id",missionId).eq("courier_id",courier.id)
    .eq("status","PENDING").gt("expires_at",now).limit(1);
   if(offered.error)throw offered.error;
   if(job.status!=="OFFERED"||job.assigned_courier_id||!offered.data?.length)
    return reply({ok:false,error:"mission_not_authorized"},403);
   return reply({ok:true,document:document(job,true)});
  }
  if(action==="accept"){
   // Somente novas corridas; nunca suspender uma entrega em andamento.
   const tele=obj(obj(courier.metadata).telemetry);
   const level=tele.battery_level;
   const at=Date.parse(txt(tele.at));
   if(typeof level==="number"&&Number.isInteger(level)&&level>=0&&level<15
       &&Number.isFinite(at)&&at<=Date.now()+60000&&Date.now()-at<=300000)
    return reply({ok:false,error:"battery_below_minimum",minimum_battery_percent:15},409);
   const offer=await admin.from("up_dispatch_offers").select("id")
    .eq("job_id",missionId).eq("courier_id",courier.id)
    .eq("status","PENDING").gt("expires_at",now).limit(1);
   if(offer.error)throw offer.error;
   if(!offer.data?.length)return reply({ok:false,error:"offer_not_found"},409);
   const accepted=await admin.rpc("up_driver_accept",{
    p_job:missionId,p_courier:courier.id,p_offer:offer.data[0].id
   });
   if(accepted.error)return reply({ok:false,error:sanitizedError(accepted.error)},409);
   return reply({ok:true});
  }
  if(action==="reject"||action==="expire"){
   const status=action==="reject"?"DECLINED":"EXPIRED";
   const upd=await admin.from("up_dispatch_offers").update({status,responded_at:now})
    .eq("job_id",missionId).eq("courier_id",courier.id).eq("status","PENDING");
   if(upd.error)throw upd.error;return reply({ok:true});
  }
  const job=await ownJob();
  if(!job)return reply({ok:false,error:"mission_not_authorized"},403);
  if(action==="location"){
   const lat=num(body.lat),lng=num(body.lng),accuracy=num(body.accuracy);
   if(!Number.isFinite(lat)||!Number.isFinite(lng))
    return reply({ok:false,error:"invalid_location"},422);
   const saved=await admin.rpc("up_driver_location",{
    p_job:job.id,p_courier:courier.id,p_lat:lat,p_lng:lng,
    p_accuracy:Number.isFinite(accuracy)?accuracy:null
   });
   if(saved.error)return reply({ok:false,error:sanitizedError(saved.error)},409);
   return reply({ok:true,history_saved:saved.data?.[0]?.saved===true});
  }
  if(action==="stage"||action==="pickup"||action==="finish"){
   const stage=txt(body.status).toUpperCase();
   const next=action==="pickup"?"TO_CUSTOMER":action==="finish"?"DELIVERED":
    stage==="COLETANDO"?"AT_STORE":stage==="NO_CLIENTE"?"AT_CUSTOMER":
    stage==="EM_ENTREGA"?"TO_CUSTOMER":"TO_STORE";
   const received=action==="finish"?num(body.received_amount):0;
   const result=await admin.rpc("up_driver_advance",{
    p_job:job.id,p_courier:courier.id,p_next:next,
    p_code:txt(body.code)||null,p_received:Number.isFinite(received)?received:-1
   });
   if(result.error)return reply({ok:false,error:sanitizedError(result.error)},409);
   return reply({ok:true});
  }
  if(action==="occurrence"){
   const reason=txt(body.reason).slice(0,500);
   if(!reason)return reply({ok:false,error:"reason_required"},422);
   const event=await admin.from("up_audit_log").insert({
    actor:uid,action:"driver_occurrence",entity_type:"job",entity_id:job.id,
    metadata:{reason,created_at:now}
   });
   if(event.error)throw event.error;return reply({ok:true});
  }
  return reply({ok:false,error:"unknown_action"},400);
 }catch(err){
  console.error("[UP independent driver API]",txt((err as Error)?.message).slice(0,150));
  return reply({ok:false,error:"driver_api_failed"},500);
 }
});
