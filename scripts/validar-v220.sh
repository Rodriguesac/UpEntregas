#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
grep -q "versionCode 220" "$ROOT/app/build.gradle"
grep -q "2.2.0-sincronia-estados" "$ROOT/app/build.gradle"
grep -q "enum UpState" "$ROOT/app/src/main/java/com/rodriguesacai/entregador/UpState.java"
grep -q "upOpenRouteId" "$ROOT/app/src/main/java/com/rodriguesacai/entregador/DriverRepository.java"
grep -q "syncDriverMissionState" "$ROOT/app/src/main/java/com/rodriguesacai/entregador/DriverRepository.java"
grep -q "showSingleRouteComplementOffer" "$ROOT/app/src/main/java/com/rodriguesacai/entregador/MainActivity.java"
grep -q "upProtocolVersion" "$ROOT/firestore.rules"
! grep -q "offerTone" "$ROOT/app/src/main/java/com/rodriguesacai/entregador/MainActivity.java"
echo "UP Entregas V2.2: validação estática OK"
