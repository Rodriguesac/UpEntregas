// Module for Rodrigues Açaí e Cia's customer PWA AFTER the independent
// bridge is enabled. Tickets are short-lived, only in memory, never persisted.
// Reuses one store-issued ticket for multiple UP GPS reads to save store quota.
const storeEndpoint="https://fdqqwdplprzpqufpgdrm.supabase.co/functions/v1/up-customer-ticket";
const upEndpoint="https://gkwmqwtktedarodvqqtn.supabase.co/functions/v1/up-customer-tracking";
const publishableUP="sb_publishable_-G8dzZ51cG9ZTdVviPAQdQ_9oSnwCyG";
const now=()=>Date.now();
const cache=new Map();
const maxCache=10;
function remember(receipt,data){
 if(cache.size>=maxCache)cache.delete(cache.keys().next().value);
 cache.set(receipt,{...data,until:now()+Math.min(60,Number(data.expires_in)||60)*1000});
}
function validateReceipt(x){
 const v=String(x??"").trim();
 if(!/^[a-zA-Z0-9_-]{20,300}$/.test(v))throw Error("invalid_receipt");
 return v;
}
async function post(url,body,key,signal){
 const headers={"content-type":"application/json"};
 if(key)headers.apikey=key;
 const r=await fetch(url,{method:"POST",headers,body:JSON.stringify(body),
  signal,cache:"no-store",credentials:"omit"});
 let data={};try{data=await r.json()}catch{}
 if(!r.ok||!data.ok){
  const e=new Error(String(data.error||"up_tracking_unavailable"));
  e.status=r.status;throw e;
 }
 return data;
}
export function clearIndependentTicket(receiptToken){
 if(receiptToken)cache.delete(String(receiptToken).trim());
 else cache.clear();
}
export async function independentDeliveryLocation(receiptToken,{
 storePublishableKey,signal
}={}){
 const receipt=validateReceipt(receiptToken);
 const key=String(storePublishableKey??"").trim();
 if(!key.startsWith("sb_publishable_"))throw Error("store_api_key_missing");
 let active=cache.get(receipt);
 if(!active||active.until<now()+6000){
  const ticket=await post(storeEndpoint,{receipt_token:receipt},key,signal);
  active={ticket:ticket.ticket,expires_in:ticket.expires_in};
  remember(receipt,active);
 }
 const result=await post(upEndpoint,{ticket:active.ticket},publishableUP,signal);
 const gps=result.tracking;
 if(!gps)return {tracking:null,status:result.status,reason:result.reason||"not_yet_available"};
 const lat=Number(gps.lat),lng=Number(gps.lng);
 if(!Number.isFinite(lat)||!Number.isFinite(lng)||
  Math.abs(lat)>90||Math.abs(lng)>180||lat===0&&lng===0)
  return {tracking:null,status:result.status,reason:"invalid_location"};
 return {tracking:{lat,lng,recorded_at:gps.recorded_at},status:result.status};
}
