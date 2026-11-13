#!/usr/bin/env bash
# Executa SOMENTE contra o PostgreSQL descartável up_test do GitHub Actions.
set -euo pipefail
: "${PGPASSWORD:?Senha do PostgreSQL descartável obrigatória}"
psql -X -v ON_ERROR_STOP=1 -d up_test -f supabase/up-merchant-onboarding-drafts.sql
psql -X -v ON_ERROR_STOP=1 -d up_test <<'SQL'
SET ROLE service_role;

SELECT merchant_id as draft_merchant_id, location_id as draft_location_id
FROM public.up_manager_create_merchant_draft(
 'firebase-owner-test','Loja Exemplo Teste UP','loja-exemplo-up',
 'Coleta','Rua Teste','123','Centro','Campo Grande','MS'
) \gset

DO $up$
BEGIN
 IF NOT EXISTS (
  SELECT 1 FROM public.up_merchants m
  JOIN public.up_merchant_locations l ON l.merchant_id=m.id
  WHERE m.slug='loja-exemplo-up' AND m.state='DRAFT'
   AND l.state='DRAFT' AND l.is_default
 ) THEN RAISE EXCEPTION 'Merchant onboarding did not preserve DRAFT state'; END IF;
 IF (SELECT count(*) FROM public.up_audit_log
    WHERE action='MERCHANT_DRAFT_CREATED'
      AND entity_type='merchant')<>1 THEN
   RAISE EXCEPTION 'Missing unique merchant creation audit event';
 END IF;
END $up$;

RESET ROLE;
DO $up$
BEGIN
 IF has_function_privilege('anon',
  'public.up_manager_create_merchant_draft(text,text,text,text,text,text,text,text,text)','EXECUTE')
 THEN RAISE EXCEPTION 'Anonymous caller can create merchants'; END IF;
 IF has_function_privilege('authenticated',
  'public.up_manager_create_merchant_draft(text,text,text,text,text,text,text,text,text)','EXECUTE')
 THEN RAISE EXCEPTION 'Authenticated customer can create merchants'; END IF;
 IF NOT has_function_privilege('service_role',
  'public.up_manager_create_merchant_draft(text,text,text,text,text,text,text,text,text)','EXECUTE')
 THEN RAISE EXCEPTION 'Server lacks merchant RPC privileges'; END IF;
END $up$;
SQL
echo 'PASS UP merchant draft: atomic shop+pickup, audit, RLS RPC permissions'
