-- UP Entregas independente: cadastro atômico de novas lojas em RASCUNHO.
-- Chamado apenas pela Edge Function autenticada de gestor OWNER/ADMIN.
-- O cliente web não possui acesso à função: somente service_role pode executar.
create or replace function public.up_manager_create_merchant_draft(
 p_actor text,
 p_display_name text,
 p_slug text,
 p_label text,
 p_street text,
 p_number text,
 p_neighborhood text,
 p_city text,
 p_state_code text
)
returns table(merchant_id uuid,location_id uuid)
language plpgsql
set search_path to 'pg_catalog','public'
as $function$
declare
 merchant uuid;
 location uuid;
 name_clean text := trim(coalesce(p_display_name,''));
 slug_clean text := lower(trim(coalesce(p_slug,'')));
 label_clean text := trim(coalesce(p_label,''));
 street_clean text := trim(coalesce(p_street,''));
 city_clean text := trim(coalesce(p_city,''));
 uf_clean text := upper(trim(coalesce(p_state_code,'')));
begin
 if length(trim(coalesce(p_actor,'')))<6 or length(p_actor)>180 then
  raise exception 'invalid_manager_actor';
 end if;
 if length(name_clean)<3 or length(name_clean)>150 or
  slug_clean !~ '^[a-z0-9][a-z0-9-]{2,69}$' or
  length(label_clean)<2 or length(label_clean)>120 or
  length(street_clean)<3 or length(street_clean)>180 or
  length(city_clean)<2 or length(city_clean)>120 or
  uf_clean !~ '^[A-Z]{2}$' or
  length(coalesce(p_number,''))>20 or
  length(coalesce(p_neighborhood,''))>120 then
  raise exception 'invalid_merchant_draft';
 end if;

 insert into public.up_merchants(slug,display_name,state)
 values(slug_clean,name_clean,'DRAFT') returning id into merchant;

 insert into public.up_merchant_locations(
  merchant_id,label,location_kind,street,number,neighborhood,city,
  state_code,state,is_default
 ) values(
  merchant,label_clean,'FIXED',street_clean,trim(coalesce(p_number,'')),
  trim(coalesce(p_neighborhood,'')),city_clean,uf_clean,'DRAFT',true
 ) returning id into location;

 insert into public.up_audit_log(actor,action,entity_type,entity_id,metadata)
 values(
  trim(p_actor),'MERCHANT_DRAFT_CREATED','merchant',merchant::text,
  jsonb_build_object('slug',slug_clean,'location_id',location::text)
 );

 return query select merchant,location;
end;
$function$;

revoke all on function public.up_manager_create_merchant_draft(
 text,text,text,text,text,text,text,text,text
) from public,anon,authenticated;
grant execute on function public.up_manager_create_merchant_draft(
 text,text,text,text,text,text,text,text,text
) to service_role;
