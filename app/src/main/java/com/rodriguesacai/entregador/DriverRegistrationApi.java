package com.rodriguesacai.entregador;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Ponte segura entre o cadastro Firebase do entregador e o Gestor Web/Supabase. */
public final class DriverRegistrationApi {
    private static final String ENDPOINT = "https://jgjmntezfjuyuxhcnvhd.supabase.co/functions/v1/driver-registration";
    private static final String PUBLISHABLE_KEY = "sb_publishable_fsub-d0wToVGTbfDoATS7A_NWaimMvX";
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);

    public Task<String> submit(Map<String, String> values) {
        JSONObject profile = new JSONObject();
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                try { profile.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue()); }
                catch (Exception ignored) {}
            }
        }
        JSONObject body = new JSONObject();
        put(body, "action", "submit");
        put(body, "profile", profile);
        return call(body).continueWith(task -> {
            if (!task.isSuccessful()) throw task.getException();
            return task.getResult().optString("status", "pending");
        });
    }

    public Task<Void> updateDocuments(String photoUrl, String documentUrl) {
        JSONObject body = new JSONObject();
        put(body, "action", "documents_update");
        put(body, "photo_url", photoUrl == null ? "" : photoUrl);
        put(body, "document_url", documentUrl == null ? "" : documentUrl);
        return call(body).continueWith(task -> {
            if (!task.isSuccessful()) throw task.getException();
            return null;
        });
    }

    public Task<String> syncStatus() {
        JSONObject body = new JSONObject();
        put(body, "action", "status_sync");
        return call(body).continueWith(task -> {
            if (!task.isSuccessful()) throw task.getException();
            return task.getResult().optString("status", "missing");
        });
    }

    private Task<JSONObject> call(JSONObject body) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return com.google.android.gms.tasks.Tasks.forException(
                new IllegalStateException("Faça login novamente."));

        TaskCompletionSource<JSONObject> source = new TaskCompletionSource<>();
        user.getIdToken(false).addOnSuccessListener(result -> IO.execute(() -> {
            HttpURLConnection connection = null;
            try {
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
                if (status < 200 || status >= 300 || !json.optBoolean("ok", false)) {
                    throw new IllegalStateException(json.optString("error", "Falha ao sincronizar cadastro."));
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
        try { object.put(key, value == null ? JSONObject.NULL : value); }
        catch (Exception ignored) {}
    }
}
