-- UP Gestor independently hosted management database objects.
-- STAGING: apply ONLY to the NEW UP Supabase, AFTER up-independent-schema.sql
-- and up-driver-operations.sql. Do not execute on the store database.
create table if not exists public.up_manager_users(
 firebase_uid text primary key,
 display_name text not null,
 role text not null check(role in ('OWNER','ADMIN','DISPATCHER','VIEWER')),
 active boolean not null default false,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);
alter table public.up_manager_users enable row level security;
revoke all on public.up_manager_users from public,anon,authenticated;
grant select,insert,update on public.up_manager_users to service_role;

-- First administrator MUST be provisioned manually by the owner on the
-- independent Supabase. No seed user, password, approval or public bootstrap.
-- INSERT INTO public.up_manager_users(firebase_uid,display_name,role,active)
-- VALUES ('VERIFIED_FIREBASE_UID','Proprietário UP','OWNER',true);

create or replace function public.up_manager_review_application(
 p_application uuid,p_decision text,p_manager text,p_reason text default null
) returns table(application_id uuid,review_state text,courier_id uuid)
language plpgsql security invoker set search_path=pg_catalog,public
as $$
declare
 applicant public.up_courier_applications%rowtype;
 new_courier uuid;
 decision text:=upper(trim(coalesce(p_decision,'')));
begin
 if p_application is null or decision not in ('APPROVED','NEEDS_CORRECTION','REJECTED') or
    length(trim(coalesce(p_manager,'')))<5 or
    (decision<>'APPROVED' and length(trim(coalesce(p_reason,'')))<5) then
  raise exception 'invalid_review';
 end if;
 select * into applicant from public.up_courier_applications a
 where a.id=p_application for update;
 if not found then raise exception 'application_not_found';end if;
 if applicant.state='APPROVED' and decision='APPROVED' then
  select c.id into new_courier from public.up_couriers c where c.firebase_uid=applicant.firebase_uid;
  if new_courier is null then raise exception 'approved_courier_missing';end if;
  return query select applicant.id,applicant.state,new_courier;return;
 end if;
 if applicant.state='APPROVED' and decision<>'APPROVED' then
  raise exception 'suspend_existing_driver_separately';
 end if;
 if decision='APPROVED' then
  if length(trim(applicant.full_name))<3 or
     length(regexp_replace(coalesce(applicant.phone,''),'[^0-9]','','g'))<10 then
   raise exception 'incomplete_application';
  end if;
  insert into public.up_couriers(firebase_uid,display_name,phone,state,online,vehicle)
   values(applicant.firebase_uid,applicant.full_name,applicant.phone,'APPROVED',false,
      applicant.vehicle)
   on conflict(firebase_uid) do update
    set display_name=excluded.display_name,phone=excluded.phone,
        state='APPROVED',online=false,vehicle=excluded.vehicle,updated_at=now()
   returning id into new_courier;
 end if;
 update public.up_courier_applications a
  set state=decision,reviewed_at=now(),reviewed_by=p_manager,
      documents=case when decision='NEEDS_CORRECTION' then
        jsonb_set(coalesce(a.documents,'{}'::jsonb),'{review_note}',to_jsonb(left(trim(p_reason),500)),true)
        else a.documents end
 where a.id=applicant.id;
 insert into public.up_audit_log(actor,action,entity_type,entity_id,metadata)
 values(p_manager,'application_review','application',applicant.id::text,
   jsonb_build_object('state',decision,'reason',left(coalesce(p_reason,''),500)));
 return query select applicant.id,decision,new_courier;
end;
$$;
revoke all on function public.up_manager_review_application(uuid,text,text,text) from public,anon,authenticated;
grant execute on function public.up_manager_review_application(uuid,text,text,text) to service_role;

-- Dispatch is ONLY within UP; never changes customer-shop orders.
create or replace function public.up_manager_create_offer(
 p_job uuid,p_courier uuid,p_manager text,p_minutes integer default 3
) returns table(offer_id uuid,expires_at timestamptz,was_created boolean)
language plpgsql security invoker set search_path=pg_catalog,public
as $$
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
$$;
revoke all on function public.up_manager_create_offer(uuid,uuid,text,integer) from public,anon,authenticated;
grant execute on function public.up_manager_create_offer(uuid,uuid,text,integer) to service_role;

