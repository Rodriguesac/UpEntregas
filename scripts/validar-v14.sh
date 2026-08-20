#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== UP Entregas V1.4: validação estática =="
grep -q "versionCode 140" app/build.gradle
grep -q "1.4.0-pro-sync-cliente-sem-blaze" app/build.gradle
grep -q "FOREGROUND_SERVICE_SPECIAL_USE" app/src/main/AndroidManifest.xml
grep -q "FOREGROUND_SERVICE_LOCATION" app/src/main/AndroidManifest.xml
grep -q "POST_NOTIFICATIONS" app/src/main/AndroidManifest.xml
grep -q 'android:name=".OnlineService"' app/src/main/AndroidManifest.xml
grep -q 'android:name=".TrackingService"' app/src/main/AndroidManifest.xml
test -f app/src/main/java/com/rodriguesacai/entregador/PermissionCenterActivity.java
test -f app/src/main/java/com/rodriguesacai/entregador/NotificationHelper.java
test -f app/src/main/res/values-night/colors.xml
! grep -R "org.mindrot.jbcrypt" -n app/src/main/java app/build.gradle
! grep -R "PasswordVerifier" -n app/src/main/java

echo "OK: versão, permissões, serviços, temas e login sem jBCrypt conferidos."
echo "A compilação Android definitiva é feita pelo GitHub Actions."
