
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.56.1";
import { createRemoteJWKSet, jwtVerify } from "npm:jose@5.9.6";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") || "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
const FIREBASE_PROJECT_ID = "rodrigues-d6566";
const FIREBASE_ISSUER = "https://securetoken.google.com/" + FIREBASE_PROJECT_ID;
const FIREBASE_JWKS = createRemoteJWKSet(new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"));

const headers = {
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS"
};

const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers });
const text = (v) => String(v ?? "").trim();
const obj = (v) => v && typeof v === "object" && !Array.isArray(v) ? v : {};
const bearer = (req) => text(req.headers.get("authorization")).replace(/^Bearer\\s+/i, "");

function approvalFromFirestore(profile) {
  const status = text(profile.statusAprovacao || profile.statusCadastro || profile.situacao).toUpperCase();
  if (profile.ativo === true && profile.aprovado === true &&
      (!status || ["APROVADO","ATIVO","LIBERADO"].includes(status))) return "approved";
  if (status.includes("CORRE")) return "correction";
  if (status.includes("BLOQ")) return "blocked";
  if (status.includes("REPRO") || status.includes("REJEIT")) return "rejected";
  return "pending";
}

function decodeFirestoreValue(value) {
  if (!value || typeof value !== "object") return null;
  if ("nullValue" in value) return null;
  if ("stringValue" in value) return value.stringValue;
  if ("integerValue" in value) return Number(value.integerValue);
  if ("doubleValue" in value) return Number(value.doubleValue);
  if ("booleanValue" in value) return Boolean(value.booleanValue);
  if ("timestampValue" in value) return value.timestampValue;
  if ("arrayValue" in value) return (value.arrayValue?.values || []).map(decodeFirestoreValue);
  if ("mapValue" in value) {
    return Object.fromEntries(Object.entries(value.mapValue?.fields || {}).map(([k,v]) => [k, decodeFirestoreValue(v)]));
  }
  return null;
}

function decodeFirestoreFields(fields) {
  return Object.fromEntries(Object.entries(fields || {}).map(([k,v]) => [k, decodeFirestoreValue(v)]));
}

async function readFirestore(uid, token) {
  const url = "https://firestore.googleapis.com/v1/projects/" + FIREBASE_PROJECT_ID +
    "/databases/(default)/documents/entregadores/" + encodeURIComponent(uid);
  const r = await fetch(url, { headers: { Authorization: "Bearer " + token }, cache: "no-store" });
  if (!r.ok) return null;
  const body = await r.json();
  return decodeFirestoreFields(body?.fields || {});
}

function mapApplication(profile, uid, email) {
  return {
    firebase_uid: uid,
    full_name: text(profile.nomeCompleto || profile.nome) || null,
    email: email || text(profile.email) || null,
    phone: text(profile.telefone || profile.whatsapp) || null,
    cpf: text(profile.cpf) || null,
    birth_date: text(profile.nascimento) || null,
    address: {
      cep: text(profile.cep),
      rua: text(profile.rua),
      numero: text(profile.numero),
      bairro: text(profile.bairro),
      cidade: text(profile.cidade)
    },
    vehicle: {
      tipo: text(profile.tipoVeiculo),
      marca: text(profile.marcaVeiculo),
      modelo: text(profile.modeloVeiculo),
      cor: text(profile.corVeiculo),
      placa: text(profile.placa)
    },
    pix: {
      tipo: text(profile.pixTipo),
      chave: text(profile.pixChave),
      titular: text(profile.pixTitular)
    },
    metadata: {
      source: "UP_ENTREGAS_ANDROID",
      app_version: text(profile.appVersionCadastro || profile.appVersion),
      migrated_backend: "fdqqwdplprzpqufpgdrm"
    },
    submitted_at: new Date().toISOString(),
    updated_at: new Date().toISOString()
  };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers });
  if (req.method !== "POST") return json({ ok:false, error:"method_not_allowed" }, 405);
  if (!SUPABASE_URL || !SERVICE_ROLE_KEY) return json({ ok:false, error:"server_not_configured" }, 500);

  const token = bearer(req);
  if (!token) return json({ ok:false, error:"firebase_token_required" }, 401);

  let uid = "";
  let email = "";
  try {
    const verified = await jwtVerify(token, FIREBASE_JWKS, {
      algorithms:["RS256"],
      audience:FIREBASE_PROJECT_ID,
      issuer:FIREBASE_ISSUER,
      clockTolerance:5
    });
    uid = text(verified.payload?.sub);
    email = text(verified.payload?.email);
  } catch {
    return json({ ok:false, error:"invalid_firebase_token" }, 401);
  }
  if (!uid) return json({ ok:false, error:"invalid_firebase_identity" }, 401);

  let body = {};
  try { body = await req.json(); } catch {}
  const action = text(body.action).toLowerCase();
  const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, { auth:{ persistSession:false, autoRefreshToken:false } });

  try {
    if (action === "submit") {
      const profile = obj(body.profile);
      const existing = await admin.from("courier_applications").select("status").eq("firebase_uid", uid).maybeSingle();
      if (existing.error) throw existing.error;
      const mapped = mapApplication(profile, uid, email);
      const current = text(existing.data?.status).toLowerCase();
      mapped.status = current === "approved" ? "correction" : "pending";
      const saved = await admin.from("courier_applications")
        .upsert(mapped, { onConflict:"firebase_uid" }).select("id,status").single();
      if (saved.error) throw saved.error;
      return json({ ok:true, status:saved.data.status, id:saved.data.id });
    }

    if (action === "documents_update") {
      const found = await admin.from("courier_applications").select("documents").eq("firebase_uid", uid).maybeSingle();
      if (found.error) throw found.error;
      if (!found.data) return json({ ok:false, error:"application_not_found" }, 404);
      const documents = {
        ...obj(found.data.documents),
        photo_url: text(body.photo_url),
        document_url: text(body.document_url)
      };
      const updated = await admin.from("courier_applications")
        .update({ documents, updated_at:new Date().toISOString() }).eq("firebase_uid", uid);
      if (updated.error) throw updated.error;
      return json({ ok:true });
    }

    if (action === "status_sync") {
      const profile = await readFirestore(uid, token);
      const status = profile ? approvalFromFirestore(profile) : "missing";
      if (profile) {
        const found = await admin.from("courier_applications").select("metadata").eq("firebase_uid", uid).maybeSingle();
        if (found.error) throw found.error;
        if (found.data) {
          const metadata = {
            ...obj(found.data.metadata),
            firestore_status: text(profile.statusAprovacao || profile.statusCadastro),
            status_synced_at: new Date().toISOString()
          };
          const updated = await admin.from("courier_applications")
            .update({ status, metadata, updated_at:new Date().toISOString() }).eq("firebase_uid", uid);
          if (updated.error) throw updated.error;
        }
      }
      return json({ ok:true, status });
    }

    return json({ ok:false, error:"unknown_action" }, 400);
  } catch (error) {
    console.error("[driver-registration]", action, error);
    return json({ ok:false, error:error instanceof Error ? error.message : String(error) }, 500);
  }
});
