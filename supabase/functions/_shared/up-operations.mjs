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
export function canRecordLocation(order){
  return isActiveMission(order)&&[UP.TO_STORE,UP.AT_STORE,UP.TO_CUSTOMER,UP.AT_CUSTOMER].includes(normalize(order.raw_payload?.up_state));
}
export function canExposeLocationToCustomer(order,requested){
  return requested===true && canRecordLocation(order) &&
    order.tracking_enabled===true &&
    Boolean(order.raw_payload?.up_pickup_at) &&
    [UP.TO_CUSTOMER,UP.AT_CUSTOMER].includes(normalize(order.raw_payload?.up_state));
}
