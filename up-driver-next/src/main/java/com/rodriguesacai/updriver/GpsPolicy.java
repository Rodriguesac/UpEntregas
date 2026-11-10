package com.rodriguesacai.updriver;

/** Pure policy so stationary GPS and reconnect behavior can be tested on the JVM. */
public final class GpsPolicy {
    private GpsPolicy() {}
    public static boolean shouldSend(long sinceAttempt, long sinceSuccess, float meters,
                                     long locationAge, float accuracy, boolean hasLast) {
        if (locationAge < 0 || locationAge > 60000 || !Float.isFinite(accuracy)
                || accuracy < 0 || accuracy > 120 || sinceAttempt < 12000) return false;
        return !hasLast || meters >= 30 || sinceSuccess >= 60000;
    }
}
