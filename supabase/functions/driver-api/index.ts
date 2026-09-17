import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.56.1";
import { createRemoteJWKSet, jwtVerify } from "npm:jose@5.9.6";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") || "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
const FIREBASE_PROJECT_ID = "rodrigues-d6566";
const FIREBASE_ISSUER = `https://securetoken.google.com/${FIREBASE_PROJECT_ID}`;
const FIREBASE_JWKS = createRemoteJWKSet(new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"));
const APP_SOURCE = "up_entregas_android_supabase_v270";
const OFFER_STATUSES = new Set(["AGUARDANDO_ENTREGADOR", "BUSCANDO_ENTREGADOR", "PRONTO", "DESPACHADO"]);
const TERMINAL_STATUSES = new Set(["ENTREGUE", "CONCLUIDO", "FINALIZADO", "CANCELADO", "CANCELADA"]);

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "Access-Control-Allow-Origin": "*",
  },
});
const text = (value: unknown) => String(value ?? "").trim();
const num = (value: unknown) => {
  const number = Number(value ?? 0);
  return Number.isFinite(number) ? number : 0;
};
const bool = (value: unknown) => value === true || String(value).toLowerCase() === "true";
const bearer = (req: Request) => text(req.headers.get("authorization")).replace(/^Bearer\s+/i, "");
const object = (value: unknown): Record<string, any> => value && typeof value === "object" && !Array.isArray(value)
  ? value as Record<string, any> : {};

function decodeFirestoreValue(value: any): any {
  if (!value || typeof value !== "object") return null;
  if ("nullValue" in value) return null;
  if ("stringValue" in value) return value.stringValue;
  if ("integerValue" in value) return Number(value.integerValue);
  if ("doubleValue" in value) return Number(value.doubleValue);
  if ("booleanValue" in value) return Boolean(value.booleanValue);
  if ("timestampValue" in value) return value.timestampValue;
  if ("referenceValue" in value) return value.referenceValue;
  if ("geoPointValue" in value) return value.geoPointValue;
  if ("arrayValue" in value) return (value.arrayValue?.values || []).map(decodeFirestoreValue);
  if ("mapValue" in value) return decodeFirestoreFields(value.mapValue?.fields || {});
  return null;
}

function decodeFirestoreFields(fields: Record<string, any>) {
  return Object.fromEntries(Object.entries(fields || {}).map(([key, value]) => [key, decodeFirestoreValue(value)]));
}

async function firestoreDocument(collection: string, id: string, firebaseToken: string) {
  const encoded = id.split("/").map(encodeURIComponent).join("/");
  const url = `https://firestore.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/databases/(default)/documents/${collection}/${encoded}`;
  const response = await fetch(url, { headers: { Authorization: `Bearer ${firebaseToken}` }, cache: "no-store" });
  if (!response.ok) return { ok: false, status: response.status, data: null };
  const body = await response.json();
  return { ok: true, status: response.status, data: decodeFirestoreFields(body?.fields || {}) };
}

function first(source: Record<string, any>, ...keys: string[]) {
  for (const key of keys) {
    const value = text(source?.[key]);
    if (value && value.toLowerCase() !== "null") return value;
  }
  return "";
}

function approvedDriver(profile: Record<string, any>) {
  if (profile?.ativo === false || profile?.aprovado === false) return false;
  const approval = first(profile, "statusAprovacao", "approvalStatus").toLowerCase();
  return !approval || approval === "aprovado" || approval === "approved";
}

function addressText(address: Record<string, any>) {
  const line = [first(address, "rua", "street"), first(address, "numero", "number")].filter(Boolean).join(", ");
  const district = first(address, "bairro", "district", "neighborhood");
  const city = [first(address, "cidade", "city"), first(address, "uf", "state")].filter(Boolean).join("/");
  return [line, district, city].filter(Boolean).join(" • ");
}

