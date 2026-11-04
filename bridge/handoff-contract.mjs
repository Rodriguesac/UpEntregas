// Shared v1 wire contract for an eventual separate UP Supabase.
// No keys, no automatic production calls, no background polling.
export const HANDOFF_VERSION=1;
export const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const text=v=>String(v??"").trim();
const states=new Set(["PAGO","PAID","CONFIRMADO","APPROVED"]);
const online=/INFINITEPAY|ONLINE|PIX|CARTAO_ONLINE|CARTÃO_ONLINE/;
const nonEmpty=o=>o&&typeof o==="object"&&!Array.isArray(o);
const value=(o,keys)=>{for(const k of keys)if(text(o?.[k]))return text(o[k]);return "";};
function coords(o){
 const rawLat=o?.latitude??o?.lat,rawLng=o?.longitude??o?.lng;
 if(rawLat==null||rawLng==null||String(rawLat).trim()===""||String(rawLng).trim()==="")return null;
 const lat=Number(rawLat),lng=Number(rawLng);
 return Number.isFinite(lat)&&lat>=-90&&lat<=90&&Number.isFinite(lng)&&lng>=-180&&lng<=180?{lat,lng}:null;
}
export function deliveryAddress(source){
 const o=nonEmpty(source)?source:{};
 const street=value(o,["rua","logradouro","street","addressLine","address"]);
 const number=value(o,["numero","number"]);
 const neighborhood=value(o,["bairro","neighborhood"]);
 const complement=value(o,["complemento","complement"]);
 const reference=value(o,["referencia","reference"]);
 const location=coords(o);
 if(!street&&!location)throw Error("missing_delivery_address");
 return {
  street:street.slice(0,180),number:number.slice(0,20),
  neighborhood:neighborhood.slice(0,120),complement:complement.slice(0,150),
  reference:reference.slice(0,150),...(location?{location}:{})
 };
}
export function buildHandoff(order,{eventId,pickup,recipient,collectionAmount}={}){
 if(!UUID.test(text(order?.id))||!UUID.test(text(eventId)))throw Error("invalid_uuid");
 const code=text(order?.order_code);
 if(!/^[0-9]{3,32}$/.test(code))throw Error("invalid_order_code");
 if(text(order?.status).toUpperCase()!=="PRONTO")throw Error("order_not_ready");
 if(!["ENTREGA","DELIVERY"].includes(text(order?.fulfillment_type).toUpperCase()))throw Error("not_a_delivery");
 if(order?.courier_id)throw Error("already_assigned");
 const method=text(order?.payment_method).toUpperCase().replaceAll(" ","");
 const paid=text(order?.payment_status).toUpperCase();
 if(online.test(method)&&!states.has(paid))throw Error("online_payment_not_confirmed");
 if(!nonEmpty(pickup))throw Error("missing_pickup_address");
 const amount=Number(collectionAmount);
 if(!Number.isFinite(amount)||amount<0||amount>100000)throw Error("invalid_collection_amount");
 if(online.test(method)&&amount!==0)throw Error("paid_online_cannot_collect_again");
 const dest=deliveryAddress(order?.delivery_address);
 const origin=deliveryAddress(pickup);
 const recipientName=text(recipient?.name).slice(0,150);
 const recipientPhone=text(recipient?.phone).replace(/[^+0-9]/g,"").slice(0,32);
 if(!recipientName&&!recipientPhone)throw Error("missing_recipient");
 // Stable ordering and the same event ID must be used on every retry.
 return {
  action:"create_job",event_id:text(eventId),
  order:{
   id:text(order.id),code,
   fulfillment_type:"ENTREGA",status:"PRONTO",
   pickup:origin,dropoff:dest,
   recipient_name:recipientName,
   recipient_phone:recipientPhone,
   collection_amount:amount
  }
 };
}
export async function signHandoff(envelope,secret,epochSeconds){
 if(text(secret).length<32)throw Error("bridge_secret_too_short");
 const time=Number(epochSeconds);
 if(!Number.isSafeInteger(time)||String(time).length!==10)throw Error("invalid_timestamp");
 const body=JSON.stringify(envelope);
 if(new TextEncoder().encode(body).byteLength>16384)throw Error("payload_too_large");
 const key=await crypto.subtle.importKey("raw",new TextEncoder().encode(secret),{name:"HMAC",hash:"SHA-256"},false,["sign"]);
 const sig=await crypto.subtle.sign("HMAC",key,new TextEncoder().encode(time+"\n"+body));
 return {body,timestamp:String(time),signature:Array.from(new Uint8Array(sig),b=>b.toString(16).padStart(2,"0")).join("")};
}
