package com.rodriguesacai.entregador;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CloudinaryUploader {
    public interface Callback { void onSuccess(String url); void onError(Exception e); }
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    // Preset público/unsigned já usado pelo GADM do Rodrigues.
    private static final String CLOUD = "dbd9x1o02";
    private static final String PRESET = "fc3i8urq";

    private CloudinaryUploader() {}

    public static void upload(Context context, Uri uri, String folder, Callback cb) {
        if (uri == null) { cb.onSuccess(""); return; }
        IO.execute(() -> {
            try {
                String boundary = "----UP" + System.currentTimeMillis();
                HttpURLConnection c = (HttpURLConnection) new URL("https://api.cloudinary.com/v1_1/" + CLOUD + "/image/upload").openConnection();
                c.setConnectTimeout(20000); c.setReadTimeout(45000); c.setDoOutput(true); c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream out = c.getOutputStream()) {
                    field(out,boundary,"upload_preset",PRESET);
                    field(out,boundary,"folder","rodrigues/up-entregas/" + folder);
                    field(out,boundary,"tags","rodrigues,up-entregas,cadastro");
                    out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"upload.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                        if (in == null) throw new IllegalStateException("Não foi possível abrir a imagem.");
                        byte[] buf = new byte[8192]; int n; while ((n=in.read(buf))>0) out.write(buf,0,n);
                    }
                    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
                    out.write(("--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int code = c.getResponseCode();
                InputStream body = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
                String text = read(body);
                if (code < 200 || code >= 300) throw new IllegalStateException("Cloudinary ("+code+"): "+text);
                String secure = new JSONObject(text).optString("secure_url","");
                if (secure.isEmpty()) throw new IllegalStateException("Cloudinary não retornou a URL da imagem.");
                context.getMainExecutor().execute(() -> cb.onSuccess(secure));
            } catch (Exception e) {
                context.getMainExecutor().execute(() -> cb.onError(e));
            }
        });
    }

    private static void field(OutputStream out,String boundary,String name,String value) throws Exception {
        out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n").getBytes(StandardCharsets.UTF_8));
    }
    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder b = new StringBuilder(); String line; while ((line=r.readLine())!=null) b.append(line); return b.toString();
        }
    }
}
