import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import vm from 'node:vm';

// Execute the actual HTTP handler with isolated auth/database adapters.
// PostgreSQL locking and transitions are tested separately against PostgreSQL.
const source=stripTypeScriptTypes(readFileSync('supabase/functions/driver-api-independent/index.ts','utf8')
  .replace(/^import .*;\n/gm,''));
const jobId='00000000-0000-4000-8000-000000000001';
const courierId='00000000-0000-4000-8000-000000000002';
function fixture({state='APPROVED',offerState='PENDING',battery=80,online=true}={}) {
 const courier={id:courierId,firebase_uid:'verified-user',state,online,
   metadata:{telemetry:{battery_level:battery,at:new Date().toISOString()}}};
 const offer={id:'00000000-0000-4000-8000-000000000003',job_id:jobId,courier_id:courierId,
   status:offerState,expires_at:offerState==='ACCEPTED'?'2000-01-01': '2099-01-01'};
 const job={id:jobId,assigned_courier_id:courierId,status:'ACCEPTED'};
 const calls=[];
 let rpcError=null;
 function from(table) {
  let rows=table==='up_couriers'?[courier]:table==='up_dispatch_offers'?[offer]:table==='up_jobs'?[job]:[];
  let single=false;
  const q={
   select(){return q},eq(k,v){rows=rows.filter(r=>r[k]===v);return q},
   in(k,vs){rows=rows.filter(r=>vs.includes(r[k]));return q},
   gt(k,v){rows=rows.filter(r=>r[k]>v);return q},
   order(){return q},limit(n){rows=rows.slice(0,n);return q},
   maybeSingle(){single=true;return q},
   then(resolve,reject){return Promise.resolve({data:single?(rows[0]||null):rows,error:null}).then(resolve,reject)}
  };return q;
 }
 let handler;
 const context=vm.createContext({Request,Response,URL,TextEncoder,Date,console,
  Deno:{env:{get:k=>({UP_INDEPENDENT_DRIVER_ENABLED:'true',SUPABASE_URL:'https://up.example',SUPABASE_SERVICE_ROLE_KEY:'isolated-test'})[k]},serve:fn=>{handler=fn}},
  createRemoteJWKSet:()=>null,jwtVerify:async token=>{
   if(token!=='valid-test-token')throw Error('invalid');return {payload:{sub:'verified-user'}};
  },createClient:()=>({from,rpc:async(name,args)=>{calls.push({name,args});return {data:[{already_applied:offer.status==='ACCEPTED'}],error:rpcError}}})});
 vm.runInContext(source,context);
 return {courier,offer,job,calls,setError:e=>{rpcError=e},request:async(body,token='valid-test-token')=>{
  const res=await handler(new Request('https://up.example/driver',{method:'POST',headers:token?{authorization:'Bearer '+token}:{},body:JSON.stringify(body)}));
  return {status:res.status,body:await res.json()};
 }};
}
let count=0;
async function test(name,fn){await fn();count++;console.log('PASS '+name)}
await test('missing and forged Firebase tokens are rejected before database access',async()=>{
 const f=fixture();assert.equal((await f.request({},'')).status,401);assert.equal((await f.request({},'forged')).status,401);assert.equal(f.calls.length,0);
});
await test('null and array payloads are rejected without an uncaught exception',async()=>{
 const f=fixture();for(const body of [null,[],true,'accept'])assert.equal((await f.request(body)).status,400);
});
await test('suspended courier cannot operate',async()=>{
 const f=fixture({state:'SUSPENDED'});assert.equal((await f.request({action:'accept',mission_id:jobId})).status,403);assert.equal(f.calls.length,0);
});
await test('accept uses verified courier identity rather than client-supplied identity',async()=>{
 const f=fixture();assert.equal((await f.request({action:'accept',mission_id:jobId,courier_id:'attacker'})).status,200);
 assert.equal(f.calls[0].args.p_courier,courierId);
});
await test('lost accept response can be retried after expiry, offline and with low battery',async()=>{
 const f=fixture({offerState:'ACCEPTED',battery:5,online:false});assert.equal((await f.request({action:'accept',mission_id:jobId})).status,200);
 assert.equal(f.calls[0].name,'up_driver_accept');
});
await test('new acceptance still respects recent low battery guard',async()=>{
 const f=fixture({battery:5});assert.equal((await f.request({action:'accept',mission_id:jobId})).body.error,'battery_below_minimum');assert.equal(f.calls.length,0);
});
await test('rejected offers cannot be accepted',async()=>{
 const f=fixture({offerState:'DECLINED'});assert.equal((await f.request({action:'accept',mission_id:jobId})).status,409);assert.equal(f.calls.length,0);
});
await test('database busy and offline errors reach the app accurately',async()=>{
 for(const error of ['courier_busy','courier_not_available']){
  const f=fixture();f.setError({message:error});assert.equal((await f.request({action:'accept',mission_id:jobId})).body.error,error);
 }
});
await test('unknown stage cannot silently start a trip',async()=>{
 const f=fixture();assert.equal((await f.request({action:'stage',mission_id:jobId,status:'typo'})).status,422);assert.equal(f.calls.length,0);
});
await test('all supported mobile steps map to canonical database states',async()=>{
 for(const [action,status,next] of [['stage','A_CAMINHO_LOJA','TO_STORE'],['stage','COLETANDO','AT_STORE'],['pickup','','TO_CUSTOMER'],['stage','NO_CLIENTE','AT_CUSTOMER'],['finish','','DELIVERED']]){
  const f=fixture();assert.equal((await f.request({action,mission_id:jobId,status,code:'1234',received_amount:10})).status,200);
  assert.equal(f.calls[0].args.p_next,next);assert.equal(f.calls[0].args.p_code,'1234');
 }
});
await test('location rejects empty, null and boolean coordinates',async()=>{
 for(const lat of [null,'',true]){const f=fixture();assert.equal((await f.request({action:'location',mission_id:jobId,lat,lng:20})).status,422);assert.equal(f.calls.length,0)}
});
await test('other couriers cannot advance or track a job',async()=>{
 const f=fixture();f.job.assigned_courier_id='other';
 for(const action of ['pickup','finish','location'])assert.equal((await f.request({action,mission_id:jobId})).status,403);
 assert.equal(f.calls.length,0);
});
console.log(`${count} runtime HTTP tests passed; no network or production writes.`);
