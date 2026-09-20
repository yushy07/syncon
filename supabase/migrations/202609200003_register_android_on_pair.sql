begin;

create or replace function public.claim_pairing_request_v2(
  p_request_id uuid,
  p_secret text,
  p_android_installation_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_request public.pairing_requests%rowtype;
  v_account_id uuid;
  v_name text;
begin
  if auth.uid() is null or coalesce((auth.jwt() ->> 'is_anonymous')::boolean, false) is true then
    raise exception 'Permanent Android account required';
  end if;
  if length(p_android_installation_id) not between 8 and 200 then
    raise exception 'Invalid Android installation';
  end if;

  select * into v_request from public.pairing_requests
  where request_id = p_request_id for update;
  if not found then return jsonb_build_object('status', 'NOT_FOUND'); end if;
  if v_request.cancelled_at is not null then return jsonb_build_object('status', 'CANCELLED'); end if;
  if v_request.claimed_at is not null then return jsonb_build_object('status', 'ALREADY_USED'); end if;
  if v_request.expires_at <= now() then return jsonb_build_object('status', 'EXPIRED'); end if;
  if v_request.attempt_count >= 5 then return jsonb_build_object('status', 'LOCKED'); end if;

  if digest(convert_to(p_secret, 'utf8'), 'sha256') <> v_request.secret_hash then
    update public.pairing_requests set attempt_count = attempt_count + 1
    where request_id = p_request_id;
    return jsonb_build_object('status', 'INVALID_SECRET');
  end if;

  select account_id into v_account_id from public.sync_accounts
  where owner_user_id = auth.uid();
  select display_name into v_name from public.profiles where user_id = auth.uid();
  if v_account_id is null then
    insert into public.sync_accounts(owner_user_id, display_name)
    values (auth.uid(), v_name)
    returning account_id into v_account_id;
    insert into public.sync_account_members(account_id, auth_user_id, member_type)
    values (v_account_id, auth.uid(), 'OWNER');
  end if;

  insert into public.installations(
    user_id, account_id, installation_id, platform, display_name, last_seen_at
  ) values (
    auth.uid(), v_account_id, p_android_installation_id, 'ANDROID', v_name, now()
  )
  on conflict (user_id, installation_id) do update set
    account_id = excluded.account_id, platform = 'ANDROID',
    display_name = coalesce(installations.display_name, excluded.display_name),
    last_seen_at = now();

  update public.sync_account_members
  set installation_id = p_android_installation_id, last_seen_at = now()
  where account_id = v_account_id and auth_user_id = auth.uid()
    and member_type = 'OWNER' and revoked_at is null;

  insert into public.sync_account_members(
    account_id, auth_user_id, member_type, installation_id
  ) values (
    v_account_id, v_request.requester_user_id, 'CHROME_DEVICE',
    v_request.installation_id
  )
  on conflict (account_id, auth_user_id) do update set
    member_type = 'CHROME_DEVICE', installation_id = excluded.installation_id,
    revoked_at = null, last_seen_at = now();

  insert into public.installations(
    user_id, account_id, installation_id, platform, display_name,
    client_version, last_seen_at
  ) values (
    auth.uid(), v_account_id, v_request.installation_id, 'CHROME',
    v_request.display_name, v_request.client_version, now()
  )
  on conflict (user_id, installation_id) do update set
    account_id = excluded.account_id, display_name = excluded.display_name,
    client_version = excluded.client_version, last_seen_at = now();

  update public.pairing_requests
  set claimed_at = now(), claimed_by_user_id = auth.uid(), account_id = v_account_id
  where request_id = p_request_id;

  return jsonb_build_object(
    'status', 'CONNECTED',
    'account_id', v_account_id,
    'installation_id', v_request.installation_id
  );
end;
$$;

revoke execute on function public.claim_pairing_request_v2(uuid, text, text)
from public, anon;
grant execute on function public.claim_pairing_request_v2(uuid, text, text)
to authenticated;

commit;
