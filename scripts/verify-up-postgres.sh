#!/usr/bin/env bash
# Integration test in disposable PostgreSQL ONLY. Never points to production.
set -euo pipefail

: "${PGHOST:=localhost}"
: "${PGPORT:=5432}"
: "${PGUSER:=postgres}"
: "${PGPASSWORD:?PGPASSWORD required for disposable test database}"
export PGHOST PGPORT PGUSER PGPASSWORD

psql -v ON_ERROR_STOP=1 -d postgres <<'SQL'
CREATE ROLE anon NOLOGIN;
CREATE ROLE authenticated NOLOGIN;
CREATE ROLE service_role NOLOGIN;
CREATE DATABASE up_test;
CREATE DATABASE store_test;
SQL

psql -v ON_ERROR_STOP=1 -d up_test -f supabase/up-independent-schema.sql

psql -v ON_ERROR_STOP=1 -d up_test <<'SQL'
SET ROLE service_role;
SELECT job_id,job_status,was_created FROM public.up_receive_handoff(
 '00000000-0000-4000-8000-000000000101'::uuid,'711572',
 '{"street":"Loja teste"}'::jsonb,'{"street":"Cliente teste"}'::jsonb,
 'Cliente fictício','67000000000',0,
 '00000000-0000-4000-8000-000000000102'::uuid,
 repeat('a',64)
);
SELECT job_id,job_status,was_created FROM public.up_receive_handoff(
 '00000000-0000-4000-8000-000000000101'::uuid,'711572',
 '{"street":"Loja teste"}'::jsonb,'{"street":"Cliente teste"}'::jsonb,
 'Cliente fictício','67000000000',0,
 '00000000-0000-4000-8000-000000000102'::uuid,
 repeat('a',64)
);
RESET ROLE;
DO $$
BEGIN
 IF (SELECT count(*) FROM public.up_jobs)<>1 THEN
  RAISE EXCEPTION 'Duplicate job inserted';
 END IF;
 IF (SELECT count(*) FROM public.up_job_events)<>1 THEN
  RAISE EXCEPTION 'Duplicate event inserted';
 END IF;
 IF (SELECT sequence FROM public.up_job_events LIMIT 1)<>1 THEN
  RAISE EXCEPTION 'Initial event sequence incorrect';
 END IF;
 IF has_table_privilege('anon','public.up_jobs','SELECT') THEN
  RAISE EXCEPTION 'GPS/job tables exposed to anon';
 END IF;
 IF has_table_privilege('authenticated','public.up_tracking_points','SELECT') THEN
  RAISE EXCEPTION 'GPS exposed to authenticated';
 END IF;
 IF has_function_privilege('anon','public.up_receive_handoff(uuid,text,jsonb,jsonb,text,text,numeric,uuid,text)','EXECUTE') THEN
  RAISE EXCEPTION 'Handoff function executable by anonymous user';
 END IF;
END $$;
SQL

psql -v ON_ERROR_STOP=1 -d store_test <<'SQL'
CREATE TABLE public.orders (id uuid primary key);
INSERT INTO public.orders(id) VALUES ('00000000-0000-4000-8000-000000000201');
SQL

psql -v ON_ERROR_STOP=1 -d store_test -f bridge/store-outbox.sql

psql -v ON_ERROR_STOP=1 -d store_test <<'SQL'
SET ROLE service_role;
INSERT INTO up_bridge_private.handoff_outbox(order_id,event_id,payload)
VALUES (
 '00000000-0000-4000-8000-000000000201',
 '00000000-0000-4000-8000-000000000202',
 '{"action":"create_job","event_id":"00000000-0000-4000-8000-000000000202"}'::jsonb
);
SELECT outbox_id,lease_id FROM public.up_store_outbox_claim(5) \gset
SELECT public.up_store_outbox_finish(:'outbox_id'::uuid,:'lease_id'::uuid,
 'ack',null,'00000000-0000-4000-8000-000000000203'::uuid) AS acknowledged;
RESET ROLE;
DO $$
BEGIN
 IF (SELECT count(*) FROM up_bridge_private.handoff_outbox WHERE state='ACKED' AND attempts=1)<>1 THEN
  RAISE EXCEPTION 'Outbox message not acknowledged';
 END IF;
 IF has_table_privilege('anon','up_bridge_private.handoff_outbox','SELECT') THEN
  RAISE EXCEPTION 'Anonymous user can read private outbox';
 END IF;
 IF has_function_privilege('authenticated','public.up_store_outbox_claim(integer)','EXECUTE') THEN
  RAISE EXCEPTION 'Authenticated user can claim outbox';
 END IF;
END $$;
SQL
echo "PASS PostgreSQL: UP schema, service_role-only, idempotent handoff, store outbox, RLS"
