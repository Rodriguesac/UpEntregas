package com.rodriguesacai.updriver;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import org.json.JSONObject;

/** Explicit online session; polls only while the UI is hidden, with bounded request frequency. */
public final class OfferMonitorService extends Service {
    private static final int ONLINE_ID=7104;
    private static volatile boolean appVisible;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Backend backend=new Backend();
    private boolean running, pending;
    private int failures;
    private String uid="";
    public static void setAppVisible(boolean value){appVisible=value;}
    public static void start(Context context){
        try {ContextCompat.startForegroundService(context,new Intent(context,OfferMonitorService.class));}
        catch(Exception ignored){}
    }
    public static void stop(Context context){context.stopService(new Intent(context,OfferMonitorService.class));}
    private PendingIntent open(){
        Intent i=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this,72,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        com.google.firebase.auth.FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
        if(user==null){stopSelf();return START_NOT_STICKY;}
        uid=user.getUid();
        Notification notification=new NotificationCompat.Builder(this,UpDriverApp.CHANNEL_ONLINE)
            .setSmallIcon(android.R.drawable.ic_dialog_map).setContentTitle("UP Entregador • online")
            .setContentText("Aguardando ofertas; toque para voltar ao aplicativo.")
            .setContentIntent(open()).setOngoing(true).setOnlyAlertOnce(true).build();
        try {startForeground(ONLINE_ID,notification);} catch(Exception e){stopSelf();return START_NOT_STICKY;}
        if(!running){running=true;handler.post(poll);}
        return START_NOT_STICKY;
    }
    private final Runnable poll=new Runnable(){
        @Override public void run(){
            if(!running)return;
            com.google.firebase.auth.FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
            if(user==null||!uid.equals(user.getUid())){stopSelf();return;}
            if(appVisible||pending){handler.postDelayed(this,15000);return;}
            pending=true;
            backend.driver("offer",Backend.args(),new Backend.Callback(){
                @Override public void onSuccess(JSONObject response){
                    pending=false;if(!running)return;failures=0;
                    JSONObject d=response.optJSONObject("document");
                    JSONObject data=d==null?null:d.optJSONObject("data");
                    if(!appVisible && data!=null && data.optBoolean("ofertaAtiva",false)){
                        notifyOffer(OfferMonitorService.this,d.optString("id","").replace("supabase:",""),false);
                    }
                    handler.postDelayed(poll,30000);
                }
                @Override public void onError(String message){
                    pending=false;if(!running)return;
                    if(message.contains("Sessão")||message.contains("aprovar seu cadastro")){stopSelf();return;}
                    failures=Math.min(failures+1,3);handler.postDelayed(poll,30000L*failures);
                }
            });
        }
    };
    public static void notifyOffer(Context c,String id,boolean foreground){
        if(id==null||!id.matches("(?i)[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))return;
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        com.google.firebase.auth.FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
        if(user==null)return;
        android.content.SharedPreferences prefs=c.getSharedPreferences("up_offer_notifications",Context.MODE_PRIVATE);
        String key="last_id_"+user.getUid();
        if(id.equals(prefs.getString(key,"")))return;
        Intent i=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi=PendingIntent.getActivity(c,71,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new NotificationCompat.Builder(c,UpDriverApp.CHANNEL_OFFERS)
            .setSmallIcon(android.R.drawable.ic_dialog_map).setContentTitle("Nova corrida UP")
            .setContentText("Uma entrega está disponível. Toque para aceitar ou recusar.")
            .setContentIntent(pi).setAutoCancel(true).setOnlyAlertOnce(true)
            .setSilent(foreground).setPriority(NotificationCompat.PRIORITY_HIGH).build();
        NotificationManager mgr=c.getSystemService(NotificationManager.class);
        if(mgr!=null){try {mgr.notify(OfferPushPolicy.notificationId(id),notification);prefs.edit().putString(key,id).apply();}catch(SecurityException ignored){}}
    }
    @Override public void onDestroy(){running=false;handler.removeCallbacks(poll);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
