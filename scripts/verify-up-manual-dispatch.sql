-- Rodar somente no PostgreSQL descartável de verify-up-e2e.sh.
-- A transação é revertida ao fim; nunca cria corridas reais.
BEGIN;
DO $manual$
declare
 m uuid;
 v_pickup uuid;
 req uuid:=gen_random_uuid();
 j uuid;
 j2 uuid;
 st text;
 created boolean;
 retry boolean;
 deny boolean;
 n integer;
begin
 select id into m from public.up_merchants where slug='rodrigues-acai-e-cia';
 if m is null then raise exception 'manual_test_merchant_missing';end if;
 update public.up_merchants set state='ACTIVE' where id=m;
 insert into public.up_merchant_locations(
  merchant_id,label,street,number,city,state_code,latitude,longitude,state,is_default)
 values(m,'Ponto Teste','Rua da Loja','100','Campo Grande','MS',-20.4,-54.6,'ACTIVE',true)
 returning id into v_pickup;
 select job_id,job_status,already_applied into j,st,created
 from public.up_manager_manual_job(
  m,v_pickup,req,'900020',
  '{"street":"Rua Destino","number":"35","city":"Campo Grande"}'::jsonb,
  'Pessoa de Teste','67999999999','firebase-test-owner',repeat('a',64));
 if j is null or st <> 'WAITING_DRIVER' or created is distinct from false
 then raise exception 'manual_create_failed';end if;
 select job_id,already_applied into j2,retry
 from public.up_manager_manual_job(
  m,v_pickup,req,'900020',
  '{"street":"Rua Destino","number":"35","city":"Campo Grande"}'::jsonb,
  'Pessoa de Teste','67999999999','firebase-test-owner',repeat('a',64));
 if j2 is distinct from j or retry is distinct from true
 then raise exception 'manual_idempotency_failed';end if;
 select count(*) into n from public.up_jobs where merchant_order_id=req;
 if n<>1 then raise exception 'manual_duplicate_created';end if;
 if not exists (select 1 from public.up_jobs
  where id=j and merchant_id=m and merchant_location_id=v_pickup
  and pickup->>'name'='Rodrigues Açaí e Cia'
  and pickup->'location'->>'lat' is not null
  and metadata->>'billing_status'='NOT_QUOTED') then
  raise exception 'manual_snapshot_or_merchant_scope_failed';
 end if;
 if (select count(*) from public.up_job_events where job_id=j)<>1
 then raise exception 'manual_event_duplicate';end if;
 if (select count(*) from public.up_wallet_transactions where job_id=j)<>0
 then raise exception 'manual_unwanted_charge';end if;

 deny:=false;
 begin
  perform public.up_manager_manual_job(
   m,v_pickup,req,'900020',
   '{"street":"Rua Alterada","city":"Campo Grande"}'::jsonb,
   'Pessoa de Teste','67999999999','firebase-test-owner',repeat('b',64));
 exception when others then
  if SQLERRM='manual_request_conflict' then deny:=true;else raise;end if;
 end;
 if not deny then raise exception 'manual_changed_payload_accepted';end if;

 update public.up_merchants set state='SUSPENDED' where id=m;
 deny:=false;
 begin
  perform public.up_manager_manual_job(
   m,v_pickup,gen_random_uuid(),'900021',
   '{"street":"Rua Destino","city":"Campo Grande"}'::jsonb,
   'Pessoa de Teste','67999999999','firebase-test-owner',repeat('c',64));
 exception when others then
  if SQLERRM='merchant_not_active' then deny:=true;else raise;end if;
 end;
 if not deny then raise exception 'manual_suspended_merchant_accepted';end if;

 -- Status retry of an existing request remains readable after suspension.
 select already_applied into retry
 from public.up_manager_manual_job(
  m,v_pickup,req,'900020',
  '{"street":"Rua Destino","number":"35","city":"Campo Grande"}'::jsonb,
  'Pessoa de Teste','67999999999','firebase-test-owner',repeat('a',64));
 if retry is distinct from true then raise exception 'manual_retry_after_suspension';end if;
 raise notice 'UP manual dispatch assertions passed';
end;
$manual$;
ROLLBACK;