-- Once a driver has a job, suspension requires releasing/completing the
-- assignment in UP first. The store is never modified.
create or replace function public.up_manager_set_driver_state(
 p_courier uuid,p_state text,p_manager text
) returns boolean
language plpgsql security invoker set search_path=pg_catalog,public
as $$
declare wanted text:=upper(trim(coalesce(p_state,'')));
begin
 if p_courier is null or wanted not in ('APPROVED','SUSPENDED') or
    length(trim(coalesce(p_manager,'')))<5 then raise exception 'invalid_driver_state';end if;
 if exists(select 1 from public.up_jobs j where j.assigned_courier_id=p_courier
  and j.status not in ('DELIVERED','CANCELLED','FAILED')) then
   raise exception 'driver_has_active_job';
 end if;
 update public.up_couriers set state=wanted,
  online=case when wanted='SUSPENDED' then false else online end,
  tracking_enabled=case when wanted='SUSPENDED' then false else tracking_enabled end,
  updated_at=now() where id=p_courier;
 if not found then return false;end if;
 insert into public.up_audit_log(actor,action,entity_type,entity_id,metadata)
 values(p_manager,'driver_state','courier',p_courier::text,jsonb_build_object('state',wanted));
 return true;
end;
$$;
revoke all on function public.up_manager_set_driver_state(uuid,text,text) from public,anon,authenticated;
grant execute on function public.up_manager_set_driver_state(uuid,text,text) to service_role;

-- Financial actions only record ledger/payout state. They NEVER send Pix.
create or replace function public.up_manager_request_payout(
 p_courier uuid,p_amount numeric,p_manager text
) returns uuid
language plpgsql security invoker set search_path=pg_catalog,public
as $$
declare
 ledger numeric:=0;
 reserved numeric:=0;
 new_id uuid;
begin
 if p_courier is null or p_amount is null or p_amount<=0 or p_amount>50000
  or length(trim(coalesce(p_manager,'')))<5 then raise exception 'invalid_payout';end if;
 perform 1 from public.up_couriers c where c.id=p_courier for update;
 if not found then raise exception 'courier_not_found';end if;
 select coalesce(sum(t.amount),0) into ledger from public.up_wallet_transactions t
 where t.courier_id=p_courier;
 select coalesce(sum(p.amount),0) into reserved from public.up_payouts p
 where p.courier_id=p_courier and p.status in ('REQUESTED','APPROVED','PAID');
 if ledger-reserved<p_amount then raise exception 'insufficient_wallet_balance';end if;
 insert into public.up_payouts(courier_id,amount,status)
 values(p_courier,p_amount,'REQUESTED') returning id into new_id;
 insert into public.up_audit_log(actor,action,entity_type,entity_id,metadata)
 values(p_manager,'payout_requested','payout',new_id::text,
  jsonb_build_object('courier_id',p_courier,'amount',p_amount));
 return new_id;
end;
$$;
revoke all on function public.up_manager_request_payout(uuid,numeric,text) from public,anon,authenticated;
grant execute on function public.up_manager_request_payout(uuid,numeric,text) to service_role;

create or replace function public.up_manager_confirm_payout(
 p_payout uuid,p_manager text,p_reference text
) returns boolean
language plpgsql security invoker set search_path=pg_catalog,public
as $$
declare
 payout public.up_payouts%rowtype;
begin
 if p_payout is null or length(trim(coalesce(p_manager,'')))<5 or
  length(trim(coalesce(p_reference,'')))<5 then raise exception 'invalid_payout_confirmation';end if;
 select * into payout from public.up_payouts p where p.id=p_payout for update;
 if not found then raise exception 'payout_not_found';end if;
 if payout.status='PAID' then
  if payout.payment_reference is distinct from p_reference then
   raise exception 'payout_already_paid_different_reference';end if;
  return true;
 end if;
 if payout.status not in ('REQUESTED','APPROVED') then
  raise exception 'payout_cannot_be_paid';end if;
 update public.up_payouts p set status='PAID',payment_reference=p_reference,paid_at=now()
 where p.id=p_payout;
 insert into public.up_audit_log(actor,action,entity_type,entity_id,metadata)
 values(p_manager,'payout_recorded_paid','payout',p_payout::text,
 jsonb_build_object('reference',p_reference,'amount',payout.amount));
 return true;
end;
$$;
revoke all on function public.up_manager_confirm_payout(uuid,text,text) from public,anon,authenticated;
grant execute on function public.up_manager_confirm_payout(uuid,text,text) to service_role;
