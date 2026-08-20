#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "Validando UP Entregas 2.4.0..."

test -f app/build.gradle
test -f .github/workflows/build-apk.yml
test -f app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
test -f app/src/main/java/com/rodriguesacai/entregador/MissionMapView.java
grep -q "versionCode 240" app/build.gradle
grep -q "versionName '2.4.0-premium-route'" app/build.gradle
grep -q "showPremiumActiveRoute" app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
grep -qi "parada" app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
grep -q "IR PARA O CLIENTE" app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
grep -q "premiumCard" app/src/main/java/com/rodriguesacai/entregador/MissionMapView.java
! grep -R "new AlertDialog\|AlertDialog.Builder\|android.app.Dialog" -n app/src/main/java >/dev/null
! grep -R "Código de retirada da rota\|Codigo de retirada da rota" -n app/src/main/java >/dev/null

echo "Estrutura: OK"
echo "Versão: 240 / 2.4.0-premium-route"
echo "Rota Premium: OK"
echo "Sem AlertDialog de aplicação: OK"
echo "Sem código extra de retirada de rota: OK"
