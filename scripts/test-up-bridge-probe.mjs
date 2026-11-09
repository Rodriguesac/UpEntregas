import assert from "node:assert/strict";
import fs from "node:fs";
import {webcrypto as crypto} from "node:crypto";
const receiver=fs.readFileSync("bridge/store-status-receiver/index.ts","utf8");
const manager=fs.readFileSync("supabase/functions/up-manager-independent/index.ts","utf8");
const ui=fs.readFileSync("web-gestor-independente/app.js","utf8");
for(const t of [
 'if(req.headers.has("origin"))','authenticated(req,body,secret)',
 'if(event?.action==="bridge_probe")',
 'UUID.test(event.nonce)','callback_enabled:callbackEnabled',
 'if(!callbackEnabled)return reply(503',
 'return reply(200,{ok:true,probe:true,signature_verified:true'
])assert.ok(receiver.includes(t),"Receiver sem proteção: "+t);
const verifiedAt=receiver.indexOf('if(!await authenticated(req,body,secret))');
const probeAt=receiver.indexOf('if(event?.action==="bridge_probe")');
const dbAt=receiver.indexOf('const admin=createClient(url,service',probeAt);
assert.ok(verifiedAt>=0&&probeAt>verifiedAt&&dbAt>probeAt,
 'Probe só depois de HMAC e antes de qualquer operação no banco');
for(const t of [
 'if(action==="integration_probe")','authorizeRole(role,privateRoles)',
 'destination!==expected','crypto.subtle.sign("HMAC"',
 '"x-up-timestamp":timestamp','"x-up-signature":hex',
 'signal:AbortSignal.timeout(9000)',
 'action:"bridge_probe"', 'status:connected?"signed_connection_ok"',
 'signature_mismatch'
])assert.ok(manager.includes(t),"Manager sem proteção: "+t);
assert.ok(ui.includes('data-action="probe-integration"'));
assert.ok(ui.includes('call("integration_probe")'));
assert.ok(!/localStorage\.setItem|sessionStorage\.setItem/.test(ui),'Não persistir credenciais no navegador');
const enc=new TextEncoder(),secret="a".repeat(64);
const payload=JSON.stringify({version:1,action:"bridge_probe",nonce:crypto.randomUUID()});
const ts=String(Math.floor(Date.now()/1000));
const key=await crypto.subtle.importKey("raw",enc.encode(secret),{name:"HMAC",hash:"SHA-256"},false,["sign","verify"]);
const signature=await crypto.subtle.sign("HMAC",key,enc.encode(ts+"\n"+payload));
assert.ok(await crypto.subtle.verify("HMAC",key,signature,enc.encode(ts+"\n"+payload)));
const other=await crypto.subtle.importKey("raw",enc.encode("b".repeat(64)),{name:"HMAC",hash:"SHA-256"},false,["verify"]);
assert.equal(await crypto.subtle.verify("HMAC",other,signature,enc.encode(ts+"\n"+payload)),false);
assert.equal(await crypto.subtle.verify("HMAC",key,signature,enc.encode(ts+"\n"+payload+"tampered")),false);
console.log("PASS: probe HMAC privado, sem alterações de pedidos e sem exposição de segredo");