function inferState(row: any) {
  const raw = object(row?.raw_payload);
  const explicit = first(raw, "up_state", "upState", "estadoOperacionalUP").toUpperCase();
  if (explicit) return explicit;
  const status = text(row?.status).toUpperCase();
  if (TERMINAL_STATUSES.has(status)) return status.startsWith("CANCEL") ? "CANCELED" : "DELIVERED";
  if (status.includes("NO_CLIENTE")) return "AT_CUSTOMER";
  if (status.includes("DESPACH") || status.includes("ENTREGA")) return "TO_CUSTOMER";
  return row?.courier_id ? "TO_STORE" : "OFFER_PENDING";
}

function orderDocument(row: any, firebaseUid: string, offer: boolean) {
  const raw = object(row?.raw_payload);
  const customer = object(raw?.cliente);
  const address = object(row?.delivery_address || raw?.endereco);
  const latlng = object(address?.latlng);
  const payment = object(raw?.pagamento);
  const method = text(row?.payment_method || payment?.forma || payment?.metodo);
  const paymentStatus = text(row?.payment_status || payment?.status).toUpperCase();
  const paidOnline = bool(payment?.online) || paymentStatus === "PAGO" || paymentStatus === "PAID" || method.toUpperCase().includes("ONLINE");
  const total = num(row?.total || raw?.total || object(raw?.valores)?.total);
  const driverFee = num(raw?.valorRepasseEntregador || raw?.valorEntregador || raw?.taxaMotoboy || row?.delivery_fee);
  const receivedByCourier = num(raw?.up_received_amount || object(row?.payment_details)?.received_by_courier);
  const settlementReceived = paidOnline ? 0 : (receivedByCourier || total);
  const changeFor = num(payment?.trocoPara || payment?.valorTrocoPara);
  const upState = offer ? "OFFER_PENDING" : inferState(row);
  const rejected = Array.isArray(raw?.up_rejected_by) ? raw.up_rejected_by : [];
  const destination = addressText(address);
  const data: Record<string, any> = {
    source: "supabase",
    missionType: "SUPABASE_ORDER",
    pedidoId: text(row?.firebase_id || row?.id),
    orderId: text(row?.id),
    codigoPedido: text(row?.order_code || raw?.codigoPedido || raw?.numeroPedido),
    numeroPedido: text(row?.order_code || raw?.numeroPedido || raw?.codigoPedido),
    clienteNome: text(customer?.nome || row?.customers?.name),
    nomeCliente: text(customer?.nome || row?.customers?.name),
    clienteTelefone: text(customer?.telefone || row?.customers?.phone),
    telefoneCliente: text(customer?.telefone || row?.customers?.phone),
    clienteEnderecoCompleto: destination,
    deliveryAddress: destination,
    enderecoCliente: destination,
    destinoEndereco: destination,
    clienteBairro: first(address, "bairro", "district", "neighborhood"),
    bairroCliente: first(address, "bairro", "district", "neighborhood"),
    clienteLat: num(address?.lat ?? latlng?.lat),
    clienteLng: num(address?.lng ?? latlng?.lng),
    destinoLat: num(address?.lat ?? latlng?.lat),
    destinoLng: num(address?.lng ?? latlng?.lng),
    enderecoLoja: first(raw, "enderecoLoja", "coletaEndereco", "origemEndereco"),
    coletaEndereco: first(raw, "coletaEndereco", "enderecoLoja", "origemEndereco"),
    coletaNome: first(raw, "coletaNome", "lojaNome") || "Rodrigues Açaí e Cia",
    lojaNome: first(raw, "lojaNome", "coletaNome") || "Rodrigues Açaí e Cia",
    lojaLat: num(raw?.lojaLat || raw?.storeLat),
    lojaLng: num(raw?.lojaLng || raw?.storeLng),
    distanciaKm: num(address?.distancia || raw?.distanciaKm),
    valorRepasseEntregador: driverFee,
    valorEntregador: driverFee,
    valorCorrida: driverFee,
    taxaMotoboy: driverFee,
    recebidoPeloEntregador: settlementReceived,
    valorBruto: settlementReceived,
    valorARepassar: Math.max(0, settlementReceived - driverFee),
    valorAReceber: paidOnline ? driverFee : Math.max(0, driverFee - settlementReceived),
    formaPagamento: method,
    metodoPagamento: method,
    pagamento: method,
    valorReceberCliente: paidOnline ? 0 : num(payment?.valorReceberCliente || total),
    totalPedido: total,
    valorPedido: total,
    precisaMaquininha: bool(payment?.precisaMaquininha) || /CREDITO|CRÉDITO|DEBITO|DÉBITO|CARTAO|CARTÃO/.test(method.toUpperCase()),
    trocoPara: changeFor,
    observacoes: text(row?.customer_note || raw?.observacao),
    observacao: text(row?.customer_note || raw?.observacao),
    codigoRetirada: text(row?.pickup_code || raw?.codigoRetirada),
    codigoEntrega: first(raw, "codigoEntrega", "deliveryCode"),
    rastreamentoClienteHabilitado: row?.tracking_enabled !== false,
    ofertaAtiva: offer,
    ofertaAceita: !offer && Boolean(row?.courier_id),
    targetDriverId: firebaseUid,
    entregadorId: row?.courier_id ? firebaseUid : "",
    driverId: row?.courier_id ? firebaseUid : "",
    uidEntregador: row?.courier_id ? firebaseUid : "",
    status: offer ? "OFERTA" : text(raw?.up_status || row?.status),
    statusCorrida: offer ? "OFERTA" : text(raw?.up_status || row?.status),
    statusEntrega: offer ? "AGUARDANDO_ENTREGADOR" : text(raw?.up_delivery_status || row?.status),
    upState,
    upProtocolVersion: 3,
    pickupConfirmed: ["TO_CUSTOMER", "AT_CUSTOMER", "DELIVERED"].includes(upState),
    ofertaCriadaEm: new Date().toISOString(),
    offerExpiresAt: Date.now() + 60000,
    createdAt: row?.created_at,
    updatedAt: row?.updated_at,
    _rejectedBy: rejected,
  };
  if (raw?.up_location) data.localizacaoEntregador = raw.up_location;
  return { id: `supabase:${row.id}`, data };
}

