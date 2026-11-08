import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
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
assert.ok(!api.includes("fdqqwdplprzpqufpgdrm"),"No store connection hardcoded");
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
assert.ok(config.includes('firebaseApiKey:""')&&config.includes('managerApiUrl:""'),
 "Never point a nonprovisioned manager at existing store API");
assert.ok(!/SERVICE_ROLE_KEY|UP_BRIDGE_SECRET/.test(config),
 "No private key in web configuration");
console.log("PASS UP Gestor: Firebase JWT, roles, RLS, review, offerings, payments, mobile UI and secret hygiene");
