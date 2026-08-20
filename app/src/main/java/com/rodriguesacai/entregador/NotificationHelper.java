package com.rodriguesacai.entregador;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public final class NotificationHelper {
    public static final String RIDES_CHANNEL = "up_rides_v23";
    public static final String ONLINE_CHANNEL = "up_online_v14";
    public static final String TRACKING_CHANNEL = "up_tracking_v14";
    public static final int ONLINE_ID = 13001;
    public static final int TRACKING_ID = 13002;

    private NotificationHelper() {}

    public static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;

        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        NotificationChannel rides = new NotificationChannel(
                RIDES_CHANNEL, "Novas corridas UP", NotificationManager.IMPORTANCE_HIGH);
        rides.setDescription("Ofertas de entrega direcionadas ao entregador");
        rides.enableVibration(false);
        rides.setSound(null, null);
        rides.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        nm.createNotificationChannel(rides);

        NotificationChannel online = new NotificationChannel(
                ONLINE_CHANNEL, "UP Entregas online", NotificationManager.IMPORTANCE_LOW);
        online.setDescription("Mantém o UP Entregas disponível enquanto você estiver ONLINE");
        online.setShowBadge(false);
        nm.createNotificationChannel(online);

        NotificationChannel tracking = new NotificationChannel(
                TRACKING_CHANNEL, "Corrida e localização", NotificationManager.IMPORTANCE_LOW);
        tracking.setDescription("Localização durante uma corrida ativa");
        tracking.setShowBadge(false);
        nm.createNotificationChannel(tracking);
    }

    public static PendingIntent openApp(Context c, String rideId) {
        return openMission(c, rideId, "rides");
    }

    public static PendingIntent openMission(Context c, String missionId, String missionType) {
        Intent i = new Intent(c, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (missionId != null && !missionId.isEmpty()) {
            i.putExtra("ride_id", missionId);
            i.putExtra("mission_id", missionId);
            i.putExtra("mission_type", missionType == null || missionType.isEmpty() ? "rides" : missionType);
            if ("rotas_entrega".equals(missionType)) i.putExtra("route_id", missionId);
        }
        int code = missionId == null || missionId.isEmpty() ? 1300 : Math.abs((missionType + missionId).hashCode());
        return PendingIntent.getActivity(c, code, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static Notification onlineNotification(Context c, String text) {
        return new NotificationCompat.Builder(c, ONLINE_CHANNEL)
                .setSmallIcon(R.drawable.ic_up_bolt)
                .setContentTitle("UP Entregas • ONLINE")
                .setContentText(text == null || text.isEmpty() ? "Aguardando nova entrega" : text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        text == null || text.isEmpty() ? "Aguardando nova entrega" : text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(openApp(c, ""))
                .build();
    }

    public static Notification trackingNotification(Context c, String rideId, String text) {
        return trackingNotification(c, rideId, "rides", text);
    }

    public static Notification trackingNotification(Context c, String missionId, String missionType, String text) {
        String safeText = "Entrega em andamento • toque para abrir o UP";
        return new NotificationCompat.Builder(c, TRACKING_CHANNEL)
                .setSmallIcon(R.drawable.ic_up_location)
                .setContentTitle("UP Entregas")
                .setContentText(safeText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(safeText))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(openMission(c, missionId, missionType))
                .build();
    }

    public static void notifyNewRide(Context c, String rideId, String pedido, String body) {
        notifyNewMission(c, rideId, "rides", pedido, body, false);
    }

    public static void notifyNewRoute(Context c, String routeId, int stops, String body) {
        String label = stops > 0 ? stops + " entregas" : "rota múltipla";
        notifyNewMission(c, routeId, "rotas_entrega", label, body, true);
    }

    public static void notifyNewRoute(Context c, String routeId, String stopsLabel, String body) {
        String label = stopsLabel == null || stopsLabel.trim().isEmpty() ? "rota múltipla" : stopsLabel.trim();
        notifyNewMission(c, routeId, "rotas_entrega", label, body, true);
    }

    private static void notifyNewMission(Context c, String missionId, String missionType, String label, String body, boolean route) {
        String title;
        if (route) title = "Nova rota UP • " + (label == null || label.isEmpty() ? "múltipla" : label);
        else title = label == null || label.isEmpty() ? "Nova corrida UP" : "Nova corrida • Pedido #" + label;
        String message = route
                ? "Nova rota disponível. Toque para ver os detalhes no UP Entregas."
                : "Nova entrega disponível. Toque para ver os detalhes no UP Entregas.";

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, RIDES_CHANNEL)
                .setSmallIcon(R.drawable.ic_up_bolt)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(openMission(c, missionId, missionType));

        try {
            NotificationManagerCompat.from(c).notify(
                    missionId == null || missionId.isEmpty() ? 13003 : Math.abs((missionType + missionId).hashCode()), b.build());
        } catch (SecurityException ignored) {}
    }

    public static void cancelRide(Context c, String rideId) {
        cancelMission(c, rideId, "rides");
        cancelMission(c, rideId, "rotas_entrega");
    }

    public static void cancelMission(Context c, String missionId, String missionType) {
        if (missionId == null || missionId.isEmpty()) return;
        NotificationManagerCompat.from(c).cancel(Math.abs((missionType + missionId).hashCode()));
    }
}
