package com.rodriguesacai.updriver;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

/** Bootstraps a physically separate Android package. No privileged server key lives here. */
public final class UpDriverApp extends Application {
    public static final String CHANNEL_OFFERS = "up_offers_audible_v2";
    public static final String CHANNEL_ONLINE = "up_online_monitor";
    public static final String CHANNEL_LOCATION = "up_mission_location";

    @Override public void onCreate() {
        super.onCreate();
        // Preview uses the already-established Firebase project identity. Before public distribution,
        // register THIS package in Firebase and ship its dedicated Android configuration.
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.UP_FIREBASE_APP_ID)
                .setApiKey(BuildConfig.UP_FIREBASE_API_KEY)
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
            offers.enableVibration(true);
            offers.setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build());
            mgr.createNotificationChannel(offers);
            mgr.createNotificationChannel(new NotificationChannel(CHANNEL_ONLINE,"UP online",NotificationManager.IMPORTANCE_LOW));
            NotificationChannel location = new NotificationChannel(CHANNEL_LOCATION,
                "GPS da corrida ativa", NotificationManager.IMPORTANCE_LOW);
            location.setDescription("Localização somente durante uma missão aceita");
            mgr.createNotificationChannel(location);
        }
    }
}
