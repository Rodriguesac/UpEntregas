import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHmac} from 'node:crypto';

const sql=readFileSync('supabase/up-independent-schema.sql','utf8');
const edge=readFileSync('supabase/functions/up-order-bridge/index.ts','utf8');
const tables=[
 'up_couriers','up_courier_applications','up_jobs','up_job_events',
 'up_tracking_points','up_dispatch_offers','up_wallet_transactions',
 'up_payouts','up_settings','up_audit_log'
];
for(const table of tables){
 assert.ok(sql.includes('create table if not exists public.'+table+' ('),'Falta a tabela '+table);
 assert.ok(sql.includes('alter table public.'+table+' enable row level security;'),'RLS obrigatório em '+table);
}
assert.ok(sql.includes('revoke all on public.up_couriers'),'Tabelas privadas devem revogar acesso comum');
assert.ok(sql.includes('from anon,authenticated;'),'Revogar usuários públicos e autenticados');
assert.ok(sql.includes('language plpgsql security invoker'),'A função não deve ser SECURITY DEFINER');
assert.ok(sql.includes('grant execute on function public.up_receive_handoff')&&sql.includes('to service_role;'),'A função de gravação deve ser exclusiva do service_role');
assert.ok(sql.includes('on conflict (merchant_order_id) do nothing'),'Handoff precisa impedir corrida duplicada');
assert.ok(sql.includes('handoff_conflict_same_order'),'O mesmo pedido com conteúdo diferente deve falhar');
assert.ok(!/references\s+public\.orders\b/i.test(sql),'UP não pode depender de FK do banco da loja');

for(const fragment of [
 'crypto.subtle.verify("HMAC"', 'req.headers.has("origin")',
 'Math.abs(Date.now()-time)>300000',
 'UP_BRIDGE_SECRET',
 'up_receive_handoff',
 'payload_too_large',
 'missing_delivery_address',
 'invalid_collection_amount',
 'event_id',
 'idempotent:!row.was_created'
])assert.ok(edge.includes(fragment),'Ponto de segurança ausente: '+fragment);
assert.ok(!edge.includes('console.log(body)'), 'Não registrar dados pessoais');
assert.ok(!edge.includes('AGUARDANDO_ENTREGADOR'), 'UP usa seu próprio estado, não a tabela de status da loja');

const body=JSON.stringify({action:'create_job',event_id:'00000000-0000-4000-8000-000000000001',order:{id:'00000000-0000-4000-8000-000000000002',code:'123456',fulfillment_type:'ENTREGA',status:'PRONTO',dropoff:{street:'Rua de teste'}}});
const ts='1793779200',secret='x'.repeat(40);
const sign=msg=>createHmac('sha256',secret).update(msg).digest('hex');
assert.notEqual(sign(ts+'\n'+body),sign(ts+'\n'+body+'alterado'), 'Assinatura autentica integralidade do conteúdo');
assert.notEqual(sign(ts+'\n'+body),sign(String(Number(ts)+1)+'\n'+body), 'Timestamp faz parte da assinatura');
console.log('PASS UP independente: 10 tabelas RLS, idempotência, HMAC, separação e segurança');
