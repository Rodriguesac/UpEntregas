package com.rodriguesacai.entregador;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Compatibilidade com FCM caso o projeto passe a usar push no futuro.
 * A V1.4 sem Blaze não depende deste serviço: o OnlineService escuta o Firestore
 * enquanto o entregador estiver explicitamente ONLINE.
 */
public class UPMessagingService extends FirebaseMessagingService {
    @Override public void onNewToken(String token) {
        String id = Session.getDriverId(this);
        if (!id.isEmpty()) new DriverRepository().saveToken(id, token);
    }

    @Override public void onMessageReceived(RemoteMessage msg) {
        String title = msg.getData().getOrDefault("title",
                msg.getNotification() != null && msg.getNotification().getTitle() != null
                        ? msg.getNotification().getTitle() : "Nova corrida UP");
        String body = msg.getData().getOrDefault("body",
                msg.getNotification() != null && msg.getNotification().getBody() != null
                        ? msg.getNotification().getBody() : "Abra o UP Entregas para conferir.");
        String routeId = msg.getData().getOrDefault("routeId", msg.getData().getOrDefault("route_id", ""));
        String rideId = msg.getData().getOrDefault("rideId", msg.getData().getOrDefault("ride_id", ""));
        String missionType = msg.getData().getOrDefault("missionType", msg.getData().getOrDefault("mission_type", routeId.isEmpty() ? "rides" : "rotas_entrega"));
        String targetDriver = msg.getData().getOrDefault("targetDriverId", "");
        String sessionDriver = Session.getDriverId(this);
        if (!targetDriver.isEmpty() && !sessionDriver.isEmpty() && !targetDriver.equals(sessionDriver)) return;

        String pedido = msg.getData().getOrDefault("codigoPedido", msg.getData().getOrDefault("numeroPedido", ""));
        if ("rotas_entrega".equals(missionType) || !routeId.isEmpty()) {
            String id = !routeId.isEmpty() ? routeId : rideId;
            String qtd = msg.getData().getOrDefault("qtdPedidos", msg.getData().getOrDefault("stops", ""));
            if (!UPApp.isAppVisible()) NotificationHelper.notifyNewRoute(this, id, qtd, "Abra o UP Entregas para responder.");
            OfferAlertPlayer.start(this, "rotas_entrega:" + id);
        } else {
            if (!UPApp.isAppVisible()) NotificationHelper.notifyNewRide(this, rideId, pedido, "Abra o UP Entregas para responder.");
            OfferAlertPlayer.start(this, "rides:" + rideId);
        }
    }
}
