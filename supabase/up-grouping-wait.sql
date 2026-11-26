
-- Rodrigues UP: grouped deliveries + full-block wait settlement (idempotent).
-- Apply after up-manager-independent.sql; functions are secured via caller's service-role API.
alter table public.up_tariff_settings add column if not exists grouping_enabled boolean not null default false;
alter table public.up_tariff_settings add column if not exists wait_block_minutes integer not null default 5;
alter table public.up_tariff_settings add column if not exists wait_block_amount numeric(8,2) not null default 1.00;
create table if not exists public.up_groups (
 id uuid primary key default gen_random_uuid(),
 courier_id uuid not null references public.up_couriers(id),
 merchant_id uuid not null references public.up_merchants(id),
 merchant_location_id uuid not null,
 pickup_started_at timestamptz,
 created_at timestamptz not null default clock_timestamp()
);
create table if not exists public.up_group_jobs (
 job_id uuid primary key references public.up_jobs(id) on delete restrict,
 group_id uuid not null references public.up_groups(id) on delete restrict,
 courier_id uuid not null references public.up_couriers(id),
 joined_at timestamptz not null default clock_timestamp()
);
create index if not exists up_group_jobs_group_idx on public.up_group_jobs(group_id);
create table if not exists public.up_wait_cycles (
 id uuid primary key default gen_random_uuid(),
 courier_id uuid not null references public.up_couriers(id),
 job_id uuid not null references public.up_jobs(id) on delete restrict,
 started_at timestamptz not null default clock_timestamp(),
 closed_at timestamptz,
 block_minutes integer not null check(block_minutes between 1 and 120),
 block_amount numeric(8,2) not null check(block_amount between 0 and 100),
 blocks_earned integer not null default 0 check(blocks_earned>=0),
 amount_earned numeric(10,2) not null default 0 check(amount_earned>=0),
 close_reason text,
 ledger_credited boolean not null default false
);
create unique index if not exists up_wait_one_open_per_courier on public.up_wait_cycles(courier_id) where closed_at is null;
create unique index if not exists up_wallet_wait_cycle_once on public.up_wallet_transactions(courier_id,description)
where kind='CREDIT' and description like 'Espera UP ciclo %';
alter table public.up_groups enable row level security;
alter table public.up_group_jobs enable row level security;
alter table public.up_wait_cycles enable row level security;

CREATE OR REPLACE FUNCTION public.up_credit_delivered_quote()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'pg_catalog', 'public'
AS $function$
declare quote numeric;
begin
 if new.status<>'DELIVERED' or old.status='DELIVERED' or new.assigned_courier_id is null or new.metadata->>'test_only'='true' or new.metadata->>'quote_source' is distinct from 'UP_MANAGER_V1' or jsonb_typeof(new.metadata->'courier_quote_amount') is distinct from 'number' then return new;end if;
 select q.amount into quote from public.up_job_quotes q join public.up_dispatch_offers o on o.id=q.offer_id and o.job_id=q.job_id and o.courier_id=q.courier_id and o.status='ACCEPTED' where q.job_id=new.id and q.courier_id=new.assigned_courier_id;
 if not found then return new;end if;
 if quote<=0 or quote>10000 or round(quote,2)<>quote then raise exception 'invalid_quote';end if;
 if exists(select 1 from public.up_wallet_transactions where job_id=new.id and kind='CREDIT' and description='Repasse de entrega cotada UP') then return new;end if;
 insert into public.up_wallet_transactions(courier_id,job_id,kind,amount,description) values(new.assigned_courier_id,new.id,'CREDIT',quote,'Repasse de entrega cotada UP')
 on conflict(job_id) where kind='CREDIT' and description='Repasse de entrega cotada UP' do nothing;
 return new;
end;$function$
;

CREATE OR REPLACE FUNCTION public.up_driver_accept(p_job uuid, p_courier uuid, p_offer uuid)
 RETURNS TABLE(job_id uuid, job_status text, already_applied boolean)
 LANGUAGE plpgsql
 SET search_path TO 'pg_catalog', 'public'
AS $function$
declare
 job public.up_jobs%rowtype;
 offer public.up_dispatch_offers%rowtype;
 courier public.up_couriers%rowtype;
 next_seq bigint;
 other_job public.up_jobs%rowtype;
 grouped boolean:=false;
 group_uuid uuid;
 next_status text;
