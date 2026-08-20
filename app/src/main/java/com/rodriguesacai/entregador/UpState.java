package com.rodriguesacai.entregador;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Locale;

/** Contrato operacional único compartilhado por UP Entregas, UP Central e Gestor. */
public enum UpState {
    PLANNED,
    OFFER_PENDING,
    TO_STORE,
    AT_STORE,
    TO_CUSTOMER,
    AT_CUSTOMER,
    DELIVERED,
    CANCELED,
    UNKNOWN;

    public static final int PROTOCOL_VERSION = 3;

    public boolean beforePickup() {
        return this == TO_STORE || this == AT_STORE;
    }

    public boolean deliveryPhase() {
        return this == TO_CUSTOMER || this == AT_CUSTOMER;
    }

    public boolean terminal() {
        return this == DELIVERED || this == CANCELED;
    }

    public static UpState from(DocumentSnapshot d) {
        if (d == null || !d.exists()) return UNKNOWN;
        String explicit = first(d, "upState", "estadoOperacionalUP", "upMissionState");
        UpState e = parse(explicit);
        if (e != UNKNOWN) return e;
        return fromLegacy(first(d, "status"), first(d, "statusEntrega"), first(d, "statusCorrida"));
    }

    public static UpState fromLegacy(String status, String delivery, String rideStatus) {
        String raw = ((status == null ? "" : status) + " " +
                (delivery == null ? "" : delivery) + " " +
                (rideStatus == null ? "" : rideStatus)).toUpperCase(Locale.ROOT);
        if (containsAny(raw, "ENTREGUE", "FINALIZ", "CONCLUID")) return DELIVERED;
        if (containsAny(raw, "CANCEL", "REJEIT", "EXPIR")) return CANCELED;
        if (containsAny(raw, "CHEGOU_CLIENTE", "NO_CLIENTE", "ENTREGADOR_NO_LOCAL")) return AT_CUSTOMER;
        if (containsAny(raw, "SAIU_PARA_ENTREGA", "A_CAMINHO_CLIENTE", "EM_ENTREGA", "DELIVERY")) return TO_CUSTOMER;
        if (containsAny(raw, "CHEGOU_LOJA", "COLETANDO", "ENTREGADOR_CHEGOU_LOJA")) return AT_STORE;
        if (containsAny(raw, "A_CAMINHO_LOJA", "ENTREGADOR_A_CAMINHO_LOJA", "PICKUP", "ACEITA")) return TO_STORE;
        if (containsAny(raw, "OFERTA", "AGUARDANDO_ENTREGADOR", "BUSCANDO_ENTREGADOR")) return OFFER_PENDING;
        if (containsAny(raw, "PLANEJADA", "PLANNED")) return PLANNED;
        return UNKNOWN;
    }

    public static String canonicalFor(String status, String deliveryStatus) {
        return fromLegacy(status, deliveryStatus, status).name();
    }

    public String friendly() {
        switch (this) {
            case OFFER_PENDING: return "Aguardando resposta";
            case TO_STORE: return "A caminho da loja";
            case AT_STORE: return "Na loja";
            case TO_CUSTOMER: return "Em rota";
            case AT_CUSTOMER: return "No cliente";
            case DELIVERED: return "Entregue";
            case CANCELED: return "Cancelada";
            case PLANNED: return "Planejada";
            default: return "Atualizando";
        }
    }

    private static UpState parse(String v) {
        if (v == null) return UNKNOWN;
        try { return valueOf(v.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return UNKNOWN; }
    }

    private static boolean containsAny(String raw, String... values) {
        for (String v : values) if (raw.contains(v)) return true;
        return false;
    }

    private static String first(DocumentSnapshot d, String... keys) {
        for (String k : keys) {
            Object v = d.get(k);
            if (v != null && !String.valueOf(v).trim().isEmpty()) return String.valueOf(v).trim();
        }
        return "";
    }
}
