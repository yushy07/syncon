begin;

-- Security events intentionally omit pairing secrets and usage payloads.
create table if not exists public.sync_security_events (
  event_id bigint generated always as identity primary key,
  account_id uuid references public.sync_accounts(account_id) on delete cascade,
  actor_user_id uuid references auth.users(id) on delete set null,
  installation_id text,
  event_type text not null check (event_type in (
    'PAIRING_CREATED', 'PAIRING_CLAIMED', 'PAIRING_CANCELLED',
    'PAIRING_EXPIRED', 'PAIRING_RETRY', 'INSTALLATION_REVOKED',
    'ACCOUNT_DELETION_REQUESTED'
  )),
  detail jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists sync_security_events_account_created_idx
  on public.sync_security_events(account_id, created_at desc);

alter table public.sync_security_events enable row level security;
drop policy if exists "owners_read_security_events" on public.sync_security_events;
create policy "owners_read_security_events"
  on public.sync_security_events for select to authenticated
  using (public.syncon_can_manage_account(account_id));
revoke all on public.sync_security_events from anon;
grant select on public.sync_security_events to authenticated;

create or replace function public.syncon_log_pairing_lifecycle()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_event text;
begin
  if tg_op = 'INSERT' then
    v_event := 'PAIRING_CREATED';
  elsif new.claimed_at is not null and old.claimed_at is null then
    v_event := 'PAIRING_CLAIMED';
  elsif new.cancelled_at is not null and old.cancelled_at is null then
    v_event := 'PAIRING_CANCELLED';
  elsif new.attempt_count > old.attempt_count then
    v_event := 'PAIRING_RETRY';
  else
    return new;
  end if;

  insert into public.sync_security_events(
    account_id, actor_user_id, installation_id, event_type, detail
  ) values (
    new.account_id,
    case when v_event = 'PAIRING_CLAIMED' then new.claimed_by_user_id else new.requester_user_id end,
    new.installation_id,
    v_event,
    jsonb_build_object('request_id', new.request_id, 'attempt_count', new.attempt_count)
  );
  return new;
end;
$$;

drop trigger if exists pairing_requests_audit_lifecycle on public.pairing_requests;
create trigger pairing_requests_audit_lifecycle
after insert or update on public.pairing_requests
for each row execute function public.syncon_log_pairing_lifecycle();

create or replace function public.syncon_log_installation_revocation()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.revoked_at is not null and old.revoked_at is null then
    insert into public.sync_security_events(
      account_id, actor_user_id, installation_id, event_type
    ) values (
      new.account_id, auth.uid(), new.installation_id, 'INSTALLATION_REVOKED'
    );
  end if;
  return new;
end;
$$;

drop trigger if exists sync_account_members_audit_revocation on public.sync_account_members;
create trigger sync_account_members_audit_revocation
after update on public.sync_account_members
for each row execute function public.syncon_log_installation_revocation();

-- Limit anonymous pairing creation independently of the Auth endpoint limit.
create or replace function public.create_pairing_request_v2(
  p_installation_id text,
  p_secret_hash text,
  p_display_name text default null,
  p_client_version text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_request_id uuid;
  v_expires_at timestamptz := now() + interval '10 minutes';
begin
  if auth.uid() is null or coalesce((auth.jwt() ->> 'is_anonymous')::boolean, false) is not true then
    raise exception 'Anonymous extension session required';
  end if;
  if length(p_installation_id) not between 8 and 200 or p_secret_hash !~ '^[0-9a-f]{64}$' then
    raise exception 'Invalid pairing request';
  end if;
  if (select count(*) from public.pairing_requests
      where requester_user_id = auth.uid() and created_at > now() - interval '1 hour') >= 5 then
    raise exception 'Pairing request rate limit reached';
  end if;

  update public.pairing_requests set cancelled_at = now()
  where requester_user_id = auth.uid() and installation_id = p_installation_id
    and claimed_at is null and cancelled_at is null and expires_at > now();

  insert into public.pairing_requests(
    requester_user_id, installation_id, display_name, client_version, secret_hash, expires_at
  ) values (
    auth.uid(), p_installation_id, nullif(trim(p_display_name), ''), p_client_version,
    decode(p_secret_hash, 'hex'), v_expires_at
  ) returning request_id into v_request_id;

  return jsonb_build_object('request_id', v_request_id, 'expires_at', v_expires_at);
end;
$$;

create or replace function public.get_sync_context_v3()
returns jsonb
language sql
security definer
stable
set search_path = public
as $$
  select case when c.account_id is null then null else jsonb_build_object(
    'account_id', c.account_id,
    'topic', 'account:' || c.account_id::text,
    'is_owner', c.owner_user_id = auth.uid()
  ) end
  from (select 1) seed
  left join public.syncon_account_context() c on true;
$$;

-- Return durable per-record acknowledgements for append-only activity.
create or replace function public.sync_push_intervals_v3(p_intervals jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_result jsonb;
  v_account_id uuid;
begin
  select c.account_id into v_account_id from public.syncon_account_context() c;
  if v_account_id is null then raise exception 'Connected SyncOn account required'; end if;
  v_result := public.sync_push_intervals_v2(p_intervals);
  return v_result || jsonb_build_object(
    'acknowledgements', coalesce((
      select jsonb_agg(jsonb_build_object(
        'record_id', i.record_id,
        'server_revision', i.server_revision,
        'status', 'ACCEPTED'
      ) order by i.server_revision)
      from public.activity_intervals i
      join jsonb_array_elements(coalesce(p_intervals, '[]'::jsonb)) p
        on p->>'record_id' = i.record_id
      where i.account_id = v_account_id
    ), '[]'::jsonb)
  );
end;
$$;

-- State v3 keeps sources/events/mappings idempotent and gives limits strict
-- optimistic concurrency through base_server_revision.
create or replace function public.sync_push_state_v3(
  p_sources jsonb default '[]'::jsonb,
  p_limits jsonb default '[]'::jsonb,
  p_block_events jsonb default '[]'::jsonb,
  p_source_mappings jsonb default '[]'::jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_account_id uuid;
  v_owner_user_id uuid;
  v_base_result jsonb;
  v_item jsonb;
  v_existing public.limit_settings%rowtype;
  v_saved public.limit_settings%rowtype;
  v_accepted jsonb := '[]'::jsonb;
  v_conflicts jsonb := '[]'::jsonb;
  v_base_revision bigint;
begin
  select c.account_id, c.owner_user_id into v_account_id, v_owner_user_id
  from public.syncon_account_context() c;
  if v_account_id is null then raise exception 'Connected SyncOn account required'; end if;
  if jsonb_typeof(coalesce(p_limits, '[]'::jsonb)) <> 'array' then
    raise exception 'Limits payload must be an array';
  end if;

  v_base_result := public.sync_push_state_v2(
    p_sources, '[]'::jsonb, p_block_events, p_source_mappings
  );

  for v_item in select value from jsonb_array_elements(coalesce(p_limits, '[]'::jsonb)) loop
    select * into v_existing from public.limit_settings
    where account_id = v_account_id and record_id = v_item->>'record_id'
    for update;
    v_base_revision := nullif(v_item->>'base_server_revision', '')::bigint;

    if found and coalesce(v_base_revision, 0) <> v_existing.server_revision then
      v_conflicts := v_conflicts || jsonb_build_array(jsonb_build_object(
        'record_id', v_existing.record_id,
        'status', 'CONFLICT',
        'expected_revision', v_base_revision,
        'server_record', to_jsonb(v_existing)
      ));
      continue;
    end if;

    insert into public.limit_settings(
      user_id, account_id, record_id, installation_id, target_type, source_platform,
      target_identifier, daily_limit_minutes, blocking_style, snooze_minutes,
      is_enabled, client_updated_at, local_revision, is_deleted
    ) values (
      v_owner_user_id, v_account_id, v_item->>'record_id', v_item->>'installation_id',
      v_item->>'target_type', nullif(v_item->>'source_platform', ''),
      v_item->>'target_identifier', nullif(v_item->>'daily_limit_minutes', '')::integer,
      coalesce(v_item->>'blocking_style', 'STRICT'),
      coalesce((v_item->>'snooze_minutes')::integer, 5),
      coalesce((v_item->>'is_enabled')::boolean, true),
      (v_item->>'client_updated_at')::timestamptz,
      coalesce((v_item->>'local_revision')::bigint, 1),
      coalesce((v_item->>'is_deleted')::boolean, false)
    )
    on conflict (user_id, record_id) do update set
      account_id = excluded.account_id,
      installation_id = excluded.installation_id,
      target_type = excluded.target_type,
      source_platform = excluded.source_platform,
      target_identifier = excluded.target_identifier,
      daily_limit_minutes = excluded.daily_limit_minutes,
      blocking_style = excluded.blocking_style,
      snooze_minutes = excluded.snooze_minutes,
      is_enabled = excluded.is_enabled,
      client_updated_at = excluded.client_updated_at,
      local_revision = excluded.local_revision,
      is_deleted = excluded.is_deleted
    returning * into v_saved;

    v_accepted := v_accepted || jsonb_build_array(jsonb_build_object(
      'record_id', v_saved.record_id,
      'server_revision', v_saved.server_revision,
      'status', 'ACCEPTED'
    ));
  end loop;

  return v_base_result || jsonb_build_object(
    'accepted_limits', jsonb_array_length(v_accepted),
    'limit_acknowledgements', v_accepted,
    'conflicts', v_conflicts
  );
end;
$$;

-- Minimal account-scoped wake signals; clients still pull through the cursor RPC.
create or replace function public.syncon_broadcast_sync_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_row record;
begin
  v_row := case when tg_op = 'DELETE' then old else new end;
  if v_row.account_id is not null then
    perform realtime.send(
      jsonb_build_object(
        'collection', tg_table_name,
        'server_revision', v_row.server_revision
      ),
      'sync_changed',
      'account:' || v_row.account_id::text,
      true
    );
  end if;
  return null;
end;
$$;

do $$
declare v_table text;
begin
  foreach v_table in array array[
    'installations', 'activity_intervals', 'source_metadata',
    'limit_settings', 'block_events', 'user_source_mappings'
  ] loop
    execute format('drop trigger if exists syncon_broadcast_sync_change on public.%I', v_table);
    execute format(
      'create trigger syncon_broadcast_sync_change after insert or update or delete on public.%I for each row execute function public.syncon_broadcast_sync_change()',
      v_table
    );
  end loop;
end $$;

drop policy if exists "members_receive_account_broadcasts" on realtime.messages;
create policy "members_receive_account_broadcasts"
on realtime.messages for select to authenticated
using (
  realtime.messages.extension = 'broadcast'
  and realtime.topic() like 'account:%'
  and public.syncon_can_access_account(
    substring(realtime.topic() from 9)::uuid
  )
);

create or replace function public.cleanup_expired_pairing_requests_v3()
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_pairings integer := 0;
  v_users integer := 0;
begin
  insert into public.sync_security_events(
    account_id, actor_user_id, installation_id, event_type, detail
  )
  select account_id, requester_user_id, installation_id, 'PAIRING_EXPIRED',
    jsonb_build_object('request_id', request_id)
  from public.pairing_requests
  where expires_at < now() - interval '24 hours'
    and claimed_at is null and cancelled_at is null;

  delete from public.pairing_requests
  where (expires_at < now() - interval '24 hours' and claimed_at is null)
     or (coalesce(claimed_at, cancelled_at) < now() - interval '30 days');
  get diagnostics v_pairings = row_count;

  delete from auth.users u
  where u.is_anonymous is true
    and u.created_at < now() - interval '30 days'
    and not exists (
      select 1 from public.sync_account_members m
      where m.auth_user_id = u.id and m.revoked_at is null
    );
  get diagnostics v_users = row_count;

  return jsonb_build_object(
    'deleted_pairing_requests', v_pairings,
    'deleted_abandoned_anonymous_users', v_users
  );
end;
$$;

create extension if not exists pg_cron with schema pg_catalog;
do $$
begin
  if exists (select 1 from cron.job where jobname = 'syncon-daily-pairing-cleanup') then
    perform cron.unschedule('syncon-daily-pairing-cleanup');
  end if;
  perform cron.schedule(
    'syncon-daily-pairing-cleanup',
    '17 3 * * *',
    'select public.cleanup_expired_pairing_requests_v3()'
  );
end $$;

create or replace function public.delete_sync_account_v3()
returns boolean
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_user_id uuid := auth.uid();
  v_account_id uuid;
begin
  if v_user_id is null or coalesce((auth.jwt() ->> 'is_anonymous')::boolean, false) is true then
    raise exception 'Permanent account owner required';
  end if;
  select account_id into v_account_id from public.sync_accounts
  where owner_user_id = v_user_id for update;
  if v_account_id is null then raise exception 'Account owner required'; end if;

  insert into public.sync_security_events(account_id, actor_user_id, event_type)
  values (v_account_id, v_user_id, 'ACCOUNT_DELETION_REQUESTED');
  delete from auth.users where id = v_user_id;
  return true;
end;
$$;

revoke execute on function public.get_sync_context_v3() from public, anon;
revoke execute on function public.sync_push_intervals_v3(jsonb) from public, anon;
revoke execute on function public.sync_push_state_v3(jsonb, jsonb, jsonb, jsonb) from public, anon;
revoke execute on function public.cleanup_expired_pairing_requests_v3() from public, anon, authenticated;
revoke execute on function public.delete_sync_account_v3() from public, anon;
grant execute on function public.get_sync_context_v3() to authenticated;
grant execute on function public.sync_push_intervals_v3(jsonb) to authenticated;
grant execute on function public.sync_push_state_v3(jsonb, jsonb, jsonb, jsonb) to authenticated;
grant execute on function public.delete_sync_account_v3() to authenticated;

commit;
