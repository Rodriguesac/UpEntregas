package com.rodriguesacai.entregador;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class LoginActivity extends AppCompatActivity {
    private final DriverRepository repo = new DriverRepository();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private boolean checkingProfile;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        applyBars();
        FirebaseUser u = auth.getCurrentUser();
        if (u != null) loadProfile(u); else renderLogin("");
    }

    @Override protected void onResume() {
        super.onResume();
        FirebaseUser u = auth.getCurrentUser();
        if (u != null && !checkingProfile) loadProfile(u);
    }

    private void applyBars() {
        getWindow().setStatusBarColor(Ui.color(this, R.color.up_bg));
        getWindow().setNavigationBarColor(Ui.color(this, R.color.up_bg));
        if (!ThemePrefs.isDark(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private void renderLogin(String initialMessage) {
        checkingProfile = false;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout root = Ui.column(this);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        root.addView(Ui.space(this, 26));
        root.addView(Ui.upMark(this, 74));
        root.addView(Ui.space(this, 12));
        root.addView(Ui.upWordmark(this, 160, 58));
        root.addView(Ui.space(this, 22));

        TextView welcome = Ui.centered(this, "Sua rota começa aqui", 30, true);
        root.addView(welcome);
        TextView description = Ui.centered(this,
                "Entre para ficar online, receber corridas e continuar exatamente de onde parou.", 14, false);
        description.setTextColor(Ui.color(this, R.color.up_text_muted));
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dp.setMargins(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 18));
        description.setLayoutParams(dp);
        root.addView(description);

        LinearLayout card = Ui.heroCard(this);
        card.addView(Ui.eyebrow(this, "Acesso do entregador"));
        card.addView(Ui.text(this, "Entrar no UP", 22, true));
        card.addView(Ui.muted(this, "Use a conta cadastrada e aprovada pela loja.", 12));
        card.addView(Ui.space(this, 8));

        TextInputLayout emailBox = Ui.formField(this, "E-mail", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        TextInputEditText email = Ui.fieldEdit(emailBox);
        if (email != null) {
            email.setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS);
            email.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        }
        card.addView(emailBox);

        TextInputLayout passwordBox = Ui.formField(this, "Senha", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordBox.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        TextInputEditText password = Ui.fieldEdit(passwordBox);
        if (password != null) {
            password.setAutofillHints(View.AUTOFILL_HINT_PASSWORD);
            password.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        }
        card.addView(passwordBox);

        TextView status = Ui.muted(this, initialMessage, 12);
        status.setVisibility(initialMessage == null || initialMessage.isBlank() ? View.GONE : View.VISIBLE);
        if (status.getVisibility() == View.VISIBLE) status.setTextColor(Ui.color(this, R.color.up_danger));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMargins(0, Ui.dp(this, 4), 0, Ui.dp(this, 6));
        status.setLayoutParams(sp);
        card.addView(status);

        Button enter = Ui.button(this, "Entrar");
        card.addView(enter);

        Button forgot = Ui.secondaryButton(this, "Esqueci minha senha");
        card.addView(forgot);

        root.addView(card);

        TextView newHere = Ui.centered(this, "Ainda não é entregador?", 13, false);
        newHere.setTextColor(Ui.color(this, R.color.up_text_muted));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.setMargins(0, Ui.dp(this, 12), 0, Ui.dp(this, 2));
        newHere.setLayoutParams(np);
        root.addView(newHere);

        Button create = Ui.outlineButton(this, "Criar conta de entregador");
        root.addView(create);

        LinearLayout secure = Ui.noticeCard(this, "Feito para trabalhar na rua",
                "Corridas direcionadas, localização durante a missão, rotas múltiplas e confirmação por código.",
                R.color.up_info);
        root.addView(secure);
        root.addView(Ui.space(this, 18));

        setContentView(scroll);

        enter.setOnClickListener(v -> login(emailBox, passwordBox, enter, status));
        if (password != null) {
            password.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    login(emailBox, passwordBox, enter, status);
                    return true;
                }
                return false;
            });
        }
        forgot.setOnClickListener(v -> resetPassword(emailBox, forgot, status));
        create.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void login(TextInputLayout emailBox, TextInputLayout passwordBox, Button enter, TextView status) {
        TextInputEditText email = Ui.fieldEdit(emailBox);
        TextInputEditText password = Ui.fieldEdit(passwordBox);
        String e = email == null ? "" : String.valueOf(email.getText()).trim();
        String p = password == null ? "" : String.valueOf(password.getText());

        emailBox.setError(null);
        passwordBox.setError(null);
        setStatus(status, "", false);

        if (!e.contains("@") || !e.contains(".")) {
            emailBox.setError("Informe um e-mail válido");
            if (email != null) email.requestFocus();
            return;
        }
        if (p.length() < 6) {
            passwordBox.setError("Informe sua senha");
            if (password != null) password.requestFocus();
            return;
        }

        enter.setEnabled(false);
        enter.setText("Entrando…");
        setStatus(status, "Validando sua conta e seu cadastro…", false);
        auth.signInWithEmailAndPassword(e, p)
                .addOnSuccessListener(r -> loadProfile(r.getUser()))
                .addOnFailureListener(x -> {
                    enter.setEnabled(true);
                    enter.setText("Entrar");
                    setStatus(status, "E-mail ou senha inválidos. Confira os dados e tente novamente.", true);
                });
    }

    private void resetPassword(TextInputLayout emailBox, Button button, TextView status) {
        TextInputEditText email = Ui.fieldEdit(emailBox);
        String e = email == null ? "" : String.valueOf(email.getText()).trim();
        emailBox.setError(null);
        if (!e.contains("@") || !e.contains(".")) {
            emailBox.setError("Digite seu e-mail primeiro");
            if (email != null) email.requestFocus();
            return;
        }
        button.setEnabled(false);
        button.setText("Enviando…");
        auth.sendPasswordResetEmail(e)
                .addOnSuccessListener(v -> {
                    button.setEnabled(true);
                    button.setText("Esqueci minha senha");
                    setStatus(status, "Enviamos as instruções de recuperação para o seu e-mail.", false);
                    status.setTextColor(Ui.color(this, R.color.up_success));
                })
                .addOnFailureListener(e1 -> {
                    button.setEnabled(true);
                    button.setText("Esqueci minha senha");
                    setStatus(status, "Não foi possível enviar a recuperação agora.", true);
                });
    }

    private void loadProfile(FirebaseUser user) {
        if (user == null) {
            renderLogin("");
            return;
        }
        checkingProfile = true;
        renderChecking();
        db.collection("entregadores").document(user.getUid()).get()
                .addOnSuccessListener(d -> {
                    checkingProfile = false;
                    if (!d.exists()) {
                        auth.signOut();
                        renderLogin("Sua conta existe, mas ainda não há um cadastro de entregador vinculado a ela.");
                        return;
                    }
                    if (isApproved(d)) {
                        Session.saveDriverId(this, user.getUid());
                        repo.recordLogin(user.getUid());
                        setResult(Activity.RESULT_OK);
                        finish();
                    } else {
                        renderPending(d);
                    }
                })
                .addOnFailureListener(e -> {
                    checkingProfile = false;
                    auth.signOut();
                    renderLogin("Não foi possível consultar seu cadastro. Verifique a internet e tente novamente.");
                });
    }

    private void renderChecking() {
        LinearLayout root = Ui.column(this);
        root.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        root.addView(Ui.upMark(this, 72));
        root.addView(Ui.space(this, 18));
        root.addView(Ui.text(this, "Preparando seu UP", 25, true));
        root.addView(Ui.space(this, 6));
        TextView t = Ui.centered(this, "Verificando cadastro e recuperando sua operação…", 13, false);
        t.setTextColor(Ui.color(this, R.color.up_text_muted));
        root.addView(t);
        setContentView(root);
    }

    private void renderPending(DocumentSnapshot d) {
        checkingProfile = false;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = Ui.column(this);
        scroll.addView(root);

        root.addView(Ui.space(this, 24));
        root.addView(Ui.upMark(this, 64));
        root.addView(Ui.space(this, 18));
        root.addView(Ui.eyebrow(this, "Situação do cadastro"));

        String st = first(d, "statusCadastro", "statusAprovacao", "situacao");
        boolean correction = up(st).contains("CORRECAO") || up(st).contains("CORREÇÃO");
        root.addView(Ui.text(this, correction ? "Precisamos de uma correção" : "Cadastro em análise", 30, true));
        root.addView(Ui.muted(this,
                correction
                        ? "A loja pediu uma atualização antes de liberar sua conta."
                        : "Seu cadastro foi recebido. Quando a loja aprovar, as corridas serão liberadas.", 14));
        root.addView(Ui.space(this, 12));

        LinearLayout card = Ui.heroCard(this);
        String name = first(d, "nome", "nomeCompleto");
        card.addView(Ui.text(this, name.isEmpty() ? "Entregador" : name, 20, true));
        card.addView(Ui.muted(this, auth.getCurrentUser() != null ? auth.getCurrentUser().getEmail() : "", 13));
        card.addView(Ui.space(this, 10));
        card.addView(Ui.pill(this, correction ? "CORREÇÃO SOLICITADA" : "AGUARDANDO APROVAÇÃO",
                correction ? R.color.up_warning : R.color.up_purple));

        String reason = first(d, "motivoCorrecao", "motivoReprovacao", "observacaoCadastro");
        if (!reason.isEmpty()) {
            card.addView(Ui.space(this, 12));
            card.addView(Ui.noticeCard(this, "Mensagem da loja", reason,
                    correction ? R.color.up_warning : R.color.up_info));
        }

        Button refresh = Ui.button(this, "Atualizar situação");
        refresh.setOnClickListener(v -> loadProfile(auth.getCurrentUser()));
        card.addView(refresh);

        if (correction) {
            Button edit = Ui.secondaryButton(this, "Corrigir cadastro");
            edit.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
            card.addView(edit);
        }

        Button out = Ui.outlineButton(this, "Sair desta conta");
        out.setOnClickListener(v -> {
            Session.clear(this);
            auth.signOut();
            renderLogin("");
        });
        card.addView(out);
        root.addView(card);

        setContentView(scroll);
    }

    private void setStatus(TextView status, String message, boolean error) {
        if (status == null) return;
        status.setText(message == null ? "" : message);
        status.setVisibility(message == null || message.isBlank() ? View.GONE : View.VISIBLE);
        status.setTextColor(Ui.color(this, error ? R.color.up_danger : R.color.up_text_muted));
    }

    private boolean isApproved(DocumentSnapshot d) {
        Boolean active = d.getBoolean("ativo");
        Boolean approved = d.getBoolean("aprovado");
        String status = up(first(d, "statusAprovacao", "statusCadastro", "situacao"));
        return Boolean.TRUE.equals(active) && Boolean.TRUE.equals(approved)
                && (status.isEmpty() || status.equals("APROVADO") || status.equals("ATIVO") || status.equals("LIBERADO"));
    }

    private static String first(DocumentSnapshot d, String... keys) {
        for (String k : keys) {
            Object v = d.get(k);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return "";
    }

    private static String up(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }
}
