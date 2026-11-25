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
    private String phase = "";
    private double customerLat = Double.NaN, customerLng = Double.NaN;
    private long requestInterval = 0L;
    private boolean forceNext;
    private boolean requesting, inFlight;
    private long lastAt, lastSuccess;
    private int generation;
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
            lastAt=0;lastSuccess=0;generation++;inFlight=false;
            lastSent=null; phase=""; forceNext=true;
        }
        String nextPhase=intent.getStringExtra("mission_state");
        if(nextPhase==null) nextPhase="";
        if(!phase.equals(nextPhase)) { phase=nextPhase; forceNext=true; }
        customerLat=intent.getDoubleExtra("customer_lat",Double.NaN);
        customerLng=intent.getDoubleExtra("customer_lng",Double.NaN);
        long desired=("TO_CUSTOMER".equals(phase)||"AT_CUSTOMER".equals(phase))?5000L:20000L;
        if(requesting && requestInterval!=desired) stopUpdates();
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
        boolean enRoute="TO_CUSTOMER".equals(phase)||"AT_CUSTOMER".equals(phase);
        requestInterval=enRoute?5000L:20000L;
        LocationRequest request = new LocationRequest.Builder(enRoute?Priority.PRIORITY_HIGH_ACCURACY:Priority.PRIORITY_BALANCED_POWER_ACCURACY,requestInterval)
            .setMinUpdateIntervalMillis(requestInterval)
            .setMaxUpdateAgeMillis(0L)
            .build();
        callback = new LocationCallback() {
            @Override public void onLocationResult(LocationResult result) {
                if(result==null) return;
                maybeSend(result.getLastLocation());
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
        long age=(SystemClock.elapsedRealtimeNanos()-loc.getElapsedRealtimeNanos())/1000000L;
        boolean customerPhase="TO_CUSTOMER".equals(phase)||"AT_CUSTOMER".equals(phase);
        if(customerPhase) {
            if(!GpsPolicy.validDeliveryFix(age,loc.getAccuracy()))return;
            double meters=Double.NaN;
            if(Double.isFinite(customerLat)&&Double.isFinite(customerLng)&&Math.abs(customerLat)<=90&&Math.abs(customerLng)<=180&&(customerLat!=0||customerLng!=0)) {
                float[] d=new float[1];
                Location.distanceBetween(loc.getLatitude(),loc.getLongitude(),customerLat,customerLng,d);
                meters=d[0];
            }
            long interval=GpsPolicy.deliveryInterval(phase,meters);
            if(!forceNext&&lastAt!=0&&now-lastAt<interval)return;
        } else if(!GpsPolicy.shouldSend(now-lastAt,now-lastSuccess,
            lastSent==null?0:lastSent.distanceTo(loc),age,loc.getAccuracy(),lastSent!=null))return;
        forceNext=false;
        final int sentGeneration=generation;
        lastAt=now;
        inFlight=true;
        backend.driver("location",Backend.args(
            "mission_id",mission,"lat",loc.getLatitude(),"lng",loc.getLongitude(),
            "accuracy",loc.getAccuracy()),new Backend.Callback(){
                @Override public void onSuccess(JSONObject response) {
                    if(sentGeneration!=generation)return;
                    inFlight=false;
                    if(response.optBoolean("history_saved",true)){lastSuccess=SystemClock.elapsedRealtime();lastSent=new Location(loc);}
                }
                @Override public void onError(String message) {
                    if(sentGeneration!=generation)return;
                    inFlight=false;
                    if(message.contains("somente em uma corrida ativa") || message.contains("vinculada")
                       ||message.contains("aprovar seu cadastro")||message.contains("Sessão"))stopSelf();
                }
            });
    }

    private void stopUpdates() {
        if(fused!=null && callback!=null)fused.removeLocationUpdates(callback);
        requesting=false;callback=null;
    }
    @Override public void onDestroy(){generation++;stopUpdates();super.onDestroy();}
    @Nullable @Override public IBinder onBind(Intent i){return null;}
}
