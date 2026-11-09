package com.rodriguesacai.updriver;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** UP Entregador: native UI; every displayed mission comes from the verified UP API. */
public final class MainActivity extends ComponentActivity {
    private static final int NAVY = Color.rgb(12,29,65), BLUE = Color.rgb(38,101,211);
    private static final int INK = Color.rgb(28,43,69), MUTED = Color.rgb(103,121,147);
    private static final int BG = Color.rgb(245,248,253), WHITE = Color.WHITE;
    private static final int AMBER = Color.rgb(255,191,63), GREEN = Color.rgb(23,143,104);
    private static final int LOCATION_PERMISSION = 83, NOTIFICATION_PERMISSION = 84;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Backend backend = new Backend();
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (approved && online && FirebaseAuth.getInstance().getCurrentUser() != null) {
                refreshOffer(false);
                handler.postDelayed(this, 20000);
            }
        }
    };
    private LinearLayout root, body;
    private String screen = "home", verification = "Verificando cadastro...", notice = "";
    private boolean approved, online, inFlight, loadingOffer;
    private JSONObject mission;
    private String missionId = "";
    private JSONArray history = new JSONArray();
    private JSONArray walletEntries = new JSONArray(), payouts = new JSONArray();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);
        setContentView(root);
        if (FirebaseAuth.getInstance().getCurrentUser() != null) verifyAccount();
        else { screen = "login"; render(); }
    }

    @Override protected void onResume() {
        super.onResume();
        if (approved && online) refreshOffer(false);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(poll);
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if ("mission".equals(screen) || "history".equals(screen) || "profile".equals(screen) || "wallet".equals(screen)) {
            screen = "home"; render(); return;
        }
        super.onBackPressed();
    }

    private int dp(float x) { return (int)(getResources().getDisplayMetrics().density * x + .5f); }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(radius)); return g;
    }
    private void render() {
        root.removeAllViews();
        header();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0,dp(14),0,dp(24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        if (!notice.isEmpty()) {
            LinearLayout banner = card();
            banner.setBackground(background(Color.rgb(234,242,255),16));
            text(banner, notice,14,BLUE,false);
        }
        if ("login".equals(screen)) login();
        else if ("signup".equals(screen)) signUp();
        else if ("pending".equals(screen)) pending();
        else if ("registration".equals(screen)) registration();
        else if ("history".equals(screen)) history();
        else if ("wallet".equals(screen)) wallet();
        else if ("profile".equals(screen)) profile();
        else if ("mission".equals(screen)) mission();
        else home();
        if (approved && !("login".equals(screen) || "signup".equals(screen)
                || "pending".equals(screen) || "registration".equals(screen))) {
            bottomNavigation();
        }
    }

    private void header() {
        LinearLayout top = new LinearLayout(this);
        top.setPadding(dp(20),dp(18),dp(18),dp(17));
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setBackgroundColor(NAVY);
        TextView logo = new TextView(this);
        logo.setText("UP");
        logo.setTextColor(NAVY);
        logo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        logo.setTextSize(21);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(background(AMBER,11));
        top.addView(logo,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0,-2,1);
        titleLp.leftMargin = dp(13);
        top.addView(titles,titleLp);
        text(titles,"UP ENTREGADOR",19,WHITE,true);
        text(titles,"Corridas e GPS em tempo real",11,Color.rgb(191,211,238),false);
        TextView version = new TextView(this);
        version.setText("v" + BuildConfig.VERSION_NAME);
        version.setTextSize(10);
        version.setTextColor(Color.rgb(199,217,244));
        top.addView(version);
        root.addView(top,new LinearLayout.LayoutParams(-1,-2));
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18),dp(18),dp(18),dp(18));
        c.setBackground(background(WHITE,20));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(dp(16),0,dp(16),dp(12));
        body.addView(c,p);
        return c;
    }

    private TextView text(LinearLayout parent, String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s == null ? "" : s);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setLineSpacing(dp(2),1f);
        if (bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        parent.addView(t);
        return t;
    }

    private void gap(LinearLayout parent,int px) {
        View v = new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(px)));
    }

    private MaterialButton button(LinearLayout parent,String label,int color,boolean light,Runnable action) {
        MaterialButton b = new MaterialButton(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setCornerRadius(dp(14));
        b.setTextColor(light?INK:WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(color));
        b.setEnabled(!inFlight);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,dp(55));
        lp.topMargin = dp(10);
        parent.addView(b,lp);
        b.setOnClickListener(v -> { if(!inFlight) action.run(); });
        return b;
    }

    private EditText input(LinearLayout container, String label, int type) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(label);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxCornerRadii(dp(12),dp(12),dp(12),dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin = dp(12);
        container.addView(layout,lp);
        TextInputEditText edit = new TextInputEditText(this);
        edit.setSingleLine(true);
        edit.setInputType(type);
        edit.setTextSize(15);
        layout.addView(edit,new LinearLayout.LayoutParams(-1,-2));
        return edit;
    }

    private void title(LinearLayout c,String a,String sub) {
        text(c,a,23,INK,true); gap(c,6);
        text(c,sub,13,MUTED,false); gap(c,5);
    }

    private void inform(String msg) {
        notice = msg == null ? "" : msg;
        render();
    }

    private void login() {
        LinearLayout c=card();
        title(c,"Bem-vindo de volta","Entre com sua conta de entregador.");
        EditText email=input(c,"Seu e-mail",InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText pass=input(c,"Senha",InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        button(c,"Entrar no UP",BLUE,false,()->{
            String e=email.getText().toString().trim(),p=pass.getText().toString();
            if (e.isEmpty() || p.isEmpty()) { inform("Informe e-mail e senha.");return; }
            inFlight=true; render();
            FirebaseAuth.getInstance().signInWithEmailAndPassword(e,p)
                .addOnSuccessListener(result->{inFlight=false; notice="";verifyAccount();})
                .addOnFailureListener(err->{inFlight=false;inform("Não foi possível entrar. Confira seus dados.");});
        });
        button(c,"Quero me cadastrar",Color.rgb(227,236,251),true,()->{
            screen="signup";notice="";render();
        });
        LinearLayout info = card();
        text(info,"Como funciona?",16,INK,true);gap(info,6);
        text(info,"Seu cadastro é analisado pelo UP Gestor. Somente entregadores aprovados recebem ofertas e podem iniciar corridas.",13,MUTED,false);
    }

    private void signUp() {
        LinearLayout c=card();
        title(c,"Cadastrar entregador","Dados reais e verificáveis. A aprovação será feita pelo gestor.");
        EditText name=input(c,"Nome completo",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText phone=input(c,"Telefone com DDD",InputType.TYPE_CLASS_PHONE);
        EditText vehicle=input(c,"Veículo (moto, bike...)",InputType.TYPE_CLASS_TEXT);
        EditText email=input(c,"E-mail",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText pass=input(c,"Criar senha (mín. 6 caracteres)",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        button(c,"Enviar cadastro para análise",BLUE,false,()->{
            String n=name.getText().toString().trim(), p=phone.getText().toString().replaceAll("\\D","");
            String v=vehicle.getText().toString().trim(), e=email.getText().toString().trim(), s=pass.getText().toString();
            if(n.length()<3||p.length()<10||v.isEmpty()||!e.contains("@")||s.length()<6) {
                inform("Preencha nome, telefone com DDD, veículo, e-mail e senha válida."); return;
            }
            inFlight=true; render();
            FirebaseAuth.getInstance().createUserWithEmailAndPassword(e,s).addOnSuccessListener(res->{
                inFlight=false;submitProfile(n,p,v);
            }).addOnFailureListener(err->{inFlight=false;inform("Não foi possível criar conta. Verifique o e-mail ou tente entrar.");});
        });
        button(c,"Já tenho cadastro",Color.rgb(227,236,251),true,()->{screen="login";notice="";render();});
    }

    private void verifyAccount() {
        if (FirebaseAuth.getInstance().getCurrentUser()==null) {screen="login";render();return;}
        verification="Verificando situação do cadastro...";
        screen="pending";render();
        backend.registration("status_sync", Backend.args(), new Backend.Callback() {
            @Override public void onSuccess(JSONObject data) { online=data.optBoolean("online",false); accountState(data.optString("status","missing")); }
            @Override public void onError(String message) {
                // Older imported couriers may be approved before independent registration is enabled.
                backend.driver("session",Backend.args(),new Backend.Callback(){
                    @Override public void onSuccess(JSONObject response) {
                        online=response.optBoolean("online",false);
                        approved=true;screen="home";notice="";
                        registerPush();render();refreshOffer(false);beginPolling();
                    }
                    @Override public void onError(String other) {
                        approved=false;screen="pending";
                        verification=message.equals("O cadastro independente está temporariamente indisponível.")
                            ? "O cadastro está em preparação; a liberação de corridas também depende do gestor."
                            : message;
                        render();
                    }
                });
            }
        });
    }

    private void accountState(String status) {
        if("approved".equals(status)){
            approved=true;screen="home";verification="Aprovado";notice="";
            registerPush();render();refreshOffer(false);beginPolling();return;
        }
        approved=false;screen="pending";
        verification="pending".equals(status)?"Sua inscrição aguarda análise do gestor."
            :"correction".equals(status)?"O gestor solicitou correção do cadastro."
            :"suspended".equals(status)?"Seu cadastro foi suspenso. Entre em contato com o gestor."
            :"rejected".equals(status)?"Seu cadastro não foi aprovado."
            :"Nenhum cadastro encontrado. Envie seus dados para análise.";
        render();
    }

    private void pending() {
        LinearLayout c=card();title(c,"Aguardando liberação","Sua conta existe, mas isso não autoriza aceitar corridas.");
        text(c,verification,15,INK,false);
        button(c,"Atualizar situação",BLUE,false,this::verifyAccount);
        button(c,"Preencher ou atualizar cadastro",Color.rgb(227,236,251),true,()->{
            screen="registration";notice="";render();
        });
        button(c,"Sair da conta",Color.rgb(244,245,249),true,this::signOut);
    }

    private void registration() {
        LinearLayout c=card();
        title(c,"Dados do entregador","Este formulário não aprova você automaticamente.");
        EditText name=input(c,"Nome completo",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText phone=input(c,"Celular com DDD",InputType.TYPE_CLASS_PHONE);
        EditText vehicle=input(c,"Tipo de veículo",InputType.TYPE_CLASS_TEXT);
        EditText plate=input(c,"Placa, se aplicável",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        button(c,"Enviar ao gestor",BLUE,false,()->{
            String n=name.getText().toString().trim(),p=phone.getText().toString().replaceAll("\\D","");
            String v=vehicle.getText().toString().trim();
            if(n.length()<3||p.length()<10||v.isEmpty()){inform("Informe nome completo, telefone e veículo.");return;}
            submitProfile(n,p,v,plate.getText().toString().trim());
        });
        button(c,"Voltar",Color.rgb(227,236,251),true,()->{screen="pending";notice="";render();});
    }
    private void submitProfile(String name,String phone,String vehicle){submitProfile(name,phone,vehicle,"");}
    private void submitProfile(String name,String phone,String vehicle,String plate) {
        backend.registration("submit",Backend.args("profile",Backend.args(
            "nomeCompleto",name,"telefone",phone,"tipoVeiculo",vehicle,"placa",plate)),
            new Backend.Callback(){
                @Override public void onSuccess(JSONObject data) {
                    verification="Cadastro enviado. Aguarde a análise do gestor.";
                    screen="pending"; inform("Cadastro recebido pelo UP.");
                }
                @Override public void onError(String message) {screen="pending";inform(message);}
            });
    }

    private void home() {
        LinearLayout status=card();
        status.setBackground(background(NAVY,20));
        text(status,"SEU STATUS AGORA",12,Color.rgb(190,208,233),true);gap(status,8);
        text(status,online?"Disponível para corridas":"Você está offline",24,WHITE,true);gap(status,5);
        text(status,online?"O UP pode lhe oferecer uma corrida direcionada.":"Fique online quando estiver pronto para trabalhar.",13,Color.rgb(213,224,241),false);
        button(status,online?"Ficar offline":"Ficar online",online?Color.rgb(60,84,119):GREEN,false,this::toggleAvailability);

        LinearLayout missionCard=card();
        if(mission==null) {
            title(missionCard,online?"Aguardando oferta":"Próxima corrida","Somente corridas do seu UP independente aparecem aqui.");
            text(missionCard,online?"Nenhuma oferta pendente no momento.":"Ative sua disponibilidade para receber ofertas.",14,MUTED,false);
        } else {
            String s=mission.optString("upState");
            title(missionCard,mission.optBoolean("ofertaAtiva")?"Nova oferta disponível":"Corrida em andamento",
                "Pedido #"+mission.optString("codigoPedido","—")+" • "+statusName(s));
            text(missionCard,mission.optString("clienteEnderecoCompleto",""),14,INK,false);
            button(missionCard,mission.optBoolean("ofertaAtiva")?"Ver oferta":"Acompanhar corrida",BLUE,false,()->{
                screen="mission";notice="";render();
            });
        }
        button(missionCard,"Atualizar corridas",Color.rgb(232,239,252),true,()->refreshOffer(true));

        LinearLayout guide=card();
        text(guide,"Fluxo da entrega",17,INK,true);gap(guide,8);
        text(guide,"Aceitar oferta → Chegar à loja → Retirar pedido → Ir ao cliente → Confirmar entrega",14,MUTED,false);
    }

    private void toggleAvailability() {
        if(inFlight)return;
        boolean newValue=!online;
        inFlight=true;render();
        backend.driver("presence",Backend.args("online",newValue),new Backend.Callback(){
            @Override public void onSuccess(JSONObject data){
                inFlight=false;online=newValue;
                if(online){registerPush();beginPolling();refreshOffer(false);}
                else {handler.removeCallbacks(poll);}
                inform(online?"Você está disponível para receber ofertas.":"Você está offline para novas ofertas.");
            }
            @Override public void onError(String message){inFlight=false;inform(message);}
        });
    }

    private void beginPolling() {
        handler.removeCallbacks(poll);
        if(approved && online) handler.postDelayed(poll,20000);
    }

    private void refreshOffer(boolean userAction) {
        if(!approved || loadingOffer)return;
        loadingOffer=true;
        backend.driver("offer",Backend.args(),new Backend.Callback(){
            @Override public void onSuccess(JSONObject data){
                loadingOffer=false;updateMission(data.optJSONObject("document"));
                if(userAction)notice="Corridas atualizadas.";
                render();
            }
            @Override public void onError(String message){
                loadingOffer=false;
                if(userAction){notice=message;render();}
            }
        });
    }

    private void updateMission(JSONObject doc) {
        if(doc==null) { mission=null; missionId="";stopLocation();return; }
        JSONObject data=doc.optJSONObject("data");
        if(data==null) {mission=null;missionId="";stopLocation();return;}
        mission=data;
        missionId=doc.optString("id","").replace("supabase:","");
        String state=data.optString("upState","");
        if(!data.optBoolean("ofertaAtiva") && activeState(state)) startLocationIfAllowed();
        else stopLocation();
    }

    private boolean activeState(String s) {
        return "ACCEPTED".equals(s) || "TO_STORE".equals(s) || "AT_STORE".equals(s)
            || "TO_CUSTOMER".equals(s) || "AT_CUSTOMER".equals(s);
    }

    private void mission() {
        if(mission==null){screen="home";render();return;}
        String state=mission.optString("upState","");
        boolean offered=mission.optBoolean("ofertaAtiva");
        LinearLayout top=card();
        top.setBackground(background(NAVY,20));
        text(top,"PEDIDO #"+mission.optString("codigoPedido","—"),13,AMBER,true);gap(top,5);
        text(top,offered?"Nova oferta":statusName(state),24,WHITE,true);gap(top,7);
        text(top,"Cada mudança é confirmada pelo servidor do UP.",12,Color.rgb(204,220,241),false);

        LinearLayout pickup=card();
        title(pickup,"Retirar na loja",mission.optString("coletaNome","Rodrigues Açaí e Cia"));
        text(pickup,mission.optString("coletaEndereco","Endereço de retirada não informado."),14,INK,false);
        button(pickup,"Abrir rota até a loja",Color.rgb(231,240,255),true,()->navigate(
            mission.optDouble("lojaLat",0),mission.optDouble("lojaLng",0),
            mission.optString("coletaEndereco","Rodrigues Açaí e Cia")));

        LinearLayout dropoff=card();
        title(dropoff,"Entrega ao cliente",mission.optString("clienteNome","Cliente"));
        text(dropoff,mission.optString("clienteEnderecoCompleto","Endereço de destino não informado."),14,INK,false);
        double collect=mission.optDouble("valorReceberCliente",0);
        gap(dropoff,8);
        text(dropoff,collect>0?"Cobrar na entrega: "+currency(collect):"Sem cobrança pendente informada.",14,collect>0?BLUE:MUTED,true);
        if(!offered) button(dropoff,"Abrir rota até o cliente",Color.rgb(231,240,255),true,()->navigate(
            mission.optDouble("clienteLat",0),mission.optDouble("clienteLng",0),
            mission.optString("clienteEnderecoCompleto","")));
        LinearLayout next=card();
        title(next,"Próxima ação","Execute uma etapa por vez. O UP impede saltos de status.");
        if(offered) {
            button(next,"Aceitar esta corrida",GREEN,false,()->action("accept",Backend.args()));
            button(next,"Recusar oferta",Color.rgb(237,241,247),true,()->action("reject",Backend.args("reason","Recusada pelo entregador")));
        } else if("ACCEPTED".equals(state)) {
            button(next,"Estou indo para a loja",BLUE,false,()->action("stage",Backend.args("status","A_CAMINHO_LOJA")));
        } else if("TO_STORE".equals(state)) {
            button(next,"Cheguei à loja",BLUE,false,()->action("stage",Backend.args("status","COLETANDO")));
        } else if("AT_STORE".equals(state)) {
            EditText pickupCode=input(next,"Código de retirada (se solicitado)",InputType.TYPE_CLASS_TEXT);
            button(next,"Confirmei a retirada",BLUE,false,()->action("pickup",Backend.args("code",pickupCode.getText().toString().trim())));
        } else if("TO_CUSTOMER".equals(state)) {
            button(next,"Cheguei ao endereço",BLUE,false,()->action("stage",Backend.args("status","NO_CLIENTE")));
        } else if("AT_CUSTOMER".equals(state)) {
            EditText deliveredCode=input(next,"Código de entrega, se solicitado",InputType.TYPE_CLASS_TEXT);
            EditText cash=input(next,collect>0?"Valor recebido do cliente (R$)":"Valor recebido (R$ 0,00)",
                InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
            if(collect<=0) cash.setText("0");
            button(next,"Concluir entrega",GREEN,false,()->{
                String raw=cash.getText().toString().trim().replace(",",".");
                double paid=0;
                try{paid=Double.parseDouble(raw);}catch(Exception ex){inform("Informe o valor recebido.");return;}
                if(!Double.isFinite(paid)||paid<collect){inform("O valor recebido não pode ser menor que "+currency(collect));return;}
                action("finish",Backend.args("code",deliveredCode.getText().toString().trim(),"received_amount",paid));
            });
        } else text(next,"Esta corrida está encerrada. Volte ao início.",14,MUTED,false);
        if(!offered && activeState(state)) {
            button(next,"Relatar ocorrência",Color.rgb(253,238,218),true,()->reportOccurrence());
        }
        button(next,"Atualizar status da corrida",Color.rgb(230,238,251),true,this::loadMission);
    }

    private void reportOccurrence() {
        if(missionId.isEmpty())return;
        LinearLayout c=card();
        title(c,"Informar ocorrência","O gestor receberá o registro, sem alterar automaticamente a corrida.");
        EditText note=input(c,"Descreva a ocorrência",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        button(c,"Enviar ocorrência",BLUE,false,()->{
            if(note.getText().toString().trim().length()<5){inform("Descreva o que ocorreu.");return;}
            action("occurrence",Backend.args("reason",note.getText().toString().trim()));
        });
    }

    private void action(String name,JSONObject payload) {
        if(inFlight||missionId.isEmpty())return;
        inFlight=true; render();
        try{payload.put("mission_id",missionId);}catch(Exception ignored){}
        backend.driver(name,payload,new Backend.Callback(){
            @Override public void onSuccess(JSONObject response) {
                inFlight=false;
                if("reject".equals(name)){mission=null;missionId="";stopLocation();screen="home";inform("Oferta recusada.");refreshOffer(false);}
                else if("occurrence".equals(name)) {inform("Ocorrência enviada ao gestor.");}
                else {notice="Etapa confirmada pelo UP.";loadMission();}
            }
            @Override public void onError(String message){inFlight=false;inform(message);}
        });
    }

    private void loadMission() {
        if(missionId.isEmpty()){refreshOffer(true);return;}
        backend.driver("mission",Backend.args("mission_id",missionId),new Backend.Callback(){
            @Override public void onSuccess(JSONObject data){
                updateMission(data.optJSONObject("document"));screen="mission";render();
                if(mission!=null && "DELIVERED".equals(mission.optString("upState"))){
                    stopLocation();handler.postDelayed(()->{screen="home";refreshOffer(true);},1600);
                }
            }
            @Override public void onError(String message){inform(message);}
        });
    }

    private void history() {
        LinearLayout c=card();
        title(c,"Minhas entregas","Histórico consultado diretamente no UP independente.");
        button(c,"Atualizar histórico",BLUE,false,this::loadHistory);
        if(history.length()==0) text(c,"Nenhuma corrida concluída ou atribuída aparece até agora.",14,MUTED,false);
        for(int i=0;i<history.length();i++) {
            JSONObject d=history.optJSONObject(i);
            if(d==null)continue;
            JSONObject data=d.optJSONObject("data");
            if(data==null)continue;
            LinearLayout item=card();
            text(item,"Pedido #"+data.optString("codigoPedido","—"),16,INK,true);gap(item,5);
            text(item,statusName(data.optString("upState","")),13,BLUE,true);
            text(item,data.optString("clienteEnderecoCompleto",""),13,MUTED,false);
        }
    }

    private void loadHistory() {
        backend.driver("history",Backend.args(),new Backend.Callback(){
            @Override public void onSuccess(JSONObject resp){history=resp.optJSONArray("documents");
                if(history==null) history=new JSONArray();screen="history";notice="";render();}
            @Override public void onError(String message){inform(message);}
        });
    }

    private void wallet() {
        LinearLayout main=card();
        title(main,"Meus ganhos","Informações financeiras consultadas diretamente no banco UP.");
        double credits=0,debits=0;
        for(int i=0;i<walletEntries.length();i++) {
            JSONObject e=walletEntries.optJSONObject(i);if(e==null)continue;
            double val=e.optDouble("amount",0);
            if("CREDIT".equals(e.optString("kind")))credits+=val;
            else if("DEBIT".equals(e.optString("kind")))debits+=val;
            else if("ADJUSTMENT".equals(e.optString("kind")))credits+=val;
        }
        text(main,"Créditos registrados: "+currency(credits),16,GREEN,true);
        text(main,"Débitos registrados: "+currency(debits),14,MUTED,false);
        gap(main,7);
        text(main,"Movimentação líquida: "+currency(credits-debits),19,INK,true);
        text(main,"Este valor é informativo, não representa promessa de saque ou repasse.",12,MUTED,false);
        button(main,"Atualizar financeiro",BLUE,false,this::loadWallet);

        LinearLayout ledger=card();
        text(ledger,"Últimos lançamentos",17,INK,true);gap(ledger,10);
        if(walletEntries.length()==0)text(ledger,"Ainda não há lançamentos registrados.",13,MUTED,false);
        for(int i=0;i<Math.min(walletEntries.length(),25);i++) {
            JSONObject entry=walletEntries.optJSONObject(i);if(entry==null)continue;
            String kind=entry.optString("kind","");
            String description=entry.optString("description","");
            if(description.isEmpty())description="Movimentação "+kind;
            text(ledger,description,14,INK,true);
            text(ledger,currency(entry.optDouble("amount",0))+" • "+kind,13,MUTED,false);
            gap(ledger,12);
        }

        LinearLayout transfers=card();
        text(transfers,"Repasses",17,INK,true);gap(transfers,10);
        if(payouts.length()==0)text(transfers,"Nenhum repasse solicitado.",13,MUTED,false);
        for(int i=0;i<Math.min(payouts.length(),25);i++) {
            JSONObject p=payouts.optJSONObject(i);if(p==null)continue;
            text(transfers,currency(p.optDouble("amount",0)),15,INK,true);
            text(transfers,p.optString("status","Em análise"),13,BLUE,false);
            gap(transfers,10);
        }
    }

    private void loadWallet() {
        backend.driver("wallet",Backend.args(),new Backend.Callback(){
            @Override public void onSuccess(JSONObject resp) {
                JSONArray a=resp.optJSONArray("transactions"), b=resp.optJSONArray("payouts");
                walletEntries=a==null?new JSONArray():a;payouts=b==null?new JSONArray():b;
                screen="wallet";notice="";render();
            }
            @Override public void onError(String message){inform(message);}
        });
    }

    private void profile() {
        LinearLayout c=card();
        title(c,"Minha conta","UP Entregador • Conta independente");
        String email=FirebaseAuth.getInstance().getCurrentUser()==null?"—":
            FirebaseAuth.getInstance().getCurrentUser().getEmail();
        text(c,email==null?"—":email,15,INK,true);gap(c,7);
        text(c,"Cadastro: aprovado pelo UP Gestor",13,GREEN,false);
        if (BuildConfig.UP_FIREBASE_ANDROID_REGISTERED)
            button(c,"Reativar notificações de ofertas",BLUE,false,this::requestNotifications);
        else text(c,"FCM ainda não homologado para este pacote Android. A versão de prévia não promete notificações push.",13,MUTED,false);
        text(c,"Para alterar dados de um cadastro aprovado, solicite revisão ao UP Gestor.",13,MUTED,false);
        button(c,"Sair com segurança",Color.rgb(245,231,231),true,this::signOut);
        LinearLayout i=card();
        text(i,"Privacidade do GPS",17,INK,true);gap(i,6);
        text(i,"O rastreamento só inicia durante uma corrida aceita, com permissão do Android e notificação persistente. Fora da missão, o serviço é encerrado.",14,MUTED,false);
    }

    private void signOut() {
        handler.removeCallbacks(poll);
        stopLocation();
        if(FirebaseAuth.getInstance().getCurrentUser()!=null)
            backend.driver("presence",Backend.args("online",false),new Backend.Callback(){
                @Override public void onSuccess(JSONObject d){}
                @Override public void onError(String e){}
            });
        FirebaseAuth.getInstance().signOut();
        approved=false;online=false;mission=null;missionId="";history=new JSONArray();
        screen="login";notice="";walletEntries=new JSONArray();payouts=new JSONArray();render();
    }

    private void bottomNavigation() {
        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(10),dp(8),dp(10),dp(7));
        bar.setBackgroundColor(WHITE);
        addTab(bar,"Início","home");
        addTab(bar,"Corrida","mission");
        addTab(bar,"Histórico","history");
        addTab(bar,"Ganhos","wallet");
        addTab(bar,"Conta","profile");
        root.addView(bar,new LinearLayout.LayoutParams(-1,-2));
    }
    private void addTab(LinearLayout parent,String label,String to) {
        MaterialButton b=new MaterialButton(this);
        b.setText(label);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setCornerRadius(dp(13));
        b.setMinHeight(dp(40));
        b.setPadding(0,0,0,0);
        b.setBackgroundTintList(ColorStateList.valueOf(screen.equals(to)?Color.rgb(230,239,254):WHITE));
        b.setTextColor(screen.equals(to)?BLUE:MUTED);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,dp(43),1);
        parent.addView(b,lp);
        b.setOnClickListener(v->{
            if("mission".equals(to) && mission==null){screen="home";render();return;}
            screen=to;notice="";render();
            if("history".equals(to))loadHistory();
            if("wallet".equals(to))loadWallet();
        });
    }

    private void navigate(double lat,double lng,String address) {
        try {
            String place=(Math.abs(lat)>0.00001 && Math.abs(lng)>0.00001)
                ?String.format(Locale.US,"%.7f,%.7f",lat,lng):address;
            if(place==null||place.trim().isEmpty()){inform("Endereço ainda não confirmado.");return;}
            Uri uri=Uri.parse("https://www.google.com/maps/dir/?api=1&destination="+Uri.encode(place));
            startActivity(new Intent(Intent.ACTION_VIEW,uri));
        } catch(Exception ex){inform("Não foi possível abrir o mapa neste aparelho.");}
    }

    private String currency(double n){return String.format(new Locale("pt","BR"),"R$ %.2f",n);}
    private String statusName(String status) {
        switch(status) {
            case "OFFER_PENDING":return "Oferta aguardando resposta";
            case "ACCEPTED":return "Aceita • Ir à loja";
            case "TO_STORE":return "A caminho da loja";
            case "AT_STORE":return "Na loja • Retirar pedido";
            case "TO_CUSTOMER":return "A caminho do cliente";
            case "AT_CUSTOMER":return "No cliente • Confirmar entrega";
            case "DELIVERED":return "Entrega concluída";
            case "CANCELLED":return "Corrida cancelada";
            case "FAILED":return "Corrida com ocorrência";
            default:return status;
        }
    }

    private void registerPush() {
        // A separate Android Firebase app registration is mandatory for reliable FCM.
        if (!BuildConfig.UP_FIREBASE_ANDROID_REGISTERED) return;
        if(Build.VERSION.SDK_INT>=33 &&
           ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestNotifications();return;
        }
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token->{
            if(token!=null&&!token.isEmpty() && approved){
                backend.driver("token",Backend.args("token",token),new Backend.Callback(){
                    @Override public void onSuccess(JSONObject response){}
                    @Override public void onError(String message){}
                });
            }
        });
    }
    private void requestNotifications() {
        if (!BuildConfig.UP_FIREBASE_ANDROID_REGISTERED) {
            inform("O Android deste novo UP ainda precisa ser registrado no Firebase.");return;
        }
        if(Build.VERSION.SDK_INT>=33 &&
          ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_PERMISSION);
        }else registerPush();
    }
    private void startLocationIfAllowed() {
        if(missionId.isEmpty()||mission==null||!activeState(mission.optString("upState")))return;
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION);return;
        }
        Intent intent=new Intent(this,LocationService.class);
        intent.putExtra("mission_id",missionId);
        try{ContextCompat.startForegroundService(this,intent);}
        catch(Exception ex){notice="GPS indisponível neste momento. Verifique a permissão.";render();}
    }
    private void stopLocation(){stopService(new Intent(this,LocationService.class));}
    @Override public void onRequestPermissionsResult(int req,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(req,permissions,grants);
        if(req==LOCATION_PERMISSION) {
            boolean allowed=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
            if(allowed)startLocationIfAllowed();
            else inform("Permissão negada. Sem GPS, o acompanhamento da entrega não funcionará.");
        }
        if(req==NOTIFICATION_PERMISSION && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED)registerPush();
    }
}
