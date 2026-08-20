#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN="$ROOT/app/src/main/java/com/rodriguesacai/entregador/MainActivity.java"
REPO="$ROOT/app/src/main/java/com/rodriguesacai/entregador/DriverRepository.java"
BUILD="$ROOT/app/build.gradle"

grep -q "versionCode 170" "$BUILD"
grep -q "1.7.0-operacao-codigos" "$BUILD"
grep -q "aguardando novas corridas" "$MAIN"
grep -q "CPF: .*maskCpf" "$MAIN" || grep -q "maskCpf(cpf)" "$MAIN"
grep -q "CADASTRAR PIX" "$MAIN"
grep -q "VER PENDÊNCIAS" "$MAIN"
grep -q "WHATSAPP DO CLIENTE" "$MAIN"
grep -q "requestPixChange" "$REPO"
grep -q 'm.put("aguardandoCodigoEntrega", true)' "$REPO"
grep -q "Código de retirada incorreto" "$REPO"
grep -q "Código de entrega incorreto" "$REPO"

echo "OK: validações estáticas v1.7.0 concluídas."
