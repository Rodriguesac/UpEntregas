package com.rodriguesacai.entregador;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cliente autenticado da API operacional do entregador no Supabase. */
public final class SupabaseDriverApi {
    public static final String ID_PREFIX = "supabase:";
    private static final String ENDPOINT = "https://jgjmntezfjuyuxhcnvhd.supabase.co/functions/v1/driver-api";
    private static final String PUBLISHABLE_KEY = "sb_publishable_fsub-d0wToVGTbfDoATS7A_NWaimMvX";
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);

    public Task<UpDocument> offer() { return documentCall("offer", new JSONObject()); }

    public Task<UpDocument> mission(String missionId) {
        JSONObject body = new JSONObject();
        put(body, "mission_id", rawId(missionId));
        return documentCall("mission", body);
    }

    public Task<UpQuery> history() {
        return call("history", new JSONObject()).continueWith(task -> {
            if (!task.isSuccessful()) throw task.getException();
            JSONArray rows = task.getResult().optJSONArray("documents");
            ArrayList<UpDocument> out = new ArrayList<>();
            if (rows != null) {
                for (int i = 0; i < rows.length(); i++) {
                    UpDocument doc = UpDocument.fromJson(rows.optJSONObject(i));
                    if (doc != null && doc.exists()) out.add(doc);
                }
            }
            return new UpQuery(out);
        });
    }

    public Task<Void> presence(boolean online) {
        JSONObject body = new JSONObject();
        put(body, "online", online);
        put(body, "app_version", BuildConfig.VERSION_NAME);
        return voidCall("presence", body);
    }

    public Task<Void> telemetry(int batteryLevel, boolean charging) {
        JSONObject body = new JSONObject();
        put(body, "battery_level", batteryLevel);
        put(body, "charging", charging);
        put(body, "app_version", BuildConfig.VERSION_NAME);
        return voidCall("telemetry", body);
    }

    public Task<Void> equipment(boolean hasCash, double cashAvailable, boolean hasMachine, String machineTypes) {
        JSONObject body = new JSONObject();
        put(body, "has_cash", hasCash);
        put(body, "cash_available", Math.max(0d, cashAvailable));
        put(body, "has_machine", hasMachine);
        put(body, "machine_types", machineTypes == null ? "" : machineTypes.trim());
        return voidCall("equipment", body);
    }

    public Task<Void> token(String token) {
        JSONObject body = new JSONObject();
        put(body, "token", token);
        put(body, "app_version", BuildConfig.VERSION_NAME);
        return voidCall("token", body);
    }

    public Task<Void> accept(String missionId) { return missionVoid("accept", missionId, new JSONObject()); }

    public Task<Void> reject(String missionId, String reason) {
        JSONObject body = new JSONObject();
        put(body, "reason", reason);
        return missionVoid("reject", missionId, body);
    }

    public Task<Void> expire(String missionId) { return missionVoid("expire", missionId, new JSONObject()); }

    public Task<Void> stage(String missionId, String status, String deliveryStatus, String timestampField) {
        JSONObject body = new JSONObject();
        put(body, "status", status);
        put(body, "delivery_status", deliveryStatus);
        put(body, "timestamp_field", timestampField);
        return missionVoid("stage", missionId, body);
    }

    public Task<Void> pickup(String missionId, String code) {
        JSONObject body = new JSONObject();
        put(body, "code", code);
        return missionVoid("pickup", missionId, body);
    }

    public Task<Void> finish(String missionId, String code, double receivedAmount) {
        JSONObject body = new JSONObject();
        put(body, "code", code);
        put(body, "received_amount", Math.max(0d, receivedAmount));
        return missionVoid("finish", missionId, body);
    }

    public Task<Void> occurrence(String missionId, String reason) {
        JSONObject body = new JSONObject();
        put(body, "reason", reason);
        return missionVoid("occurrence", missionId, body);
    }

    public Task<Void> location(String missionId, double lat, double lng, float accuracy, float speed,
                               float bearing, boolean visibleToCustomer, double traveledDeliveryMeters) {
        JSONObject body = new JSONObject();
        put(body, "mission_id", rawId(missionId));
        put(body, "lat", lat);
        put(body, "lng", lng);
        put(body, "accuracy", accuracy);
        put(body, "speed", speed);
        put(body, "bearing", bearing);
        put(body, "visible_to_customer", visibleToCustomer);
        put(body, "traveled_delivery_meters", traveledDeliveryMeters);
        return voidCall("location", body);
    }

    private Task<Void> missionVoid(String action, String missionId, JSONObject body) {
        put(body, "mission_id", rawId(missionId));
        return voidCall(action, body);
    }

    private Task<Void> voidCall(String action, JSONObject body) {
        return call(action, body).continueWith(task -> {
            if (!task.isSuccessful()) throw task.getException();
            return null;
        });
    }

    private Task<UpDocument> documentCall(String action, JSONObject body) {
        return call(action, body).continueWith(task -> {
            if (!task.isSuccessful()) throw task.getException();
            JSONObject document = task.getResult().optJSONObject("document");
            return document == null ? null : UpDocument.fromJson(document);
        });
    }

    private Task<JSONObject> call(String action, JSONObject payload) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return Tasks.forException(new IllegalStateException("Faça login novamente."));
        TaskCompletionSource<JSONObject> source = new TaskCompletionSource<>();
        user.getIdToken(false).addOnSuccessListener(result -> IO.execute(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject body = new JSONObject(payload.toString());
                body.put("action", action);
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(9000);
                connection.setReadTimeout(12000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + result.getToken());
                connection.setRequestProperty("apikey", PUBLISHABLE_KEY);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
                int status = connection.getResponseCode();
                InputStream input = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                String response = read(input);
                JSONObject json = response.isEmpty() ? new JSONObject() : new JSONObject(response);
                if (status < 200 || status >= 300) {
                    String message = json.optString("message", json.optString("error", "Falha na operação."));
                    throw new IllegalStateException(message);
                }
                source.setResult(json);
            } catch (Exception error) {
                source.setException(error);
            } finally {
                if (connection != null) connection.disconnect();
            }
        })).addOnFailureListener(source::setException);
        return source.getTask();
    }

    private static String rawId(String id) {
        if (id == null) return "";
        return id.startsWith(ID_PREFIX) ? id.substring(ID_PREFIX.length()) : id;
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "";
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) >= 0) out.write(buffer, 0, count);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void put(JSONObject object, String key, Object value) {
        try { object.put(key, value == null ? JSONObject.NULL : value); } catch (Exception ignored) {}
    }
}
