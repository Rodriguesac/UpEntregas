// Regra operacional: ofertas novas exigem pelo menos 15% de bateria.
// Dados antigos/ausentes nao bloqueiam por si sós, pois o app também
// verifica o nível diretamente no aparelho antes de aceitar.
export const MIN_BATTERY_PERCENT = 15;
export function shouldBlockNewOffer(metadata, nowMs = Date.now()) {
  if (!metadata || typeof metadata !== "object" || Array.isArray(metadata)) return false;
  const telemetry = metadata.telemetry;
  if (!telemetry || typeof telemetry !== "object" || Array.isArray(telemetry)) return false;
  const level = telemetry.battery_level;
  if (typeof level !== "number" || !Number.isInteger(level) || level < 0 || level > 100) return false;
  const atMs = typeof telemetry.at === "string" ? Date.parse(telemetry.at) : NaN;
  if (!Number.isFinite(atMs) || atMs > nowMs + 60_000 || nowMs - atMs > 5 * 60_000) return false;
  return level < MIN_BATTERY_PERCENT;
}
