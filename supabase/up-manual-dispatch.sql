-- UP Entregas: solicitação manual por gestor autorizado.
-- Somente no banco UP; execute DEPOIS de up-multimerchant-foundation.sql.
-- A interface de gestão verifica o JWT e perfil; a RPC é service_role only.
-- Não define tarifa, débito, carteira ou pagamentos automaticamente.
create or replace function public.up_manager_manual_job(
 p_merchant uuid,
 p_location uuid,
 p_request_id uuid,
 p_code text,
 p_dropoff jsonb,
 p_name text,
 p_phone text,
 p_actor text,
 p_fingerprint text
) returns table(job_id uuid,job_status text,already_applied boolean)
language plpgsql security invoker
set search_path=pg_catalog,public
as $$
declare
 m public.up_merchants%rowtype;
 loc public.up_merchant_locations%rowtype;
 prior public.up_jobs%rowtype;
 pickup_snapshot jsonb;
 new_id uuid;
begin
 if p_merchant is null or p_location is null or p_request_id is null or
    coalesce(p_code,'') !~ '^[0-9]{3,32}$' or
    coalesce(p_fingerprint,'') !~ '^[0-9a-f]{64}$' or
    length(trim(coalesce(p_actor,'')))<5 or
    jsonb_typeof(p_dropoff) is distinct from 'object' or
    length(trim(coalesce(p_name,''))) not between 2 and 150 or
    coalesce(p_phone,'') !~ '^[0-9]{10,15}$' then
  raise exception 'invalid_manual_request';
 end if;
 if length(trim(coalesce(p_dropoff->>'street','')))<3 or
    length(trim(coalesce(p_dropoff->>'city','')))<2 then
  raise exception 'invalid_delivery_address';
 end if;

 -- The request UUID is globally unique for legacy compatibility; never
 -- reveal a different merchant's job on a repeated UUID.
 select * into prior from public.up_jobs
 where merchant_order_id=p_request_id for update;
 if found then
  if prior.merchant_id is distinct from p_merchant or
     prior.merchant_location_id is distinct from p_location or
     prior.source_payload_sha256 is distinct from p_fingerprint or
     prior.metadata->>'entry_channel' is distinct from 'UP_MANAGER_MANUAL' then
   raise exception 'manual_request_conflict';
  end if;
  return query select prior.id,prior.status,true;
  return;
 end if;

 select * into m from public.up_merchants where id=p_merchant for share;
 if not found or m.state <> 'ACTIVE' then raise exception 'merchant_not_active';end if;
 select * into loc from public.up_merchant_locations
 where merchant_id=p_merchant and id=p_location for share;
 if not found or loc.state <> 'ACTIVE' then raise exception 'pickup_not_active';end if;
 pickup_snapshot=jsonb_build_object(
  'name',m.display_name,'street',coalesce(loc.street,''),
  'number',coalesce(loc.number,''),'neighborhood',coalesce(loc.neighborhood,''),
  'city',coalesce(loc.city,''),'state',coalesce(loc.state_code,''),
  'postal_code',coalesce(loc.postal_code,''),'complement',coalesce(loc.complement,''),
  'reference',coalesce(loc.reference,''),'location_revision',loc.revision
 );
 if loc.latitude is not null and loc.longitude is not null then
  pickup_snapshot=pickup_snapshot||jsonb_build_object(
    'location',jsonb_build_object('lat',loc.latitude,'lng',loc.longitude));
 end if;

 insert into public.up_jobs(
  merchant_id,merchant_location_id,merchant_order_id,merchant_order_code,
  pickup,dropoff,recipient_name,recipient_phone,collection_amount,
  status,source_event_id,source_payload_sha256,metadata
 ) values (
  p_merchant,p_location,p_request_id,p_code,
  pickup_snapshot,p_dropoff,trim(p_name),p_phone,0,
  'WAITING_DRIVER',p_request_id,p_fingerprint,
  jsonb_build_object('entry_channel','UP_MANAGER_MANUAL',
   'created_by',p_actor,'billing_status','NOT_QUOTED')
 )
 on conflict(merchant_order_id) do nothing returning id into new_id;

 if new_id is null then
  select * into prior from public.up_jobs
   where merchant_order_id=p_request_id for update;
  if not found or prior.merchant_id is distinct from p_merchant or
     prior.merchant_location_id is distinct from p_location or
     prior.source_payload_sha256 is distinct from p_fingerprint or
     prior.metadata->>'entry_channel' is distinct from 'UP_MANAGER_MANUAL' then
   raise exception 'manual_request_conflict';
  end if;
  return query select prior.id,prior.status,true;
  return;
 end if;
 insert into public.up_job_events(job_id,sequence,status,source,event_id,notes)
 values(new_id,1,'WAITING_DRIVER','up_manager_manual',p_request_id,
   'Solicitação manual do UP; preço ainda não cotado');
 insert into public.up_audit_log(actor,action,entity_type,entity_id,metadata)
 values(p_actor,'manual_job_created','job',new_id::text,
   jsonb_build_object('merchant_id',p_merchant,'location_id',p_location));
 return query select new_id,'WAITING_DRIVER'::text,false;
end;
$$;
revoke all on function public.up_manager_manual_job(
 uuid,uuid,uuid,text,jsonb,text,text,text,text) from public,anon,authenticated;
grant execute on function public.up_manager_manual_job(
 uuid,uuid,uuid,text,jsonb,text,text,text,text) to service_role;
