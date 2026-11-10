import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {createClient} from "npm:@supabase/supabase-js@2.56.1";
import {createRemoteJWKSet,jwtVerify} from "npm:jose@5.9.6";

// STAGING ONLY: separate UP backend. Users may submit or edit their own
// application but can NEVER approve their own driver account.
const project=Deno.env.get("UP_FIREBASE_PROJECT_ID")||"rodrigues-d6566";
const jwks=createRemoteJWKSet(new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"));
const txt=(v:unknown)=>String(v??"").trim();
const obj=(v:any):Record<string,any>=>v&&typeof v==="object"&&!Array.isArray(v)?v:{};
const json=(body:unknown,status=200)=>new Response(JSON.stringify(body),{
 status,headers:{"content-type":"application/json; charset=utf-8","cache-control":"no-store"}
});
const asUrl=(value:unknown)=>{
 const v=txt(value);
 if(!v)return "";
 let u:URL;try{u=new URL(v)}catch{throw Error("invalid_document_url")}
 if(u.protocol!=="https:"||u.username||u.password||v.length>1200)
  throw Error("invalid_document_url");
 return u.href;
};
const stateToApp=(s:unknown)=>({PENDING:"pending",APPROVED:"approved",
 NEEDS_CORRECTION:"correction",REJECTED:"rejected"}[txt(s).toUpperCase()]||"missing");
Deno.serve(async req=>{
 if(req.method!=="POST")return json({ok:false,error:"method_not_allowed"},405);
 if(req.headers.has("origin"))return json({ok:false,error:"native_app_only"},403);
 const url=Deno.env.get("SUPABASE_URL"),service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
 if(!url||!service)return json({ok:false,error:"service_not_configured"},503);
 const token=txt(req.headers.get("authorization")).replace(/^Bearer\s+/i,"");
 if(!token)return json({ok:false,error:"firebase_token_required"},401);
 let uid:string,email:string;
 try{
  const verified=await jwtVerify(token,jwks,{
   algorithms:["RS256"],audience:project,
   issuer:"https://securetoken.google.com/"+project,clockTolerance:5
  });
  uid=txt(verified.payload.sub);email=txt(verified.payload.email).slice(0,150);
  if(!uid)throw Error("missing_firebase_uid");
 }catch{return json({ok:false,error:"invalid_firebase_token"},401)}
 let raw:string;try{raw=await req.text()}catch{return json({ok:false,error:"invalid_request"},400)}
 if(new TextEncoder().encode(raw).length>12288)
  return json({ok:false,error:"payload_too_large"},413);
 let body:any;try{body=JSON.parse(raw)}catch{return json({ok:false,error:"invalid_json"},400)}
 const action=txt(body.action).toLowerCase();
 const db=createClient(url,service,{auth:{persistSession:false,autoRefreshToken:false}});
 try{
  const existing=await db.from("up_courier_applications")
   .select("id,state,documents").eq("firebase_uid",uid).maybeSingle();
  if(existing.error)throw existing.error;
  if(action==="status_sync"){
   const courier=await db.from("up_couriers").select("state,online")
    .eq("firebase_uid",uid).maybeSingle();
   if(courier.error)throw courier.error;
   // A suspended/rejected courier must never appear approved because
   // an older application has an APPROVED state.
   const courierState=txt(courier.data?.state).toUpperCase();
   const status=courierState==="APPROVED"?"approved":
    courierState==="SUSPENDED"?"suspended":
    courierState==="REJECTED"?"rejected":stateToApp(existing.data?.state);
   return json({ok:true,status,online:courier.data?.online===true});
  }
  // Read-only status remains available while enrollment is disabled.
  // Every mutating action below still requires the private activation flag.
  if(Deno.env.get("UP_INDEPENDENT_REGISTRATION_ENABLED")!=="true"){
    const gate=await db.from("up_settings").select("value").eq("id","runtime_registration").maybeSingle();
    if(gate.error)throw gate.error;
    if(gate.data?.value?.enabled!==true)return json({ok:false,error:"independent_registration_disabled"},503);
   }
  if(action==="submit"){
   const profile=obj(body.profile);
   const fullName=txt(profile.nomeCompleto||profile.nome).slice(0,150);
   const phone=txt(profile.telefone||profile.whatsapp).replace(/[^+0-9]/g,"").slice(0,25);
   if(fullName.length<3||phone.length<10)return json({
    ok:false,error:"name_and_phone_required"
   },422);
   const previous=txt(existing.data?.state);
   if(previous==="APPROVED")
    return json({ok:false,error:"approved_profile_requires_manager_review"},409);
   const status="PENDING";
   const documents={
    ...obj(existing.data?.documents),
    cpf:txt(profile.cpf).replace(/[^0-9]/g,"").slice(0,11),
    birth_date:txt(profile.nascimento).slice(0,15)
   };
   const vehicle={
    type:txt(profile.tipoVeiculo||profile.veiculo).slice(0,65),
    plate:txt(profile.placa).slice(0,10),
    model:txt(profile.modelo).slice(0,80)
   };
   const saved=await db.from("up_courier_applications").upsert({
    firebase_uid:uid,full_name:fullName,phone,email,documents,vehicle,
    state:status
   },{onConflict:"firebase_uid"}).select("id,state").single();
   if(saved.error)throw saved.error;
   return json({ok:true,status:stateToApp(saved.data.state),id:saved.data.id});
  }
  if(action==="documents_update"){
   if(!existing.data)return json({ok:false,error:"application_not_found"},404);
   if(existing.data.state==="APPROVED")
    return json({ok:false,error:"approved_profile_requires_manager_review"},409);
   const updatedDocs={...obj(existing.data.documents),
    photo_url:asUrl(body.photo_url),document_url:asUrl(body.document_url)};
   const update=await db.from("up_courier_applications")
    .update({documents:updatedDocs,
      state:existing.data.state==="APPROVED"?"NEEDS_CORRECTION":existing.data.state
    }).eq("firebase_uid",uid);
   if(update.error)throw update.error;
   return json({ok:true});
  }
  return json({ok:false,error:"unknown_action"},400);
 }catch(e){
  const msg=txt((e as Error)?.message);
  if(msg==="invalid_document_url")return json({ok:false,error:msg},422);
  console.error("[UP independent registration]",msg.slice(0,130));
  return json({ok:false,error:"registration_failed"},500);
 }
});
