package com.rodriguesacai.entregador;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Map<String, String> data = new HashMap<>();
    private final Map<String, TextInputLayout> fields = new HashMap<>();

    private int step = 1;
    private Uri photoUri;
    private Uri docUri;
    private LinearLayout root;
    private boolean editingExisting;
    private boolean loadingExisting;

    private final ActivityResultLauncher<String> photoPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) photoUri = uri;
                render();
            });

    private final ActivityResultLauncher<String> docPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) docUri = uri;
                render();
            });

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        applyBars();
        FirebaseUser current = auth.getCurrentUser();
        editingExisting = current != null;
        if (editingExisting) loadExisting(current); else render();
    }

    private void applyBars() {
        getWindow().setStatusBarColor(Ui.color(this, R.color.up_bg));
        getWindow().setNavigationBarColor(Ui.color(this, R.color.up_bg));
        if (!ThemePrefs.isDark(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private void loadExisting(FirebaseUser user) {
        loadingExisting = true;
        renderLoading();
        db.collection("entregadores").document(user.getUid()).get()
                .addOnSuccessListener(d -> {
                    loadingExisting = false;
                    if (d.exists()) importProfile(d);
                    if (user.getEmail() != null) data.put("email", user.getEmail());
                    render();
                })
                .addOnFailureListener(e -> {
                    loadingExisting = false;
                    if (user.getEmail() != null) data.put("email", user.getEmail());
                    render();
                    Ui.message(this, "Não foi possível carregar todos os dados anteriores.");
                });
    }

    private void importProfile(DocumentSnapshot d) {
        String[] keys = {
                "nome", "nomeCompleto", "email", "cpf", "nascimento", "telefone", "cep", "rua", "numero",
                "bairro", "cidade", "tipoVeiculo", "marcaVeiculo", "modeloVeiculo", "corVeiculo", "placa",
                "pixTipo", "pixChave", "pixTitular"
        };
        for (String key : keys) {
            Object value = d.get(key);
            if (value == null) continue;
            String target = key.equals("nomeCompleto") && !data.containsKey("nome") ? "nome" : key;
            if (!data.containsKey(target) || data.get(target).isEmpty()) data.put(target, String.valueOf(value));
        }
    }

    private void renderLoading() {
        LinearLayout box = Ui.column(this);
        box.setGravity(Gravity.CENTER);
        box.addView(Ui.upMark(this, 68));
        box.addView(Ui.space(this, 16));
        box.addView(Ui.text(this, "Preparando seu cadastro", 25, true));
        box.addView(Ui.space(this, 6));
        TextView info = Ui.centered(this, "Carregando seus dados para você não precisar preencher tudo de novo.", 13, false);
        info.setTextColor(Ui.color(this, R.color.up_text_muted));
        box.addView(info);
        setContentView(box);
    }

    private void render() {
        if (loadingExisting) return;
        fields.clear();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        root = Ui.column(this);
        scroll.addView(root);

        showHeader();
        showProgress();
        root.addView(Ui.space(this, 18));

        switch (step) {
            case 1: account(); break;
            case 2: personal(); break;
            case 3: vehicle(); break;
            case 4: documents(); break;
            case 5: finance(); break;
            default: review(); break;
        }
        root.addView(Ui.space(this, 24));
        setContentView(scroll);
    }

    private void showHeader() {
        LinearLayout top = Ui.row(this);
        if (step > 1) {
            TextView back = Ui.headerAction(this, "←");
            back.setContentDescription("Voltar uma etapa");
            back.setOnClickListener(v -> {
                step--;
                render();
            });
            top.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        } else {
            TextView close = Ui.headerAction(this, "×");
            close.setContentDescription("Fechar cadastro");
            close.setOnClickListener(v -> finish());
            top.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        }

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        titles.addView(Ui.text(this, editingExisting ? "Atualizar cadastro" : "Criar conta", 22, true));
        titles.addView(Ui.muted(this, "Etapa " + step + " de 6", 12));
        top.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(Ui.upMark(this, 48));
        root.addView(top);
    }

    private void showProgress() {
        LinearLayout bars = Ui.row(this);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, Ui.dp(this, 18), 0, 0);
        bars.setLayoutParams(bp);
        for (int i = 1; i <= 6; i++) bars.addView(Ui.stepBar(this, i <= step));
        root.addView(bars);
    }

    private void pageTitle(String eyebrow, String title, String description) {
        root.addView(Ui.eyebrow(this, eyebrow));
        root.addView(Ui.text(this, title, 29, true));
        TextView desc = Ui.muted(this, description, 14);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, Ui.dp(this, 7), 0, Ui.dp(this, 10));
        desc.setLayoutParams(p);
        root.addView(desc);
    }

    private LinearLayout card() {
        LinearLayout c = Ui.heroCard(this);
        root.addView(c);
        return c;
    }

    private TextInputEditText addField(LinearLayout card, String label, String key, int type) {
        TextInputLayout box = Ui.formField(this, label, type);
        TextInputEditText edit = Ui.fieldEdit(box);
        fields.put(key, box);
        if (edit != null) {
            edit.setText(data.getOrDefault(key, ""));
            edit.setSelection(edit.length());
            edit.setTag(key);
            edit.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    data.put(key, s == null ? "" : s.toString().trim());
                    box.setError(null);
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
        card.addView(box);
        return edit;
    }

    private void account() {
        pageTitle("Conta", editingExisting ? "Confirme sua conta" : "Como você vai entrar",
                editingExisting
                        ? "Mantemos seu acesso atual. Você pode corrigir os demais dados sem criar outra conta."
                        : "Use um e-mail que você acessa. Ele será sua forma de entrar no UP Entregas.");
        LinearLayout c = card();
        addField(c, "Nome completo", "nome", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        TextInputEditText email = addField(c, "E-mail", "email",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        if (editingExisting && email != null) email.setEnabled(false);

        if (!editingExisting) {
            TextInputEditText pass = addField(c, "Senha", "senha",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            TextInputLayout passBox = fields.get("senha");
            if (passBox != null) passBox.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
            if (pass != null) pass.setAutofillHints("newPassword");

            addField(c, "Confirmar senha", "senha2",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            TextInputLayout confirmBox = fields.get("senha2");
            if (confirmBox != null) confirmBox.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        }
        navigation(c);
    }

    private void personal() {
        pageTitle("Dados pessoais", "Quem está fazendo a entrega",
                "Esses dados identificam seu cadastro e ajudam a loja a falar com você quando necessário.");
        LinearLayout c = card();
        addField(c, "CPF", "cpf", InputType.TYPE_CLASS_NUMBER);
        addField(c, "Data de nascimento (dd/mm/aaaa)", "nascimento", InputType.TYPE_CLASS_DATETIME);
        addField(c, "Telefone / WhatsApp", "telefone", InputType.TYPE_CLASS_PHONE);
        c.addView(Ui.space(this, 4));
        c.addView(Ui.eyebrow(this, "Endereço"));
        addField(c, "CEP", "cep", InputType.TYPE_CLASS_NUMBER);
        addField(c, "Rua", "rua", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        addField(c, "Número", "numero", InputType.TYPE_CLASS_TEXT);
        addField(c, "Bairro", "bairro", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        addField(c, "Cidade", "cidade", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        navigation(c);
    }

    private void vehicle() {
        pageTitle("Veículo", "Como você faz as entregas",
                "Escolha o veículo primeiro. O UP usa essa informação na operação e no cadastro do entregador.");
        LinearLayout c = card();
        c.addView(Ui.muted(this, "Tipo de veículo", 12));
        LinearLayout choices = Ui.row(this);
        for (String type : new String[]{"Moto", "Carro", "Bicicleta"}) {
            Button b = Ui.choiceButton(this, type, type.equalsIgnoreCase(data.getOrDefault("tipoVeiculo", "")));
            b.setOnClickListener(v -> {
                data.put("tipoVeiculo", type);
                if ("Bicicleta".equals(type)) data.put("placa", "");
                render();
            });
            choices.addView(b);
        }
        c.addView(choices);
        c.addView(Ui.space(this, 8));
        addField(c, "Marca", "marcaVeiculo", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        addField(c, "Modelo", "modeloVeiculo", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        addField(c, "Cor", "corVeiculo", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        if (!"Bicicleta".equalsIgnoreCase(data.getOrDefault("tipoVeiculo", ""))) {
            addField(c, "Placa", "placa", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        } else {
            c.addView(Ui.noticeCard(this, "Bicicleta selecionada",
                    "Placa não se aplica. No aplicativo e no gestor ela aparecerá corretamente como bicicleta.",
                    R.color.up_info));
        }
        navigation(c);
    }

    private void documents() {
        pageTitle("Fotos e documentos", "Envie imagens legíveis",
                "O seletor de fotos é a única parte que abre a tela protegida do Android. Depois você volta direto para o UP.");
        LinearLayout c = card();

        LinearLayout photo = Ui.menuRow(this, "●", "Foto de perfil",
                photoUri == null ? "Ainda não selecionada" : "Imagem selecionada");
        photo.setOnClickListener(v -> photoPicker.launch("image/*"));
        c.addView(photo);

        LinearLayout document = Ui.menuRow(this, "▤", "CNH / documento",
                docUri == null ? "Ainda não selecionado" : "Imagem selecionada");
        document.setOnClickListener(v -> docPicker.launch("image/*"));
        c.addView(document);

        c.addView(Ui.noticeCard(this, "Entrega de bicicleta",
                "A loja pode analisar o cadastro sem CNH quando o veículo informado for bicicleta.", R.color.up_info));
        navigation(c);
    }

    private void finance() {
        pageTitle("Recebimento", "Onde você recebe seus repasses",
                "Informe a chave Pix do titular. Alterações futuras poderão passar por aprovação da loja.");
        LinearLayout c = card();

        c.addView(Ui.muted(this, "Tipo da chave Pix", 12));
        LinearLayout first = Ui.row(this);
        addChoice(first, "CPF", "pixTipo");
        addChoice(first, "Celular", "pixTipo");
        c.addView(first);
        LinearLayout second = Ui.row(this);
        addChoice(second, "E-mail", "pixTipo");
        addChoice(second, "Aleatória", "pixTipo");
        c.addView(second);
        c.addView(Ui.space(this, 8));

        addField(c, "Chave Pix", "pixChave", InputType.TYPE_CLASS_TEXT);
        addField(c, "Nome do titular", "pixTitular", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        navigation(c);
    }

    private void addChoice(LinearLayout row, String value, String key) {
        Button b = Ui.choiceButton(this, value, value.equalsIgnoreCase(data.getOrDefault(key, "")));
        b.setOnClickListener(v -> {
            data.put(key, value);
            render();
        });
        row.addView(b);
    }

    private void review() {
        pageTitle("Revisão", "Confira antes de enviar",
                "Depois do envio, a loja analisa o cadastro. Você verá a situação diretamente na tela de acesso.");
        LinearLayout c = card();

        c.addView(Ui.eyebrow(this, "Conta"));
        c.addView(Ui.text(this, data.getOrDefault("nome", "—"), 20, true));
        c.addView(Ui.muted(this, data.getOrDefault("email", "—"), 13));
        c.addView(Ui.divider(this));

        c.addView(Ui.eyebrow(this, "Identificação"));
        c.addView(Ui.dataLine(this, "CPF", maskCpf(data.getOrDefault("cpf", ""))));
        c.addView(Ui.dataLine(this, "Telefone", data.getOrDefault("telefone", "—")));
        c.addView(Ui.divider(this));

        c.addView(Ui.eyebrow(this, "Veículo"));
        String vehicle = data.getOrDefault("tipoVeiculo", "Não informado");
        String model = data.getOrDefault("modeloVeiculo", "");
        c.addView(Ui.text(this, model.isEmpty() ? vehicle : vehicle + " • " + model, 16, true));
        c.addView(Ui.muted(this, "Placa: " + ("Bicicleta".equalsIgnoreCase(vehicle) ? "Não se aplica" : data.getOrDefault("placa", "—")), 13));
        c.addView(Ui.divider(this));

        c.addView(Ui.eyebrow(this, "Recebimento"));
        c.addView(Ui.text(this, data.getOrDefault("pixTipo", "Pix"), 15, true));
        c.addView(Ui.muted(this, mask(data.getOrDefault("pixChave", "")), 13));
        c.addView(Ui.space(this, 8));
        c.addView(Ui.noticeCard(this, "Documentos",
                "Foto de perfil: " + (photoUri == null ? "não alterada/selecionada" : "selecionada")
                        + " • Documento: " + (docUri == null ? "não alterado/selecionado" : "selecionado"),
                R.color.up_info));

        navigation(c);
    }

    private void navigation(LinearLayout card) {
        LinearLayout row = Ui.row(this);
        if (step > 1) {
            Button back = Ui.secondaryButton(this, "Voltar");
            back.setOnClickListener(v -> {
                step--;
                render();
            });
            row.addView(back, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        Button next = Ui.button(this, step == 6 ? (editingExisting ? "Enviar atualização" : "Enviar cadastro") : "Continuar");
        next.setOnClickListener(v -> {
            if (!validateStep()) return;
            if (step < 6) {
                step++;
                render();
            } else {
                submit(next);
            }
        });
        row.addView(next, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row);
    }

    private boolean validateStep() {
        if (step == 1) {
            String name = value("nome");
            String email = value("email");
            if (name.length() < 3) return fieldError("nome", "Informe seu nome completo.");
            if (!email.contains("@") || !email.contains(".")) return fieldError("email", "Informe um e-mail válido.");
            if (!editingExisting) {
                String pass = value("senha");
                if (pass.length() < 6) return fieldError("senha", "Use uma senha com pelo menos 6 caracteres.");
                if (!pass.equals(value("senha2"))) return fieldError("senha2", "As senhas não conferem.");
            }
        }
        if (step == 2) {
            String cpf = value("cpf").replaceAll("\\D", "");
            if (cpf.length() != 11) return fieldError("cpf", "Informe um CPF com 11 números.");
            data.put("cpf", cpf);
            if (value("telefone").replaceAll("\\D", "").length() < 10)
                return fieldError("telefone", "Informe seu telefone com DDD.");
        }
        if (step == 3 && value("tipoVeiculo").isEmpty()) {
            Ui.message(this, "Escolha Moto, Carro ou Bicicleta.");
            return false;
        }
        if (step == 5) {
            if (value("pixTipo").isEmpty()) {
                Ui.message(this, "Escolha o tipo da chave Pix.");
                return false;
            }
            if (value("pixChave").isEmpty()) return fieldError("pixChave", "Informe sua chave Pix.");
            if (value("pixTitular").length() < 3) return fieldError("pixTitular", "Informe o nome do titular.");
        }
        return true;
    }

    private boolean fieldError(String key, String message) {
        TextInputLayout box = fields.get(key);
        if (box != null) {
            box.setError(message);
            if (box.getEditText() != null) box.getEditText().requestFocus();
        } else {
            Ui.message(this, message);
        }
        return false;
    }

    private String value(String key) {
        String v = data.get(key);
        return v == null ? "" : v.trim();
    }

    private void submit(Button button) {
        button.setEnabled(false);
        button.setText(editingExisting ? "Enviando atualização…" : "Criando conta…");

        FirebaseUser current = auth.getCurrentUser();
        if (current != null) {
            continueExistingAccount(current, button);
            return;
        }

        String email = value("email");
        String password = value("senha");
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> saveProfile(r.getUser(), button))
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        button.setText("Verificando conta existente…");
                        auth.signInWithEmailAndPassword(email, password)
                                .addOnSuccessListener(r -> continueExistingAccount(r.getUser(), button))
                                .addOnFailureListener(loginError -> {
                                    button.setEnabled(true);
                                    button.setText("Enviar cadastro");
                                    Ui.message(this, "Este e-mail já possui conta. Use a mesma senha dessa conta ou volte para entrar.");
                                });
                    } else {
                        button.setEnabled(true);
                        button.setText("Enviar cadastro");
                        Ui.message(this, friendlyAuthError(e));
                    }
                });
    }

    private void continueExistingAccount(FirebaseUser user, Button button) {
        if (user == null) {
            button.setEnabled(true);
            button.setText(editingExisting ? "Enviar atualização" : "Enviar cadastro");
            Ui.message(this, "Não foi possível acessar a conta existente.");
            return;
        }
        button.setText("Verificando cadastro…");
        db.collection("entregadores").document(user.getUid()).get()
                .addOnSuccessListener(d -> {
                    if (d.exists() && Boolean.TRUE.equals(d.getBoolean("aprovado"))
                            && Boolean.TRUE.equals(d.getBoolean("ativo")) && !editingExisting) {
                        renderAlreadyApproved();
                        return;
                    }
                    saveProfile(user, button);
                })
                .addOnFailureListener(e -> {
                    button.setEnabled(true);
                    button.setText(editingExisting ? "Enviar atualização" : "Enviar cadastro");
                    Ui.message(this, "Não foi possível verificar o cadastro existente.");
                });
    }

    private void saveProfile(FirebaseUser user, Button button) {
        if (user == null) {
            button.setEnabled(true);
            Ui.message(this, "Conta não criada.");
            return;
        }

        Map<String, Object> profile = new HashMap<>();
        profile.putAll(data);
        profile.remove("senha");
        profile.remove("senha2");
        profile.put("authUid", user.getUid());
        profile.put("email", user.getEmail());
        profile.put("statusCadastro", editingExisting ? "CORRECAO_REENVIADA" : "AGUARDANDO_APROVACAO");
        profile.put("statusAprovacao", "PENDENTE");
        profile.put("aprovado", false);
        profile.put("ativo", false);
        profile.put("online", false);
        profile.put("aceitaNovasOfertas", false);
        profile.put("origemCadastro", "UP_ENTREGAS_ANDROID");
        profile.put("appVersionCadastro", BuildConfig.VERSION_NAME);
        profile.put("cadastroEnviadoEm", FieldValue.serverTimestamp());
        profile.put("updatedAt", FieldValue.serverTimestamp());

        button.setText("Salvando cadastro…");
        db.collection("entregadores").document(user.getUid()).set(profile, SetOptions.merge())
                .addOnSuccessListener(v -> uploadAssets(user.getUid(), button))
                .addOnFailureListener(e -> {
                    button.setEnabled(true);
                    button.setText(editingExisting ? "Enviar atualização" : "Enviar cadastro");
                    Ui.message(this, "Não foi possível salvar o cadastro. Verifique a internet e tente novamente.");
                });
    }

    private void uploadAssets(String uid, Button button) {
        button.setText("Enviando imagens…");
        CloudinaryUploader.upload(this, photoUri, "entregadores/perfil", new CloudinaryUploader.Callback() {
            @Override public void onSuccess(String photo) {
                CloudinaryUploader.upload(RegisterActivity.this, docUri, "entregadores/documentos", new CloudinaryUploader.Callback() {
                    @Override public void onSuccess(String document) {
                        Map<String, Object> update = new HashMap<>();
                        if (!photo.isEmpty()) update.put("fotoUrl", photo);
                        if (!document.isEmpty()) update.put("documentoUrl", document);
                        update.put("updatedAt", FieldValue.serverTimestamp());
                        db.collection("entregadores").document(uid).set(update, SetOptions.merge())
                                .addOnCompleteListener(t -> renderSuccess("Cadastro enviado para análise.", false));
                    }

                    @Override public void onError(Exception e) {
                        renderSuccess("Cadastro enviado. Uma das imagens ficou pendente e poderá ser atualizada depois.", true);
                    }
                });
            }

            @Override public void onError(Exception e) {
                renderSuccess("Cadastro enviado. Uma das imagens ficou pendente e poderá ser atualizada depois.", true);
            }
        });
    }

    private void renderSuccess(String message, boolean warning) {
        LinearLayout root = Ui.column(this);
        root.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        TextView check = Ui.centered(this, "✓", 40, true);
        check.setTextColor(Ui.color(this, warning ? R.color.up_warning : R.color.up_success));
        check.setBackground(Ui.rounded(this, warning ? R.color.up_surface_alt : R.color.up_purple_soft,
                999, warning ? R.color.up_warning : R.color.up_purple_soft, 1));
        check.setGravity(Gravity.CENTER);
        root.addView(check, new LinearLayout.LayoutParams(Ui.dp(this, 86), Ui.dp(this, 86)));
        root.addView(Ui.space(this, 20));
        root.addView(Ui.text(this, editingExisting ? "Atualização enviada" : "Cadastro enviado", 29, true));
        root.addView(Ui.space(this, 7));
        TextView body = Ui.centered(this, message, 14, false);
        body.setTextColor(Ui.color(this, R.color.up_text_muted));
        root.addView(body);
        root.addView(Ui.space(this, 18));
        root.addView(Ui.noticeCard(this, "Próximo passo",
                "A loja analisa seu cadastro. A situação aparecerá automaticamente na tela de acesso.", R.color.up_info));
        Button done = Ui.button(this, "Voltar para o acesso");
        done.setOnClickListener(v -> finish());
        root.addView(done);
        setContentView(root);
    }

    private void renderAlreadyApproved() {
        LinearLayout root = Ui.column(this);
        root.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        root.addView(Ui.upMark(this, 70));
        root.addView(Ui.space(this, 16));
        root.addView(Ui.text(this, "Sua conta já está aprovada", 27, true));
        root.addView(Ui.space(this, 6));
        TextView body = Ui.centered(this, "Você não precisa criar outro cadastro. Volte e entre normalmente.", 14, false);
        body.setTextColor(Ui.color(this, R.color.up_text_muted));
        root.addView(body);
        root.addView(Ui.space(this, 18));
        Button done = Ui.button(this, "Voltar para entrar");
        done.setOnClickListener(v -> finish());
        root.addView(done);
        setContentView(root);
    }

    private String friendlyAuthError(Exception e) {
        String m = e == null ? "Erro desconhecido." : String.valueOf(e.getMessage());
        String lower = m.toLowerCase(Locale.ROOT);
        if (lower.contains("network")) return "Verifique sua internet e tente novamente.";
        if (lower.contains("password")) return "Confira a senha informada.";
        if (lower.contains("email")) return "Confira o e-mail informado.";
        return "Não foi possível criar a conta agora. Tente novamente.";
    }

    private static String mask(String value) {
        if (value == null || value.isEmpty()) return "—";
        if (value.length() <= 4) return "••••";
        return value.substring(0, 2) + "••••" + value.substring(value.length() - 2);
    }

    private static String maskCpf(String raw) {
        String cpf = raw == null ? "" : raw.replaceAll("\\D", "");
        if (cpf.length() != 11) return "—";
        return "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**";
    }
}
