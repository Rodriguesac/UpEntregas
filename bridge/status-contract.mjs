// Protocolo de retorno UP -> Loja. Sem GPS, documentos, taxas ou credenciais.
// Versão 1, somente eventos de transição. Nunca altera carrinho ou pagamento.
export const STATUS_VERSION=1;
export const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const STATUS=["WAITING_DRIVER","OFFERED","ACCEPTED","TO_STORE","AT_STORE","TO_CUSTOMER","AT_CUSTOMER","DELIVERED","CANCELLED","FAILED"];
const RANK=new Map(STATUS.map((x,i)=>[x,i]));
const TERMINAL=new Set(["DELIVERED","CANCELLED","FAILED"]);
const seq=v=>Number.isSafeInteger(Number(v))&&Number(v)>0?Number(v):NaN;
const str=v=>String(v??"").trim();
const iso=v=>{const date=new Date(v);return Number.isNaN(date.valueOf())?"":date.toISOString()};
export function newStatusEvent({eventId,orderId,jobId,sequence,state,occurredAt}){
 const ids=[eventId,orderId,jobId].map(str);
 if(ids.some(x=>!UUID.test(x)))throw Error("invalid_event_identity");
 const s=str(state).toUpperCase(),n=seq(sequence);
 if(!RANK.has(s))throw Error("unknown_delivery_state");
 if(!Number.isFinite(n))throw Error("invalid_sequence");
 const time=iso(occurredAt);
 if(!time)throw Error("invalid_occurred_at");
 return {version:STATUS_VERSION,action:"status_changed",event_id:ids[0],order_id:ids[1],
  job_id:ids[2],sequence:n,state:s,occurred_at:time};
}
export function customerStatus(upState){
 const v=str(upState).toUpperCase();
 if(!RANK.has(v))throw Error("unknown_delivery_state");
 if(v==="DELIVERED")return "ENTREGUE";
 if(v==="TO_CUSTOMER"||v==="AT_CUSTOMER")return "EM_ENTREGA";
 if(v==="CANCELLED"||v==="FAILED")return null; // exige revisão da loja; não cancelar pedido
 return "AGUARDANDO_ENTREGADOR";
}
export function validateProgress(current,event){
 if(event?.version!==STATUS_VERSION||event?.action!=="status_changed")throw Error("invalid_protocol");
 const parsed=newStatusEvent({eventId:event.event_id,orderId:event.order_id,
  jobId:event.job_id,sequence:event.sequence,state:event.state,occurredAt:event.occurred_at});
 const own=current&&typeof current==="object"?current:{};
 if(own.mode!=="isolated")throw Error("not_an_isolated_up_order");
 if(own.orderId&&own.orderId!==parsed.order_id)throw Error("order_mismatch");
 if(own.jobId&&own.jobId!==parsed.job_id)throw Error("job_mismatch");
 const priorState=str(own.state).toUpperCase();
 const priorSeq=Number(own.sequence||0);
 if(!Number.isSafeInteger(priorSeq)||priorSeq<0)throw Error("invalid_saved_sequence");
 if(priorSeq===parsed.sequence&&str(own.eventId)===parsed.event_id&&priorState===parsed.state)
  return {decision:"duplicate",next:own,shopStatus:null};
 if(parsed.sequence<=priorSeq)return {decision:"stale",next:own,shopStatus:null};
 if(TERMINAL.has(priorState))throw Error("terminal_job_cannot_change");
 if(priorState&&RANK.has(priorState)&&!TERMINAL.has(parsed.state)&&RANK.get(parsed.state)<RANK.get(priorState))
  throw Error("out_of_order_delivery_state");
 if(priorSeq===0&&parsed.sequence!==1)throw Error("missing_initial_event");
 const next={mode:"isolated",orderId:parsed.order_id,jobId:parsed.job_id,
   eventId:parsed.event_id,sequence:parsed.sequence,state:parsed.state,
   occurredAt:parsed.occurred_at};
 return {decision:"apply",next,shopStatus:customerStatus(parsed.state)};
}
