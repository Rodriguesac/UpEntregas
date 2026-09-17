from pathlib import Path

reg = Path('app/src/main/java/com/rodriguesacai/entregador/RegisterActivity.java')
s = reg.read_text()
old = '    private final FirebaseFirestore db = FirebaseFirestore.getInstance();\n    private final Map<String, String> data = new HashMap<>();'
new = '    private final FirebaseFirestore db = FirebaseFirestore.getInstance();\n    private final DriverRegistrationApi registrationApi = new DriverRegistrationApi();\n    private final Map<String, String> data = new HashMap<>();'
if old in s:
    s = s.replace(old, new, 1)
elif 'DriverRegistrationApi registrationApi' not in s:
    raise SystemExit('campo RegisterActivity não encontrado')

old = '''        db.collection("entregadores").document(user.getUid()).set(profile, SetOptions.merge())
                .addOnSuccessListener(v -> uploadAssets(user.getUid(), button))
                .addOnFailureListener(e -> {
                    button.setEnabled(true);
                    button.setText(editingExisting ? "Enviar atualização" : "Enviar cadastro");
                    Ui.message(this, "Não foi possível salvar o cadastro. Verifique a internet e tente novamente.");
                });'''
new = '''        db.collection("entregadores").document(user.getUid()).set(profile, SetOptions.merge())
                .addOnSuccessListener(v -> {
                    button.setText("Enviando para análise…");
                    registrationApi.submit(data)
                            .addOnSuccessListener(status -> uploadAssets(user.getUid(), button))
                            .addOnFailureListener(e -> {
                                button.setEnabled(true);
                                button.setText(editingExisting ? "Enviar atualização" : "Enviar cadastro");
                                Ui.message(this, "O cadastro foi salvo, mas não chegou ao Gestor. Toque em enviar novamente.");
                            });
                })
                .addOnFailureListener(e -> {
                    button.setEnabled(true);
                    button.setText(editingExisting ? "Enviar atualização" : "Enviar cadastro");
                    Ui.message(this, "Não foi possível salvar o cadastro. Verifique a internet e tente novamente.");
                });'''
if old in s:
    s = s.replace(old, new, 1)
elif 'registrationApi.submit(data)' not in s:
    raise SystemExit('saveProfile não encontrado')

old = '''                        db.collection("entregadores").document(uid).set(update, SetOptions.merge())
                                .addOnCompleteListener(t -> renderSuccess("Cadastro enviado para análise.", false));'''
new = '''                        db.collection("entregadores").document(uid).set(update, SetOptions.merge())
                                .addOnCompleteListener(t -> registrationApi.updateDocuments(photo, document)
                                        .addOnCompleteListener(sync -> renderSuccess("Cadastro enviado para análise.", false)));'''
if old in s:
    s = s.replace(old, new, 1)
elif 'registrationApi.updateDocuments(photo, document)' not in s:
    raise SystemExit('uploadAssets não encontrado')
reg.write_text(s)

login = Path('app/src/main/java/com/rodriguesacai/entregador/LoginActivity.java')
s = login.read_text()
old = '    private final DriverRepository repo = new DriverRepository();\n    private final FirebaseFirestore db = FirebaseFirestore.getInstance();'
new = '    private final DriverRepository repo = new DriverRepository();\n    private final DriverRegistrationApi registrationApi = new DriverRegistrationApi();\n    private final FirebaseFirestore db = FirebaseFirestore.getInstance();'
if old in s:
    s = s.replace(old, new, 1)
elif 'DriverRegistrationApi registrationApi' not in s:
    raise SystemExit('campo LoginActivity não encontrado')

if 'readProfileAfterManagerSync' not in s:
    start = s.index('    private void loadProfile(FirebaseUser user) {')
    end = s.index('    private void renderChecking() {', start)
    replacement = '''    private void loadProfile(FirebaseUser user) {
        if (user == null) {
            renderLogin("");
            return;
        }
        checkingProfile = true;
        renderChecking();
        registrationApi.syncStatus()
                .addOnCompleteListener(sync -> readProfileAfterManagerSync(user));
    }

    private void readProfileAfterManagerSync(FirebaseUser user) {
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

'''
    s = s[:start] + replacement + s[end:]
login.write_text(s)

print('Integração aplicada.')
