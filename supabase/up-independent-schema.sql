-- UP Entregas — schema de projeto INDEPENDENTE (staging; não executar no Supabase da loja)
-- Versão 2026-11-04; criar somente no projeto UP dedicado.
-- A API do aplicativo deverá operar pelas Edge Functions usando service_role.
create extension if not exists pgcrypto;

create table if not exists public.up_couriers (
  id uuid primary key default gen_random_uuid(),
  firebase_uid text not null unique,
  display_name text not null,
  phone text,
  state text not null default 'PENDING' check (state in ('PENDING','APPROVED','SUSPENDED','REJECTED')),
  online boolean not null default false,
  tracking_enabled boolean not null default false,
  last_lat numeric(10,7) check (last_lat between -90 and 90),
  last_lng numeric(10,7) check (last_lng between -180 and 180),
  last_location_at timestamptz,
  vehicle jsonb not null default '{}'::jsonb,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.up_courier_applications (
  id uuid primary key default gen_random_uuid(),
  firebase_uid text not null unique,
  full_name text not null,
  phone text,
  email text,
  documents jsonb not null default '{}'::jsonb,
  vehicle jsonb not null default '{}'::jsonb,
  payment_details jsonb not null default '{}'::jsonb,
  state text not null default 'PENDING' check (state in ('PENDING','APPROVED','NEEDS_CORRECTION','REJECTED')),
  reviewed_at timestamptz,
  reviewed_by text,
  created_at timestamptz not null default now()
);

create table if not exists public.up_jobs (
  id uuid primary key default gen_random_uuid(),
  merchant_order_id uuid not null unique,
  merchant_order_code text not null,
  pickup jsonb not null default '{}'::jsonb,
  dropoff jsonb not null default '{}'::jsonb,
  recipient_name text,
  recipient_phone text,
  collection_amount numeric(12,2) not null default 0 check (collection_amount >= 0),
  status text not null default 'WAITING_DRIVER'
    check (status in ('WAITING_DRIVER','OFFERED','ACCEPTED','TO_STORE','AT_STORE','TO_CUSTOMER','AT_CUSTOMER','DELIVERED','CANCELLED','FAILED')),
  assigned_courier_id uuid references public.up_couriers(id) on delete set null,
  source_event_id uuid not null unique,
  source_payload_sha256 text not null check (source_payload_sha256 ~ '^[0-9a-f]{64}$'),
  received_at timestamptz not null default now(),
  assigned_at timestamptz,
  delivered_at timestamptz,
  updated_at timestamptz not null default now(),
  metadata jsonb not null default '{}'::jsonb
);

create table if not exists public.up_job_events (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references public.up_jobs(id) on delete cascade,
  sequence bigint not null check (sequence >= 1),
  status text not null,
  source text not null,
  event_id uuid not null unique,
  unique(job_id,sequence),
  notes text,
  occurred_at timestamptz not null default now(),
  delivery_to_store_at timestamptz,
  delivery_to_store_state text not null default 'PENDING'
    check (delivery_to_store_state in ('PENDING','SENDING','DELIVERED','RETRY','REJECTED','RECONCILE')),
  delivery_attempts integer not null default 0 check (delivery_attempts >= 0),
  next_delivery_at timestamptz not null default now(),
  delivery_lease_id uuid,
  delivery_lease_until timestamptz,
  delivery_last_error_code text,
  payload jsonb not null default '{}'::jsonb
);

create table if not exists public.up_tracking_points (
  id bigint generated always as identity primary key,
  job_id uuid references public.up_jobs(id) on delete cascade,
  courier_id uuid not null references public.up_couriers(id),
  lat numeric(10,7) not null check (lat between -90 and 90),
  lng numeric(10,7) not null check (lng between -180 and 180),
  accuracy_m numeric check (accuracy_m >= 0),
  recorded_at timestamptz not null default now(),
  received_at timestamptz not null default now()
);

create table if not exists public.up_dispatch_offers (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references public.up_jobs(id) on delete cascade,
  courier_id uuid not null references public.up_couriers(id),
  status text not null default 'PENDING'
    check (status in ('PENDING','ACCEPTED','DECLINED','EXPIRED','CANCELLED')),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  responded_at timestamptz
);

create table if not exists public.up_wallet_transactions (
  id uuid primary key default gen_random_uuid(),
  courier_id uuid not null references public.up_couriers(id),
  job_id uuid references public.up_jobs(id),
  kind text not null check (kind in ('CREDIT','DEBIT','ADJUSTMENT')),
  amount numeric(12,2) not null,
  description text,
  created_at timestamptz not null default now()
);

create table if not exists public.up_payouts (
  id uuid primary key default gen_random_uuid(),
  courier_id uuid not null references public.up_couriers(id),
  amount numeric(12,2) not null check (amount > 0),
  status text not null default 'REQUESTED'
    check (status in ('REQUESTED','APPROVED','PAID','REJECTED')),
  payment_reference text,
  requested_at timestamptz not null default now(),
  paid_at timestamptz
);

create table if not exists public.up_settings (
  id text primary key,
  value jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

create table if not exists public.up_audit_log (
  id uuid primary key default gen_random_uuid(),
  actor text not null,
  action text not null,
  entity_type text,
  entity_id text,
  created_at timestamptz not null default now(),
  metadata jsonb not null default '{}'::jsonb
);

create index if not exists up_jobs_status_updated_idx on public.up_jobs(status,updated_at desc);
create index if not exists up_job_events_job_time_idx on public.up_job_events(job_id,occurred_at desc);
create index if not exists up_job_events_due_idx on public.up_job_events(next_delivery_at,occurred_at)
 where delivery_to_store_state in ('PENDING','RETRY','SENDING');
create index if not exists up_tracking_job_time_idx on public.up_tracking_points(job_id,recorded_at desc);
create index if not exists up_tracking_courier_time_idx on public.up_tracking_points(courier_id,recorded_at desc);
create index if not exists up_dispatch_offers_courier_status_idx on public.up_dispatch_offers(courier_id,status,created_at desc);

-- Defesas adicionais; nenhum cliente anônimo deve consultar documentos, GPS ou carteira.
alter table public.up_couriers enable row level security;
alter table public.up_courier_applications enable row level security;
alter table public.up_jobs enable row level security;
alter table public.up_job_events enable row level security;
alter table public.up_tracking_points enable row level security;
alter table public.up_dispatch_offers enable row level security;
alter table public.up_wallet_transactions enable row level security;
alter table public.up_payouts enable row level security;
alter table public.up_settings enable row level security;
alter table public.up_audit_log enable row level security;

revoke all on public.up_couriers, public.up_courier_applications, public.up_jobs,
 public.up_job_events, public.up_tracking_points, public.up_dispatch_offers,
 public.up_wallet_transactions, public.up_payouts, public.up_settings,
 public.up_audit_log from anon,authenticated;
revoke all on sequence public.up_tracking_points_id_seq from anon,authenticated;
grant select,insert,update,delete on public.up_couriers, public.up_courier_applications,
 public.up_jobs,public.up_job_events,public.up_tracking_points,public.up_dispatch_offers,
 public.up_wallet_transactions,public.up_payouts,public.up_settings,public.up_audit_log
 to service_role;
grant usage,select on sequence public.up_tracking_points_id_seq to service_role;

-- Não criar políticas permissivas de cliente; autenticar Firebase nas Edge Functions.
-- Operações privilegiadas usam somente credenciais service-role no servidor.

-- Recebimento transacional: nunca criar a corrida sem seu evento inicial.
-- SECURITY INVOKER e permissão exclusiva para service_role.
create or replace function public.up_receive_handoff(
  p_merchant_order_id uuid,
  p_code text,
  p_pickup jsonb,
  p_dropoff jsonb,
  p_name text,
  p_phone text,
  p_collect numeric,
  p_event_id uuid,
  p_hash text
)
returns table(job_id uuid, job_status text, was_created boolean)
language plpgsql security invoker
set search_path = public, pg_temp
as $$
declare
  found_job public.up_jobs%rowtype;
  new_id uuid;
  created boolean := false;
begin
  if p_merchant_order_id is null or p_event_id is null or p_hash !~ '^[0-9a-f]{64}$' then
    raise exception 'invalid_handoff_parameters';
  end if;
  insert into public.up_jobs(
    merchant_order_id,merchant_order_code,pickup,dropoff,recipient_name,recipient_phone,
    collection_amount,source_event_id,source_payload_sha256
  ) values (
    p_merchant_order_id,p_code,p_pickup,p_dropoff,p_name,p_phone,
    p_collect,p_event_id,p_hash
  ) on conflict (merchant_order_id) do nothing
  returning id into new_id;
  if new_id is not null then created := true; end if;
  select * into found_job from public.up_jobs
   where merchant_order_id=p_merchant_order_id;
  if not found then raise exception 'handoff_not_found'; end if;
  if found_job.source_payload_sha256<>p_hash or found_job.source_event_id<>p_event_id then
    raise exception 'handoff_conflict_same_order';
  end if;
  insert into public.up_job_events(job_id,sequence,status,source,event_id,notes)
   values(found_job.id,1,found_job.status,'store_handoff',p_event_id,'Pedido pronto recebido da loja')
   on conflict(event_id) do nothing;
  return query select found_job.id,found_job.status,created;
end;
$$;
revoke all on function public.up_receive_handoff(uuid,text,jsonb,jsonb,text,text,numeric,uuid,text) from public,anon,authenticated;
grant execute on function public.up_receive_handoff(uuid,text,jsonb,jsonb,text,text,numeric,uuid,text) to service_role;


-- UP->Store callback contract: only this service_role API changes job status.
-- The public website, GADMINISTRA and driver APK cannot execute these RPCs.
create or replace function public.up_job_advance(
 p_job_id uuid,p_new_state text,p_event_id uuid,p_source text default 'driver_api'
) returns table(event_id uuid,sequence bigint,job_status text,already_applied boolean)
language plpgsql security invoker set search_path=pg_catalog,public
as $$
declare
 job public.up_jobs%rowtype;
 previous public.up_job_events%rowtype;
 new_sequence bigint;
 requested text:=upper(trim(coalesce(p_new_state,'')));
 actor text:=left(coalesce(nullif(trim(p_source),''),'system'),48);
 allowed boolean:=false;
begin
 if p_job_id is null or p_event_id is null or requested not in
  ('WAITING_DRIVER','OFFERED','ACCEPTED','TO_STORE','AT_STORE',
   'TO_CUSTOMER','AT_CUSTOMER','DELIVERED','CANCELLED','FAILED') then
  raise exception 'invalid_job_transition';
 end if;
 select * into job from public.up_jobs where id=p_job_id for update;
 if not found then raise exception 'job_not_found'; end if;
 select * into previous from public.up_job_events where public.up_job_events.event_id=p_event_id;
 if found then
  if previous.job_id<>p_job_id or previous.status<>requested then
   raise exception 'event_id_collision';
  end if;
  return query select previous.event_id,previous.sequence,previous.status,true;
  return;
 end if;
 if job.status in ('DELIVERED','CANCELLED','FAILED') then
  raise exception 'terminal_job_cannot_change';
 end if;
 -- An external event should normally advance by one operational milestone.
 -- Offers may be renewed while the job remains OFFERED; backward transitions forbidden.
 allowed:=case job.status
  when 'WAITING_DRIVER' then requested='OFFERED'
  when 'OFFERED' then requested in ('OFFERED','ACCEPTED')
  when 'ACCEPTED' then requested='TO_STORE'
  when 'TO_STORE' then requested='AT_STORE'
  when 'AT_STORE' then requested='TO_CUSTOMER'
  when 'TO_CUSTOMER' then requested='AT_CUSTOMER'
  when 'AT_CUSTOMER' then requested='DELIVERED'
  else false end;
 if requested in ('CANCELLED','FAILED') then allowed:=true;end if;
 if not allowed then raise exception 'invalid_job_transition';end if;
 select coalesce(max(e.sequence),0)+1 into new_sequence
 from public.up_job_events e where e.job_id=p_job_id;
 update public.up_jobs set status=requested,updated_at=now(),
  delivered_at=case when requested='DELIVERED' then now() else delivered_at end
 where id=p_job_id;
 insert into public.up_job_events(job_id,sequence,status,source,event_id,notes)
  values(p_job_id,new_sequence,requested,actor,p_event_id,'UP status change');
 return query select p_event_id,new_sequence,requested,false;
end;
$$;
revoke all on function public.up_job_advance(uuid,text,uuid,text) from public,anon,authenticated;
grant execute on function public.up_job_advance(uuid,text,uuid,text) to service_role;

-- Claim events ordered by job. Never send sequence 2 before sequence 1 is
-- acknowledged by the shop, including after timeout/retries.
create or replace function public.up_status_callback_claim(p_limit integer default 5)
returns table(event_id uuid,order_id uuid,job_id uuid,sequence bigint,
 status text,occurred_at timestamptz,lease_id uuid,attempts integer)
language sql security invoker set search_path=pg_catalog,public
as $$
 with picked as (
  select e.id from public.up_job_events e
  where ((e.delivery_to_store_state in ('PENDING','RETRY') and e.next_delivery_at<=now())
     or (e.delivery_to_store_state='SENDING' and e.delivery_lease_until<now()))
   and e.delivery_attempts<9
   and not exists (
    select 1 from public.up_job_events prior
    where prior.job_id=e.job_id and prior.sequence<e.sequence
     and prior.delivery_to_store_state<>'DELIVERED'
   )
  order by e.next_delivery_at,e.occurred_at
  limit least(greatest(coalesce(p_limit,5),1),10)
  for update of e skip locked
 )
 update public.up_job_events e
 set delivery_to_store_state='SENDING',delivery_attempts=e.delivery_attempts+1,
  delivery_lease_id=gen_random_uuid(),delivery_lease_until=now()+interval '90 seconds'
 from picked p, public.up_jobs j
 where e.id=p.id and j.id=e.job_id
 returning e.event_id,j.merchant_order_id,e.job_id,e.sequence,
  e.status,e.occurred_at,e.delivery_lease_id,e.delivery_attempts
$$;
revoke all on function public.up_status_callback_claim(integer) from public,anon,authenticated;
grant execute on function public.up_status_callback_claim(integer) to service_role;

-- Preserve rejected callbacks for investigation. Never discard unknown or
-- possibly delivered notifications. ACK requires the exact lease.
create or replace function public.up_status_callback_finish(
 p_event_id uuid,p_lease uuid,p_decision text,p_error_code text default null
) returns boolean
language plpgsql security invoker set search_path=pg_catalog,public
as $$
declare updated integer;
begin
 if p_decision not in ('ack','retry','halt') then raise exception 'invalid_callback_decision';end if;
 update public.up_job_events e set
  delivery_to_store_state=case
   when p_decision='ack' then 'DELIVERED'
   when p_decision='halt' then 'REJECTED'
   when e.delivery_attempts>=9 then 'RECONCILE'
   else 'RETRY' end,
  delivery_to_store_at=case when p_decision='ack' then now() else e.delivery_to_store_at end,
  next_delivery_at=case when p_decision='retry' then
    now()+make_interval(secs=>least(900,(12*power(2,least(e.delivery_attempts-1,7)))::integer))
    else e.next_delivery_at end,
  delivery_last_error_code=case when p_decision='ack' then null
    else left(coalesce(p_error_code,'unknown'),80) end,
  delivery_lease_id=null,delivery_lease_until=null
 where e.event_id=p_event_id and e.delivery_lease_id=p_lease
  and e.delivery_to_store_state='SENDING';
 get diagnostics updated=row_count;
 return updated=1;
end;
$$;
revoke all on function public.up_status_callback_finish(uuid,uuid,text,text) from public,anon,authenticated;
grant execute on function public.up_status_callback_finish(uuid,uuid,text,text) to service_role;