async function ensureCourier(admin: any, firebaseUid: string, profile: Record<string, any>) {
  const existing = await admin.from("couriers").select("*").eq("firebase_uid", firebaseUid).maybeSingle();
  if (existing.error) throw new Error(`courier_lookup_failed: ${existing.error.message}`);
  const oldMetadata = object(existing.data?.metadata);
  const metadata = {
    ...oldMetadata,
    approval_status: first(profile, "statusAprovacao") || "aprovado",
    vehicle_type: first(profile, "tipoVeiculo", "modalidade"),
    app_source: APP_SOURCE,
  };
  const values = {
    firebase_uid: firebaseUid,
    firebase_id: firebaseUid,
    name: first(profile, "nomeCompleto", "nome") || null,
    phone: first(profile, "whatsapp", "telefone") || null,
    metadata,
    updated_at: new Date().toISOString(),
  };
  const saved = await admin.from("couriers").upsert(values, { onConflict: "firebase_uid" }).select("*").single();
  if (saved.error) throw new Error(`courier_upsert_failed: ${saved.error.message}`);
  return saved.data;
}

const ORDER_SELECT = "id,firebase_id,customer_id,courier_id,order_code,status,fulfillment_type,delivery_fee,total,payment_method,payment_status,payment_details,delivery_address,customer_note,tracking_enabled,pickup_code,raw_payload,created_at,updated_at,accepted_at,completed_at,customers(name,phone)";

async function orderById(admin: any, id: string) {
  const result = await admin.from("orders").select(ORDER_SELECT).eq("id", id).maybeSingle();
  if (result.error) throw new Error(`order_lookup_failed: ${result.error.message}`);
  return result.data;
}

async function currentOrder(admin: any, courierId: string) {
  const result = await admin.from("orders").select(ORDER_SELECT).eq("courier_id", courierId).order("updated_at", { ascending: false }).limit(20);
  if (result.error) throw new Error(`current_order_failed: ${result.error.message}`);
  return (result.data || []).find((row: any) => !TERMINAL_STATUSES.has(text(row.status).toUpperCase())) || null;
}

