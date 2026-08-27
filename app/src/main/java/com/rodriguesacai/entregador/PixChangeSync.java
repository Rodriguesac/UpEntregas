package com.rodriguesacai.entregador;

import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ponte financeira do UP Entregas para o Supabase.
 *
 * O app NÃO informa ao Supabase "confie que recebi". Ele apenas pede uma
 * reconciliação. O backend valida o ID token Firebase e relê a própria missão
 * no Firestore, sob as Security Rules do entregador, antes de liberar o troco Pix.
 */
public final class PixChangeSync {
    private static final String TAG = "PixChangeSync";
    private static final String ENDPOINT = "https://jgjmntezfjuyuxhcnvhd.supabase.co/functions/v1/driver-cash-confirm";
    private static final String PUBLISHABLE_KEY = "sb_publishable_fsub-d0wToVGTbfDoATS7A_NWaimMvX";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Set<String> seen = new HashSet<>();
    private static ListenerRegistration ridesListener;
    private static ListenerRegistration routesListener;
    private static FirebaseAuth.AuthStateListener authListener;
    private static boolean started;

    private PixChangeSync() {}

    public static synchronized void start() {
        if (started) return;
        started = true;
        FirebaseAuth auth = FirebaseAuth.getInstance();
        authListener = firebaseAuth -> attach(firebaseAuth.getCurrentUser());
        auth.addAuthStateListener(authListener);
    }

    private static synchronized void attach(FirebaseUser user) {
        clearListeners();
        seen.clear();
        if (user == null) return;
        String uid = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        ridesListener = db.collection("rides")
                .whereEqualTo("entregadorId", uid)
                .limit(30)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        Log.w(TAG, "rides listener", error);
                        return;
                    }
                    if (snap == null) return;
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        if (rideHasConfirmedCash(doc, uid)) sync("rides", doc, user);
                    }
                });

        routesListener = db.collection("rotas_entrega")
                .whereEqualTo("entregadorId", uid)
                .limit(30)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        Log.w(TAG, "routes listener", error);
                        return;
                    }
                    if (snap == null) return;
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        if (routeHasConfirmedCash(doc, uid)) sync("rotas_entrega", doc, user);
                    }
                });
    }

    private static boolean rideHasConfirmedCash(DocumentSnapshot doc, String uid) {
        if (doc == null || !doc.exists()) return false;
        String assigned = first(doc, "entregadorId", "driverId", "uidEntregador", "entregadorUid");
        if (!uid.equals(assigned)) return false;
        String status = (first(doc, "status", "statusCorrida") + " " + first(doc, "statusEntrega") + " " + first(doc, "upState")).toUpperCase();
        if (!(status.contains("ENTREGUE") || status.contains("CONCLUID") || status.contains("FINALIZAD") || status.contains("DELIVERED"))) return false;
        Object raw = doc.get("pagamentoRecebidoPeloEntregador");
        if (!(raw instanceof Map)) return false;
        Object amount = ((Map<?, ?>) raw).get("recebidoPeloEntregador");
        return number(amount) > 0d;
    }

    private static boolean routeHasConfirmedCash(DocumentSnapshot doc, String uid) {
        if (doc == null || !doc.exists()) return false;
        String assigned = first(doc, "entregadorId", "driverId", "uidEntregador", "entregadorUid");
        if (!uid.equals(assigned)) return false;
        Object raw = doc.get("paradas");
        if (!(raw instanceof List)) return false;
        for (Object item : (List<?>) raw) {
            if (!(item instanceof Map)) continue;
            Map<?, ?> stop = (Map<?, ?>) item;
            boolean delivered = Boolean.TRUE.equals(stop.get("entregue")) || "ENTREGUE".equalsIgnoreCase(String.valueOf(stop.get("status")));
            if (delivered && number(stop.get("recebidoPeloEntregador")) > 0d) return true;
        }
        return false;
    }

    private static void sync(String collection, DocumentSnapshot doc, FirebaseUser user) {
        String signature = collection + ":" + doc.getId() + ":" + updateSignature(doc);
        synchronized (PixChangeSync.class) {
            if (!seen.add(signature)) return;
        }
        user.getIdToken(false)
                .addOnSuccessListener(result -> IO.execute(() -> post(collection, doc.getId(), result.getToken(), signature)))
                .addOnFailureListener(error -> {
                    synchronized (PixChangeSync.class) { seen.remove(signature); }
                    Log.w(TAG, "token", error);
                });
    }

    private static void post(String collection, String missionId, String firebaseToken, String signature) {
        HttpURLConnection connection = null;
        try {
            JSONObject payload = new JSONObject();
            payload.put("mission_type", collection);
            payload.put("mission_id", missionId);
            byte[] data = payload.toString().getBytes(StandardCharsets.UTF_8);

            connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + firebaseToken);
            connection.setRequestProperty("apikey", PUBLISHABLE_KEY);
            connection.setFixedLengthStreamingMode(data.length);
            try (OutputStream out = connection.getOutputStream()) { out.write(data); }

            int status = connection.getResponseCode();
            if (status >= 200 && status < 300) {
                Log.i(TAG, "cash confirmation reconciled for " + collection + "/" + missionId);
                return;
            }
            // 409 normalmente significa que não havia troco Pix pendente ou que ainda
            // não existe confirmação suficiente. Mantém elegível para uma próxima versão do documento.
            if (status >= 500 || status == 401 || status == 403) {
                synchronized (PixChangeSync.class) { seen.remove(signature); }
            }
            Log.w(TAG, "sync rejected: HTTP " + status + " for " + collection + "/" + missionId);
        } catch (Exception error) {
            synchronized (PixChangeSync.class) { seen.remove(signature); }
            Log.w(TAG, "sync failed", error);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String updateSignature(DocumentSnapshot doc) {
        Timestamp ts = doc.getTimestamp("updatedAt");
        if (ts == null) ts = doc.getTimestamp("statusAtualizadoEm");
        if (ts != null) return ts.getSeconds() + "." + ts.getNanoseconds();
        return String.valueOf(doc.getData() == null ? 0 : doc.getData().hashCode());
    }

    private static String first(DocumentSnapshot doc, String... fields) {
        for (String field : fields) {
            Object value = doc.get(field);
            if (value == null) continue;
            String text = String.valueOf(value).trim();
            if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) return text;
        }
        return "";
    }

    private static double number(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try { return Double.parseDouble(((String) value).replace(',', '.')); } catch (Exception ignored) {}
        }
        return 0d;
    }

    private static synchronized void clearListeners() {
        if (ridesListener != null) { ridesListener.remove(); ridesListener = null; }
        if (routesListener != null) { routesListener.remove(); routesListener = null; }
    }
}