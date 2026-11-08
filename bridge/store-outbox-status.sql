-- Public API name with server-only execution. Reads private outbox
-- without exposing this schema to PostgREST or customer browsers.
create or replace function public.up_store_outbox_status(p_order_id uuid)
returns table(state text,event_id uuid,up_job_id uuid)
language sql security invoker
set search_path=pg_catalog,public,up_bridge_private
as $$
 select o.state,o.event_id,o.up_job_id
 from up_bridge_private.handoff_outbox o
 where o.order_id=p_order_id limit 1
$$;
revoke all on function public.up_store_outbox_status(uuid) from public,anon,authenticated;
grant execute on function public.up_store_outbox_status(uuid) to service_role;
