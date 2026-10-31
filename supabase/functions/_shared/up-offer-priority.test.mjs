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
