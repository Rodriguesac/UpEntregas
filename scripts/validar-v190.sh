#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN="$ROOT/app/src/main/java/com/rodriguesacai/entregador/MainActivity.java"
UI="$ROOT/app/src/main/java/com/rodriguesacai/entregador/Ui.java"
BUILD="$ROOT/app/build.gradle"
WF="$ROOT/.github/workflows/build-apk.yml"

grep -q "versionCode 190" "$BUILD"
grep -q "1.9.0-foco-entrega" "$BUILD"
grep -q 'showRidesPage()' "$MAIN"
grep -q 'showEarningsPage()' "$MAIN"
grep -q 'showAccountPage()' "$MAIN"
grep -q 'showNotificationsPage()' "$MAIN"
grep -q 'showProfilePage()' "$MAIN"
grep -q 'showPixPage()' "$MAIN"
grep -q 'deliveryProgress(status, delivery)' "$MAIN"
grep -q 'Cheguei à loja' "$MAIN"
grep -q 'Confirmar retirada' "$MAIN"
grep -q 'Cheguei ao cliente' "$MAIN"
grep -q 'Confirmar entrega' "$MAIN"
grep -q 'accentGradientCard' "$UI"
grep -q 'menuRow' "$UI"
grep -q 'up-entregas-debug-keystore-estavel-v1' "$WF"

# Bottom-nav account no longer opens a system AlertDialog.
! grep -A12 'private void showAccountMenu' "$MAIN" | grep -q 'AlertDialog'

echo "OK: V1.9 foco na entrega passou nas validações estáticas."
