#!/usr/bin/env bash
set -euo pipefail

echo '== UP Entregas v1.8.0 — validação estática =='
grep -q "versionCode 180" app/build.gradle
grep -q "versionName '1.8.0-visual-profissional'" app/build.gradle
grep -q 'Aguardando nova entrega' app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
grep -q 'Resumo de hoje' app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
grep -q 'showBottomNav' app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
grep -q 'Minha conta e veículo' app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
grep -q 'Confirmar retirada' app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
grep -q 'Confirmar entrega' app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
grep -q 'up_purple_soft' app/src/main/res/values/colors.xml
test -f .github/workflows/build-apk.yml

echo 'OK: estrutura, versão e marcadores visuais encontrados.'
