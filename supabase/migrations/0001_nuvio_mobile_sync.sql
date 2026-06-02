-- NuvioMobile fork Supabase backend starter.
-- Run this in a fresh Supabase project. It recreates the app-facing tables,
-- RLS policies, and RPC contracts used by the mobile client.

create extension if not exists pgcrypto;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create table if not exists public.profiles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid(),
  profile_index int not null check (profile_index between 1 and 4),
  name text not null default '',
  avatar_color_hex text not null default '#1E88E5',
  avatar_id text,
  avatar_url text,
  uses_primary_addons boolean not null default false,
  uses_primary_plugins boolean not null default false,
  pin_enabled boolean not null default false,
  pin_hash text,
  pin_locked_until timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, profile_index)
);

create table if not exists public.addons (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid(),
  profile_id int not null,
  url text not null,
  name text,
  enabled boolean not null default true,
  sort_order int not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, profile_id, url)
);

create table if not exists public.plugins (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid(),
  profile_id int not null,
  url text not null,
  name text,
  enabled boolean not null default true,
  sort_order int not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, profile_id, url)
);

create table if not exists public.library_items (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid(),
  profile_id int not null,
  content_id text not null,
  content_type text not null,
  name text not null default '',
  poster text,
  poster_shape text not null default 'POSTER',
  background text,
  description text,
  release_info text,
  imdb_rating real,
  genres text[] not null default '{}',
  addon_base_url text,
  added_at bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, profile_id, content_type, content_id)
);

create table if not exists public.watch_progress (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid(),
  profile_id int not null,
  progress_key text not null,
  content_id text not null,
  content_type text not null,
  video_id text not null,
  season int,
  episode int,
  position bigint not null default 0,
  duration bigint not null default 0,
  last_watched bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, profile_id, progress_key),
  unique (user_id, profile_id, video_id)
);

create table if not exists public.watch_progress_events (
  event_id bigserial primary key,
  user_id uuid not null,
  profile_id int not null,
  operation text not null check (operation in ('upsert', 'delete')),
  progress_key text not null,
  content_id text not null default '',
  content_type text not null default '',
  video_id text not null default '',
  season int,
  episode int,
  position bigint not null default 0,
  duration bigint not null default 0,
  last_watched bigint not null default 0,
  created_at timestamptz not null default now()
);

create table if not exists public.watched_items (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid(),
  profile_id int not null,
  watched_key text not null,
  content_id text not null,
  content_type text not null,
  title text not null default '',
  season int,
  episode int,
  watched_at bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, profile_id, watched_key)
);

create table if not exists public.watched_item_events (
  event_id bigserial primary key,
  user_id uuid not null,
  profile_id int not null,
  operation text not null check (operation in ('upsert', 'delete')),
  watched_key text not null,
  content_id text not null default '',
  content_type text not null default '',
  title text not null default '',
  season int,
  episode int,
  watched_at bigint not null default 0,
  created_at timestamptz not null default now()
);

create table if not exists public.profile_settings_blobs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid(),
  profile_id int not null,
  platform text not null default 'mobile',
  settings_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, profile_id, platform)
);

create table if not exists public.home_catalog_settings (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid(),
  profile_id int not null,
  platform text not null default 'mobile',
  settings_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, profile_id, platform)
);

create table if not exists public.collections (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid(),
  profile_id int not null,
  collections_json jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, profile_id)
);

create table if not exists public.avatar_catalog (
  id text primary key,
  display_name text not null default '',
  storage_path text not null default '',
  category text not null default 'character',
  sort_order int not null default 0,
  is_active boolean not null default true,
  bg_color text
);

do $$
declare
  table_name text;
begin
  foreach table_name in array array[
    'profiles',
    'addons',
    'plugins',
    'library_items',
    'watch_progress',
    'watched_items',
    'profile_settings_blobs',
    'home_catalog_settings',
    'collections'
  ] loop
    execute format('drop trigger if exists set_updated_at on public.%I', table_name);
    execute format(
      'create trigger set_updated_at before update on public.%I for each row execute function public.set_updated_at()',
      table_name
    );
  end loop;
end;
$$;

