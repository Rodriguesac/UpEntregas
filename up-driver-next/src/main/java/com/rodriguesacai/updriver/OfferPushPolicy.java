package com.rodriguesacai.updriver;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Accepts only independent UP offer events from the app's FCM channel.
 * A push is a hint to refresh the verified offer API; it is never authority
 * to accept a ride or display protected order details.
 */
public final class OfferPushPolicy {
    private static final Pattern UUID = Pattern.compile("(?i)^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$");
    private OfferPushPolicy() {}

    public static boolean isIndependentOffer(Map<String,String> data) {
        if (data == null || !"independent".equals(data.get("upSource"))) return false;
        String offerId = data.get("offerId");
        String rideId = data.get("rideId");
        return offerId != null && UUID.matcher(offerId).matches()
            && rideId != null && rideId.startsWith("supabase:")
            && UUID.matcher(rideId.substring("supabase:".length())).matches();
    }

    public static String title(String supplied) {
        return clean(supplied, "Nova corrida UP", 80);
    }

    public static String body(String supplied) {
        return clean(supplied, "Uma entrega está disponível. Abra o UP Entregas.", 180);
    }

    public static int notificationId(String offerId) {
        return 100 + (offerId.hashCode() & 0x3fffffff);
    }

    private static String clean(String supplied, String fallback, int maxLength) {
        if (supplied == null) return fallback;
        StringBuilder out = new StringBuilder(Math.min(supplied.length(), maxLength));
        for (int i=0; i<supplied.length() && out.length()<maxLength; i++) {
            char c = supplied.charAt(i);
            out.append(Character.isISOControl(c) ? ' ' : c);
        }
        String text=out.toString().trim();
        return text.isEmpty() ? fallback : text;
    }
}
