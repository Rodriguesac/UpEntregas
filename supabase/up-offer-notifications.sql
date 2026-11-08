-- UP Entregas: private push notification outbox.
-- Deploy ONLY in the independent UP project, after manager + driver schema.
-- Firebase device registration stays private; messages hold no customer PII.
create table if not exists public.up_offer_notifications (
 id uuid primary key default gen_random_uuid(),
 offer_id uuid not null unique references public.up_dispatch_offers(id) on delete cascade,
 status text not null default 'PENDING'
  check(status in ('PENDING','SENDING','RETRY','SENT','FAILED','RECONCILE')),
 attempts integer not null default 0 check(attempts >= 0),
 next_attempt_at timestamptz not null default now(),
 lease_id uuid,
 leased_until timestamptz,
 last_error_code text,
 created_at timestamptz not null default now(),
 sent_at timestamptz
);
create index if not exists up_push_due_idx
 on public.up_offer_notifications(next_attempt_at,created_at)
 where status in ('PENDING','SENDING','RETRY');
alter table public.up_offer_notifications enable row level security;
revoke all on public.up_offer_notifications from anon,authenticated;
grant select,insert,update on public.up_offer_notifications to service_role;

create or replace function public.up_offer_push_enqueue()
returns trigger language plpgsql security invoker
set search_path=pg_catalog,public
as $$
begin
 if new.status='PENDING' then
  insert into public.up_offer_notifications(offer_id)
   values(new.id) on conflict(offer_id) do nothing;
 end if;
 return new;
end;
$$;
revoke all on function public.up_offer_push_enqueue() from public,anon,authenticated;
drop trigger if exists trg_up_offer_push on public.up_dispatch_offers;
create trigger trg_up_offer_push after insert on public.up_dispatch_offers
 for each row execute function public.up_offer_push_enqueue();

create or replace function public.up_offer_push_claim(p_limit integer default 5)
returns table(notification_id uuid,offer_id uuid,job_id uuid,courier_id uuid,
 expires_at timestamptz,lease_id uuid,attempts integer)
language sql security invoker set search_path=pg_catalog,public
as $$
 with chosen as (
  select n.id from public.up_offer_notifications n
  join public.up_dispatch_offers o on o.id=n.offer_id
  join public.up_jobs j on j.id=o.job_id
  join public.up_couriers c on c.id=o.courier_id
  where ((n.status in ('PENDING','RETRY') and n.next_attempt_at<=now())
     or (n.status='SENDING' and n.leased_until<now()))
   and n.attempts<6
   and o.status='PENDING' and o.expires_at>now()
   and j.status='OFFERED' and j.assigned_courier_id is null
   and c.state='APPROVED' and c.online=true
  order by n.next_attempt_at,n.created_at
  limit least(greatest(coalesce(p_limit,5),1),10)
  for update of n skip locked
 )
 update public.up_offer_notifications n
 set status='SENDING',attempts=n.attempts+1,
  lease_id=gen_random_uuid(),leased_until=now()+interval '60 seconds'
 from chosen p,public.up_dispatch_offers o
 where n.id=p.id and o.id=n.offer_id
 returning n.id,o.id,o.job_id,o.courier_id,o.expires_at,n.lease_id,n.attempts;
$$;
revoke all on function public.up_offer_push_claim(integer) from public,anon,authenticated;
grant execute on function public.up_offer_push_claim(integer) to service_role;

create or replace function public.up_offer_push_finish(
 p_notification uuid,p_lease uuid,p_decision text,p_error_code text default null
) returns boolean language plpgsql security invoker
set search_path=pg_catalog,public as $$
declare affected integer;
begin
 if p_decision not in ('sent','retry','halt') then
  raise exception 'invalid_push_decision';
 end if;
 update public.up_offer_notifications n set
  status=case when p_decision='sent' then 'SENT'
   when p_decision='halt' then 'FAILED'
   when n.attempts>=6 then 'RECONCILE'
   else 'RETRY' end,
  sent_at=case when p_decision='sent' then now() else n.sent_at end,
  next_attempt_at=case when p_decision='retry' then now()+
   make_interval(secs=>least(180,(5*power(2,least(n.attempts-1,5)))::integer))
   else n.next_attempt_at end,
  last_error_code=case when p_decision='sent' then null
   else left(coalesce(p_error_code,'unknown'),80) end,
  lease_id=null,leased_until=null
 where n.id=p_notification and n.lease_id=p_lease and n.status='SENDING';
 get diagnostics affected=row_count;
 return affected=1;
end;
$$;
revoke all on function public.up_offer_push_finish(uuid,uuid,text,text) from public,anon,authenticated;
grant execute on function public.up_offer_push_finish(uuid,uuid,text,text) to service_role;