alter table public.profiles enable row level security;
alter table public.addons enable row level security;
alter table public.plugins enable row level security;
alter table public.library_items enable row level security;
alter table public.watch_progress enable row level security;
alter table public.watch_progress_events enable row level security;
alter table public.watched_items enable row level security;
alter table public.watched_item_events enable row level security;
alter table public.profile_settings_blobs enable row level security;
alter table public.home_catalog_settings enable row level security;
alter table public.collections enable row level security;
alter table public.avatar_catalog enable row level security;

do $$
declare
  table_name text;
begin
  foreach table_name in array array[
    'profiles',
    'addons',
    'plugins',
    'library_items',
    'watch_progress',
    'watch_progress_events',
    'watched_items',
    'watched_item_events',
    'profile_settings_blobs',
    'home_catalog_settings',
    'collections'
  ] loop
    execute format('drop policy if exists "%I owner select" on public.%I', table_name, table_name);
    execute format('drop policy if exists "%I owner insert" on public.%I', table_name, table_name);
    execute format('drop policy if exists "%I owner update" on public.%I', table_name, table_name);
    execute format('drop policy if exists "%I owner delete" on public.%I', table_name, table_name);
    execute format('create policy "%I owner select" on public.%I for select using (user_id = auth.uid())', table_name, table_name);
    execute format('create policy "%I owner insert" on public.%I for insert with check (user_id = auth.uid())', table_name, table_name);
    execute format('create policy "%I owner update" on public.%I for update using (user_id = auth.uid()) with check (user_id = auth.uid())', table_name, table_name);
    execute format('create policy "%I owner delete" on public.%I for delete using (user_id = auth.uid())', table_name, table_name);
  end loop;
end;
$$;

drop policy if exists "avatar catalog public read" on public.avatar_catalog;
create policy "avatar catalog public read" on public.avatar_catalog for select using (is_active);

create or replace function public.sync_pull_profiles()
returns setof public.profiles
language plpgsql
security invoker
as $$
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;

  insert into public.profiles (user_id, profile_index, name, avatar_color_hex)
  values (auth.uid(), 1, 'Profile 1', '#1E88E5')
  on conflict (user_id, profile_index) do nothing;

  return query
    select *
    from public.profiles
    where user_id = auth.uid()
    order by profile_index;
end;
$$;

create or replace function public.sync_push_profiles(p_profiles jsonb)
returns void
language plpgsql
security invoker
as $$
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;

  delete from public.profiles
  where user_id = auth.uid()
    and profile_index not in (
      select profile_index
      from jsonb_to_recordset(coalesce(p_profiles, '[]'::jsonb)) as x(profile_index int)
    );

  insert into public.profiles (
    user_id,
    profile_index,
    name,
    avatar_color_hex,
    uses_primary_addons,
    uses_primary_plugins,
    avatar_id,
    avatar_url
  )
  select
    auth.uid(),
    x.profile_index,
    coalesce(x.name, ''),
    coalesce(x.avatar_color_hex, '#1E88E5'),
    coalesce(x.uses_primary_addons, false),
    coalesce(x.uses_primary_plugins, false),
    x.avatar_id,
    x.avatar_url
  from jsonb_to_recordset(coalesce(p_profiles, '[]'::jsonb)) as x(
    profile_index int,
    name text,
    avatar_color_hex text,
    uses_primary_addons boolean,
    uses_primary_plugins boolean,
    avatar_id text,
    avatar_url text
  )
  on conflict (user_id, profile_index) do update set
    name = excluded.name,
    avatar_color_hex = excluded.avatar_color_hex,
    uses_primary_addons = excluded.uses_primary_addons,
    uses_primary_plugins = excluded.uses_primary_plugins,
    avatar_id = excluded.avatar_id,
    avatar_url = excluded.avatar_url;
end;
$$;

create or replace function public.sync_delete_profile_data(p_profile_id int)
returns void
language plpgsql
security invoker
as $$
begin
  delete from public.addons where user_id = auth.uid() and profile_id = p_profile_id;
  delete from public.plugins where user_id = auth.uid() and profile_id = p_profile_id;
  delete from public.library_items where user_id = auth.uid() and profile_id = p_profile_id;
  delete from public.watch_progress where user_id = auth.uid() and profile_id = p_profile_id;
  delete from public.watched_items where user_id = auth.uid() and profile_id = p_profile_id;
  delete from public.profile_settings_blobs where user_id = auth.uid() and profile_id = p_profile_id;
  delete from public.home_catalog_settings where user_id = auth.uid() and profile_id = p_profile_id;
  delete from public.collections where user_id = auth.uid() and profile_id = p_profile_id;
  delete from public.profiles where user_id = auth.uid() and profile_index = p_profile_id;
