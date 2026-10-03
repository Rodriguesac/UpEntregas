package com.rodriguesacai.entregador;

import android.location.Location;

import java.util.Locale;

/**
 * Política local de rastreamento.
 *
 * O GPS pode continuar ativo no aparelho durante a missão, mas a posição só é
 * enviada ao backend quando a política realmente exigir. Isso reduz chamadas,
 * gravações e transferência no Supabase sem prejudicar o acompanhamento.
 *
 * A configuração vem do campo trackingConfig devolvido pela API e pode ser
 * alterada futuramente pelo Gerenciador UP através de couriers.metadata.tracking_config.
 */
final class TrackingPolicy {
    private String mode = "dynamic";
    private long manualIntervalMs = 30_000L;
    private float manualMinDistanceM = 50f;

    private long beforePickupIntervalMs = 45_000L;
    private float beforePickupMinDistanceM = 50f;
    private long deliveryMovingIntervalMs = 20_000L;
    private float deliveryMovingMinDistanceM = 20f;
    private long deliveryFastIntervalMs = 15_000L;
    private float deliveryFastMinDistanceM = 35f;
    private long stationaryIntervalMs = 60_000L;
    private float stationaryMinDistanceM = 15f;
    private long heartbeatMs = 90_000L;

    private long lastUploadAt = 0L;
    private double lastLat = Double.NaN;
    private double lastLng = Double.NaN;

    void apply(UpDocument mission) {
        if (mission == null) return;
        String remoteMode = string(mission, "trackingConfig.mode", "trackingMode", "upTrackingMode");
        if (!remoteMode.isEmpty()) {
            remoteMode = remoteMode.toLowerCase(Locale.ROOT);
            if ("manual".equals(remoteMode) || "dynamic".equals(remoteMode)) mode = remoteMode;
        }

        manualIntervalMs = seconds(mission, manualIntervalMs, 10, 300,
                "trackingConfig.manual_interval_seconds", "trackingIntervalSeconds");
        manualMinDistanceM = meters(mission, manualMinDistanceM, 5, 500,
                "trackingConfig.manual_min_distance_m", "trackingMinDistanceMeters");

        beforePickupIntervalMs = seconds(mission, beforePickupIntervalMs, 15, 300,
                "trackingConfig.before_pickup_interval_seconds");
        beforePickupMinDistanceM = meters(mission, beforePickupMinDistanceM, 5, 500,
                "trackingConfig.before_pickup_min_distance_m");

        deliveryMovingIntervalMs = seconds(mission, deliveryMovingIntervalMs, 10, 180,
                "trackingConfig.delivery_moving_interval_seconds");
        deliveryMovingMinDistanceM = meters(mission, deliveryMovingMinDistanceM, 5, 300,
                "trackingConfig.delivery_moving_min_distance_m");

        deliveryFastIntervalMs = seconds(mission, deliveryFastIntervalMs, 10, 120,
                "trackingConfig.delivery_fast_interval_seconds");
        deliveryFastMinDistanceM = meters(mission, deliveryFastMinDistanceM, 5, 300,
                "trackingConfig.delivery_fast_min_distance_m");

        stationaryIntervalMs = seconds(mission, stationaryIntervalMs, 30, 300,
                "trackingConfig.stationary_interval_seconds");
        stationaryMinDistanceM = meters(mission, stationaryMinDistanceM, 5, 100,
                "trackingConfig.stationary_min_distance_m");

        heartbeatMs = seconds(mission, heartbeatMs, 45, 600,
                "trackingConfig.heartbeat_seconds");
    }

    boolean shouldUpload(Location location, boolean deliveryPhase, long now) {
        if (location == null) return false;
        if (lastUploadAt <= 0 || Double.isNaN(lastLat) || Double.isNaN(lastLng)) return true;

        long elapsed = Math.max(0L, now - lastUploadAt);
        float distance = distanceFromLast(location);

        if ("manual".equals(mode)) {
            if (elapsed < manualIntervalMs) return false;
            if (distance >= manualMinDistanceM) return true;
            return elapsed >= Math.max(heartbeatMs, manualIntervalMs * 3L);
        }

        long interval;
        float minimumDistance;
        float speed = location.hasSpeed() ? Math.max(0f, location.getSpeed()) : 0f;

        if (!deliveryPhase) {
            interval = beforePickupIntervalMs;
            minimumDistance = beforePickupMinDistanceM;
        } else if (speed >= 8f) {
            interval = deliveryFastIntervalMs;
            minimumDistance = deliveryFastMinDistanceM;
        } else if (speed >= 1.5f) {
            interval = deliveryMovingIntervalMs;
            minimumDistance = deliveryMovingMinDistanceM;
        } else {
            interval = stationaryIntervalMs;
            minimumDistance = stationaryMinDistanceM;
        }

        if (elapsed < interval) return false;
        if (distance >= minimumDistance) return true;
        return elapsed >= heartbeatMs;
    }

    void markUploaded(Location location, long now) {
        if (location == null) return;
        lastUploadAt = now;
        lastLat = location.getLatitude();
        lastLng = location.getLongitude();
    }

    String modeLabel() {
        return "manual".equals(mode) ? "manual" : "dinâmico";
    }

    private float distanceFromLast(Location location) {
        float[] result = new float[1];
        Location.distanceBetween(lastLat, lastLng, location.getLatitude(), location.getLongitude(), result);
        return result[0];
    }

    private static String string(UpDocument document, String... paths) {
        for (String path : paths) {
            Object value = document.get(path);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) return text;
            }
        }
        return "";
    }

    private static long seconds(UpDocument document, long fallbackMs, int min, int max, String... paths) {
        for (String path : paths) {
            Object value = document.get(path);
            if (value == null) continue;
            try {
                long seconds = Math.round(Double.parseDouble(String.valueOf(value).replace(',', '.')));
                seconds = Math.max(min, Math.min(max, seconds));
                return seconds * 1000L;
            } catch (Exception ignored) {}
        }
        return fallbackMs;
    }

    private static float meters(UpDocument document, float fallback, int min, int max, String... paths) {
        for (String path : paths) {
            Object value = document.get(path);
            if (value == null) continue;
            try {
                float meters = Float.parseFloat(String.valueOf(value).replace(',', '.'));
                return Math.max(min, Math.min(max, meters));
            } catch (Exception ignored) {}
        }
        return fallback;
    }
}
