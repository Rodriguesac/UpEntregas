package com.rodriguesacai.entregador;

import android.content.Context;
import android.content.SharedPreferences;

public final class Session {
    private static final String PREF = "up_entregas_session";
    private static final String KEY_DRIVER = "driver_id";
    private static final String KEY_RIDE = "ride_id";
    private static final String KEY_MISSION_TYPE = "mission_type";
    private static final String KEY_CUSTOMER_VISIBLE = "customer_visible";
    private Session() {}

    public static String getDriverId(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_DRIVER, "");
    }

    public static void saveDriverId(Context c, String id) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_DRIVER, id).apply();
    }

    public static String getRideId(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_RIDE, "");
    }

    public static void saveRideId(Context c, String id) {
        saveMission(c, "rides", id);
    }

    public static void saveMission(Context c, String type, String id) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(KEY_RIDE, id == null ? "" : id)
                .putString(KEY_MISSION_TYPE, type == null || type.isEmpty() ? "rides" : type)
                .apply();
    }

    public static String getMissionType(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_MISSION_TYPE, "rides");
    }

    public static void saveCustomerVisible(Context c, boolean visible) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_CUSTOMER_VISIBLE, visible).apply();
    }

    public static boolean isCustomerVisible(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_CUSTOMER_VISIBLE, false);
    }

    public static void clearRide(Context c) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY_RIDE).remove(KEY_MISSION_TYPE).remove(KEY_CUSTOMER_VISIBLE).apply();
    }

    public static void clear(Context c) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
