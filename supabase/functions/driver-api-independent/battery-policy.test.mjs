import test from "node:test";
import assert from "node:assert/strict";
import { shouldBlockNewOffer, MIN_BATTERY_PERCENT } from "./battery-policy.mjs";

const NOW = Date.parse("2026-11-09T21:00:00.000Z");
const data = (battery_level, at = new Date(NOW).toISOString()) =>
  ({ telemetry: { battery_level, at } });

test("limiar de bateria para ofertas novas", () => {
  assert.equal(MIN_BATTERY_PERCENT, 15);
  assert.equal(shouldBlockNewOffer(data(14), NOW), true);
  assert.equal(shouldBlockNewOffer(data(0), NOW), true);
  assert.equal(shouldBlockNewOffer(data(15), NOW), false);
  assert.equal(shouldBlockNewOffer(data(20), NOW), false);
});
test("telemetria ausente, invalida ou antiga nao bloqueia por engano", () => {
  assert.equal(shouldBlockNewOffer({}, NOW), false);
  assert.equal(shouldBlockNewOffer(data(-1), NOW), false);
  assert.equal(shouldBlockNewOffer(data(999), NOW), false);
  assert.equal(shouldBlockNewOffer(data("3"), NOW), false);
  assert.equal(shouldBlockNewOffer(data(5, new Date(NOW - 6*60_000).toISOString()), NOW), false);
  assert.equal(shouldBlockNewOffer(data(5, "nao-e-data"), NOW), false);
  assert.equal(shouldBlockNewOffer(data(5, new Date(NOW + 5*60_000).toISOString()), NOW), false);
});
test("bateria critica fresca permanece bloqueada mesmo carregando", () => {
  assert.equal(shouldBlockNewOffer({
    telemetry: { battery_level: 12, charging: true, at: new Date(NOW).toISOString() }
  }, NOW), true);
});
