begin;

create extension if not exists pgcrypto with schema extensions;

create table public.sync_accounts (
  account_id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null unique references auth.users(id) on delete cascade,
  display_name text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.sync_account_members (
  account_id uuid not null references public.sync_accounts(account_id) on delete cascade,
  auth_user_id uuid not null references auth.users(id) on delete cascade,
  member_type text not null check (member_type in ('OWNER', 'ANDROID_DEVICE', 'CHROME_DEVICE')),
  installation_id text,
  joined_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  revoked_at timestamptz,
  primary key (account_id, auth_user_id)
);

create table public.pairing_requests (
  request_id uuid primary key default gen_random_uuid(),
  requester_user_id uuid not null references auth.users(id) on delete cascade,
  installation_id text not null check (length(installation_id) between 8 and 200),
  display_name text,
  client_version text,
  secret_hash bytea not null,
  expires_at timestamptz not null,
  claimed_at timestamptz,
  claimed_by_user_id uuid references auth.users(id) on delete set null,
  account_id uuid references public.sync_accounts(account_id) on delete cascade,
  attempt_count integer not null default 0,
  cancelled_at timestamptz,
  created_at timestamptz not null default now(),
  check (expires_at > created_at)
);

insert into public.profiles(user_id, display_name)
select u.id, split_part(coalesce(u.email, ''), '@', 1)
from auth.users u
on conflict (user_id) do nothing;

insert into public.sync_accounts(owner_user_id, display_name)
select p.user_id, p.display_name from public.profiles p
on conflict (owner_user_id) do nothing;

insert into public.sync_account_members(account_id, auth_user_id, member_type)
select a.account_id, a.owner_user_id, 'OWNER'
from public.sync_accounts a
on conflict (account_id, auth_user_id) do nothing;

alter table public.installations add column account_id uuid;
alter table public.activity_intervals add column account_id uuid;
alter table public.source_metadata add column account_id uuid;
alter table public.limit_settings add column account_id uuid;
alter table public.block_events add column account_id uuid;
alter table public.user_source_mappings add column account_id uuid;

update public.installations t set account_id = a.account_id from public.sync_accounts a where a.owner_user_id = t.user_id;
update public.activity_intervals t set account_id = a.account_id from public.sync_accounts a where a.owner_user_id = t.user_id;
update public.source_metadata t set account_id = a.account_id from public.sync_accounts a where a.owner_user_id = t.user_id;
update public.limit_settings t set account_id = a.account_id from public.sync_accounts a where a.owner_user_id = t.user_id;
update public.block_events t set account_id = a.account_id from public.sync_accounts a where a.owner_user_id = t.user_id;
update public.user_source_mappings t set account_id = a.account_id from public.sync_accounts a where a.owner_user_id = t.user_id;

alter table public.installations alter column account_id set not null;
alter table public.activity_intervals alter column account_id set not null;
alter table public.source_metadata alter column account_id set not null;
alter table public.limit_settings alter column account_id set not null;
alter table public.block_events alter column account_id set not null;
alter table public.user_source_mappings alter column account_id set not null;

alter table public.installations add constraint installations_account_fk foreign key (account_id) references public.sync_accounts(account_id) on delete cascade;
alter table public.activity_intervals add constraint activity_intervals_account_fk foreign key (account_id) references public.sync_accounts(account_id) on delete cascade;
alter table public.source_metadata add constraint source_metadata_account_fk foreign key (account_id) references public.sync_accounts(account_id) on delete cascade;
alter table public.limit_settings add constraint limit_settings_account_fk foreign key (account_id) references public.sync_accounts(account_id) on delete cascade;
alter table public.block_events add constraint block_events_account_fk foreign key (account_id) references public.sync_accounts(account_id) on delete cascade;
alter table public.user_source_mappings add constraint user_source_mappings_account_fk foreign key (account_id) references public.sync_accounts(account_id) on delete cascade;

create unique index installations_account_installation_uidx on public.installations(account_id, installation_id);
create unique index activity_intervals_account_record_uidx on public.activity_intervals(account_id, record_id);
create unique index source_metadata_account_source_uidx on public.source_metadata(account_id, source_type, source_identifier);
create unique index limit_settings_account_record_uidx on public.limit_settings(account_id, record_id);
create unique index block_events_account_record_uidx on public.block_events(account_id, record_id);
create unique index source_mappings_account_record_uidx on public.user_source_mappings(account_id, record_id);
create index sync_account_members_user_idx on public.sync_account_members(auth_user_id) where revoked_at is null;
create index pairing_requests_requester_idx on public.pairing_requests(requester_user_id, created_at desc);
create index pairing_requests_expiry_idx on public.pairing_requests(expires_at) where claimed_at is null and cancelled_at is null;

create or replace function public.syncon_can_access_account(p_account_id uuid)
returns boolean
language sql
security definer
stable
set search_path = public
as $$
  select exists(
    select 1 from public.sync_account_members m
    where m.account_id = p_account_id
      and m.auth_user_id = auth.uid()
      and m.revoked_at is null
  );
$$;

create or replace function public.syncon_can_manage_account(p_account_id uuid)
returns boolean
language sql
security definer
stable
set search_path = public
as $$
  select exists(
    select 1 from public.sync_accounts a
    where a.account_id = p_account_id and a.owner_user_id = auth.uid()
  );
$$;

create or replace function public.syncon_account_context()
returns table(account_id uuid, owner_user_id uuid)
language sql
security definer
stable
set search_path = public
as $$
  select a.account_id, a.owner_user_id
  from public.sync_account_members m
  join public.sync_accounts a using (account_id)
  where m.auth_user_id = auth.uid() and m.revoked_at is null
  order by (m.member_type = 'OWNER') desc, m.joined_at
  limit 1;
$$;

create or replace function public.syncon_create_profile()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_account_id uuid;
  v_name text := coalesce(new.raw_user_meta_data ->> 'display_name', split_part(coalesce(new.email, ''), '@', 1));
begin
  insert into public.profiles(user_id, display_name) values (new.id, v_name)
  on conflict (user_id) do nothing;

  if coalesce(new.is_anonymous, false) then
    return new;
  end if;

  insert into public.sync_accounts(owner_user_id, display_name) values (new.id, v_name)
  on conflict (owner_user_id) do update set display_name = coalesce(sync_accounts.display_name, excluded.display_name)
  returning account_id into v_account_id;
  insert into public.sync_account_members(account_id, auth_user_id, member_type)
  values (v_account_id, new.id, 'OWNER')
  on conflict (account_id, auth_user_id) do nothing;
  return new;
end;
$$;

alter table public.sync_accounts enable row level security;
alter table public.sync_account_members enable row level security;
alter table public.pairing_requests enable row level security;

create policy "account_members_read_account" on public.sync_accounts for select to authenticated
using (public.syncon_can_access_account(account_id));
create policy "owner_updates_account" on public.sync_accounts for update to authenticated
using (owner_user_id = auth.uid()) with check (owner_user_id = auth.uid());
create policy "members_read_members" on public.sync_account_members for select to authenticated
using (public.syncon_can_access_account(account_id));
create policy "requester_reads_pairing" on public.pairing_requests for select to authenticated
using (requester_user_id = auth.uid());

create policy "account_members_read_installations" on public.installations for select to authenticated using (public.syncon_can_access_account(account_id));
create policy "account_members_insert_installations" on public.installations for insert to authenticated with check (public.syncon_can_access_account(account_id));
create policy "account_members_update_installations" on public.installations for update to authenticated using (public.syncon_can_access_account(account_id)) with check (public.syncon_can_access_account(account_id));
create policy "account_members_read_intervals" on public.activity_intervals for select to authenticated using (public.syncon_can_access_account(account_id));
create policy "account_members_insert_intervals" on public.activity_intervals for insert to authenticated with check (public.syncon_can_access_account(account_id));
create policy "account_members_update_intervals" on public.activity_intervals for update to authenticated using (public.syncon_can_access_account(account_id)) with check (public.syncon_can_access_account(account_id));
create policy "account_members_read_sources" on public.source_metadata for select to authenticated using (public.syncon_can_access_account(account_id));
create policy "account_members_insert_sources" on public.source_metadata for insert to authenticated with check (public.syncon_can_access_account(account_id));
create policy "account_members_update_sources" on public.source_metadata for update to authenticated using (public.syncon_can_access_account(account_id)) with check (public.syncon_can_access_account(account_id));
create policy "account_members_read_limits" on public.limit_settings for select to authenticated using (public.syncon_can_access_account(account_id));
create policy "account_members_insert_limits" on public.limit_settings for insert to authenticated with check (public.syncon_can_access_account(account_id));
create policy "account_members_update_limits" on public.limit_settings for update to authenticated using (public.syncon_can_access_account(account_id)) with check (public.syncon_can_access_account(account_id));
create policy "account_members_read_events" on public.block_events for select to authenticated using (public.syncon_can_access_account(account_id));
create policy "account_members_insert_events" on public.block_events for insert to authenticated with check (public.syncon_can_access_account(account_id));
create policy "account_members_update_events" on public.block_events for update to authenticated using (public.syncon_can_access_account(account_id)) with check (public.syncon_can_access_account(account_id));
create policy "account_members_read_mappings" on public.user_source_mappings for select to authenticated using (public.syncon_can_access_account(account_id));
create policy "account_members_insert_mappings" on public.user_source_mappings for insert to authenticated with check (public.syncon_can_access_account(account_id));
create policy "account_members_update_mappings" on public.user_source_mappings for update to authenticated using (public.syncon_can_access_account(account_id)) with check (public.syncon_can_access_account(account_id));

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

create or replace function public.get_pairing_status_v2(p_request_id uuid)
returns jsonb
language plpgsql
security definer
stable
set search_path = public
as $$
declare
  v_request public.pairing_requests%rowtype;
begin
  select * into v_request from public.pairing_requests
  where request_id = p_request_id and requester_user_id = auth.uid();
  if not found then return jsonb_build_object('status', 'NOT_FOUND'); end if;
  if v_request.cancelled_at is not null then return jsonb_build_object('status', 'CANCELLED'); end if;
  if v_request.claimed_at is not null then
    return jsonb_build_object('status', 'CONNECTED', 'account_id', v_request.account_id, 'claimed_at', v_request.claimed_at);
  end if;
  if v_request.expires_at <= now() then return jsonb_build_object('status', 'EXPIRED'); end if;
  return jsonb_build_object('status', 'WAITING', 'expires_at', v_request.expires_at);
end;
$$;

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

  select * into v_request from public.pairing_requests where request_id = p_request_id for update;
  if not found then return jsonb_build_object('status', 'NOT_FOUND'); end if;
  if v_request.cancelled_at is not null then return jsonb_build_object('status', 'CANCELLED'); end if;
  if v_request.claimed_at is not null then return jsonb_build_object('status', 'ALREADY_USED'); end if;
  if v_request.expires_at <= now() then return jsonb_build_object('status', 'EXPIRED'); end if;
  if v_request.attempt_count >= 5 then return jsonb_build_object('status', 'LOCKED'); end if;

  if digest(convert_to(p_secret, 'utf8'), 'sha256') <> v_request.secret_hash then
    update public.pairing_requests set attempt_count = attempt_count + 1 where request_id = p_request_id;
    return jsonb_build_object('status', 'INVALID_SECRET');
  end if;

  select account_id into v_account_id from public.sync_accounts where owner_user_id = auth.uid();
  if v_account_id is null then
    select display_name into v_name from public.profiles where user_id = auth.uid();
    insert into public.sync_accounts(owner_user_id, display_name) values (auth.uid(), v_name)
    returning account_id into v_account_id;
    insert into public.sync_account_members(account_id, auth_user_id, member_type)
    values (v_account_id, auth.uid(), 'OWNER');
  end if;

  insert into public.sync_account_members(account_id, auth_user_id, member_type, installation_id)
  values (v_account_id, v_request.requester_user_id, 'CHROME_DEVICE', v_request.installation_id)
  on conflict (account_id, auth_user_id) do update set
    member_type = 'CHROME_DEVICE', installation_id = excluded.installation_id,
    revoked_at = null, last_seen_at = now();

  insert into public.installations(user_id, account_id, installation_id, platform, display_name, client_version, last_seen_at)
  values (auth.uid(), v_account_id, v_request.installation_id, 'CHROME', v_request.display_name, v_request.client_version, now())
  on conflict (user_id, installation_id) do update set
    account_id = excluded.account_id, display_name = excluded.display_name,
    client_version = excluded.client_version, last_seen_at = now();

  update public.pairing_requests set claimed_at = now(), claimed_by_user_id = auth.uid(), account_id = v_account_id
  where request_id = p_request_id;

  return jsonb_build_object('status', 'CONNECTED', 'account_id', v_account_id, 'installation_id', v_request.installation_id);
end;
$$;

create or replace function public.sync_register_installation_v2(
  p_installation_id text,
  p_platform text,
  p_display_name text default null,
  p_client_version text default null
)
returns bigint
language plpgsql
security definer
set search_path = public
as $$
declare
  v_account_id uuid;
  v_owner_user_id uuid;
  v_revision bigint;
begin
  select c.account_id, c.owner_user_id into v_account_id, v_owner_user_id from public.syncon_account_context() c;
  if v_account_id is null then raise exception 'Connected SyncOn account required'; end if;

  insert into public.installations(user_id, account_id, installation_id, platform, display_name, client_version, last_seen_at)
  values (v_owner_user_id, v_account_id, p_installation_id, p_platform, p_display_name, p_client_version, now())
  on conflict (user_id, installation_id) do update set
    account_id = excluded.account_id, platform = excluded.platform,
    display_name = coalesce(excluded.display_name, installations.display_name),
    client_version = coalesce(excluded.client_version, installations.client_version), last_seen_at = now()
  returning server_revision into v_revision;

  update public.sync_account_members set last_seen_at = now(), installation_id = coalesce(installation_id, p_installation_id)
  where account_id = v_account_id and auth_user_id = auth.uid() and revoked_at is null;
  return v_revision;
end;
$$;

create or replace function public.sync_push_intervals_v2(p_intervals jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_account_id uuid;
  v_owner_user_id uuid;
  v_accepted integer := 0;
  v_max_revision bigint := 0;
begin
  select c.account_id, c.owner_user_id into v_account_id, v_owner_user_id from public.syncon_account_context() c;
  if v_account_id is null then raise exception 'Connected SyncOn account required'; end if;
  if jsonb_typeof(coalesce(p_intervals, '[]'::jsonb)) <> 'array' then raise exception 'p_intervals must be an array'; end if;

  with payload as (
    select * from jsonb_to_recordset(coalesce(p_intervals, '[]'::jsonb)) as x(
      record_id text, installation_id text, source_platform text, source_type text,
      source_identifier text, usage_date date, start_time_utc timestamptz,
      end_time_utc timestamptz, duration_millis bigint, timezone_id text,
      utc_offset_minutes integer, client_created_at timestamptz,
      client_updated_at timestamptz, local_revision bigint, is_deleted boolean
    )
  ), changed as (
    insert into public.activity_intervals(
      user_id, account_id, record_id, installation_id, source_platform, source_type,
      source_identifier, usage_date, start_time_utc, end_time_utc, duration_millis,
      timezone_id, utc_offset_minutes, client_created_at, client_updated_at, local_revision, is_deleted
    )
    select v_owner_user_id, v_account_id, record_id, installation_id, source_platform, source_type,
      source_identifier, usage_date, start_time_utc, end_time_utc, duration_millis,
      timezone_id, utc_offset_minutes, client_created_at, client_updated_at,
      coalesce(local_revision, 1), coalesce(is_deleted, false)
    from payload
    on conflict (user_id, record_id) do update set
      start_time_utc = excluded.start_time_utc, end_time_utc = excluded.end_time_utc,
      duration_millis = excluded.duration_millis, timezone_id = excluded.timezone_id,
      utc_offset_minutes = excluded.utc_offset_minutes, client_updated_at = excluded.client_updated_at,
      local_revision = excluded.local_revision, is_deleted = excluded.is_deleted
    where excluded.local_revision > activity_intervals.local_revision
       or (excluded.local_revision = activity_intervals.local_revision and excluded.client_updated_at > activity_intervals.client_updated_at)
    returning server_revision
  ) select count(*), coalesce(max(server_revision), 0) into v_accepted, v_max_revision from changed;

  return jsonb_build_object('accepted', v_accepted, 'max_server_revision', v_max_revision);
end;
$$;

create or replace function public.sync_push_state_v2(
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
  v_sources integer := 0;
  v_limits integer := 0;
  v_events integer := 0;
  v_mappings integer := 0;
  v_max_revision bigint := 0;
begin
  select c.account_id, c.owner_user_id into v_account_id, v_owner_user_id
  from public.syncon_account_context() c;
  if v_account_id is null then raise exception 'Connected SyncOn account required'; end if;
  if jsonb_typeof(coalesce(p_sources, '[]'::jsonb)) <> 'array'
     or jsonb_typeof(coalesce(p_limits, '[]'::jsonb)) <> 'array'
     or jsonb_typeof(coalesce(p_block_events, '[]'::jsonb)) <> 'array'
     or jsonb_typeof(coalesce(p_source_mappings, '[]'::jsonb)) <> 'array'
  then
    raise exception 'All sync payloads must be arrays';
  end if;

  with payload as (
    select * from jsonb_to_recordset(coalesce(p_sources, '[]'::jsonb)) as x(
      source_type text, source_identifier text, display_name text, category text,
      is_category_manually_set boolean, client_updated_at timestamptz,
      local_revision bigint, is_deleted boolean
    )
  ), changed as (
    insert into public.source_metadata(
      user_id, account_id, source_type, source_identifier, display_name, category,
      is_category_manually_set, client_updated_at, local_revision, is_deleted
    )
    select v_owner_user_id, v_account_id, source_type, source_identifier, display_name,
      coalesce(category, 'Other'), coalesce(is_category_manually_set, false),
      client_updated_at, coalesce(local_revision, 1), coalesce(is_deleted, false)
    from payload
    on conflict (user_id, source_type, source_identifier) do update set
      account_id = excluded.account_id, display_name = excluded.display_name,
      category = excluded.category,
      is_category_manually_set = excluded.is_category_manually_set,
      client_updated_at = excluded.client_updated_at,
      local_revision = excluded.local_revision, is_deleted = excluded.is_deleted
    where excluded.client_updated_at > source_metadata.client_updated_at
       or (excluded.client_updated_at = source_metadata.client_updated_at
           and excluded.local_revision > source_metadata.local_revision)
    returning server_revision
  )
  select count(*), coalesce(max(server_revision), v_max_revision)
  into v_sources, v_max_revision from changed;

  with payload as (
    select * from jsonb_to_recordset(coalesce(p_limits, '[]'::jsonb)) as x(
      record_id text, installation_id text, target_type text, source_platform text,
      target_identifier text, daily_limit_minutes integer, blocking_style text,
      snooze_minutes integer, is_enabled boolean, client_updated_at timestamptz,
      local_revision bigint, is_deleted boolean
    )
  ), changed as (
    insert into public.limit_settings(
      user_id, account_id, record_id, installation_id, target_type, source_platform,
      target_identifier, daily_limit_minutes, blocking_style, snooze_minutes,
      is_enabled, client_updated_at, local_revision, is_deleted
    )
    select v_owner_user_id, v_account_id, record_id, installation_id, target_type,
      source_platform, target_identifier, daily_limit_minutes,
      coalesce(blocking_style, 'STRICT'), coalesce(snooze_minutes, 5),
      coalesce(is_enabled, true), client_updated_at, coalesce(local_revision, 1),
      coalesce(is_deleted, false)
    from payload
    on conflict (user_id, record_id) do update set
      account_id = excluded.account_id, installation_id = excluded.installation_id,
      target_type = excluded.target_type, source_platform = excluded.source_platform,
      target_identifier = excluded.target_identifier,
      daily_limit_minutes = excluded.daily_limit_minutes,
      blocking_style = excluded.blocking_style, snooze_minutes = excluded.snooze_minutes,
      is_enabled = excluded.is_enabled, client_updated_at = excluded.client_updated_at,
      local_revision = excluded.local_revision, is_deleted = excluded.is_deleted
    where excluded.client_updated_at > limit_settings.client_updated_at
       or (excluded.client_updated_at = limit_settings.client_updated_at
           and excluded.local_revision > limit_settings.local_revision)
    returning server_revision
  )
  select count(*), greatest(v_max_revision, coalesce(max(server_revision), 0))
  into v_limits, v_max_revision from changed;

  with payload as (
    select * from jsonb_to_recordset(coalesce(p_block_events, '[]'::jsonb)) as x(
      record_id text, installation_id text, source_platform text,
      source_identifier text, category text, usage_date date, event_type text,
      event_time_utc timestamptz, extra_minutes integer, local_revision bigint,
      is_deleted boolean
    )
  ), changed as (
    insert into public.block_events(
      user_id, account_id, record_id, installation_id, source_platform,
      source_identifier, category, usage_date, event_type, event_time_utc,
      extra_minutes, local_revision, is_deleted
    )
    select v_owner_user_id, v_account_id, record_id, installation_id, source_platform,
      source_identifier, category, usage_date, event_type, event_time_utc,
      coalesce(extra_minutes, 0), coalesce(local_revision, 1),
      coalesce(is_deleted, false)
    from payload
    on conflict (user_id, record_id) do update set
      account_id = excluded.account_id, local_revision = excluded.local_revision,
      is_deleted = excluded.is_deleted
    where excluded.local_revision > block_events.local_revision
    returning server_revision
  )
  select count(*), greatest(v_max_revision, coalesce(max(server_revision), 0))
  into v_events, v_max_revision from changed;

  with payload as (
    select * from jsonb_to_recordset(coalesce(p_source_mappings, '[]'::jsonb)) as x(
      record_id text, source_type text, source_identifier text,
      logical_service_id text, client_updated_at timestamptz,
      local_revision bigint, is_deleted boolean
    )
  ), changed as (
    insert into public.user_source_mappings(
      user_id, account_id, record_id, source_type, source_identifier,
      logical_service_id, client_updated_at, local_revision, is_deleted
    )
    select v_owner_user_id, v_account_id, record_id, source_type, source_identifier,
      logical_service_id, client_updated_at, coalesce(local_revision, 1),
      coalesce(is_deleted, false)
    from payload
    on conflict (user_id, record_id) do update set
      account_id = excluded.account_id, source_type = excluded.source_type,
      source_identifier = excluded.source_identifier,
      logical_service_id = excluded.logical_service_id,
      client_updated_at = excluded.client_updated_at,
      local_revision = excluded.local_revision, is_deleted = excluded.is_deleted
    where excluded.client_updated_at > user_source_mappings.client_updated_at
       or (excluded.client_updated_at = user_source_mappings.client_updated_at
           and excluded.local_revision > user_source_mappings.local_revision)
    returning server_revision
  )
  select count(*), greatest(v_max_revision, coalesce(max(server_revision), 0))
  into v_mappings, v_max_revision from changed;

  return jsonb_build_object(
    'accepted_sources', v_sources,
    'accepted_limits', v_limits,
    'accepted_block_events', v_events,
    'accepted_source_mappings', v_mappings,
    'max_server_revision', v_max_revision
  );
end;
$$;

create or replace function public.sync_pull_v2(p_after_revision bigint default 0, p_limit integer default 1000)
returns jsonb
language plpgsql
security definer
stable
set search_path = public
as $$
declare
  v_account_id uuid;
  v_cutoff bigint;
  v_has_more boolean;
begin
  select c.account_id into v_account_id from public.syncon_account_context() c;
  if v_account_id is null then raise exception 'Connected SyncOn account required'; end if;
  p_limit := greatest(1, least(coalesce(p_limit, 1000), 5000));

  select coalesce(max(server_revision), p_after_revision) into v_cutoff from (
    select server_revision from (
      select server_revision from public.installations where account_id = v_account_id and server_revision > p_after_revision
      union all select server_revision from public.activity_intervals where account_id = v_account_id and server_revision > p_after_revision
      union all select server_revision from public.source_metadata where account_id = v_account_id and server_revision > p_after_revision
      union all select server_revision from public.limit_settings where account_id = v_account_id and server_revision > p_after_revision
      union all select server_revision from public.block_events where account_id = v_account_id and server_revision > p_after_revision
      union all select server_revision from public.user_source_mappings where account_id = v_account_id and server_revision > p_after_revision
    ) revisions order by server_revision limit p_limit
  ) page;

  select exists(select 1 from (
    select server_revision from public.installations where account_id = v_account_id
    union all select server_revision from public.activity_intervals where account_id = v_account_id
    union all select server_revision from public.source_metadata where account_id = v_account_id
    union all select server_revision from public.limit_settings where account_id = v_account_id
    union all select server_revision from public.block_events where account_id = v_account_id
    union all select server_revision from public.user_source_mappings where account_id = v_account_id
  ) revisions where server_revision > v_cutoff) into v_has_more;

  return jsonb_build_object(
    'after_revision', p_after_revision, 'next_revision', v_cutoff, 'has_more', v_has_more,
    'installations', coalesce((select jsonb_agg(to_jsonb(t) order by server_revision) from public.installations t where account_id = v_account_id and server_revision > p_after_revision and server_revision <= v_cutoff), '[]'::jsonb),
    'intervals', coalesce((select jsonb_agg(to_jsonb(t) order by server_revision) from public.activity_intervals t where account_id = v_account_id and server_revision > p_after_revision and server_revision <= v_cutoff), '[]'::jsonb),
    'sources', coalesce((select jsonb_agg(to_jsonb(t) order by server_revision) from public.source_metadata t where account_id = v_account_id and server_revision > p_after_revision and server_revision <= v_cutoff), '[]'::jsonb),
    'limits', coalesce((select jsonb_agg(to_jsonb(t) order by server_revision) from public.limit_settings t where account_id = v_account_id and server_revision > p_after_revision and server_revision <= v_cutoff), '[]'::jsonb),
    'block_events', coalesce((select jsonb_agg(to_jsonb(t) order by server_revision) from public.block_events t where account_id = v_account_id and server_revision > p_after_revision and server_revision <= v_cutoff), '[]'::jsonb),
    'source_mappings', coalesce((select jsonb_agg(to_jsonb(t) order by server_revision) from public.user_source_mappings t where account_id = v_account_id and server_revision > p_after_revision and server_revision <= v_cutoff), '[]'::jsonb)
  );
end;
$$;

create or replace function public.list_connected_installations_v2()
returns table(installation_id text, platform text, display_name text, client_version text, last_seen_at timestamptz, is_current boolean)
language sql
security definer
stable
set search_path = public
as $$
  select i.installation_id, i.platform, i.display_name, i.client_version, i.last_seen_at,
    exists(select 1 from public.sync_account_members m where m.account_id = i.account_id and m.auth_user_id = auth.uid() and m.installation_id = i.installation_id and m.revoked_at is null)
  from public.installations i
  join public.syncon_account_context() c on c.account_id = i.account_id
  order by i.last_seen_at desc;
$$;

create or replace function public.account_usage_summary_v2(
  p_from_date date,
  p_to_date date
)
returns table(usage_date date, summed_device_millis bigint, active_digital_span_millis bigint)
language sql
security definer
stable
set search_path = public
as $$
  with account_context as (
    select c.account_id from public.syncon_account_context() c
  ), filtered as (
    select i.usage_date, i.start_time_utc, i.end_time_utc, i.duration_millis
    from public.activity_intervals i
    join account_context c using (account_id)
    where not i.is_deleted and i.usage_date between p_from_date and p_to_date
  ), marked as (
    select *, case when start_time_utc > coalesce(
      max(end_time_utc) over (
        partition by usage_date order by start_time_utc, end_time_utc
        rows between unbounded preceding and 1 preceding
      ), '-infinity'::timestamptz
    ) then 1 else 0 end as starts_island
    from filtered
  ), grouped as (
    select *, sum(starts_island) over (
      partition by usage_date order by start_time_utc, end_time_utc
      rows unbounded preceding
    ) as island_id
    from marked
  ), islands as (
    select usage_date, island_id, min(start_time_utc) as island_start,
      max(end_time_utc) as island_end
    from grouped group by usage_date, island_id
  ), summed as (
    select usage_date, sum(duration_millis)::bigint as summed_device_millis
    from filtered group by usage_date
  ), spans as (
    select usage_date,
      (sum(extract(epoch from (island_end - island_start))) * 1000)::bigint
        as active_digital_span_millis
    from islands group by usage_date
  )
  select summed.usage_date, summed.summed_device_millis,
    coalesce(spans.active_digital_span_millis, 0)
  from summed left join spans using (usage_date)
  order by summed.usage_date;
$$;

create or replace function public.revoke_installation_v2(p_installation_id text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_account_id uuid;
  v_count integer;
begin
  select a.account_id into v_account_id from public.sync_accounts a where a.owner_user_id = auth.uid();
  if v_account_id is null then raise exception 'Account owner required'; end if;
  update public.sync_account_members set revoked_at = now()
  where account_id = v_account_id and installation_id = p_installation_id and member_type <> 'OWNER' and revoked_at is null;
  get diagnostics v_count = row_count;
  return v_count > 0;
end;
$$;

grant select, update on public.sync_accounts to authenticated;
grant select on public.sync_account_members to authenticated;
grant select on public.pairing_requests to authenticated;
revoke all on public.sync_accounts, public.sync_account_members, public.pairing_requests from anon;
revoke execute on function public.syncon_can_access_account(uuid) from public, anon;
revoke execute on function public.syncon_can_manage_account(uuid) from public, anon;
revoke execute on function public.syncon_account_context() from public, anon;
revoke execute on function public.create_pairing_request_v2(text, text, text, text) from public, anon;
revoke execute on function public.get_pairing_status_v2(uuid) from public, anon;
revoke execute on function public.claim_pairing_request_v2(uuid, text, text) from public, anon;
revoke execute on function public.sync_register_installation_v2(text, text, text, text) from public, anon;
revoke execute on function public.sync_push_intervals_v2(jsonb) from public, anon;
revoke execute on function public.sync_push_state_v2(jsonb, jsonb, jsonb, jsonb) from public, anon;
revoke execute on function public.sync_pull_v2(bigint, integer) from public, anon;
revoke execute on function public.list_connected_installations_v2() from public, anon;
revoke execute on function public.account_usage_summary_v2(date, date) from public, anon;
revoke execute on function public.revoke_installation_v2(text) from public, anon;
grant execute on function public.syncon_can_access_account(uuid) to authenticated;
grant execute on function public.syncon_can_manage_account(uuid) to authenticated;
grant execute on function public.syncon_account_context() to authenticated;
grant execute on function public.create_pairing_request_v2(text, text, text, text) to authenticated;
grant execute on function public.get_pairing_status_v2(uuid) to authenticated;
grant execute on function public.claim_pairing_request_v2(uuid, text, text) to authenticated;
grant execute on function public.sync_register_installation_v2(text, text, text, text) to authenticated;
grant execute on function public.sync_push_intervals_v2(jsonb) to authenticated;
grant execute on function public.sync_push_state_v2(jsonb, jsonb, jsonb, jsonb) to authenticated;
grant execute on function public.sync_pull_v2(bigint, integer) to authenticated;
grant execute on function public.list_connected_installations_v2() to authenticated;
grant execute on function public.account_usage_summary_v2(date, date) to authenticated;
grant execute on function public.revoke_installation_v2(text) to authenticated;

commit;
