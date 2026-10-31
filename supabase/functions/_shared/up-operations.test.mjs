import test from "node:test";
import assert from "node:assert/strict";
import {UP,isActiveMission,nextStage,canPickup,canFinish,canRecordLocation,canExposeLocationToCustomer,canOfferToCourier} from "./up-operations.mjs";
const order=(status="ACEITO",up_state=UP.TO_STORE,pickup=false)=>({id:"1",status,tracking_enabled:true,
  raw_payload:{up_state,...(pickup?{up_pickup_at:"2026-10-30T18:00:00Z"}:{})}});
test("ciclo oficial: aceite, loja, retirada, cliente, conclusão",()=>{
  const o=order();
  assert.equal(isActiveMission(o),true);
  assert.equal(nextStage(o,"COLETANDO"),UP.AT_STORE);
  assert.equal(nextStage(o,"NO_CLIENTE"),null);
  o.raw_payload.up_state=UP.AT_STORE;
  assert.equal(canPickup(o),true);assert.equal(canFinish(o),false);
  o.raw_payload.up_state=UP.TO_CUSTOMER;
  assert.equal(nextStage(o,"NO_CLIENTE"),null,"retirada deve ter confirmação gravada");
  o.raw_payload.up_pickup_at="2026-10-30T18:00:00Z";
  assert.equal(nextStage(o,"NO_CLIENTE"),UP.AT_CUSTOMER);
  assert.equal(canExposeLocationToCustomer(o,true),true);
  o.raw_payload.up_state=UP.AT_CUSTOMER;
  assert.equal(canFinish(o),true);
  o.status="ENTREGUE";
  assert.equal(canFinish(o),false);
  assert.equal(canRecordLocation(o),false);
  assert.equal(canExposeLocationToCustomer(o,true),false);
});
test("status enviado pelo aplicativo não pode saltar retirada nem inventar etapa",()=>{
  const o=order();
  for(const s of ["EM_ENTREGA","NO_CLIENTE","SAIU_PARA_ENTREGA","ENTREGUE","NÃO_EXISTE"]){
    assert.equal(nextStage(o,s),null,s);
  }
  assert.equal(canPickup(o),false);
  o.raw_payload.up_state=UP.AT_STORE;
  assert.equal(nextStage(o,"EM_ENTREGA"),null);
});
test("o GPS público exige permissão do gestor e retirada real",()=>{
  const o=order("DESPACHADO",UP.TO_CUSTOMER,true);
  assert.equal(canExposeLocationToCustomer(o,true),true);
  o.tracking_enabled=false;assert.equal(canExposeLocationToCustomer(o,true),false);
  o.tracking_enabled=true;assert.equal(canExposeLocationToCustomer(o,false),false);
  delete o.raw_payload.up_pickup_at;assert.equal(canExposeLocationToCustomer(o,true),false);
  o.raw_payload.up_pickup_at="2026-10-30T18:00:00Z";
  o.status="CANCELADO";assert.equal(canExposeLocationToCustomer(o,true),false);
});
test("localização de viagem à loja fica privada, e entrega finalizada não aceita GPS",()=>{
  for(const step of [UP.TO_STORE,UP.AT_STORE]){
    const o=order("ACEITO",step,false);
    assert.equal(canRecordLocation(o),true);
    assert.equal(canExposeLocationToCustomer(o,true),false);
  }
  for(const status of ["CONCLUIDO","CANCELADO","ENTREGUE"]){
    const o=order(status,UP.TO_CUSTOMER,true);
    assert.equal(canRecordLocation(o),false);
  }
});

test("oferta direcionada só aparece ao motoboy escolhido",()=>{
 const o={id:"pedido1",courier_id:null,status:"AGUARDANDO_ENTREGADOR",
   fulfillment_type:"ENTREGA",raw_payload:{up_target_courier_id:"moto-a"}};
 assert.equal(canOfferToCourier(o,"moto-a","firebase-a"),true);
 assert.equal(canOfferToCourier(o,"moto-b","firebase-b"),false);
 o.raw_payload.up_rejected_by=["firebase-a"];
 assert.equal(canOfferToCourier(o,"moto-a","firebase-a"),false);
 delete o.raw_payload.up_target_courier_id;
 assert.equal(canOfferToCourier(o,"moto-b","firebase-b"),true);
 o.status="ENTREGUE";
 assert.equal(canOfferToCourier(o,"moto-b","firebase-b"),false);
});
