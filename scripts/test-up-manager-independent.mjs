import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import vm from "node:vm";
const api=readFileSync("supabase/functions/up-manager-independent/index.ts","utf8");
const sql=readFileSync("supabase/up-manager-independent.sql","utf8");
const app=readFileSync("web-gestor-independente/app.js","utf8");
const html=readFileSync("web-gestor-independente/index.html","utf8");
const css=readFileSync("web-gestor-independente/styles.css","utf8");
const config=readFileSync("web-gestor-independente/config.js","utf8");
for(const p of [
 "jwtVerify(token,jwks","algorithms:[\"RS256\"]",
 "audience:project","UP_INDEPENDENT_MANAGER_ENABLED",
 "UP_MANAGER_ALLOWED_ORIGINS","hosts.includes(origin)",
 'req.headers.get("authorization")',
 '.from("up_manager_users")',
 '.eq("firebase_uid",uid)',
 'actor.data?.active','privateRoles','operatingRoles',
 'authorizeRole(role,privateRoles)',
 'authorizeRole(role,operatingRoles)',
 '"application_detail"','"review_application"',
 '"set_driver_state"','"send_offer"','"payout_confirm"',
 '"payout_request"','"occurrences"','"audit"',
 'up_manager_review_application','up_manager_create_offer',
 'up_manager_set_driver_state','up_manager_request_payout',
 'up_manager_confirm_payout'
])assert.ok(api.includes(p),"Manager API security requirement missing: "+p);
for(const p of [
 "create table if not exists public.up_manager_users(",
 "role text not null check(role in ('OWNER','ADMIN','DISPATCHER','VIEWER'))",
 "active boolean not null default false",
 "alter table public.up_manager_users enable row level security",
 "revoke all on public.up_manager_users from public,anon,authenticated",
 "up_manager_review_application","up_manager_create_offer",
 "up_manager_set_driver_state","up_manager_request_payout",
 "up_manager_confirm_payout","for update",
 "job_already_offered","driver_has_active_job",
 "insufficient_wallet_balance","payout_recorded_paid",
 "grant execute on function public.up_manager_create_offer",
 "to service_role"
])assert.ok(sql.includes(p),"Manager SQL requirement missing: "+p);
assert.ok(!sql.includes("VALUES ('admin','admin'"),"Never ship a default administrator");
assert.ok(!sql.includes("security definer"),"Avoid bypass-RLS manager RPCs");
assert.ok(!/\.from\(["'](?:orders|customers|tracking_points)["']\)/.test(api),
 "Independent manager cannot query the store or its GPS");
// An exact allowed callback destination is configuration validation, not a store DB connection.
assert.ok(api.includes('UP_STATUS_STORE_ENDPOINT'),"Manager diagnostics must verify expected callback target");
assert.ok(!api.includes('createClient("https://fdqqwdplprzpqufpgdrm'),"Manager must never connect to store database");
assert.ok(!api.includes('fetch("https://fdqqwdplprzpqufpgdrm'),"Manager must never fetch store APIs");
assert.ok(!app.includes("x-gestor-pin"),"New manager must not use shared store PIN");
assert.ok(!app.includes("localStorage")||app.includes("Never store Firebase"),"No persisted credentials");
assert.ok(!/localStorage\.setItem|sessionStorage\.setItem/.test(app),"No browser token persistence");
assert.ok(app.includes('refreshIdentity()'),"Firebase refresh token must work");
assert.ok(app.includes('accounts:signInWithPassword'),"Firebase login required");
assert.ok(app.includes('application_detail'),"Review view must fetch the selected application only");
assert.ok(app.includes('UP_MANAGER_CONFIG'),"Public config must be separate");
assert.ok(app.includes('getElementById')||app.includes('querySelector'),"UI must use real DOM");
assert.ok(app.includes("data-action"),"UI actions must be wired");
assert.ok(app.includes("Esc")||app.includes("escapeHtml"),"HTML data must be escaped");
assert.ok(css.includes("@media(max-width:720px)"),"Mobile nav CSS required");
assert.ok(html.includes('name="viewport"'),"Mobile viewport required");
assert.ok(html.includes('id="bottomNav"'),"Mobile nav required");
assert.ok(html.includes('id="sideNav"'),"Desktop nav required");
const sandbox={window:{}};
vm.runInNewContext(config,sandbox);
const publicConfig=sandbox.window.UP_MANAGER_CONFIG;
assert.equal(publicConfig.enabled,false,"The independent manager stays disabled until backend is authorized");
assert.equal(publicConfig.projectRef,"gkwmqwtktedarodvqqtn");
assert.equal(publicConfig.projectUrl,"https://gkwmqwtktedarodvqqtn.supabase.co");
assert.equal(publicConfig.managerApiUrl,publicConfig.projectUrl+"/functions/v1/up-manager-independent");
assert.match(publicConfig.publishableKey,/^sb_publishable_[A-Za-z0-9_-]+$/);
assert.match(publicConfig.firebaseApiKey,/^AIza[A-Za-z0-9_-]{30,}$/);
assert.equal(publicConfig.firebaseProjectId,"rodrigues-d6566");
assert.ok(app.includes("CONFIG.enabled===true"),"UI must require explicit activation");
assert.ok(app.includes('"apikey":CONFIG.publishableKey'),"API must send only the publishable key and the Firebase JWT");
assert.ok(app.includes("url.hostname===project.hostname"),"API hostname must match its configured UP project");
assert.ok(!/SERVICE_ROLE_KEY|UP_BRIDGE_SECRET/.test(config),
 "No private key in web configuration");
assert.ok(api.includes('action==="integration_health"'),"Manager integration diagnostics must exist");
assert.ok(api.includes('authorizeRole(role,privateRoles);'),"Diagnostics require an administrator role");
assert.ok(!api.includes("value:Deno.env.get("),"Never expose secrets as response fields");
assert.ok(app.includes('function renderIntegrations()')&&app.includes('integration_health'),"Manager UI must display diagnostics");
for(const item of ['action==="driver_diagnostics"','push_registered','gps_recent','firebase_linked','battery_recent'])
 assert.ok(api.includes(item),"Missing private courier readiness diagnostics: "+item);
for(const item of ["function driverDiagnosticsDialog(","data-action=\"driver-diagnostics\"","driver_diagnostics"])
 assert.ok(app.includes(item),"Missing mobile courier readiness diagnostics UI: "+item);
const merchantDraftSql=readFileSync("supabase/up-merchant-onboarding-drafts.sql","utf8");
for(const item of [
 'action==="merchants_list"',
 'action==="merchant_draft_create"',
 'up_manager_create_merchant_draft',
 'if(role!=="OWNER")',
])assert.ok(api.includes(item),"UP multi-store API security contract missing: "+item);
for(const item of [
 'function renderMerchants()',
 'function merchantDraftDialog()',
 'merchant_draft_create',
 'merchants_list',
 'Cadastro de lojas exige o proprietário',
])assert.ok(app.includes(item),"UP multi-store UI contract missing: "+item);
for(const item of [
 "create or replace function public.up_manager_create_merchant_draft(",
 "values(slug_clean,name_clean,'DRAFT')",
 "uf_clean,'DRAFT',true",
 "up_audit_log",
 "revoke all on function public.up_manager_create_merchant_draft(",
 "to service_role;"
])assert.ok(merchantDraftSql.includes(item),"UP draft-only SQL contract missing: "+item);
assert.ok(!merchantDraftSql.toLowerCase().includes("security definer"),"No SECURITY DEFINER in merchant onboarding");
console.log("PASS UP Gestor: Firebase JWT, roles, RLS, review, offerings, payments, mobile UI and secret hygiene");
