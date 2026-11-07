-- UP Entregas: driver operations for the INDEPENDENT project only.
-- Apply AFTER up-independent-schema.sql; never run this file in store production.
-- Exposed RPCs are service_role only; Edge driver API must verify Firebase JWT.

-- Device notification tokens remain server-only and cannot be read directly.
create table if not exists public.up_driver_devices (
 id uuid primary key default gen_random_uuid(),
 courier_id uuid not null references public.up_couriers(id) on delete cascade,
 token text not null unique,
 platform text not null default 'android',
 active boolean not null default true,
 updated_at timestamptz not null default now()
);
create index if not exists up_driver_devices_owner_idx on public.up_driver_devices(courier_id,active);
alter table public.up_driver_devices enable row level security;
revoke all on public.up_driver_devices from anon,authenticated;
grant select,insert,update,delete on public.up_driver_devices to service_role;

create unique index if not exists up_one_accepted_offer_per_job
 on public.up_dispatch_offers(job_id) where status='ACCEPTED';
create index if not exists up_driver_offer_due_idx
 on public.up_dispatch_offers(courier_id,status,expires_at);

-- A pre-existing, driver-targeted unexpired offer is mandatory. Row lock
-- prevents a second driver from assigning the same UP job concurrently.
create or replace function public.up_driver_accept(
 p_job uuid,p_courier uuid,p_offer uuid
) returns table(job_id uuid,job_status text,already_applied boolean)
language plpgsql security invoker
set search_path=pg_catalog,public
as $$
declare
 job public.up_jobs%rowtype;
 offer public.up_dispatch_offers%rowtype;
 courier public.up_couriers%rowtype;
 next_seq bigint;
begin
 if p_job is null or p_courier is null or p_offer is null then
  raise exception 'invalid_accept_parameters';
 end if;
 select * into courier from public.up_couriers where id=p_courier;
 if not found or courier.state<>'APPROVED' then raise exception 'courier_not_approved';end if;
 select * into job from public.up_jobs where id=p_job for update;
 if not found then raise exception 'job_not_found';end if;
 select * into offer from public.up_dispatch_offers o where o.id=p_offer
  and o.job_id=p_job and o.courier_id=p_courier for update;
 if not found then raise exception 'offer_not_found';end if;
 if job.assigned_courier_id=p_courier and job.status in
    ('ACCEPTED','TO_STORE','AT_STORE','TO_CUSTOMER','AT_CUSTOMER','DELIVERED')
    and offer.status='ACCEPTED' then
  return query select job.id,job.status,true;return;
 end if;
 if job.assigned_courier_id is not null or job.status<>'OFFERED' then
  raise exception 'job_unavailable';
 end if;
 if offer.status<>'PENDING' or offer.expires_at<=now() then
  raise exception 'offer_expired_or_used';
 end if;
 update public.up_jobs set assigned_courier_id=p_courier,status='ACCEPTED',
  assigned_at=now(),updated_at=now() where id=p_job;
 update public.up_dispatch_offers o set status='ACCEPTED',responded_at=now() where o.id=p_offer;
 update public.up_dispatch_offers o set status='CANCELLED',responded_at=now()
  where o.job_id=p_job and o.id<>p_offer and o.status='PENDING';
 select coalesce(max(e.sequence),0)+1 into next_seq
  from public.up_job_events e where e.job_id=p_job;
 insert into public.up_job_events(job_id,sequence,status,source,event_id,notes)
  values(p_job,next_seq,'ACCEPTED','driver_api',gen_random_uuid(),'Oferta aceita pelo entregador');
 return query select p_job,'ACCEPTED'::text,false;
end;
$$;
revoke all on function public.up_driver_accept(uuid,uuid,uuid) from public,anon,authenticated;
grant execute on function public.up_driver_accept(uuid,uuid,uuid) to service_role;

-- A driver can advance ONLY its assigned nonterminal job. Code checks are
-- performed inside the same transaction as the status event.
create or replace function public.up_driver_advance(
 p_job uuid,p_courier uuid,p_next text,p_code text default null,
 p_received numeric default 0
) returns table(job_id uuid,job_status text,already_applied boolean)
language plpgsql security invoker
set search_path=pg_catalog,public
as $$
declare
 job public.up_jobs%rowtype;
 next_state text:=upper(trim(coalesce(p_next,'')));
 expected_state text;
 expected_code text;
 new_seq bigint;