end;
$$;

create or replace function public.sync_pull_profile_locks()
returns table(profile_index int, pin_enabled boolean, pin_locked_until text)
language sql
security invoker
as $$
  select profile_index, pin_enabled, pin_locked_until::text
  from public.profiles
  where user_id = auth.uid()
  order by profile_index
$$;

create or replace function public.verify_profile_pin(p_profile_id int, p_pin text)
returns table(unlocked boolean, retry_after_seconds int, message text)
language plpgsql
security invoker
as $$
declare
  stored_hash text;
begin
  select pin_hash into stored_hash
  from public.profiles
  where user_id = auth.uid() and profile_index = p_profile_id;

  if stored_hash is null then
    return query select true, 0, null::text;
  elsif crypt(coalesce(p_pin, ''), stored_hash) = stored_hash then
    return query select true, 0, null::text;
  else
    return query select false, 0, 'Incorrect PIN'::text;
  end if;
end;
$$;

create or replace function public.set_profile_pin(p_profile_id int, p_pin text, p_current_pin text default null)
returns void
language plpgsql
security invoker
as $$
declare
  stored_hash text;
begin
  select pin_hash into stored_hash
  from public.profiles
  where user_id = auth.uid() and profile_index = p_profile_id;

  if stored_hash is not null and p_current_pin is not null and crypt(p_current_pin, stored_hash) <> stored_hash then
    raise exception 'current PIN is incorrect';
  end if;

  update public.profiles
  set pin_enabled = true,
      pin_hash = crypt(coalesce(p_pin, ''), gen_salt('bf')),
      pin_locked_until = null
  where user_id = auth.uid() and profile_index = p_profile_id;
end;
$$;

create or replace function public.clear_profile_pin(p_profile_id int, p_current_pin text default null)
returns void
language plpgsql
security invoker
as $$
declare
  stored_hash text;
begin
  select pin_hash into stored_hash
  from public.profiles
  where user_id = auth.uid() and profile_index = p_profile_id;

  if stored_hash is not null and p_current_pin is not null and crypt(p_current_pin, stored_hash) <> stored_hash then
    raise exception 'current PIN is incorrect';
  end if;

  update public.profiles
  set pin_enabled = false,
      pin_hash = null,
      pin_locked_until = null
  where user_id = auth.uid() and profile_index = p_profile_id;
end;
$$;

create or replace function public.clear_profile_pin_with_account_password(p_profile_id int, p_account_password text)
returns void
language sql
security invoker
as $$
  select public.clear_profile_pin(p_profile_id, null);
$$;

create or replace function public.get_avatar_catalog()
returns setof public.avatar_catalog
language sql
security invoker
as $$
  select *
  from public.avatar_catalog
  where is_active
  order by sort_order, id
$$;

create or replace function public.sync_push_addons(p_profile_id int, p_addons jsonb)
returns void
language plpgsql
security invoker
as $$
begin
  delete from public.addons where user_id = auth.uid() and profile_id = p_profile_id;

  insert into public.addons (user_id, profile_id, url, name, enabled, sort_order)
  select auth.uid(), p_profile_id, x.url, x.name, coalesce(x.enabled, true), coalesce(x.sort_order, 0)
  from jsonb_to_recordset(coalesce(p_addons, '[]'::jsonb)) as x(
    url text,
    name text,
    enabled boolean,
    sort_order int
  );
end;
$$;

create or replace function public.sync_push_plugins(p_profile_id int, p_plugins jsonb)
returns void
language plpgsql
security invoker
as $$
begin
  delete from public.plugins where user_id = auth.uid() and profile_id = p_profile_id;

  insert into public.plugins (user_id, profile_id, url, name, enabled, sort_order)
  select auth.uid(), p_profile_id, x.url, x.name, coalesce(x.enabled, true), coalesce(x.sort_order, 0)
  from jsonb_to_recordset(coalesce(p_plugins, '[]'::jsonb)) as x(
    url text,
    name text,
    enabled boolean,
    sort_order int
  );
end;
$$;

