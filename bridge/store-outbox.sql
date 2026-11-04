-- STAGING ONLY. Apply in Rodrigues Açaí e Cia Supabase only after separate UP is ready.
-- The store keeps ONE small delivery message per order, never a stream of GPS points.
-- NOT installed in production by this file commit.
create schema if not exists up_bridge_private;
revoke all on schema up_bridge_private from public, anon, authenticated;
grant usage on schema up_bridge_private to service_role;

create table if not exists up_bridge_private.handoff_outbox (
 id uuid primary key default gen_random_uuid(),
 order_id uuid not null unique references public.orders(id),
 event_id uuid not null unique,
 payload jsonb not null,
 state text not null default 'PENDING'
   check (state in ('PENDING','SENDING','RETRY','ACKED','DEAD','CANCELLED')),
 attempts integer not null default 0 check (attempts>=0),
 next_attempt_at timestamptz not null default now(),
 lease_id uuid,
 leased_until timestamptz,
 last_error_code text,
 up_job_id uuid,
 created_at timestamptz not null default now(),
 acknowledged_at timestamptz
);
create index if not exists up_bridge_outbox_due_idx
 on up_bridge_private.handoff_outbox(next_attempt_at,created_at)
 where state in ('PENDING','RETRY','SENDING');
revoke all on up_bridge_private.handoff_outbox from public, anon, authenticated;
grant select,insert,update on up_bridge_private.handoff_outbox to service_role;

-- Claim a small batch without two workers sending the same message simultaneously.
create or replace function public.up_store_outbox_claim(p_limit integer default 5)
returns table(outbox_id uuid,event_id uuid,payload jsonb,lease_id uuid,attempts integer)
language sql security invoker set search_path=pg_catalog,public,up_bridge_private
as $$
 with picked as (
  select o.id from up_bridge_private.handoff_outbox o
  where (o.state in ('PENDING','RETRY') and o.next_attempt_at<=now())
     or (o.state='SENDING' and o.leased_until<now())
  order by o.next_attempt_at asc,o.created_at asc
  limit least(greatest(p_limit,1),10)
  for update skip locked
 )
 update up_bridge_private.handoff_outbox o
 set state='SENDING',attempts=o.attempts+1,lease_id=gen_random_uuid(),
     leased_until=now()+interval '90 seconds'
 from picked p
 where o.id=p.id
 returning o.id,o.event_id,o.payload,o.lease_id,o.attempts
$$;
revoke all on function public.up_store_outbox_claim(integer) from public,anon,authenticated;
grant execute on function public.up_store_outbox_claim(integer) to service_role;

-- ACK or schedule a later retry with bounded exponential backoff.
-- This does NOT mark shop order delivered; a separate authenticated status
-- receiver handles that, and a post-ACK integration must change PRONTO->queue.
create or replace function public.up_store_outbox_finish(
 p_id uuid,p_lease uuid,p_decision text,p_error_code text default null,p_job_id uuid default null
) returns boolean
language plpgsql security invoker set search_path=pg_catalog,public,up_bridge_private
as $$
declare
 changed integer;
begin
 if p_decision not in ('ack','retry','halt') then raise exception 'invalid_outbox_decision';end if;
 update up_bridge_private.handoff_outbox o
 set state=case when p_decision='ack' then 'ACKED'
                when p_decision='halt' or o.attempts>=9 then 'DEAD' else 'RETRY' end,
     acknowledged_at=case when p_decision='ack' then now() else o.acknowledged_at end,
     up_job_id=case when p_decision='ack' then p_job_id else o.up_job_id end,
     next_attempt_at=case when p_decision='retry' then
      now()+make_interval(secs=>least(900,(12*power(2,least(o.attempts-1,7)))::integer))
      else o.next_attempt_at end,
     last_error_code=case when p_decision='ack' then null else left(coalesce(p_error_code,'unknown'),80) end,
     lease_id=null,leased_until=null
 where o.id=p_id and o.lease_id=p_lease and o.state='SENDING';
 get diagnostics changed=row_count;
 return changed=1;
end;
$$;
revoke all on function public.up_store_outbox_finish(uuid,uuid,text,text,uuid) from public,anon,authenticated;
grant execute on function public.up_store_outbox_finish(uuid,uuid,text,text,uuid) to service_role;
