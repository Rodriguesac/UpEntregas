package com.rodriguesacai.updriver;

import android.os.Handler;
import android.os.Looper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Authenticated, single-purpose client for UP's independent server only. */
public final class Backend {
    public interface Callback {
        void onSuccess(JSONObject response);
        void onError(String message);
    }
    private static final String PROJECT_HOST = "gkwmqwtktedarodvqqtn.supabase.co";
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public void driver(String action, JSONObject payload, Callback cb) {
        request(BuildConfig.UP_DRIVER_ENDPOINT, action, payload, cb);
    }

    public void registration(String action, JSONObject payload, Callback cb) {
        request(BuildConfig.UP_REGISTRATION_ENDPOINT, action, payload, cb);
    }

    public static JSONObject args(Object... pairs) {
        JSONObject out = new JSONObject();
        if (pairs != null) for (int i = 0; i + 1 < pairs.length; i += 2) {
            try { out.put(String.valueOf(pairs[i]), pairs[i+1]); }
            catch (Exception ignored) {}
        }
        return out;
    }

    private void request(String endpoint, String action, JSONObject payload, Callback cb) {
        try {
            URL url = new URL(endpoint);
            if (!"https".equals(url.getProtocol()) || !PROJECT_HOST.equals(url.getHost())
                 || !url.getPath().startsWith("/functions/v1/")) {
                error(cb, "Destino de API inválido. O UP nunca deve acessar o banco da loja.");
                return;
            }
        } catch (Exception ex) {
            error(cb, "Endereço de conexão inválido.");
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            error(cb, "Faça login para continuar.");
            return;
        }
        user.getIdToken(false)
            .addOnSuccessListener(result -> IO.execute(() -> {
                HttpURLConnection con = null;
                try {
                    JSONObject body = payload == null ? new JSONObject()
                        : new JSONObject(payload.toString());
                    body.put("action", action);
                    byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                    if (bytes.length > 8192) throw new IllegalStateException("Dados muito grandes.");
                    con = (HttpURLConnection)new URL(endpoint).openConnection();
                    con.setRequestMethod("POST");
                    con.setConnectTimeout(9000);
                    con.setReadTimeout(12000);
                    con.setDoOutput(true);
                    con.setRequestProperty("Content-Type", "application/json");
                    con.setRequestProperty("Accept", "application/json");
                    con.setRequestProperty("apikey", BuildConfig.UP_PUBLISHABLE_KEY);
                    con.setRequestProperty("Authorization", "Bearer " + result.getToken());
                    con.setFixedLengthStreamingMode(bytes.length);
                    try(OutputStream os = con.getOutputStream()) { os.write(bytes); }
                    int code = con.getResponseCode();
                    InputStream in = code >= 200 && code < 300
                        ? con.getInputStream() : con.getErrorStream();
                    String raw = read(in);
                    JSONObject response = raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
                    if (code < 200 || code >= 300 || !response.optBoolean("ok", false)) {
                        error(cb, explain(response.optString("error", "Erro de conexão.")));
                        return;
                    }
                    MAIN.post(() -> cb.onSuccess(response));
                } catch (Exception ex) {
                    error(cb, "Não foi possível comunicar com o UP. Confira a internet e tente novamente.");
                } finally {
                    if(con != null) con.disconnect();
                }
            }))
            .addOnFailureListener(ex -> error(cb, "Sessão expirada. Entre novamente."));
    }

    private static String read(InputStream stream) throws Exception {
        if(stream == null) return "";
        try(InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[4096]; int n;
            while((n = in.read(b)) != -1) {
                if(out.size() + n > 16384) throw new IllegalStateException("Resposta muito grande.");
                out.write(b,0,n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void error(Callback cb,String msg) {
        MAIN.post(() -> cb.onError(msg));
    }

    public static String explain(String code) {
        switch(code) {
            case "independent_driver_disabled": return "As corridas independentes ainda não foram liberadas.";
            case "independent_registration_disabled": return "O cadastro independente está temporariamente indisponível.";
            case "driver_not_approved": case "courier_not_approved": return "O gestor precisa aprovar seu cadastro.";
            case "firebase_token_required": case "invalid_firebase_token": return "Sessão expirada. Entre novamente.";
            case "offer_not_found": case "offer_expired_or_used": return "Esta oferta expirou ou já foi utilizada.";
            case "job_unavailable": return "A corrida já não está disponível.";
            case "invalid_driver_transition": return "Esta etapa não pode ser confirmada agora. Atualize a corrida.";
            case "battery_below_minimum": return "Bateria abaixo de 15%. Recarregue antes de aceitar novas corridas.";
            case "incorrect_confirmation_code": return "Código de confirmação incorreto.";
            case "collection_not_confirmed": return "Informe o valor recebido antes de concluir.";
            case "mission_not_authorized": return "A corrida não está vinculada à sua conta.";
            case "location_not_allowed": return "GPS autorizado somente em uma corrida ativa.";
            case "name_and_phone_required": return "Informe nome completo e telefone válido.";
            case "registration_failed": return "Cadastro indisponível. Tente novamente.";
            default: return "Falha na operação do UP (" + code.replaceAll("[^a-zA-Z0-9_-]", "") + ").";
        }
    }
}