begin
 if p_job is null or p_courier is null or p_offer is null then
  raise exception 'invalid_accept_parameters';
 end if;
 -- Lock order: job first, then courier, same as UP manager offer RPC.
 select * into job from public.up_jobs where id=p_job for update;
 if not found then raise exception 'job_not_found';end if;
 select * into courier from public.up_couriers where id=p_courier for update;
 if not found or courier.state<>'APPROVED' then raise exception 'courier_not_approved';end if;
 select * into offer from public.up_dispatch_offers o where o.id=p_offer
  and o.job_id=p_job and o.courier_id=p_courier for update;
 if not found then raise exception 'offer_not_found';end if;
 if job.assigned_courier_id=p_courier and job.status in
    ('ACCEPTED','TO_STORE','AT_STORE','TO_CUSTOMER','AT_CUSTOMER','DELIVERED')
    and offer.status='ACCEPTED' then
  return query select job.id,job.status,true;return;
 end if;
 -- The courier row is locked, so two jobs cannot be accepted concurrently.
 -- Preserve idempotent retries above even when the courier has gone offline.
 if courier.online is distinct from true then
  raise exception 'courier_not_available';
 end if;
 select * into other_job from public.up_jobs active
 where active.assigned_courier_id=p_courier and active.id<>p_job
   and active.status not in ('DELIVERED','CANCELLED','FAILED')
 order by active.assigned_at asc nulls last,active.id limit 1;
 grouped:=found;
 if grouped then
  if (select grouping_enabled from public.up_tariff_settings where id=1) is distinct from true
    or exists(select 1 from public.up_jobs active
      where active.assigned_courier_id=p_courier and active.id<>p_job
      and active.status not in ('DELIVERED','CANCELLED','FAILED')
      and (active.status not in ('ACCEPTED','TO_STORE','AT_STORE')
        or active.merchant_id is distinct from job.merchant_id
        or active.merchant_location_id is distinct from job.merchant_location_id))
    or exists(select 1 from public.up_group_jobs g join public.up_groups b on b.id=g.group_id
      join public.up_jobs j on j.id=g.job_id
      where j.assigned_courier_id=p_courier and j.status not in ('DELIVERED','CANCELLED','FAILED')
        and b.pickup_started_at is not null)
  then raise exception 'courier_busy';end if;
 end if;
 if job.assigned_courier_id is not null or job.status<>'OFFERED' then
  raise exception 'job_unavailable';
 end if;
 if offer.status<>'PENDING' or offer.expires_at<=now() then
  raise exception 'offer_expired_or_used';
 end if;
 if grouped then
   select g.group_id into group_uuid from public.up_group_jobs g where g.job_id=other_job.id;
   if group_uuid is null then
     insert into public.up_groups(courier_id,merchant_id,merchant_location_id)
     values(p_courier,job.merchant_id,job.merchant_location_id) returning id into group_uuid;
     insert into public.up_group_jobs(job_id,group_id,courier_id)
       select a.id,group_uuid,p_courier from public.up_jobs a
       where a.assigned_courier_id=p_courier and a.status in ('ACCEPTED','TO_STORE','AT_STORE')
       on conflict(job_id) do nothing;
   end if;
   insert into public.up_group_jobs(job_id,group_id,courier_id)
     values(p_job,group_uuid,p_courier) on conflict(job_id) do nothing;
   if other_job.status='AT_STORE' then perform public.up_wait_end(p_courier,'GROUP_ACCEPT');end if;
 end if;
 next_status:=case when grouped and other_job.status='AT_STORE' then 'AT_STORE'
                  when grouped and other_job.status='TO_STORE' then 'TO_STORE'
                  else 'ACCEPTED' end;
 update public.up_jobs set assigned_courier_id=p_courier,status=next_status,
  assigned_at=now(),updated_at=now(),
  metadata=case when grouped then coalesce(metadata,'{}'::jsonb)||
   jsonb_build_object('up_group_id',group_uuid,'up_grouped',true)
   else metadata end
  where id=p_job;
 if grouped and other_job.status='AT_STORE' then perform public.up_wait_begin(other_job.id,p_courier);end if;
 update public.up_dispatch_offers o set status='ACCEPTED',responded_at=now() where o.id=p_offer;
 update public.up_dispatch_offers o set status='CANCELLED',responded_at=now()
  where o.job_id=p_job and o.id<>p_offer and o.status='PENDING';
 select coalesce(max(e.sequence),0)+1 into next_seq
  from public.up_job_events e where e.job_id=p_job;
 insert into public.up_job_events(job_id,sequence,status,source,event_id,notes)
  values(p_job,next_seq,'ACCEPTED','driver_api',gen_random_uuid(),case when grouped then 'Pedido agrupado aceito' else 'Oferta aceita pelo entregador' end);
 return query select p_job,next_status,false;
