-- Additive quote infrastructure; no default tariff, no retroactive credits.
begin;
create table if not exists public.up_tariff_settings (
 id integer primary key check(id=1),
 minimum_amount numeric(10,2) not null check(minimum_amount between 0 and 500),
 per_km numeric(10,2) not null check(per_km between 0 and 100),
 enabled boolean not null default false,
 updated_at timestamptz not null default now()
);
alter table public.up_tariff_settings enable row level security;
revoke all on public.up_tariff_settings from public,anon,authenticated;
grant select,insert,update on public.up_tariff_settings to service_role;
create or replace function public.up_tariff_save(p_minimum numeric,p_per_km numeric,p_enabled boolean,p_actor text)
returns jsonb language plpgsql set search_path=pg_catalog,public as $$
declare result public.up_tariff_settings%rowtype;
begin
 if p_minimum is null or p_per_km is null or p_enabled is null or p_minimum<0 or p_minimum>500 or p_per_km<0 or p_per_km>100 or round(p_minimum,2)<>p_minimum or round(p_per_km,2)<>p_per_km or length(trim(coalesce(p_actor,'')))<5 then raise exception 'invalid_tariff';end if;
 if p_enabled and p_minimum=0 and p_per_km=0 then raise exception 'invalid_tariff';end if;
 insert into public.up_tariff_settings(id,minimum_amount,per_km,enabled) values(1,p_minimum,p_per_km,p_enabled)
 on conflict(id) do update set minimum_amount=excluded.minimum_amount,per_km=excluded.per_km,enabled=excluded.enabled,updated_at=now() returning * into result;
 insert into public.up_audit_log(actor,action,entity_type,entity_id,metadata) values(p_actor,'tariff_saved','tariff','1',to_jsonb(result));
 return to_jsonb(result);
end;$$;
revoke all on function public.up_tariff_save(numeric,numeric,boolean,text) from public,anon,authenticated;
grant execute on function public.up_tariff_save(numeric,numeric,boolean,text) to service_role;
create table if not exists public.up_job_quotes (job_id uuid primary key references public.up_jobs(id) on delete restrict, offer_id uuid not null unique references public.up_dispatch_offers(id), courier_id uuid not null references public.up_couriers(id), amount numeric(12,2) not null check(amount>0 and amount<=10000), quoted_at timestamptz not null default now());
alter table public.up_job_quotes enable row level security;
revoke all on public.up_job_quotes from public,anon,authenticated;
grant select,insert,update on public.up_job_quotes to service_role;
create or replace function public.up_manager_create_quoted_offer(p_job uuid,p_courier uuid,p_manager text,p_minutes integer,p_mode text,p_amount numeric,p_distance numeric,p_duration numeric)
returns table(offer_id uuid,expires_at timestamptz,was_created boolean)
language plpgsql set search_path=pg_catalog,public as $$
declare j public.up_jobs%rowtype; t public.up_tariff_settings%rowtype; prior public.up_dispatch_offers%rowtype; quote numeric; result record;
begin
 if p_job is null or p_courier is null or p_minutes is null or p_minutes<1 or p_minutes>10 or length(trim(coalesce(p_manager,'')))<5 or p_mode is null or p_mode not in ('manual','automatic') or (p_distance is not null and (p_distance<=0 or p_distance>500)) or (p_duration is not null and (p_duration<=0 or p_duration>1440)) then raise exception 'invalid_quote';end if;
 select * into j from public.up_jobs where id=p_job for update;
 if not found then raise exception 'job_not_found';end if;
 if j.assigned_courier_id is not null or j.status not in ('WAITING_DRIVER','OFFERED') then raise exception 'job_unavailable';end if;
 if p_mode='automatic' then
  select * into t from public.up_tariff_settings where id=1 and enabled;
  if not found then raise exception 'tariff_not_configured';end if;
  if p_distance is null then raise exception 'invalid_quote';end if;
  quote:=round(greatest(t.minimum_amount,t.per_km*p_distance),2);
 else quote:=p_amount;
 end if;
 if quote is null or quote<=0 or quote>10000 or round(quote,2)<>quote then raise exception 'invalid_quote';end if;
 select * into prior from public.up_dispatch_offers o where o.job_id=p_job and o.status='PENDING' and o.expires_at>now() order by o.created_at desc limit 1;
 if found then
  if prior.courier_id<>p_courier or (j.metadata->'courier_quote_amount') is distinct from to_jsonb(quote) or j.metadata->>'quote_mode' is distinct from p_mode or coalesce(j.metadata->'route_distance_km','null'::jsonb) is distinct from coalesce(to_jsonb(p_distance),'null'::jsonb) or coalesce(j.metadata->'route_duration_minutes','null'::jsonb) is distinct from coalesce(to_jsonb(p_duration),'null'::jsonb) then raise exception 'quote_already_active';end if;
  return query select prior.id,prior.expires_at,false;return;
 end if;
 update public.up_jobs set metadata=coalesce(metadata,'{}'::jsonb)||jsonb_build_object('courier_quote_amount',quote,'quote_mode',p_mode,'route_distance_km',p_distance,'route_duration_minutes',p_duration,'quote_source','UP_MANAGER_V1','quote_at',now(),'quoted_by',p_manager) where id=p_job;
 select * into result from public.up_manager_create_offer(p_job,p_courier,p_manager,p_minutes);
 insert into public.up_job_quotes(job_id,offer_id,courier_id,amount) values(p_job,result.offer_id,p_courier,quote)
 on conflict(job_id) do update set offer_id=excluded.offer_id,courier_id=excluded.courier_id,amount=excluded.amount,quoted_at=now();
 return query select result.offer_id,result.expires_at,result.was_created;
