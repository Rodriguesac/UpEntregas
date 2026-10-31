import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";

test("UP integra registro do token no Supabase com identidade Firebase",()=>{
 const repository=readFileSync("app/src/main/java/com/rodriguesacai/entregador/DriverRepository.java","utf8");
 const api=readFileSync("app/src/main/java/com/rodriguesacai/entregador/SupabaseDriverApi.java","utf8");
 const edge=readFileSync("supabase/functions/driver-api/index.ts","utf8");
 const main=readFileSync("app/src/main/java/com/rodriguesacai/entregador/MainActivity.java","utf8");
 assert.match(repository,/return Tasks\.whenAll\(legacy, supabase\.token\(token\)\)/);
 assert.match(api,/public Task<Void> token\(String token\)/);
 assert.match(edge,/if \(action === "token"\)/);
 assert.match(edge,/from\("fcm_devices"\)\.upsert/);
 assert.match(main,/FirebaseMessaging\.getInstance\(\)\.getToken\(\)/);
});

test("FCM direcionado não acorda entregador deslogado ou com outra identidade",()=>{
 const fcm=readFileSync("app/src/main/java/com/rodriguesacai/entregador/UPMessagingService.java","utf8");
 assert.match(fcm,/sessionDriver\.isEmpty\(\) \|\| !targetDriver\.equals\(sessionDriver\)/);
 assert.match(fcm,/if \(rideId\.isEmpty\(\) && routeId\.isEmpty\(\)\) return;/);
 assert.match(fcm,/NotificationHelper\.notifyNewRide\(this, rideId, pedido,/);
 assert.match(fcm,/OfferAlertPlayer\.start\(this, "rides:" \+ rideId\)/);
});
