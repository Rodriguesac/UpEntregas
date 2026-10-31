/**
 * Chamada da RPC transacional instalada SOMENTE após homologação isolada.
 * Banco decide oferta/aceite com lock do motoboy e do pedido; Edge Functions
 * fazem autenticação/autorização antes de usar a credencial service_role.
 */
const MESSAGES={
 invalid_dispatch_request:"Solicitação de entrega inválida.",
 courier_not_found:"Motoboy não encontrado.",
 order_not_found:"Pedido não encontrado.",
 order_not_delivery:"Este pedido não é para entrega.",
 mission_already_accepted:"A entrega já foi aceita.",
 offer_already_pending:"Esta corrida já foi oferecida a outro motoboy.",
 order_not_offerable:"Pedido ainda não está pronto para uma oferta.",
 payment_pending:"Pagamento não confirmado.",
 courier_unavailable:"Motoboy não está disponível.",
 courier_rejected:"Este motoboy já recusou a corrida.",
 courier_busy:"O motoboy já está atendendo outra corrida.",
 courier_has_pending_offer:"O motoboy já possui uma oferta pendente.",
 courier_identity_mismatch:"A conta do motoboy não corresponde à sessão atual.",
 mission_not_available:"Esta oferta não está mais disponível.",
 courier_offline:"O motoboy precisa ficar online."
};
const INVALID = new Set(["invalid_dispatch_request","courier_identity_mismatch"]);
const NOT_FOUND = new Set(["courier_not_found","order_not_found"]);

export class AtomicDispatchError extends Error {
 constructor(code,status) {
  super(MESSAGES[code]||"Não foi possível confirmar a operação. Atualize e tente novamente.");
  this.name="AtomicDispatchError";this.code=code;this.status=status;
 }
}
export async function upAtomicDispatch(admin,params){
 const args={
  p_action:params.action,
  p_order_id:params.orderId,
  p_courier_id:params.courierId,
  p_firebase_uid:params.firebaseUid||null,
  p_actor_id:params.actorId||null,
  p_source:params.source||"UP_ENTREGAS"
 };
 const result=await admin.rpc("up_atomic_dispatch",args);
 if(result.error){
   const raw=String(result.error.message||"").trim();
   const code=Object.keys(MESSAGES).find(k=>raw===k || raw.startsWith(k+":"))||"rpc_unavailable";
   const status=code==="rpc_unavailable"?503:INVALID.has(code)?403:NOT_FOUND.has(code)?404:409;
   throw new AtomicDispatchError(code,status);
 }
 if(!result.data?.ok)throw new AtomicDispatchError("rpc_unavailable",503);
 return result.data;
}
