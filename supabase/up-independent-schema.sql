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
  status text not null,
  source text not null,
  event_id uuid not null unique,
  notes text,
  occurred_at timestamptz not null default now(),
  delivery_to_store_at timestamptz,
  delivery_to_store_state text not null default 'PENDING'
    check (delivery_to_store_state in ('PENDING','DELIVERED','RETRY','REJECTED')),
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

-- Não criar políticas permissivas de cliente; autenticar Firebase nas Edge Functions.
-- Operações privilegiadas usam somente credenciais service-role no servidor.
