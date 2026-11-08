#!/usr/bin/env node
// OWNER-APPROVED ONE-WAY migration: old store Supabase -> independent UP.
// Defaults to dry-run, never prints/export secrets or customer documents.
// Never moves orders, receipt tokens, GPS history or unrelated customers.
import {randomUUID} from 'node:crypto';
const SOURCE='https://fdqqwdplprzpqufpgdrm.supabase.co';
const TARGET='https://gkwmqwtktedarodvqqtn.supabase.co';
const oldKey=process.env.UP_OLD_SERVICE_ROLE||'';
const newKey=process.env.UP_NEW_SERVICE_ROLE||'';
const dryRun=process.env.UP_MIGRATION_DRY_RUN!=='false';
const approved=process.env.UP_MIGRATION_APPROVED==='true';
const noPII=(x)=>String(x??'').trim();
function verifyConfig(){
 if(!oldKey||!newKey||oldKey.length<30||newKey.length<30)
  throw Error('private_server_keys_required');
 if(!dryRun&&!approved)throw Error('migration_owner_approval_required');
 if(process.env.UP_TARGET_PROJECT_REF!=='gkwmqwtktedarodvqqtn')
  throw Error('destination_mismatch');
}
const validDate=(v)=>v&&Number.isFinite(Date.parse(v))?v:null;
const permitted=(v,values,label)=>{
 const result=noPII(v).toUpperCase();
 if(!values.includes(result))throw Error('invalid_legacy_'+label);
 return result;
};
async function api(host,key,path,{method='GET',payload}={}){
 const response=await fetch(host+'/rest/v1/'+path,{
  method,headers:{
   apikey:key,authorization:'Bearer '+key,
   'content-type':'application/json',
   prefer:'resolution=merge-duplicates,return=minimal'
  },
  ...(payload!==undefined?{body:JSON.stringify(payload)}:{})
 });
 if(!response.ok){
  // Never echo PostgREST response; it could contain sensitive customer data.
  throw Error('database_request_failed_'+response.status);
 }
 if(method==='GET')return await response.json();
 return null;
}
async function table(host,key,name){
 const result=[];
 for(let offset=0;offset<10000;offset+=500){
  const part=await api(host,key,
   encodeURIComponent(name)+'?select=*&limit=500&offset='+offset);
  if(!Array.isArray(part))throw Error('invalid_source_page');
  result.push(...part);
  if(part.length<500)break;
  if(result.length>=10000)throw Error('migration_limit_exceeded');
 }
 return result;
}
function applicationState(status){
 const state=noPII(status).toLowerCase();
 const map={approved:'APPROVED',pending:'PENDING',
  correction:'NEEDS_CORRECTION',rejected:'REJECTED',blocked:'REJECTED'};
 if(!map[state])throw Error('unexpected_registration_state');
 return map[state];
}
function courierState(row,app){
 const legacy=noPII(row.status).toLowerCase();
 if(['blocked','suspended','rejected'].includes(legacy))return 'SUSPENDED';
 if(app?.state==='APPROVED')return 'APPROVED';
 // Never auto-approve someone whose legacy application is missing.
 return 'PENDING';
}
function mapApplication(item){
 if(!item.id||!item.firebase_uid)throw Error('application_identity_missing');
 const documents=item.documents&&typeof item.documents==='object'?{...item.documents}:{};
 if(item.cpf)documents.cpf=noPII(item.cpf).replace(/[^\d]/g,'').slice(0,11);
 if(item.birth_date)documents.birth_date=noPII(item.birth_date).slice(0,16);
 return {
  id:item.id,firebase_uid:item.firebase_uid,full_name:noPII(item.full_name).slice(0,150),
  phone:noPII(item.phone).slice(0,28),email:noPII(item.email).slice(0,150),
  documents,vehicle:item.vehicle||{},
  payment_details:item.pix||{},state:applicationState(item.status),
  reviewed_at:validDate(item.reviewed_at),reviewed_by:noPII(item.reviewed_by)||null,
  created_at:validDate(item.submitted_at)||new Date().toISOString()
 };
}
function mapCourier(row,app){
 const uid=noPII(row.firebase_uid||row.firebase_id);
 if(!row.id||!uid)throw Error('courier_identity_missing');
 return {
  id:row.id,firebase_uid:uid,
  display_name:noPII(row.name||app?.full_name).slice(0,150)||'Entregador',
  phone:noPII(row.phone||app?.phone).slice(0,28),
  state:courierState(row,app),online:false,tracking_enabled:false,
  last_lat:null,last_lng:null,last_location_at:null,
  vehicle:app?.vehicle||{},metadata:{migration:'one_way',source:'up_legacy'},
  created_at:validDate(row.created_at)||new Date().toISOString()
 };
}
function financialRows(wallet,payouts,drivers){
 const known=new Set(drivers.map(x=>x.id));
 const txn=wallet.map(w=>{
  if(!known.has(w.courier_id))throw Error('wallet_courier_orphan');
  const kind=permitted(w.kind,['CREDIT','DEBIT','ADJUSTMENT'],'ledger_kind');
  const amount=Number(w.amount);
  if(!Number.isFinite(amount)||Math.abs(amount)>100000)throw Error('invalid_ledger_amount');
  return {
   id:w.id,courier_id:w.courier_id,job_id:null,kind,amount,
   description:noPII(w.description).slice(0,400),
   created_at:validDate(w.created_at)||new Date().toISOString()
  };
 });
 const pay=payouts.map(p=>{
  if(!known.has(p.courier_id))throw Error('payout_courier_orphan');
  const map={pending:'REQUESTED',requested:'REQUESTED',approved:'APPROVED',
    paid:'PAID',rejected:'REJECTED'};
  const state=map[noPII(p.status).toLowerCase()];
  if(!state)throw Error('invalid_legacy_payout_state');
  const amount=Number(p.amount);
  if(!Number.isFinite(amount)||amount<=0||amount>100000)throw Error('invalid_payout_amount');
  return {id:p.id,courier_id:p.courier_id,amount,status:state,
   payment_reference:noPII(p.metadata?.payment_reference||p.note).slice(0,180)||null,
   requested_at:validDate(p.requested_at)||new Date().toISOString(),
   paid_at:validDate(p.paid_at)};
 });
 return {transactions:txn,payouts:pay};
}
async function writeMany(name,rows){
 for(let i=0;i<rows.length;i+=100)
  await api(TARGET,newKey,encodeURIComponent(name)+'?on_conflict=id',{
   method:'POST',payload:rows.slice(i,i+100)});
}
async function main(){
 verifyConfig();
 const [origCouriers,origApplications,wallet,payouts]=await Promise.all([
  table(SOURCE,oldKey,'couriers'),table(SOURCE,oldKey,'courier_applications'),
  table(SOURCE,oldKey,'courier_wallet_transactions'),table(SOURCE,oldKey,'courier_payouts')
 ]);
 const applications=origApplications.map(mapApplication);
 const byUid=new Map(applications.map(a=>[a.firebase_uid,a]));
 const drivers=origCouriers.map(c=>mapCourier(c,byUid.get(c.firebase_uid||c.firebase_id)));
 const ids=new Set(drivers.map(x=>x.firebase_uid));
 if(ids.size!==drivers.length)throw Error('duplicate_courier_uid');
 const data=financialRows(wallet,payouts,drivers);
 // Never log names, personal documents, tokens, addresses, CPF, Pix keys or GPS.
 console.log(JSON.stringify({mode:dryRun?'DRY_RUN':'WRITE',couriers:drivers.length,
  applications:applications.length,ledger:data.transactions.length,
  payouts:data.payouts.length,unapproved:drivers.filter(x=>x.state!=='APPROVED').length}));
 if(dryRun)return;
 // Inserts are idempotent; preserves old IDs for wallets and payouts.
 await writeMany('up_courier_applications',applications);
 await writeMany('up_couriers',drivers);
 await writeMany('up_wallet_transactions',data.transactions);
 await writeMany('up_payouts',data.payouts);
 await api(TARGET,newKey,'up_audit_log',{method:'POST',
  payload:[{id:randomUUID(),actor:'up_migration',action:'legacy_data_import',
   entity_type:'migration',metadata:{couriers:drivers.length,applications:applications.length,
    ledger:data.transactions.length,payouts:data.payouts.length}}]});
 console.log('UP migration copied to NEW project. Old store data remains unchanged.');
}
main().catch(err=>{console.error('UP migration stopped:',err.message||'unexpected_error');process.exitCode=1});
