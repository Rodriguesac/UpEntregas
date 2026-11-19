-- UP Entregas: banco operacional ISOLADO.
-- APLICAR APENAS no NOVO projeto Supabase da organização UP Entregas.
-- NÃO executar em rodriguesacaiecia2.
-- Tabelas sem GRANT nem políticas RLS de acesso direto de clientes.
-- Todas as operações externas exigem Edge Functions autenticadas.
begin;

create table if not exists public.up_couriers (
  id uuid primary key default gen_random_uuid(),
  firebase_uid text not null unique,
  full_name text not null,
  phone text,
  status text not null default 'offline'
    check (status in ('offline','available','busy','suspended')),
  is_online boolean not null default false,
  approved_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.up_courier_applications (
  id uuid primary key default gen_random_uuid(),
  firebase_uid text not null unique,
  applicant_name text not null,
  private_application jsonb not null default '{}'::jsonb,
  review_status text not null default 'pending'
    check (review_status in ('pending','correction','approved','rejected','blocked')),
  reviewed_by text,
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.up_deliveries (
  id uuid primary key default gen_random_uuid(),
  source_system text not null,
  source_order_id text not null,
  order_code text,
  customer_firebase_uid text,
  delivery_address jsonb not null default '{}'::jsonb,
  collection_amount numeric(12,2) not null default 0 check (collection_amount >= 0),
  assigned_courier_id uuid references public.up_couriers(id) on delete set null,
  tracking_enabled boolean not null default false,
  status text not null default 'pending'
    check (status in (
      'pending','assigned','accepted','at_store',
      'picked_up','in_route','at_customer','delivered',
      'failed','canceled'
    )),
  requested_at timestamptz not null default now(),
  assigned_at timestamptz,
  picked_up_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint up_deliveries_source_order_unique unique(source_system, source_order_id)
);

create index if not exists up_deliveries_courier_status_idx
  on public.up_deliveries(assigned_courier_id,status);
create index if not exists up_deliveries_customer_status_idx
  on public.up_deliveries(customer_firebase_uid,status);

create table if not exists public.up_delivery_events (
  id bigint generated always as identity primary key,
  delivery_id uuid not null references public.up_deliveries(id) on delete cascade,
  event_key text unique,
  event_type text not null,
  actor_uid text,
  metadata jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null default now()
);
create index if not exists up_delivery_events_delivery_at_idx
  on public.up_delivery_events(delivery_id,occurred_at desc);

-- Última posição por motoboy: upsert, sem registrar cada coordenada.
-- A entrega autorizada do cliente deve ser validada por Edge Function.
create table if not exists public.up_courier_locations (
  courier_id uuid primary key references public.up_couriers(id) on delete cascade,
  latitude double precision not null check (latitude between -90 and 90),
  longitude double precision not null check (longitude between -180 and 180),
  accuracy_m double precision check (accuracy_m is null or accuracy_m >= 0),
  sampled_at timestamptz not null,
  updated_at timestamptz not null default now()
);

-- Histórico opcional. Não gravar por padrão: controlar nas Edge Functions.
create table if not exists public.up_location_history (
  id bigint generated always as identity primary key,
  courier_id uuid not null references public.up_couriers(id) on delete cascade,
  delivery_id uuid references public.up_deliveries(id) on delete set null,
  latitude double precision not null check (latitude between -90 and 90),
  longitude double precision not null check (longitude between -180 and 180),
  accuracy_m double precision check (accuracy_m is null or accuracy_m >= 0),
  sampled_at timestamptz not null
);
create index if not exists up_location_history_courier_at_idx
  on public.up_location_history(courier_id,sampled_at desc);

-- Recebimento idempotente de eventos da loja / GADMINISTRA.
create table if not exists public.up_inbox_events (
  external_event_id text primary key,
  event_type text not null,
  source_system text not null,
  payload jsonb not null,
  processing_status text not null default 'pending'
    check (processing_status in ('pending','processed','failed')),
  failure_reason text,
  received_at timestamptz not null default now(),
  processed_at timestamptz
);

-- Eventos do UP para sincronizar o status de volta ao pedido principal.
create table if not exists public.up_outbox_events (
  id uuid primary key default gen_random_uuid(),
  delivery_id uuid not null references public.up_deliveries(id) on delete cascade,
  event_type text not null,
  payload jsonb not null default '{}'::jsonb,
  delivery_status text not null default 'pending'
    check (delivery_status in ('pending','sending','sent','failed')),
  attempt_count integer not null default 0 check (attempt_count >= 0),
  next_attempt_at timestamptz,
  created_at timestamptz not null default now(),
  sent_at timestamptz
);
create index if not exists up_outbox_unsent_idx
  on public.up_outbox_events(delivery_status, next_attempt_at, created_at);

create table if not exists public.up_wallet_entries (
  id uuid primary key default gen_random_uuid(),
  courier_id uuid not null references public.up_couriers(id) on delete restrict,
  delivery_id uuid references public.up_deliveries(id) on delete set null,
  event_reference text not null unique,
  kind text not null check (kind in ('earning','collection','adjustment','payout','reversal')),
  amount numeric(12,2) not null,
  description text,
  created_at timestamptz not null default now()
);
create index if not exists up_wallet_courier_at_idx
  on public.up_wallet_entries(courier_id,created_at desc);

create table if not exists public.up_payouts (
  id uuid primary key default gen_random_uuid(),
  courier_id uuid not null references public.up_couriers(id) on delete restrict,
  amount numeric(12,2) not null check (amount > 0),
  status text not null default 'pending' check (status in ('pending','paid','canceled')),
  reference text unique,
  requested_at timestamptz not null default now(),
  paid_at timestamptz
);

create table if not exists public.up_settings (
  key text primary key,
  value jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);
insert into public.up_settings(key,value)
values ('tracking','{
  "mode":"dynamic",
  "before_pickup_interval_seconds":45,
  "moving_interval_seconds":20,
  "fast_interval_seconds":15,
  "stationary_interval_seconds":60,
  "customer_mirror_interval_seconds":30,
  "history_enabled":false,
  "customer_tracking_starts_at":"picked_up"
}'::jsonb)
on conflict(key) do nothing;

-- Sem acesso SQL direto pelo navegador, Android ou cliente anônimo:
-- as Edge Functions autenticam Firebase, autorizam por recurso e acessam
-- este banco com credencial server-side armazenada como secret.
do $$
declare t text;
begin
  foreach t in array array[
    'up_couriers','up_courier_applications','up_deliveries',
    'up_delivery_events','up_courier_locations','up_location_history',
    'up_inbox_events','up_outbox_events','up_wallet_entries',
    'up_payouts','up_settings'
  ]
  loop
    execute format('alter table public.%I enable row level security', t);
    execute format('revoke all on table public.%I from anon, authenticated', t);
  end loop;
end $$;

comment on table public.up_deliveries is
  'Projeção logística; o pedido comercial permanece na base de origem.';
comment on table public.up_courier_locations is
  'Apenas localização atual; nunca conceder SELECT público.';
comment on table public.up_inbox_events is
  'Eventos recebidos da loja com deduplicação por external_event_id.';

commit;
