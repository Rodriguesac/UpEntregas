-- Fundação multiestabelecimento UP Entregas.
-- Primeiro parceiro cadastrado em modo DRAFT, sem liberar corridas.
-- Os endereços ficam em pontos de retirada editáveis; a corrida retém o snapshot de pickup.
-- Segredos por parceiro devem viver em Edge Secrets / Vault, NUNCA nestas tabelas.

begin;

create table if not exists public.up_merchants (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique
    check (slug ~ '^[a-z0-9][a-z0-9-]{2,79}$'),
  display_name text not null check (length(trim(display_name)) between 2 and 180),
  state text not null default 'DRAFT'
    check (state in ('DRAFT','ACTIVE','SUSPENDED','CLOSED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.up_merchant_locations (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid not null references public.up_merchants(id) on delete restrict,
  label text not null check (length(trim(label)) between 1 and 120),
  location_kind text not null default 'FIXED' check (location_kind in ('FIXED','MOBILE')),
  street text,
  number text,
  neighborhood text,
  city text,
  state_code text,
  postal_code text,
  complement text,
  reference text,
  latitude numeric(10,7) check (latitude between -90 and 90),
  longitude numeric(10,7) check (longitude between -180 and 180),
  state text not null default 'DRAFT' check (state in ('DRAFT','ACTIVE','PAUSED','ARCHIVED')),
  is_default boolean not null default false,
  revision integer not null default 1 check (revision>0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint up_merchant_locations_coords_pair check
    ((latitude is null and longitude is null) or (latitude is not null and longitude is not null)),
  constraint up_merchant_locations_active_address check
    (state <> 'ACTIVE' or
      (nullif(trim(coalesce(city,'')),'') is not null and
       (nullif(trim(coalesce(street,'')),'') is not null or
        (latitude is not null and longitude is not null)))),
  unique(merchant_id,id)
);

create unique index if not exists up_merchant_locations_default_idx
  on public.up_merchant_locations(merchant_id)
  where is_default and state='ACTIVE';
create index if not exists up_merchant_locations_by_merchant_idx
  on public.up_merchant_locations(merchant_id,state,updated_at desc);

create table if not exists public.up_merchant_members (
  merchant_id uuid not null references public.up_merchants(id) on delete cascade,
  firebase_uid text not null check(length(trim(firebase_uid)) between 4 and 256),
  role text not null check (role in ('OWNER','MANAGER','OPERATOR','VIEWER')),
  active boolean not null default false,
  created_at timestamptz not null default now(),
  primary key (merchant_id,firebase_uid)
);

create table if not exists public.up_merchant_integrations (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid not null references public.up_merchants(id) on delete restrict,
  provider text not null check (length(trim(provider)) between 2 and 80),
  external_merchant_ref text not null check (length(trim(external_merchant_ref)) between 2 and 160),
  status text not null default 'DISABLED'
    check (status in ('DISABLED','READY','SUSPENDED')),
  signing_key_ref text, -- referência indireta a credencial privada; NUNCA segredo
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(provider,external_merchant_ref)
);

-- Nenhum usuário público ou autenticado pode consultar/cadastrar parceiros
-- diretamente pela Data API; APIs de gestão deverão revalidar Firebase e escopo.
alter table public.up_merchants enable row level security;
alter table public.up_merchant_locations enable row level security;
alter table public.up_merchant_members enable row level security;
alter table public.up_merchant_integrations enable row level security;
revoke all on public.up_merchants,public.up_merchant_locations,
  public.up_merchant_members,public.up_merchant_integrations
  from public,anon,authenticated;
grant select,insert,update,delete on public.up_merchants,public.up_merchant_locations,
  public.up_merchant_members,public.up_merchant_integrations to service_role;

-- Não presumir endereço físico nem consentimento para ativar entregas.
insert into public.up_merchants(slug,display_name,state)
values ('rodrigues-acai-e-cia','Rodrigues Açaí e Cia','DRAFT')
on conflict(slug) do nothing;

-- Vincular automaticamente a integração legada, de parceiro único,
-- à Rodrigues sem exigir mudanças no RPC HMAC existente.
alter table public.up_jobs
  add column if not exists merchant_id uuid references public.up_merchants(id) on delete restrict;
alter table public.up_jobs
  add column if not exists merchant_location_id uuid;
update public.up_jobs
  set merchant_id=(select id from public.up_merchants where slug='rodrigues-acai-e-cia')
  where merchant_id is null;
alter table public.up_jobs alter column merchant_id set not null;
do $$
declare original_merchant_id uuid;
begin
  select id into original_merchant_id from public.up_merchants
    where slug='rodrigues-acai-e-cia';
  if original_merchant_id is null then raise exception 'original_merchant_missing'; end if;
  execute format('alter table public.up_jobs alter column merchant_id set default %L::uuid',original_merchant_id);
  if not exists (select 1 from pg_constraint where conname='up_jobs_merchant_location_scoped_fk') then
    alter table public.up_jobs
      add constraint up_jobs_merchant_location_scoped_fk
        foreign key(merchant_id,merchant_location_id)
        references public.up_merchant_locations(merchant_id,id)
        on delete restrict;
  end if;
end $$;

create index if not exists up_jobs_merchant_status_idx
  on public.up_jobs(merchant_id,status,updated_at desc);
create unique index if not exists up_jobs_merchant_external_order_idx
  on public.up_jobs(merchant_id,merchant_order_id);
-- A restrição UNIQUE anterior de merchant_order_id é mantida para não quebrar
-- o contrato atual. Ampliar o recebimento/HMAC para outros parceiros antes
-- de removê-la em uma migração separada.

commit;