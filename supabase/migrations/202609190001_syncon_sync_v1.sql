begin;

create sequence if not exists public.syncon_server_revision_seq as bigint;

create or replace function public.syncon_touch_revision()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.server_revision := nextval('public.syncon_server_revision_seq');
  new.updated_at := now();
  return new;
end;
$$;

create table if not exists public.profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.installations (
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  installation_id text not null check (length(installation_id) between 8 and 200),
  platform text not null check (platform in ('ANDROID', 'CHROME')),
  display_name text,
  client_version text,
  last_seen_at timestamptz not null default now(),
  server_revision bigint not null default nextval('public.syncon_server_revision_seq'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, installation_id)
);

create table if not exists public.activity_intervals (
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  record_id text not null check (length(record_id) between 1 and 200),
  installation_id text not null,
  source_platform text not null check (source_platform in ('ANDROID', 'CHROME')),
  source_type text not null check (source_type in ('ANDROID_APP', 'CHROME_DOMAIN')),
  source_identifier text not null check (length(source_identifier) between 1 and 255),
  usage_date date not null,
  start_time_utc timestamptz not null,
  end_time_utc timestamptz not null,
  duration_millis bigint not null check (duration_millis > 0 and duration_millis <= 86400000),
  timezone_id text not null,
  utc_offset_minutes integer not null check (utc_offset_minutes between -1080 and 1080),
  client_created_at timestamptz not null,
  client_updated_at timestamptz not null,
  local_revision bigint not null default 1 check (local_revision > 0),
  server_revision bigint not null default nextval('public.syncon_server_revision_seq'),
  is_deleted boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, record_id),
  foreign key (user_id, installation_id)
    references public.installations(user_id, installation_id) on delete cascade,
  check (end_time_utc > start_time_utc),
  check (abs(duration_millis - (extract(epoch from (end_time_utc - start_time_utc)) * 1000)::bigint) <= 1000)
);

create table if not exists public.source_metadata (
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  source_type text not null check (source_type in ('ANDROID_APP', 'CHROME_DOMAIN')),
  source_identifier text not null check (length(source_identifier) between 1 and 255),
  display_name text,
  category text not null default 'Other',
  is_category_manually_set boolean not null default false,
  client_updated_at timestamptz not null,
  local_revision bigint not null default 1 check (local_revision > 0),
  server_revision bigint not null default nextval('public.syncon_server_revision_seq'),
  is_deleted boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, source_type, source_identifier)
);

create table if not exists public.limit_settings (
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  record_id text not null check (length(record_id) between 1 and 200),
  installation_id text not null,
  target_type text not null check (target_type in ('SOURCE', 'CATEGORY', 'LOGICAL_SERVICE', 'ACCOUNT')),
  source_platform text check (source_platform is null or source_platform in ('ANDROID', 'CHROME')),
  target_identifier text not null check (length(target_identifier) between 1 and 255),
  daily_limit_minutes integer check (daily_limit_minutes is null or daily_limit_minutes between 1 and 1440),
  blocking_style text not null check (blocking_style in ('STRICT', 'SOFT')),
  snooze_minutes integer not null default 5 check (snooze_minutes between 1 and 120),
  is_enabled boolean not null default true,
  client_updated_at timestamptz not null,
  local_revision bigint not null default 1 check (local_revision > 0),
  server_revision bigint not null default nextval('public.syncon_server_revision_seq'),
  is_deleted boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, record_id),
  foreign key (user_id, installation_id)
    references public.installations(user_id, installation_id) on delete cascade
);

create table if not exists public.block_events (
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  record_id text not null check (length(record_id) between 1 and 200),
  installation_id text not null,
  source_platform text not null check (source_platform in ('ANDROID', 'CHROME')),
  source_identifier text not null check (length(source_identifier) between 1 and 255),
  category text,
  usage_date date not null,
  event_type text not null check (event_type in ('BLOCKED', 'SNOOZED', 'DISMISSED', 'WARNING')),
  event_time_utc timestamptz not null,
  extra_minutes integer not null default 0 check (extra_minutes between 0 and 1440),
  local_revision bigint not null default 1 check (local_revision > 0),
  server_revision bigint not null default nextval('public.syncon_server_revision_seq'),
  is_deleted boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, record_id),
  foreign key (user_id, installation_id)
    references public.installations(user_id, installation_id) on delete cascade
);

