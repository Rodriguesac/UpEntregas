package com.rodriguesacai.entregador;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class OnlineService extends Service {
    private ListenerRegistration rideListener;
    private ListenerRegistration routeListener;
    private final DriverRepository repo = new DriverRepository();
    private String driverId = "";
    private DocumentSnapshot rideOffer;
    private DocumentSnapshot routeOffer;
    private String notifiedKey = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable telemetry = new Runnable() {
        @Override public void run() {
            publishTelemetry();
            handler.postDelayed(this, 60_000L);
        }
    };

    public static void start(Context c) {
        Intent i = new Intent(c, OnlineService.class);
        ContextCompat.startForegroundService(c, i);
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, OnlineService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannels(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        driverId = Session.getDriverId(this);
        if (driverId.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int type = Build.VERSION.SDK_INT >= 34
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE : 0;
        ServiceCompat.startForeground(this, NotificationHelper.ONLINE_ID,
                NotificationHelper.onlineNotification(this, "Aguardando novas corridas e rotas"), type);
        attachListeners();
        handler.removeCallbacks(telemetry);
        handler.post(telemetry);
        return START_STICKY;
    }

    private void attachListeners() {
        if (rideListener != null) rideListener.remove();
        if (routeListener != null) routeListener.remove();

        rideListener = repo.listenDirectedRides(driverId, new DriverRepository.RideCallback() {
            @Override public void onRide(DocumentSnapshot d) {
                rideOffer = d;
                refreshOffer();
            }
            @Override public void onError(Exception e) { showReconnect(); }
        });

        routeListener = repo.listenDirectedRoutes(driverId, new DriverRepository.RideCallback() {
            @Override public void onRide(DocumentSnapshot d) {
                routeOffer = d;
                refreshOffer();
            }
            @Override public void onError(Exception e) { showReconnect(); }
        });
    }

    private void refreshOffer() {
        DocumentSnapshot best = chooseOffer();
        if (best == null) {
            if (!notifiedKey.isEmpty()) {
                String[] p = notifiedKey.split(":", 3);
                if (p.length >= 2) NotificationHelper.cancelMission(this, p[1], p[0]);
            }
            notifiedKey = "";
            OfferAlertPlayer.stop();
            return;
        }

        boolean route = DriverRepository.isMultiRoute(best);
        String type = route ? "rotas_entrega" : "rides";
        String id = best.getId();
        int count = route ? Math.max(DriverRepository.routeOrderIds(best).size(), DriverRepository.routeStops(best).size()) : 1;
        String key = type + ":" + id + ":" + count;
        OfferAlertPlayer.start(this, type + ":" + id);
        if (key.equals(notifiedKey)) return;
        if (!notifiedKey.isEmpty()) {
            String[] p = notifiedKey.split(":", 3);
            if (p.length >= 2) NotificationHelper.cancelMission(this, p[1], p[0]);
        }
        notifiedKey = key;

        // Se o UP já está visível, a própria MainActivity abre a tela de oferta.
        // A notificação de alta prioridade é reservada para quando o app está em segundo plano.
        if (!UPApp.isAppVisible()) {
            if (route) {
                NotificationHelper.notifyNewRoute(this, id, count, "Abra o UP Entregas para responder.");
            } else {
                String pedido = DriverRepository.first(best, "codigoPedido", "numeroPedido", "orderId", "pedidoId");
                NotificationHelper.notifyNewRide(this, id, pedido, "Abra o UP Entregas para responder.");
            }
        }
    }

    private DocumentSnapshot chooseOffer() {
        if (rideOffer == null) return routeOffer;
        if (routeOffer == null) return rideOffer;
        long a = DriverRepository.offerExpiryMillis(rideOffer);
        long b = DriverRepository.offerExpiryMillis(routeOffer);
        if (a <= 0 && b <= 0) return rideOffer;
        if (a <= 0) return routeOffer;
        if (b <= 0) return rideOffer;
        return a <= b ? rideOffer : routeOffer;
    }

    private void showReconnect() {
        ServiceCompat.startForeground(this, NotificationHelper.ONLINE_ID,
                NotificationHelper.onlineNotification(this,
                        "ONLINE • conexão será restabelecida automaticamente"),
                Build.VERSION.SDK_INT >= 34 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE : 0);
    }

    private void publishTelemetry() {
        if (driverId.isEmpty()) return;
        DeviceStatus.Battery b = DeviceStatus.battery(this);
        if (b.level >= 0) repo.saveDeviceTelemetry(driverId, b.level, b.charging);
    }

    @Override public void onDestroy() {
        if (rideListener != null) rideListener.remove();
        if (routeListener != null) routeListener.remove();
        handler.removeCallbacks(telemetry);
        OfferAlertPlayer.stop();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
