-- GPS belongs exclusively to the UP Entregas Supabase project.
-- A manual service-role call can prune old TERMINAL-job points only.
-- Active routes are never removed, regardless of age.
create or replace function public.up_prune_stale_location_data(p_keep_days integer default 7)
returns table(deleted_points bigint, cleared_couriers bigint)
language plpgsql security invoker
set search_path=pg_catalog,public
as $$
declare
 v_deleted bigint;
 v_cleared bigint;
begin
 if p_keep_days is null or p_keep_days not between 7 and 365 then
   raise exception 'retention_out_of_range';
 end if;
 delete from public.up_tracking_points p
 using public.up_jobs j
 where j.id=p.job_id
  and j.status in ('DELIVERED','CANCELLED','FAILED')
  and p.recorded_at < now() - make_interval(days=>p_keep_days);
 get diagnostics v_deleted = row_count;
 -- A driver's cached last coordinate should not remain indefinitely after going offline.
 -- Never alter an on-duty driver, or a driver assigned to a non-terminal job.
 update public.up_couriers c
 set last_lat=null,last_lng=null,last_location_at=null,tracking_enabled=false,
     updated_at=now()
 where c.online=false and c.last_location_at < now()-interval '2 hours'
 and not exists(
   select 1 from public.up_jobs j where j.assigned_courier_id=c.id
   and j.status not in ('DELIVERED','CANCELLED','FAILED')
 );
 get diagnostics v_cleared=row_count;
 return query select v_deleted,v_cleared;
end;
$$;
revoke all on function public.up_prune_stale_location_data(integer) from public,anon,authenticated;
grant execute on function public.up_prune_stale_location_data(integer) to service_role;
comment on function public.up_prune_stale_location_data(integer) is
 'Privileged manual GPS retention: 7-365 days, terminal jobs only, clear stale offline courier cache. Never run in shop Supabase.';
