#!/usr/bin/env bash
# Never touches production. Executed only on disposable up_test created by
# verify-up-postgres.sh and populated by verify-up-driver-postgres.sh.
set -euo pipefail
: "${PGPASSWORD:?Disposable PostgreSQL password required}"
psql -v ON_ERROR_STOP=1 -d up_test -f supabase/up-manager-independent.sql
psql -v ON_ERROR_STOP=1 -d up_test <<'SQL'
SET ROLE service_role;

-- No default approved manager: the test explicitly seeds one verified UID.
INSERT INTO public.up_manager_users(firebase_uid,display_name,role,active)
VALUES ('firebase-owner-test','Gestor UP Teste','OWNER',true);
INSERT INTO public.up_courier_applications
 (id,firebase_uid,full_name,phone,email,state,documents,vehicle)
VALUES
 ('00000000-0000-4000-8000-000000000901','new-driver-test',
  'Entregador cadastrado','67999999999','teste@example.invalid','PENDING',
  '{"photo_url":"https://example.invalid/photo.jpg"}','{"type":"moto"}');

SELECT application_id,review_state,courier_id FROM public.up_manager_review_application(
 '00000000-0000-4000-8000-000000000901','APPROVED','firebase-owner-test',null);
SELECT application_id,review_state,courier_id FROM public.up_manager_review_application(
 '00000000-0000-4000-8000-000000000901','APPROVED','firebase-owner-test',null);
SELECT id AS new_courier_id FROM public.up_couriers WHERE firebase_uid='new-driver-test' \gset

DO $mgr$
BEGIN
 IF (SELECT count(*) FROM public.up_couriers WHERE firebase_uid='new-driver-test'
   AND state='APPROVED')<>1 THEN RAISE EXCEPTION 'Approval did not activate one courier';END IF;
 IF (SELECT count(*) FROM public.up_audit_log WHERE action='application_review')<>1 THEN
  RAISE EXCEPTION 'Duplicate approval audit detected';END IF;
END $mgr$;

UPDATE public.up_couriers SET online=true WHERE id=:'new_courier_id'::uuid;
SELECT job_id AS managed_job FROM public.up_receive_handoff(
 '00000000-0000-4000-8000-000000000902'::uuid,'890902',
 '{"street":"Loja teste"}'::jsonb,'{"street":"Cliente teste"}'::jsonb,
 'Cliente fictício','67000000000',0,
 '00000000-0000-4000-8000-000000000903'::uuid,repeat('b',64)) \gset

SELECT offer_id,expires_at,was_created FROM public.up_manager_create_offer(
 :'managed_job'::uuid,:'new_courier_id'::uuid,'firebase-owner-test',3);
SELECT offer_id,expires_at,was_created FROM public.up_manager_create_offer(
 :'managed_job'::uuid,:'new_courier_id'::uuid,'firebase-owner-test',3);

DO $mgr$
DECLARE denied boolean:=false;
BEGIN
 BEGIN
  perform public.up_manager_create_offer(
    (select id from public.up_jobs where merchant_order_code='890902'),
    '00000000-0000-4000-8000-000000000701',
    'firebase-owner-test',3);
 EXCEPTION WHEN OTHERS THEN
  IF sqlerrm='job_already_offered' THEN denied:=true;ELSE RAISE;END IF;
 END;
 IF NOT denied THEN RAISE EXCEPTION 'Overlapping offer created for another driver';END IF;
 IF (SELECT count(*) FROM public.up_dispatch_offers
   WHERE job_id=(select id from public.up_jobs where merchant_order_code='890902'))<>1 THEN
  RAISE EXCEPTION 'Duplicate offer created on retry';END IF;
 IF (SELECT count(*) FROM public.up_job_events
   WHERE job_id=(select id from public.up_jobs where merchant_order_code='890902'))<>2 THEN
  RAISE EXCEPTION 'Offer transition events not recorded';END IF;
END $mgr$;

INSERT INTO public.up_wallet_transactions(courier_id,kind,amount,description)
VALUES(:'new_courier_id'::uuid,'CREDIT',50,'Teste crédito em carteira');

SELECT public.up_manager_request_payout(
 :'new_courier_id'::uuid,30,'firebase-owner-test') AS payout_id \gset
DO $mgr$
DECLARE denied boolean:=false;
BEGIN
 BEGIN
  perform public.up_manager_request_payout(
    (select id from public.up_couriers where firebase_uid='new-driver-test'),
    25,'firebase-owner-test');
 EXCEPTION WHEN OTHERS THEN
  IF sqlerrm='insufficient_wallet_balance' THEN denied:=true;ELSE RAISE;END IF;
 END;
 IF NOT denied THEN RAISE EXCEPTION 'Payout bypassed reserved wallet balance';END IF;
END $mgr$;
SELECT public.up_manager_confirm_payout(
 :'payout_id'::uuid,'firebase-owner-test','MANUAL-RECEIPT-EXAMPLE') AS paid;
SELECT public.up_manager_confirm_payout(
 :'payout_id'::uuid,'firebase-owner-test','MANUAL-RECEIPT-EXAMPLE') AS paid_replay;
DO $mgr$
BEGIN
 IF (SELECT status FROM public.up_payouts WHERE id=:'payout_id'::uuid)<>'PAID' THEN
  RAISE EXCEPTION 'Manual payout confirmation not recorded';END IF;
 IF (SELECT count(*) FROM public.up_audit_log WHERE action='payout_recorded_paid')<>1 THEN
  RAISE EXCEPTION 'Duplicate paid audit';END IF;
END $mgr$;
DO $mgr$
DECLARE denied boolean:=false;
BEGIN
 BEGIN
  perform public.up_manager_set_driver_state(
   '00000000-0000-4000-8000-000000000701','SUSPENDED','firebase-owner-test');
 EXCEPTION WHEN OTHERS THEN
  IF sqlerrm='driver_has_active_job' THEN denied:=true;ELSE RAISE;END IF;
 END;
 IF NOT denied THEN RAISE EXCEPTION 'Driver with an active ride could be suspended';END IF;
END $mgr$;
SELECT public.up_manager_set_driver_state(:'new_courier_id'::uuid,
 'SUSPENDED','firebase-owner-test') AS suspended;
RESET ROLE;

DO $mgr$
BEGIN
 IF has_table_privilege('anon','public.up_manager_users','SELECT') THEN
  RAISE EXCEPTION 'Manager UID allowlist leaked to anon';END IF;
 IF has_table_privilege('authenticated','public.up_manager_users','UPDATE') THEN
  RAISE EXCEPTION 'Manager roles editable by authenticated';END IF;
 IF has_function_privilege('anon',
  'public.up_manager_create_offer(uuid,uuid,text,integer)','EXECUTE') THEN
  RAISE EXCEPTION 'Anonymous visitor can dispatch offers';END IF;
 IF has_function_privilege('authenticated',
  'public.up_manager_confirm_payout(uuid,text,text)','EXECUTE') THEN
  RAISE EXCEPTION 'Authenticated user can mark payout paid';END IF;
END $mgr$;
SQL
echo "PASS UP Gestor SQL: allowlist, approval idempotency, exclusive offers, payouts and audit"