async function offeredOrder(admin: any, firebaseUid: string) {
  const result = await admin.from("orders").select(ORDER_SELECT).is("courier_id", null).order("created_at", { ascending: true }).limit(30);
  if (result.error) throw new Error(`offer_lookup_failed: ${result.error.message}`);
  return (result.data || []).find((row: any) => {
    if (text(row.fulfillment_type).toUpperCase() !== "ENTREGA" && text(row.fulfillment_type).toUpperCase() !== "DELIVERY") return false;
    if (!OFFER_STATUSES.has(text(row.status).toUpperCase())) return false;
    const rejected = object(row.raw_payload)?.up_rejected_by;
    return !Array.isArray(rejected) || !rejected.includes(firebaseUid);
  }) || null;
}

async function event(admin: any, orderId: string, status: string, firebaseUid: string, note = "", raw: Record<string, any> = {}) {
  const inserted = await admin.from("order_status_events").insert({
    order_id: orderId,
    status,
    note: note || null,
    actor_type: "courier",
    actor_id: firebaseUid,
    source: APP_SOURCE,
    occurred_at: new Date().toISOString(),
    raw_payload: raw,
  });
  if (inserted.error) throw new Error(`event_insert_failed: ${inserted.error.message}`);
}

async function assignedOrder(admin: any, courier: any, missionId: string) {
  const row = await orderById(admin, missionId);
  if (!row) throw new Error("Entrega não encontrada.");
  if (row.courier_id !== courier.id) throw new Error("Esta entrega não está atribuída a você.");
  return row;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, apikey, content-type", "Access-Control-Allow-Methods": "POST, OPTIONS" } });
  if (req.method !== "POST") return json({ error: "method_not_allowed", message: "Método não permitido." }, 405);
  if (!SUPABASE_URL || !SERVICE_ROLE_KEY) return json({ error: "server_not_configured", message: "Servidor não configurado." }, 500);

  const token = bearer(req);
  if (!token) return json({ error: "firebase_token_required", message: "Faça login novamente." }, 401);
  let firebaseUid = "";
  try {
    const verified = await jwtVerify(token, FIREBASE_JWKS, { algorithms: ["RS256"], audience: FIREBASE_PROJECT_ID, issuer: FIREBASE_ISSUER, clockTolerance: 5 });
    firebaseUid = text(verified.payload?.sub);
  } catch {
    return json({ error: "invalid_firebase_token", message: "Sua sessão expirou. Entre novamente." }, 401);
  }
  if (!firebaseUid) return json({ error: "invalid_firebase_identity", message: "Conta inválida." }, 401);

  let body: Record<string, any>;
  try { body = await req.json(); } catch { return json({ error: "invalid_json", message: "Solicitação inválida." }, 400); }
  const action = text(body.action).toLowerCase();
  const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, { auth: { persistSession: false, autoRefreshToken: false } });

  try {
    const profileResult = await firestoreDocument("entregadores", firebaseUid, token);
    if (!profileResult.ok || !profileResult.data) return json({ error: "driver_profile_not_accessible", message: "Cadastro do entregador não encontrado." }, profileResult.status === 403 ? 403 : 404);
    if (!approvedDriver(profileResult.data)) return json({ error: "driver_not_approved", message: "Seu cadastro ainda não está liberado para entregas." }, 403);
    const courier = await ensureCourier(admin, firebaseUid, profileResult.data);
    const now = new Date().toISOString();

    if (action === "presence") {
      const online = bool(body.online);
      const metadata = { ...object(courier.metadata), app_version: text(body.app_version), last_presence_at: now };
      const updated = await admin.from("couriers").update({ online, status: online ? "available" : "offline", metadata, updated_at: now }).eq("id", courier.id);
      if (updated.error) throw new Error(`presence_update_failed: ${updated.error.message}`);
      return json({ ok: true });
    }

    if (action === "telemetry") {
      const metadata = { ...object(courier.metadata), battery_level: num(body.battery_level), charging: bool(body.charging), app_version: text(body.app_version), telemetry_at: now };
      const updated = await admin.from("couriers").update({ metadata, updated_at: now }).eq("id", courier.id);
      if (updated.error) throw new Error(`telemetry_update_failed: ${updated.error.message}`);
      return json({ ok: true });
    }

    if (action === "equipment") {
      const metadata = { ...object(courier.metadata), has_cash: bool(body.has_cash), cash_available: Math.max(0, num(body.cash_available)), has_machine: bool(body.has_machine), machine_types: text(body.machine_types), equipment_at: now };
      const updated = await admin.from("couriers").update({ metadata, updated_at: now }).eq("id", courier.id);
      if (updated.error) throw new Error(`equipment_update_failed: ${updated.error.message}`);
      return json({ ok: true });
    }

    if (action === "token") {
      const deviceToken = text(body.token);
      if (!deviceToken) return json({ error: "token_required", message: "Token de notificação ausente." }, 400);
      const saved = await admin.from("fcm_devices").upsert({ owner_type: "courier", owner_id: courier.id, app: "up_entregas", platform: "android", token: deviceToken, active: true, metadata: { firebase_uid: firebaseUid, app_version: text(body.app_version) }, last_seen_at: now, updated_at: now }, { onConflict: "token" });
      if (saved.error) throw new Error(`token_upsert_failed: ${saved.error.message}`);
      return json({ ok: true });
    }

    if (action === "offer") {
      const current = await currentOrder(admin, courier.id);
      if (current) return json({ ok: true, document: orderDocument(current, firebaseUid, false) });
      const offered = await offeredOrder(admin, firebaseUid);
      return json({ ok: true, document: offered ? orderDocument(offered, firebaseUid, true) : null });
    }

    if (action === "history") {
      const result = await admin.from("orders").select(ORDER_SELECT).eq("courier_id", courier.id).order("updated_at", { ascending: false }).limit(40);
      if (result.error) throw new Error(`history_failed: ${result.error.message}`);
      const documents = (result.data || []).filter((row: any) => TERMINAL_STATUSES.has(text(row.status).toUpperCase())).map((row: any) => orderDocument(row, firebaseUid, false));
      return json({ ok: true, documents });
    }

    const missionId = text(body.mission_id);
    if (!missionId) return json({ error: "mission_required", message: "Entrega não informada." }, 400);

    if (action === "mission") {
      const row = await orderById(admin, missionId);
      if (!row) return json({ error: "mission_not_found", message: "Entrega não encontrada." }, 404);
      const raw = object(row.raw_payload);
      const rejected = Array.isArray(raw.up_rejected_by) ? raw.up_rejected_by : [];
      const allowedOffer = !row.courier_id && OFFER_STATUSES.has(text(row.status).toUpperCase()) && !rejected.includes(firebaseUid);
      if (row.courier_id !== courier.id && !allowedOffer) return json({ error: "mission_forbidden", message: "Esta entrega não está disponível para você." }, 403);
      return json({ ok: true, document: orderDocument(row, firebaseUid, !row.courier_id) });
    }

    if (action === "accept") {
      const row = await orderById(admin, missionId);
      if (!row) return json({ error: "mission_not_found", message: "Entrega não encontrada." }, 404);
      if (row.courier_id && row.courier_id !== courier.id) return json({ error: "mission_taken", message: "Esta entrega já foi aceita por outro entregador." }, 409);
      if (!row.courier_id && !OFFER_STATUSES.has(text(row.status).toUpperCase())) return json({ error: "mission_not_available", message: "Esta entrega não está mais disponível." }, 409);
      const raw = { ...object(row.raw_payload), up_state: "TO_STORE", up_status: "ACEITA", up_delivery_status: "ENTREGADOR_A_CAMINHO_LOJA", up_driver_uid: firebaseUid, up_accepted_at: now, up_source: APP_SOURCE };
      let update = admin.from("orders").update({ courier_id: courier.id, raw_payload: raw, accepted_at: row.accepted_at || now, updated_at: now }).eq("id", missionId);
      if (!row.courier_id) update = update.is("courier_id", null);
      const saved = await update.select("id");
      if (saved.error) throw new Error(`accept_failed: ${saved.error.message}`);
      if (!saved.data?.length) return json({ error: "mission_taken", message: "Esta entrega acabou de ser aceita por outro entregador." }, 409);
      await admin.from("couriers").update({ status: "pickup", online: true, metadata: { ...object(courier.metadata), current_order_id: missionId, up_state: "TO_STORE" }, updated_at: now }).eq("id", courier.id);
      await event(admin, missionId, "ENTREGADOR_ACEITOU", firebaseUid, "Entrega aceita no UP Entregas.");
      return json({ ok: true });
    }

    if (action === "reject" || action === "expire") {
      const row = await orderById(admin, missionId);
      if (!row) return json({ ok: true });
      if (row.courier_id && row.courier_id !== courier.id) return json({ error: "mission_forbidden", message: "Esta entrega não está disponível para você." }, 403);
      if (row.courier_id === courier.id) return json({ error: "mission_already_accepted", message: "A entrega já foi aceita. Use a opção de ajuda." }, 409);
      const raw = object(row.raw_payload);
      const rejected = Array.isArray(raw.up_rejected_by) ? [...raw.up_rejected_by] : [];
      if (!rejected.includes(firebaseUid)) rejected.push(firebaseUid);
      const nextRaw = { ...raw, up_rejected_by: rejected, up_last_rejection_reason: action === "expire" ? "Oferta expirada" : text(body.reason), up_last_rejection_at: now };
      const saved = await admin.from("orders").update({ raw_payload: nextRaw, updated_at: now }).eq("id", missionId).is("courier_id", null);
      if (saved.error) throw new Error(`reject_failed: ${saved.error.message}`);
      await event(admin, missionId, action === "expire" ? "OFERTA_EXPIRADA" : "ENTREGADOR_RECUSOU", firebaseUid, action === "expire" ? "Tempo da oferta encerrado." : text(body.reason));
      return json({ ok: true });
    }

    const row = await assignedOrder(admin, courier, missionId);
    const raw = object(row.raw_payload);

    if (action === "stage") {
      const status = text(body.status).toUpperCase();
      const deliveryStatus = text(body.delivery_status).toUpperCase();
      const state = status === "COLETANDO" ? "AT_STORE" : status === "NO_CLIENTE" ? "AT_CUSTOMER" : status === "EM_ENTREGA" ? "TO_CUSTOMER" : "TO_STORE";
      const nextRaw: Record<string, any> = { ...raw, up_state: state, up_status: status, up_delivery_status: deliveryStatus, up_status_at: now, up_source: APP_SOURCE };
      const timestampField = text(body.timestamp_field);
      if (timestampField && /^[A-Za-z][A-Za-z0-9_]{0,63}$/.test(timestampField)) nextRaw[timestampField] = now;
      const saved = await admin.from("orders").update({ raw_payload: nextRaw, updated_at: now }).eq("id", missionId).eq("courier_id", courier.id);
      if (saved.error) throw new Error(`stage_failed: ${saved.error.message}`);
      await admin.from("couriers").update({ status: state === "TO_CUSTOMER" ? "delivery" : state === "AT_CUSTOMER" ? "at_customer" : "pickup", tracking_enabled: true, metadata: { ...object(courier.metadata), current_order_id: missionId, up_state: state }, updated_at: now }).eq("id", courier.id);
      await event(admin, missionId, deliveryStatus || status, firebaseUid, "Etapa atualizada pelo entregador.", { up_state: state });
      return json({ ok: true });
    }

    if (action === "pickup") {
      const expected = first(raw, "codigoRetirada", "pickupCode") || text(row.pickup_code);
      const entered = text(body.code);
      if (expected && expected !== entered) return json({ error: "invalid_pickup_code", message: "Código de retirada incorreto." }, 409);
      const nextRaw = { ...raw, up_state: "TO_CUSTOMER", up_status: "EM_ENTREGA", up_delivery_status: "SAIU_PARA_ENTREGA", up_pickup_at: now, up_source: APP_SOURCE };
      const saved = await admin.from("orders").update({ status: "DESPACHADO", raw_payload: nextRaw, dispatched_at: now, updated_at: now }).eq("id", missionId).eq("courier_id", courier.id);
      if (saved.error) throw new Error(`pickup_failed: ${saved.error.message}`);
      await admin.from("couriers").update({ status: "delivery", tracking_enabled: true, metadata: { ...object(courier.metadata), current_order_id: missionId, up_state: "TO_CUSTOMER" }, updated_at: now }).eq("id", courier.id);
      await event(admin, missionId, "SAIU_PARA_ENTREGA", firebaseUid, "Pedido retirado na loja.");
      return json({ ok: true });
    }

    if (action === "finish") {
      const expected = first(raw, "codigoEntrega", "deliveryCode");
      const entered = text(body.code);
      if (expected && expected !== entered) return json({ error: "invalid_delivery_code", message: "Código de entrega incorreto." }, 409);
      const received = Math.max(0, num(body.received_amount));
      const nextRaw = { ...raw, up_state: "DELIVERED", up_status: "ENTREGUE", up_delivery_status: "ENTREGUE", up_delivered_at: now, up_received_amount: received, up_delivery_code_confirmed: Boolean(entered), up_source: APP_SOURCE };
      const saved = await admin.from("orders").update({ status: "ENTREGUE", payment_details: { ...object(row.payment_details), received_by_courier: received, received_at: now }, raw_payload: nextRaw, completed_at: now, updated_at: now }).eq("id", missionId).eq("courier_id", courier.id);
      if (saved.error) throw new Error(`finish_failed: ${saved.error.message}`);
      await admin.from("couriers").update({ status: courier.online ? "available" : "offline", tracking_enabled: false, metadata: { ...object(courier.metadata), current_order_id: null, up_state: "DELIVERED" }, updated_at: now }).eq("id", courier.id);
      await event(admin, missionId, "ENTREGUE", firebaseUid, "Entrega concluída no UP Entregas.", { received_amount: received });
      return json({ ok: true });
    }

    if (action === "location") {
      const lat = num(body.lat), lng = num(body.lng);
      if (Math.abs(lat) > 90 || Math.abs(lng) > 180 || (lat === 0 && lng === 0)) return json({ error: "invalid_location", message: "Localização inválida." }, 400);
      const location = { lat, lng, accuracy: num(body.accuracy), speed: num(body.speed), bearing: num(body.bearing), visible_to_customer: bool(body.visible_to_customer), traveled_delivery_meters: num(body.traveled_delivery_meters), updated_at: now };
      const courierUpdate = await admin.from("couriers").update({ current_lat: lat, current_lng: lng, last_location_at: now, tracking_enabled: true, metadata: { ...object(courier.metadata), current_order_id: missionId, last_location: location }, updated_at: now }).eq("id", courier.id);
      if (courierUpdate.error) throw new Error(`courier_location_failed: ${courierUpdate.error.message}`);
      const point = await admin.from("tracking_points").insert({ order_id: missionId, courier_id: courier.id, lat, lng, accuracy_m: num(body.accuracy), speed_mps: num(body.speed), heading: num(body.bearing), recorded_at: now, raw_payload: { visible_to_customer: bool(body.visible_to_customer), traveled_delivery_meters: num(body.traveled_delivery_meters), source: APP_SOURCE } });
      if (point.error) throw new Error(`tracking_point_failed: ${point.error.message}`);
      const orderUpdate = await admin.from("orders").update({ raw_payload: { ...raw, up_location: location }, updated_at: now }).eq("id", missionId).eq("courier_id", courier.id);
      if (orderUpdate.error) throw new Error(`order_location_failed: ${orderUpdate.error.message}`);
      return json({ ok: true });
    }

    if (action === "occurrence") {
      const reason = text(body.reason) || "Ocorrência informada pelo entregador.";
      const nextRaw = { ...raw, up_occurrence_active: true, up_occurrence_reason: reason, up_occurrence_at: now };
      const saved = await admin.from("orders").update({ raw_payload: nextRaw, updated_at: now }).eq("id", missionId).eq("courier_id", courier.id);
      if (saved.error) throw new Error(`occurrence_failed: ${saved.error.message}`);
      await event(admin, missionId, "OCORRENCIA_ABERTA", firebaseUid, reason, { priority: "high" });
      return json({ ok: true });
    }

    return json({ error: "unknown_action", message: "Ação não reconhecida." }, 400);
  } catch (error) {
    console.error("[driver-api]", action, error);
    const message = error instanceof Error ? error.message : String(error);
    const userMessage = message.includes(":") ? "Não foi possível concluir agora. Tente novamente." : message;
    return json({ error: "driver_api_failed", message: userMessage }, 500);
  }
});
