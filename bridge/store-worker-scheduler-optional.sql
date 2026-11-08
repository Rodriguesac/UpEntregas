-- Install ONLY in the existing STORE Supabase, after the independent UP
-- receiver exists, secrets are configured and the owner approves cutover.
-- This is NOT auto-installed. The manager and driver GPS remain outside store.
create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

do $validate_up_store$
begin
 if not exists(select 1 from vault.decrypted_secrets where name='up_store_project_url')
    or not exists(select 1 from vault.decrypted_secrets where name='up_store_worker_token') then
  raise exception 'up_store_scheduler_secrets_missing';
 end if;
 if (select decrypted_secret from vault.decrypted_secrets
     where name='up_store_project_url') <>
    'https://fdqqwdplprzpqufpgdrm.supabase.co' then
   raise exception 'outbox_scheduler_must_target_store';
 end if;
end $validate_up_store$;

select cron.schedule(
 'rodrigues-up-outbox-every-minute',
 '* * * * *',
 $worker$
 select net.http_post(
  url := (select decrypted_secret from vault.decrypted_secrets where name='up_store_project_url')
        || '/functions/v1/up-store-outbox-worker',
  headers := jsonb_build_object(
   'content-type','application/json',
   'Authorization','Bearer ' ||
    (select decrypted_secret from vault.decrypted_secrets where name='up_store_worker_token')
  ),
  body := '{}'::jsonb
 );
 $worker$
);
