import test from "node:test";
import assert from "node:assert/strict";
import {upAtomicDispatch,AtomicDispatchError} from "./up-atomic-dispatch.mjs";
const orderId="bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1",courierId="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1";

test("Gadministra, Rodrigues Gestor e UP usam a mesma assinatura RPC",async()=>{
 let called;
 const admin={rpc:async(name,args)=>{called={name,args};return {data:{ok:true,order_id:orderId},error:null}}};
 const result=await upAtomicDispatch(admin,{
  action:"offer",orderId,courierId,source:"GADM_UP_DISPATCH",actorId:"owner"
 });
 assert.equal(result.ok,true);
 assert.deepEqual(called,{name:"up_atomic_dispatch",args:{
  p_action:"offer",p_order_id:orderId,p_courier_id:courierId,
  p_firebase_uid:null,p_actor_id:"owner",p_source:"GADM_UP_DISPATCH",p_cancel_reason:null
 }});
});

test("conflitos são reportados e não tentam repetir UPDATE fora da transação",async()=>{
 const admin={rpc:async()=>({data:null,error:{message:"courier_busy",code:"P0001"}})};
 await assert.rejects(upAtomicDispatch(admin,{action:"accept",orderId,courierId}),error=>{
  assert.equal(error instanceof AtomicDispatchError,true);
  assert.equal(error.code,"courier_busy");
  assert.equal(error.status,409);
  return true;
 });
});

test("sem RPC de homologação, falha fechada com 503 em vez de despachar inseguro",async()=>{
 const admin={rpc:async()=>({data:null,error:{message:"Could not find the function public.up_atomic_dispatch in the schema cache",code:"PGRST202"}})};
 await assert.rejects(upAtomicDispatch(admin,{action:"offer",orderId,courierId}),error=>{
  assert.equal(error.code,"rpc_unavailable");
  assert.equal(error.status,503);
  assert.doesNotMatch(error.message,/service_role|Bearer|senha/i);
  return true;
 });
});

test("resposta sem confirmação explícita também falha fechada",async()=>{
 const admin={rpc:async()=>({data:{ok:false},error:null})};
 await assert.rejects(upAtomicDispatch(admin,{action:"offer",orderId,courierId}),
  e=>e instanceof AtomicDispatchError&&e.status===503);
});

test("cancelamento envia motivo ao mesmo procedimento atômico",async()=>{
 let args;
 const admin={rpc:async(_,v)=>{args=v;return {data:{ok:true,status:"CANCELADO"},error:null}}};
 const response=await upAtomicDispatch(admin,{action:"cancel",orderId,courierId,
  source:"GADM_UP_DISPATCH",actorId:"gestor",reason:"Pedido cancelado pela loja"});
 assert.equal(response.status,"CANCELADO");
 assert.equal(args.p_cancel_reason,"Pedido cancelado pela loja");
 assert.equal(args.p_action,"cancel");
});