create table if not exists public.user_source_mappings (
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  record_id text not null check (length(record_id) between 1 and 200),
  source_type text not null check (source_type in ('ANDROID_APP', 'CHROME_DOMAIN')),
  source_identifier text not null check (length(source_identifier) between 1 and 255),
  logical_service_id text not null check (length(logical_service_id) between 1 and 100),
  client_updated_at timestamptz not null,
  local_revision bigint not null default 1 check (local_revision > 0),
  server_revision bigint not null default nextval('public.syncon_server_revision_seq'),
  is_deleted boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, record_id),
  unique (user_id, source_type, source_identifier)
);

create or replace function public.syncon_create_profile()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles(user_id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data ->> 'display_name', split_part(coalesce(new.email, ''), '@', 1)))
  on conflict (user_id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created_syncon on auth.users;
create trigger on_auth_user_created_syncon
after insert on auth.users
for each row execute function public.syncon_create_profile();

create index if not exists activity_intervals_user_revision_idx
  on public.activity_intervals(user_id, server_revision);
create index if not exists activity_intervals_user_date_idx
  on public.activity_intervals(user_id, usage_date);
create index if not exists activity_intervals_user_source_idx
  on public.activity_intervals(user_id, source_type, source_identifier, usage_date);
create index if not exists limit_settings_user_revision_idx
  on public.limit_settings(user_id, server_revision);
create index if not exists source_metadata_user_revision_idx
  on public.source_metadata(user_id, server_revision);
create index if not exists block_events_user_revision_idx
  on public.block_events(user_id, server_revision);
create index if not exists user_source_mappings_user_revision_idx
  on public.user_source_mappings(user_id, server_revision);

drop trigger if exists installations_touch_revision on public.installations;
create trigger installations_touch_revision before insert or update on public.installations
for each row execute function public.syncon_touch_revision();
drop trigger if exists activity_intervals_touch_revision on public.activity_intervals;
create trigger activity_intervals_touch_revision before insert or update on public.activity_intervals
for each row execute function public.syncon_touch_revision();
drop trigger if exists source_metadata_touch_revision on public.source_metadata;
create trigger source_metadata_touch_revision before insert or update on public.source_metadata
for each row execute function public.syncon_touch_revision();
drop trigger if exists limit_settings_touch_revision on public.limit_settings;
create trigger limit_settings_touch_revision before insert or update on public.limit_settings
for each row execute function public.syncon_touch_revision();
drop trigger if exists block_events_touch_revision on public.block_events;
create trigger block_events_touch_revision before insert or update on public.block_events
for each row execute function public.syncon_touch_revision();
drop trigger if exists user_source_mappings_touch_revision on public.user_source_mappings;
create trigger user_source_mappings_touch_revision before insert or update on public.user_source_mappings
for each row execute function public.syncon_touch_revision();

alter table public.profiles enable row level security;
alter table public.installations enable row level security;
alter table public.activity_intervals enable row level security;
alter table public.source_metadata enable row level security;
alter table public.limit_settings enable row level security;
alter table public.block_events enable row level security;
alter table public.user_source_mappings enable row level security;

create policy "profiles_select_own" on public.profiles for select using (auth.uid() = user_id);
create policy "profiles_insert_own" on public.profiles for insert with check (auth.uid() = user_id);
create policy "profiles_update_own" on public.profiles for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "installations_select_own" on public.installations for select using (auth.uid() = user_id);
create policy "installations_insert_own" on public.installations for insert with check (auth.uid() = user_id);
create policy "installations_update_own" on public.installations for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "installations_delete_own" on public.installations for delete using (auth.uid() = user_id);

create policy "activity_intervals_select_own" on public.activity_intervals for select using (auth.uid() = user_id);
create policy "activity_intervals_insert_own" on public.activity_intervals for insert with check (auth.uid() = user_id);
create policy "activity_intervals_update_own" on public.activity_intervals for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "source_metadata_select_own" on public.source_metadata for select using (auth.uid() = user_id);
create policy "source_metadata_insert_own" on public.source_metadata for insert with check (auth.uid() = user_id);
create policy "source_metadata_update_own" on public.source_metadata for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "limit_settings_select_own" on public.limit_settings for select using (auth.uid() = user_id);
create policy "limit_settings_insert_own" on public.limit_settings for insert with check (auth.uid() = user_id);
create policy "limit_settings_update_own" on public.limit_settings for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "block_events_select_own" on public.block_events for select using (auth.uid() = user_id);
create policy "block_events_insert_own" on public.block_events for insert with check (auth.uid() = user_id);
create policy "block_events_update_own" on public.block_events for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "user_source_mappings_select_own" on public.user_source_mappings for select using (auth.uid() = user_id);
create policy "user_source_mappings_insert_own" on public.user_source_mappings for insert with check (auth.uid() = user_id);
create policy "user_source_mappings_update_own" on public.user_source_mappings for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

create or replace function public.sync_register_installation_v1(
  p_installation_id text,
  p_platform text,
  p_display_name text default null,
  p_client_version text default null
)
returns bigint
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_revision bigint;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;

  insert into public.installations(user_id, installation_id, platform, display_name, client_version, last_seen_at)
  values (auth.uid(), p_installation_id, p_platform, p_display_name, p_client_version, now())
  on conflict (user_id, installation_id) do update
    set platform = excluded.platform,
        display_name = coalesce(excluded.display_name, installations.display_name),
        client_version = coalesce(excluded.client_version, installations.client_version),
        last_seen_at = now()
  returning server_revision into v_revision;

  return v_revision;
end;
$$;

create or replace function public.sync_push_intervals_v1(p_intervals jsonb)
returns jsonb
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_accepted integer := 0;
  v_max_revision bigint := 0;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if jsonb_typeof(coalesce(p_intervals, '[]'::jsonb)) <> 'array' then raise exception 'p_intervals must be an array'; end if;

  with payload as (
    select * from jsonb_to_recordset(coalesce(p_intervals, '[]'::jsonb)) as x(
      record_id text,
      installation_id text,
      source_platform text,
      source_type text,
      source_identifier text,
      usage_date date,
      start_time_utc timestamptz,
      end_time_utc timestamptz,
      duration_millis bigint,
      timezone_id text,
      utc_offset_minutes integer,
      client_created_at timestamptz,
      client_updated_at timestamptz,
      local_revision bigint,
      is_deleted boolean
    )
  ), changed as (
    insert into public.activity_intervals(
      user_id, record_id, installation_id, source_platform, source_type, source_identifier,
      usage_date, start_time_utc, end_time_utc, duration_millis, timezone_id,
      utc_offset_minutes, client_created_at, client_updated_at, local_revision, is_deleted
    )
    select auth.uid(), record_id, installation_id, source_platform, source_type, source_identifier,
      usage_date, start_time_utc, end_time_utc, duration_millis, timezone_id,
      utc_offset_minutes, client_created_at, client_updated_at, coalesce(local_revision, 1), coalesce(is_deleted, false)
    from payload
    on conflict (user_id, record_id) do update set
      start_time_utc = excluded.start_time_utc,
      end_time_utc = excluded.end_time_utc,
      duration_millis = excluded.duration_millis,
      timezone_id = excluded.timezone_id,
      utc_offset_minutes = excluded.utc_offset_minutes,
      client_updated_at = excluded.client_updated_at,
      local_revision = excluded.local_revision,
      is_deleted = excluded.is_deleted
    where excluded.local_revision > activity_intervals.local_revision
       or (excluded.local_revision = activity_intervals.local_revision
           and excluded.client_updated_at > activity_intervals.client_updated_at)
    returning server_revision
  )
  select count(*), coalesce(max(server_revision), 0) into v_accepted, v_max_revision from changed;

  return jsonb_build_object('accepted', v_accepted, 'max_server_revision', v_max_revision);
end;
$$;

create or replace function public.sync_push_state_v1(
  p_sources jsonb default '[]'::jsonb,
  p_limits jsonb default '[]'::jsonb,
  p_block_events jsonb default '[]'::jsonb,
  p_source_mappings jsonb default '[]'::jsonb
)
returns jsonb
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_sources integer := 0;
  v_limits integer := 0;
  v_events integer := 0;
  v_mappings integer := 0;
  v_max_revision bigint := 0;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if jsonb_typeof(coalesce(p_sources, '[]'::jsonb)) <> 'array'
     or jsonb_typeof(coalesce(p_limits, '[]'::jsonb)) <> 'array'
     or jsonb_typeof(coalesce(p_block_events, '[]'::jsonb)) <> 'array'
     or jsonb_typeof(coalesce(p_source_mappings, '[]'::jsonb)) <> 'array'
  then
    raise exception 'All sync payloads must be arrays';
  end if;

  with payload as (
    select * from jsonb_to_recordset(coalesce(p_sources, '[]'::jsonb)) as x(
      source_type text,
      source_identifier text,
      display_name text,
      category text,
      is_category_manually_set boolean,
      client_updated_at timestamptz,
      local_revision bigint,
      is_deleted boolean
    )
  ), changed as (
    insert into public.source_metadata(
      user_id, source_type, source_identifier, display_name, category,
      is_category_manually_set, client_updated_at, local_revision, is_deleted
    )
    select auth.uid(), source_type, source_identifier, display_name, coalesce(category, 'Other'),
      coalesce(is_category_manually_set, false), client_updated_at,
      coalesce(local_revision, 1), coalesce(is_deleted, false)
    from payload
    on conflict (user_id, source_type, source_identifier) do update set
      display_name = excluded.display_name,
      category = excluded.category,
      is_category_manually_set = excluded.is_category_manually_set,
      client_updated_at = excluded.client_updated_at,
      local_revision = excluded.local_revision,
      is_deleted = excluded.is_deleted
    where excluded.client_updated_at > source_metadata.client_updated_at
       or (excluded.client_updated_at = source_metadata.client_updated_at
           and excluded.local_revision > source_metadata.local_revision)
    returning server_revision
  )
  select count(*), coalesce(max(server_revision), v_max_revision)
  into v_sources, v_max_revision from changed;

  with payload as (
    select * from jsonb_to_recordset(coalesce(p_limits, '[]'::jsonb)) as x(
      record_id text,
      installation_id text,
      target_type text,
      source_platform text,
      target_identifier text,
      daily_limit_minutes integer,
      blocking_style text,
      snooze_minutes integer,
      is_enabled boolean,
      client_updated_at timestamptz,
      local_revision bigint,
      is_deleted boolean
    )
  ), changed as (
    insert into public.limit_settings(
      user_id, record_id, installation_id, target_type, source_platform,
      target_identifier, daily_limit_minutes, blocking_style, snooze_minutes,
      is_enabled, client_updated_at, local_revision, is_deleted
    )
    select auth.uid(), record_id, installation_id, target_type, source_platform,
      target_identifier, daily_limit_minutes, coalesce(blocking_style, 'STRICT'),
      coalesce(snooze_minutes, 5), coalesce(is_enabled, true), client_updated_at,
      coalesce(local_revision, 1), coalesce(is_deleted, false)
    from payload
    on conflict (user_id, record_id) do update set
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
    where excluded.client_updated_at > limit_settings.client_updated_at
       or (excluded.client_updated_at = limit_settings.client_updated_at
           and excluded.local_revision > limit_settings.local_revision)
    returning server_revision
  )
  select count(*), greatest(v_max_revision, coalesce(max(server_revision), 0))
  into v_limits, v_max_revision from changed;

  with payload as (
    select * from jsonb_to_recordset(coalesce(p_block_events, '[]'::jsonb)) as x(
      record_id text,
      installation_id text,
      source_platform text,
      source_identifier text,
      category text,
      usage_date date,
      event_type text,
      event_time_utc timestamptz,
      extra_minutes integer,
      local_revision bigint,
      is_deleted boolean
    )
  ), changed as (
    insert into public.block_events(
      user_id, record_id, installation_id, source_platform, source_identifier,
      category, usage_date, event_type, event_time_utc, extra_minutes,
      local_revision, is_deleted
    )
    select auth.uid(), record_id, installation_id, source_platform, source_identifier,
      category, usage_date, event_type, event_time_utc, coalesce(extra_minutes, 0),
      coalesce(local_revision, 1), coalesce(is_deleted, false)
    from payload
    on conflict (user_id, record_id) do update set
      local_revision = excluded.local_revision,
      is_deleted = excluded.is_deleted
    where excluded.local_revision > block_events.local_revision
    returning server_revision
  )
  select count(*), greatest(v_max_revision, coalesce(max(server_revision), 0))
  into v_events, v_max_revision from changed;

  with payload as (
    select * from jsonb_to_recordset(coalesce(p_source_mappings, '[]'::jsonb)) as x(
      record_id text,
      source_type text,
      source_identifier text,
      logical_service_id text,
      client_updated_at timestamptz,
      local_revision bigint,
      is_deleted boolean
    )
  ), changed as (
    insert into public.user_source_mappings(
      user_id, record_id, source_type, source_identifier, logical_service_id,
      client_updated_at, local_revision, is_deleted
    )
    select auth.uid(), record_id, source_type, source_identifier, logical_service_id,
      client_updated_at, coalesce(local_revision, 1), coalesce(is_deleted, false)
    from payload
    on conflict (user_id, record_id) do update set
      source_type = excluded.source_type,
      source_identifier = excluded.source_identifier,
      logical_service_id = excluded.logical_service_id,
      client_updated_at = excluded.client_updated_at,
      local_revision = excluded.local_revision,
      is_deleted = excluded.is_deleted
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

create or replace function public.sync_pull_v1(
  p_after_revision bigint default 0,
  p_limit integer default 1000
)
returns jsonb
language plpgsql
security invoker
stable
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_cutoff bigint;
  v_has_more boolean;
begin
  if v_user_id is null then raise exception 'Authentication required'; end if;
  p_limit := greatest(1, least(coalesce(p_limit, 1000), 5000));

  select coalesce(max(server_revision), p_after_revision) into v_cutoff
  from (
    select server_revision from (
      select server_revision from public.installations where user_id = v_user_id and server_revision > p_after_revision
      union all select server_revision from public.activity_intervals where user_id = v_user_id and server_revision > p_after_revision
      union all select server_revision from public.source_metadata where user_id = v_user_id and server_revision > p_after_revision
      union all select server_revision from public.limit_settings where user_id = v_user_id and server_revision > p_after_revision
      union all select server_revision from public.block_events where user_id = v_user_id and server_revision > p_after_revision
      union all select server_revision from public.user_source_mappings where user_id = v_user_id and server_revision > p_after_revision
    ) revisions
    order by server_revision
    limit p_limit
  ) page;

  select exists(
    select 1 from (
      select server_revision from public.installations where user_id = v_user_id
      union all select server_revision from public.activity_intervals where user_id = v_user_id
      union all select server_revision from public.source_metadata where user_id = v_user_id
      union all select server_revision from public.limit_settings where user_id = v_user_id
      union all select server_revision from public.block_events where user_id = v_user_id
      union all select server_revision from public.user_source_mappings where user_id = v_user_id
    ) revisions where server_revision > v_cutoff
  ) into v_has_more;

  return jsonb_build_object(
    'after_revision', p_after_revision,
    'next_revision', v_cutoff,
    'has_more', v_has_more,
    'installations', coalesce((select jsonb_agg(to_jsonb(t) order by server_revision) from public.installations t where user_id = v_user_id and server_revision > p_after_revision and server_revision <= v_cutoff), '[]'::jsonb),
    'intervals', coalesce((select jsonb_agg(to_jsonb(t) order by server_revision) from public.activity_intervals t where user_id = v_user_id and server_revision > p_after_revision and server_revision <= v_cutoff), '[]'::jsonb),
    'sources', coalesce((select jsonb_agg(to_jsonb(t) order by server_revision) from public.source_metadata t where user_id = v_user_id and server_revision > p_after_revision and server_revision <= v_cutoff), '[]'::jsonb),
    'limits', coalesce((select jsonb_agg(to_jsonb(t) order by server_revision) from public.limit_settings t where user_id = v_user_id and server_revision > p_after_revision and server_revision <= v_cutoff), '[]'::jsonb),
    'block_events', coalesce((select jsonb_agg(to_jsonb(t) order by server_revision) from public.block_events t where user_id = v_user_id and server_revision > p_after_revision and server_revision <= v_cutoff), '[]'::jsonb),
    'source_mappings', coalesce((select jsonb_agg(to_jsonb(t) order by server_revision) from public.user_source_mappings t where user_id = v_user_id and server_revision > p_after_revision and server_revision <= v_cutoff), '[]'::jsonb)
  );
end;
$$;

create or replace function public.account_usage_summary_v1(
  p_from_date date,
  p_to_date date
)
returns table(usage_date date, summed_device_millis bigint, active_digital_span_millis bigint)
language sql
security invoker
stable
set search_path = public
as $$
  with filtered as (
    select i.usage_date, i.start_time_utc, i.end_time_utc, i.duration_millis
    from public.activity_intervals i
    where i.user_id = auth.uid()
      and not i.is_deleted
      and i.usage_date between p_from_date and p_to_date
  ), marked as (
    select *,
      case when start_time_utc > coalesce(
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
    select usage_date, island_id, min(start_time_utc) as island_start, max(end_time_utc) as island_end
    from grouped
    group by usage_date, island_id
  ), summed as (
    select usage_date, sum(duration_millis)::bigint as summed_device_millis
    from filtered group by usage_date
  ), spans as (
    select usage_date, (sum(extract(epoch from (island_end - island_start))) * 1000)::bigint as active_digital_span_millis
    from islands group by usage_date
  )
  select summed.usage_date, summed.summed_device_millis, coalesce(spans.active_digital_span_millis, 0)
  from summed left join spans using (usage_date)
  order by summed.usage_date;
$$;

grant select, insert, update, delete on public.profiles to authenticated;
grant select, insert, update, delete on public.installations to authenticated;
grant select, insert, update on public.activity_intervals to authenticated;
grant select, insert, update on public.source_metadata to authenticated;
grant select, insert, update on public.limit_settings to authenticated;
grant select, insert, update on public.block_events to authenticated;
grant select, insert, update on public.user_source_mappings to authenticated;
grant usage, select on sequence public.syncon_server_revision_seq to authenticated;
revoke execute on function public.sync_register_installation_v1(text, text, text, text) from public, anon;
revoke execute on function public.sync_push_intervals_v1(jsonb) from public, anon;
revoke execute on function public.sync_push_state_v1(jsonb, jsonb, jsonb, jsonb) from public, anon;
revoke execute on function public.sync_pull_v1(bigint, integer) from public, anon;
revoke execute on function public.account_usage_summary_v1(date, date) from public, anon;
grant execute on function public.sync_register_installation_v1(text, text, text, text) to authenticated;
grant execute on function public.sync_push_intervals_v1(jsonb) to authenticated;
grant execute on function public.sync_push_state_v1(jsonb, jsonb, jsonb, jsonb) to authenticated;
grant execute on function public.sync_pull_v1(bigint, integer) to authenticated;
grant execute on function public.account_usage_summary_v1(date, date) to authenticated;

alter table public.activity_intervals replica identity full;
alter table public.source_metadata replica identity full;
alter table public.limit_settings replica identity full;
alter table public.block_events replica identity full;
alter table public.user_source_mappings replica identity full;

do $$
declare
  v_table text;
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    foreach v_table in array array[
      'activity_intervals', 'source_metadata', 'limit_settings',
      'block_events', 'user_source_mappings'
    ] loop
      if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = v_table
      ) then
        execute format('alter publication supabase_realtime add table public.%I', v_table);
      end if;
    end loop;
  end if;
end;
$$;

commit;
