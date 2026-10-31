import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

test("a fila do motoboy prioriza a oferta direcionada, depois ofertas genéricas", () => {
  const source=fs.readFileSync("supabase/functions/driver-api/index.ts","utf8");
  const begin=source.indexOf("async function offeredOrder(");
  const end=source.indexOf("async function event(",begin);
  assert.ok(begin>=0&&end>begin,"buscar a função de ofertas");
  const fn=source.slice(begin,end);
  const direct=fn.indexOf('.contains("raw_payload", { up_target_courier_id: courier.id })');
  const generic=fn.indexOf('const result = await admin.from("orders")');
  assert.ok(direct>=0&&generic>direct,"ofertas direcionadas devem ter prioridade");
  assert.equal((fn.match(/\.in\("status",/g)||[]).length,2,"não varrer pedidos encerrados");
  assert.match(fn,/if \(targeted\) return targeted;/,"não substituir a oferta do gestor por genérica");
  assert.match(fn,/!object\(row\.raw_payload\)\.up_target_courier_id/,"genérica não revela oferta de outro entregador");
});

test("aceite verifica destinatário, atualização concorrente e não aceita duas vezes",()=>{
  const source=fs.readFileSync("supabase/functions/driver-api/index.ts","utf8");
  assert.match(source,/!canOfferToCourier\(row,courier\.id,firebaseUid\)/);
  assert.match(source,/already_accepted:true/);
  assert.match(source,/update = update\.eq\("updated_at",row\.updated_at\)/);
});

test("motoboy já em missão não volta a disponível após reconectar",()=>{
  const src=fs.readFileSync("supabase/functions/driver-api/index.ts","utf8");
  const presence=src.slice(src.indexOf('if (action === "presence")'),src.indexOf('if (action === "telemetry")'));
  assert.match(presence,/const active = await currentOrder\(admin,courier\.id\)/);
  assert.match(presence,/const busyStatus=active \?/);
  assert.doesNotMatch(presence,/status: online \? "available" : "offline"/);
});
test("aceite nega nova missão quando já está em outra entrega ativa",()=>{
  const src=fs.readFileSync("supabase/functions/driver-api/index.ts","utf8");
  const start=src.indexOf('if (action === "accept")');
  const end=src.indexOf('if (action === "reject"',start);
  assert.ok(start>=0&&end>start);
  const accept=src.slice(start,end);
  assert.match(accept,/activeMission=await currentOrder\(admin,courier\.id\)/);
  assert.match(accept,/activeMission\.id !== missionId/);
  assert.match(accept,/error:"courier_busy"/);
  assert.match(accept,/error:"courier_offline"/);
  const current=src.slice(src.indexOf("async function currentOrder("),src.indexOf("async function offeredOrder("));
  assert.match(current,/\.not\("status","in"/);
  assert.doesNotMatch(current,/\.limit\(20\)/);
});
