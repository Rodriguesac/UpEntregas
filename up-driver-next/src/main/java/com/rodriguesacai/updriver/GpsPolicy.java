package com.rodriguesacai.updriver;

/** Pure policy so stationary GPS and reconnect behavior can be tested on the JVM. */
public final class GpsPolicy {
    private GpsPolicy() {}
    public static long deliveryInterval(String state, double meters) {
        if (!"TO_CUSTOMER".equals(state) || !Double.isFinite(meters) || meters < 0) return 30000L;
        if (meters <= 50) return 5000L;
        if (meters <= 100) return 15000L;
        return 30000L;
    }
    public static boolean validDeliveryFix(long age, float accuracy) {
        return age >= 0 && age <= 15000 && Float.isFinite(accuracy) && accuracy >= 0 && accuracy <= 120;
    }
    public static boolean shouldSend(long sinceAttempt, long sinceSuccess, float meters,
                                     long locationAge, float accuracy, boolean hasLast) {
        if (locationAge < 0 || locationAge > 60000 || !Float.isFinite(accuracy)
                || accuracy < 0 || accuracy > 120 || sinceAttempt < 12000) return false;
        return !hasLast || meters >= 30 || sinceSuccess >= 60000;
    }
}
