package com.rodriguesacai.entregador;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DriverRepository {
    public interface DriverCallback { void onResult(DocumentSnapshot doc); void onError(Exception e); }
    public interface RideCallback { void onRide(DocumentSnapshot doc); void onError(Exception e); }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void loadDriver(String id, DriverCallback cb) {
        db.collection("entregadores").document(id).get()
                .addOnSuccessListener(cb::onResult)
                .addOnFailureListener(cb::onError);
    }

    public Task<Void> recordLogin(String driverId) {
        Map<String, Object> m = new HashMap<>();
        m.put("lastLoginAt", FieldValue.serverTimestamp());
        m.put("ultimoLoginEm", FieldValue.serverTimestamp());
        m.put("appVersion", BuildConfig.VERSION_NAME);
        m.put("upProtocolVersion", UpState.PROTOCOL_VERSION);
        m.put("platform", "android_native_up_entregas");
        m.put("updatedAt", FieldValue.serverTimestamp());
        return db.collection("entregadores").document(driverId).set(m, SetOptions.merge());
    }

    public Task<Void> setOnline(String driverId, boolean online) {
        Map<String, Object> m = new HashMap<>();
        m.put("online", online);
        m.put("statusOnline", online ? "Online" : "Offline");
        m.put("status", online ? "Livre" : "Offline");
        m.put("aceitaNovasOfertas", online);
        m.put("statusOperacional", online ? "DISPONIVEL" : "INDISPONIVEL");
        m.put("appVersion", BuildConfig.VERSION_NAME);
        m.put("updatedAt", FieldValue.serverTimestamp());
        return db.collection("entregadores").document(driverId).set(m, SetOptions.merge());
    }

    public void loadUpConfig(DriverCallback cb) {
        db.collection("up_config").document("master").get()
                .addOnSuccessListener(cb::onResult)
                .addOnFailureListener(cb::onError);
    }

    public Task<Void> saveDeviceTelemetry(String driverId, int batteryLevel, boolean charging) {
        Map<String, Object> telemetry = new HashMap<>();
        telemetry.put("batteryLevel", batteryLevel);
        telemetry.put("bateria", batteryLevel);
        telemetry.put("carregando", charging);
        telemetry.put("charging", charging);
        telemetry.put("appVersion", BuildConfig.VERSION_NAME);
        telemetry.put("updatedAt", FieldValue.serverTimestamp());

        Map<String, Object> m = new HashMap<>();
        m.put("batteryLevel", batteryLevel);
        m.put("bateria", batteryLevel);
        m.put("bateriaPercentual", batteryLevel);
        m.put("charging", charging);
        m.put("carregando", charging);
        m.put("batteryUpdatedAt", FieldValue.serverTimestamp());
        m.put("bateriaAtualizadaEm", FieldValue.serverTimestamp());
        m.put("telemetria", telemetry);
        m.put("appVersion", BuildConfig.VERSION_NAME);
        m.put("updatedAt", FieldValue.serverTimestamp());
        return db.collection("entregadores").document(driverId).set(m, SetOptions.merge());
    }

    public Task<Void> saveOperationalEquipment(String driverId, boolean hasCash, double cashAvailable,
                                               boolean hasMachine, String machineTypes) {
        Map<String, Object> op = new HashMap<>();
        op.put("temTroco", hasCash);
        op.put("trocoDisponivel", Math.max(0d, cashAvailable));
        op.put("temMaquininha", hasMachine);
        op.put("maquininhaTipos", machineTypes == null ? "" : machineTypes.trim());
        op.put("atualizadoEm", FieldValue.serverTimestamp());

        Map<String, Object> m = new HashMap<>();
        m.put("temTroco", hasCash);
        m.put("trocoDisponivel", Math.max(0d, cashAvailable));
        m.put("temMaquininha", hasMachine);
        m.put("maquininhaTipos", machineTypes == null ? "" : machineTypes.trim());
        m.put("operacao", op);
        m.put("equipamentos", op);
        m.put("updatedAt", FieldValue.serverTimestamp());
        return db.collection("entregadores").document(driverId).set(m, SetOptions.merge());
    }

    /** Somente ofertas direcionadas ao entregador. ofertaParaTodos/broadcast não são aceitos. */
    public ListenerRegistration listenDirectedRides(String driverId, RideCallback cb) {
        return db.collection("rides").whereEqualTo("targetDriverId", driverId).addSnapshotListener((snap, e) -> {
            if (e != null) { cb.onError(e); return; }
            if (snap == null) return;
            DocumentSnapshot best = null;
            long bestExpiry = Long.MAX_VALUE;
            for (DocumentSnapshot d : snap.getDocuments()) {
                if (!isDirectedTo(d, driverId)) continue;
                if (!Boolean.TRUE.equals(d.getBoolean("ofertaAtiva"))) continue;
                long expiry = offerExpiryMillis(d);
                if (expiry > 0 && expiry <= System.currentTimeMillis()) {
                    markOfferExpired(driverId, d.getId());
                    continue;
                }
                if (best == null || (expiry > 0 && expiry < bestExpiry)) {
                    best = d;
                    bestExpiry = expiry > 0 ? expiry : Long.MAX_VALUE;
                }
            }
            cb.onRide(best);
        });
    }

    public ListenerRegistration listenRide(String rideId, RideCallback cb) {
        return db.collection("rides").document(rideId).addSnapshotListener((d, e) -> {
            if (e != null) { cb.onError(e); return; }
            cb.onRide(d != null && d.exists() ? d : null);
        });
    }

    /** Ofertas de rota múltipla são documentos de rotas_entrega e continuam sempre direcionadas manualmente. */
    public ListenerRegistration listenDirectedRoutes(String driverId, RideCallback cb) {
        return db.collection("rotas_entrega").whereEqualTo("targetDriverId", driverId).addSnapshotListener((snap, e) -> {
            if (e != null) { cb.onError(e); return; }
            if (snap == null) return;
            DocumentSnapshot best = null;
            long bestExpiry = Long.MAX_VALUE;
            for (DocumentSnapshot d : snap.getDocuments()) {
                if (!isMultiRoute(d)) continue;
                if (!Boolean.TRUE.equals(d.getBoolean("ofertaAtiva"))) continue;
                if (!isDirectedTo(d, driverId)) continue;
                long expiry = offerExpiryMillis(d);
                if (expiry > 0 && expiry <= System.currentTimeMillis()) {
                    markRouteOfferExpired(driverId, d.getId());
                    continue;
                }
                if (best == null || (expiry > 0 && expiry < bestExpiry)) {
                    best = d;
                    bestExpiry = expiry > 0 ? expiry : Long.MAX_VALUE;
                }
            }
            cb.onRide(best);
        });
    }

    public ListenerRegistration listenRoute(String routeId, RideCallback cb) {
        return db.collection("rotas_entrega").document(routeId).addSnapshotListener((d, e) -> {
            if (e != null) { cb.onError(e); return; }
            cb.onRide(d != null && d.exists() ? d : null);
        });
    }

    public Task<DocumentSnapshot> loadRoute(String routeId) {
        return db.collection("rotas_entrega").document(routeId).get();
    }

    public Task<Void> acceptRide(String driverId, String rideId) {
        DocumentReference ride = db.collection("rides").document(rideId);
        DocumentReference driver = db.collection("entregadores").document(driverId);
        DocumentReference event = db.collection("appEventosOperacao").document();

        return db.runTransaction(tx -> {
            DocumentSnapshot r = tx.get(ride);
            DocumentSnapshot d = tx.get(driver);
            if (!r.exists()) throw new IllegalStateException("Corrida não encontrada.");
            if (!d.exists()) throw new IllegalStateException("Cadastro do entregador não encontrado.");

            if (!Boolean.TRUE.equals(r.getBoolean("ofertaAtiva")))
                throw new IllegalStateException("Oferta não está mais ativa.");
            if (!isDirectedTo(r, driverId))
                throw new IllegalStateException("Esta oferta foi enviada para outro entregador.");
            long expiry = offerExpiryMillis(r);
            if (expiry > 0 && expiry <= System.currentTimeMillis())
                throw new IllegalStateException("O prazo desta oferta terminou. Aguarde o gestor.");

            Boolean busy = d.getBoolean("emCorrida");
            Boolean online = d.getBoolean("online");
            Boolean active = d.getBoolean("ativo");
            Boolean approved = d.getBoolean("aprovado");
            String approval = s(d, "statusAprovacao");
            if (Boolean.TRUE.equals(busy)) throw new IllegalStateException("Você já está em uma corrida.");
            if (!Boolean.TRUE.equals(online)) throw new IllegalStateException("Você precisa estar ONLINE para aceitar.");
            if (Boolean.FALSE.equals(active) || Boolean.FALSE.equals(approved) || (!approval.isEmpty() && !"aprovado".equalsIgnoreCase(approval)))
                throw new IllegalStateException("Seu cadastro não está liberado para corridas.");

            String assigned = first(r, "entregadorId", "driverId", "uidEntregador");
            if (!assigned.isEmpty() && !driverId.equals(assigned))
                throw new IllegalStateException("Corrida já atribuída a outro entregador.");

            DocumentReference pedidoRef = pedidoRef(r);
            DocumentReference rotaRef = rotaRef(r);
            DocumentSnapshot pedido = pedidoRef == null ? null : tx.get(pedidoRef);
            DocumentSnapshot rota = rotaRef == null ? null : tx.get(rotaRef);

            Map<String, Object> rm = new HashMap<>();
            rm.put("ofertaAceita", true);
            rm.put("ofertaAtiva", false);
            rm.put("ofertaParaTodos", false);
            rm.put("broadcast", false);
            rm.put("acceptedAt", FieldValue.serverTimestamp());
            rm.put("aceitaEm", FieldValue.serverTimestamp());
            rm.put("entregadorId", driverId);
            rm.put("driverId", driverId);
            rm.put("uidEntregador", driverId);
            rm.put("status", "ACEITA");
            rm.put("statusCorrida", "ACEITA");
            rm.put("statusEntregador", "ACEITA");
            rm.put("statusOferta", "ACEITA");
            rm.put("statusOfertaEntregador", "ACEITA");
            rm.put("statusEntrega", "ENTREGADOR_A_CAMINHO_LOJA");
            rm.put("upProtocolVersion", UpState.PROTOCOL_VERSION);
            rm.put("upState", UpState.TO_STORE.name());
            rm.put("upRouteOpen", true);
            rm.put("pickupConfirmed", false);
            rm.put("corridaAtiva", true);
            rm.put("emCorrida", true);
            rm.put("liberadoParaEntregador", true);
            rm.put("pendenteGestor", false);
            rm.put("ultimaAcaoEntregador", "ACEITA");
            rm.put("rastreamentoVisivelCliente", false);
            rm.put("statusAtualizadoEm", FieldValue.serverTimestamp());
            rm.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(ride, rm);

            Map<String, Object> dm = new HashMap<>();
            dm.put("emCorrida", true);
            dm.put("corridaAtiva", true);
            dm.put("corridaAtualId", rideId);
            dm.put("currentRideId", rideId);
            dm.put("rideAtualId", rideId);
            dm.put("missaoAtualId", rideId);
            dm.put("missaoAtualTipo", "rides");
            dm.put("status", "Ocupado");
            dm.put("statusOperacional", "PICKUP");
            dm.put("upProtocolVersion", UpState.PROTOCOL_VERSION);
            dm.put("upMissionState", UpState.TO_STORE.name());
            dm.put("upRouteOpen", true);
            dm.put("canReceiveRouteComplement", true);
            String openRouteId = first(r, "rotaEntregaId", "rotaId", "routeId");
            if (!openRouteId.isEmpty()) dm.put("upOpenRouteId", openRouteId);
            dm.put("aceitaNovasOfertas", false);
            dm.put("ofertaAtualId", FieldValue.delete());
            dm.put("ultimaAceitacaoEm", FieldValue.serverTimestamp());
            dm.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(driver, dm);

            Map<String, Object> routeSync = stageMap(driverId, rideId, "ACEITA", "ENTREGADOR_A_CAMINHO_LOJA", null);
            routeSync.put("ofertaAtiva", false);
            routeSync.put("ofertaAceita", true);
            if (rota != null && rota.exists()) tx.update(rotaRef, routeSync);

            if (pedido != null && pedido.exists()) {
                Map<String, Object> pm = pedidoStageMap(driverId, rideId, "ENTREGADOR_A_CAMINHO_LOJA", null);
                pm.put("ofertaAtiva", false);
                pm.put("ofertaAceita", true);
                pm.put("corridaAtiva", true);
                pm.put("emCorrida", true);
                pm.put("acceptedAt", FieldValue.serverTimestamp());
                pm.put("aceitaEm", FieldValue.serverTimestamp());
                tx.update(pedidoRef, pm);
            }

            tx.set(event, eventMap(driverId, rideId, r, "CORRIDA_ACEITA", "Oferta aceita pelo entregador."));
            return null;
        });
    }

    public Task<Void> rejectRide(String driverId, String rideId, String reason) {
        DocumentReference ride = db.collection("rides").document(rideId);
        DocumentReference driver = db.collection("entregadores").document(driverId);
        DocumentReference rejection = ride.collection("rejections").document();
        DocumentReference event = db.collection("appEventosOperacao").document();

        return db.runTransaction(tx -> {
            DocumentSnapshot r = tx.get(ride);
            DocumentSnapshot d = tx.get(driver);
            if (!r.exists()) throw new IllegalStateException("Corrida não encontrada.");
            if (!Boolean.TRUE.equals(r.getBoolean("ofertaAtiva"))) throw new IllegalStateException("Oferta não está mais ativa.");
            if (!isDirectedTo(r, driverId)) throw new IllegalStateException("Esta oferta não pertence a você.");

            DocumentReference pedidoRef = pedidoRef(r);
            DocumentReference rotaRef = rotaRef(r);
            DocumentSnapshot pedido = pedidoRef == null ? null : tx.get(pedidoRef);
            DocumentSnapshot rota = rotaRef == null ? null : tx.get(rotaRef);

            Map<String, Object> m = new HashMap<>();
            m.put("ofertaAtiva", false);
            m.put("ofertaAceita", false);
            m.put("status", "REJEITADA");
            m.put("upProtocolVersion", UpState.PROTOCOL_VERSION);
            m.put("upState", UpState.CANCELED.name());
            m.put("upRouteOpen", false);
            m.put("statusCorrida", "REJEITADA");
            m.put("statusOferta", "REJEITADA");
            m.put("statusOfertaEntregador", "REJEITADA");
            m.put("statusEntregador", "REJEITADA");
            m.put("rejeitadaPor", driverId);
            m.put("lastRejectedBy", driverId);
            m.put("ultimoRejeitadoPor", driverId);
            m.put("rejectionReason", reason);
            m.put("motivoRejeicao", reason);
            m.put("rejeitadaEm", FieldValue.serverTimestamp());
            m.put("rejectedDriverIds", FieldValue.arrayUnion(driverId));
            m.put("rejeitados", FieldValue.arrayUnion(driverId));
            m.put("pendenteGestor", true);
            m.put("acaoNecessariaGestor", "ESCOLHER_ENTREGADOR");
            m.put("ultimaAcaoEntregador", "REJEITADA");
            m.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(ride, m);

            if (rota != null && rota.exists()) {
                Map<String, Object> routeSync = new HashMap<>(m);
                routeSync.remove("rejectedDriverIds");
                routeSync.remove("rejeitados");
                tx.update(rotaRef, routeSync);
            }
            if (pedido != null && pedido.exists()) {
                Map<String, Object> pm = new HashMap<>();
                pm.put("ofertaAtiva", false);
                pm.put("ofertaAceita", false);
                pm.put("statusEntrega", "AGUARDANDO_ENTREGADOR");
                pm.put("entrega.status", "AGUARDANDO_ENTREGADOR");
                pm.put("rejeitadaPor", driverId);
                pm.put("lastRejectedBy", driverId);
                pm.put("motivoRejeicao", reason);
                pm.put("rejeitadaEm", FieldValue.serverTimestamp());
                pm.put("pendenteGestor", true);
                pm.put("acaoNecessariaGestor", "ESCOLHER_ENTREGADOR");
                pm.put("updatedAt", FieldValue.serverTimestamp());
                tx.update(pedidoRef, pm);
            }

            if (d.exists() && !Boolean.TRUE.equals(d.getBoolean("emCorrida"))) {
                Map<String, Object> dm = new HashMap<>();
                boolean on = Boolean.TRUE.equals(d.getBoolean("online"));
                dm.put("status", on ? "Livre" : "Offline");
                dm.put("statusOperacional", on ? "DISPONIVEL" : "INDISPONIVEL");
                dm.put("upMissionState", UpState.CANCELED.name());
                dm.put("upRouteOpen", false);
                dm.put("canReceiveRouteComplement", false);
                dm.put("upOpenRouteId", FieldValue.delete());
                dm.put("aceitaNovasOfertas", on);
                dm.put("ofertaAtualId", FieldValue.delete());
                dm.put("updatedAt", FieldValue.serverTimestamp());
                tx.update(driver, dm);
            }

            Map<String, Object> rej = new HashMap<>();
            rej.put("driverId", driverId);
            rej.put("reason", reason);
            rej.put("createdAt", FieldValue.serverTimestamp());
            tx.set(rejection, rej);
            tx.set(event, eventMap(driverId, rideId, r, "CORRIDA_REJEITADA", reason));
            return null;
        });
    }

    public Task<Void> markOfferExpired(String driverId, String rideId) {
        DocumentReference ride = db.collection("rides").document(rideId);
        DocumentReference driver = db.collection("entregadores").document(driverId);
        return db.runTransaction(tx -> {
            DocumentSnapshot r = tx.get(ride);
            if (!r.exists() || !Boolean.TRUE.equals(r.getBoolean("ofertaAtiva")) || !isDirectedTo(r, driverId)) return null;
            long expiry = offerExpiryMillis(r);
            if (expiry <= 0 || expiry > System.currentTimeMillis()) return null;

            DocumentReference pedidoRef = pedidoRef(r);
            DocumentReference rotaRef = rotaRef(r);
            DocumentSnapshot pedido = pedidoRef == null ? null : tx.get(pedidoRef);
            DocumentSnapshot rota = rotaRef == null ? null : tx.get(rotaRef);
            DocumentSnapshot d = tx.get(driver);

            Map<String, Object> m = new HashMap<>();
            m.put("ofertaAtiva", false);
            m.put("ofertaAceita", false);
            m.put("status", "EXPIRADA");
            m.put("statusOferta", "EXPIRADA");
            m.put("statusOfertaEntregador", "EXPIRADA");
            m.put("statusEntregador", "EXPIRADA");
            m.put("lastExpiredFor", driverId);
            m.put("expiredDriverIds", FieldValue.arrayUnion(driverId));
            m.put("pendenteGestor", true);
            m.put("acaoNecessariaGestor", "ESCOLHER_ENTREGADOR");
            m.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(ride, m);

            if (rota != null && rota.exists()) {
                Map<String, Object> rm = new HashMap<>(m);
                rm.remove("expiredDriverIds");
                tx.update(rotaRef, rm);
            }
            if (pedido != null && pedido.exists()) {
                Map<String, Object> pm = new HashMap<>();
                pm.put("ofertaAtiva", false);
                pm.put("ofertaAceita", false);
                pm.put("statusEntrega", "AGUARDANDO_ENTREGADOR");
                pm.put("entrega.status", "AGUARDANDO_ENTREGADOR");
                pm.put("lastExpiredFor", driverId);
                pm.put("pendenteGestor", true);
                pm.put("acaoNecessariaGestor", "ESCOLHER_ENTREGADOR");
                pm.put("updatedAt", FieldValue.serverTimestamp());
                tx.update(pedidoRef, pm);
            }
            if (d.exists() && !Boolean.TRUE.equals(d.getBoolean("emCorrida")) && rideId.equals(s(d, "ofertaAtualId"))) {
                boolean on = Boolean.TRUE.equals(d.getBoolean("online"));
                Map<String, Object> dm = new HashMap<>();
                dm.put("status", on ? "Livre" : "Offline");
                dm.put("statusOperacional", on ? "DISPONIVEL" : "INDISPONIVEL");
                dm.put("upMissionState", UpState.CANCELED.name());
                dm.put("upRouteOpen", false);
                dm.put("canReceiveRouteComplement", false);
                dm.put("upOpenRouteId", FieldValue.delete());
                dm.put("aceitaNovasOfertas", on);
                dm.put("ofertaAtualId", FieldValue.delete());
                dm.put("updatedAt", FieldValue.serverTimestamp());
                tx.update(driver, dm);
            }
            return null;
        });
    }

    public Task<Void> setRideStage(String driverId, String rideId, String status, String deliveryStatus, String timestampField) {
        return updateStageTransaction(driverId, rideId, status, deliveryStatus, timestampField, null, false);
    }

    public Task<Void> pickupRide(String driverId, String rideId, String enteredCode) {
        return updateStageTransaction(driverId, rideId, "EM_ENTREGA", "SAIU_PARA_ENTREGA", "retiradoEm", enteredCode, true);
    }

    private Task<Void> updateStageTransaction(String driverId, String rideId, String status, String deliveryStatus,
                                              String timestampField, String pickupCode, boolean validatePickupCode) {
        DocumentReference ride = db.collection("rides").document(rideId);
        DocumentReference driver = db.collection("entregadores").document(driverId);
        DocumentReference event = db.collection("appEventosOperacao").document();
        return db.runTransaction(tx -> {
            DocumentSnapshot r = tx.get(ride);
            DocumentSnapshot d = tx.get(driver);
            if (!r.exists()) throw new IllegalStateException("Corrida não encontrada.");
            ensureAssigned(r, driverId);
            if (isTerminal(r)) throw new IllegalStateException("Esta corrida já foi encerrada.");

            if (validatePickupCode) {
                String expected = first(r, "codigoRetirada", "codigoLiberacao", "codigoParaRetirada", "codigoRetiradaLoja");
                boolean required = !expected.isEmpty() || Boolean.TRUE.equals(r.getBoolean("pickupCodeRequired"));
                String entered = pickupCode == null ? "" : pickupCode.trim();
                if (required && expected.isEmpty())
                    throw new IllegalStateException("O código de retirada ainda não foi disponibilizado pela loja.");
                if (required && !expected.equals(entered))
                    throw new IllegalArgumentException("Código de retirada incorreto.");
            }

            DocumentReference pedidoRef = pedidoRef(r);
            DocumentReference rotaRef = rotaRef(r);
            DocumentSnapshot pedido = pedidoRef == null ? null : tx.get(pedidoRef);
            DocumentSnapshot rota = rotaRef == null ? null : tx.get(rotaRef);

            boolean shareWithCustomer = customerTrackingEnabled(r, pedido);
            Map<String, Object> m = stageMap(driverId, rideId, status, deliveryStatus, timestampField);
            if ("COLETANDO".equals(status)) {
                m.put("pickupStartedAt", FieldValue.serverTimestamp());
                m.put("chegouColetaEm", FieldValue.serverTimestamp());
            }
            if ("EM_ENTREGA".equals(status)) {
                m.put("deliveryStartedAt", FieldValue.serverTimestamp());
                m.put("saiuEntregaEm", FieldValue.serverTimestamp());
                m.put("rastreamentoVisivelCliente", shareWithCustomer);
                m.put("aguardandoCodigoEntrega", true);
            }
            if ("NO_CLIENTE".equals(status)) {
                m.put("arrivedClientAt", FieldValue.serverTimestamp());
                m.put("aguardandoCodigoEntrega", true);
            }
            tx.update(ride, m);
            if (pedido != null && pedido.exists()) {
                Map<String, Object> pm = pedidoStageMap(driverId, rideId, deliveryStatus, timestampField);
                if ("COLETANDO".equals(status)) {
                    pm.put("pickupStartedAt", FieldValue.serverTimestamp());
                    pm.put("chegouColetaEm", FieldValue.serverTimestamp());
                }
                if ("EM_ENTREGA".equals(status)) {
                    pm.put("deliveryStartedAt", FieldValue.serverTimestamp());
                    pm.put("saiuEntregaEm", FieldValue.serverTimestamp());
                    pm.put("rastreamentoVisivelCliente", shareWithCustomer);
                    pm.put("aguardandoCodigoEntrega", true);
                }
                if ("NO_CLIENTE".equals(status)) {
                    pm.put("arrivedClientAt", FieldValue.serverTimestamp());
                    pm.put("aguardandoCodigoEntrega", true);
                }
                tx.update(pedidoRef, pm);
            }
            if (rota != null && rota.exists()) tx.update(rotaRef, m);

            if (d.exists()) {
                Map<String, Object> dm = new HashMap<>();
                String driverUpState = UpState.canonicalFor(status, deliveryStatus);
                dm.put("statusOperacional", "EM_ENTREGA".equals(status) ? "DELIVERY" : ("NO_CLIENTE".equals(status) ? "NO_CLIENTE" : "PICKUP"));
                dm.put("upProtocolVersion", UpState.PROTOCOL_VERSION);
                dm.put("upMissionState", driverUpState);
                boolean beforePickup = UpState.TO_STORE.name().equals(driverUpState) || UpState.AT_STORE.name().equals(driverUpState);
                dm.put("upRouteOpen", beforePickup);
                dm.put("canReceiveRouteComplement", beforePickup);
                String openRouteId = first(r, "rotaEntregaId", "rotaId", "routeId");
                if (beforePickup && !openRouteId.isEmpty()) dm.put("upOpenRouteId", openRouteId);
                else if (!beforePickup) dm.put("upOpenRouteId", FieldValue.delete());
                dm.put("rastreamentoAtivo", true);
                dm.put("updatedAt", FieldValue.serverTimestamp());
                tx.update(driver, dm);
            }
            tx.set(event, eventMap(driverId, rideId, r, "ETAPA_" + status, deliveryStatus));
            return null;
        });
    }

    public Task<Void> finishRide(String driverId, String rideId, String enteredCode, double receivedAmount) {
        DocumentReference ride = db.collection("rides").document(rideId);
        DocumentReference driver = db.collection("entregadores").document(driverId);
        DocumentReference event = db.collection("appEventosOperacao").document();
        DocumentReference settlement = db.collection("acertosEntregadores").document();

        return db.runTransaction(tx -> {
            DocumentSnapshot r = tx.get(ride);
            DocumentSnapshot d = tx.get(driver);
            if (!r.exists()) throw new IllegalStateException("Corrida não encontrada.");
            ensureAssigned(r, driverId);
            if (isTerminal(r)) throw new IllegalStateException("Esta corrida já foi encerrada.");

            String status = s(r, "status").toUpperCase(Locale.ROOT);
            String deliveryStatus = s(r, "statusEntrega").toUpperCase(Locale.ROOT);
            if (!status.contains("NO_CLIENTE") && !deliveryStatus.contains("CHEGOU_CLIENTE"))
                throw new IllegalStateException("Primeiro confirme que chegou ao cliente.");

            String expected = first(r, "codigoEntrega", "codigoEntregaCliente", "deliveryCode");
            boolean required = !expected.isEmpty() || Boolean.TRUE.equals(r.getBoolean("deliveryCodeRequired")) || Boolean.TRUE.equals(r.getBoolean("aguardandoCodigoEntrega"));
            String entered = enteredCode == null ? "" : enteredCode.trim();
            if (required && expected.isEmpty())
                throw new IllegalStateException("O código de entrega ainda não está disponível. Atualize a corrida e tente novamente.");
            if (required && !expected.equals(entered))
                throw new IllegalArgumentException("Código de entrega incorreto.");

            DocumentReference pedidoRef = pedidoRef(r);
            DocumentReference rotaRef = rotaRef(r);
            DocumentSnapshot pedido = pedidoRef == null ? null : tx.get(pedidoRef);
            DocumentSnapshot rota = rotaRef == null ? null : tx.get(rotaRef);

            double repasse = firstNumber(r, "valorRepasseEntregador", "repasseEntregador", "valorEntregador", "valorCorrida");
            double valorARepassar = Math.max(0d, receivedAmount - repasse);
            double valorAReceber = Math.max(0d, repasse - receivedAmount);
            String payment = first(r, "formaPagamento", "metodoPagamento", "pagamento");

            Map<String, Object> paymentMap = new HashMap<>();
            paymentMap.put("appVersion", BuildConfig.VERSION_NAME);
            paymentMap.put("atualizadoEm", FieldValue.serverTimestamp());
            paymentMap.put("createdAt", FieldValue.serverTimestamp());
            paymentMap.put("criadoEm", FieldValue.serverTimestamp());
            paymentMap.put("corridaId", rideId);
            paymentMap.put("pedidoId", pedidoId(r));
            paymentMap.put("entregadorId", driverId);
            paymentMap.put("entregadorUid", driverId);
            paymentMap.put("formaPagamento", payment);
            paymentMap.put("recebidoPeloEntregador", receivedAmount);
            paymentMap.put("recebidoPor", "ENTREGADOR");
            paymentMap.put("status", "AGUARDANDO_CONFERENCIA");
            paymentMap.put("origem", "APP_ENTREGADOR");
            paymentMap.put("observacao", "Confirmado no UP Entregas");
            paymentMap.put("precisaConferencia", true);
            paymentMap.put("taxaMotoboy", repasse);
            paymentMap.put("valorBruto", receivedAmount);
            paymentMap.put("valorLiquido", receivedAmount);
            paymentMap.put("valorAReceber", valorAReceber);
            paymentMap.put("valorARepassar", valorARepassar);

            Map<String, Object> rm = new HashMap<>();
            rm.put("status", "CONCLUIDA");
            rm.put("statusCorrida", "FINALIZADA");
            rm.put("statusEntrega", "ENTREGUE");
            rm.put("upProtocolVersion", UpState.PROTOCOL_VERSION);
            rm.put("upState", UpState.DELIVERED.name());
            rm.put("upRouteOpen", false);
            rm.put("pickupConfirmed", true);
            rm.put("statusEntregador", "CONCLUIDA");
            rm.put("statusOferta", "FINALIZADA");
            rm.put("statusOfertaEntregador", "FINALIZADA");
            rm.put("statusPedido", "FINALIZADO");
            rm.put("corridaAtiva", false);
            rm.put("emCorrida", false);
            rm.put("ofertaAtiva", false);
            rm.put("aguardandoCodigoEntrega", false);
            rm.put("rastreamentoVisivelCliente", false);
            rm.put("concluidaEm", FieldValue.serverTimestamp());
            rm.put("entregueEm", FieldValue.serverTimestamp());
            rm.put("finishedAt", FieldValue.serverTimestamp());
            rm.put("statusAtualizadoEm", FieldValue.serverTimestamp());
            rm.put("pagamentoRecebidoPeloEntregador", paymentMap);
            rm.put("acerto", paymentMap);
            rm.put("financeiro.precisaConferencia", true);
            rm.put("financeiroConferidoPeloApp", true);
            rm.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(ride, rm);

            boolean staysOnline = d.exists() && Boolean.TRUE.equals(d.getBoolean("online"));
            Map<String, Object> dm = new HashMap<>();
            dm.put("emCorrida", false);
            dm.put("corridaAtiva", false);
            dm.put("corridaAtualId", null);
            dm.put("currentRideId", "");
            dm.put("rideAtualId", "");
            dm.put("missaoAtualId", null);
            dm.put("status", staysOnline ? "Livre" : "Offline");
            dm.put("statusOperacional", staysOnline ? "DISPONIVEL" : "INDISPONIVEL");
            dm.put("upMissionState", UpState.DELIVERED.name());
            dm.put("upRouteOpen", false);
            dm.put("canReceiveRouteComplement", false);
            dm.put("aceitaNovasOfertas", staysOnline);
            dm.put("rastreamentoAtivo", false);
            dm.put("rastreamento.ativo", false);
            dm.put("rastreamento.finalizadoEm", FieldValue.serverTimestamp());
            dm.put("ultimaConclusaoEm", FieldValue.serverTimestamp());
            dm.put("ultimaMissaoEncerradaId", rideId);
            dm.put("financeiro.precisaConferencia", true);
            dm.put("financeiro.recebidoPeloEntregador", receivedAmount);
            dm.put("financeiro.taxaMotoboy", repasse);
            dm.put("financeiro.valorAReceber", valorAReceber);
            dm.put("financeiro.valorARepassar", valorARepassar);
            dm.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(driver, dm);

            if (rota != null && rota.exists()) tx.update(rotaRef, rm);
            if (pedido != null && pedido.exists()) {
                Map<String, Object> pm = pedidoStageMap(driverId, rideId, "ENTREGUE", null);
                pm.put("corridaAtiva", false);
                pm.put("emCorrida", false);
                pm.put("ofertaAtiva", false);
                pm.put("aguardandoCodigoEntrega", false);
                pm.put("rastreamentoVisivelCliente", false);
                pm.put("concluidaEm", FieldValue.serverTimestamp());
                pm.put("entregueEm", FieldValue.serverTimestamp());
                pm.put("finishedAt", FieldValue.serverTimestamp());
                pm.put("finalizado", true);
                pm.put("pagamentoRecebidoPeloEntregador", paymentMap);
                pm.put("acerto", paymentMap);
                pm.put("financeiro.precisaConferencia", true);
                pm.put("financeiroConferidoPeloApp", true);
                tx.update(pedidoRef, pm);
            }

            tx.set(settlement, paymentMap);
            tx.set(event, eventMap(driverId, rideId, r, "CORRIDA_CONCLUIDA", "Entrega finalizada com código."));
            return null;
        });
    }

    public Task<Void> acceptRoute(String driverId, String routeId) {
        DocumentReference route = db.collection("rotas_entrega").document(routeId);
        DocumentReference driver = db.collection("entregadores").document(driverId);
        DocumentReference event = db.collection("appEventosOperacao").document();
        return db.runTransaction(tx -> {
            DocumentSnapshot r = tx.get(route);
            DocumentSnapshot d = tx.get(driver);
            if (!r.exists()) throw new IllegalStateException("Rota não encontrada.");
            if (!isMultiRoute(r)) throw new IllegalStateException("Esta missão não é uma rota múltipla.");
            if (!Boolean.TRUE.equals(r.getBoolean("ofertaAtiva"))) throw new IllegalStateException("A oferta desta rota não está mais ativa.");
            if (!isDirectedTo(r, driverId)) throw new IllegalStateException("Esta rota foi enviada para outro entregador.");
            long expiry = offerExpiryMillis(r);
            if (expiry > 0 && expiry <= System.currentTimeMillis()) throw new IllegalStateException("O prazo desta rota terminou.");
            if (!d.exists()) throw new IllegalStateException("Cadastro do entregador não encontrado.");
            if (Boolean.TRUE.equals(d.getBoolean("emCorrida"))) throw new IllegalStateException("Você já está em uma missão.");
            if (!Boolean.TRUE.equals(d.getBoolean("online"))) throw new IllegalStateException("Você precisa estar ONLINE para aceitar.");

            List<String> ids = routeOrderIds(r);
            List<DocumentSnapshot> orders = new ArrayList<>();
            for (String id : ids) orders.add(tx.get(db.collection("pedidos").document(id)));

            Map<String, Object> rm = stageMap(driverId, routeId, "ACEITA", "ENTREGADOR_A_CAMINHO_LOJA", null);
            rm.put("tipo", "ROTA_MULTIPLA");
            rm.put("ofertaAtiva", false);
            rm.put("ofertaAceita", true);
            rm.put("statusOferta", "ACEITA");
            rm.put("statusOfertaEntregador", "ACEITA");
            rm.put("upProtocolVersion", UpState.PROTOCOL_VERSION);
            rm.put("upState", UpState.TO_STORE.name());
            rm.put("upRouteOpen", true);
            rm.put("pickupConfirmed", false);
            rm.put("rotaAberta", r.getBoolean("rotaAberta") == null || Boolean.TRUE.equals(r.getBoolean("rotaAberta")));
            rm.put("indiceParadaAtual", 0);
            rm.put("acceptedAt", FieldValue.serverTimestamp());
            rm.put("aceitaEm", FieldValue.serverTimestamp());
            tx.update(route, rm);

            Map<String, Object> dm = new HashMap<>();
            dm.put("emCorrida", true);
            dm.put("corridaAtiva", true);
            dm.put("corridaAtualId", routeId);
            dm.put("currentRideId", routeId);
            dm.put("rideAtualId", routeId);
            dm.put("missaoAtualId", routeId);
            dm.put("missaoAtualTipo", "rotas_entrega");
            dm.put("status", "Ocupado");
            dm.put("statusOperacional", "PICKUP");
            dm.put("upProtocolVersion", UpState.PROTOCOL_VERSION);
            dm.put("upMissionState", UpState.TO_STORE.name());
            dm.put("upRouteOpen", true);
            dm.put("canReceiveRouteComplement", true);
            dm.put("upOpenRouteId", routeId);
            dm.put("aceitaNovasOfertas", false);
            dm.put("ofertaAtualId", FieldValue.delete());
            dm.put("ultimaAceitacaoEm", FieldValue.serverTimestamp());
            dm.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(driver, dm);

            for (int i = 0; i < ids.size(); i++) {
                DocumentSnapshot o = orders.get(i);
                if (!o.exists()) continue;
                Map<String, Object> pm = pedidoStageMap(driverId, routeId, "ENTREGADOR_A_CAMINHO_LOJA", null);
                pm.put("rotaId", routeId);
                pm.put("rotaAtualId", routeId);
                pm.put("rotaPlanejadaId", routeId);
                pm.put("rotaPlanejadaOrdem", i + 1);
                pm.put("ofertaAtiva", false);
                pm.put("ofertaAceita", true);
                pm.put("corridaAtiva", true);
                pm.put("emCorrida", true);
                pm.put("acceptedAt", FieldValue.serverTimestamp());
                tx.update(o.getReference(), pm);
            }
            tx.set(event, eventMap(driverId, routeId, r, "ROTA_MULTIPLA_ACEITA", "Rota múltipla aceita pelo entregador."));
            return null;
        });
    }

    /** Aceita um pedido complementar oferecido pelo gestor enquanto a rota ainda está aberta na loja. */
    public Task<Void> acceptRouteComplement(String driverId, String routeId) {
        DocumentReference route = db.collection("rotas_entrega").document(routeId);
        DocumentReference event = db.collection("appEventosOperacao").document();
        return db.runTransaction(tx -> {
            DocumentSnapshot r = tx.get(route);
            if (!r.exists()) throw new IllegalStateException("Rota não encontrada.");
            ensureAssigned(r, driverId);
            String linkedRideId = first(r, "complementoLinkedRideId", "sourceRideId");
            DocumentReference linkedRideRef = linkedRideId.isEmpty() ? null : db.collection("rides").document(linkedRideId);
            DocumentSnapshot linkedRide = linkedRideRef == null ? null : tx.get(linkedRideRef);
            if (Boolean.FALSE.equals(r.getBoolean("rotaAberta")) || Boolean.TRUE.equals(r.getBoolean("pickupConfirmed"))) throw new IllegalStateException("A rota já saiu da loja e não aceita novos pedidos.");
            if (!Boolean.TRUE.equals(r.getBoolean("complementoOfertaAtiva"))) throw new IllegalStateException("Este complemento não está mais disponível.");
            Object raw = r.get("complementoOferta");
            if (!(raw instanceof Map)) throw new IllegalStateException("Complemento inválido.");
            @SuppressWarnings("unchecked") Map<String,Object> comp = new HashMap<>((Map<String,Object>) raw);
            String orderId = String.valueOf(comp.get("pedidoId") == null ? "" : comp.get("pedidoId")).trim();
            if (orderId.isEmpty()) throw new IllegalStateException("Pedido complementar não identificado.");
            DocumentReference orderRef = db.collection("pedidos").document(orderId);
            DocumentSnapshot order = tx.get(orderRef);
            if (!order.exists()) throw new IllegalStateException("Pedido complementar não encontrado.");

            List<String> ids = routeOrderIds(r);
            Object proposedIdsRaw = comp.get("pedidoIdsPropostos");
            if (proposedIdsRaw instanceof List) {
                ids = new ArrayList<>();
                for (Object x : (List<?>) proposedIdsRaw) {
                    String id = x == null ? "" : String.valueOf(x).trim();
                    if (!id.isEmpty() && !ids.contains(id)) ids.add(id);
                }
            }
            if (!ids.contains(orderId)) ids.add(orderId);

            List<String> trackingOrderIds = new ArrayList<>();
            Object trackingRaw = r.get("rastreamentoPedidosHabilitados");
            if (trackingRaw instanceof List) {
                for (Object value : (List<?>) trackingRaw) {
                    String id = value == null ? "" : String.valueOf(value).trim();
                    if (!id.isEmpty() && !trackingOrderIds.contains(id)) trackingOrderIds.add(id);
                }
            } else if (customerTrackingEnabled(r, null)) {
                trackingOrderIds.addAll(routeOrderIds(r));
            }
            if (customerTrackingEnabled(order, linkedRide)) {
                if (!trackingOrderIds.contains(orderId)) trackingOrderIds.add(orderId);
            } else {
                trackingOrderIds.remove(orderId);
            }

            List<Map<String,Object>> stops = routeStops(r);
            Object proposedStopsRaw = comp.get("paradasPropostas");
            if (proposedStopsRaw instanceof List) {
                stops = new ArrayList<>();
                for (Object x : (List<?>) proposedStopsRaw) {
                    if (x instanceof Map) {
                        @SuppressWarnings("unchecked") Map<String,Object> m = new HashMap<>((Map<String,Object>) x);
                        stops.add(m);
                    }
                }
            } else {
                boolean stopExists = false;
                for (Map<String,Object> x : stops) if (orderId.equals(String.valueOf(x.get("pedidoId")))) { stopExists = true; break; }
                Object p = comp.get("parada");
                if (!stopExists && p instanceof Map) {
                    @SuppressWarnings("unchecked") Map<String,Object> added = new HashMap<>((Map<String,Object>) p);
                    added.put("ordem", stops.size() + 1);
                    stops.add(added);
                }
            }
            for (int i = 0; i < stops.size(); i++) stops.get(i).put("ordem", i + 1);

            Map<String,Object> rm = new HashMap<>();
            rm.put("pedidoIds", ids); rm.put("pedidosIds", ids); rm.put("paradas", stops);
            rm.put("rastreamentoPedidosHabilitados", trackingOrderIds);
            rm.put("qtdPedidos", ids.size()); rm.put("quantidadePedidos", ids.size());
            Object newKm = comp.get("novoKm"); if (newKm instanceof Number) rm.put("kmEstimado", ((Number)newKm).doubleValue());
            Object newFee = comp.get("novoRepasse"); if (newFee instanceof Number) { rm.put("valorRepasseEntregador", ((Number)newFee).doubleValue()); rm.put("repasseTotal", ((Number)newFee).doubleValue()); }
            rm.put("complementoOfertaAtiva", false); rm.put("complementoAceito", true);
            rm.put("complementoRespondidoEm", FieldValue.serverTimestamp()); rm.put("updatedAt", FieldValue.serverTimestamp());

            List<DocumentSnapshot> routeOrders = new ArrayList<>();
            for (String id : ids) {
                if (id.equals(orderId)) routeOrders.add(order);
                else routeOrders.add(tx.get(db.collection("pedidos").document(id)));
            }
            tx.update(route, rm);
            if (linkedRide != null && linkedRide.exists()) {
                Map<String,Object> lm = new HashMap<>();
                lm.put("complementoOfertaAtiva", false);
                lm.put("complementoAceito", true);
                lm.put("upRouteOpen", true);
                lm.put("updatedAt", FieldValue.serverTimestamp());
                tx.update(linkedRideRef, lm);
            }
            for (int i = 0; i < ids.size(); i++) {
                DocumentSnapshot od = routeOrders.get(i);
                if (od == null || !od.exists()) continue;
                Map<String,Object> pm = pedidoStageMap(driverId, routeId, "ENTREGADOR_A_CAMINHO_LOJA", null);
                pm.put("rotaId", routeId); pm.put("rotaAtualId", routeId); pm.put("rotaPlanejadaId", routeId);
                pm.put("rotaPlanejada", true); pm.put("rotaPlanejadaOrdem", i + 1);
                pm.put("ofertaAceita", true); pm.put("ofertaAtiva", false); pm.put("corridaAtiva", true); pm.put("emCorrida", true);
                pm.put("rotaComplementoOfertaId", FieldValue.delete()); pm.put("rotaComplementoEntregadorId", FieldValue.delete());
                tx.update(od.getReference(), pm);
            }
            tx.set(event, eventMap(driverId, routeId, r, "ROTA_COMPLEMENTO_ACEITO", "Pedido complementar adicionado à rota antes da retirada."));
            return null;
        });
    }

    public Task<Void> rejectRouteComplement(String driverId, String routeId, String reason) {
        DocumentReference route = db.collection("rotas_entrega").document(routeId);
        DocumentReference event = db.collection("appEventosOperacao").document();
        return db.runTransaction(tx -> {
            DocumentSnapshot r = tx.get(route);
            if (!r.exists()) throw new IllegalStateException("Rota não encontrada.");
            ensureAssigned(r, driverId);
            String linkedRideId = first(r, "complementoLinkedRideId", "sourceRideId");
            DocumentReference linkedRideRef = linkedRideId.isEmpty() ? null : db.collection("rides").document(linkedRideId);
            DocumentSnapshot linkedRide = linkedRideRef == null ? null : tx.get(linkedRideRef);
            if (!Boolean.TRUE.equals(r.getBoolean("complementoOfertaAtiva"))) return null;
            Object raw = r.get("complementoOferta");
            String orderId = "";
            if (raw instanceof Map) {
                Object oid = ((Map<?,?>) raw).get("pedidoId");
                orderId = oid == null ? "" : String.valueOf(oid).trim();
            }
            DocumentSnapshot order = null;
            DocumentReference orderRef = null;
            if (!orderId.isEmpty()) {
                orderRef = db.collection("pedidos").document(orderId);
                order = tx.get(orderRef);
            }
            Map<String,Object> rm = new HashMap<>();
            rm.put("complementoOfertaAtiva", false); rm.put("complementoAceito", false);
            rm.put("complementoRejeitadoPor", driverId); rm.put("complementoMotivoRejeicao", reason == null ? "" : reason);
            rm.put("complementoRespondidoEm", FieldValue.serverTimestamp()); rm.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(route, rm);
            if (linkedRide != null && linkedRide.exists()) {
                Map<String,Object> lm = new HashMap<>();
                lm.put("complementoOfertaAtiva", false);
                lm.put("complementoAceito", false);
                lm.put("updatedAt", FieldValue.serverTimestamp());
                tx.update(linkedRideRef, lm);
            }
            if (order != null && order.exists()) {
                Map<String,Object> pm = new HashMap<>();
                pm.put("upProtocolVersion", UpState.PROTOCOL_VERSION); pm.put("upState", UpState.OFFER_PENDING.name()); pm.put("upRouteOpen", false);
                pm.put("status", "BUSCANDO_ENTREGADOR"); pm.put("statusPedido", "BUSCANDO_ENTREGADOR");
                pm.put("statusEntrega", "AGUARDANDO_ENTREGADOR"); pm.put("ofertaAtiva", false); pm.put("ofertaAceita", false);
                pm.put("corridaAtiva", false); pm.put("emCorrida", false); pm.put("pendenteGestor", true);
                pm.put("acaoNecessariaGestor", "ESCOLHER_ENTREGADOR");
                pm.put("entregadorId", FieldValue.delete()); pm.put("entregadorUid", FieldValue.delete()); pm.put("driverId", FieldValue.delete());
                pm.put("corridaAtualId", FieldValue.delete()); pm.put("rotaId", FieldValue.delete()); pm.put("rotaAtualId", FieldValue.delete());
                pm.put("rotaPlanejadaId", FieldValue.delete()); pm.put("rotaPlanejadaOrdem", FieldValue.delete()); pm.put("rotaPlanejada", false);
                pm.put("rotaComplementoOfertaId", FieldValue.delete()); pm.put("rotaComplementoEntregadorId", FieldValue.delete());
                pm.put("updatedAt", FieldValue.serverTimestamp()); pm.put("statusAtualizadoEm", FieldValue.serverTimestamp());
                tx.update(orderRef, pm);
            }
            tx.set(event, eventMap(driverId, routeId, r, "ROTA_COMPLEMENTO_REJEITADO", reason));
            return null;
        });
    }

    public Task<Void> rejectRoute(String driverId, String routeId, String reason) {
        DocumentReference route = db.collection("rotas_entrega").document(routeId);
        DocumentReference driver = db.collection("entregadores").document(driverId);
        DocumentReference event = db.collection("appEventosOperacao").document();
        return db.runTransaction(tx -> {
            DocumentSnapshot r = tx.get(route);
            DocumentSnapshot d = tx.get(driver);
            if (!r.exists()) throw new IllegalStateException("Rota não encontrada.");
            List<String> ids = routeOrderIds(r);
            List<DocumentSnapshot> orders = new ArrayList<>();
            for (String id : ids) orders.add(tx.get(db.collection("pedidos").document(id)));
            if (!Boolean.TRUE.equals(r.getBoolean("ofertaAtiva"))) throw new IllegalStateException("A oferta não está mais ativa.");
            if (!isDirectedTo(r, driverId)) throw new IllegalStateException("Esta rota foi enviada para outro entregador.");
            Map<String, Object> rm = new HashMap<>();
            rm.put("ofertaAtiva", false);
            rm.put("ofertaAceita", false);
            rm.put("status", "REJEITADA");
            rm.put("statusCorrida", "REJEITADA");
            rm.put("statusOferta", "REJEITADA");
            rm.put("statusOfertaEntregador", "REJEITADA");
            rm.put("pendenteGestor", true);
            rm.put("acaoNecessariaGestor", "ESCOLHER_ENTREGADOR");
            rm.put("rejeitadaPor", driverId);
            rm.put("motivoRejeicao", reason == null ? "" : reason);
            rm.put("rejeitadaEm", FieldValue.serverTimestamp());
            rm.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(route, rm);
            if (d.exists()) {
                boolean on = Boolean.TRUE.equals(d.getBoolean("online"));
                Map<String, Object> dm = new HashMap<>();
                dm.put("status", on ? "Livre" : "Offline");
                dm.put("statusOperacional", on ? "DISPONIVEL" : "INDISPONIVEL");
                dm.put("upMissionState", UpState.CANCELED.name());
                dm.put("upRouteOpen", false);
                dm.put("canReceiveRouteComplement", false);
                dm.put("upOpenRouteId", FieldValue.delete());
                dm.put("aceitaNovasOfertas", on);
                dm.put("ofertaAtualId", FieldValue.delete());
                dm.put("updatedAt", FieldValue.serverTimestamp());
                tx.update(driver, dm);
            }
            for (DocumentSnapshot o : orders) {
                if (!o.exists()) continue;
                Map<String,Object> pm = new HashMap<>();
                pm.put("status", "BUSCANDO_ENTREGADOR"); pm.put("statusPedido", "BUSCANDO_ENTREGADOR"); pm.put("statusEntrega", "AGUARDANDO_DECISAO_GESTOR");
                pm.put("ofertaAtiva", false); pm.put("ofertaAceita", false); pm.put("corridaAtiva", false); pm.put("emCorrida", false);
                pm.put("entregadorId", FieldValue.delete()); pm.put("entregadorUid", FieldValue.delete()); pm.put("driverId", FieldValue.delete()); pm.put("corridaAtualId", FieldValue.delete());
                pm.put("rotaId", FieldValue.delete()); pm.put("rotaAtualId", FieldValue.delete()); pm.put("rotaPlanejadaId", FieldValue.delete()); pm.put("rotaPlanejadaOrdem", FieldValue.delete()); pm.put("rotaPlanejada", false);
                pm.put("pendenteGestor", true); pm.put("acaoNecessariaGestor", "ESCOLHER_ENTREGADOR"); pm.put("updatedAt", FieldValue.serverTimestamp()); pm.put("statusAtualizadoEm", FieldValue.serverTimestamp());
                tx.update(o.getReference(), pm);
            }
            tx.set(event, eventMap(driverId, routeId, r, "ROTA_MULTIPLA_REJEITADA", reason));
            return null;
        });
    }

    public Task<Void> markRouteOfferExpired(String driverId, String routeId) {
        DocumentReference route = db.collection("rotas_entrega").document(routeId);
        DocumentReference driver = db.collection("entregadores").document(driverId);
        return db.runTransaction(tx -> {
            DocumentSnapshot r = tx.get(route);
            DocumentSnapshot d = tx.get(driver);
            if (!r.exists() || !Boolean.TRUE.equals(r.getBoolean("ofertaAtiva")) || !isDirectedTo(r, driverId)) return null;
            List<String> ids = routeOrderIds(r);
            List<DocumentSnapshot> orders = new ArrayList<>();
            for (String id : ids) orders.add(tx.get(db.collection("pedidos").document(id)));
            Map<String, Object> rm = new HashMap<>();
            rm.put("ofertaAtiva", false);
            rm.put("ofertaAceita", false);
            rm.put("status", "EXPIRADA");
            rm.put("statusCorrida", "EXPIRADA");
            rm.put("statusOferta", "EXPIRADA");
            rm.put("pendenteGestor", true);
            rm.put("acaoNecessariaGestor", "ESCOLHER_ENTREGADOR");
            rm.put("lastExpiredFor", driverId);
            rm.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(route, rm);
            if (d.exists() && !Boolean.TRUE.equals(d.getBoolean("emCorrida"))) {
                boolean on = Boolean.TRUE.equals(d.getBoolean("online"));
                Map<String, Object> dm = new HashMap<>();
                dm.put("status", on ? "Livre" : "Offline");
                dm.put("statusOperacional", on ? "DISPONIVEL" : "INDISPONIVEL");
                dm.put("upMissionState", UpState.CANCELED.name());
                dm.put("upRouteOpen", false);
                dm.put("canReceiveRouteComplement", false);
                dm.put("upOpenRouteId", FieldValue.delete());
                dm.put("aceitaNovasOfertas", on);
                dm.put("ofertaAtualId", FieldValue.delete());
                dm.put("updatedAt", FieldValue.serverTimestamp());
                tx.update(driver, dm);
            }
            for (DocumentSnapshot o : orders) {
                if (!o.exists()) continue;
                Map<String,Object> pm = new HashMap<>();
                pm.put("status", "BUSCANDO_ENTREGADOR"); pm.put("statusPedido", "BUSCANDO_ENTREGADOR"); pm.put("statusEntrega", "AGUARDANDO_DECISAO_GESTOR");
                pm.put("ofertaAtiva", false); pm.put("ofertaAceita", false); pm.put("corridaAtiva", false); pm.put("emCorrida", false);
                pm.put("entregadorId", FieldValue.delete()); pm.put("entregadorUid", FieldValue.delete()); pm.put("driverId", FieldValue.delete()); pm.put("corridaAtualId", FieldValue.delete());
                pm.put("rotaId", FieldValue.delete()); pm.put("rotaAtualId", FieldValue.delete()); pm.put("rotaPlanejadaId", FieldValue.delete()); pm.put("rotaPlanejadaOrdem", FieldValue.delete()); pm.put("rotaPlanejada", false);
                pm.put("pendenteGestor", true); pm.put("acaoNecessariaGestor", "ESCOLHER_ENTREGADOR"); pm.put("updatedAt", FieldValue.serverTimestamp()); pm.put("statusAtualizadoEm", FieldValue.serverTimestamp());
                tx.update(o.getReference(), pm);
            }
            return null;
        });
    }

    public Task<Void> setRouteStage(String driverId, String routeId, String status, String deliveryStatus, String timestampField) {
        DocumentReference route = db.collection("rotas_entrega").document(routeId);
        DocumentReference driver = db.collection("entregadores").document(driverId);
        DocumentReference event = db.collection("appEventosOperacao").document();
        return db.runTransaction(tx -> {
            DocumentSnapshot r = tx.get(route);
            DocumentSnapshot d = tx.get(driver);
            if (!r.exists()) throw new IllegalStateException("Rota não encontrada.");
            ensureAssigned(r, driverId);
            if (isTerminal(r)) throw new IllegalStateException("Esta rota já foi encerrada.");
            List<String> ids = routeOrderIds(r);
            List<DocumentSnapshot> orders = new ArrayList<>();
            for (String id : ids) orders.add(tx.get(db.collection("pedidos").document(id)));
            int current = routeCurrentIndex(r, ids.size());

            Map<String, Object> rm = stageMap(driverId, routeId, status, deliveryStatus, timestampField);
            if ("COLETANDO".equals(status)) {
                rm.put("pickupStartedAt", FieldValue.serverTimestamp());
                rm.put("chegouColetaEm", FieldValue.serverTimestamp());
            }
            if ("NO_CLIENTE".equals(status)) {
                rm.put("arrivedClientAt", FieldValue.serverTimestamp());
                rm.put("aguardandoCodigoEntrega", true);
            }
            tx.update(route, rm);

            for (int i = 0; i < orders.size(); i++) {
                DocumentSnapshot o = orders.get(i);
                if (!o.exists()) continue;
                if ("NO_CLIENTE".equals(status) && i != current) continue;
                Map<String, Object> pm = pedidoStageMap(driverId, routeId, deliveryStatus, timestampField);
                if ("COLETANDO".equals(status)) pm.put("chegouColetaEm", FieldValue.serverTimestamp());
                if ("NO_CLIENTE".equals(status)) {
                    pm.put("arrivedClientAt", FieldValue.serverTimestamp());
                    pm.put("aguardandoCodigoEntrega", true);
                }
                tx.update(o.getReference(), pm);
            }
            if (d.exists()) {
                Map<String, Object> dm = new HashMap<>();
                String driverUpState = UpState.canonicalFor(status, deliveryStatus);
                dm.put("statusOperacional", "NO_CLIENTE".equals(status) ? "NO_CLIENTE" : (UpState.TO_CUSTOMER.name().equals(driverUpState) ? "DELIVERY" : "PICKUP"));
                dm.put("upProtocolVersion", UpState.PROTOCOL_VERSION);
                dm.put("upMissionState", driverUpState);
                boolean beforePickup = UpState.TO_STORE.name().equals(driverUpState) || UpState.AT_STORE.name().equals(driverUpState);
                dm.put("upRouteOpen", beforePickup);
                dm.put("canReceiveRouteComplement", beforePickup);
                if (beforePickup) dm.put("upOpenRouteId", routeId);
                else dm.put("upOpenRouteId", FieldValue.delete());
                dm.put("updatedAt", FieldValue.serverTimestamp());
                tx.update(driver, dm);
            }
            tx.set(event, eventMap(driverId, routeId, r, "ROTA_ETAPA_" + status, deliveryStatus));
            return null;
        });
    }

    public Task<Void> pickupRoute(String driverId, String routeId) {
        DocumentReference route = db.collection("rotas_entrega").document(routeId);
        DocumentReference driver = db.collection("entregadores").document(driverId);
        DocumentReference event = db.collection("appEventosOperacao").document();
        return db.runTransaction(tx -> {
            DocumentSnapshot r = tx.get(route);
            DocumentSnapshot d = tx.get(driver);
            if (!r.exists()) throw new IllegalStateException("Rota não encontrada.");
            ensureAssigned(r, driverId);

            // Uma rota múltipla não cria um segundo PIN artificial de retirada.
            // A confirmação aqui representa o handoff físico de todos os pedidos da loja
            // para o entregador. Códigos de entrega continuam individuais por cliente.
            List<String> ids = routeOrderIds(r);
            List<DocumentSnapshot> orders = new ArrayList<>();
            for (String id : ids) orders.add(tx.get(db.collection("pedidos").document(id)));

            boolean routeTrackingEnabled = routeHasCustomerTracking(r);
            Map<String, Object> rm = stageMap(driverId, routeId, "EM_ENTREGA", "SAIU_PARA_ENTREGA", "retiradoEm");
            rm.put("rotaAberta", false);
            rm.put("upRouteOpen", false);
            rm.put("pickupConfirmed", true);
            rm.put("retiradaConfirmada", true);
            rm.put("bloqueadaParaNovosPedidos", true);
            rm.put("indiceParadaAtual", 0);
            rm.put("deliveryStartedAt", FieldValue.serverTimestamp());
            rm.put("saiuEntregaEm", FieldValue.serverTimestamp());
            rm.put("rastreamentoVisivelCliente", routeTrackingEnabled);
            rm.put("aguardandoCodigoEntrega", false);
            tx.update(route, rm);

            for (DocumentSnapshot o : orders) {
                if (!o.exists()) continue;
                Map<String, Object> pm = pedidoStageMap(driverId, routeId, "SAIU_PARA_ENTREGA", "retiradoEm");
                pm.put("deliveryStartedAt", FieldValue.serverTimestamp());
                pm.put("saiuEntregaEm", FieldValue.serverTimestamp());
                pm.put("rastreamentoVisivelCliente", customerTrackingEnabled(o, r));
                pm.put("aguardandoCodigoEntrega", false);
                tx.update(o.getReference(), pm);
            }
            if (d.exists()) {
                Map<String, Object> dm = new HashMap<>();
                dm.put("statusOperacional", "DELIVERY");
                dm.put("upProtocolVersion", UpState.PROTOCOL_VERSION);
                dm.put("upMissionState", UpState.TO_CUSTOMER.name());
                dm.put("upRouteOpen", false);
                dm.put("canReceiveRouteComplement", false);
                dm.put("upOpenRouteId", FieldValue.delete());
                dm.put("rastreamentoAtivo", true);
                dm.put("updatedAt", FieldValue.serverTimestamp());
                tx.update(driver, dm);
            }
            tx.set(event, eventMap(driverId, routeId, r, "ROTA_RETIRADA_CONFIRMADA", "Rota fechada e pedidos retirados."));
            return null;
        });
    }

    /** Finaliza somente a parada atual. Retorna quantas entregas ainda faltam. */
    public Task<Integer> finishRouteStop(String driverId, String routeId, String enteredCode, double receivedAmount) {
        DocumentReference route = db.collection("rotas_entrega").document(routeId);
        DocumentReference driver = db.collection("entregadores").document(driverId);
        DocumentReference event = db.collection("appEventosOperacao").document();
        DocumentReference settlement = db.collection("acertosEntregadores").document();
        return db.runTransaction(tx -> {
            DocumentSnapshot r = tx.get(route);
            DocumentSnapshot d = tx.get(driver);
            if (!r.exists()) throw new IllegalStateException("Rota não encontrada.");
            ensureAssigned(r, driverId);
            if (isTerminal(r)) throw new IllegalStateException("Esta rota já foi encerrada.");

            List<String> ids = routeOrderIds(r);
            if (ids.isEmpty()) throw new IllegalStateException("Rota sem pedidos.");
            int current = routeCurrentIndex(r, ids.size());
            DocumentReference orderRef = db.collection("pedidos").document(ids.get(current));
            DocumentSnapshot order = tx.get(orderRef);
            if (!order.exists()) throw new IllegalStateException("Pedido da parada atual não foi encontrado.");

            String routeStatus = (s(r, "status") + " " + s(r, "statusEntrega")).toUpperCase(Locale.ROOT);
            if (!routeStatus.contains("NO_CLIENTE") && !routeStatus.contains("CHEGOU_CLIENTE"))
                throw new IllegalStateException("Primeiro confirme que chegou ao cliente atual.");

            String expected = first(order, "codigoEntrega", "codigoEntregaCliente", "deliveryCode");
            boolean required = !expected.isEmpty() || Boolean.TRUE.equals(order.getBoolean("deliveryCodeRequired"));
            String entered = enteredCode == null ? "" : enteredCode.trim();
            if (required && expected.isEmpty()) throw new IllegalStateException("O código de entrega deste pedido ainda não está disponível.");
            if (required && !expected.equals(entered)) throw new IllegalArgumentException("Código de entrega incorreto.");

            List<Map<String, Object>> stops = routeStops(r);
            if (stops.size() < ids.size()) {
                while (stops.size() < ids.size()) {
                    Map<String, Object> x = new HashMap<>();
                    x.put("pedidoId", ids.get(stops.size()));
                    x.put("ordem", stops.size() + 1);
                    stops.add(x);
                }
            }
            Map<String, Object> stop = stops.get(current);
            stop.put("status", "ENTREGUE");
            stop.put("entregue", true);
            stop.put("recebidoPeloEntregador", Math.max(0d, receivedAmount));
            stop.put("entregueEmMs", System.currentTimeMillis());

            Map<String, Object> pm = pedidoStageMap(driverId, routeId, "ENTREGUE", null);
            pm.put("corridaAtiva", false);
            pm.put("emCorrida", false);
            pm.put("aguardandoCodigoEntrega", false);
            pm.put("rastreamentoVisivelCliente", false);
            pm.put("concluidaEm", FieldValue.serverTimestamp());
            pm.put("entregueEm", FieldValue.serverTimestamp());
            pm.put("finishedAt", FieldValue.serverTimestamp());
            pm.put("finalizado", true);
            Map<String, Object> paymentStop = new HashMap<>();
            paymentStop.put("recebidoPeloEntregador", Math.max(0d, receivedAmount));
            paymentStop.put("formaPagamento", first(order, "formaPagamento", "pagamento.forma", "pagamento"));
            paymentStop.put("rotaId", routeId);
            paymentStop.put("pedidoId", ids.get(current));
            paymentStop.put("entregadorId", driverId);
            paymentStop.put("status", "AGUARDANDO_CONFERENCIA");
            paymentStop.put("createdAt", FieldValue.serverTimestamp());
            pm.put("pagamentoRecebidoPeloEntregador", paymentStop);
            tx.update(orderRef, pm);

            double priorReceived = firstNumber(r, "recebidoAcumulado", "recebidoPeloEntregador");
            double totalReceived = priorReceived + Math.max(0d, receivedAmount);
            int next = current + 1;
            int remaining = Math.max(0, ids.size() - next);

            if (remaining > 0) {
                Map<String, Object> rm = stageMap(driverId, routeId, "EM_ENTREGA", "SAIU_PARA_ENTREGA", null);
                rm.put("indiceParadaAtual", next);
                rm.put("entregasConcluidas", next);
                rm.put("paradas", stops);
                rm.put("recebidoAcumulado", totalReceived);
                rm.put("aguardandoCodigoEntrega", false);
                rm.put("rastreamentoVisivelCliente", routeHasCustomerTracking(r));
                tx.update(route, rm);
                Map<String, Object> dm = new HashMap<>();
                dm.put("status", "Ocupado");
                dm.put("statusOperacional", "DELIVERY");
                dm.put("updatedAt", FieldValue.serverTimestamp());
                tx.update(driver, dm);
                tx.set(event, eventMap(driverId, routeId, r, "ROTA_PARADA_CONCLUIDA", "Parada concluída. Próxima entrega liberada."));
                return remaining;
            }

            double repasse = firstNumber(r, "valorRepasseEntregador", "repasseTotal", "valorCorrida");
            double valorARepassar = Math.max(0d, totalReceived - repasse);
            double valorAReceber = Math.max(0d, repasse - totalReceived);
            Map<String, Object> settlementMap = new HashMap<>();
            settlementMap.put("tipo", "ROTA_MULTIPLA");
            settlementMap.put("rotaId", routeId);
            settlementMap.put("corridaId", routeId);
            settlementMap.put("entregadorId", driverId);
            settlementMap.put("taxaMotoboy", repasse);
            settlementMap.put("valorRepasseEntregador", repasse);
            settlementMap.put("recebidoPeloEntregador", totalReceived);
            settlementMap.put("valorBruto", totalReceived);
            settlementMap.put("valorARepassar", valorARepassar);
            settlementMap.put("valorAReceber", valorAReceber);
            settlementMap.put("quantidadePedidos", ids.size());
            settlementMap.put("pedidoIds", ids);
            settlementMap.put("status", "AGUARDANDO_CONFERENCIA");
            settlementMap.put("precisaConferencia", true);
            settlementMap.put("origem", "UP_ENTREGAS_ANDROID");
            settlementMap.put("appVersion", BuildConfig.VERSION_NAME);
            settlementMap.put("createdAt", FieldValue.serverTimestamp());
            settlementMap.put("criadoEm", FieldValue.serverTimestamp());
            settlementMap.put("updatedAt", FieldValue.serverTimestamp());

            Map<String, Object> rm = new HashMap<>();
            rm.put("status", "CONCLUIDA");
            rm.put("statusCorrida", "FINALIZADA");
            rm.put("statusEntrega", "ENTREGUE");
            rm.put("upProtocolVersion", UpState.PROTOCOL_VERSION);
            rm.put("upState", UpState.DELIVERED.name());
            rm.put("upRouteOpen", false);
            rm.put("pickupConfirmed", true);
            rm.put("corridaAtiva", false);
            rm.put("emCorrida", false);
            rm.put("ofertaAtiva", false);
            rm.put("rotaAberta", false);
            rm.put("upRouteOpen", false);
            rm.put("pickupConfirmed", true);
            rm.put("retiradaConfirmada", true);
            rm.put("bloqueadaParaNovosPedidos", true);
            rm.put("indiceParadaAtual", current);
            rm.put("entregasConcluidas", ids.size());
            rm.put("paradas", stops);
            rm.put("recebidoAcumulado", totalReceived);
            rm.put("aguardandoCodigoEntrega", false);
            rm.put("rastreamentoVisivelCliente", false);
            rm.put("concluidaEm", FieldValue.serverTimestamp());
            rm.put("entregueEm", FieldValue.serverTimestamp());
            rm.put("finishedAt", FieldValue.serverTimestamp());
            rm.put("financeiro", settlementMap);
            rm.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(route, rm);

            boolean staysOnline = d.exists() && Boolean.TRUE.equals(d.getBoolean("online"));
            Map<String, Object> dm = new HashMap<>();
            dm.put("emCorrida", false);
            dm.put("corridaAtiva", false);
            dm.put("corridaAtualId", null);
            dm.put("currentRideId", "");
            dm.put("rideAtualId", "");
            dm.put("missaoAtualId", null);
            dm.put("missaoAtualTipo", null);
            dm.put("status", staysOnline ? "Livre" : "Offline");
            dm.put("statusOperacional", staysOnline ? "DISPONIVEL" : "INDISPONIVEL");
            dm.put("upMissionState", UpState.DELIVERED.name());
            dm.put("upRouteOpen", false);
            dm.put("canReceiveRouteComplement", false);
            dm.put("aceitaNovasOfertas", staysOnline);
            dm.put("rastreamentoAtivo", false);
            dm.put("ultimaConclusaoEm", FieldValue.serverTimestamp());
            dm.put("ultimaMissaoEncerradaId", routeId);
            dm.put("financeiro", settlementMap);
            dm.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(driver, dm);
            tx.set(settlement, settlementMap);
            tx.set(event, eventMap(driverId, routeId, r, "ROTA_MULTIPLA_CONCLUIDA", "Todas as paradas foram concluídas."));
            return 0;
        });
    }

    public Task<Void> saveToken(String driverId, String token) {
        Map<String, Object> m = new HashMap<>();
        m.put("fcmToken", token);
        m.put("pushToken", token);
        m.put("tokenPush", token);
        m.put("tokenAtualizadoEm", FieldValue.serverTimestamp());
        m.put("tokenUpdatedAt", FieldValue.serverTimestamp());
        m.put("updatedAt", FieldValue.serverTimestamp());
        return db.collection("entregadores").document(driverId).set(m, SetOptions.merge());
    }

    /** Grava GPS no entregador e na missão atual; em rotas, espelha somente para pedidos com mapa habilitado. */
    public Task<Void> saveMissionLocation(String driverId, double lat, double lng, float accuracy, float speed, float bearing,
                                          String missionId, String missionType, boolean visibleToCustomer) {
        if ("rotas_entrega".equals(missionType)) {
            Map<String, Object> coords = new HashMap<>();
            coords.put("lat", lat); coords.put("lng", lng); coords.put("accuracy", accuracy);
            coords.put("speed", speed); coords.put("bearing", bearing); coords.put("updatedAt", FieldValue.serverTimestamp());
            Map<String, Object> dm = new HashMap<>();
            dm.put("coords", coords);
            dm.put("localizacaoAtualizadaEm", FieldValue.serverTimestamp());
            dm.put("localizacaoOrigem", "up_entregas_android_" + BuildConfig.VERSION_NAME);
            dm.put("rastreamentoAtivo", true);
            dm.put("rastreamento.ativo", true);
            dm.put("rastreamento.corridaAtualId", missionId);
            dm.put("rastreamento.atualizadoEm", FieldValue.serverTimestamp());
            Map<String, Object> rm = new HashMap<>();
            rm.put("localizacaoEntregador", coords);
            rm.put("entregadorLat", lat); rm.put("entregadorLng", lng);
            rm.put("localizacaoEntregadorAtualizadaEm", FieldValue.serverTimestamp());
            rm.put("updatedAt", FieldValue.serverTimestamp());
            DocumentReference routeRef = db.collection("rotas_entrega").document(missionId);
            return routeRef.get().continueWithTask(routeTask -> {
                DocumentSnapshot route = routeTask.getResult();
                WriteBatch batch = db.batch();
                batch.set(db.collection("entregadores").document(driverId), dm, SetOptions.merge());
                batch.set(routeRef, rm, SetOptions.merge());
                if (visibleToCustomer) {
                    for (String orderId : routeOrderIds(route)) {
                        if (!routeTrackingEnabledForOrder(route, orderId)) continue;
                        Map<String, Object> pm = new HashMap<>(rm);
                        pm.put("entregadorId", driverId);
                        pm.put("driverId", driverId);
                        pm.put("rotaAtualId", missionId);
                        batch.set(db.collection("pedidos").document(orderId), pm, SetOptions.merge());
                    }
                }
                return batch.commit();
            });
        }
        return saveLocation(driverId, lat, lng, accuracy, speed, bearing, missionId, visibleToCustomer);
    }

    /** Grava GPS interno e só espelha no pedido quando a etapa e a preferência permitem o mapa do cliente. */
    public Task<Void> saveLocation(String driverId, double lat, double lng, float accuracy, float speed, float bearing,
                                   String rideId, boolean visibleToCustomer) {
        Map<String, Object> coords = new HashMap<>();
        coords.put("lat", lat);
        coords.put("lng", lng);
        coords.put("accuracy", accuracy);
        coords.put("speed", speed);
        coords.put("bearing", bearing);
        coords.put("updatedAt", FieldValue.serverTimestamp());

        Map<String, Object> dm = new HashMap<>();
        dm.put("coords", coords);
        dm.put("localizacaoAtualizadaEm", FieldValue.serverTimestamp());
        dm.put("localizacaoOrigem", "up_entregas_android_" + BuildConfig.VERSION_NAME);
        dm.put("rastreamentoAtivo", true);
        dm.put("rastreamento.ativo", true);
        dm.put("rastreamento.corridaAtualId", rideId);
        dm.put("rastreamento.intervaloSeg", 15);
        dm.put("rastreamento.atualizadoEm", FieldValue.serverTimestamp());

        Map<String, Object> rm = new HashMap<>();
        rm.put("localizacaoEntregador", coords);
        rm.put("entregadorLat", lat);
        rm.put("entregadorLng", lng);
        rm.put("entregadorAccuracy", accuracy);
        rm.put("entregadorSpeed", speed);
        rm.put("localizacaoEntregadorAtualizadaEm", FieldValue.serverTimestamp());
        rm.put("updatedAt", FieldValue.serverTimestamp());

        DocumentReference driverRef = db.collection("entregadores").document(driverId);
        if (rideId == null || rideId.isEmpty()) {
            WriteBatch batch = db.batch();
            batch.update(driverRef, dm);
            return batch.commit();
        }

        DocumentReference rideRef = db.collection("rides").document(rideId);
        return rideRef.get().continueWithTask(task -> {
            WriteBatch batch = db.batch();
            batch.update(driverRef, dm);
            batch.update(rideRef, rm);
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                DocumentSnapshot mission = task.getResult();
                String pid = pedidoId(mission);
                if (visibleToCustomer && customerTrackingEnabled(mission, null) && !pid.isEmpty()) {
                    Map<String, Object> pm = new HashMap<>(rm);
                    pm.put("entregadorId", driverId);
                    pm.put("driverId", driverId);
                    pm.put("corridaAtualId", rideId);
                    batch.set(db.collection("pedidos").document(pid), pm, SetOptions.merge());
                }
            }
            return batch.commit();
        });
    }

    /**
     * Reconciliacao segura: espelha no cadastro do entregador o estado que ja existe na missao.
     * Nao avanca etapa; serve para Gestor/Central recuperarem rotas antigas ou app reaberto.
     */
    public Task<Void> syncDriverMissionState(String driverId, String missionId, String missionType, DocumentSnapshot mission) {
        if (driverId == null || driverId.isEmpty() || mission == null || !mission.exists()) return Tasks.forResult(null);
        UpState state = UpState.from(mission);
        if (state == UpState.OFFER_PENDING || state == UpState.PLANNED || state == UpState.UNKNOWN) return Tasks.forResult(null);
        boolean beforePickup = state.beforePickup();
        boolean terminal = state.terminal();
        Map<String,Object> dm = new HashMap<>();
        dm.put("upProtocolVersion", UpState.PROTOCOL_VERSION);
        dm.put("upMissionState", state.name());
        dm.put("upRouteOpen", beforePickup);
        dm.put("canReceiveRouteComplement", beforePickup);
        if (terminal) {
            dm.put("upOpenRouteId", FieldValue.delete());
            dm.put("emCorrida", false);
            dm.put("corridaAtiva", false);
            dm.put("statusOperacional", "DISPONIVEL");
        } else {
            dm.put("emCorrida", true);
            dm.put("corridaAtiva", true);
            dm.put("corridaAtualId", missionId);
            dm.put("missaoAtualId", missionId);
            dm.put("missaoAtualTipo", "rotas_entrega".equals(missionType) ? "rotas_entrega" : "rides");
            String routeId = "rotas_entrega".equals(missionType) ? missionId : first(mission, "rotaEntregaId", "rotaId", "routeId");
            if (beforePickup && !routeId.isEmpty()) dm.put("upOpenRouteId", routeId);
            else if (!beforePickup) dm.put("upOpenRouteId", FieldValue.delete());
            dm.put("statusOperacional", state == UpState.AT_CUSTOMER ? "NO_CLIENTE" : (state.deliveryPhase() ? "DELIVERY" : "PICKUP"));
        }
        dm.put("updatedAt", FieldValue.serverTimestamp());
        return db.collection("entregadores").document(driverId).set(dm, SetOptions.merge());
    }

    public Task<DocumentSnapshot> loadRide(String id) { return db.collection("rides").document(id).get(); }

    public Task<QuerySnapshot> loadHistory(String driverId) {
        return db.collection("rides").whereEqualTo("entregadorId", driverId).limit(30).get();
    }

    public Task<QuerySnapshot> loadSettlements(String driverId) {
        return db.collection("acertosEntregadores").whereEqualTo("entregadorId", driverId).limit(100).get();
    }

    public Task<QuerySnapshot> loadNotifications(String driverId) {
        return db.collection("app_notifications").whereEqualTo("targetDriverId", driverId).limit(80).get();
    }

    /** Solicita alteração de Pix sem substituir dados aprovados diretamente. A loja/GADM decide a aprovação. */
    public Task<Void> requestPixChange(String driverId, String pixType, String pixKey, String holder) {
        DocumentReference request = db.collection("suporteEntregador").document();
        Map<String, Object> m = new HashMap<>();
        m.put("entregadorId", driverId);
        m.put("tipo", "ALTERACAO_PIX");
        m.put("categoria", "CADASTRO_FINANCEIRO");
        m.put("status", "PENDENTE");
        m.put("origem", "UP_ENTREGAS_ANDROID");
        m.put("pixTipoSolicitado", pixType == null ? "" : pixType.trim());
        m.put("pixChaveSolicitada", pixKey == null ? "" : pixKey.trim());
        m.put("pixTitularSolicitado", holder == null ? "" : holder.trim());
        m.put("appVersion", BuildConfig.VERSION_NAME);
        m.put("createdAt", FieldValue.serverTimestamp());
        m.put("criadoEm", FieldValue.serverTimestamp());
        m.put("updatedAt", FieldValue.serverTimestamp());
        return request.set(m);
    }

    public Task<Void> occurrenceRoute(String driverId, String routeId, String reason) {
        DocumentReference occ = db.collection("ocorrenciasOperacao").document();
        DocumentReference route = db.collection("rotas_entrega").document(routeId);
        return db.runTransaction(tx -> {
            DocumentSnapshot r = tx.get(route);
            if (!r.exists()) throw new IllegalStateException("Rota não encontrada.");
            ensureAssigned(r, driverId);
            Map<String, Object> m = new HashMap<>();
            m.put("entregadorId", driverId);
            m.put("entregadorUid", driverId);
            m.put("routeId", routeId);
            m.put("rotaId", routeId);
            m.put("missaoId", routeId);
            m.put("collectionName", "rotas_entrega");
            m.put("motivo", reason);
            m.put("descricao", "Ocorrência enviada pelo app durante rota múltipla.");
            m.put("origem", "APP_ENTREGADOR");
            m.put("status", "ABERTA");
            m.put("prioridade", "ALTA");
            m.put("visivelGestor", true);
            m.put("criadaEm", FieldValue.serverTimestamp());
            m.put("createdAt", FieldValue.serverTimestamp());
            m.put("atualizadoEm", FieldValue.serverTimestamp());
            tx.set(occ, m);
            Map<String, Object> rm = new HashMap<>();
            rm.put("ocorrenciaAtiva", true);
            rm.put("statusOcorrencia", "ABERTA");
            rm.put("ultimaOcorrenciaId", occ.getId());
            rm.put("ultimaOcorrenciaMotivo", reason);
            rm.put("ultimaOcorrenciaEm", FieldValue.serverTimestamp());
            rm.put("pendenteGestor", true);
            rm.put("acaoNecessariaGestor", "RESOLVER_OCORRENCIA");
            rm.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(route, rm);
            return null;
        });
    }

    public Task<Void> occurrence(String driverId, String rideId, String reason) {
        DocumentReference occ = db.collection("ocorrenciasOperacao").document();
        DocumentReference ride = db.collection("rides").document(rideId);
        return db.runTransaction(tx -> {
            DocumentSnapshot r = tx.get(ride);
            if (!r.exists()) throw new IllegalStateException("Corrida não encontrada.");
            ensureAssigned(r, driverId);

            Map<String, Object> m = new HashMap<>();
            m.put("entregadorId", driverId);
            m.put("entregadorUid", driverId);
            m.put("rideId", rideId);
            m.put("missaoId", rideId);
            m.put("pedidoId", pedidoId(r));
            m.put("codigoPedido", first(r, "codigoPedido", "numeroPedido"));
            m.put("collectionName", "rides");
            m.put("motivo", reason);
            m.put("descricao", "Ocorrência enviada pelo app do entregador.");
            m.put("origem", "APP_ENTREGADOR");
            m.put("status", "ABERTA");
            m.put("prioridade", "ALTA");
            m.put("visivelGestor", true);
            m.put("criadaEm", FieldValue.serverTimestamp());
            m.put("createdAt", FieldValue.serverTimestamp());
            m.put("atualizadoEm", FieldValue.serverTimestamp());
            tx.set(occ, m);

            Map<String, Object> rm = new HashMap<>();
            rm.put("ocorrenciaAtiva", true);
            rm.put("statusOcorrencia", "ABERTA");
            rm.put("ultimaOcorrenciaId", occ.getId());
            rm.put("ultimaOcorrenciaMotivo", reason);
            rm.put("ultimaOcorrenciaEm", FieldValue.serverTimestamp());
            rm.put("pendenteGestor", true);
            rm.put("acaoNecessariaGestor", "RESOLVER_OCORRENCIA");
            rm.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(ride, rm);
            return null;
        });
    }

    public static long offerExpiryMillis(DocumentSnapshot d) {
        String[] fields = {"offerExpiresAt", "ofertaExpiraEm", "expiresAt", "expiraEm", "prazoRespostaOfertaMs"};
        for (String f : fields) {
            Object o = d.get(f);
            long v = toMillis(o, d);
            if (v > 0) return v;
        }
        return 0;
    }

    private static long toMillis(Object o, DocumentSnapshot d) {
        if (o == null) return 0;
        if (o instanceof Timestamp) return ((Timestamp) o).toDate().getTime();
        if (o instanceof Date) return ((Date) o).getTime();
        if (o instanceof Number) {
            long n = ((Number) o).longValue();
            if (n > 1_000_000_000_000L) return n;
            if (n > 1_000_000_000L) return n * 1000L;
            if (n > 0 && n <= 10 * 60 * 1000L) {
                Object created = d.get("ofertaCriadaEm");
                long base = toMillis(created, d);
                if (base == 0) base = toMillis(d.get("createdAt"), d);
                if (base > 0) return base + n;
            }
            return 0;
        }
        if (o instanceof String) {
            try { return Instant.parse((String) o).toEpochMilli(); } catch (Exception ignored) {}
            try { return Long.parseLong((String) o); } catch (Exception ignored) {}
        }
        return 0;
    }

    public static boolean isDirectedTo(DocumentSnapshot r, String driverId) {
        if (driverId == null || driverId.isEmpty()) return false;
        return driverId.equals(directedTarget(r));
    }

    /**
     * Retorna um único alvo. Se campos de destino estiverem conflitantes, retorna vazio por segurança.
     */
    public static String directedTarget(DocumentSnapshot r) {
        String[] fields = {"targetDriverId", "ofertaParaEntregadorId", "driverAtualOferta", "entregadorAtualOferta", "entregadorSelecionadoId", "entregadorOfertaId"};
        String target = "";
        for (String f : fields) {
            String v = s(r, f).trim();
            if (v.isEmpty() || "null".equalsIgnoreCase(v)) continue;
            if (target.isEmpty()) target = v;
            else if (!target.equals(v)) return "";
        }
        return target;
    }

    public static boolean isMultiRoute(DocumentSnapshot r) {
        if (r == null || !r.exists()) return false;
        String type = first(r, "tipo", "routeType", "missionType").toUpperCase(Locale.ROOT);
        if (type.contains("ROTA_MULTIPLA") || type.contains("MULTI")) return true;
        Object n = r.get("qtdPedidos");
        if (!(n instanceof Number)) n = r.get("quantidadePedidos");
        return n instanceof Number && ((Number) n).intValue() > 1;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> routeStops(DocumentSnapshot r) {
        ArrayList<Map<String, Object>> out = new ArrayList<>();
        Object raw = r == null ? null : r.get("paradas");
        if (raw instanceof List) {
            for (Object x : (List<?>) raw) {
                if (x instanceof Map) out.add(new HashMap<>((Map<String, Object>) x));
            }
        }
        return out;
    }

    public static List<String> routeOrderIds(DocumentSnapshot r) {
        ArrayList<String> out = new ArrayList<>();
        if (r == null) return out;
        Object raw = r.get("pedidoIds");
        if (!(raw instanceof List)) raw = r.get("pedidosIds");
        if (raw instanceof List) {
            for (Object x : (List<?>) raw) {
                String id = x == null ? "" : String.valueOf(x).trim();
                if (!id.isEmpty() && !out.contains(id)) out.add(id);
            }
        }
        if (out.isEmpty()) {
            for (Map<String, Object> stop : routeStops(r)) {
                Object x = stop.get("pedidoId");
                String id = x == null ? "" : String.valueOf(x).trim();
                if (!id.isEmpty() && !out.contains(id)) out.add(id);
            }
        }
        return out;
    }

    public static int routeCurrentIndex(DocumentSnapshot r, int size) {
        Object x = r == null ? null : r.get("indiceParadaAtual");
        int i = x instanceof Number ? ((Number) x).intValue() : 0;
        if (size <= 0) return 0;
        return Math.max(0, Math.min(size - 1, i));
    }

    private static void ensureAssigned(DocumentSnapshot r, String driverId) {
        String assigned = first(r, "entregadorId", "driverId", "uidEntregador");
        if (!driverId.equals(assigned)) throw new IllegalStateException("Esta corrida não está atribuída a você.");
    }

    private static boolean isTerminal(DocumentSnapshot r) {
        String s = first(r, "status", "statusCorrida", "statusEntrega").toUpperCase(Locale.ROOT);
        return s.contains("CONCLUID") || s.contains("FINALIZ") || s.contains("ENTREGUE") || s.contains("CANCELAD");
    }

    private static boolean customerTrackingEnabled(DocumentSnapshot primary, DocumentSnapshot fallback) {
        for (DocumentSnapshot snapshot : new DocumentSnapshot[]{primary, fallback}) {
            if (snapshot == null || !snapshot.exists()) continue;
            Boolean explicit = snapshot.getBoolean("rastreamentoClienteHabilitado");
            if (explicit == null) explicit = snapshot.getBoolean("entrega.rastreamentoClienteHabilitado");
            if (explicit != null) return explicit;
        }
        return true;
    }

    private static boolean routeTrackingEnabledForOrder(DocumentSnapshot route, String orderId) {
        if (route != null && route.exists()) {
            Object raw = route.get("rastreamentoPedidosHabilitados");
            if (raw instanceof List) {
                for (Object value : (List<?>) raw) {
                    if (orderId.equals(String.valueOf(value))) return true;
                }
                return false;
            }
        }
        return customerTrackingEnabled(route, null);
    }

    private static boolean routeHasCustomerTracking(DocumentSnapshot route) {
        if (route != null && route.exists()) {
            Object raw = route.get("rastreamentoPedidosHabilitados");
            if (raw instanceof List) return !((List<?>) raw).isEmpty();
        }
        return customerTrackingEnabled(route, null);
    }

    private DocumentReference pedidoRef(DocumentSnapshot r) {
        String id = pedidoId(r);
        return id.isEmpty() ? null : db.collection("pedidos").document(id);
    }

    private DocumentReference rotaRef(DocumentSnapshot r) {
        String id = first(r, "rotaEntregaId", "rotaId");
        return id.isEmpty() ? null : db.collection("rotas_entrega").document(id);
    }

    private static String pedidoId(DocumentSnapshot r) {
        String id = first(r, "pedidoId", "orderId");
        if (id.startsWith("rota_")) return "";
        return id;
    }

    private static Map<String, Object> stageMap(String driverId, String rideId, String status, String deliveryStatus, String timestampField) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("statusCorrida", status);
        m.put("statusEntregador", status);
        m.put("statusEntrega", deliveryStatus);
        String upState = UpState.canonicalFor(status, deliveryStatus);
        m.put("upProtocolVersion", UpState.PROTOCOL_VERSION);
        m.put("upState", upState);
        if (UpState.TO_STORE.name().equals(upState) || UpState.AT_STORE.name().equals(upState)) {
            m.put("upRouteOpen", true);
            m.put("pickupConfirmed", false);
        } else if (UpState.TO_CUSTOMER.name().equals(upState) || UpState.AT_CUSTOMER.name().equals(upState) || UpState.DELIVERED.name().equals(upState)) {
            m.put("upRouteOpen", false);
            m.put("pickupConfirmed", true);
        }
        m.put("entregadorId", driverId);
        m.put("driverId", driverId);
        m.put("entregadorUid", driverId);
        m.put("corridaAtualId", rideId);
        m.put("corridaAtiva", true);
        m.put("emCorrida", true);
        m.put("statusAtualizadoEm", FieldValue.serverTimestamp());
        m.put("updatedAt", FieldValue.serverTimestamp());
        if (timestampField != null && !timestampField.isEmpty()) m.put(timestampField, FieldValue.serverTimestamp());
        return m;
    }

    private static Map<String, Object> pedidoStageMap(String driverId, String rideId, String deliveryStatus, String timestampField) {
        Map<String, Object> m = new HashMap<>();
        m.put("statusEntrega", deliveryStatus);
        m.put("entrega.status", deliveryStatus);
        String upState = UpState.canonicalFor(deliveryStatus, deliveryStatus);
        m.put("upProtocolVersion", UpState.PROTOCOL_VERSION);
        m.put("upState", upState);
        if (UpState.TO_STORE.name().equals(upState) || UpState.AT_STORE.name().equals(upState)) {
            m.put("upRouteOpen", true);
            m.put("pickupConfirmed", false);
        } else if (UpState.TO_CUSTOMER.name().equals(upState) || UpState.AT_CUSTOMER.name().equals(upState) || UpState.DELIVERED.name().equals(upState)) {
            m.put("upRouteOpen", false);
            m.put("pickupConfirmed", true);
        }
        String customerStatus = customerOrderStatus(deliveryStatus);
        if (!customerStatus.isEmpty()) {
            m.put("status", customerStatus);
            m.put("statusPedido", customerStatus);
            m.put("statusAtual", customerStatus);
        }
        m.put("entrega.entregadorId", driverId);
        m.put("entregadorId", driverId);
        m.put("driverId", driverId);
        m.put("entregadorUid", driverId);
        m.put("corridaAtualId", rideId);
        m.put("statusAtualizadoEm", FieldValue.serverTimestamp());
        m.put("atualizadoEm", FieldValue.serverTimestamp());
        m.put("updatedAt", FieldValue.serverTimestamp());
        if (timestampField != null && !timestampField.isEmpty()) m.put(timestampField, FieldValue.serverTimestamp());
        return m;
    }

    private static String customerOrderStatus(String deliveryStatus) {
        String s = deliveryStatus == null ? "" : deliveryStatus.trim().toUpperCase(Locale.ROOT);
        switch (s) {
            case "AGUARDANDO_ENTREGADOR":
            case "AGUARDANDO_DECISAO_GESTOR": return "BUSCANDO_ENTREGADOR";
            case "ENTREGADOR_A_CAMINHO_LOJA":
            case "ACEITA": return "A_CAMINHO_LOJA";
            case "ENTREGADOR_CHEGOU_LOJA":
            case "COLETANDO": return "COLETANDO";
            case "SAIU_PARA_ENTREGA":
            case "EM_ENTREGA": return "A_CAMINHO_CLIENTE";
            case "ENTREGADOR_CHEGOU_CLIENTE":
            case "NO_CLIENTE": return "ENTREGADOR_NO_LOCAL";
            case "ENTREGUE": return "ENTREGUE";
            default: return "";
        }
    }

    private static Map<String, Object> eventMap(String driverId, String rideId, DocumentSnapshot r, String type, String reason) {
        Map<String, Object> e = new HashMap<>();
        e.put("tipo", type);
        e.put("motivo", reason);
        e.put("origem", "APP_ENTREGADOR");
        e.put("entregadorId", driverId);
        e.put("entregadorUid", driverId);
        e.put("missaoId", rideId);
        e.put("pedidoId", pedidoId(r));
        e.put("rotaId", first(r, "rotaEntregaId", "rotaId"));
        e.put("criadoEm", FieldValue.serverTimestamp());
        e.put("createdAt", FieldValue.serverTimestamp());
        e.put("appVersion", BuildConfig.VERSION_NAME);
        return e;
    }

    private static double firstNumber(DocumentSnapshot d, String... fields) {
        for (String f : fields) {
            Object o = d.get(f);
            if (o instanceof Number) return ((Number) o).doubleValue();
            if (o instanceof String) {
                try { return Double.parseDouble(((String) o).replace(",", ".")); } catch (Exception ignored) {}
            }
        }
        return 0d;
    }

    static String first(DocumentSnapshot d, String... fields) {
        for (String f : fields) {
            String v = s(d, f);
            if (!v.isEmpty() && !"null".equalsIgnoreCase(v)) return v;
        }
        return "";
    }

    static String s(DocumentSnapshot d, String field) {
        if (d == null) return "";
        Object o = d.get(field);
        return o == null ? "" : String.valueOf(o);
    }
}
