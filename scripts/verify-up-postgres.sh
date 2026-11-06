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
-- Supabase's service_role bypasses RLS; replicate that grant in this disposable database.
CREATE ROLE service_role NOLOGIN BYPASSRLS;
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
CREATE TABLE public.orders (
 id uuid primary key,order_code text not null,status text not null,
 fulfillment_type text not null,courier_id uuid,payment_method text,
 payment_status text,raw_payload jsonb not null default '{}'::jsonb,
 updated_at timestamptz not null default now()
);
INSERT INTO public.orders(id,order_code,status,fulfillment_type,payment_method,payment_status) VALUES
 ('00000000-0000-4000-8000-000000000201','711572','PRONTO','ENTREGA','DINHEIRO','PENDENTE'),
 ('00000000-0000-4000-8000-000000000301','800301','PRONTO','ENTREGA','DINHEIRO','PENDENTE'),
 ('00000000-0000-4000-8000-000000000401','800401','PRONTO','ENTREGA','INFINITEPAY','PENDENTE');
-- In Supabase the service_role has privileges on the existing public.orders.
-- Replicate that grant only inside the disposable integration-test database.
GRANT SELECT,UPDATE ON public.orders TO service_role;
SQL

psql -v ON_ERROR_STOP=1 -d store_test -f bridge/store-outbox.sql

psql -v ON_ERROR_STOP=1 -d store_test <<'SQL'
SET ROLE service_role;
-- Real server-side enqueue validates the order and freezes a unique event ID.
SELECT outbox_id,was_created FROM public.up_store_outbox_enqueue(
 '00000000-0000-4000-8000-000000000201',
 '00000000-0000-4000-8000-000000000202',
 '{"action":"create_job","event_id":"00000000-0000-4000-8000-000000000202",
 "order":{"id":"00000000-0000-4000-8000-000000000201","code":"711572",
 "fulfillment_type":"ENTREGA","status":"PRONTO","pickup":{"street":"Loja teste"},
 "dropoff":{"street":"Rua teste"},"collection_amount":10}}'::jsonb
);
-- Sending the exact same request returns the original queue row, never duplicates.
SELECT outbox_id,was_created FROM public.up_store_outbox_enqueue(
 '00000000-0000-4000-8000-000000000201',
 '00000000-0000-4000-8000-000000000202',
 '{"action":"create_job","event_id":"00000000-0000-4000-8000-000000000202",
 "order":{"id":"00000000-0000-4000-8000-000000000201","code":"711572",
 "fulfillment_type":"ENTREGA","status":"PRONTO","pickup":{"street":"Loja teste"},
 "dropoff":{"street":"Rua teste"},"collection_amount":10}}'::jsonb
);
SELECT outbox_id,lease_id FROM public.up_store_outbox_claim(5) \gset
SELECT public.up_store_outbox_finish(:'outbox_id'::uuid,:'lease_id'::uuid,
 'ack',null,'00000000-0000-4000-8000-000000000203'::uuid) AS acknowledged;

-- Pending online payment cannot be queued.
DO $$
DECLARE rejected boolean:=false;
BEGIN
 BEGIN
  perform public.up_store_outbox_enqueue(
   '00000000-0000-4000-8000-000000000401',
   '00000000-0000-4000-8000-000000000402',
   '{"action":"create_job","event_id":"00000000-0000-4000-8000-000000000402",
    "order":{"id":"00000000-0000-4000-8000-000000000401",
    "code":"800401","status":"PRONTO","fulfillment_type":"ENTREGA",
    "pickup":{"street":"Loja"},"dropoff":{"street":"Cliente"},"collection_amount":0}}'::jsonb
  );
 EXCEPTION WHEN OTHERS THEN
  IF sqlerrm='online_payment_not_confirmed' THEN rejected:=true; ELSE RAISE; END IF;
 END;
 IF NOT rejected THEN RAISE EXCEPTION 'Pending online payment was enqueued'; END IF;
END $$;

-- An order cancelled while queued is removed before any UP API request.
SELECT outbox_id FROM public.up_store_outbox_enqueue(
 '00000000-0000-4000-8000-000000000301',
 '00000000-0000-4000-8000-000000000302',
 '{"action":"create_job","event_id":"00000000-0000-4000-8000-000000000302",
 "order":{"id":"00000000-0000-4000-8000-000000000301","code":"800301",
 "fulfillment_type":"ENTREGA","status":"PRONTO","pickup":{"street":"Loja"},
 "dropoff":{"street":"Cliente"},"collection_amount":0}}'::jsonb
);
UPDATE public.orders SET status='CANCELADO' WHERE id='00000000-0000-4000-8000-000000000301';
SELECT count(*) AS unclaimed_stale FROM public.up_store_outbox_claim(5);
RESET ROLE;
DO $$
BEGIN
 IF (SELECT count(*) FROM up_bridge_private.handoff_outbox WHERE state='ACKED' AND attempts=1 AND up_job_id IS NOT NULL)<>1 THEN
  RAISE EXCEPTION 'Outbox message not acknowledged';
 END IF;
 IF (SELECT status FROM public.orders WHERE order_code='711572')<>'AGUARDANDO_ENTREGADOR' THEN
  RAISE EXCEPTION 'Store order advanced before/without UP acknowledgment';
 END IF;
 IF (SELECT raw_payload #>> '{up_bridge,mode}' FROM public.orders WHERE order_code='711572')<>'isolated' THEN
  RAISE EXCEPTION 'UP callback was not activated on store order';
 END IF;
 IF (SELECT count(*) FROM up_bridge_private.handoff_outbox WHERE state='CANCELLED' AND attempts=0)<>1 THEN
  RAISE EXCEPTION 'Cancelled order was sent despite the second readiness check';
 END IF;
 IF (SELECT count(*) FROM up_bridge_private.handoff_outbox)<>2 THEN
  RAISE EXCEPTION 'Duplicate queue row or unpaid checkout inserted';
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
