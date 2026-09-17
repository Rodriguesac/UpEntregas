package com.rodriguesacai.entregador;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Documento operacional independente do provedor.
 *
 * Mantém a API pequena usada pelas telas do UP e permite que uma missão venha
 * tanto do Firestore legado quanto do Supabase, sem duplicar a interface.
 */
public final class UpDocument {
    private final String id;
    private final Map<String, Object> data;
    private final DocumentSnapshot firestore;
    private final boolean exists;

    private UpDocument(String id, Map<String, Object> data, DocumentSnapshot firestore, boolean exists) {
        this.id = id == null ? "" : id;
        this.data = data == null ? new HashMap<>() : new HashMap<>(data);
        this.firestore = firestore;
        this.exists = exists;
    }

    public static UpDocument fromFirestore(DocumentSnapshot doc) {
        if (doc == null) return null;
        return new UpDocument(doc.getId(), doc.getData(), doc, doc.exists());
    }

    public static UpDocument fromJson(JSONObject envelope) {
        if (envelope == null) return null;
        String id = envelope.optString("id", "");
        JSONObject object = envelope.optJSONObject("data");
        if (object == null) object = envelope;
        Map<String, Object> values = jsonObjectToMap(object);
        if (id.isEmpty()) id = String.valueOf(values.remove("_id"));
        return new UpDocument(id, values, null, !id.isEmpty());
    }

    public static UpDocument of(String id, Map<String, Object> data) {
        return new UpDocument(id, data, null, id != null && !id.isEmpty());
    }

    public String getId() { return id; }

    public boolean exists() { return exists; }

    public boolean isSupabase() { return firestore == null && id.startsWith(SupabaseDriverApi.ID_PREFIX); }

    DocumentSnapshot firestore() { return firestore; }

    public Map<String, Object> getData() { return Collections.unmodifiableMap(data); }

    public Object get(String path) {
        if (path == null || path.isEmpty()) return null;
        Object current = data;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map)) return null;
            current = ((Map<?, ?>) current).get(part);
            if (current == null) return null;
        }
        return current;
    }

    public String getString(String path) {
        Object value = get(path);
        return value instanceof String ? (String) value : null;
    }

    public Boolean getBoolean(String path) {
        Object value = get(path);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String && ("true".equalsIgnoreCase((String) value) || "false".equalsIgnoreCase((String) value)))
            return Boolean.valueOf((String) value);
        return null;
    }

    public Long getLong(String path) {
        Object value = get(path);
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) try { return Long.parseLong((String) value); } catch (Exception ignored) {}
        return null;
    }

    public Double getDouble(String path) {
        Object value = get(path);
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) try { return Double.parseDouble(((String) value).replace(',', '.')); } catch (Exception ignored) {}
        return null;
    }

    public Timestamp getTimestamp(String path) {
        Object value = get(path);
        if (value instanceof Timestamp) return (Timestamp) value;
        if (value instanceof Date) return new Timestamp((Date) value);
        if (value instanceof String) {
            try { return new Timestamp(Date.from(Instant.parse((String) value))); } catch (Exception ignored) {}
        }
        return null;
    }

    static Map<String, Object> jsonObjectToMap(JSONObject object) {
        HashMap<String, Object> out = new HashMap<>();
        if (object == null) return out;
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = object.opt(key);
            out.put(key, jsonValue(value));
        }
        return out;
    }

    private static Object jsonValue(Object value) {
        if (value == null || value == JSONObject.NULL) return null;
        if (value instanceof JSONObject) return jsonObjectToMap((JSONObject) value);
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) out.add(jsonValue(array.opt(i)));
            return out;
        }
        return value;
    }
}
