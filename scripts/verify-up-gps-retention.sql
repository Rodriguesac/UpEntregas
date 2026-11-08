-- In disposable UP database only; rollback guarantees zero retained test data.
begin;
do $retention$
declare
 v_courier uuid:=gen_random_uuid();
 v_active uuid:=gen_random_uuid();
 v_job_closed uuid:=gen_random_uuid();
 v_job_live uuid:=gen_random_uuid();
 v_removed bigint;
 v_cached bigint;
 v_remaining_closed int;
 v_remaining_live int;
begin
 insert into public.up_couriers(id,firebase_uid,display_name,state,online,last_lat,last_lng,last_location_at,tracking_enabled)
 values (v_courier,'test-'||gen_random_uuid(),'Retenção Offline','APPROVED',false,-20.5,-54.5,now()-interval '3 hours',true),
        (v_active,'test-'||gen_random_uuid(),'Retenção em Rota','APPROVED',true,-20.4,-54.4,now()-interval '3 hours',true);
 insert into public.up_jobs(id,merchant_order_id,merchant_order_code,source_event_id,source_payload_sha256,status,assigned_courier_id)
 values(v_job_closed,gen_random_uuid(),'992001',gen_random_uuid(),repeat('a',64),'DELIVERED',v_courier),
       (v_job_live,gen_random_uuid(),'992002',gen_random_uuid(),repeat('a',64),'TO_CUSTOMER',v_active);
 insert into public.up_tracking_points(job_id,courier_id,lat,lng,recorded_at)
 values(v_job_closed,v_courier,-20.5,-54.5,now()-interval '8 days'),
       (v_job_closed,v_courier,-20.5,-54.5,now()-interval '1 day'),
       (v_job_live,v_active,-20.4,-54.4,now()-interval '8 days');
 select deleted_points,cleared_couriers into v_removed,v_cached
 from public.up_prune_stale_location_data(7);
 if v_removed<>1 or v_cached<>1 then
   raise exception 'Unexpected prune counts deleted:%,cleared:%',v_removed,v_cached;
 end if;
 select count(*) into v_remaining_closed from public.up_tracking_points where job_id=v_job_closed;
 select count(*) into v_remaining_live from public.up_tracking_points where job_id=v_job_live;
 if v_remaining_closed<>1 or v_remaining_live<>1 then
   raise exception 'Pruning deleted recent or active-route GPS';
 end if;
 if exists(select 1 from public.up_couriers where id=v_courier and (last_lat is not null or last_lng is not null)) then
   raise exception 'Offline stale cache was not cleared';
 end if;
 if exists(select 1 from public.up_couriers where id=v_active and last_lat is null) then
   raise exception 'Active driver cache was incorrectly erased';
 end if;
 if has_function_privilege('anon','public.up_prune_stale_location_data(integer)','EXECUTE') or
    has_function_privilege('authenticated','public.up_prune_stale_location_data(integer)','EXECUTE') then
   raise exception 'Retention should never be callable from public clients';
 end if;
 raise notice 'UP GPS retention: privacy and active-route protection PASS';
end $retention$;
rollback;
