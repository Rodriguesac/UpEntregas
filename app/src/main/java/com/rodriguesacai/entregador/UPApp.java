package com.rodriguesacai.entregador;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public class UPApp extends Application {
    private static int startedActivities = 0;

    @Override public void onCreate() {
        super.onCreate();
        ThemePrefs.applySavedTheme(this);
        NotificationHelper.createChannels(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
            @Override public void onActivityStarted(Activity activity) { startedActivities++; }
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) { startedActivities = Math.max(0, startedActivities - 1); }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    /** True quando alguma tela do UP está visível para o usuário. */
    public static boolean isAppVisible() { return startedActivities > 0; }
}
