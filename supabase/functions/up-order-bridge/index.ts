import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {createClient} from "npm:@supabase/supabase-js@2.56.1";

// Deploy ONLY on the future standalone UP Supabase project.
// This is private server-to-server traffic, never called directly from the app.
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const txt=(v:unknown)=>String(v??"").trim();
const json=(status:number,body:Record<string,unknown>)=>new Response(JSON.stringify(body),{
 status,headers:{"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store"}
});
const encoder=new TextEncoder();
function hex(bytes:Uint8Array){return [...bytes].map(x=>x.toString(16).padStart(2,"0")).join("");}
function fromHex(s:string){
 if(!/^[a-f0-9]{64}$/i.test(s))return null;
 return Uint8Array.from(s.match(/../g)!.map(x=>parseInt(x,16)));
}
async function sha256(body:string){return hex(new Uint8Array(await crypto.subtle.digest("SHA-256",encoder.encode(body))));}
async function verified(req:Request,body:string,secret:string){
 const ts=txt(req.headers.get("x-up-timestamp"));
 const sig=txt(req.headers.get("x-up-signature"));
 if(!/^\d{10}$/.test(ts))return false;
 const time=Number(ts)*1000;
 if(!Number.isFinite(time)||Math.abs(Date.now()-time)>300000)return false;
 const signature=fromHex(sig);
 if(!signature)return false;
 const key=await crypto.subtle.importKey("raw",encoder.encode(secret),{name:"HMAC",hash:"SHA-256"},false,["verify"]);
 return await crypto.subtle.verify("HMAC",key,signature,encoder.encode(ts+"\n"+body));
}
const maxSize=16384;
Deno.serve(async req=>{
 if(req.method!=="POST")return json(405,{ok:false,error:"method_not_allowed"});
 if(req.headers.has("origin"))return json(403,{ok:false,error:"server_to_server_only"});
 const secret=Deno.env.get("UP_BRIDGE_SECRET");
 const url=Deno.env.get("SUPABASE_URL"),service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
 if(!secret||secret.length<32||!url||!service)return json(503,{ok:false,error:"server_not_configured"});
 const size=Number(req.headers.get("content-length")||0);
 if(size>maxSize)return json(413,{ok:false,error:"payload_too_large"});
 let body:string;
 try{body=await req.text()}catch{return json(400,{ok:false,error:"invalid_request"});}
 if(encoder.encode(body).length>maxSize)return json(413,{ok:false,error:"payload_too_large"});
 if(!await verified(req,body,secret))return json(401,{ok:false,error:"invalid_signature_or_timestamp"});
 let input:any;
 try{input=JSON.parse(body)}catch{return json(400,{ok:false,error:"invalid_json"});}
 const action=txt(input?.action);
 if(!["create_job","get_status"].includes(action))return json(422,{ok:false,error:"invalid_action"});
 const orderId=txt(input?.order?.id||input?.merchant_order_id);
 if(!UUID.test(orderId))return json(422,{ok:false,error:"invalid_order_id"});
 const admin=createClient(url,service,{auth:{persistSession:false,autoRefreshToken:false}});
 try{
  if(action==="get_status"){
   const response=await admin.from("up_jobs").select("merchant_order_id,status,updated_at,assigned_at,delivered_at")
      .eq("merchant_order_id",orderId).maybeSingle();
   if(response.error)throw response.error;
   if(!response.data)return json(404,{ok:false,error:"job_not_found"});
   return json(200,{ok:true,job:response.data});
  }
  const order=input?.order||{};
  const eventId=txt(input.event_id);
  const code=txt(order.code);
  if(!UUID.test(eventId)||!/^\d{3,32}$/.test(code)||
   !["ENTREGA","DELIVERY"].includes(txt(order.fulfillment_type).toUpperCase())||
   txt(order.status).toUpperCase()!=="PRONTO")return json(422,{ok:false,error:"invalid_handoff"});
  const pickup=order.pickup&&typeof order.pickup==="object"&&!Array.isArray(order.pickup)?order.pickup:{};
  const dropoff=order.dropoff&&typeof order.dropoff==="object"&&!Array.isArray(order.dropoff)?order.dropoff:{};
  if(!Object.keys(dropoff).length)return json(422,{ok:false,error:"missing_delivery_address"});
  const collect=Number(order.collection_amount||0);
  if(!Number.isFinite(collect)||collect<0||collect>100000)return json(422,{ok:false,error:"invalid_collection_amount"});
  const name=txt(order.recipient_name).slice(0,150);
  const phone=txt(order.recipient_phone).slice(0,32);
  // Hash the entire signed message. A repeated event must be byte-for-byte identical.
  const fingerprint=await sha256(body);
  const result=await admin.rpc("up_receive_handoff",{
    p_merchant_order_id:orderId,
    p_code:code,p_pickup:pickup,p_dropoff:dropoff,
    p_name:name,p_phone:phone,p_collect:collect,
    p_event_id:eventId,p_hash:fingerprint
  });
  if(result.error){
   if(/handoff_conflict_same_order/i.test(result.error.message))return json(409,{ok:false,error:"order_already_received_with_different_payload"});
   throw result.error;
  }
  const row=Array.isArray(result.data)?result.data[0]:null;
  if(!row)return json(503,{ok:false,error:"handoff_not_committed"});
  return json(200,{ok:true,job_id:row.job_id,status:row.job_status,idempotent:!row.was_created});
 }catch(error){console.error("[up-order-bridge]",action,(error as Error)?.message||"error");
  return json(500,{ok:false,error:"up_bridge_failed"});}
});
