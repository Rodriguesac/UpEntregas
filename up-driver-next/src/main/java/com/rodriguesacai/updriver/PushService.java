package com.rodriguesacai.updriver;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import org.json.JSONObject;

/** Push is a wake-up notification; the server remains the only authority on an offer. */
public final class PushService extends FirebaseMessagingService {
    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        if (!BuildConfig.UP_FIREBASE_ANDROID_REGISTERED) return;
        if (token==null || token.isEmpty() ||
            FirebaseAuth.getInstance().getCurrentUser()==null) return;
        new Backend().driver("token",Backend.args("token",token),new Backend.Callback(){
            @Override public void onSuccess(JSONObject response){}
            @Override public void onError(String message){}
        });
    }

    @Override public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        if (!BuildConfig.UP_FIREBASE_ANDROID_REGISTERED) return;
        if(FirebaseAuth.getInstance().getCurrentUser()==null)return;
        if(Build.VERSION.SDK_INT>=33
          && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)
              !=PackageManager.PERMISSION_GRANTED) return;
        String title = message.getNotification()==null?"Nova informação do UP"
            :message.getNotification().getTitle();
        String body = message.getNotification()==null?"Abra o UP para consultar suas corridas."
            :message.getNotification().getBody();
        if(title==null||title.trim().isEmpty()) title="UP Entregas";
        if(body==null||body.trim().isEmpty())body="Consulte o aplicativo para verificar a próxima ação.";
        Intent launch=new Intent(this,MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending=PendingIntent.getActivity(this,45,launch,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notif=new NotificationCompat.Builder(this,UpDriverApp.CHANNEL_OFFERS)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title.length()>80?title.substring(0,80):title)
            .setContentText(body.length()>180?body.substring(0,180):body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build();
        NotificationManager mgr=getSystemService(NotificationManager.class);
        if(mgr!=null)mgr.notify((int)(System.currentTimeMillis()%30000)+100,notif);
    }
}
