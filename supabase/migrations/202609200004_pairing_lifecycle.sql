begin;

create or replace function public.cancel_pairing_request_v2(p_request_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count integer;
begin
  update public.pairing_requests
  set cancelled_at = now()
  where request_id = p_request_id
    and requester_user_id = auth.uid()
    and claimed_at is null
    and cancelled_at is null;
  get diagnostics v_count = row_count;
  return v_count > 0;
end;
$$;

create or replace function public.list_connected_installations_v2()
returns table(
  installation_id text,
  platform text,
  display_name text,
  client_version text,
  last_seen_at timestamptz,
  is_current boolean
)
language sql
security definer
stable
set search_path = public
as $$
  select i.installation_id, i.platform, i.display_name, i.client_version,
    i.last_seen_at,
    exists(
      select 1 from public.sync_account_members m
      where m.account_id = i.account_id
        and m.auth_user_id = auth.uid()
        and m.installation_id = i.installation_id
        and m.revoked_at is null
    )
  from public.installations i
  join public.syncon_account_context() c on c.account_id = i.account_id
  where i.platform = 'ANDROID'
     or exists(
       select 1 from public.sync_account_members m
       where m.account_id = i.account_id
         and m.installation_id = i.installation_id
         and m.member_type = 'CHROME_DEVICE'
         and m.revoked_at is null
     )
  order by i.last_seen_at desc;
$$;

create or replace function public.cleanup_expired_pairing_requests_v2()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count integer;
begin
  delete from public.pairing_requests
  where (expires_at < now() - interval '24 hours' and claimed_at is null)
     or (coalesce(claimed_at, cancelled_at) < now() - interval '30 days');
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

revoke execute on function public.cancel_pairing_request_v2(uuid) from public, anon;
grant execute on function public.cancel_pairing_request_v2(uuid) to authenticated;
revoke execute on function public.cleanup_expired_pairing_requests_v2() from public, anon, authenticated;

commit;
