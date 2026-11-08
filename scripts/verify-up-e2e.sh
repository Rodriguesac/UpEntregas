#!/usr/bin/env bash
# Integração REAL de tabelas e funções num PostgreSQL 100% descartável.
# Não aceita execução em ambientes remotos ou produção.
set -euo pipefail
: "${PGPASSWORD:?Senha do banco descartável exigida}"
: "${PGHOST:?Informe localhost para a base descartável}"
if [[ "$PGHOST" != "localhost" && "$PGHOST" != "127.0.0.1" ]]; then
  echo "ERRO: recusa testar fora de localhost" >&2
  exit 1
fi
: "${PGPORT:=5432}"
export PGPORT
# A cadeia de verificação anterior cria as roles anon/authenticated/service_role.
psql -X -v ON_ERROR_STOP=1 -d postgres -c 'CREATE DATABASE up_e2e_test;'
psql -X -v ON_ERROR_STOP=1 -d up_e2e_test -f supabase/up-independent-schema.sql >/dev/null
psql -X -v ON_ERROR_STOP=1 -d up_e2e_test -f supabase/up-driver-operations.sql >/dev/null
psql -X -v ON_ERROR_STOP=1 -d up_e2e_test -f supabase/up-manager-independent.sql >/dev/null
psql -X -v ON_ERROR_STOP=1 -d up_e2e_test -f supabase/up-offer-notifications.sql >/dev/null
psql -X -v ON_ERROR_STOP=1 -d up_e2e_test -f scripts/verify-up-e2e.sql
test "$(psql -X -v ON_ERROR_STOP=1 -d up_e2e_test -Atqc 'select count(*) from public.up_jobs')" = "0"
test "$(psql -X -v ON_ERROR_STOP=1 -d up_e2e_test -Atqc 'select count(*) from public.up_couriers')" = "0"
test "$(psql -X -v ON_ERROR_STOP=1 -d up_e2e_test -Atqc 'select count(*) from public.up_tracking_points')" = "0"
echo "PASS UP E2E: pedido, oferta, aceite, percurso, GPS, status, callback ordenado e rollback"
