package com.rodriguesacai.entregador;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class PermissionCenterActivity extends AppCompatActivity {
    private LinearLayout root;

    private final ActivityResultLauncher<String[]> requestPermissions = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> render());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (root != null) render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        root = Ui.column(this);
        scroll.addView(root);

        LinearLayout header = Ui.row(this);
        TextView back = Ui.pill(this, "← Voltar", R.color.up_purple);
        back.setOnClickListener(v -> finish());
        header.addView(back);
        root.addView(header);
        root.addView(Ui.space(this, 10));
        root.addView(Ui.eyebrow(this, "Configuração do aparelho"));
        root.addView(Ui.text(this, "Permissões do UP Entregas", 28, true));
        root.addView(Ui.muted(this,
                "O UP explica cada necessidade aqui. A tela do Android só é aberta quando a permissão pertence ao sistema.", 14));
        root.addView(Ui.space(this, 8));

        boolean notif = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean location = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean gps = lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean battery = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());

        permissionCard("Notificações", notif,
                notif ? "Liberado. Novas corridas podem aparecer como alerta de alta prioridade."
                        : "Necessário para os avisos de nova corrida.",
                "Liberar notificações", v -> askRuntimePermissions());

        permissionCard("Localização precisa", location,
                location ? "Liberada para navegação e rastreamento durante a corrida."
                        : "Necessária quando uma corrida estiver em andamento.",
                "Liberar localização", v -> askRuntimePermissions());

        permissionCard("GPS do aparelho", gps,
                gps ? "Ativado." : "Ative a localização do aparelho para usar o rastreamento.",
                "Abrir localização do aparelho", v ->
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));

        permissionCard("Bateria / segundo plano", battery,
                battery ? "O Android não está restringindo o UP Entregas por economia de bateria."
                        : "Recomendado para reduzir o risco de o sistema interromper o serviço ONLINE.",
                "Permitir em segundo plano", v -> requestBatteryExemption());

        permissionCard("Configurações do aplicativo", true,
                "Use esta tela se o fabricante do celular tiver opções extras de inicialização automática ou bateria.",
                "Abrir configurações do app", v -> openAppSettings());

        Button test = Ui.greenButton(this, "Testar notificação de corrida");
        test.setOnClickListener(v -> {
            NotificationHelper.createChannels(this);
            NotificationHelper.notifyNewRide(this, "TESTE-PERMISSOES", "TESTE",
                    "Se este aviso apareceu no topo da tela, as notificações estão funcionando.");
        });
        root.addView(test);

        root.addView(Ui.space(this, 10));
        root.addView(Ui.muted(this,
                "Quando você estiver online ou em missão, o Android poderá mostrar uma notificação permanente para manter o serviço ativo. Ela não substitui as telas do UP.", 12));
        setContentView(scroll);
    }

    private void permissionCard(String title, boolean ok, String detail, String buttonText,
                                android.view.View.OnClickListener click) {
        LinearLayout c = Ui.card(this);
        LinearLayout row = Ui.row(this);
        TextView titleView = Ui.text(this, title, 18, true);
        row.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.pill(this, ok ? "Liberado" : "Ajustar", ok ? R.color.up_success : R.color.up_warning));
        c.addView(row);
        c.addView(Ui.muted(this, detail, 13));
        if (!ok || title.equals("Configurações do aplicativo")) {
            Button b = Ui.secondaryButton(this, buttonText);
            b.setOnClickListener(click);
            c.addView(b);
        }
        root.addView(c);
    }

    private void askRuntimePermissions() {
        java.util.ArrayList<String> p = new java.util.ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            p.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            p.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!p.isEmpty()) requestPermissions.launch(p.toArray(new String[0]));
    }

    private void requestBatteryExemption() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void openAppSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }
}
