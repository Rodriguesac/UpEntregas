#!/usr/bin/env bash
# Integration test on the disposable up_test database created by verify-up-postgres.sh
set -euo pipefail
: "${PGPASSWORD:?Disposable PostgreSQL password missing}"
psql -v ON_ERROR_STOP=1 -d up_test -f supabase/up-driver-operations.sql
psql -v ON_ERROR_STOP=1 -d up_test <<'SQL'
SET ROLE service_role;
INSERT INTO public.up_couriers(id,firebase_uid,display_name,state,online)
VALUES ('00000000-0000-4000-8000-000000000701','driver-approved','Entregador de teste','APPROVED',true),
       ('00000000-0000-4000-8000-000000000702','driver-other','Outro entregador de teste','APPROVED',true);
SELECT id AS driver_job FROM public.up_jobs
 WHERE merchant_order_id='00000000-0000-4000-8000-000000000101'::uuid \gset
-- A targeted offer must exist; another Firebase courier cannot accept it.
INSERT INTO public.up_dispatch_offers(id,job_id,courier_id,expires_at)
VALUES('00000000-0000-4000-8000-000000000703',:'driver_job'::uuid,
       '00000000-0000-4000-8000-000000000701',now()+interval '5 minutes');
DO $drivercheck$
DECLARE denied boolean:=false;
BEGIN
 BEGIN
  perform public.up_driver_accept(
   (select id from public.up_jobs limit 1),
   '00000000-0000-4000-8000-000000000702',
   '00000000-0000-4000-8000-000000000703');
 EXCEPTION WHEN OTHERS THEN
  IF sqlerrm='offer_not_found' THEN denied:=true;ELSE RAISE;END IF;
 END;
 IF NOT denied THEN RAISE EXCEPTION 'Unauthorized driver accepted offer';END IF;
END $drivercheck$;
SELECT job_status,already_applied FROM public.up_driver_accept(
 :'driver_job'::uuid,'00000000-0000-4000-8000-000000000701',
 '00000000-0000-4000-8000-000000000703');
SELECT job_status,already_applied FROM public.up_driver_accept(
 :'driver_job'::uuid,'00000000-0000-4000-8000-000000000701',
 '00000000-0000-4000-8000-000000000703');
DO $drivercheck$
DECLARE denied boolean:=false;
BEGIN
 BEGIN
  perform public.up_driver_advance(
   (select id from public.up_jobs limit 1),
   '00000000-0000-4000-8000-000000000701','DELIVERED');
 EXCEPTION WHEN OTHERS THEN
  IF sqlerrm='invalid_driver_transition' THEN denied:=true;ELSE RAISE;END IF;
 END;
 IF NOT denied THEN RAISE EXCEPTION 'Skipped mandatory driver milestones';END IF;
END $drivercheck$;
SELECT job_status FROM public.up_driver_advance(:'driver_job'::uuid,
 '00000000-0000-4000-8000-000000000701','TO_STORE');
SELECT saved,reason FROM public.up_driver_location(:'driver_job'::uuid,
 '00000000-0000-4000-8000-000000000701',-20.4695000,-54.6200000,15);
SELECT saved,reason FROM public.up_driver_location(:'driver_job'::uuid,
 '00000000-0000-4000-8000-000000000701',-20.4695000,-54.6200000,15);
SELECT job_status FROM public.up_driver_advance(:'driver_job'::uuid,
 '00000000-0000-4000-8000-000000000701','AT_STORE');
SELECT job_status FROM public.up_driver_advance(:'driver_job'::uuid,
 '00000000-0000-4000-8000-000000000701','TO_CUSTOMER');
SELECT job_status FROM public.up_driver_advance(:'driver_job'::uuid,
 '00000000-0000-4000-8000-000000000701','AT_CUSTOMER');
SELECT job_status FROM public.up_driver_advance(:'driver_job'::uuid,
 '00000000-0000-4000-8000-000000000701','DELIVERED',null,0);
DO $drivercheck$
BEGIN
 IF (SELECT count(*) FROM public.up_jobs WHERE status='DELIVERED'
   AND assigned_courier_id='00000000-0000-4000-8000-000000000701')<>1 THEN
  RAISE EXCEPTION 'Driver completion not recorded';END IF;
 IF (SELECT count(*) FROM public.up_tracking_points)<>1 THEN
  RAISE EXCEPTION 'Duplicate or missing location point';END IF;
 IF (SELECT count(*) FROM public.up_job_events)<>8 THEN
  RAISE EXCEPTION 'Driver milestone events missing';END IF;
 IF has_function_privilege('authenticated','public.up_driver_accept(uuid,uuid,uuid)','EXECUTE') THEN
  RAISE EXCEPTION 'Client can accept without Firebase JWT validation';END IF;
 IF has_function_privilege('anon','public.up_driver_location(uuid,uuid,numeric,numeric,numeric)','EXECUTE') THEN
  RAISE EXCEPTION 'Anonymous tracking writes enabled';END IF;
 IF has_table_privilege('authenticated','public.up_driver_devices','SELECT') THEN
  RAISE EXCEPTION 'Device tokens exposed';END IF;
END $drivercheck$;
RESET ROLE;
SQL
echo "PASS UP driver: assigned offer, replay protection, stages, isolated GPS and permissions"
