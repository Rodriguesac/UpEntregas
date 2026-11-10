import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=(s)=>readFileSync(s,'utf8');
for(const [key,slug] of [['driver','driver-api-independent'],['registration','driver-registration-independent'],['manager','up-manager-independent']]){
 const s=read('supabase/functions/'+slug+'/index.ts');
 assert.match(s,new RegExp('eq\\("id","runtime_'+key+'"\\)'));
 assert.match(s,/gate\.data\?\.value\?\.enabled!==true/);
 assert.match(s,/jwtVerify\(/);
 assert.match(s,/SUPABASE_SERVICE_ROLE_KEY/);
}
const m=read('supabase/functions/up-manager-independent/index.ts');
assert.match(m,/appassets\.androidplatform\.net/);
assert.ok(m.indexOf('manager_not_authorized')<m.indexOf('"runtime_manager"'));
assert.match(read('web-gestor-independente/config.js'),/enabled:true,/);
console.log('PASS UP: private runtime flags and manager authentication');
