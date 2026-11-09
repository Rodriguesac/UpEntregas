import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const api=readFileSync("supabase/functions/driver-registration-independent/index.ts","utf8");
for(const s of [
 'jwtVerify(token,jwks','algorithms:["RS256"]','audience:project',
 'firebase_uid",uid','UP_INDEPENDENT_REGISTRATION_ENABLED")!=="true"',
 'req.headers.has("origin")',
 '.from("up_courier_applications")',
 '.from("up_couriers")',
 'const status="PENDING"',
 'approved_profile_requires_manager_review',
 'stateToApp','name_and_phone_required','invalid_document_url',
 'u.protocol!=="https:"'
])assert.ok(api.includes(s),'Independent registration requires '+s);
assert.ok(!api.includes('state:profile.status'),'The applicant must not approve their own registration');
assert.ok(!api.includes('.update({state:"APPROVED"'),'Never approve via public registration action');
assert.ok(!api.includes('SERVICE_ROLE_KEY = "'),'Do not hardcode private key');
assert.ok(!api.includes('fdqqwdplprzpqufpgdrm'),'Must not reference the store project');
const statusSync=api.indexOf('if(action==="status_sync")');
const registrationGate=api.indexOf('if(Deno.env.get("UP_INDEPENDENT_REGISTRATION_ENABLED")!=="true")');
assert.ok(statusSync>=0 && registrationGate>statusSync,
 'Read-only status sync must work with enrollment disabled, after JWT verification');
assert.ok(api.indexOf('jwtVerify(token,jwks')<statusSync,
 'Never allow unauthenticated status lookups');
assert.ok(api.includes('courierState==="SUSPENDED"?"suspended"'),
 'Suspended couriers may not inherit stale approved application status');
assert.ok(api.includes('if(previous==="APPROVED")'),
 'Approved applications must not be downgraded via the public endpoint');
console.log('PASS cadastro independente: JWT, consulta read-only, revisao e bloqueio de autoaprovacao');
