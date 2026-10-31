/**
 * Contrato do UP Entregas operacional V3.
 * Executado no backend com a identidade do motoboy já autenticada.
 * Não altera registros; o chamador ainda deve condicionar gravações ao pedido atribuído.
 */
export const UP = Object.freeze({
  TO_STORE:"TO_STORE", AT_STORE:"AT_STORE", TO_CUSTOMER:"TO_CUSTOMER",
  AT_CUSTOMER:"AT_CUSTOMER", DELIVERED:"DELIVERED", CANCELED:"CANCELED"
});
const terminal=new Set(["ENTREGUE","CONCLUIDO","FINALIZADO","CANCELADO","CANCELADA","COMPLETED","DELIVERED","CANCELED","CANCELLED"]);
const states=new Set(Object.values(UP));
const normalize=value=>String(value??"").trim().normalize("NFD").replace(/[\u0300-\u036f]/g,"").toUpperCase().replace(/\s+/g,"_");
export function isActiveMission(order){
  if(!order || terminal.has(normalize(order.status)))return false;
  const state=normalize(order.raw_payload?.up_state);
  return states.has(state)&&state!==UP.DELIVERED&&state!==UP.CANCELED;
}
export function nextStage(order,status){
  if(!isActiveMission(order))return null;
  const state=normalize(order.raw_payload?.up_state), event=normalize(status);
  if(event==="COLETANDO" && [UP.TO_STORE,UP.AT_STORE].includes(state))return UP.AT_STORE;
  if(event==="NO_CLIENTE" && state===UP.TO_CUSTOMER && order.raw_payload?.up_pickup_at)return UP.AT_CUSTOMER;
  if(event==="EM_ENTREGA" && state===UP.TO_CUSTOMER && order.raw_payload?.up_pickup_at)return UP.TO_CUSTOMER;
  return null;
}
export function canPickup(order){
  return isActiveMission(order)&&normalize(order.raw_payload?.up_state)===UP.AT_STORE;
}
export function canFinish(order){
  return isActiveMission(order)&&normalize(order.raw_payload?.up_state)===UP.AT_CUSTOMER &&
    Boolean(order.raw_payload?.up_pickup_at);
}
/** Coordenadas recebidas do app: não transformar null/"" em 0 silenciosamente. */
export function parseGpsCoordinates(latValue,lngValue){
  if(latValue==null||lngValue==null||
    String(latValue).trim()===""||String(lngValue).trim()==="")return null;
  const lat=Number(latValue),lng=Number(lngValue);
  if(!Number.isFinite(lat)||!Number.isFinite(lng)||
     lat < -90||lat > 90||lng < -180||lng > 180||
     (lat===0&&lng===0))return null;
  return {lat,lng};
}
/** Intervalo só vale para duas amostras da MESMA entrega. */
export function shouldThrottleGpsSample(previousLocation,missionId,nowMs,minimumIntervalMs){
  if(!missionId||!previousLocation||
    String(previousLocation.order_id||"")!==String(missionId))return false;
  const previousAt=Date.parse(String(previousLocation.updated_at||""));
  return Number.isFinite(previousAt)&&previousAt>0&&
    Number.isFinite(nowMs)&&Number.isFinite(minimumIntervalMs)&&
    minimumIntervalMs>0&&nowMs>=previousAt&&
    nowMs-previousAt<minimumIntervalMs;
}
export function canRecordLocation(order){
  return isActiveMission(order)&&[UP.TO_STORE,UP.AT_STORE,UP.TO_CUSTOMER,UP.AT_CUSTOMER].includes(normalize(order.raw_payload?.up_state));
}
export function canExposeLocationToCustomer(order,requested){
  return requested===true && canRecordLocation(order) &&
    order.tracking_enabled===true &&
    Boolean(order.raw_payload?.up_pickup_at) &&
    [UP.TO_CUSTOMER,UP.AT_CUSTOMER].includes(normalize(order.raw_payload?.up_state));
}

/** A oferta específica não pode aparecer a outro motoboy.
 * Ofertas sem destino permanecem compatíveis com o fluxo antigo. */
export function canOfferToCourier(order,courierId,firebaseUid){
  if(!order||order.courier_id)return false;
  if(!["ENTREGA","DELIVERY"].includes(normalize(order.fulfillment_type)))return false;
  if(!["AGUARDANDO_ENTREGADOR","BUSCANDO_ENTREGADOR","PRONTO","DESPACHADO"].includes(normalize(order.status)))return false;
  const raw=order.raw_payload||{};
  const target=String(raw.up_target_courier_id||"");
  if(target&&target!==String(courierId||""))return false;
  const rejected=raw.up_rejected_by;
  if(Array.isArray(rejected)&&rejected.includes(firebaseUid))return false;
  return true;
}