begin
 if p_job is null or p_courier is null or next_state not in
   ('TO_STORE','AT_STORE','TO_CUSTOMER','AT_CUSTOMER','DELIVERED') then
  raise exception 'invalid_driver_transition';
 end if;
 select * into job from public.up_jobs where id=p_job for update;
 if not found then raise exception 'job_not_found';end if;
 if job.assigned_courier_id is distinct from p_courier then
  raise exception 'not_assigned_driver';
 end if;
 if job.status=next_state then
  return query select job.id,job.status,true;return;
 end if;
 expected_state:=case next_state
   when 'TO_STORE' then 'ACCEPTED'
   when 'AT_STORE' then 'TO_STORE'
   when 'TO_CUSTOMER' then 'AT_STORE'
   when 'AT_CUSTOMER' then 'TO_CUSTOMER'
   when 'DELIVERED' then 'AT_CUSTOMER' end;
 if job.status<>expected_state then raise exception 'invalid_driver_transition';end if;
 if next_state='TO_CUSTOMER' then
  expected_code=nullif(trim(coalesce(job.metadata->>'pickup_code','')),'');
 elsif next_state='DELIVERED' then
  expected_code=nullif(trim(coalesce(job.metadata->>'delivery_code','')),'');
 else expected_code=null;end if;
 if expected_code is not null and expected_code<>trim(coalesce(p_code,'')) then
  raise exception 'incorrect_confirmation_code';
 end if;
 if p_received is null or p_received<0 or p_received>100000 then
  raise exception 'invalid_received_amount';
 end if;
 if next_state='DELIVERED' and job.collection_amount>0 and
    p_received<job.collection_amount then
  raise exception 'collection_not_confirmed';
 end if;
 update public.up_jobs set status=next_state,updated_at=now(),
  delivered_at=case when next_state='DELIVERED' then now() else delivered_at end,
  metadata=case when next_state='DELIVERED'
    then jsonb_set(coalesce(metadata,'{}'::jsonb),'{received_amount}',to_jsonb(p_received),true)
    else metadata end
 where id=p_job;
 select coalesce(max(e.sequence),0)+1 into new_seq
  from public.up_job_events e where e.job_id=p_job;
 insert into public.up_job_events(job_id,sequence,status,source,event_id,notes)
  values(p_job,new_seq,next_state,'driver_api',gen_random_uuid(),'Etapa informada pelo entregador');
 return query select p_job,next_state,false;
end;
$$;
revoke all on function public.up_driver_advance(uuid,uuid,text,text,numeric) from public,anon,authenticated;
grant execute on function public.up_driver_advance(uuid,uuid,text,text,numeric) to service_role;

-- Independent GPS writes: no customer order update and no tracking in the
-- Supabase used by Rodrigues Açaí e Cia.
create or replace function public.up_driver_location(
 p_job uuid,p_courier uuid,p_lat numeric,p_lng numeric,p_accuracy numeric default null
) returns table(saved boolean,reason text)
language plpgsql security invoker
set search_path=pg_catalog,public
as $$
declare
 job public.up_jobs%rowtype;
 courier public.up_couriers%rowtype;
begin
 if p_job is null or p_courier is null or p_lat is null or p_lng is null or
   p_lat not between -90 and 90 or p_lng not between -180 and 180 or
   (p_lat=0 and p_lng=0) or
   (p_accuracy is not null and (p_accuracy<0 or p_accuracy>1000)) then
  raise exception 'invalid_location';
 end if;
 select * into job from public.up_jobs where id=p_job;
 if not found or job.assigned_courier_id is distinct from p_courier or
   job.status not in ('ACCEPTED','TO_STORE','AT_STORE','TO_CUSTOMER','AT_CUSTOMER') then
  raise exception 'location_not_allowed';
 end if;
 select * into courier from public.up_couriers where id=p_courier for update;
 if not found or courier.state<>'APPROVED' then raise exception 'courier_not_approved';end if;
 if courier.last_location_at is not null and
  courier.last_location_at>now()-interval '8 seconds' then
  return query select false,'throttled'::text;return;
 end if;
 update public.up_couriers set last_lat=p_lat,last_lng=p_lng,
  last_location_at=now(),tracking_enabled=true,updated_at=now()
  where id=p_courier;
 insert into public.up_tracking_points(job_id,courier_id,lat,lng,accuracy_m)
  values(p_job,p_courier,p_lat,p_lng,p_accuracy);
 return query select true,'recorded'::text;
end;
$$;
revoke all on function public.up_driver_location(uuid,uuid,numeric,numeric,numeric) from public,anon,authenticated;
grant execute on function public.up_driver_location(uuid,uuid,numeric,numeric,numeric) to service_role;
