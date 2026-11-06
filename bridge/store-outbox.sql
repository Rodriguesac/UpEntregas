-- STAGING ONLY. Store-side SQL for Rodrigues Açaí e Cia AFTER the independent UP is ready.
-- Does not run until intentionally applied in the STORE project after the cutover checklist.
-- Never store GPS here; only one small signed-delivery payload per order.
create schema if not exists up_bridge_private;
revoke all on schema up_bridge_private from public,anon,authenticated;
grant usage on schema up_bridge_private to service_role;

create table if not exists up_bridge_private.handoff_outbox (
 id uuid primary key default gen_random_uuid(),
 order_id uuid not null unique references public.orders(id),
 event_id uuid not null unique,
 payload jsonb not null,
 state text not null default 'PENDING'
  check (state in ('PENDING','SENDING','RETRY','ACKED','DEAD','CANCELLED','RECONCILE')),
 attempts integer not null default 0 check (attempts >= 0),
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
revoke all on up_bridge_private.handoff_outbox from public,anon,authenticated;
grant select,insert,update on up_bridge_private.handoff_outbox to service_role;

-- Only a server-side call with service_role may enqueue; lock the order in
-- the SAME transaction, preventing enqueue once it leaves the ready state.
create or replace function public.up_store_outbox_enqueue(
 p_order_id uuid,p_event_id uuid,p_payload jsonb
) returns table(outbox_id uuid,was_created boolean)
language plpgsql security invoker
set search_path=pg_catalog,public,up_bridge_private
as $$
declare
 s public.orders%rowtype;
 existing up_bridge_private.handoff_outbox%rowtype;
 new_id uuid;
 method text;
 paid text;
 amount numeric;
begin
 if p_order_id is null or p_event_id is null or p_payload is null or
    jsonb_typeof(p_payload)<>'object' or
    p_payload->>'action'<>'create_job' or
    p_payload->>'event_id'<>p_event_id::text or
    p_payload#>>'{order,id}'<>p_order_id::text or
    p_payload#>>'{order,status}'<>'PRONTO' or
    upper(coalesce(p_payload#>>'{order,fulfillment_type}',''))<>'ENTREGA' then
   raise exception 'invalid_handoff_payload';
 end if;
 if jsonb_typeof(p_payload#>'{order,pickup}')<>'object' or
    p_payload#>'{order,pickup}'='{}'::jsonb or
    jsonb_typeof(p_payload#>'{order,dropoff}')<>'object' or
    p_payload#>'{order,dropoff}'='{}'::jsonb then
   raise exception 'missing_delivery_address';
 end if;
 select * into s from public.orders where id=p_order_id for update;
 if not found then raise exception 'order_not_found'; end if;

 -- Retry with the same original event and payload, but never create another.
 select * into existing from up_bridge_private.handoff_outbox where order_id=p_order_id;
 if found then
   if existing.event_id<>p_event_id or existing.payload<>p_payload then
     raise exception 'handoff_conflict_same_order';
   end if;
   return query select existing.id,false;
   return;
 end if;

 if upper(s.status)<>'PRONTO' or
    upper(s.fulfillment_type) not in ('ENTREGA','DELIVERY') or
    s.courier_id is not null or
    nullif(s.raw_payload->>'up_target_courier_id','') is not null or
    coalesce(s.raw_payload->>'up_bridge_mode','')='isolated' then
   raise exception 'order_not_ready_for_up';
 end if;
 if coalesce(s.order_code,'')<>p_payload#>>'{order,code}' then
   raise exception 'handoff_order_code_mismatch';
 end if;
 method=upper(replace(coalesce(s.payment_method,''),' ',''));
 paid=upper(coalesce(s.payment_status,''));
 if method ~ 'INFINITEPAY|ONLINE|PIX|CARTAO_ONLINE|CARTÃO_ONLINE' and
    paid not in ('PAGO','PAID','APPROVED','CONFIRMADO') then
   raise exception 'online_payment_not_confirmed';
 end if;
 begin
   amount=(p_payload#>>'{order,collection_amount}')::numeric;
 exception when invalid_text_representation or numeric_value_out_of_range then
   raise exception 'invalid_collection_amount';
 end;
 if amount is null or amount<0 or amount>100000 or
    (method ~ 'INFINITEPAY|ONLINE|PIX|CARTAO_ONLINE|CARTÃO_ONLINE' and amount<>0) then
   raise exception 'invalid_collection_amount';
 end if;
 insert into up_bridge_private.handoff_outbox(order_id,event_id,payload)
  values(p_order_id,p_event_id,p_payload)
  returning id into new_id;
 return query select new_id,true;
end;
$$;
revoke all on function public.up_store_outbox_enqueue(uuid,uuid,jsonb) from public,anon,authenticated;
grant execute on function public.up_store_outbox_enqueue(uuid,uuid,jsonb) to service_role;

-- Claim messages without blocking competing workers. Retired orders cannot be
-- sent even if they were valid when first enqueued; uncertain transmissions
-- move to RECONCILE rather than silently vanish.
create or replace function public.up_store_outbox_claim(p_limit integer default 5)
returns table(outbox_id uuid,event_id uuid,payload jsonb,lease_id uuid,attempts integer)
language plpgsql security invoker
set search_path=pg_catalog,public,up_bridge_private
as $$
begin
 update up_bridge_private.handoff_outbox o
 set state=case when o.attempts>0 then 'RECONCILE' else 'CANCELLED' end,
     last_error_code='store_order_changed_before_send',
     lease_id=null,leased_until=null
 from public.orders s
 where o.order_id=s.id
   and (o.state in ('PENDING','RETRY') or (o.state='SENDING' and o.leased_until<now()))
   and (upper(s.status)<>'PRONTO' or upper(s.fulfillment_type) not in ('ENTREGA','DELIVERY')
        or s.courier_id is not null);
 update up_bridge_private.handoff_outbox o
 set state='RECONCILE',last_error_code='attempts_exhausted',
     lease_id=null,leased_until=null
 where o.attempts>=9 and
       (o.state in ('PENDING','RETRY') or (o.state='SENDING' and o.leased_until<now()));
 return query
 with picked as (
  select o.id from up_bridge_private.handoff_outbox o
  join public.orders s on s.id=o.order_id
  where ((o.state in ('PENDING','RETRY') and o.next_attempt_at<=now())
     or (o.state='SENDING' and o.leased_until<now()))
    and o.attempts<9 and upper(s.status)='PRONTO'
    and upper(s.fulfillment_type) in ('ENTREGA','DELIVERY')
    and s.courier_id is null
  order by o.next_attempt_at,o.created_at
  limit least(greatest(coalesce(p_limit,5),1),10)
  for update of o skip locked
 )
 update up_bridge_private.handoff_outbox o
 set state='SENDING',attempts=o.attempts+1,lease_id=gen_random_uuid(),
     leased_until=now()+interval '90 seconds'
 from picked p
 where o.id=p.id
 returning o.id,o.event_id,o.payload,o.lease_id,o.attempts;
end;
$$;
revoke all on function public.up_store_outbox_claim(integer) from public,anon,authenticated;
grant execute on function public.up_store_outbox_claim(integer) to service_role;

-- ACK transitions to UP exactly once, only when the UP has confirmed a job.
-- If the shop order changed while UP accepted it, do NOT lose the UP job:
-- retain its ID in RECONCILE for staff to resolve without duplicate dispatch.
create or replace function public.up_store_outbox_finish(
 p_id uuid,p_lease uuid,p_decision text,p_error_code text default null,p_job_id uuid default null
) returns boolean
language plpgsql security invoker
set search_path=pg_catalog,public,up_bridge_private
as $$
declare
 ticket up_bridge_private.handoff_outbox%rowtype;
 changed_id uuid;
 next_state text;
begin
 if p_decision not in ('ack','retry','halt') then raise exception 'invalid_outbox_decision';end if;
 if p_decision='ack' and p_job_id is null then raise exception 'missing_up_job_id';end if;
 select * into ticket from up_bridge_private.handoff_outbox o
 where o.id=p_id and o.lease_id=p_lease and o.state='SENDING' for update;
 if not found then return false;end if;
 if p_decision='ack' then
  update public.orders s
  set status='AGUARDANDO_ENTREGADOR',
      raw_payload=jsonb_set(coalesce(s.raw_payload,'{}'::jsonb),
         '{up_bridge}',jsonb_build_object(
            'mode','isolated','orderId',s.id,'jobId',p_job_id,
            'eventId',null,'sequence',0,'state','WAITING_DRIVER',
            'handoffAt',now()),true),
      updated_at=now()
  where s.id=ticket.order_id and upper(s.status)='PRONTO' and
        upper(s.fulfillment_type) in ('ENTREGA','DELIVERY') and s.courier_id is null
  returning s.id into changed_id;
  next_state=case when changed_id is null then 'RECONCILE' else 'ACKED' end;
  update up_bridge_private.handoff_outbox o
   set state=next_state,up_job_id=p_job_id,
       acknowledged_at=case when next_state='ACKED' then now() else null end,
       last_error_code=case when next_state='RECONCILE'
         then 'shop_changed_after_up_ack' else null end,
       lease_id=null,leased_until=null
  where o.id=ticket.id;
  return true;
 end if;
 next_state=case when p_decision='halt' then 'DEAD'
    when ticket.attempts>=9 then 'RECONCILE' else 'RETRY' end;
 update up_bridge_private.handoff_outbox o
 set state=next_state,
     next_attempt_at=case when next_state='RETRY' then
       now()+make_interval(secs=>least(900,(12*power(2,least(o.attempts-1,7)))::integer))
       else o.next_attempt_at end,
     last_error_code=left(coalesce(p_error_code,'unknown'),80),
     lease_id=null,leased_until=null
 where o.id=ticket.id;
 return true;
end;
$$;
revoke all on function public.up_store_outbox_finish(uuid,uuid,text,text,uuid) from public,anon,authenticated;
grant execute on function public.up_store_outbox_finish(uuid,uuid,text,text,uuid) to service_role;