create or replace function public.sync_pull_library(p_profile_id int, p_limit int default 500, p_offset int default 0)
returns table(
  content_id text,
  content_type text,
  name text,
  poster text,
  poster_shape text,
  background text,
  description text,
  release_info text,
  imdb_rating real,
  genres text[],
  addon_base_url text,
  added_at bigint
)
language sql
security invoker
as $$
  select content_id, content_type, name, poster, poster_shape, background, description,
         release_info, imdb_rating, genres, addon_base_url, added_at
  from public.library_items
  where user_id = auth.uid() and profile_id = p_profile_id
  order by added_at desc
  limit greatest(coalesce(p_limit, 500), 0)
  offset greatest(coalesce(p_offset, 0), 0)
$$;

create or replace function public.sync_push_library(p_profile_id int, p_items jsonb)
returns void
language plpgsql
security invoker
as $$
begin
  delete from public.library_items where user_id = auth.uid() and profile_id = p_profile_id;

  insert into public.library_items (
    user_id, profile_id, content_id, content_type, name, poster, poster_shape,
    background, description, release_info, imdb_rating, genres, addon_base_url, added_at
  )
  select auth.uid(), p_profile_id, x.content_id, x.content_type, coalesce(x.name, ''),
         x.poster, coalesce(x.poster_shape, 'POSTER'), x.background, x.description,
         x.release_info, x.imdb_rating, coalesce(x.genres, '{}'), x.addon_base_url,
         coalesce(x.added_at, 0)
  from jsonb_to_recordset(coalesce(p_items, '[]'::jsonb)) as x(
    content_id text,
    content_type text,
    name text,
    poster text,
    poster_shape text,
    background text,
    description text,
    release_info text,
    imdb_rating real,
    genres text[],
    addon_base_url text,
    added_at bigint
  );
end;
$$;

create or replace function public.sync_get_watch_progress_delta_cursor(p_profile_id int)
returns bigint
language sql
security invoker
as $$
  select coalesce(max(event_id), 0)
  from public.watch_progress_events
  where user_id = auth.uid() and profile_id = p_profile_id
$$;

create or replace function public.sync_pull_watch_progress(p_profile_id int, p_since_last_watched bigint default null, p_limit int default null)
returns table(
  content_id text,
  content_type text,
  video_id text,
  season int,
  episode int,
  "position" bigint,
  duration bigint,
  last_watched bigint,
  progress_key text
)
language sql
security invoker
as $$
  select content_id, content_type, video_id, season, episode, position, duration, last_watched, progress_key
  from public.watch_progress
  where user_id = auth.uid()
    and profile_id = p_profile_id
    and (p_since_last_watched is null or last_watched > p_since_last_watched)
  order by last_watched desc
  limit coalesce(p_limit, 2147483647)
$$;

create or replace function public.sync_pull_watch_progress_delta(p_profile_id int, p_since_event_id bigint, p_limit int default 900)
returns table(
  event_id bigint,
  operation text,
  progress_key text,
  content_id text,
  content_type text,
  video_id text,
  season int,
  episode int,
  "position" bigint,
  duration bigint,
  last_watched bigint
)
language sql
security invoker
as $$
  select event_id, operation, progress_key, content_id, content_type, video_id,
         season, episode, position, duration, last_watched
  from public.watch_progress_events
  where user_id = auth.uid()
    and profile_id = p_profile_id
    and event_id > coalesce(p_since_event_id, 0)
  order by event_id
  limit greatest(coalesce(p_limit, 900), 0)
$$;

create or replace function public.sync_push_watch_progress(p_profile_id int, p_entries jsonb)
returns void
language plpgsql
security invoker
as $$
begin
  insert into public.watch_progress (
    user_id, profile_id, progress_key, content_id, content_type, video_id,
    season, episode, position, duration, last_watched
  )
  select auth.uid(), p_profile_id, x.progress_key, x.content_id, x.content_type, x.video_id,
         x.season, x.episode, coalesce(x.position, 0), coalesce(x.duration, 0),
         coalesce(x.last_watched, 0)
  from jsonb_to_recordset(coalesce(p_entries, '[]'::jsonb)) as x(
    content_id text,
    content_type text,
    video_id text,
    season int,
    episode int,
    position bigint,
    duration bigint,
    last_watched bigint,
    progress_key text
  )
  on conflict (user_id, profile_id, progress_key) do update set
    content_id = excluded.content_id,
    content_type = excluded.content_type,
    video_id = excluded.video_id,
    season = excluded.season,
    episode = excluded.episode,
    position = excluded.position,
    duration = excluded.duration,
    last_watched = excluded.last_watched;

  insert into public.watch_progress_events (
    user_id, profile_id, operation, progress_key, content_id, content_type, video_id,
    season, episode, position, duration, last_watched
  )
  select auth.uid(), p_profile_id, 'upsert', x.progress_key, x.content_id, x.content_type, x.video_id,
         x.season, x.episode, coalesce(x.position, 0), coalesce(x.duration, 0),
         coalesce(x.last_watched, 0)
  from jsonb_to_recordset(coalesce(p_entries, '[]'::jsonb)) as x(
    content_id text,
    content_type text,
    video_id text,
    season int,
    episode int,
    position bigint,
    duration bigint,
    last_watched bigint,
    progress_key text
  );