end;
$function$
;

CREATE OR REPLACE FUNCTION public.up_driver_advance(p_job uuid, p_courier uuid, p_next text, p_code text DEFAULT NULL::text, p_received numeric DEFAULT 0)
 RETURNS TABLE(job_id uuid, job_status text, already_applied boolean)
 LANGUAGE plpgsql
 SET search_path TO 'pg_catalog', 'public'
AS $function$
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
 -- Serialize pickup with concurrent grouping offers/acceptance for this courier.
 perform 1 from public.up_couriers where id=p_courier for update;
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
 if next_state='AT_STORE' then
    with changed as (
      update public.up_jobs j set status='AT_STORE',updated_at=now()
       where j.assigned_courier_id=p_courier and j.status='TO_STORE'
        and exists(select 1 from public.up_group_jobs g
         join public.up_group_jobs current_g on current_g.group_id=g.group_id
         where current_g.job_id=p_job and g.job_id=j.id)
       returning j.id
    )
    insert into public.up_job_events(job_id,sequence,status,source,event_id,notes)
    select ch.id,coalesce((select max(e.sequence) from public.up_job_events e where e.job_id=ch.id),0)+1,
      'AT_STORE','driver_api',gen_random_uuid(),'Chegada à loja em rota agrupada'
    from changed ch;
    perform public.up_wait_begin(p_job,p_courier);
 elsif next_state='TO_CUSTOMER' then
    perform public.up_wait_end(p_courier,'FIRST_PICKUP');
    update public.up_groups b set pickup_started_at=now()
     where b.pickup_started_at is null
      and exists(select 1 from public.up_group_jobs g
       where g.group_id=b.id and g.job_id=p_job);
 end if;
 return query select p_job,next_state,false;
end;
$function$
;

CREATE OR REPLACE FUNCTION public.up_manager_create_offer(p_job uuid, p_courier uuid, p_manager text, p_minutes integer DEFAULT 3)
 RETURNS TABLE(offer_id uuid, expires_at timestamp with time zone, was_created boolean)
 LANGUAGE plpgsql
 SET search_path TO 'pg_catalog', 'public'
AS $function$
declare
 job public.up_jobs%rowtype;
 courier public.up_couriers%rowtype;
 prior public.up_dispatch_offers%rowtype;
 created uuid;
 expires timestamptz;
