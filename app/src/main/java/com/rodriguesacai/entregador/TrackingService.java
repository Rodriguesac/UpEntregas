package com.rodriguesacai.entregador;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ServiceCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.ListenerRegistration;

public class TrackingService extends Service {
    public static final String EXTRA_RIDE = "ride_id";
    public static final String EXTRA_CUSTOMER_VISIBLE = "customer_visible";
    public static final String EXTRA_MISSION_TYPE = "mission_type";

    private FusedLocationProviderClient client;
    private LocationCallback callback;
    private String driverId = "";
    private String rideId = "";
    private boolean customerVisible = false;
    private boolean customerTrackingEnabled = true;
    private String missionType = "rides";
    private ListenerRegistration missionListener;
    private final DriverRepository repo = new DriverRepository();
    private Boolean lastDeliveryPhase;
    private double traveledDeliveryMeters = 0d;
    private double lastDistanceLat = Double.NaN;
    private double lastDistanceLng = Double.NaN;
    private float lastDistanceAccuracy = 0f;
    private long lastDistanceTime = 0L;

    @Override public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannels(this);
        client = LocationServices.getFusedLocationProviderClient(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        driverId = Session.getDriverId(this);
        if (intent != null) {
            String x = intent.getStringExtra(EXTRA_RIDE);
            if (x != null && !x.isEmpty()) rideId = x;
            customerVisible = intent.getBooleanExtra(EXTRA_CUSTOMER_VISIBLE, Session.isCustomerVisible(this));
            String mt = intent.getStringExtra(EXTRA_MISSION_TYPE);
            if (mt != null && !mt.isEmpty()) missionType = mt;
        }
        if (rideId.isEmpty()) rideId = Session.getRideId(this);
        if (intent == null) customerVisible = Session.isCustomerVisible(this);
        if (intent == null || missionType.isEmpty()) missionType = Session.getMissionType(this);

        if (driverId.isEmpty() || rideId.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        Session.saveMission(this, missionType, rideId);
        Session.saveCustomerVisible(this, customerVisible);
        loadDistanceState();
        int type = Build.VERSION.SDK_INT >= 29 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0;
        ServiceCompat.startForeground(this, NotificationHelper.TRACKING_ID,
                NotificationHelper.trackingNotification(this, rideId, missionType, trackingText()), type);
        startLocationUpdates();
        startMissionWatch();
        return START_STICKY;
    }

    private void startMissionWatch() {
        if (missionListener != null) { missionListener.remove(); missionListener = null; }
        if (rideId.isEmpty()) return;
        DriverRepository.RideCallback callback = new DriverRepository.RideCallback() {
            @Override public void onRide(com.google.firebase.firestore.DocumentSnapshot d) {
                if (d == null || !d.exists()) {
                    stopSelf();
                    return;
                }
                UpState state = UpState.from(d);
                if (state.terminal()) {
                    stopSelf();
                    return;
                }
                boolean deliveryPhase = state.deliveryPhase();
                if (lastDeliveryPhase == null) {
                    if (!deliveryPhase) resetDistanceState();
                } else if (deliveryPhase && !lastDeliveryPhase) {
                    resetDistanceState();
                }
                lastDeliveryPhase = deliveryPhase;
                customerVisible = deliveryPhase;
                Boolean explicit = d.getBoolean("rastreamentoClienteHabilitado");
                customerTrackingEnabled = explicit == null || explicit;
                if ("rotas_entrega".equals(missionType)) {
                    Object enabledOrders = d.get("rastreamentoPedidosHabilitados");
                    if (enabledOrders instanceof java.util.List) {
                        customerTrackingEnabled = !((java.util.List<?>) enabledOrders).isEmpty();
                    }
                }
                Session.saveCustomerVisible(TrackingService.this, customerVisible);
                updateForegroundNotification();

                if ("rotas_entrega".equals(missionType) && Boolean.TRUE.equals(d.getBoolean("complementoOfertaAtiva")) && !Boolean.FALSE.equals(d.getBoolean("rotaAberta"))) {
                    OfferAlertPlayer.start(TrackingService.this, "complemento:" + rideId);
                    NotificationHelper.notifyNewRoute(TrackingService.this, rideId, "+1",
                            "Novo pedido disponível para sua rota • abra o UP para aceitar ou recusar.");
                } else {
                    OfferAlertPlayer.stop();
                }
            }
            @Override public void onError(Exception e) { }
        };
        missionListener = "rotas_entrega".equals(missionType)
                ? repo.listenRoute(rideId, callback)
                : repo.listenRide(rideId, callback);
    }

    private String trackingText() {
        if (customerVisible && customerTrackingEnabled) {
            return "A caminho do cliente • mapa compartilhado durante a entrega";
        }
        if (customerVisible) {
            return "A caminho do cliente • GPS interno da missão ativo";
        }
        return "A caminho da loja • GPS interno da missão ativo";
    }

    private void updateForegroundNotification() {
        int type = Build.VERSION.SDK_INT >= 29 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0;
        ServiceCompat.startForeground(this, NotificationHelper.TRACKING_ID,
                NotificationHelper.trackingNotification(this, rideId, missionType, trackingText()), type);
    }

    private void startLocationUpdates() {
        if (callback != null) client.removeLocationUpdates(callback);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15000)
                .setMinUpdateIntervalMillis(8000)
                .setMinUpdateDistanceMeters(10f)
                .build();
        callback = new LocationCallback() {
            @Override public void onLocationResult(LocationResult r) {
                Location l = r.getLastLocation();
                if (l != null && !driverId.isEmpty() && !rideId.isEmpty()) {
                    updateTraveledDistance(l);
                    DriverRepository repo = new DriverRepository();
                    repo.saveMissionLocation(driverId, l.getLatitude(), l.getLongitude(),
                            l.getAccuracy(), l.getSpeed(), l.getBearing(), rideId, missionType,
                            customerVisible, traveledDeliveryMeters);
                    DeviceStatus.Battery battery = DeviceStatus.battery(TrackingService.this);
                    if (battery.level >= 0) repo.saveDeviceTelemetry(driverId, battery.level, battery.charging);
                }
            }
        };
        client.requestLocationUpdates(req, callback, getMainLooper());
    }