end;
$$;

create or replace function public.sync_delete_watch_progress(p_profile_id int, p_keys jsonb)
returns void
language plpgsql
security invoker
as $$
begin
  insert into public.watch_progress_events (
    user_id, profile_id, operation, progress_key, content_id, content_type, video_id,
    season, episode, position, duration, last_watched
  )
  select auth.uid(), p_profile_id, 'delete', wp.progress_key, wp.content_id, wp.content_type,
         wp.video_id, wp.season, wp.episode, wp.position, wp.duration, wp.last_watched
  from public.watch_progress wp
  join jsonb_array_elements_text(coalesce(p_keys, '[]'::jsonb)) k(value)
    on k.value = wp.progress_key
  where wp.user_id = auth.uid() and wp.profile_id = p_profile_id;

  delete from public.watch_progress wp
  using jsonb_array_elements_text(coalesce(p_keys, '[]'::jsonb)) k(value)
  where wp.user_id = auth.uid()
    and wp.profile_id = p_profile_id
    and wp.progress_key = k.value;
end;
$$;

create or replace function public.nuvio_watched_key(p_content_id text, p_season int, p_episode int)
returns text
language sql
immutable
as $$
  select coalesce(p_content_id, '') || '|s=' || coalesce(p_season::text, '') || '|e=' || coalesce(p_episode::text, '')
$$;

create or replace function public.sync_get_watched_items_delta_cursor(p_profile_id int)
returns bigint
language sql
security invoker
as $$
  select coalesce(max(event_id), 0)
  from public.watched_item_events
  where user_id = auth.uid() and profile_id = p_profile_id
$$;

create or replace function public.sync_pull_watched_items(p_profile_id int, p_page int default 1, p_page_size int default 900)
returns table(content_id text, content_type text, title text, season int, episode int, watched_at bigint)
language sql
security invoker
as $$
  select content_id, content_type, title, season, episode, watched_at
  from public.watched_items
  where user_id = auth.uid() and profile_id = p_profile_id
  order by watched_at desc
  limit greatest(coalesce(p_page_size, 900), 0)
  offset greatest((coalesce(p_page, 1) - 1) * coalesce(p_page_size, 900), 0)
$$;

create or replace function public.sync_pull_watched_items_delta(p_profile_id int, p_since_event_id bigint, p_limit int default 900)
returns table(event_id bigint, operation text, content_id text, content_type text, title text, season int, episode int, watched_at bigint)
language sql
security invoker
as $$
  select event_id, operation, content_id, content_type, title, season, episode, watched_at
  from public.watched_item_events
  where user_id = auth.uid()
    and profile_id = p_profile_id
    and event_id > coalesce(p_since_event_id, 0)
  order by event_id
  limit greatest(coalesce(p_limit, 900), 0)
$$;

create or replace function public.sync_push_watched_items(p_profile_id int, p_items jsonb)
returns void
language plpgsql
security invoker
as $$
begin
  insert into public.watched_items (
    user_id, profile_id, watched_key, content_id, content_type, title, season, episode, watched_at
  )
  select auth.uid(), p_profile_id, public.nuvio_watched_key(x.content_id, x.season, x.episode),
         x.content_id, x.content_type, coalesce(x.title, ''), x.season, x.episode,
         coalesce(x.watched_at, 0)
  from jsonb_to_recordset(coalesce(p_items, '[]'::jsonb)) as x(
    content_id text,
    content_type text,
    title text,
    season int,
    episode int,
    watched_at bigint
  )
  on conflict (user_id, profile_id, watched_key) do update set
    content_id = excluded.content_id,
    content_type = excluded.content_type,
    title = excluded.title,
    season = excluded.season,
    episode = excluded.episode,
    watched_at = excluded.watched_at;

  insert into public.watched_item_events (
    user_id, profile_id, operation, watched_key, content_id, content_type, title, season, episode, watched_at
  )
  select auth.uid(), p_profile_id, 'upsert', public.nuvio_watched_key(x.content_id, x.season, x.episode),
         x.content_id, x.content_type, coalesce(x.title, ''), x.season, x.episode,
         coalesce(x.watched_at, 0)
  from jsonb_to_recordset(coalesce(p_items, '[]'::jsonb)) as x(
    content_id text,
    content_type text,
    title text,
    season int,
    episode int,
    watched_at bigint
  );
