// UP production connectivity/authentication smoke check. NEVER sends a real user,
// payment, courier location, order identifier, Firebase ID token or API secret.
import fs from 'node:fs';
import assert from 'node:assert/strict';

const UP='https://gkwmqwtktedarodvqqtn.supabase.co/functions/v1';
const STORE='https://fdqqwdplprzpqufpgdrm.supabase.co/functions/v1';
const publicConfig=fs.readFileSync('web-gestor-independente/config.js','utf8');
const publishable=publicConfig.match(/publishableKey:"(sb_publishable_[A-Za-z0-9_-]+)"/)?.[1];
assert.ok(publishable,'UP public client key must be present in public config');
const issues=[], warnings=[], report=[];
const request=async(name,url,{origin='',action='session',body=null,method='POST',key=publishable}={})=>{
 const headers={apikey:key};
 if(origin)headers.Origin=origin;
 if(method==='POST')headers['content-type']='application/json';
 const res=await fetch(url,{
  method,headers,body:method==='POST'?JSON.stringify(body??{action}):undefined,
  signal:AbortSignal.timeout(16000),redirect:'error'
 });
 let data={};
 try{data=await res.json()}catch{}
 return {name,status:res.status,error:String(data?.error??''),
  cors:res.headers.get('access-control-allow-origin')||''};
};
function guard(r){
 const passed=r.status===401 && r.error==='firebase_token_required';
 report.push({name:r.name,result:passed?'PASS':'FAIL',message:'HTTP '+r.status+' '+r.error});
 if(!passed)issues.push(r.name+': expected 401/firebase_token_required, received '+r.status+'/'+r.error);
}
try{
 guard(await request('Android driver login guard',UP+'/driver-api-independent'));
 guard(await request('Android registration guard',UP+'/driver-registration-independent',{action:'status'}));
 guard(await request('UP Gestor role guard',UP+'/up-manager-independent',{
  action:'bootstrap',origin:'https://upgestor-rac.web.app'}));
 const preflight=await fetch(UP+'/up-manager-independent',{
  method:'OPTIONS',headers:{
   Origin:'https://upgestor-rac.web.app',
   'Access-Control-Request-Method':'POST',
   'Access-Control-Request-Headers':'authorization,content-type,apikey'
  },signal:AbortSignal.timeout(16000),redirect:'error'
 });
 const cors=preflight.status===204&&preflight.headers.get('access-control-allow-origin')==='https://upgestor-rac.web.app';
 report.push({name:'Browser CORS (UP Gestor)',result:cors?'PASS':'FAIL',message:'HTTP '+preflight.status});
 if(!cors)issues.push('UP Gestor CORS misconfigured');
 // Empty ticket only: never a customer receipt or live geolocation.
 const customer=await request('UP customer GPS ticket guard',UP+'/up-customer-tracking',{
  body:{ticket:''},origin:'https://rodriguesacaiecia.web.app'
 });
 if(customer.status===401&&customer.error==='invalid_or_expired_ticket'){
  report.push({name:customer.name,result:'PASS',message:'Invalid ticket rejected; API reachable'});
 }else if(customer.status===503){
  warnings.push('Customer GPS API not ready: HTTP 503 '+customer.error);
  report.push({name:customer.name,result:'WARN',message:'HTTP 503 '+customer.error});
 }else{
  issues.push('Customer GPS ticket check unexpected: '+customer.status+'/'+customer.error);
  report.push({name:customer.name,result:'FAIL',message:'HTTP '+customer.status+' '+customer.error});
 }
 // No valid receipt or ID is ever sent; this call must disclose no data.
 const ticket=await request('Store customer ticket input guard',STORE+'/up-customer-ticket',{
  body:{},origin:'https://rodriguesacaiecia.web.app',key:''
 });
 const ticketOk=ticket.status>=400&&ticket.status<500;
 report.push({name:ticket.name,result:ticketOk?'PASS':'WARN',message:'HTTP '+ticket.status+' '+ticket.error});
 if(!ticketOk)warnings.push('Store customer ticket not ready/unexpected: '+ticket.status+'/'+ticket.error);
}catch(e){issues.push('Network or probe failure: '+(e.message||e))}
for(const r of report)console.log(r.result+': '+r.name+' — '+r.message);
for(const w of warnings)console.warn('WARN: '+w);
for(const i of issues)console.error('FAIL: '+i);
const summary=[
 '## UP Entregas — teste de transporte e autorização (somente leitura)','',
 '| Verificação | Resultado | Detalhe |','|---|---|---|',
 ...report.map(x=>'| '+x.name+' | '+x.result+' | '+x.message.replace(/\|/g,'/')+' |'),
 '','**Problemas:** '+issues.length+'. **Avisos:** '+warnings.length+'.','',
 'Nenhum pedido, corrida, cobrança, token real ou coordenada foi enviado.',
 '**Não substitui teste de login e GPS em aparelho Android real.**'
].join('\n')+'\n';
if(process.env.GITHUB_STEP_SUMMARY)fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY,summary);
if(issues.length)process.exitCode=1;
