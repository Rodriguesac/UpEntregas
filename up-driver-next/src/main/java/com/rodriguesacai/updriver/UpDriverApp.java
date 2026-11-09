package com.rodriguesacai.updriver;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

/** Bootstraps a physically separate Android package. No privileged server key lives here. */
public final class UpDriverApp extends Application {
    public static final String CHANNEL_OFFERS = "up_offers";
    public static final String CHANNEL_LOCATION = "up_mission_location";

    @Override public void onCreate() {
        super.onCreate();
        // Preview uses the already-established Firebase project identity. Before public distribution,
        // register THIS package in Firebase and ship its dedicated Android configuration.
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                .setApplicationId("1:1010835711502:android:979e8dadff59ad17d6be5f")
                .setApiKey("AIzaSyBkCcdbR8Z9V9DB0wAJfC_YiRaNe8nnaA4")
                .setProjectId("rodrigues-d6566")
                .setGcmSenderId("1010835711502")
                .build();
            FirebaseApp.initializeApp(this, options);
        }
        NotificationManager mgr = getSystemService(NotificationManager.class);
        if (mgr != null && Build.VERSION.SDK_INT >= 26) {
            NotificationChannel offers = new NotificationChannel(CHANNEL_OFFERS,
                "Novas corridas", NotificationManager.IMPORTANCE_HIGH);
            offers.setDescription("Ofertas de entrega e atualizações de corridas do UP");
            mgr.createNotificationChannel(offers);
            NotificationChannel location = new NotificationChannel(CHANNEL_LOCATION,
                "GPS da corrida ativa", NotificationManager.IMPORTANCE_LOW);
            location.setDescription("Localização somente durante uma missão aceita");
            mgr.createNotificationChannel(location);
        }
    }
}
