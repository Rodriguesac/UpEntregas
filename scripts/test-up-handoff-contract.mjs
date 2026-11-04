import assert from "node:assert/strict";
import {createHmac} from "node:crypto";
import {buildHandoff,deliveryAddress,signHandoff} from "../bridge/handoff-contract.mjs";

const eventId="e13d57d2-8817-47de-8717-112233445566";
const orderId="0a12bf8d-7ff3-4239-9236-556677889900";
const order={
 id:orderId,order_code:"711572",status:"PRONTO",fulfillment_type:"ENTREGA",
 payment_method:"INFINITEPAY",payment_status:"PAGO",
 delivery_address:{rua:"Rua de teste",numero:"163",bairro:"Estrela Dalva",complemento:"Casa",lat:-20.4,lng:-54.6}
};
const pickup={rua:"Loja teste",numero:"12",bairro:"Centro",lat:-20.5,lng:-54.5};
const opts={eventId,pickup,recipient:{name:"Pessoa de teste",phone:"(67) 99999-0000"},collectionAmount:0};
const a=buildHandoff(order,opts),b=buildHandoff(order,opts);
assert.deepEqual(a,b,"Payload deve ser determinístico em tentativas repetidas");
assert.equal(a.order.dropoff.number,"163");
assert.equal(a.order.dropoff.location.lat,-20.4);
assert.ok(!JSON.stringify(a).includes("CPF"),"Handoff não deve transportar documentos");
assert.equal(a.order.recipient_phone,"67999990000");
assert.equal(deliveryAddress({street:"Rua",number:"1"}).street,"Rua");
const bad=(change,pattern)=>assert.throws(()=>buildHandoff({...order,...change},opts),pattern);
bad({status:"EM_PREPARO"},/order_not_ready/);
bad({status:"CANCELADO"},/order_not_ready/);
bad({fulfillment_type:"RETIRADA"},/not_a_delivery/);
bad({courier_id:"some-id"},/already_assigned/);
bad({payment_status:"PENDENTE"},/online_payment_not_confirmed/);
bad({id:"invalid"},/invalid_uuid/);
bad({delivery_address:{}},/missing_delivery_address/);
assert.throws(()=>buildHandoff(order,{...opts,collectionAmount:10}),/paid_online_cannot_collect_again/);
assert.throws(()=>buildHandoff(order,{...opts,recipient:{}}),/missing_recipient/);
const secret="test-secret-stays-on-server-only-not-in-app";
const time=1793779200;
const signed=await signHandoff(a,secret,time);
const expected=createHmac("sha256",secret).update(signed.timestamp+"\n"+signed.body).digest("hex");
assert.equal(signed.signature,expected,"Assinatura WebCrypto compatível com HMAC no receptor");
assert.equal(signed.body,JSON.stringify(a),"Preservar o mesmo corpo assinado");
assert.equal((await signHandoff(b,secret,time)).signature,signed.signature,"Reenvio idêntico na mesma data");
assert.notEqual((await signHandoff({...a,event_id:"e13d57d2-8817-47de-8717-001122334455"},secret,time)).signature,signed.signature);
await assert.rejects(()=>signHandoff(a,"short",time),/bridge_secret_too_short/);
console.log("PASS contrato UP: validações, privacidade, idempotência, HMAC e pagamento");
