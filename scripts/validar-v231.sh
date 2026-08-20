#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN="$ROOT/app/src/main/java/com/rodriguesacai/entregador/MainActivity.java"
REPO="$ROOT/app/src/main/java/com/rodriguesacai/entregador/DriverRepository.java"
BUILD="$ROOT/app/build.gradle"
grep -q 'versionCode 231' "$BUILD"
grep -q 'Retirei todos os pedidos' "$MAIN"
! grep -q 'Código de retirada da rota' "$MAIN"
! grep -q 'codigoRetiradaRota' "$MAIN"
grep -q 'pickupRoute(driverId, rideId)' "$MAIN"
grep -q 'Task<Void> pickupRoute(String driverId, String routeId)' "$REPO"
printf 'UP Entregas 2.3.1: retirada múltipla corrigida — OK\n'