begin
 if p_job is null or p_courier is null or length(trim(coalesce(p_manager,'')))<5 or
   p_minutes<1 or p_minutes>10 then
  raise exception 'invalid_offer';
 end if;
 select * into job from public.up_jobs j where j.id=p_job for update;
 if not found then raise exception 'job_not_found';end if;
 if job.assigned_courier_id is not null or job.status not in ('WAITING_DRIVER','OFFERED') then
  raise exception 'job_unavailable';
 end if;
 select * into courier from public.up_couriers c where c.id=p_courier for update;
 if not found or courier.state<>'APPROVED' or courier.online is distinct from true then
  raise exception 'courier_not_available';
 end if;
 if exists(select 1 from public.up_jobs j
   where j.assigned_courier_id=p_courier
   and j.status not in ('DELIVERED','CANCELLED','FAILED')) then
  if (select grouping_enabled from public.up_tariff_settings where id=1) is distinct from true
    or exists(select 1 from public.up_jobs active
      where active.assigned_courier_id=p_courier and active.status not in ('DELIVERED','CANCELLED','FAILED')
      and (active.status not in ('ACCEPTED','TO_STORE','AT_STORE')
         or active.merchant_id is distinct from job.merchant_id
         or active.merchant_location_id is distinct from job.merchant_location_id))
    or exists(select 1 from public.up_group_jobs g join public.up_groups b on b.id=g.group_id
       join public.up_jobs j on j.id=g.job_id
      where j.assigned_courier_id=p_courier and j.status not in ('DELIVERED','CANCELLED','FAILED')
         and b.pickup_started_at is not null)
  then raise exception 'courier_busy';end if;
 end if;
 update public.up_dispatch_offers o set status='EXPIRED',responded_at=now()
  where o.job_id=p_job and o.status='PENDING' and o.expires_at<=now();
 select * into prior from public.up_dispatch_offers o
  where o.job_id=p_job and o.status='PENDING' and o.expires_at>now()
  order by o.created_at desc limit 1 for update;
 if found then
  if prior.courier_id<>p_courier then raise exception 'job_already_offered';end if;
  return query select prior.id,prior.expires_at,false;return;
 end if;
 expires:=now()+make_interval(mins=>p_minutes);
 insert into public.up_dispatch_offers(job_id,courier_id,status,expires_at)
  values(p_job,p_courier,'PENDING',expires) returning id into created;
 perform public.up_job_advance(p_job,'OFFERED',gen_random_uuid(),'up_manager');
 insert into public.up_audit_log(actor,action,entity_type,entity_id,metadata)
  values(p_manager,'offer_sent','job',p_job::text,
   jsonb_build_object('courier_id',p_courier,'offer_id',created,'expires_at',expires));
 return query select created,expires,true;
end;
$function$
;

CREATE OR REPLACE FUNCTION public.up_wait_begin(p_job uuid, p_courier uuid)
 RETURNS void
 LANGUAGE plpgsql
 SET search_path TO 'pg_catalog', 'public'
AS $function$
declare policy jsonb; j public.up_jobs%rowtype; mins int; amt numeric; 
begin
 if exists(select 1 from public.up_wait_cycles where courier_id=p_courier and closed_at is null) then return; end if;
 select * into j from public.up_jobs where id=p_job and assigned_courier_id=p_courier and status='AT_STORE';
 if not found or j.metadata->>'test_only'='true' then return; end if;
 select value into policy from public.up_settings where id='waiting_policy';
 if coalesce(policy->>'enabled','false')<>'true' or coalesce(policy->>'simulation_only','true')='true' then return; end if;
 mins:=coalesce((policy->>'block_minutes')::int,5);
 amt:=coalesce((policy->>'block_amount')::numeric,1);
 if mins<1 or mins>120 or amt<0 or amt>100 then raise exception 'invalid_waiting_policy';end if;
 insert into public.up_wait_cycles(courier_id,job_id,block_minutes,block_amount)
 values (p_courier,p_job,mins,amt) on conflict do nothing;
end;$function$
;

CREATE OR REPLACE FUNCTION public.up_wait_end(p_courier uuid, p_reason text)
 RETURNS numeric
 LANGUAGE plpgsql
 SET search_path TO 'pg_catalog', 'public'
AS $function$
declare cycle public.up_wait_cycles%rowtype; blocks int; value_earned numeric(10,2); closed timestamptz:=clock_timestamp();
begin
 select * into cycle from public.up_wait_cycles where courier_id=p_courier and closed_at is null for update;
 if not found then return 0;end if;
 blocks:=greatest(0,least(288,floor(extract(epoch from (closed-cycle.started_at))/(cycle.block_minutes*60))::int));
 value_earned:=round(blocks*cycle.block_amount,2);
 update public.up_wait_cycles set closed_at=closed,blocks_earned=blocks,
  amount_earned=value_earned,close_reason=left(coalesce(p_reason,'CLOSED'),40),
  ledger_credited=(value_earned>0) where id=cycle.id;
 if value_earned>0 then
   insert into public.up_wallet_transactions(courier_id,job_id,kind,amount,description)
   values (cycle.courier_id,cycle.job_id,'CREDIT',value_earned,'Espera UP ciclo '||cycle.id::text)
   on conflict do nothing;
 end if;
 return value_earned;
end;$function$
;
revoke all on function public.up_wait_begin(uuid,uuid) from public,anon,authenticated;
revoke all on function public.up_wait_end(uuid,text) from public,anon,authenticated;
