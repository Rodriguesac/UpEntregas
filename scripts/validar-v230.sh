#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "[1/7] Estrutura do projeto"
test -f app/build.gradle
test -f settings.gradle
test -f .github/workflows/build-apk.yml

echo "[2/7] Versão"
grep -q "versionCode 230" app/build.gradle
grep -q "versionName '2.3.0-material-native'" app/build.gradle

echo "[3/7] Sem Dialog/AlertDialog de aplicação"
if grep -RniE 'android\.app\.Dialog|AlertDialog|new[[:space:]]+Dialog\(' app/src/main/java --include='*.java'; then
  echo "ERRO: janela Dialog/AlertDialog encontrada."
  exit 1
fi

echo "[4/7] Sem Toast de aplicação"
if grep -RniE 'Toast\.makeText|import android\.widget\.Toast' app/src/main/java --include='*.java'; then
  echo "ERRO: Toast encontrado. Use mensagem interna do app."
  exit 1
fi

echo "[5/7] Painel interno e Material 3"
grep -q 'class InAppPanel' app/src/main/java/com/rodriguesacai/entregador/InAppPanel.java
grep -q 'Theme.Material3.DayNight.NoActionBar' app/src/main/res/values/themes.xml
grep -q 'MaterialButton' app/src/main/java/com/rodriguesacai/entregador/Ui.java
grep -q 'TextInputLayout' app/src/main/java/com/rodriguesacai/entregador/Ui.java

echo "[6/7] Protocolo operacional preservado"
grep -q 'rotas_entrega' app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
grep -q 'acceptRouteComplement' app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
grep -q 'finishRouteStop' app/src/main/java/com/rodriguesacai/entregador/MainActivity.java

echo "[7/7] XML básico"
python - <<'PY'
import xml.etree.ElementTree as ET
from pathlib import Path
for p in Path('app/src/main/res').rglob('*.xml'):
    ET.parse(p)
ET.parse('app/src/main/AndroidManifest.xml')
print('XML OK')
PY

echo "UP Entregas 2.3.0: validação estática OK"
