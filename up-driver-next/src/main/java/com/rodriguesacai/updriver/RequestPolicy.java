package com.rodriguesacai.updriver;

/** Bounded retries only for reads or operations with server-side idempotency. */
public final class RequestPolicy {
    private RequestPolicy() {}
    public static final int MAX_RESPONSE_BYTES = 256 * 1024;

    public static boolean mayRetry(String action, int status, int attempt) {
        if (attempt >= 2 || !(status == 0 || status == 408 || status == 429 || status >= 500)) return false;
        switch (action) {
            case "offer": case "mission": case "history": case "wallet":
            case "session": case "equipment_status": case "status_sync":
            case "accept": case "stage": case "pickup": case "finish":
                return true;
            default: return false;
        }
    }

    public static long delayMillis(int attempt, String retryAfter) {
        if (retryAfter != null) try {
            long seconds = Long.parseLong(retryAfter);
            if (seconds >= 0) return Math.max(1000L, Math.min(30L, seconds) * 1000L);
        } catch (NumberFormatException ignored) {}
        return attempt == 0 ? 1000L : 3000L;
    }
}
