-- OPTIONAL, not included in automatic deployment.
-- Only run in the NEW independent UP Supabase after worker secrets and
-- Supabase Vault names have been configured in the correct project.
-- See: https://supabase.com/docs/guides/functions/schedule-functions
create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

do $up$
begin
 if not exists(select 1 from vault.decrypted_secrets where name='up_project_url') or
    not exists(select 1 from vault.decrypted_secrets where name='up_push_worker_token') or
    not exists(select 1 from vault.decrypted_secrets where name='up_status_worker_token') then
  raise exception 'vault_worker_secrets_not_configured';
 end if;
 if (select decrypted_secret from vault.decrypted_secrets where name='up_project_url')
     <> 'https://gkwmqwtktedarodvqqtn.supabase.co' then
  raise exception 'cron_target_must_be_independent_up_project';
 end if;
end $up$;

select cron.schedule(
 'up-notify-offers-every-minute','* * * * *',
 $job$
   select net.http_post(
     url := (select decrypted_secret from vault.decrypted_secrets
       where name='up_project_url') || '/functions/v1/up-offer-push',
     headers := jsonb_build_object(
       'Content-type','application/json',
       'Authorization','Bearer ' ||
          (select decrypted_secret from vault.decrypted_secrets
           where name='up_push_worker_token')
     ),
     body := '{}'::jsonb
   );
 $job$
);

select cron.schedule(
 'up-return-status-every-minute','* * * * *',
 $job$
   select net.http_post(
     url := (select decrypted_secret from vault.decrypted_secrets
       where name='up_project_url') || '/functions/v1/up-status-relay',
     headers := jsonb_build_object(
       'Content-type','application/json',
       'Authorization','Bearer ' ||
          (select decrypted_secret from vault.decrypted_secrets
           where name='up_status_worker_token')
     ),
     body := '{}'::jsonb
   );
 $job$
);

-- One hour is enough to keep stale GPS from accumulating indefinitely.
-- The cutoff is conservative and may be adjusted to the documented legal
-- retention policy before enabling. No shop tracking table is touched.
select cron.schedule(
 'up-remove-expired-gps','13 * * * *',
 $job$
   delete from public.up_tracking_points
   where id in (
      select id from public.up_tracking_points
      where recorded_at < now()-interval '7 days'
      order by recorded_at asc limit 20000
   );
 $job$
);

select cron.schedule(
 'up-expire-old-offers','*/5 * * * *',
 $job$
   update public.up_dispatch_offers
     set status='EXPIRED',responded_at=now()
   where status='PENDING' and expires_at<now();

   update public.up_offer_notifications n
    set status='FAILED',last_error_code='offer_expired'
   from public.up_dispatch_offers o
   where n.offer_id=o.id and o.status='EXPIRED'
     and n.status in ('PENDING','RETRY');
 $job$
);
