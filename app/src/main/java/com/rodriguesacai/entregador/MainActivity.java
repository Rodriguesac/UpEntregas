package com.rodriguesacai.entregador;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private final DriverRepository repo = new DriverRepository();
    private String driverId = "";
    private String rideId = "";
    private DocumentSnapshot ride;
    private ListenerRegistration offerListener;
    private ListenerRegistration routeOfferListener;
    private ListenerRegistration currentRideListener;
    private String missionType = "rides";
    private int batteryLevel = -1;
    private boolean batteryCharging = false;
    private boolean hasCash = false;
    private double cashAvailable = 0d;
    private boolean hasMachine = false;
    private String machineTypes = "";
    private DocumentSnapshot upConfig;
    private int lastRouteStopCount = 0;
    private LinearLayout root;
    private boolean online = false;
    private String driverName = "";
    private CountDownTimer offerTimer;
    private InAppPanel activePanel;
    // 0 Início, 1 Corridas, 2 Ganhos, 3 Conta, 4 Notificações, 5 Perfil, 6 Pix, 7 Operação
    private int currentTab = 0;
    private int deepReturnTab = 0;

    private final ActivityResultLauncher<Intent> loginLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                driverId = Session.getDriverId(this);
                if (driverId.isEmpty()) finish();
                else startApp();
            });

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.color(this, R.color.up_bg));
        getWindow().setNavigationBarColor(Ui.color(this, R.color.up_surface));
        if (!ThemePrefs.isDark(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        ensureLogin();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!driverId.isEmpty()) publishTelemetry();
        if (!driverId.isEmpty() && root != null) render();
    }

    @Override public void onBackPressed() {
        if (activePanel != null && activePanel.isShowing()) {
            activePanel.dismiss();
            return;
        }
        if (currentTab >= 4) {
            currentTab = deepReturnTab;
            render();
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String incoming = missionIdFromIntent(intent);
        String type = intent == null ? "rides" : intent.getStringExtra("mission_type");
        if (type == null || type.isEmpty()) type = intent != null && intent.getStringExtra("route_id") != null ? "rotas_entrega" : "rides";
        if (!incoming.isEmpty() && !driverId.isEmpty()) loadIncomingMission(incoming, type);
    }

    private void ensureLogin() {
        driverId = Session.getDriverId(this);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) { Session.clear(this); driverId = ""; }
        if (!driverId.isEmpty() && FirebaseAuth.getInstance().getCurrentUser() != null && !driverId.equals(FirebaseAuth.getInstance().getCurrentUser().getUid())) { Session.clear(this); driverId = ""; }
        if (driverId.isEmpty()) {
            loginLauncher.launch(new Intent(this, LoginActivity.class));
        } else startApp();
    }

    private void startApp() {
        publishTelemetry();
        repo.loadUpConfig(new DriverRepository.DriverCallback() {
            @Override public void onResult(DocumentSnapshot d) { upConfig = d != null && d.exists() ? d : null; }
            @Override public void onError(Exception e) { upConfig = null; }
        });
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(t -> repo.saveToken(driverId, t));
        repo.loadDriver(driverId, new DriverRepository.DriverCallback() {
            @Override public void onResult(DocumentSnapshot d) {
                driverName = DriverRepository.first(d, "nomeCompleto", "nome");
                online = Boolean.TRUE.equals(d.getBoolean("online"));
                hasCash = Boolean.TRUE.equals(d.getBoolean("temTroco"));
                cashAvailable = numberFrom(d, "trocoDisponivel");
                hasMachine = Boolean.TRUE.equals(d.getBoolean("temMaquininha"));
                machineTypes = DriverRepository.first(d, "maquininhaTipos");
                String current = DriverRepository.first(d, "corridaAtualId", "currentRideId", "rideAtualId", "missaoAtualId");
                String currentType = DriverRepository.first(d, "missaoAtualTipo");
                String sessionMission = Session.getRideId(MainActivity.this);
                String sessionType = Session.getMissionType(MainActivity.this);
                if (current.isEmpty()) { current = sessionMission; currentType = sessionType; }
                if (currentType.isEmpty()) currentType = "rides";
                if (!current.isEmpty()) {
                    currentTab = 1;
                    rideId = current;
                    missionType = currentType;
                    Session.saveMission(MainActivity.this, missionType, current);
                    if ("rotas_entrega".equals(missionType)) {
                        repo.loadRoute(current).addOnSuccessListener(x -> {
                            ride = x.exists() ? x : null;
                            if (ride == null) clearCurrentRide(false); else listenCurrentRide();
                            if (ride != null) lastRouteStopCount = routeStopCount(ride);
                            render();
                        }).addOnFailureListener(e -> { toast(e.getMessage()); render(); });
                    } else {
                        repo.loadRide(current).addOnSuccessListener(x -> {
                            ride = x.exists() ? x : null;
                            if (ride == null) clearCurrentRide(false); else listenCurrentRide();
                            render();
                        }).addOnFailureListener(e -> { toast(e.getMessage()); render(); });
                    }
                } else {
                    render();
                }
                listenOffers();
                Intent incomingIntent = getIntent();
                String incoming = missionIdFromIntent(incomingIntent);
                String incomingType = incomingIntent == null ? "rides" : incomingIntent.getStringExtra("mission_type");
                if (incomingType == null || incomingType.isEmpty()) incomingType = incomingIntent != null && incomingIntent.getStringExtra("route_id") != null ? "rotas_entrega" : "rides";
                if (!incoming.isEmpty() && rideId.isEmpty()) loadIncomingMission(incoming, incomingType);
                if (online) OnlineService.start(MainActivity.this);
            }

            @Override public void onError(Exception e) {
                toast("Falha ao carregar cadastro: " + e.getMessage());
                render();
            }
        });
    }

    private void listenOffers() {
        if (offerListener != null) offerListener.remove();
        if (routeOfferListener != null) routeOfferListener.remove();

        offerListener = repo.listenDirectedRides(driverId, new DriverRepository.RideCallback() {
            @Override public void onRide(DocumentSnapshot d) {
                if (!online || hasActiveMission()) return;
                if (d == null) {
                    if (ride != null && Boolean.TRUE.equals(ride.getBoolean("ofertaAtiva")) && "rides".equals(missionType)) clearCurrentRide(true);
                    return;
                }
                ride = d; rideId = d.getId(); missionType = "rides";
                Session.saveMission(MainActivity.this, missionType, rideId);
                currentTab = 1;
                render();
            }
            @Override public void onError(Exception e) { toast("Falha ao ouvir corridas: " + e.getMessage()); }
        });

        routeOfferListener = repo.listenDirectedRoutes(driverId, new DriverRepository.RideCallback() {
            @Override public void onRide(DocumentSnapshot d) {
                if (!online || hasActiveMission()) return;
                if (d == null) {
                    if (ride != null && Boolean.TRUE.equals(ride.getBoolean("ofertaAtiva")) && "rotas_entrega".equals(missionType)) clearCurrentRide(true);
                    return;
                }
                ride = d; rideId = d.getId(); missionType = "rotas_entrega";
                Session.saveMission(MainActivity.this, missionType, rideId);
                lastRouteStopCount = routeStopCount(d);
                currentTab = 1;
                render();
            }
            @Override public void onError(Exception e) { toast("Falha ao ouvir rotas: " + e.getMessage()); }
        });
    }

    private void listenCurrentRide() {
        if (currentRideListener != null) currentRideListener.remove();
        if (rideId.isEmpty()) return;
        DriverRepository.RideCallback cb = new DriverRepository.RideCallback() {
            @Override public void onRide(DocumentSnapshot d) {
                if (d == null) { clearCurrentRide(true); return; }
                if ("rotas_entrega".equals(missionType)) {
                    int nowCount = routeStopCount(d);
                    if (lastRouteStopCount > 0 && nowCount > lastRouteStopCount && Boolean.TRUE.equals(d.getBoolean("rotaAberta"))) {
                        toast("+" + (nowCount - lastRouteStopCount) + " pedido adicionado à sua rota antes da retirada.");
                    }
                    lastRouteStopCount = nowCount;
                }
                ride = d;
                repo.syncDriverMissionState(driverId, rideId, missionType, d);
                if (isTerminal(d)) {
                    String terminal = DriverRepository.first(d, "statusEntrega", "statusCorrida", "status");
                    clearCurrentRide(false);
                    toast("Missão encerrada: " + terminal);
                }
                render();
            }
            @Override public void onError(Exception e) { toast("Falha ao atualizar missão: " + e.getMessage()); }
        };
        currentRideListener = "rotas_entrega".equals(missionType) ? repo.listenRoute(rideId, cb) : repo.listenRide(rideId, cb);
    }

    private void loadIncomingMission(String incoming, String type) {
        if (incoming == null || incoming.isEmpty()) return;
        missionType = "rotas_entrega".equals(type) ? "rotas_entrega" : "rides";
        if ("rotas_entrega".equals(missionType)) {
            repo.loadRoute(incoming).addOnSuccessListener(d -> {
                if (!d.exists()) return;
                boolean assigned = driverId.equals(DriverRepository.first(d, "entregadorId", "driverId", "uidEntregador"));
                if (!DriverRepository.isDirectedTo(d, driverId) && !assigned) return;
                ride = d; rideId = d.getId();
                Session.saveMission(this, missionType, rideId);
                currentTab = 1; lastRouteStopCount = routeStopCount(d);
                if (!Boolean.TRUE.equals(d.getBoolean("ofertaAtiva"))) { repo.syncDriverMissionState(driverId, rideId, missionType, d); listenCurrentRide(); }
                render();
            });
        } else {
            repo.loadRide(incoming).addOnSuccessListener(d -> {
                if (!d.exists()) return;
                boolean assigned = driverId.equals(DriverRepository.first(d, "entregadorId", "driverId", "uidEntregador"));
                if (!DriverRepository.isDirectedTo(d, driverId) && !assigned) return;
                ride = d; rideId = d.getId();
                Session.saveMission(this, missionType, rideId);
                currentTab = 1;
                if (!Boolean.TRUE.equals(d.getBoolean("ofertaAtiva"))) { repo.syncDriverMissionState(driverId, rideId, missionType, d); listenCurrentRide(); }
                render();
            });
        }
    }

    private void render() {
        if (offerTimer != null) { offerTimer.cancel(); offerTimer = null; }

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Ui.color(this, R.color.up_bg));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        root = Ui.column(this);
        scroll.addView(root);
        screen.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (currentTab == 0) {
            showHeader();
            showIdle();
        } else if (currentTab == 1) {
            boolean premiumActiveRoute = ride != null && ride.exists() && isMultiRouteMission()
                    && !Boolean.TRUE.equals(ride.getBoolean("ofertaAtiva"));
            if (premiumActiveRoute) {
                showActiveRouteHeader();
            } else {
                String ridesTitle = (ride != null && ride.exists())
                        ? (Boolean.TRUE.equals(ride.getBoolean("ofertaAtiva")) ? "Nova entrega" : "Entrega atual")
                        : "Corridas";
                showPageHeader(ridesTitle, false);
            }
            showRidesPage();
        } else if (currentTab == 2) {
            showPageHeader("Ganhos", false);
            showEarningsPage();
        } else if (currentTab == 3) {
            showPageHeader("Conta", false);
            showAccountPage();
        } else if (currentTab == 4) {
            showPageHeader("Notificações", true);
            showNotificationsPage();
        } else if (currentTab == 5) {
            showPageHeader("Minha conta e veículo", true);
            showProfilePage();
        } else if (currentTab == 6) {
            showPageHeader("Pix e pagamentos", true);
            showPixPage();
        } else if (currentTab == 7) {
            showPageHeader("Operação", true);
            showOperationPage();
        } else {
            currentTab = 0;
            showHeader();
            showIdle();
        }

        root.addView(Ui.space(this, 18));
        screen.addView(showBottomNav());
        setContentView(screen);
        syncPresenceService();
    }

    private void showIdle() {
        boolean hasRide = ride != null && ride.exists();
        boolean offer = hasRide && Boolean.TRUE.equals(ride.getBoolean("ofertaAtiva"));

        LinearLayout availability = Ui.accentGradientCard(this);
        LinearLayout top = Ui.row(this);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView onlineTitle = Ui.text(this,
                hasRide ? (offer ? "Nova entrega" : "Entrega em andamento") : (online ? "Online" : "Offline"),
                24, true);
        onlineTitle.setTextColor(Ui.color(this,
                hasRide ? R.color.up_purple : (online ? R.color.up_success : R.color.up_text)));
        texts.addView(onlineTitle);
        texts.addView(Ui.muted(this,
                hasRide ? "Sua próxima ação está na tela de Corridas." :
                        (online ? "Pronto para receber entregas." : "Ative quando estiver disponível."), 13));
        top.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        SwitchMaterial sw = new SwitchMaterial(this);
        sw.setChecked(online);
        sw.setEnabled(!hasRide);
        sw.setShowText(false);
        sw.setOnCheckedChangeListener((b, v) -> {
            if (hasRide) return;
            if (v && !hasInternet()) {
                sw.setChecked(false);
                toast("Sem internet. Conecte-se para ficar online.");
                return;
            }
            if (v && !hasCorePermissions()) {
                sw.setChecked(false);
                toast("Libere localização e notificações para ficar online.");
                startActivity(new Intent(this, PermissionCenterActivity.class));
                return;
            }
            repo.setOnline(driverId, v).addOnSuccessListener(x -> {
                online = v;
                if (v) OnlineService.start(this); else OnlineService.stop(this);
                listenOffers();
                render();
            }).addOnFailureListener(e -> {
                sw.setChecked(!v);
                toast(e.getMessage());
            });
        });
        top.addView(sw);
        availability.addView(top);
        root.addView(availability);

        if (!hasCorePermissions()) {
            LinearLayout permission = Ui.noticeCard(this, "Finalize a configuração",
                    "Libere notificações e localização para receber corridas com segurança.", R.color.up_warning);
            Button configure = Ui.secondaryButton(this, "Configurar aparelho");
            configure.setOnClickListener(v -> startActivity(new Intent(this, PermissionCenterActivity.class)));
            permission.addView(configure);
            root.addView(permission);
        }

        LinearLayout stats = Ui.summaryStrip(this);
        TextView gainsValue = Ui.text(this, "R$ —", 17, true);
        gainsValue.setTextColor(Ui.color(this, R.color.up_success));
        TextView ridesValue = Ui.text(this, "—", 17, true);
        TextView statusValue = Ui.text(this, online ? "Ativo" : "Pausado", 16, true);
        statusValue.setTextColor(Ui.color(this, online ? R.color.up_success : R.color.up_text_muted));
        stats.addView(Ui.summaryItem(this, "Hoje", gainsValue), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stats.addView(Ui.summaryItem(this, "Entregas", ridesValue), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stats.addView(Ui.summaryItem(this, "Online", statusValue), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(stats);
        loadTodaySummary(gainsValue, ridesValue);

        LinearLayout next = Ui.card(this);
        if (hasRide) {
            next.addView(Ui.eyebrow(this, offer ? "Ação necessária" : "Entrega atual"));
            String pedido = pick("codigoPedido", "numeroPedido", "orderId", "pedidoId");
            next.addView(Ui.text(this, offer ? "Você recebeu uma nova entrega" : "Pedido #" + labelOrDash(pedido), 21, true));
            next.addView(Ui.muted(this, offer ? "Confira a corrida e responda à oferta." : "Continue de onde parou.", 13));
            Button open = Ui.button(this, offer ? "Ver nova entrega" : "Abrir entrega");
            open.setOnClickListener(v -> { currentTab = 1; render(); });
            next.addView(open);
        } else {
            next.addView(Ui.eyebrow(this, online ? "Pronto" : "Pausado"));
            next.addView(Ui.text(this, online ? "Aguardando próxima entrega" : "Você está fora de operação", 21, true));
            next.addView(Ui.muted(this,
                    online ? "Assim que houver uma corrida para você, o UP Entregas vai avisar." :
                            "Fique online quando quiser voltar a receber entregas.", 13));
        }
        root.addView(next);
    }

    private void showRide() {
        if (ride == null || !ride.exists()) return;
        if (isMultiRouteMission()) { showMultiRoute(); return; }

        String status = DriverRepository.s(ride, "status").toUpperCase(Locale.ROOT);
        String delivery = DriverRepository.s(ride, "statusEntrega").toUpperCase(Locale.ROOT);
        boolean offer = Boolean.TRUE.equals(ride.getBoolean("ofertaAtiva"));
        UpState upState = offer ? UpState.OFFER_PENDING : UpState.from(ride);
        boolean toStore = !offer && upState == UpState.TO_STORE;
        boolean atStore = !offer && upState == UpState.AT_STORE;
        boolean enRoute = !offer && upState == UpState.TO_CUSTOMER;
        boolean atCustomer = !offer && upState == UpState.AT_CUSTOMER;

        String pedido = pick("codigoPedido", "numeroPedido", "orderId", "pedidoId");
        String customerName = pick("clienteNome", "nomeCliente");
        String storeAddress = pick("enderecoLoja", "coletaEndereco", "origemEndereco");
        String customerAddress = pick("clienteEnderecoCompleto", "deliveryAddress", "enderecoCliente", "destinoEndereco");
        String customerDistrict = pick("clienteBairro", "bairroCliente", "deliveryNeighborhood");
        String payment = pick("formaPagamento", "metodoPagamento", "pagamento");
        double amountToCollect = firstNum("valorReceberCliente", "amountToCollect", "totalPedido", "valorTotalPedido", "valorPedido");
        String paymentUpper = payment.toUpperCase(Locale.ROOT);
        boolean paidOnline = isPaidOnlinePayment(paymentUpper);

        LinearLayout hero = Ui.card(this);

        LinearLayout top = Ui.row(this);
        LinearLayout idBlock = new LinearLayout(this);
        idBlock.setOrientation(LinearLayout.VERTICAL);
        idBlock.addView(Ui.muted(this, offer ? "Nova corrida" : "Corrida ativa", 11));
        TextView orderNumber = Ui.text(this, "Pedido #" + labelOrDash(pedido), 22, true);
        idBlock.addView(orderNumber);
        top.addView(idBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView statePill = Ui.pill(this,
                offer ? "Responder" : (atCustomer ? "No cliente" : (enRoute ? "Em rota" : (atStore ? "Na loja" : "A caminho"))),
                offer ? R.color.up_warning : (atCustomer ? R.color.up_success : R.color.up_purple));
        top.addView(statePill);
        hero.addView(top);

        if (!offer) hero.addView(deliveryProgress(status, delivery));

        if (offer) {
            LinearLayout storeHeader = Ui.row(this);
            storeHeader.addView(Ui.brandLogo(this, 48));
            LinearLayout storeText = new LinearLayout(this);
            storeText.setOrientation(LinearLayout.VERTICAL);
            storeText.setPadding(Ui.dp(this, 10), 0, 0, 0);
            storeText.addView(Ui.muted(this, "Retirada", 10));
            storeText.addView(Ui.text(this, "Rodrigues Açaí e Cia", 16, true));
            if (!storeAddress.isEmpty()) storeText.addView(Ui.muted(this, storeAddress, 12));
            storeHeader.addView(storeText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            hero.addView(storeHeader);

            hero.addView(Ui.divider(this));
            String preview = customerDistrict.isEmpty() ? customerAddress : customerDistrict;
            hero.addView(Ui.addressBlock(this, "Destino", customerName.isEmpty() ? "Cliente" : customerName,
                    labelOrDash(preview), true));

            double km = num("distanciaKm");
            double ganho = Math.max(num("valorRepasseEntregador"), Math.max(num("valorEntregador"), num("valorCorrida")));
            LinearLayout offerMeta = Ui.summaryStrip(this);
            TextView kmV = Ui.text(this, km > 0 ? String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", km) : "—", 16, true);
            TextView feeV = Ui.text(this, ganho > 0 ? formatMoney(ganho) : "—", 16, true);
            feeV.setTextColor(Ui.color(this, R.color.up_success));
            TextView payV = Ui.text(this, paidOnline ? "Pago" : (payment.isEmpty() ? "—" : payment), 13, true);
            offerMeta.addView(Ui.summaryItem(this, "Distância", kmV), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            offerMeta.addView(Ui.summaryItem(this, "Você recebe", feeV), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            offerMeta.addView(Ui.summaryItem(this, "Pagamento", payV), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            hero.addView(offerMeta);

            long expiry = DriverRepository.offerExpiryMillis(ride);
            if (expiry > 0) {
                long remaining = Math.max(0, expiry - System.currentTimeMillis());
                TextView countdown = Ui.muted(this, "Responda em " + Math.max(0, remaining / 1000L) + " s", 12);
                countdown.setTextColor(Ui.color(this, R.color.up_warning));
                hero.addView(countdown);
                if (remaining > 0) {
                    alertOfferTick();
                    final String expiringRideId = rideId;
                    offerTimer = new CountDownTimer(remaining, 1000L) {
                        @Override public void onTick(long millisUntilFinished) {
                            long seconds = Math.max(0, millisUntilFinished / 1000L);
                            countdown.setText("Responda em " + seconds + " s");
                            if (seconds > 0 && seconds % 5 == 0) alertOfferTick();
                        }
                        @Override public void onFinish() {
                            countdown.setText("Oferta expirada");
                            repo.markOfferExpired(driverId, expiringRideId).addOnCompleteListener(t -> {
                                if (expiringRideId.equals(rideId)) {
                                    clearCurrentRide(false);
                                    toast("O prazo terminou. O gestor decidirá o próximo passo.");
                                    render();
                                }
                            });
                        }
                    }.start();
                }
            }
        } else if (toStore || atStore) {
            LinearLayout storeHeader = Ui.row(this);
            storeHeader.addView(Ui.brandLogo(this, 54));
            LinearLayout storeText = new LinearLayout(this);
            storeText.setOrientation(LinearLayout.VERTICAL);
            storeText.setPadding(Ui.dp(this, 11), 0, 0, 0);
            storeText.addView(Ui.muted(this, atStore ? "Você chegou à loja" : "Sua próxima parada", 10));
            storeText.addView(Ui.text(this, "Rodrigues Açaí e Cia", 18, true));
            storeText.addView(Ui.muted(this, labelOrDash(storeAddress), 13));
            storeHeader.addView(storeText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            hero.addView(storeHeader);

            String preview = customerDistrict.isEmpty() ? customerAddress : customerDistrict;
            if (!preview.isEmpty()) {
                hero.addView(Ui.divider(this));
                hero.addView(Ui.muted(this, "Depois da retirada", 10));
                hero.addView(Ui.text(this, customerName.isEmpty() ? "Entrega ao cliente" : customerName, 14, true));
                hero.addView(Ui.muted(this, preview, 12));
            }
        } else {
            hero.addView(Ui.addressBlock(this, "Entregar para",
                    customerName.isEmpty() ? "Cliente" : customerName,
                    labelOrDash(customerAddress), true));

            hero.addView(Ui.divider(this));
            LinearLayout paymentRow = Ui.row(this);
            LinearLayout paymentLeft = new LinearLayout(this);
            paymentLeft.setOrientation(LinearLayout.VERTICAL);
            paymentLeft.addView(Ui.muted(this, "Pagamento", 10));
            paymentLeft.addView(Ui.text(this, payment.isEmpty() ? "Não informado" : payment, 14, true));
            paymentRow.addView(paymentLeft, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout paymentRight = new LinearLayout(this);
            paymentRight.setOrientation(LinearLayout.VERTICAL);
            TextView label = Ui.muted(this, paidOnline ? "Situação" : "Receber do cliente", 10);
            label.setGravity(Gravity.END);
            paymentRight.addView(label);
            TextView value = Ui.text(this, paidOnline ? "Já pago" : formatMoney(amountToCollect), 16, true);
            value.setGravity(Gravity.END);
            value.setTextColor(Ui.color(this, R.color.up_success));
            paymentRight.addView(value);
            paymentRow.addView(paymentRight, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            hero.addView(paymentRow);

            String trocoPara = pick("trocoPara").trim();
            if (!paidOnline && !trocoPara.isEmpty() && !trocoPara.equals("0") && !trocoPara.equals("0,00") && !trocoPara.equals("0.00")) {
                hero.addView(Ui.noticeCard(this, "Troco", "Levar troco para " + trocoPara, R.color.up_warning));
            }
            if (Boolean.TRUE.equals(ride.getBoolean("precisaMaquininha"))) {
                hero.addView(Ui.noticeCard(this, "Pagamento", "Levar maquininha para esta entrega.", R.color.up_warning));
            }

            String obs = pick("observacoes", "observacao", "deliveryNotes");
            if (!obs.isEmpty()) hero.addView(Ui.noticeCard(this, "Observação do cliente", obs, R.color.up_info));
        }

        root.addView(hero);

        if (!offer && (toStore || atStore) && Boolean.TRUE.equals(ride.getBoolean("complementoOfertaAtiva"))) {
            showSingleRouteComplementOffer();
        }

        root.addView(MissionMapView.card(this,
                offer ? "Loja e destino" : (toStore ? "Caminho para a loja" : (atStore ? "Você está na loja" : "Destino da entrega")),
                singleMapPoints(), (!offer && toStore) ? -1 : 0));

        if (offer) {
            Button accept = Ui.greenButton(this, "Aceitar entrega");
            Button reject = Ui.outlineButton(this, "Recusar");
            accept.setOnClickListener(v -> {
                if (!requireInternetForAction()) return;
                accept.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
                accept.setEnabled(false);
                reject.setEnabled(false);
                String old = accept.getText().toString();
                accept.setText("Aceitando…");
                repo.acceptRide(driverId, rideId).addOnSuccessListener(x -> {
                    OfferAlertPlayer.stop();
                    missionType = "rides";
                    Session.saveMission(this, missionType, rideId);
                    OnlineService.stop(this);
                    startTracking(false);
                    listenCurrentRide();
                    reloadRide();
                }).addOnFailureListener(e -> {
                    accept.setEnabled(true);
                    reject.setEnabled(true);
                    accept.setText(old);
                    toast(e.getMessage());
                });
            });
            reject.setOnClickListener(v -> reasonPanel());
            root.addView(accept);
            root.addView(reject);
        } else {
            showStageButtons(status);
        }
    }

    private void showSingleRouteComplementOffer() {
        Object raw = ride == null ? null : ride.get("complementoOferta");
        if (!(raw instanceof Map)) return;
        @SuppressWarnings("unchecked") Map<String,Object> comp = (Map<String,Object>) raw;
        final String routeId = mapString(comp, "routeId", "rotaId").isEmpty()
                ? pick("complementoRouteId", "rotaId", "routeId", "rotaEntregaId")
                : mapString(comp, "routeId", "rotaId");
        if (routeId == null || routeId.trim().isEmpty()) return;
        OfferAlertPlayer.start(this, "complemento:" + routeId);
        LinearLayout card = Ui.card(this);
        TextView eye = Ui.eyebrow(this, "+ 1 ENTREGA PARA SUA ROTA");
        eye.setTextColor(Ui.color(this, R.color.up_yellow));
        card.addView(eye);
        String number = mapString(comp, "numeroPedido", "codigoPedido");
        String client = mapString(comp, "clienteNome", "nomeCliente");
        String address = mapString(comp, "endereco", "deliveryAddress");
        card.addView(Ui.text(this, (number.isEmpty() ? "Novo pedido" : "Pedido #" + number) + (client.isEmpty() ? "" : " • " + client), 19, true));
        if (!address.isEmpty()) card.addView(Ui.muted(this, address, 13));
        double extraKm = mapDouble(comp, "kmAdicional", "desvioKm");
        double extraFee = mapDouble(comp, "repasseAdicional", "ganhoAdicional");
        String meta = (extraKm > 0 ? String.format(Locale.forLanguageTag("pt-BR"), "+ %.1f km", extraKm) : "Mesmo ponto de retirada") +
                (extraFee > 0 ? " • + " + formatMoney(extraFee) : "");
        TextView m = Ui.text(this, meta, 14, true); m.setTextColor(Ui.color(this, R.color.up_success)); card.addView(m);
        card.addView(Ui.muted(this, "Sua retirada ainda não foi confirmada. Se aceitar, esta corrida vira uma rota múltipla sem perder o pedido atual.", 12));
        Button add = Ui.greenButton(this, "Adicionar à minha rota");
        Button reject = Ui.outlineButton(this, "Recusar complemento");
        add.setOnClickListener(v -> {
            if (!requireInternetForAction()) return;
            add.setEnabled(false); reject.setEnabled(false); add.setText("Adicionando…");
            repo.acceptRouteComplement(driverId, routeId).addOnSuccessListener(x -> {
                OfferAlertPlayer.stop();
                if (currentRideListener != null) { currentRideListener.remove(); currentRideListener = null; }
                missionType = "rotas_entrega";
                rideId = routeId;
                Session.saveMission(this, missionType, rideId);
                repo.loadRoute(routeId).addOnSuccessListener(d -> {
                    ride = d.exists() ? d : null;
                    lastRouteStopCount = routeStopCount(ride);
                    listenCurrentRide();
                    toast("Pedido adicionado. Sua rota foi atualizada.");
                    render();
                }).addOnFailureListener(e -> { toast(e.getMessage()); reloadRide(); });
            }).addOnFailureListener(e -> { add.setEnabled(true); reject.setEnabled(true); add.setText("Adicionar à minha rota"); toast(e.getMessage()); });
        });
        reject.setOnClickListener(v -> {
            reject.setEnabled(false); add.setEnabled(false);
            repo.rejectRouteComplement(driverId, routeId, "Entregador optou por não adicionar este pedido.")
                    .addOnSuccessListener(x -> { OfferAlertPlayer.stop(); toast("Complemento recusado. Sua corrida continua."); reloadRide(); })
                    .addOnFailureListener(e -> { reject.setEnabled(true); add.setEnabled(true); toast(e.getMessage()); });
        });
        card.addView(add); card.addView(reject); root.addView(card);
    }

    private void showMultiRoute() {
        if (ride == null || !ride.exists()) return;
        String status = DriverRepository.s(ride, "status").toUpperCase(Locale.ROOT);
        String delivery = DriverRepository.s(ride, "statusEntrega").toUpperCase(Locale.ROOT);
        boolean offer = Boolean.TRUE.equals(ride.getBoolean("ofertaAtiva"));
        UpState upState = offer ? UpState.OFFER_PENDING : UpState.from(ride);
        boolean toStore = !offer && upState == UpState.TO_STORE;
        boolean atStore = !offer && upState == UpState.AT_STORE;
        boolean enRoute = !offer && upState == UpState.TO_CUSTOMER;
        boolean atCustomer = !offer && upState == UpState.AT_CUSTOMER;

        List<Map<String, Object>> stops = DriverRepository.routeStops(ride);
        List<String> orderIds = DriverRepository.routeOrderIds(ride);
        int count = Math.max(stops.size(), orderIds.size());
        int current = DriverRepository.routeCurrentIndex(ride, Math.max(1, count));
        Map<String, Object> stop = current < stops.size() ? stops.get(current) : null;
        boolean routeOpen = !Boolean.FALSE.equals(ride.getBoolean("rotaAberta"));
        double km = firstNum("kmEstimado", "distanciaKm");
        double fee = Math.max(firstNum("valorRepasseEntregador", "repasseTotal", "valorCorrida"), 0d);

        if (!offer) {
            showPremiumActiveRoute(stops, orderIds, count, current, stop, routeOpen, toStore, atStore, enRoute, atCustomer, km, fee);
            return;
        }

        LinearLayout hero = Ui.heroCard(this);
        LinearLayout header = Ui.row(this);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(Ui.eyebrow(this, offer ? "Nova rota" : "Rota ativa"));
        labels.addView(Ui.text(this, count + (count == 1 ? " entrega" : " entregas"), 24, true));
        header.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView pill = Ui.pill(this,
                offer ? "RESPONDER" : (atCustomer ? "NO CLIENTE" : (enRoute ? "EM ROTA" : (atStore ? "NA LOJA" : "A CAMINHO"))),
                offer ? R.color.up_warning : (atCustomer ? R.color.up_success : R.color.up_purple));
        header.addView(pill);
        hero.addView(header);

        if (offer) {
            hero.addView(Ui.muted(this, "Confira as paradas no mapa. A decisão de aceitar continua sendo sua.", 13));
            LinearLayout meta = Ui.summaryStrip(this);
            TextView a = Ui.text(this, km > 0 ? String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", km) : "—", 16, true);
            TextView b = Ui.text(this, fee > 0 ? formatMoney(fee) : "—", 16, true);
            b.setTextColor(Ui.color(this, R.color.up_success));
            TextView c = Ui.text(this, String.valueOf(count), 16, true);
            meta.addView(Ui.summaryItem(this, "Distância", a), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            meta.addView(Ui.summaryItem(this, "Você recebe", b), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            meta.addView(Ui.summaryItem(this, "Paradas", c), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            hero.addView(meta);

            long expiry = DriverRepository.offerExpiryMillis(ride);
            if (expiry > 0) {
                long remaining = Math.max(0, expiry - System.currentTimeMillis());
                TextView countdown = Ui.muted(this, "Responda em " + Math.max(0, remaining / 1000L) + " s", 12);
                countdown.setTextColor(Ui.color(this, R.color.up_warning));
                hero.addView(countdown);
                if (remaining > 0) {
                    final String expiringRouteId = rideId;
                    offerTimer = new CountDownTimer(remaining, 1000L) {
                        @Override public void onTick(long millisUntilFinished) {
                            countdown.setText("Responda em " + Math.max(0, millisUntilFinished / 1000L) + " s");
                        }
                        @Override public void onFinish() {
                            countdown.setText("Oferta expirada");
                            repo.markRouteOfferExpired(driverId, expiringRouteId).addOnCompleteListener(t -> {
                                if (expiringRouteId.equals(rideId)) {
                                    OfferAlertPlayer.stop();
                                    clearCurrentRide(false);
                                    toast("O prazo terminou. O gestor decidirá o próximo passo.");
                                    render();
                                }
                            });
                        }
                    }.start();
                }
            }
        } else {
            hero.addView(routeProgress(count, current, toStore || atStore, enRoute || atCustomer));
            if ((toStore || atStore) && routeOpen) {
                hero.addView(Ui.noticeCard(this, "Rota aberta",
                        "O gestor ainda pode adicionar outro pedido até você confirmar a retirada da loja.", R.color.up_info));
            }
            if (toStore || atStore) {
                hero.addView(Ui.addressBlock(this, atStore ? "Você está na loja" : "Próxima parada",
                        routeStoreName(),
                        labelOrDash(DriverRepository.first(ride, "enderecoLoja", "coletaEndereco", "origemEndereco")), false));
            } else if (stop != null) {
                String client = mapString(stop, "clienteNome", "nomeCliente");
                String address = mapString(stop, "endereco", "deliveryAddress", "destinoEndereco");
                hero.addView(Ui.addressBlock(this, "Parada " + (current + 1) + " de " + count,
                        client.isEmpty() ? "Cliente" : client, labelOrDash(address), true));
                String payment = mapString(stop, "formaPagamento", "pagamento");
                double collect = mapDouble(stop, "valorReceberCliente", "amountToCollect", "valorCobrar");
                if (!payment.isEmpty() || collect > 0) {
                    hero.addView(Ui.divider(this));
                    LinearLayout pr = Ui.row(this);
                    LinearLayout pl = new LinearLayout(this); pl.setOrientation(LinearLayout.VERTICAL);
                    pl.addView(Ui.muted(this, "Pagamento", 10));
                    pl.addView(Ui.text(this, payment.isEmpty() ? "Não informado" : payment, 14, true));
                    pr.addView(pl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    LinearLayout pv = new LinearLayout(this); pv.setOrientation(LinearLayout.VERTICAL);
                    TextView lab = Ui.muted(this, collect > 0 ? "Receber" : "Situação", 10); lab.setGravity(Gravity.END); pv.addView(lab);
                    TextView val = Ui.text(this, collect > 0 ? formatMoney(collect) : "Já pago", 16, true); val.setGravity(Gravity.END); val.setTextColor(Ui.color(this, R.color.up_success)); pv.addView(val);
                    pr.addView(pv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    hero.addView(pr);
                }
                double changeTo = mapDouble(stop, "trocoPara");
                if (changeTo > 0) hero.addView(Ui.noticeCard(this, "Troco", "Levar troco para " + formatMoney(changeTo), R.color.up_warning));
                if (mapBool(stop, "precisaMaquininha")) hero.addView(Ui.noticeCard(this, "Maquininha", "Esta parada precisa de maquininha.", R.color.up_warning));
            }
        }
        root.addView(hero);

        if (!offer && routeOpen && Boolean.TRUE.equals(ride.getBoolean("complementoOfertaAtiva"))) {
            showRouteComplementOffer();
        } else if (!offer) {
            OfferAlertPlayer.stop();
        }

        List<MissionMapView.Point> points = routeMapPoints(stops, current);
        root.addView(MissionMapView.card(this, offer ? "Loja + todas as paradas" : (toStore ? "Caminho para a loja" : (atStore ? "Você está na loja" : "Sua rota de entrega")), points, (!offer && toStore) ? -1 : current));

        if (offer) {
            LinearLayout list = Ui.card(this);
            list.addView(Ui.eyebrow(this, "Paradas da rota"));
            for (int i = 0; i < stops.size(); i++) {
                Map<String, Object> x = stops.get(i);
                String client = mapString(x, "clienteNome", "nomeCliente");
                String address = mapString(x, "endereco", "deliveryAddress");
                LinearLayout row = Ui.row(this);
                TextView n = Ui.centered(this, String.valueOf(i + 1), 13, true);
                n.setTextColor(0xFFFFFFFF);
                n.setMinWidth(Ui.dp(this, 34)); n.setMinHeight(Ui.dp(this, 34)); n.setGravity(Gravity.CENTER);
                n.setBackground(Ui.rounded(this, R.color.up_purple, 999, R.color.up_purple, 0));
                row.addView(n);
                LinearLayout tx = new LinearLayout(this); tx.setOrientation(LinearLayout.VERTICAL); tx.setPadding(Ui.dp(this, 10), 0, 0, 0);
                tx.addView(Ui.text(this, client.isEmpty() ? "Cliente " + (i + 1) : client, 14, true));
                tx.addView(Ui.muted(this, labelOrDash(address), 11));
                row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                list.addView(row);
                if (i + 1 < stops.size()) list.addView(Ui.divider(this));
            }
            root.addView(list);

            Button accept = Ui.button(this, "Aceitar rota");
            Button reject = Ui.outlineButton(this, "Recusar");
            accept.setOnClickListener(v -> {
                if (!requireInternetForAction()) return;
                accept.setEnabled(false); reject.setEnabled(false); accept.setText("Aceitando…");
                repo.acceptRoute(driverId, rideId).addOnSuccessListener(x -> {
                    OfferAlertPlayer.stop();
                    missionType = "rotas_entrega";
                    Session.saveMission(this, missionType, rideId);
                    OnlineService.stop(this);
                    startTracking(false);
                    listenCurrentRide();
                    reloadRide();
                }).addOnFailureListener(e -> {
                    accept.setEnabled(true); reject.setEnabled(true); accept.setText("Aceitar rota"); toast(e.getMessage());
                });
            });
            reject.setOnClickListener(v -> reasonPanel());
            root.addView(accept); root.addView(reject);
            return;
        }

        LinearLayout quick = Ui.row(this);
        if (toStore) {
            Button nav = Ui.miniAction(this, "Navegar até a loja");
            nav.setOnClickListener(v -> openRouteNavigation(true));
            quick.addView(nav);
        } else if (atStore) {
            TextView ready = Ui.muted(this, "Você já está na loja • confira os pedidos e confirme a retirada", 12);
            quick.addView(ready);
        } else if (stop != null) {
            Button nav = Ui.miniAction(this, "Navegar"); nav.setOnClickListener(v -> openRouteNavigation(false)); quick.addView(nav);
            String phone = mapString(stop, "clienteTelefone", "telefone", "whatsapp");
            if (!phone.isEmpty()) { Button wa = Ui.miniAction(this, "WhatsApp"); wa.setOnClickListener(v -> openWhatsApp(phone)); quick.addView(wa); }
        }
        root.addView(quick);

        Button primary;
        if (toStore) {
            primary = Ui.button(this, "Cheguei à loja");
            primary.setOnClickListener(v -> routeStage(primary, "Confirmando chegada…", "COLETANDO", "ENTREGADOR_CHEGOU_LOJA", "chegouLojaEm"));
        } else if (atStore) {
            primary = Ui.greenButton(this, "Retirei todos os pedidos");
            primary.setOnClickListener(v -> routePickupPanel());
        } else if (enRoute) {
            primary = Ui.button(this, "Cheguei ao cliente");
            primary.setOnClickListener(v -> routeStage(primary, "Confirmando chegada…", "NO_CLIENTE", "ENTREGADOR_CHEGOU_CLIENTE", "chegouClienteEm"));
        } else if (atCustomer) {
            primary = Ui.greenButton(this, "Confirmar entrega " + (current + 1) + " de " + count);
            primary.setOnClickListener(v -> routeFinishPanel());
        } else {
            primary = Ui.secondaryButton(this, "Atualizar rota"); primary.setOnClickListener(v -> reloadRide());
        }
        root.addView(primary);
        Button problem = Ui.outlineButton(this, atStore ? "Problema na retirada" : "Preciso de ajuda");
        problem.setOnClickListener(v -> occurrencePanel());
        root.addView(problem);
    }


    private void showActiveRouteHeader() {
        LinearLayout header = Ui.row(this);
        header.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 10));

        android.widget.ImageView logo = Ui.upWordmark(this, 76, 42);
        header.addView(logo);

        TextView title = Ui.text(this, "Rota ativa", 22, true);
        title.setPadding(Ui.dp(this, 12), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        android.widget.ImageView bell = Ui.iconAction(this, R.drawable.ic_bell, "Notificações");
        bell.setOnClickListener(v -> { deepReturnTab = 1; currentTab = 4; render(); });
        header.addView(bell);
        root.addView(header);
    }

    private void showPremiumActiveRoute(List<Map<String, Object>> stops,
                                        List<String> orderIds,
                                        int count,
                                        int current,
                                        Map<String, Object> stop,
                                        boolean routeOpen,
                                        boolean toStore,
                                        boolean atStore,
                                        boolean enRoute,
                                        boolean atCustomer,
                                        double km,
                                        double fee) {
        count = Math.max(1, count);
        current = Math.max(0, Math.min(current, count - 1));

        LinearLayout routeHeader = Ui.routeHeaderCard(this);
        LinearLayout titleRow = Ui.row(this);
        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        TextView eye = Ui.eyebrow(this, "Rota ativa");
        titleBlock.addView(eye);
        titleBlock.addView(Ui.text(this, count + (count == 1 ? " entrega" : " entregas"), 23, true));
        titleRow.addView(titleBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView routeCode = Ui.pill(this, premiumRouteCode(), R.color.up_purple);
        titleRow.addView(routeCode);
        routeHeader.addView(titleRow);

        String routeState = routeOpen ? "Rota aberta para complemento" : "Rota fechada após retirada";
        TextView state = Ui.muted(this, routeState, 11);
        state.setPadding(0, Ui.dp(this, 8), 0, 0);
        routeHeader.addView(state);
        root.addView(routeHeader);

        root.addView(premiumRouteProgress(count, current, toStore, atStore, enRoute, atCustomer));

        List<MissionMapView.Point> points = routeMapPoints(stops, current);
        root.addView(MissionMapView.premiumCard(this, points, (toStore || atStore) ? -1 : current));

        if (toStore || atStore) {
            showPremiumPickupStage(stops, count, routeOpen, toStore, atStore);
        } else if (stop != null) {
            showPremiumCurrentStop(stop, count, current, enRoute, atCustomer);
            showPremiumUpcomingStops(stops, current);
        } else {
            LinearLayout missing = Ui.card(this);
            missing.addView(Ui.eyebrow(this, "Rota em atualização"));
            missing.addView(Ui.text(this, "Preparando a próxima parada", 19, true));
            missing.addView(Ui.muted(this, "Os dados da rota estão sendo sincronizados. Nenhuma etapa será avançada automaticamente.", 12));
            root.addView(missing);
        }

        if (routeOpen && Boolean.TRUE.equals(ride.getBoolean("complementoOfertaAtiva"))) {
            showRouteComplementOffer();
        } else {
            OfferAlertPlayer.stop();
        }

        if (fee > 0 || km > 0) {
            LinearLayout strip = Ui.summaryStrip(this);
            TextView kmValue = Ui.text(this, km > 0 ? String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", km) : "—", 14, true);
            TextView feeValue = Ui.text(this, fee > 0 ? formatMoney(fee) : "—", 14, true);
            feeValue.setTextColor(Ui.color(this, R.color.up_success));
            strip.addView(Ui.summaryItem(this, "Rota", kmValue), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            strip.addView(Ui.summaryItem(this, "Seu ganho", feeValue), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            root.addView(strip);
        }
    }

    private ViewGroup premiumRouteProgress(int count, int current, boolean toStore, boolean atStore, boolean enRoute, boolean atCustomer) {
        LinearLayout wrap = Ui.compactCard(this);
        wrap.setPadding(Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 10));

        LinearLayout row = Ui.row(this);
        int slots = Math.min(count, 4);
        int activeIndex = (toStore || atStore) ? -1 : current;

        LinearLayout store = premiumProgressStep("✓", "Loja", activeIndex >= 0, activeIndex < 0);
        row.addView(store, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        for (int i = 0; i < slots; i++) {
            boolean done = activeIndex > i;
            boolean active = activeIndex == i;
            String symbol = done ? "✓" : String.valueOf(i + 1);
            String label;
            if (active) label = "Atual";
            else if (done) label = "Concluída";
            else if (i == current + 1) label = "A seguir";
            else label = i == slots - 1 ? "Depois" : "Parada";
            row.addView(premiumProgressStep(symbol, label, done, active), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        wrap.addView(row);
        if (count > slots) {
            TextView more = Ui.centered(this, "+ " + (count - slots) + " parada(s) na rota completa", 11, false);
            more.setTextColor(Ui.color(this, R.color.up_text_muted));
            more.setPadding(0, Ui.dp(this, 8), 0, 0);
            wrap.addView(more);
        }
        return wrap;
    }

    private LinearLayout premiumProgressStep(String symbol, String label, boolean done, boolean active) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);

        TextView dot = Ui.centered(this, symbol, 13, true);
        dot.setGravity(Gravity.CENTER);
        dot.setMinWidth(Ui.dp(this, 34));
        dot.setMinHeight(Ui.dp(this, 34));
        int dotColor = active ? R.color.up_purple : (done ? R.color.up_success : R.color.up_surface_alt);
        int textColor = active ? 0xFFFFFFFF : (done ? 0xFF07111F : Ui.color(this, R.color.up_text_muted));
        dot.setTextColor(textColor);
        dot.setBackground(Ui.rounded(this, dotColor, 999, active ? R.color.up_purple : R.color.up_border, 1));
        item.addView(dot);

        TextView lab = Ui.centered(this, label, 10, active);
        lab.setTextColor(Ui.color(this, active ? R.color.up_purple : R.color.up_text_muted));
        lab.setPadding(0, Ui.dp(this, 5), 0, 0);
        item.addView(lab);
        return item;
    }

    private void showPremiumPickupStage(List<Map<String, Object>> stops, int count, boolean routeOpen, boolean toStore, boolean atStore) {
        LinearLayout card = Ui.heroCard(this);
        card.addView(Ui.eyebrow(this, atStore ? "Retirada" : "Indo para a loja"));
        card.addView(Ui.text(this, routeStoreName(), 21, true));
        card.addView(Ui.muted(this, labelOrDash(DriverRepository.first(ride, "enderecoLoja", "coletaEndereco", "origemEndereco")), 13));

        LinearLayout info = Ui.noticeCard(this,
                count + (count == 1 ? " pedido para retirar" : " pedidos para retirar"),
                routeOpen ? "A rota ainda aceita complemento até a retirada ser confirmada." : "Rota fechada para novos pedidos.",
                routeOpen ? R.color.up_info : R.color.up_success);
        card.addView(info);

        if (!stops.isEmpty()) {
            card.addView(Ui.divider(this));
            for (int i = 0; i < stops.size(); i++) {
                Map<String, Object> x = stops.get(i);
                String order = mapString(x, "numeroPedido", "codigoPedido", "pedidoId", "orderId");
                String client = mapString(x, "clienteNome", "nomeCliente");
                LinearLayout line = Ui.row(this);
                TextView n = Ui.centered(this, String.valueOf(i + 1), 12, true);
                n.setMinWidth(Ui.dp(this, 30)); n.setMinHeight(Ui.dp(this, 30)); n.setGravity(Gravity.CENTER);
                n.setBackground(Ui.rounded(this, R.color.up_purple_soft, 999, R.color.up_border, 1));
                n.setTextColor(Ui.color(this, R.color.up_purple));
                line.addView(n);
                String text = (order.isEmpty() ? "Pedido" : "Pedido #" + order) + (client.isEmpty() ? "" : " • " + client);
                TextView label = Ui.text(this, text, 13, true);
                label.setPadding(Ui.dp(this, 9), 0, 0, 0);
                line.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                card.addView(line);
                if (i + 1 < stops.size()) card.addView(Ui.divider(this));
            }
        }
        root.addView(card);

        if (toStore) {
            Button nav = Ui.yellowButton(this, "IR PARA A LOJA");
            nav.setOnClickListener(v -> openRouteNavigation(true));
            root.addView(nav);
            Button arrived = Ui.outlineButton(this, "CHEGUEI À LOJA");
            arrived.setOnClickListener(v -> routeStage(arrived, "Confirmando chegada…", "COLETANDO", "ENTREGADOR_CHEGOU_LOJA", "chegouLojaEm"));
            root.addView(arrived);
        } else if (atStore) {
            Button pickup = Ui.greenButton(this, "RETIREI TODOS OS PEDIDOS");
            pickup.setOnClickListener(v -> routePickupPanel());
            root.addView(pickup);
            Button problem = Ui.outlineButton(this, "PROBLEMA NA RETIRADA");
            problem.setOnClickListener(v -> occurrencePanel());
            root.addView(problem);
        }
    }

    private void showPremiumCurrentStop(Map<String, Object> stop, int count, int current, boolean enRoute, boolean atCustomer) {
        LinearLayout card = Ui.heroCard(this);
        LinearLayout top = Ui.row(this);
        TextView eye = Ui.eyebrow(this, "Parada " + (current + 1) + " de " + count);
        top.addView(eye, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String order = mapString(stop, "numeroPedido", "codigoPedido", "pedidoId", "orderId");
        if (!order.isEmpty()) top.addView(Ui.pill(this, "Pedido #" + order, R.color.up_purple));
        card.addView(top);

        String client = mapString(stop, "clienteNome", "nomeCliente");
        String address = mapString(stop, "endereco", "deliveryAddress", "destinoEndereco");
        String district = mapString(stop, "bairro", "clienteBairro", "deliveryNeighborhood");
        String city = mapString(stop, "cidade", "clienteCidade", "deliveryCity");
        card.addView(Ui.text(this, client.isEmpty() ? "Cliente" : client, 22, true));
        card.addView(Ui.muted(this, labelOrDash(address), 13));
        String locality = district + (!district.isEmpty() && !city.isEmpty() ? " • " : "") + city;
        if (!locality.isEmpty()) card.addView(Ui.muted(this, locality, 11));

        double stopKm = mapDouble(stop, "distanciaKm", "km", "distanceKm");
        double etaMin = mapDouble(stop, "etaMin", "tempoMin", "durationMin");
        if (stopKm > 0 || etaMin > 0) {
            String eta = (etaMin > 0 ? Math.round(etaMin) + " min" : "") + (etaMin > 0 && stopKm > 0 ? " • " : "") + (stopKm > 0 ? String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", stopKm) : "");
            TextView etaView = Ui.text(this, eta, 13, true);
            etaView.setTextColor(Ui.color(this, R.color.up_purple));
            etaView.setPadding(0, Ui.dp(this, 9), 0, 0);
            card.addView(etaView);
        }

        card.addView(premiumPaymentPanel(stop));
        root.addView(card);

        if (enRoute) {
            Button nav = Ui.yellowButton(this, "IR PARA O CLIENTE");
            nav.setOnClickListener(v -> openRouteNavigation(false));
            root.addView(nav);
            Button arrived = Ui.outlineButton(this, "CHEGUEI AO CLIENTE");
            arrived.setOnClickListener(v -> routeStage(arrived, "Confirmando chegada…", "NO_CLIENTE", "ENTREGADOR_CHEGOU_CLIENTE", "chegouClienteEm"));
            root.addView(arrived);
        } else if (atCustomer) {
            Button finish = Ui.greenButton(this, "CONFIRMAR ENTREGA " + (current + 1) + " DE " + count);
            finish.setOnClickListener(v -> routeFinishPanel());
            root.addView(finish);
        }

        LinearLayout actions = Ui.row(this);
        Button route = Ui.miniAction(this, "Ver rota completa");
        route.setOnClickListener(v -> routeFullPanel());
        actions.addView(route);
        String phone = mapString(stop, "clienteTelefone", "telefone", "whatsapp");
        if (!phone.isEmpty()) {
            Button contact = Ui.miniAction(this, "Ligar / WhatsApp");
            contact.setOnClickListener(v -> openWhatsApp(phone));
            actions.addView(contact);
        }
        Button problem = Ui.miniAction(this, "Problema");
        problem.setOnClickListener(v -> occurrencePanel());
        actions.addView(problem);
        root.addView(actions);
    }

    private LinearLayout premiumPaymentPanel(Map<String, Object> stop) {
        LinearLayout panel = Ui.paymentCard(this);
        String payment = mapString(stop, "formaPagamento", "metodoPagamento", "pagamento");
        double collect = mapDouble(stop, "valorReceberCliente", "amountToCollect", "valorCobrar", "totalPedido");
        double changeTo = mapDouble(stop, "trocoPara", "troco_para");
        boolean needsMachine = mapBool(stop, "precisaMaquininha") || payment.toUpperCase(Locale.ROOT).contains("CARTÃO") || payment.toUpperCase(Locale.ROOT).contains("CARTAO");
        String machineName = mapString(stop, "maquininhaNome", "maquininhaApelido", "terminalNome", "terminalId");
        boolean paidOnline = isPaidOnlinePayment(payment) || mapBool(stop, "pagoOnline");

        LinearLayout method = new LinearLayout(this);
        method.setOrientation(LinearLayout.VERTICAL);
        method.addView(Ui.muted(this, "Pagamento", 10));
        TextView methodValue = Ui.text(this, paidOnline ? "Pago online" : (payment.isEmpty() ? "Não informado" : payment), 15, true);
        method.addView(methodValue);
        panel.addView(method, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f));

        LinearLayout amount = new LinearLayout(this);
        amount.setOrientation(LinearLayout.VERTICAL);
        amount.addView(Ui.muted(this, paidOnline ? "Situação" : "Receber", 10));
        TextView amountValue = Ui.text(this, paidOnline ? "Não cobrar" : (collect > 0 ? formatMoney(collect) : "Confirmar valor"), 16, true);
        amountValue.setTextColor(Ui.color(this, R.color.up_success));
        amount.addView(amountValue);
        panel.addView(amount, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (!paidOnline && changeTo > 0) {
            LinearLayout change = new LinearLayout(this);
            change.setOrientation(LinearLayout.VERTICAL);
            change.addView(Ui.muted(this, "Troco para", 10));
            TextView changeValue = Ui.text(this, formatMoney(changeTo), 16, true);
            changeValue.setTextColor(Ui.color(this, R.color.up_success));
            change.addView(changeValue);
            panel.addView(change, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        } else if (!paidOnline && needsMachine) {
            LinearLayout machine = new LinearLayout(this);
            machine.setOrientation(LinearLayout.VERTICAL);
            machine.addView(Ui.muted(this, "Recurso", 10));
            TextView machineValue = Ui.text(this, machineName.isEmpty() ? "Maquininha" : machineName, 14, true);
            machineValue.setTextColor(Ui.color(this, R.color.up_warning));
            machine.addView(machineValue);
            panel.addView(machine, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return panel;
    }

    private void showPremiumUpcomingStops(List<Map<String, Object>> stops, int current) {
        if (stops == null || current + 1 >= stops.size()) return;
        TextView title = Ui.muted(this, "Próximas paradas", 11);
        title.setPadding(Ui.dp(this, 4), Ui.dp(this, 3), 0, Ui.dp(this, 4));
        root.addView(title);

        for (int i = current + 1; i < stops.size(); i++) {
            Map<String, Object> x = stops.get(i);
            LinearLayout row = Ui.upcomingStopCard(this);
            TextView number = Ui.centered(this, String.valueOf(i + 1), 12, true);
            number.setMinWidth(Ui.dp(this, 32)); number.setMinHeight(Ui.dp(this, 32)); number.setGravity(Gravity.CENTER);
            number.setBackground(Ui.rounded(this, R.color.up_surface_alt, 999, R.color.up_border, 1));
            number.setTextColor(Ui.color(this, R.color.up_text_muted));
            row.addView(number);

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 8), 0);
            String order = mapString(x, "numeroPedido", "codigoPedido", "pedidoId", "orderId");
            labels.addView(Ui.text(this, i == current + 1 ? "Próxima parada" : "Depois", 12, true));
            if (!order.isEmpty()) labels.addView(Ui.muted(this, "Pedido #" + order, 10));
            row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            String payment = mapString(x, "formaPagamento", "metodoPagamento", "pagamento");
            double collect = mapDouble(x, "valorReceberCliente", "amountToCollect", "valorCobrar");
            boolean paid = isPaidOnlinePayment(payment) || mapBool(x, "pagoOnline");
            String upcomingMachine = mapString(x, "maquininhaNome", "maquininhaApelido", "terminalNome", "terminalId");
            boolean upcomingNeedsMachine = mapBool(x, "precisaMaquininha") || payment.toUpperCase(Locale.ROOT).contains("CARTÃO") || payment.toUpperCase(Locale.ROOT).contains("CARTAO");
            String payLabel = paid ? "Pago online" : (upcomingNeedsMachine ? "Cartão • " + (upcomingMachine.isEmpty() ? "maquininha" : upcomingMachine) : (payment.isEmpty() ? "Pagamento" : payment));
            TextView pay = Ui.muted(this, payLabel, 10);
            pay.setGravity(Gravity.END);
            row.addView(pay);
            root.addView(row);
        }
    }

    private void routeFullPanel() {
        List<Map<String, Object>> stops = DriverRepository.routeStops(ride);
        int current = DriverRepository.routeCurrentIndex(ride, Math.max(1, stops.size()));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < stops.size(); i++) {
            Map<String, Object> x = stops.get(i);
            String client = mapString(x, "clienteNome", "nomeCliente");
            String address = mapString(x, "endereco", "deliveryAddress", "destinoEndereco");
            String order = mapString(x, "numeroPedido", "codigoPedido", "pedidoId", "orderId");
            LinearLayout row = Ui.upcomingStopCard(this);
            TextView number = Ui.centered(this, i < current ? "✓" : String.valueOf(i + 1), 12, true);
            number.setMinWidth(Ui.dp(this, 32)); number.setMinHeight(Ui.dp(this, 32)); number.setGravity(Gravity.CENTER);
            number.setBackground(Ui.rounded(this, i == current ? R.color.up_purple : (i < current ? R.color.up_success : R.color.up_surface_alt), 999, R.color.up_border, 1));
            number.setTextColor(i <= current ? 0xFFFFFFFF : Ui.color(this, R.color.up_text_muted));
            row.addView(number);
            LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL); labels.setPadding(Ui.dp(this, 10), 0, 0, 0);
            labels.addView(Ui.text(this, (order.isEmpty() ? "Pedido" : "Pedido #" + order) + (client.isEmpty() ? "" : " • " + client), 13, true));
            labels.addView(Ui.muted(this, labelOrDash(address), 10));
            row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            content.addView(row);
        }
        Button close = Ui.secondaryButton(this, "Voltar para a parada atual");
        content.addView(close);
        InAppPanel panel = createSheet("Rota completa", stops.size() + (stops.size() == 1 ? " entrega" : " entregas") + " nesta missão.", content);
        close.setOnClickListener(v -> panel.dismiss());
        panel.show();
    }

    private String premiumRouteCode() {
        String code = DriverRepository.first(ride, "codigoRota", "numeroRota", "routeCode");
        if (!code.isEmpty()) return code.startsWith("#") ? code : "#" + code;
        if (rideId == null || rideId.isEmpty()) return "ROTA";
        String compact = rideId.replaceAll("[^A-Za-z0-9]", "");
        if (compact.length() > 5) compact = compact.substring(compact.length() - 5);
        return "#" + compact.toUpperCase(Locale.ROOT);
    }

    private void showRouteComplementOffer() {
        Object raw = ride.get("complementoOferta");
        if (!(raw instanceof Map)) return;
        @SuppressWarnings("unchecked") Map<String,Object> comp = (Map<String,Object>) raw;
        OfferAlertPlayer.start(this, "complemento:" + rideId);
        LinearLayout card = Ui.card(this);
        TextView eye = Ui.eyebrow(this, "+ NOVA ENTREGA PARA ESTA ROTA");
        eye.setTextColor(Ui.color(this, R.color.up_yellow)); card.addView(eye);
        String number = mapString(comp, "numeroPedido", "codigoPedido");
        String client = mapString(comp, "clienteNome", "nomeCliente");
        String address = mapString(comp, "endereco", "deliveryAddress");
        card.addView(Ui.text(this, (number.isEmpty() ? "Novo pedido" : "Pedido #" + number) + (client.isEmpty() ? "" : " • " + client), 19, true));
        if (!address.isEmpty()) card.addView(Ui.muted(this, address, 13));
        double extraKm = mapDouble(comp, "kmAdicional", "desvioKm");
        double extraFee = mapDouble(comp, "repasseAdicional", "ganhoAdicional");
        String meta = (extraKm > 0 ? String.format(Locale.forLanguageTag("pt-BR"), "+ %.1f km", extraKm) : "Mesmo ponto de retirada") +
                (extraFee > 0 ? " • + " + formatMoney(extraFee) : "");
        TextView m = Ui.text(this, meta, 14, true); m.setTextColor(Ui.color(this, R.color.up_success)); card.addView(m);
        card.addView(Ui.muted(this, "Você ainda não retirou os pedidos. Aceite para incluir esta entrega na mesma rota, ou recuse sem perder a rota atual.", 12));
        Button add = Ui.greenButton(this, "Adicionar à minha rota");
        Button reject = Ui.outlineButton(this, "Recusar complemento");
        add.setOnClickListener(v -> {
            if (!requireInternetForAction()) return;
            add.setEnabled(false); reject.setEnabled(false); add.setText("Adicionando…");
            repo.acceptRouteComplement(driverId, rideId).addOnSuccessListener(x -> {
                OfferAlertPlayer.stop(); toast("Pedido adicionado à rota."); reloadRide();
            }).addOnFailureListener(e -> { add.setEnabled(true); reject.setEnabled(true); add.setText("Adicionar à minha rota"); toast(e.getMessage()); });
        });
        reject.setOnClickListener(v -> {
            reject.setEnabled(false); add.setEnabled(false);
            repo.rejectRouteComplement(driverId, rideId, "Entregador optou por não adicionar este pedido.")
                    .addOnSuccessListener(x -> { OfferAlertPlayer.stop(); toast("Complemento recusado. Sua rota continua."); reloadRide(); })
                    .addOnFailureListener(e -> { reject.setEnabled(true); add.setEnabled(true); toast(e.getMessage()); });
        });
        card.addView(add); card.addView(reject); root.addView(card);
    }

    private ViewGroup routeProgress(int count, int current, boolean atStoreStage, boolean deliveryStage) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 8));
        LinearLayout row = Ui.row(this);
        String[] labels = {"Loja", "Parada " + Math.min(count, current + 1), "Final"};
        int stage = deliveryStage ? 1 : 0;
        for (int i = 0; i < labels.length; i++) {
            LinearLayout item = new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER);
            TextView dot = Ui.centered(this, i <= stage ? "●" : "○", 19, true);
            dot.setTextColor(Ui.color(this, i <= stage ? R.color.up_purple : R.color.up_text_muted)); item.addView(dot);
            TextView lab = Ui.centered(this, labels[i], 11, i == stage);
            lab.setTextColor(Ui.color(this, i == stage ? R.color.up_purple : R.color.up_text_muted)); item.addView(lab);
            row.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        wrap.addView(row);
        return wrap;
    }

    private void routeStage(Button source, String loading, String s, String delivery, String ts) {
        if (!requireInternetForAction()) return;
        String original = source.getText().toString(); source.setEnabled(false); source.setText(loading);
        repo.setRouteStage(driverId, rideId, s, delivery, ts).addOnSuccessListener(v -> {
            startTracking("NO_CLIENTE".equals(s) || "EM_ENTREGA".equals(s)); reloadRide();
        }).addOnFailureListener(e -> { source.setEnabled(true); source.setText(original); toast(e.getMessage()); });
    }

    private void routePickupPanel() {
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);

        List<Map<String, Object>> stops = DriverRepository.routeStops(ride);
        int total = Math.max(1, stops.size());
        TextView summary = Ui.text(this,
                total == 1 ? "1 pedido nesta retirada" : total + " pedidos nesta retirada",
                14, true);
        content.addView(summary);
        content.addView(Ui.muted(this,
                "Confirme somente depois que a loja entregar todos os pedidos. Não existe código extra de retirada só porque a corrida virou uma rota múltipla.",
                12));

        Button confirm = Ui.greenButton(this, "Retirei todos os pedidos");
        Button cancel = Ui.secondaryButton(this, "Ainda estou aguardando");
        content.addView(confirm); content.addView(cancel);

        InAppPanel dialog = createSheet("Confirmar retirada",
                "Ao confirmar, a rota fecha para novos pedidos e o mapa passa para a primeira entrega.", content);
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            if (!requireInternetForAction()) return;
            confirm.setEnabled(false);
            confirm.setText("Confirmando retirada…");
            repo.pickupRoute(driverId, rideId).addOnSuccessListener(x -> {
                dialog.dismiss();
                startTracking(true);
                reloadRide();
            }).addOnFailureListener(e -> {
                confirm.setEnabled(true);
                confirm.setText("Retirei todos os pedidos");
                toast(e.getMessage());
            });
        });
        dialog.show();
    }

    private void routeFinishPanel() {
        List<Map<String, Object>> stops = DriverRepository.routeStops(ride);
        int current = DriverRepository.routeCurrentIndex(ride, Math.max(1, stops.size()));
        Map<String, Object> stop = current < stops.size() ? stops.get(current) : null;
        String payment = stop == null ? "" : mapString(stop, "formaPagamento", "pagamento");
        double collect = stop == null ? 0d : mapDouble(stop, "valorReceberCliente", "amountToCollect", "valorCobrar");
        boolean paidOnline = isPaidOnlinePayment(payment) || (stop != null && mapBool(stop, "pagoOnline"));

        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        EditText code = addFormField(content, "Código de entrega", InputType.TYPE_CLASS_NUMBER);
        EditText received = addFormField(content, "Valor recebido do cliente", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (paidOnline) {
            received.setText("0.00");
            received.setEnabled(false);
        } else if (collect > 0) {
            received.setText(String.format(Locale.US, "%.2f", collect));
        }
        Button confirm = Ui.greenButton(this, "Concluir esta parada"); Button cancel = Ui.secondaryButton(this, "Voltar"); content.addView(confirm); content.addView(cancel);
        InAppPanel dialog = createSheet("Entrega " + (current + 1) + " de " + Math.max(1, stops.size()),
                paidOnline ? "Pedido já pago. Peça ao cliente o código de entrega." : "Confirme o código e o valor recebido nesta parada.", content);
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            if (!requireInternetForAction()) return;
            if (code.getText().toString().trim().isEmpty()) { code.setError("Digite o código do cliente"); return; }
            confirm.setEnabled(false); confirm.setText("Concluindo…");
            repo.finishRouteStop(driverId, rideId, code.getText().toString().trim(), parseMoney(received.getText().toString()))
                    .addOnSuccessListener(remaining -> {
                        dialog.dismiss();
                        if (remaining != null && remaining > 0) {
                            toast("Entrega concluída. Próxima parada liberada.");
                            startTracking(true); reloadRide();
                        } else {
                            stopTracking(); clearCurrentRide(false); toast("Rota concluída. Bom trabalho!"); render();
                        }
                    }).addOnFailureListener(e -> { confirm.setEnabled(true); confirm.setText("Concluir esta parada"); toast(e.getMessage()); });
        });
        dialog.show();
    }

    private void showStageButtons(String status) {
        String delivery = DriverRepository.s(ride, "statusEntrega").toUpperCase(Locale.ROOT);
        String customerPhone = pick("clienteTelefone", "telefoneCliente", "clienteWhatsApp", "whatsappCliente");

        UpState upState = UpState.from(ride);
        boolean toStore = upState == UpState.TO_STORE;
        boolean atStore = upState == UpState.AT_STORE;
        boolean enRoute = upState == UpState.TO_CUSTOMER;
        boolean atCustomer = upState == UpState.AT_CUSTOMER;

        LinearLayout quick = Ui.row(this);
        if (toStore) {
            Button navigate = Ui.miniAction(this, "Navegar até a loja");
            navigate.setOnClickListener(v -> openMap(true));
            quick.addView(navigate);
        } else if (atStore) {
            TextView ready = Ui.muted(this, "Você já está na loja • confirme a retirada quando receber o pedido", 12);
            quick.addView(ready);
        } else {
            Button navigate = Ui.miniAction(this, "Navegar");
            navigate.setOnClickListener(v -> openMap(false));
            quick.addView(navigate);
            if (!customerPhone.isEmpty()) {
                Button customer = Ui.miniAction(this, "WhatsApp");
                customer.setOnClickListener(v -> openWhatsApp(customerPhone));
                quick.addView(customer);
            }
        }
        root.addView(quick);

        Button primary;
        if (toStore) {
            primary = Ui.button(this, "Cheguei à loja");
            primary.setOnClickListener(v -> stage(primary, "Confirmando chegada…", "COLETANDO", "ENTREGADOR_CHEGOU_LOJA", "chegouLojaEm"));
        } else if (atStore) {
            primary = Ui.greenButton(this, "Retirei o pedido");
            primary.setOnClickListener(v -> pickupPanel());
        } else if (enRoute) {
            primary = Ui.button(this, "Cheguei ao cliente");
            primary.setOnClickListener(v -> stage(primary, "Confirmando chegada…", "NO_CLIENTE", "ENTREGADOR_CHEGOU_CLIENTE", "chegouClienteEm"));
        } else if (atCustomer) {
            primary = Ui.greenButton(this, "Confirmar entrega");
            primary.setOnClickListener(v -> finishPanel());
        } else {
            primary = Ui.secondaryButton(this, "Atualizar corrida");
            primary.setOnClickListener(v -> reloadRide());
        }
        root.addView(primary);

        Button problem = Ui.outlineButton(this, atStore ? "Problema na retirada" : "Preciso de ajuda");
        problem.setOnClickListener(v -> occurrencePanel());
        root.addView(problem);
    }

    private void stage(String s, String delivery, String ts) {
        repo.setRideStage(driverId, rideId, s, delivery, ts)
                .addOnSuccessListener(v -> {
                    startTracking("EM_ENTREGA".equals(s) || "NO_CLIENTE".equals(s));
                    reloadRide();
                })
                .addOnFailureListener(e -> toast(e.getMessage()));
    }

    private void stage(Button source, String loading, String s, String delivery, String ts) {
        if (!requireInternetForAction()) return;
        if (source == null) { stage(s, delivery, ts); return; }
        String original = source.getText().toString();
        source.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
        source.setEnabled(false);
        source.setText(loading);
        repo.setRideStage(driverId, rideId, s, delivery, ts)
                .addOnSuccessListener(v -> {
                    startTracking("EM_ENTREGA".equals(s) || "NO_CLIENTE".equals(s));
                    reloadRide();
                })
                .addOnFailureListener(e -> {
                    source.setEnabled(true);
                    source.setText(original);
                    toast(e.getMessage());
                });
    }

    private InAppPanel createSheet(String title, String subtitle, LinearLayout content) {
        activePanel = new InAppPanel(this, title, subtitle, content);
        return activePanel;
    }

    private EditText addFormField(LinearLayout parent, String label, int inputType) {
        TextInputLayout box = Ui.formField(this, label, inputType);
        parent.addView(box);
        return Ui.fieldEdit(box);
    }

    private void pickupPanel() {
        String expected = pick("codigoRetirada", "codigoLiberacao", "codigoParaRetirada", "codigoRetiradaLoja");

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        EditText code = addFormField(content,
                expected.isEmpty() ? "Código de retirada (se solicitado)" : "Código de retirada",
                InputType.TYPE_CLASS_NUMBER);

        Button confirm = Ui.greenButton(this, "Confirmar retirada");
        Button cancel = Ui.secondaryButton(this, "Agora não");
        content.addView(confirm);
        content.addView(cancel);

        InAppPanel dialog = createSheet(
                "Retirada na loja",
                expected.isEmpty()
                        ? "Confira o pedido antes de sair. Se a loja pedir código, informe aqui."
                        : "Digite o código que a loja informar para liberar a retirada.",
                content);

        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            if (!requireInternetForAction()) return;
            String original = confirm.getText().toString();
            confirm.setEnabled(false);
            confirm.setText("Confirmando…");
            repo.pickupRide(driverId, rideId, code.getText().toString().trim())
                    .addOnSuccessListener(x -> {
                        dialog.dismiss();
                        startTracking(true);
                        reloadRide();
                    })
                    .addOnFailureListener(e -> {
                        confirm.setEnabled(true);
                        confirm.setText(original);
                        toast(e.getMessage());
                    });
        });
        dialog.show();
    }

    private void finishPanel() {
        String payment = pick("formaPagamento", "metodoPagamento", "pagamento").toUpperCase(Locale.ROOT);
        double expectedReceive = firstNum("valorReceberCliente", "amountToCollect", "totalPedido", "valorTotalPedido", "valorPedido");
        boolean paidOnline = isPaidOnlinePayment(payment);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        EditText code = addFormField(content, "Código de entrega", InputType.TYPE_CLASS_NUMBER);

        EditText received = addFormField(content, "Valor recebido do cliente",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (expectedReceive > 0) received.setText(String.format(Locale.US, "%.2f", expectedReceive));
        if (paidOnline) {
            received.setText("0.00");
            received.setEnabled(false);
        }

        if (!paidOnline) {
            content.addView(Ui.noticeCard(this, "Conferência rápida",
                    "Confirme o valor recebido antes de finalizar. O acerto será registrado automaticamente.",
                    R.color.up_info));
        }

        Button confirm = Ui.greenButton(this, "Finalizar entrega");
        Button cancel = Ui.secondaryButton(this, "Voltar");
        content.addView(confirm);
        content.addView(cancel);

        InAppPanel dialog = createSheet(
                "Entrega ao cliente",
                paidOnline
                        ? "Pedido já pago. Peça ao cliente apenas o código de entrega."
                        : "Peça o código ao cliente e confirme o valor recebido.",
                content);

        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            if (!requireInternetForAction()) return;
            if (code.getText().toString().trim().isEmpty()) {
                code.setError("Digite o código do cliente");
                return;
            }
            double amount = parseMoney(received.getText().toString());
            String original = confirm.getText().toString();
            confirm.setEnabled(false);
            confirm.setText("Finalizando…");
            repo.finishRide(driverId, rideId, code.getText().toString().trim(), amount)
                    .addOnSuccessListener(x -> {
                        dialog.dismiss();
                        stopTracking();
                        clearCurrentRide(false);
                        toast("Entrega concluída.");
                        render();
                    })
                    .addOnFailureListener(e -> {
                        confirm.setEnabled(true);
                        confirm.setText(original);
                        toast(e.getMessage());
                    });
        });
        dialog.show();
    }

    private void reasonPanel() {
        String[] reasons = {"Longe demais", "Outra entrega", "Problema no veículo", "Encerrando expediente", "Outro"};
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        InAppPanel dialog = createSheet("Recusar entrega",
                "Escolha o motivo. A corrida volta para decisão do gestor e não será repassada automaticamente.",
                content);

        for (String reason : reasons) {
            Button option = Ui.secondaryButton(this, reason);
            option.setOnClickListener(v -> {
                if (!requireInternetForAction()) return;
                option.setEnabled(false);
                (isMultiRouteMission() ? repo.rejectRoute(driverId, rideId, reason) : repo.rejectRide(driverId, rideId, reason))
                        .addOnSuccessListener(x -> {
                            dialog.dismiss();
                            OfferAlertPlayer.stop();
                            clearCurrentRide(false);
                            toast("Recusa enviada ao gestor.");
                            render();
                        })
                        .addOnFailureListener(e -> {
                            option.setEnabled(true);
                            toast(e.getMessage());
                        });
            });
            content.addView(option);
        }

        Button cancel = Ui.outlineButton(this, "Voltar");
        cancel.setOnClickListener(v -> dialog.dismiss());
        content.addView(cancel);
        dialog.show();
    }

    private void occurrencePanel() {
        String[] reasons = {"Loja demorando", "Cliente não atende", "Endereço incorreto", "Problema no veículo", "Local inseguro", "Outro"};
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        InAppPanel dialog = createSheet("Preciso de ajuda",
                "Escolha o que está acontecendo. O gestor será avisado sem encerrar sua corrida.",
                content);

        for (String reason : reasons) {
            Button option = Ui.secondaryButton(this, reason);
            option.setOnClickListener(v -> {
                if (!requireInternetForAction()) return;
                option.setEnabled(false);
                (isMultiRouteMission() ? repo.occurrenceRoute(driverId, rideId, reason) : repo.occurrence(driverId, rideId, reason))
                        .addOnSuccessListener(x -> {
                            dialog.dismiss();
                            toast("Gestor avisado. Continue com a entrega quando puder.");
                        })
                        .addOnFailureListener(e -> {
                            option.setEnabled(true);
                            toast(e.getMessage());
                        });
            });
            content.addView(option);
        }

        Button cancel = Ui.outlineButton(this, "Voltar");
        cancel.setOnClickListener(v -> dialog.dismiss());
        content.addView(cancel);
        dialog.show();
    }

    private void reloadRide() {
        if (rideId.isEmpty()) return;
        if ("rotas_entrega".equals(missionType)) {
            repo.loadRoute(rideId).addOnSuccessListener(d -> { ride = d.exists() ? d : null; if (ride != null) lastRouteStopCount = routeStopCount(ride); render(); });
        } else {
            repo.loadRide(rideId).addOnSuccessListener(d -> { ride = d.exists() ? d : null; render(); });
        }
    }

    private void startTracking(boolean visibleToCustomer) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            toast("A localização precisa estar liberada para iniciar a missão.");
            startActivity(new Intent(this, PermissionCenterActivity.class));
            return;
        }
        OnlineService.stop(this);
        Session.saveMission(this, missionType, rideId);
        Session.saveCustomerVisible(this, visibleToCustomer);
        Intent i = new Intent(this, TrackingService.class);
        i.putExtra(TrackingService.EXTRA_RIDE, rideId);
        i.putExtra(TrackingService.EXTRA_MISSION_TYPE, missionType);
        i.putExtra(TrackingService.EXTRA_CUSTOMER_VISIBLE, visibleToCustomer);
        ContextCompat.startForegroundService(this, i);
    }

    private void stopTracking() { stopService(new Intent(this, TrackingService.class)); }

    private void clearCurrentRide(boolean rerender) {
        ride = null;
        rideId = "";
        missionType = "rides";
        lastRouteStopCount = 0;
        currentTab = 0;
        Session.clearRide(this);
        OfferAlertPlayer.stop();
        if (currentRideListener != null) { currentRideListener.remove(); currentRideListener = null; }
        stopTracking();
        if (online) OnlineService.start(this);
        if (rerender) render();
    }

    private void chooseNavigation(boolean store) {
        openMap(store);
    }

    private void openMap(boolean store) {
        String addr = store
                ? pick("enderecoLoja", "coletaEndereco", "origemEndereco")
                : pick("clienteEnderecoCompleto", "deliveryAddress", "enderecoCliente", "destinoEndereco");
        if (addr.isEmpty()) { toast("Endereço não informado nesta corrida."); return; }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(addr))));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(addr))));
        }
    }

    private void openRouteNavigation(boolean store) {
        String addr;
        if (store) {
            addr = DriverRepository.first(ride, "enderecoLoja", "coletaEndereco", "origemEndereco");
        } else {
            List<Map<String, Object>> stops = DriverRepository.routeStops(ride);
            int i = DriverRepository.routeCurrentIndex(ride, Math.max(1, stops.size()));
            addr = i < stops.size() ? mapString(stops.get(i), "endereco", "deliveryAddress", "destinoEndereco") : "";
        }
        if (addr == null || addr.trim().isEmpty()) { toast("Endereço não informado nesta parada."); return; }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(addr))));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(addr))));
        }
    }

    private void showEarnings() {
        currentTab = 2;
        render();
    }

    private void showPendingSettlements(ArrayList<DocumentSnapshot> docs) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        if (docs == null || docs.isEmpty()) {
            content.addView(Ui.muted(this, "Nenhum acerto aguardando conferência.", 13));
        } else {
            int shown = 0;
            for (DocumentSnapshot d : docs) {
                if (shown++ >= 12) break;
                String order = DriverRepository.first(d, "codigoPedido", "numeroPedido", "pedidoId");
                double received = firstNumber(d, "recebidoPeloEntregador", "valorBruto");
                double remit = firstNumber(d, "valorARepassar");
                double receive = firstNumber(d, "valorAReceber");

                LinearLayout card = Ui.compactCard(this);
                card.addView(Ui.text(this, order.isEmpty() ? "Corrida" : "Pedido #" + order, 14, true));
                card.addView(Ui.moneyRow(this, "Recebido", formatMoney(received), R.color.up_success));
                if (remit > 0) card.addView(Ui.moneyRow(this, "A repassar", formatMoney(remit), R.color.up_warning));
                if (receive > 0) card.addView(Ui.moneyRow(this, "A receber", formatMoney(receive), R.color.up_success));
                content.addView(card);
            }
        }

        Button close = Ui.secondaryButton(this, "Fechar");
        content.addView(close);
        InAppPanel dialog = createSheet("Acertos pendentes",
                "Valores aguardando conferência da loja.", content);
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static double firstNumber(DocumentSnapshot d, String... fields) {
        for (String f : fields) {
            Object o = d.get(f);
            if (o instanceof Number) return ((Number) o).doubleValue();
            if (o instanceof String) try { return Double.parseDouble(((String) o).replace(",", ".")); } catch (Exception ignored) {}
        }
        return 0d;
    }

    private void showHistory() {
        currentTab = 1;
        render();
    }

    private void showNotifications() {
        currentTab = 4;
        render();
    }

    private void showProfile() {
        currentTab = 5;
        render();
    }

    private void showPixChangePanel(String currentType, String currentKey, String currentHolder) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        EditText type = addFormField(content, "Tipo: CPF / celular / e-mail / aleatória", InputType.TYPE_CLASS_TEXT);
        type.setText(currentType == null ? "" : currentType);

        EditText key = addFormField(content, "Chave Pix", InputType.TYPE_CLASS_TEXT);
        key.setText(currentKey == null ? "" : currentKey);

        EditText holder = addFormField(content, "Nome do titular", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        holder.setText(currentHolder == null ? "" : currentHolder);

        Button send = Ui.greenButton(this, "Enviar para aprovação");
        Button cancel = Ui.secondaryButton(this, "Cancelar");
        content.addView(send);
        content.addView(cancel);

        InAppPanel dialog = createSheet("Pix de recebimento",
                "A nova chave só substituirá a atual depois da aprovação da loja.", content);
        cancel.setOnClickListener(v -> dialog.dismiss());
        send.setOnClickListener(v -> {
            String t = type.getText().toString().trim();
            String k = key.getText().toString().trim();
            String h = holder.getText().toString().trim();
            if (t.isEmpty()) { type.setError("Informe o tipo da chave"); return; }
            if (k.isEmpty()) { key.setError("Informe a chave Pix"); return; }
            if (h.isEmpty()) { holder.setError("Informe o titular"); return; }

            String original = send.getText().toString();
            send.setEnabled(false);
            send.setText("Enviando…");
            repo.requestPixChange(driverId, t, k, h)
                    .addOnSuccessListener(ok -> {
                        dialog.dismiss();
                        toast("Solicitação enviada para aprovação.");
                    })
                    .addOnFailureListener(e -> {
                        send.setEnabled(true);
                        send.setText(original);
                        toast(e.getMessage());
                    });
        });
        dialog.show();
    }

    private void openWhatsApp(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.isEmpty()) { toast("Telefone do cliente não informado."); return; }
        if (digits.length() <= 11) digits = "55" + digits;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + digits)));
        } catch (Exception e) {
            toast("Não foi possível abrir o WhatsApp.");
        }
    }

    private static String maskCpf(String raw) {
        String cpf = raw == null ? "" : raw.replaceAll("\\D", "");
        if (cpf.length() != 11) return "Não informado";
        return "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**";
    }

    private static String maskPix(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "—";
        String v = raw.trim();
        if (v.length() <= 6) return "••••";
        return v.substring(0, Math.min(3, v.length())) + "••••" + v.substring(Math.max(0, v.length() - 3));
    }

    private static String normalizePlate(String vehicleType, String raw) {
        String type = vehicleType == null ? "" : vehicleType.toLowerCase(Locale.ROOT);
        if (type.contains("bicic") || type.contains("bike")) return "Não se aplica";
        String plate = raw == null ? "" : raw.trim();
        String normalized = plate.toLowerCase(Locale.ROOT).replace(" ", "");
        if (normalized.isEmpty() || normalized.contains("semplac") || normalized.contains("naoseaplica") || normalized.contains("nãoseaplica"))
            return "Não informado";
        return plate.toUpperCase(Locale.ROOT);
    }

    private static String formatMoney(double value) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", value);
    }

    private static String shortId(String id) {
        if (id == null || id.isEmpty()) return "—";
        return id.length() <= 10 ? id : id.substring(0, 6) + "…" + id.substring(id.length() - 3);
    }

    private static String labelOrDash(String x) { return x == null || x.trim().isEmpty() ? "—" : x.trim(); }

    private static Date docDate(DocumentSnapshot d) {
        Object x = d.get("createdAt");
        if (x == null) x = d.get("criadoEm");
        if (x == null) x = d.get("updatedAt");
        return x instanceof com.google.firebase.Timestamp ? ((com.google.firebase.Timestamp) x).toDate() : null;
    }

    private static long docMillis(DocumentSnapshot d) {
        Date x = docDate(d);
        return x == null ? 0L : x.getTime();
    }

    private void alertOfferTick() {
        // Um único controlador de alerta mantém toque + vibração até aceitar, recusar ou expirar.
        OfferAlertPlayer.start(this, missionType + ":" + rideId);
    }

    private void showSystemHealth() {
        boolean internet = hasInternet();
        boolean location = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean notifications = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        if (internet && notifications && (!online || location)) return;

        LinearLayout card = Ui.compactCard(this);
        TextView title = Ui.text(this, "Seu aparelho precisa de atenção", 14, true);
        title.setTextColor(Ui.color(this, R.color.up_warning));
        card.addView(title);
        StringBuilder msg = new StringBuilder();
        if (!internet) msg.append("Sem internet. ");
        if (!notifications) msg.append("Notificações desativadas. ");
        if (online && !location) msg.append("Localização desativada.");
        card.addView(Ui.muted(this, msg.toString().trim(), 12));
        card.setOnClickListener(v -> startActivity(new Intent(this, PermissionCenterActivity.class)));
        root.addView(card);
    }

    private boolean hasInternet() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
                return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            }
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception ignored) { return false; }
    }

    private void showFooter() {
        // A navegação principal agora fica fixa na parte inferior da tela.
    }

    private void showHeader() {
        LinearLayout header = Ui.row(this);
        header.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 10));

        header.addView(Ui.upMark(this, 50));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        titles.addView(Ui.text(this, "UP Entregas", 20, true));
        String hello = driverName.isEmpty() ? "Pronto para rodar" : "Olá, " + firstName(driverName);
        titles.addView(Ui.muted(this, hello, 12));
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        android.widget.ImageView bell = Ui.iconAction(this, R.drawable.ic_bell, "Notificações");
        bell.setOnClickListener(v -> { deepReturnTab = 0; currentTab = 4; render(); });
        header.addView(bell);

        root.addView(header);
    }


    private ViewGroup showBottomNav() {
        LinearLayout bar = Ui.bottomBar(this);
        boolean mission = ride != null && ride.exists() && !Boolean.TRUE.equals(ride.getBoolean("ofertaAtiva"));

        LinearLayout home = Ui.navItemView(this, R.drawable.ic_nav_home, "Início", currentTab == 0);
        LinearLayout rides = Ui.navItemView(this, R.drawable.ic_nav_rides, mission ? "Entrega" : "Corridas", currentTab == 1);
        LinearLayout earnings = Ui.navItemView(this, R.drawable.ic_nav_wallet, "Ganhos", currentTab == 2);
        LinearLayout account = Ui.navItemView(this, R.drawable.ic_nav_account, "Conta", currentTab == 3 || currentTab == 5 || currentTab == 6);

        home.setOnClickListener(v -> { currentTab = 0; render(); });
        rides.setOnClickListener(v -> { currentTab = 1; render(); });
        earnings.setOnClickListener(v -> { currentTab = 2; render(); });
        account.setOnClickListener(v -> { deepReturnTab = 3; currentTab = 3; render(); });

        bar.addView(home, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(rides, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(earnings, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(account, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }


    private void showPageHeader(String title, boolean back) {
        LinearLayout header = Ui.row(this);
        header.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 12));
        if (back) {
            TextView arrow = Ui.headerAction(this, "‹");
            arrow.setOnClickListener(v -> {
                currentTab = deepReturnTab;
                render();
            });
            header.addView(arrow);
            TextView t = Ui.text(this, title, 22, true);
            t.setPadding(Ui.dp(this, 12), 0, 0, 0);
            header.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        } else {
            header.addView(Ui.text(this, title, 24, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        root.addView(header);
    }

    private void showRidesPage() {
        boolean hasRide = ride != null && ride.exists();
        if (hasRide) {
            showRide();
            return;
        }
        LinearLayout empty = Ui.card(this);
        empty.addView(Ui.eyebrow(this, online ? "Disponível" : "Sem corrida"));
        empty.addView(Ui.text(this, "Nenhuma entrega ativa", 21, true));
        empty.addView(Ui.muted(this,
                online ? "Quando uma corrida for enviada para você, ela aparecerá aqui." : "Fique online para receber novas entregas.", 13));
        root.addView(empty);

        root.addView(Ui.space(this, 8));
        root.addView(Ui.eyebrow(this, "Histórico recente"));
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.addView(Ui.muted(this, "Carregando corridas…", 13));
        root.addView(holder);
        repo.loadHistory(driverId).addOnSuccessListener(q -> {
            if (currentTab != 1 || ride != null) return;
            holder.removeAllViews();
            int shown = 0;
            for (DocumentSnapshot d : q.getDocuments()) {
                if (shown++ >= 8) break;
                String order = DriverRepository.first(d, "codigoPedido", "numeroPedido", "pedidoId");
                String statusText = DriverRepository.first(d, "statusEntrega", "statusCorrida", "status");
                double fee = firstNumber(d, "valorRepasseEntregador", "valorEntregador", "valorCorrida", "taxaMotoboy");
                LinearLayout card = Ui.compactCard(this);
                LinearLayout row = Ui.row(this);
                LinearLayout labels = new LinearLayout(this);
                labels.setOrientation(LinearLayout.VERTICAL);
                labels.addView(Ui.text(this, "Pedido #" + labelOrDash(order), 15, true));
                labels.addView(Ui.muted(this, statusFriendly(statusText), 12));
                row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView value = Ui.text(this, formatMoney(fee), 14, true);
                value.setTextColor(Ui.color(this, R.color.up_success));
                value.setGravity(Gravity.END);
                row.addView(value);
                card.addView(row);
                holder.addView(card);
            }
            if (holder.getChildCount() == 0) holder.addView(Ui.muted(this, "Nenhuma corrida concluída ainda.", 13));
        }).addOnFailureListener(e -> {
            if (currentTab == 1) { holder.removeAllViews(); holder.addView(Ui.muted(this, "Não foi possível carregar o histórico.", 13)); }
        });
    }

    private ViewGroup deliveryProgress(String status, String delivery) {
        String joined = (status + " " + delivery).toUpperCase(Locale.ROOT);
        int stage = 0;
        if (joined.contains("COLET") || joined.contains("CHEGOU_LOJA")) stage = 0;
        if (joined.contains("EM_ENTREGA") || joined.contains("SAIU_PARA_ENTREGA")) stage = 1;
        if (joined.contains("NO_CLIENTE") || joined.contains("CHEGOU_CLIENTE") || joined.contains("ENTREGUE")) stage = 2;

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 8));
        LinearLayout dots = Ui.row(this);
        String[] labels = {"Loja", "Em rota", "Entrega"};
        for (int i = 0; i < labels.length; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            TextView dot = Ui.centered(this, i <= stage ? "●" : "○", 19, true);
            dot.setTextColor(Ui.color(this, i <= stage ? R.color.up_purple : R.color.up_text_muted));
            item.addView(dot);
            TextView lab = Ui.centered(this, labels[i], 11, i == stage);
            lab.setTextColor(Ui.color(this, i == stage ? R.color.up_purple : R.color.up_text_muted));
            item.addView(lab);
            dots.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        wrap.addView(dots);
        return wrap;
    }

    private void showEarningsPage() {
        LinearLayout loading = Ui.card(this);
        loading.addView(Ui.muted(this, "Carregando seus ganhos…", 13));
        root.addView(loading);
        repo.loadSettlements(driverId).addOnSuccessListener(q -> {
            if (currentTab != 2) return;
            root.removeView(loading);
            long now = System.currentTimeMillis();
            long day = 24L * 60L * 60L * 1000L;
            double today = 0d, received = 0d, remit = 0d, toReceive = 0d;
            int countToday = 0;
            for (DocumentSnapshot d : q.getDocuments()) {
                Date created = docDate(d);
                long age = created == null ? Long.MAX_VALUE : Math.max(0L, now - created.getTime());
                if (age > day) continue;
                today += firstNumber(d, "taxaMotoboy", "valorCorrida", "valorRepasseEntregador");
                countToday++;
                received += firstNumber(d, "recebidoPeloEntregador", "valorBruto");
                remit += firstNumber(d, "valorARepassar");
                toReceive += firstNumber(d, "valorAReceber");
            }

            LinearLayout hero = Ui.accentGradientCard(this);
            hero.addView(Ui.muted(this, "Ganhos hoje", 12));
            TextView total = Ui.text(this, formatMoney(today), 31, true);
            total.setTextColor(0xFFFFFFFF);
            hero.addView(total);
            hero.addView(Ui.muted(this, countToday + (countToday == 1 ? " corrida" : " corridas"), 12));
            root.addView(hero, Math.max(0, root.getChildCount() - 1));

            LinearLayout details = Ui.card(this);
            details.addView(Ui.moneyRow(this, "Recebido de clientes", formatMoney(received), R.color.up_success));
            details.addView(Ui.divider(this));
            details.addView(Ui.moneyRow(this, "A repassar à loja", formatMoney(remit), R.color.up_warning));
            details.addView(Ui.divider(this));
            details.addView(Ui.moneyRow(this, "A receber da loja", formatMoney(toReceive), R.color.up_text));
            root.addView(details, Math.max(0, root.getChildCount() - 1));

            LinearLayout history = Ui.menuRow(this, "$", "Ver histórico", "Corridas e valores anteriores");
            history.setOnClickListener(v -> { currentTab = 1; render(); });
            root.addView(history, Math.max(0, root.getChildCount() - 1));
        }).addOnFailureListener(e -> {
            if (currentTab == 2) {
                loading.removeAllViews();
                loading.addView(Ui.text(this, "Não foi possível carregar os ganhos", 15, true));
                loading.addView(Ui.muted(this, "Tente novamente em instantes.", 12));
            }
        });
    }

    private void showAccountPage() {
        LinearLayout profile = Ui.card(this);
        LinearLayout row = Ui.row(this);
        TextView avatar = Ui.centered(this, initials(driverName), 18, true);
        avatar.setTextColor(0xFFFFFFFF);
        avatar.setMinWidth(Ui.dp(this, 58));
        avatar.setMinHeight(Ui.dp(this, 58));
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Ui.rounded(this, R.color.up_purple, 999, R.color.up_purple, 0));
        row.addView(avatar);

        LinearLayout who = new LinearLayout(this);
        who.setOrientation(LinearLayout.VERTICAL);
        who.setPadding(Ui.dp(this, 14), 0, 0, 0);
        who.addView(Ui.text(this, driverName.isEmpty() ? "Entregador" : driverName, 16, true));
        who.addView(Ui.muted(this, "Entregador parceiro", 12));
        TextView approved = Ui.pill(this, "APROVADO", R.color.up_success);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.setMargins(0, Ui.dp(this, 5), 0, 0);
        who.addView(approved, ap);
        row.addView(who, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        profile.addView(row);
        root.addView(profile);

        root.addView(Ui.sectionTitle(this, "Sua conta"));

        LinearLayout account = Ui.menuRow(this, "●", "Minha conta e veículo", "Dados pessoais e veículo");
        account.setOnClickListener(v -> { deepReturnTab = 3; currentTab = 5; render(); });
        root.addView(account);

        LinearLayout pix = Ui.menuRow(this, "$", "Pix e pagamentos", "Recebimentos e chave Pix");
        pix.setOnClickListener(v -> { deepReturnTab = 3; currentTab = 6; render(); });
        root.addView(pix);

        LinearLayout operation = Ui.menuRow(this, "↔", "Operação", "Troco, maquininha e disponibilidade");
        operation.setOnClickListener(v -> { deepReturnTab = 3; currentTab = 7; render(); });
        root.addView(operation);

        LinearLayout notifications = Ui.menuRow(this, "•", "Notificações", "Avisos e novas corridas");
        notifications.setOnClickListener(v -> { deepReturnTab = 3; currentTab = 4; render(); });
        root.addView(notifications);

        LinearLayout permissionsRow = Ui.menuRow(this, "+", "Permissões e bateria", "Só mexa aqui se o app pedir");
        permissionsRow.setOnClickListener(v -> startActivity(new Intent(this, PermissionCenterActivity.class)));
        root.addView(permissionsRow);

        LinearLayout appearance = Ui.menuRow(this, "☼", "Aparência", ThemePrefs.isDark(this) ? "Tema escuro" : "Tema claro");
        appearance.setOnClickListener(v -> ThemePrefs.toggle(this));
        root.addView(appearance);

        LinearLayout about = Ui.compactCard(this);
        LinearLayout brand = Ui.row(this);
        brand.addView(Ui.upMark(this, 46));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(Ui.dp(this, 10), 0, 0, 0);
        labels.addView(Ui.text(this, "UP Entregas", 14, true));
        labels.addView(Ui.muted(this, "Rodrigues Açaí e Cia • " + BuildConfig.VERSION_NAME, 11));
        brand.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        about.addView(brand);
        root.addView(about);

        LinearLayout logoutRow = Ui.menuRow(this, "↪", "Sair da conta", "");
        TextView logoutTitle = (TextView) logoutRow.getTag();
        if (logoutTitle != null) logoutTitle.setTextColor(Ui.color(this, R.color.up_danger));
        logoutRow.setOnClickListener(v -> logout());
        root.addView(logoutRow);
    }

    private void showNotificationsPage() {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.addView(Ui.muted(this, "Carregando notificações…", 13));
        root.addView(holder);
        repo.loadNotifications(driverId).addOnSuccessListener(q -> {
            if (currentTab != 4) return;
            holder.removeAllViews();
            ArrayList<DocumentSnapshot> docs = new ArrayList<>(q.getDocuments());
            docs.sort((a, b) -> Long.compare(docMillis(b), docMillis(a)));
            int shown = 0;
            for (DocumentSnapshot d : docs) {
                if (shown++ >= 20) break;
                String title = DriverRepository.first(d, "title", "titulo", "categoria", "category");
                String msg = DriverRepository.first(d, "message", "mensagem");
                LinearLayout card = Ui.compactCard(this);
                card.addView(Ui.text(this, title.isEmpty() ? "UP Entregas" : title, 15, true));
                if (!msg.isEmpty()) card.addView(Ui.muted(this, msg, 13));
                Date when = docDate(d);
                if (when != null) card.addView(Ui.muted(this, String.valueOf(android.text.format.DateFormat.format("dd/MM • HH:mm", when)), 11));
                holder.addView(card);
            }
            if (holder.getChildCount() == 0) holder.addView(Ui.muted(this, "Nenhuma notificação por enquanto.", 13));
        }).addOnFailureListener(e -> {
            if (currentTab == 4) { holder.removeAllViews(); holder.addView(Ui.muted(this, "Não foi possível carregar as notificações.", 13)); }
        });
    }

    private void showProfilePage() {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.addView(Ui.muted(this, "Carregando cadastro…", 13));
        root.addView(holder);
        repo.loadDriver(driverId, new DriverRepository.DriverCallback() {
            @Override public void onResult(DocumentSnapshot d) {
                if (currentTab != 5) return;
                holder.removeAllViews();
                String name = DriverRepository.first(d, "nomeCompleto", "nome");
                String cpf = DriverRepository.first(d, "cpf", "documentoCpf");
                String type = DriverRepository.first(d, "tipoVeiculo", "modalidade");
                String brand = DriverRepository.first(d, "marcaVeiculo", "marca");
                String model = DriverRepository.first(d, "modeloVeiculo", "modelo");
                String color = DriverRepository.first(d, "corVeiculo", "cor");
                String plate = normalizePlate(type, DriverRepository.first(d, "placa"));
                String phone = DriverRepository.first(d, "whatsapp", "telefone");

                LinearLayout personal = Ui.card(MainActivity.this);
                personal.addView(Ui.eyebrow(MainActivity.this, "Dados pessoais"));
                personal.addView(Ui.dataLine(MainActivity.this, "Nome", labelOrDash(name)));
                personal.addView(Ui.dataLine(MainActivity.this, "CPF", maskCpf(cpf)));
                personal.addView(Ui.dataLine(MainActivity.this, "WhatsApp", labelOrDash(phone)));
                holder.addView(personal);

                StringBuilder vehicle = new StringBuilder(labelOrDash(type));
                if (!brand.isEmpty()) vehicle.append(" • ").append(brand);
                if (!model.isEmpty()) vehicle.append(" • ").append(model);
                if (!color.isEmpty()) vehicle.append(" • ").append(color);
                LinearLayout vehicleCard = Ui.card(MainActivity.this);
                vehicleCard.addView(Ui.eyebrow(MainActivity.this, "Veículo"));
                vehicleCard.addView(Ui.dataLine(MainActivity.this, "Veículo", vehicle.toString()));
                vehicleCard.addView(Ui.dataLine(MainActivity.this, "Placa", plate));
                holder.addView(vehicleCard);

                holder.addView(Ui.muted(MainActivity.this, "Alterações sensíveis continuam sujeitas à aprovação da loja.", 12));
            }
            @Override public void onError(Exception e) {
                if (currentTab == 5) { holder.removeAllViews(); holder.addView(Ui.muted(MainActivity.this, "Não foi possível carregar seu cadastro.", 13)); }
            }
        });
    }

    private void showPixPage() {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.addView(Ui.muted(this, "Carregando dados de pagamento…", 13));
        root.addView(holder);
        repo.loadDriver(driverId, new DriverRepository.DriverCallback() {
            @Override public void onResult(DocumentSnapshot d) {
                if (currentTab != 6) return;
                holder.removeAllViews();
                String pix = DriverRepository.first(d, "pixChave", "pix", "chavePix");
                String pixType = DriverRepository.first(d, "pixTipo", "tipoPix");
                String pixHolder = DriverRepository.first(d, "pixTitular", "titularPix");
                LinearLayout card = Ui.card(MainActivity.this);
                card.addView(Ui.eyebrow(MainActivity.this, "Recebimento"));
                card.addView(Ui.dataLine(MainActivity.this, "Tipo", pixType.isEmpty() ? "Não cadastrado" : pixType));
                card.addView(Ui.dataLine(MainActivity.this, "Chave", pix.isEmpty() ? "Não cadastrada" : maskPix(pix)));
                if (!pixHolder.isEmpty()) card.addView(Ui.dataLine(MainActivity.this, "Titular", pixHolder));
                Button change = Ui.button(MainActivity.this, pix.isEmpty() ? "Cadastrar Pix" : "Solicitar alteração do Pix");
                change.setOnClickListener(v -> showPixChangePanel(pixType, pix, pixHolder));
                card.addView(change);
                holder.addView(card);
                holder.addView(Ui.muted(MainActivity.this, "A chave nova só entra em vigor após aprovação da loja.", 12));
            }
            @Override public void onError(Exception e) {
                if (currentTab == 6) { holder.removeAllViews(); holder.addView(Ui.muted(MainActivity.this, "Não foi possível carregar os dados de pagamento.", 13)); }
            }
        });
    }

    private void showOperationPage() {
        DeviceStatus.Battery battery = DeviceStatus.battery(this);
        batteryLevel = battery.level;
        batteryCharging = battery.charging;
        if (battery.level >= 0) repo.saveDeviceTelemetry(driverId, battery.level, battery.charging);

        LinearLayout state = Ui.card(this);
        state.addView(Ui.eyebrow(this, "Status enviado à central"));
        LinearLayout statusRow = Ui.row(this);
        LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL);
        left.addView(Ui.text(this, battery.level >= 0 ? "Bateria " + battery.level + "%" : "Bateria indisponível", 20, true));
        left.addView(Ui.muted(this, battery.charging ? "Carregando agora" : "Atualização automática enquanto estiver online", 12));
        statusRow.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView onlinePill = Ui.pill(this, online ? "ONLINE" : "OFFLINE", online ? R.color.up_success : R.color.up_text_muted);
        statusRow.addView(onlinePill);
        state.addView(statusRow);
        if (upConfig != null) {
            int limit = (int) Math.round(numberFrom(upConfig, "batteryAlertPct"));
            if (limit > 0 && battery.level >= 0 && battery.level < limit) {
                state.addView(Ui.noticeCard(this, "Bateria baixa", "A central está configurada para alertar abaixo de " + limit + "%.", R.color.up_warning));
            }
        }
        root.addView(state);

        LinearLayout equipment = Ui.card(this);
        equipment.addView(Ui.eyebrow(this, "O que você tem disponível agora"));
        equipment.addView(Ui.muted(this, "Essas informações ajudam o gestor a não enviar uma corrida que exige algo que você não tem.", 12));

        SwitchMaterial cashSwitch = new SwitchMaterial(this);
        cashSwitch.setText("Tenho troco disponível");
        cashSwitch.setTextColor(Ui.color(this, R.color.up_text));
        cashSwitch.setTextSize(15);
        cashSwitch.setChecked(hasCash);
        equipment.addView(cashSwitch);

        EditText cash = addFormField(equipment, "Valor de troco disponível", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        cash.setText(String.format(Locale.US, "%.2f", cashAvailable));
        cash.setEnabled(hasCash);
        cashSwitch.setOnCheckedChangeListener((b, checked) -> cash.setEnabled(checked));

        equipment.addView(Ui.divider(this));

        SwitchMaterial machineSwitch = new SwitchMaterial(this);
        machineSwitch.setText("Estou com maquininha");
        machineSwitch.setTextColor(Ui.color(this, R.color.up_text));
        machineSwitch.setTextSize(15);
        machineSwitch.setChecked(hasMachine);
        equipment.addView(machineSwitch);

        EditText machine = addFormField(equipment, "Tipos aceitos (ex.: débito e crédito)", InputType.TYPE_CLASS_TEXT);
        machine.setText(machineTypes);
        machine.setEnabled(hasMachine);
        machineSwitch.setOnCheckedChangeListener((b, checked) -> machine.setEnabled(checked));

        Button save = Ui.button(this, "Salvar disponibilidade");
        save.setOnClickListener(v -> {
            if (!requireInternetForAction()) return;
            boolean hc = cashSwitch.isChecked();
            double value = hc ? parseMoney(cash.getText().toString()) : 0d;
            boolean hm = machineSwitch.isChecked();
            String types = hm ? machine.getText().toString().trim() : "";
            save.setEnabled(false); save.setText("Salvando…");
            repo.saveOperationalEquipment(driverId, hc, value, hm, types).addOnSuccessListener(x -> {
                hasCash = hc; cashAvailable = value; hasMachine = hm; machineTypes = types;
                save.setEnabled(true); save.setText("Salvo");
                toast("Disponibilidade enviada ao UP Central.");
            }).addOnFailureListener(e -> { save.setEnabled(true); save.setText("Salvar disponibilidade"); toast(e.getMessage()); });
        });
        equipment.addView(save);
        root.addView(equipment);

        LinearLayout note = Ui.compactCard(this);
        note.addView(Ui.text(this, "Como o gestor usa isso", 14, true));
        note.addView(Ui.muted(this, "O UP Central cruza bateria, GPS, troco e maquininha com cada pedido. A escolha do entregador continua manual.", 12));
        root.addView(note);
    }

    private static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "UP";
        String[] p = name.trim().split("\\s+");
        String a = p[0].substring(0, 1).toUpperCase(Locale.ROOT);
        String b = p.length > 1 ? p[p.length - 1].substring(0, 1).toUpperCase(Locale.ROOT) : "";
        return a + b;
    }

    private static String statusFriendly(String raw) {
        String s = raw == null ? "" : raw.replace('_', ' ').trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return "Concluída";
        if (s.contains("entregue") || s.contains("finaliz") || s.contains("conclu")) return "Entregue";
        if (s.contains("cancel")) return "Cancelada";
        if (s.contains("recus") || s.contains("rejeit")) return "Recusada";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void loadTodaySummary(TextView gainsValue, TextView ridesValue) {
        repo.loadSettlements(driverId).addOnSuccessListener(q -> {
            long now = System.currentTimeMillis();
            long day = 24L * 60L * 60L * 1000L;
            double today = 0d;
            int count = 0;
            for (DocumentSnapshot d : q.getDocuments()) {
                Date created = docDate(d);
                if (created == null || now - created.getTime() > day) continue;
                today += firstNumber(d, "taxaMotoboy", "valorCorrida", "valorRepasseEntregador");
                count++;
            }
            gainsValue.setText(formatMoney(today));
            ridesValue.setText(String.valueOf(count));
        }).addOnFailureListener(e -> {
            gainsValue.setText("R$ —");
            ridesValue.setText("—");
        });
    }

    private void showAccountMenu() {
        currentTab = 3;
        render();
    }

    private void logout() {
        if (!rideId.isEmpty()) {
            toast("Finalize a entrega atual antes de sair da conta.");
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        Button leave = Ui.dangerButton(this, "Sair da conta");
        Button stay = Ui.secondaryButton(this, "Continuar conectado");
        content.addView(leave);
        content.addView(stay);

        InAppPanel dialog = createSheet("Sair do UP Entregas",
                "Você ficará offline e precisará entrar novamente para receber corridas.", content);

        stay.setOnClickListener(v -> dialog.dismiss());
        leave.setOnClickListener(v -> {
            leave.setEnabled(false);
            repo.setOnline(driverId, false);
            OnlineService.stop(this);
            Session.clear(this);
            FirebaseAuth.getInstance().signOut();
            stopTracking();
            dialog.dismiss();
            recreate();
        });
        dialog.show();
    }

    private void publishTelemetry() {
        if (driverId == null || driverId.isEmpty()) return;
        DeviceStatus.Battery b = DeviceStatus.battery(this);
        batteryLevel = b.level; batteryCharging = b.charging;
        if (b.level >= 0) repo.saveDeviceTelemetry(driverId, b.level, b.charging);
    }

    private boolean hasActiveMission() {
        return ride != null && ride.exists() && !Boolean.TRUE.equals(ride.getBoolean("ofertaAtiva"));
    }

    private boolean isMultiRouteMission() {
        return "rotas_entrega".equals(missionType) || DriverRepository.isMultiRoute(ride);
    }

    private int routeStopCount(DocumentSnapshot d) {
        if (d == null || !d.exists()) return 0;
        return Math.max(DriverRepository.routeStops(d).size(), DriverRepository.routeOrderIds(d).size());
    }

    private String missionIdFromIntent(Intent intent) {
        if (intent == null) return "";
        String[] keys = {"mission_id", "route_id", "ride_id"};
        for (String k : keys) { String v = intent.getStringExtra(k); if (v != null && !v.trim().isEmpty()) return v.trim(); }
        return "";
    }

    private String routeStoreName() {
        String x = DriverRepository.first(ride, "coletaNome", "lojaNome", "storeName");
        return x == null || x.trim().isEmpty() ? "Rodrigues Açaí e Cia" : x.trim();
    }

    private static double numberFrom(DocumentSnapshot d, String field) {
        if (d == null || field == null) return 0d;
        Object o = d.get(field);
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) try { return Double.parseDouble(((String) o).replace(",", ".")); } catch (Exception ignored) {}
        return 0d;
    }

    private static String mapString(Map<String, Object> map, String... keys) {
        if (map == null) return "";
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null && !String.valueOf(v).trim().isEmpty()) return String.valueOf(v).trim();
        }
        return "";
    }

    private static double mapDouble(Map<String, Object> map, String... keys) {
        if (map == null) return 0d;
        for (String k : keys) {
            Object v = map.get(k);
            if (v instanceof Number) return ((Number) v).doubleValue();
            if (v instanceof String) try { return Double.parseDouble(((String) v).replace(",", ".")); } catch (Exception ignored) {}
        }
        return 0d;
    }

    private static boolean mapBool(Map<String, Object> map, String key) {
        if (map == null || key == null) return false;
        Object v = map.get(key);
        return Boolean.TRUE.equals(v) || (v != null && "true".equalsIgnoreCase(String.valueOf(v)));
    }

    private List<MissionMapView.Point> singleMapPoints() {
        ArrayList<MissionMapView.Point> out = new ArrayList<>();
        double storeLat = numberFrom(ride, "lojaLat");
        double storeLng = numberFrom(ride, "lojaLng");
        String storeName = DriverRepository.first(ride, "coletaNome", "lojaNome");
        String storeAddress = DriverRepository.first(ride, "enderecoLoja", "coletaEndereco", "origemEndereco");
        if (Math.abs(storeLat) > .000001 && Math.abs(storeLng) > .000001)
            out.add(new MissionMapView.Point(storeName.isEmpty() ? "Loja" : storeName, storeAddress, storeLat, storeLng, "store", 0));

        double clientLat = numberFrom(ride, "clienteLat");
        if (clientLat == 0d) clientLat = numberFrom(ride, "destinoLat");
        double clientLng = numberFrom(ride, "clienteLng");
        if (clientLng == 0d) clientLng = numberFrom(ride, "destinoLng");
        String client = DriverRepository.first(ride, "clienteNome", "nomeCliente");
        String clientAddress = DriverRepository.first(ride, "clienteEnderecoCompleto", "deliveryAddress", "enderecoCliente", "destinoEndereco");
        if (Math.abs(clientLat) > .000001 && Math.abs(clientLng) > .000001)
            out.add(new MissionMapView.Point(client.isEmpty() ? "Cliente" : client, clientAddress, clientLat, clientLng, "active", 1));

        Object loc = ride == null ? null : ride.get("localizacaoEntregador");
        if (loc instanceof Map) {
            @SuppressWarnings("unchecked") Map<String,Object> m = (Map<String,Object>) loc;
            double lat = mapDouble(m, "lat", "latitude"), lng = mapDouble(m, "lng", "longitude");
            if (Math.abs(lat) > .000001 && Math.abs(lng) > .000001)
                out.add(new MissionMapView.Point("Você", "Localização atual", lat, lng, "driver", 0));
        }
        return out;
    }

    private List<MissionMapView.Point> routeMapPoints(List<Map<String, Object>> stops, int current) {
        ArrayList<MissionMapView.Point> out = new ArrayList<>();
        double storeLat = numberFrom(ride, "lojaLat");
        if (storeLat == 0d) storeLat = numberFrom(ride, "storeLat");
        double storeLng = numberFrom(ride, "lojaLng");
        if (storeLng == 0d) storeLng = numberFrom(ride, "storeLng");
        if (storeLat == 0) { Object o = ride == null ? null : ride.get("coleta.coords.lat"); if (o instanceof Number) storeLat = ((Number)o).doubleValue(); }
        if (storeLng == 0) { Object o = ride == null ? null : ride.get("coleta.coords.lng"); if (o instanceof Number) storeLng = ((Number)o).doubleValue(); }
        String storeName = DriverRepository.first(ride, "coletaNome", "lojaNome");
        String storeAddress = DriverRepository.first(ride, "enderecoLoja", "coletaEndereco", "origemEndereco");
        if (Math.abs(storeLat) > .000001 && Math.abs(storeLng) > .000001)
            out.add(new MissionMapView.Point(storeName.isEmpty() ? "Loja" : storeName, storeAddress, storeLat, storeLng, "store", 0));
        for (int i = 0; i < stops.size(); i++) {
            Map<String, Object> x = stops.get(i);
            double lat = mapDouble(x, "lat", "latitude"); double lng = mapDouble(x, "lng", "longitude");
            if (Math.abs(lat) < .000001 || Math.abs(lng) < .000001) continue;
            String name = mapString(x, "clienteNome", "nomeCliente"); String address = mapString(x, "endereco", "deliveryAddress");
            out.add(new MissionMapView.Point(name.isEmpty() ? "Parada " + (i + 1) : name, address, lat, lng, i == current ? "active" : "stop", i + 1));
        }
        Object loc = ride == null ? null : ride.get("localizacaoEntregador");
        if (loc instanceof Map) {
            @SuppressWarnings("unchecked") Map<String,Object> m = (Map<String,Object>) loc;
            double lat = mapDouble(m, "lat", "latitude"), lng = mapDouble(m, "lng", "longitude");
            if (Math.abs(lat) > .000001 && Math.abs(lng) > .000001) out.add(new MissionMapView.Point("Você", "Localização atual", lat, lng, "driver", 0));
        }
        return out;
    }

    private static boolean isPaidOnlinePayment(String payment) {
        if (payment == null) return false;
        String p = payment.trim().toUpperCase(Locale.ROOT);
        return p.contains("ONLINE") || p.contains("JÁ PAGO") || p.contains("JA PAGO") ||
                p.contains("PAGO ONLINE") || p.contains("PIX ONLINE");
    }

    private static String firstName(String name) {
        if (name == null || name.trim().isEmpty()) return "Entregador";
        String clean = name.trim();
        int space = clean.indexOf(' ');
        return space > 0 ? clean.substring(0, space) : clean;
    }

    private Button quickAction(String text) {
        Button b = Ui.secondaryButton(this, text);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3));
        b.setLayoutParams(p);
        b.setTextSize(14);
        return b;
    }

    private boolean requireInternetForAction() {
        if (hasInternet()) return true;
        toast("Sem internet. Conecte-se antes de confirmar esta etapa.");
        return false;
    }

    private boolean hasCorePermissions() {
        boolean location = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean notification = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        return location && notification;
    }

    private void syncPresenceService() {
        boolean hasRide = ride != null && ride.exists();
        boolean activeMission = hasRide && !Boolean.TRUE.equals(ride.getBoolean("ofertaAtiva"));

        if (activeMission) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            OnlineService.stop(this);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (online) OnlineService.start(this);
            else OnlineService.stop(this);
        }
    }

    private boolean isTerminal(DocumentSnapshot d) {
        String x = (DriverRepository.s(d, "status") + " " + DriverRepository.s(d, "statusCorrida") + " " + DriverRepository.s(d, "statusEntrega")).toUpperCase(Locale.ROOT);
        return x.contains("CANCELAD") || x.contains("CONCLUID") || x.contains("FINALIZ") || x.contains("ENTREGUE") || x.contains("EXPIRADA") || x.contains("REJEITADA");
    }

    private String pick(String... fields) { return DriverRepository.first(ride, fields); }

    private double num(String f) {
        Object o = ride == null ? null : ride.get(f);
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) try { return Double.parseDouble(((String) o).replace(",", ".")); } catch (Exception ignored) {}
        return 0;
    }

    private double firstNum(String... fields) {
        for (String f : fields) {
            double v = num(f);
            if (v > 0) return v;
        }
        return 0;
    }

    private double parseMoney(String s) {
        try { return Double.parseDouble(s.trim().replace("R$", "").replace(" ", "").replace(",", ".")); }
        catch (Exception e) { return 0d; }
    }

    private void toast(String s) { Ui.message(this, s == null ? "Não foi possível concluir." : s); }

    @Override protected void onDestroy() {
        if (offerTimer != null) { offerTimer.cancel(); offerTimer = null; }
        if (offerListener != null) offerListener.remove();
        if (currentRideListener != null) currentRideListener.remove();
        super.onDestroy();
    }
}