end;
$$;

create or replace function public.sync_delete_watched_items(p_profile_id int, p_keys jsonb)
returns void
language plpgsql
security invoker
as $$
begin
  insert into public.watched_item_events (
    user_id, profile_id, operation, watched_key, content_id, content_type, title, season, episode, watched_at
  )
  select auth.uid(), p_profile_id, 'delete', wi.watched_key, wi.content_id, wi.content_type,
         wi.title, wi.season, wi.episode, wi.watched_at
  from public.watched_items wi
  join jsonb_to_recordset(coalesce(p_keys, '[]'::jsonb)) as k(content_id text, season int, episode int)
    on wi.watched_key = public.nuvio_watched_key(k.content_id, k.season, k.episode)
  where wi.user_id = auth.uid() and wi.profile_id = p_profile_id;

  delete from public.watched_items wi
  using jsonb_to_recordset(coalesce(p_keys, '[]'::jsonb)) as k(content_id text, season int, episode int)
  where wi.user_id = auth.uid()
    and wi.profile_id = p_profile_id
    and wi.watched_key = public.nuvio_watched_key(k.content_id, k.season, k.episode);
end;
$$;

create or replace function public.sync_pull_profile_settings_blob(p_profile_id int, p_platform text default 'mobile')
returns table(profile_id int, settings_json jsonb, updated_at text)
language sql
security invoker
as $$
  select profile_id, settings_json, updated_at::text
  from public.profile_settings_blobs
  where user_id = auth.uid() and profile_id = p_profile_id and platform = p_platform
$$;

create or replace function public.sync_push_profile_settings_blob(p_profile_id int, p_platform text default 'mobile', p_settings_json jsonb default '{}'::jsonb)
returns void
language sql
security invoker
as $$
  insert into public.profile_settings_blobs (user_id, profile_id, platform, settings_json)
  values (auth.uid(), p_profile_id, coalesce(p_platform, 'mobile'), coalesce(p_settings_json, '{}'::jsonb))
  on conflict (user_id, profile_id, platform) do update set
    settings_json = excluded.settings_json
$$;

create or replace function public.sync_pull_home_catalog_settings(p_profile_id int, p_platform text default 'mobile')
returns table(profile_id int, settings_json jsonb, updated_at text)
language sql
security invoker
as $$
  select profile_id, settings_json, updated_at::text
  from public.home_catalog_settings
  where user_id = auth.uid() and profile_id = p_profile_id and platform = p_platform
$$;

create or replace function public.sync_push_home_catalog_settings(p_profile_id int, p_platform text default 'mobile', p_settings_json jsonb default '{}'::jsonb)
returns void
language sql
security invoker
as $$
  insert into public.home_catalog_settings (user_id, profile_id, platform, settings_json)
  values (auth.uid(), p_profile_id, coalesce(p_platform, 'mobile'), coalesce(p_settings_json, '{}'::jsonb))
  on conflict (user_id, profile_id, platform) do update set
    settings_json = excluded.settings_json
$$;

create or replace function public.sync_pull_collections(p_profile_id int)
returns table(profile_id int, collections_json jsonb, updated_at text)
language sql
security invoker
as $$
  select profile_id, collections_json, updated_at::text
  from public.collections
  where user_id = auth.uid() and profile_id = p_profile_id
$$;

create or replace function public.sync_push_collections(p_profile_id int, p_collections_json jsonb default '[]'::jsonb)
returns void
language sql
security invoker
as $$
  insert into public.collections (user_id, profile_id, collections_json)
  values (auth.uid(), p_profile_id, coalesce(p_collections_json, '[]'::jsonb))
  on conflict (user_id, profile_id) do update set
    collections_json = excluded.collections_json
$$;