    /**
     * Soma apenas o trecho depois da retirada. O filtro de precisão, deslocamento mínimo e
     * velocidade impossível reduz o acúmulo de ruído quando o entregador está parado.
     */
    private void updateTraveledDistance(Location current) {
        if (!customerVisible || !current.hasAccuracy() || current.getAccuracy() > 80f) return;
        long time = current.getTime() > 0 ? current.getTime() : System.currentTimeMillis();
        if (Double.isNaN(lastDistanceLat) || Double.isNaN(lastDistanceLng)) {
            rememberDistancePoint(current, time);
            return;
        }

        float[] result = new float[1];
        Location.distanceBetween(lastDistanceLat, lastDistanceLng,
                current.getLatitude(), current.getLongitude(), result);
        double delta = result[0];
        double noiseFloor = Math.max(8d, (lastDistanceAccuracy + current.getAccuracy()) * 0.35d);
        double elapsedSeconds = Math.max(1d, Math.abs(time - lastDistanceTime) / 1000d);
        double maximumPlausible = Math.max(120d, elapsedSeconds * 45d);
        if (delta < noiseFloor) return;
        if (delta > maximumPlausible) return;

        traveledDeliveryMeters += delta;
        rememberDistancePoint(current, time);
    }

    private void rememberDistancePoint(Location location, long time) {
        lastDistanceLat = location.getLatitude();
        lastDistanceLng = location.getLongitude();
        lastDistanceAccuracy = location.getAccuracy();
        lastDistanceTime = time;
        SharedPreferences.Editor editor = distancePreferences().edit();
        String key = distanceKey();
        editor.putLong(key + "_meters", Double.doubleToRawLongBits(traveledDeliveryMeters));
        editor.putLong(key + "_lat", Double.doubleToRawLongBits(lastDistanceLat));
        editor.putLong(key + "_lng", Double.doubleToRawLongBits(lastDistanceLng));
        editor.putFloat(key + "_accuracy", lastDistanceAccuracy);
        editor.putLong(key + "_time", lastDistanceTime);
        editor.apply();
    }

    private void loadDistanceState() {
        SharedPreferences prefs = distancePreferences();
        String key = distanceKey();
        traveledDeliveryMeters = Double.longBitsToDouble(prefs.getLong(key + "_meters", Double.doubleToRawLongBits(0d)));
        lastDistanceLat = Double.longBitsToDouble(prefs.getLong(key + "_lat", Double.doubleToRawLongBits(Double.NaN)));
        lastDistanceLng = Double.longBitsToDouble(prefs.getLong(key + "_lng", Double.doubleToRawLongBits(Double.NaN)));
        lastDistanceAccuracy = prefs.getFloat(key + "_accuracy", 0f);
        lastDistanceTime = prefs.getLong(key + "_time", 0L);
    }

    private void resetDistanceState() {
        traveledDeliveryMeters = 0d;
        lastDistanceLat = Double.NaN;
        lastDistanceLng = Double.NaN;
        lastDistanceAccuracy = 0f;
        lastDistanceTime = 0L;
        String key = distanceKey();
        distancePreferences().edit()
                .remove(key + "_meters")
                .remove(key + "_lat")
                .remove(key + "_lng")
                .remove(key + "_accuracy")
                .remove(key + "_time")
                .apply();
    }

    private SharedPreferences distancePreferences() {
        return getSharedPreferences("up_tracking_metrics", MODE_PRIVATE);
    }

    private String distanceKey() {
        return missionType + "_" + rideId;
    }

    @Override public void onDestroy() {
        if (callback != null) client.removeLocationUpdates(callback);
        if (missionListener != null) missionListener.remove();
        OfferAlertPlayer.stop();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