end;$$;
revoke all on function public.up_manager_create_quoted_offer(uuid,uuid,text,integer,text,numeric,numeric,numeric) from public,anon,authenticated;
grant execute on function public.up_manager_create_quoted_offer(uuid,uuid,text,integer,text,numeric,numeric,numeric) to service_role;
-- The only new automatic credit is for a confirmed manager quote and a real delivered job.
-- Unique partial index makes retry-safe; inspect existing credits before installing.
create unique index if not exists up_wallet_quote_credit_once on public.up_wallet_transactions(job_id) where kind='CREDIT' and description='Repasse de entrega cotada UP';
create or replace function public.up_credit_delivered_quote() returns trigger language plpgsql set search_path=pg_catalog,public as $$
declare quote numeric;
begin
 if new.status<>'DELIVERED' or old.status='DELIVERED' or new.assigned_courier_id is null or new.metadata->>'test_only'='true' or new.metadata->>'quote_source' is distinct from 'UP_MANAGER_V1' or jsonb_typeof(new.metadata->'courier_quote_amount') is distinct from 'number' then return new;end if;
 select q.amount into quote from public.up_job_quotes q join public.up_dispatch_offers o on o.id=q.offer_id and o.job_id=q.job_id and o.courier_id=q.courier_id and o.status='ACCEPTED' where q.job_id=new.id and q.courier_id=new.assigned_courier_id;
 if not found then return new;end if;
 if quote<=0 or quote>10000 or round(quote,2)<>quote then raise exception 'invalid_quote';end if;
 if exists(select 1 from public.up_wallet_transactions where job_id=new.id and kind='CREDIT') then return new;end if;
 insert into public.up_wallet_transactions(courier_id,job_id,kind,amount,description) values(new.assigned_courier_id,new.id,'CREDIT',quote,'Repasse de entrega cotada UP')
 on conflict(job_id) where kind='CREDIT' and description='Repasse de entrega cotada UP' do nothing;
 return new;
end;$$;
revoke all on function public.up_credit_delivered_quote() from public,anon,authenticated;
drop trigger if exists up_credit_delivered_quote_v1 on public.up_jobs;
create trigger up_credit_delivered_quote_v1 after update of status on public.up_jobs for each row when (new.status='DELIVERED' and old.status is distinct from new.status) execute function public.up_credit_delivered_quote();
create or replace function public.up_courier_wallet_summary(p_courier uuid) returns jsonb language sql stable set search_path=pg_catalog,public as $$
 select jsonb_build_object('available_balance',
 (select coalesce(sum(amount),0) from public.up_wallet_transactions where courier_id=p_courier)-
 (select coalesce(sum(amount),0) from public.up_payouts where courier_id=p_courier and status in ('REQUESTED','APPROVED','PAID')),
 'next_payout_at',null);
$$;
revoke all on function public.up_courier_wallet_summary(uuid) from public,anon,authenticated;
grant execute on function public.up_courier_wallet_summary(uuid) to service_role;
commit;
