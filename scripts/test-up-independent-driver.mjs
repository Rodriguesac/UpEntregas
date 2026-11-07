import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const api=readFileSync('supabase/functions/driver-api-independent/index.ts','utf8');
const sql=readFileSync('supabase/up-driver-operations.sql','utf8');
for(const text of [
 'jwtVerify(bearer,jwks','algorithms:["RS256"]',
 'audience:firebaseProject','firebase_uid",uid',
 'courier.state!=="APPROVED"',
 'UP_INDEPENDENT_DRIVER_ENABLED")!=="true"',
 'UP_FIREBASE_PROJECT_ID','UP_DRIVER', // replaced below if needed
 '.eq("courier_id",courier.id)',
 'up_driver_accept','up_driver_advance','up_driver_location',
 'up_dispatch_offers','up_jobs','up_driver_devices',
 'mission_not_authorized','offer_not_found',
 'req.headers.has("origin")'
]){
 if(text==='UP_DRIVER')continue;
 assert.ok(api.includes(text),'Independent driver security/API missing: '+text);
}
for(const text of [
 "for update","offer.expires_at<=now()",
 "offer.status<>'PENDING'","job.assigned_courier_id is not null",
 "job.status<>'OFFERED'","courier.state<>'APPROVED'",
 "p_courier","incorrect_confirmation_code",
 "job.status<>expected_state","p_received<job.collection_amount",
 "p_lat not between -90 and 90",
 "job.assigned_courier_id is distinct from p_courier",
 "last_location_at>now()-interval '8 seconds'",
 "alter table public.up_driver_devices enable row level security",
 "revoke all on public.up_driver_devices from anon,authenticated",
 "grant execute on function public.up_driver_accept"
])assert.ok(sql.includes(text),'UP SQL driver rule missing: '+text);
assert.ok(!/\.from\(["']orders["']\)/.test(api),'Independent API cannot query shop orders');
assert.ok(!/\.from\(["']customers["']\)/.test(api),'Independent API cannot query shop customers');
assert.ok(!/\.from\(["']tracking_points["']\)/.test(api),'Independent API must use private UP GPS RPC');
assert.ok(!api.includes('SERVICE_ROLE_KEY = "'),'Service key cannot be hardcoded');
assert.ok(!api.includes('fdqqwdplprzpqufpgdrm'),'No store database reference');
console.log('PASS API independente: Firebase Auth, ofertas vinculadas, GPS e isolamento');
