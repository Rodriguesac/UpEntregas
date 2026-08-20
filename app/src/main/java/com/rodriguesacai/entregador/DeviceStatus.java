package com.rodriguesacai.entregador;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

public final class DeviceStatus {
    public static final class Battery {
        public final int level;
        public final boolean charging;
        Battery(int level, boolean charging) {
            this.level = level;
            this.charging = charging;
        }
    }

    private DeviceStatus() {}

    public static Battery battery(Context context) {
        try {
            Intent i = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i == null) return new Battery(-1, false);
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int pct = level < 0 ? -1 : Math.max(0, Math.min(100, Math.round(level * 100f / Math.max(1, scale))));
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
            return new Battery(pct, charging);
        } catch (Exception e) {
            return new Battery(-1, false);
        }
    }
}
