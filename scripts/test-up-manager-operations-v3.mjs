import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import vm from "node:vm";
const root="web-gestor-independente/";
const app=readFileSync(root+"app.js","utf8");
const view=readFileSync(root+"operations-ui.js","utf8");
const map=readFileSync(root+"map.js","utf8");
const page=readFileSync(root+"index.html","utf8");
const css=readFileSync(root+"styles.css","utf8");
const api=readFileSync("supabase/functions/up-manager-independent/index.ts","utf8");
for(const file of ["config.js","map.js","app.js","operations-ui.js"]){
 assert.ok(page.includes('<script src="'+file+'"'),"Missing loaded script "+file);
}
assert.ok(page.indexOf("map.js")<page.indexOf("app.js"));
assert.ok(page.indexOf("app.js")<page.indexOf("operations-ui.js"));
assert.match(page,/theme-color" content="#053e31"/);
for(const name of ["overview","jobs","couriers","map"]){
 assert.ok(view.includes("function "+name+"("),"Missing page renderer "+name);
}
assert.ok(app.includes("window.UPGestorV3"),"Missing real UI routing");
assert.ok(app.includes("operations_map"),"Map must get data from secure backend");
assert.ok(app.includes("offer_history"),"Refusals must come from secure backend");
assert.ok(app.includes("OPERATION_REFRESH_MS=90000"),"No aggressive polling");
assert.ok(app.includes("document.hidden"),"Auto refresh must pause when app is hidden");
assert.ok(app.includes("STORE.active"),"Refresh cannot run before authentication");
assert.ok(app.includes('next==="map"&&!isDispatcher()'),"Viewer cannot access map");
assert.ok(app.includes('x.id==="map"&&!isDispatcher()'),"Map cannot be shown to viewer");
for(const x of ["action===\"operations_map\"","action===\"offer_history\"",
 ".eq(\"online\",true)",'.eq("state","APPROVED")',
 "tracking_enabled===true","5*60*1000",
 'authorizeRole(role,operatingRoles);']){
 assert.ok(api.includes(x),"Missing GPS privacy / authorization: "+x);
}
assert.ok(!/localStorage\.setItem|sessionStorage\.setItem/.test(app));
assert.ok(!/service_role|secret/i.test(readFileSync(root+"config.js","utf8")
 .replaceAll("service_role, HMAC, senhas, tokens de sessão ou segredos", "")),"No secret in config");
assert.ok(!/fetch\(/.test(map),"Map can only load public image tiles");
assert.ok(map.includes("tile.openstreetmap.org"),"Map must attribute tile provider");
assert.ok(map.includes("openstreetmap.org/copyright"),"Attribution link missing");
assert.ok(map.includes("state.items.length"),"Do not load maps without real positions");
assert.ok(css.includes("grid-template-columns:repeat(5"),"5 mobile nav slots required");
const sandbox={window:{},document:{},URL,Math,Number,Date,String,Object,Array,Set,Map};
vm.runInNewContext(map,sandbox);
vm.runInNewContext(view,sandbox);
assert.equal(typeof sandbox.window.UPMap.mount,"function");
assert.equal(typeof sandbox.window.UPMap.zoom,"function");
assert.equal(typeof sandbox.window.UPGestorV3.map,"function");
assert.equal(typeof sandbox.window.UPGestorV3.overview,"function");
console.log("PASS: responsive UP Gestor pages, no fake GPS, role restrictions, OSM laziness, visible-only polling");
