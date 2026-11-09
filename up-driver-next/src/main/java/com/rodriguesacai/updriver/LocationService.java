package com.rodriguesacai.updriver;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONObject;

/** Visible mission-only GPS. Never activates on idle, closed or unassigned jobs. */
public final class LocationService extends Service {
    private static final int NOTIFICATION_ID = 7103;
    private final Backend backend = new Backend();
    private FusedLocationProviderClient fused;
    private LocationCallback callback;
    private String mission = "";
    private boolean requesting, inFlight;
    private long lastAt;
    private Location lastSent;

    @Override public void onCreate() {
        super.onCreate();
        fused = LocationServices.getFusedLocationProviderClient(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String incoming = intent==null?"":intent.getStringExtra("mission_id");
        if(incoming==null || !incoming.matches("(?i)[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
          || FirebaseAuth.getInstance().getCurrentUser()==null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if(!mission.equals(incoming)) {
            stopUpdates();
            mission=incoming;
            lastAt=0;
            lastSent=null;
        }
        try {
            startForeground(NOTIFICATION_ID, notification());
            startUpdates();
        } catch(Exception ex) {
            stopUpdates();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private Notification notification() {
        Intent open = new Intent(this,MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,21,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,UpDriverApp.CHANNEL_LOCATION)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("UP Entregador • corrida em andamento")
            .setContentText("GPS ativo somente enquanto a missão estiver em andamento")
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private boolean permitted() {
        return ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }

    private void startUpdates() {
        if(requesting || !permitted()) return;
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY,20000L)
            .setMinUpdateIntervalMillis(10000L)
            .setMinUpdateDistanceMeters(30f)
            .build();
        callback = new LocationCallback() {
            @Override public void onLocationResult(LocationResult result) {
                if(result==null) return;
                for(Location loc: result.getLocations()) maybeSend(loc);
            }
        };
        try {
            fused.requestLocationUpdates(request,callback,Looper.getMainLooper());
            requesting=true;
        } catch(SecurityException ex) { stopSelf(); }
    }

    private void maybeSend(Location loc) {
        if(loc==null||inFlight||mission.isEmpty()||!permitted()
          ||FirebaseAuth.getInstance().getCurrentUser()==null) return;
        if(!loc.hasAccuracy()||loc.getAccuracy()<0||loc.getAccuracy()>120) return;
        if(!Double.isFinite(loc.getLatitude())||!Double.isFinite(loc.getLongitude()))return;
        if(Math.abs(loc.getLatitude())<0.00001 && Math.abs(loc.getLongitude())<0.00001)return;
        long now=SystemClock.elapsedRealtime();
        if(now-lastAt<12000) return;
        if(lastSent!=null && lastSent.distanceTo(loc)<30 && now-lastAt<60000)return;
        inFlight=true;
        backend.driver("location",Backend.args(
            "mission_id",mission,"lat",loc.getLatitude(),"lng",loc.getLongitude(),
            "accuracy",loc.getAccuracy()),new Backend.Callback(){
                @Override public void onSuccess(JSONObject response) {
                    inFlight=false;lastAt=SystemClock.elapsedRealtime();lastSent=new Location(loc);
                }
                @Override public void onError(String message) {
                    inFlight=false; lastAt=SystemClock.elapsedRealtime();
                    if(message.contains("somente em uma corrida ativa") || message.contains("vinculada"))stopSelf();
                }
            });
    }

    private void stopUpdates() {
        if(fused!=null && callback!=null)fused.removeLocationUpdates(callback);
        requesting=false;callback=null;
    }
    @Override public void onDestroy(){stopUpdates();super.onDestroy();}
    @Nullable @Override public IBinder onBind(Intent i){return null;}
}
