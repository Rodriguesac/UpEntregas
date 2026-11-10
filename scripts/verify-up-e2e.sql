-- Executar APENAS no PostgreSQL descartável criado por verify-up-e2e.sh.
-- Todos os eventos devem desaparecer no ROLLBACK, inclusive pontos GPS.
BEGIN;
DO $e2e$
DECLARE
 v_order uuid := gen_random_uuid();
 v_event uuid := gen_random_uuid();
 v_job uuid;
 v_courier uuid;
 v_other uuid;
 v_offer uuid;
 v_second_job uuid;
 v_third_job uuid;
 v_second_offer uuid;
 v_third_offer uuid;
 v_status text;
 v_created boolean;
 v_repeated boolean;
 v_event_count int;
 v_saved boolean;
 v_reason text;
 v_callback record;
 v_ack boolean;
 v_denied boolean;
BEGIN
 INSERT INTO public.up_couriers(firebase_uid,display_name,state,online)
 VALUES('test-'||gen_random_uuid(),'Motorista Teste','APPROVED',true)
 RETURNING id INTO v_courier;
 INSERT INTO public.up_couriers(firebase_uid,display_name,state,online)
 VALUES('test-'||gen_random_uuid(),'Outro Motorista','APPROVED',true)
 RETURNING id INTO v_other;

 SELECT job_id,job_status,was_created INTO v_job,v_status,v_created
 FROM public.up_receive_handoff(v_order,'900001',
  '{"street":"Loja Teste","number":"1"}'::jsonb,
  '{"street":"Rua Teste","number":"2"}'::jsonb,
  'Cliente Ficticio','67000000000',0,v_event,repeat('a',64));
 IF v_job IS NULL OR v_status<>'WAITING_DRIVER' OR v_created IS DISTINCT FROM true
 THEN RAISE EXCEPTION 'e2e_handoff_failed';END IF;

 SELECT was_created INTO v_repeated
 FROM public.up_receive_handoff(v_order,'900001',
  '{"street":"Loja Teste","number":"1"}'::jsonb,
  '{"street":"Rua Teste","number":"2"}'::jsonb,
  'Cliente Ficticio','67000000000',0,v_event,repeat('a',64));
 IF v_repeated IS DISTINCT FROM false
 THEN RAISE EXCEPTION 'e2e_duplicate_order';END IF;

 SELECT offer_id INTO v_offer
 FROM public.up_manager_create_offer(v_job,v_courier,'firebase-test-owner',3);
 IF v_offer IS NULL THEN RAISE EXCEPTION 'e2e_offer_missing';END IF;

 -- A previously offered courier must not be able to accept while offline.
 UPDATE public.up_couriers SET online=false WHERE id=v_courier;
 v_denied:=false;
 BEGIN
  PERFORM public.up_driver_accept(v_job,v_courier,v_offer);
 EXCEPTION WHEN OTHERS THEN
  IF SQLERRM='courier_not_available' THEN v_denied:=true;ELSE RAISE;END IF;
 END;
 IF NOT v_denied THEN RAISE EXCEPTION 'e2e_offline_courier_accepted';END IF;
 UPDATE public.up_couriers SET online=true WHERE id=v_courier;

 v_denied:=false;
 BEGIN
  PERFORM public.up_driver_accept(v_job,v_other,v_offer);
 EXCEPTION WHEN OTHERS THEN
  IF SQLERRM='offer_not_found' THEN v_denied:=true;ELSE RAISE;END IF;
 END;
 IF NOT v_denied THEN RAISE EXCEPTION 'e2e_unauthorized_accept';END IF;

 SELECT job_status INTO v_status
 FROM public.up_driver_accept(v_job,v_courier,v_offer);
 IF v_status<>'ACCEPTED' THEN RAISE EXCEPTION 'e2e_accept_failed';END IF;
 SELECT already_applied INTO v_repeated
 FROM public.up_driver_accept(v_job,v_courier,v_offer);
 IF v_repeated IS DISTINCT FROM true
 THEN RAISE EXCEPTION 'e2e_accept_idempotency_failed';END IF;
 SELECT job_status INTO v_status
 FROM public.up_driver_advance(v_job,v_courier,'TO_STORE');
 IF v_status<>'TO_STORE' THEN RAISE EXCEPTION 'e2e_to_store_failed';END IF;

 SELECT saved,reason INTO v_saved,v_reason
 FROM public.up_driver_location(v_job,v_courier,-20.4567,-54.6245,12);
 IF v_saved IS DISTINCT FROM true OR v_reason<>'recorded'
 THEN RAISE EXCEPTION 'e2e_gps_failed';END IF;

 SELECT job_status INTO v_status
 FROM public.up_driver_advance(v_job,v_courier,'AT_STORE');
 IF v_status<>'AT_STORE' THEN RAISE EXCEPTION 'e2e_at_store_failed';END IF;
 SELECT job_status INTO v_status
 FROM public.up_driver_advance(v_job,v_courier,'TO_CUSTOMER');
 IF v_status<>'TO_CUSTOMER' THEN RAISE EXCEPTION 'e2e_to_customer_failed';END IF;
 SELECT job_status INTO v_status
 FROM public.up_driver_advance(v_job,v_courier,'AT_CUSTOMER');
 IF v_status<>'AT_CUSTOMER' THEN RAISE EXCEPTION 'e2e_at_customer_failed';END IF;
 SELECT job_status INTO v_status
 FROM public.up_driver_advance(v_job,v_courier,'DELIVERED',NULL,0);
 IF v_status<>'DELIVERED' THEN RAISE EXCEPTION 'e2e_delivered_failed';END IF;
 SELECT count(*) INTO v_event_count FROM public.up_job_events WHERE job_id=v_job;
 IF v_event_count<>8 THEN
  RAISE EXCEPTION 'e2e_sequence_count_got_%',v_event_count;END IF;
 SELECT * INTO v_callback FROM public.up_status_callback_claim(1);
 IF v_callback.job_id IS DISTINCT FROM v_job OR v_callback.sequence<>1
 THEN RAISE EXCEPTION 'e2e_callback_first_event_failed';END IF;
 SELECT public.up_status_callback_finish(v_callback.event_id,v_callback.lease_id,'ack')
 INTO v_ack;
 IF v_ack IS DISTINCT FROM true THEN RAISE EXCEPTION 'e2e_callback_ack_failed';END IF;
 SELECT * INTO v_callback FROM public.up_status_callback_claim(1);
 IF v_callback.job_id IS DISTINCT FROM v_job OR v_callback.sequence<>2
 THEN RAISE EXCEPTION 'e2e_callback_order_failed';END IF;
 IF has_table_privilege('anon','public.up_tracking_points','SELECT') OR
  has_table_privilege('authenticated','public.up_tracking_points','SELECT') OR
  has_function_privilege('authenticated','public.up_driver_location(uuid,uuid,numeric,numeric,numeric)','EXECUTE')
 THEN RAISE EXCEPTION 'e2e_gps_permissions_failed';END IF;
 -- Two valid offers can exist for different jobs, but only one may be accepted
 -- by the same courier while its previous ride remains active.
 SELECT job_id INTO v_second_job
 FROM public.up_receive_handoff(gen_random_uuid(),'900002',
   '{"street":"Store 2"}'::jsonb,'{"street":"Customer 2"}'::jsonb,
   'Customer 2','67000000000',0,gen_random_uuid(),repeat('b',64));
 SELECT job_id INTO v_third_job
 FROM public.up_receive_handoff(gen_random_uuid(),'900003',
   '{"street":"Store 3"}'::jsonb,'{"street":"Customer 3"}'::jsonb,
   'Customer 3','67000000000',0,gen_random_uuid(),repeat('c',64));
 SELECT offer_id INTO v_second_offer
 FROM public.up_manager_create_offer(v_second_job,v_courier,'firebase-test-owner',3);
 SELECT offer_id INTO v_third_offer
 FROM public.up_manager_create_offer(v_third_job,v_courier,'firebase-test-owner',3);
 SELECT job_status INTO v_status
 FROM public.up_driver_accept(v_second_job,v_courier,v_second_offer);
 IF v_status <> 'ACCEPTED' THEN RAISE EXCEPTION 'e2e_second_accept_failed';END IF;
 v_denied:=false;
 BEGIN
  PERFORM public.up_driver_accept(v_third_job,v_courier,v_third_offer);
 EXCEPTION WHEN OTHERS THEN
  IF SQLERRM='courier_busy' THEN v_denied:=true; ELSE RAISE; END IF;
 END;
 IF NOT v_denied THEN RAISE EXCEPTION 'e2e_courier_double_booking';END IF;
 IF EXISTS(SELECT 1 FROM public.up_jobs
           WHERE id=v_third_job AND assigned_courier_id IS NOT NULL) THEN
  RAISE EXCEPTION 'e2e_double_book_corrupted_assignment';
 END IF;
 RAISE NOTICE 'UP E2E internal assertions passed (offline, idempotency, double booking)';
END $e2e$;
ROLLBACK;
