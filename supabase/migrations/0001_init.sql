-- SmartFamily · Supabase schema v1 (Phase 0: families, members, invites, module settings)
-- Run once in the Supabase dashboard: SQL Editor -> New query -> paste -> Run.
-- Every table has row-level security: a user only ever sees families they are an active member of.

-- Server time in milliseconds; every write gets a fresh server timestamp so incremental sync works.
create or replace function public.sf_now_ms() returns bigint
language sql stable as $$ select (extract(epoch from clock_timestamp()) * 1000)::bigint $$;

create or replace function public.sf_touch() returns trigger
language plpgsql as $$
begin
  new.updated_at := public.sf_now_ms();
  return new;
end $$;

-- ---------- tables ----------

create table if not exists public.profiles (
  id            text primary key,              -- auth.uid()::text
  display_name  text not null default '',
  avatar_color  int  not null default 0,
  updated_at    bigint not null default public.sf_now_ms()
);

create table if not exists public.families (
  id          text primary key,
  name        text not null,
  owner_id    text not null,
  kashrut     text not null default 'kosher',
  created_at  bigint not null default public.sf_now_ms(),
  updated_at  bigint not null default public.sf_now_ms(),
  deleted_at  bigint
);

create table if not exists public.family_members (
  id            text primary key,
  family_id     text not null references public.families(id) on delete cascade,
  user_id       text not null,
  display_name  text not null default '',
  role          text not null check (role in ('OWNER','ADMIN','MEMBER','CHILD','GUEST')),
  status        text not null default 'ACTIVE' check (status in ('ACTIVE','PENDING')),
  joined_at     bigint not null default public.sf_now_ms(),
  updated_at    bigint not null default public.sf_now_ms(),
  deleted_at    bigint,
  unique (family_id, user_id)
);
create index if not exists family_members_user_idx on public.family_members(user_id);

create table if not exists public.invites (
  code        text primary key,
  family_id   text not null references public.families(id) on delete cascade,
  created_by  text not null,
  role        text not null default 'MEMBER' check (role in ('ADMIN','MEMBER','CHILD','GUEST')),
  expires_at  bigint not null,
  used_by     text,
  updated_at  bigint not null default public.sf_now_ms(),
  deleted_at  bigint
);

create table if not exists public.module_settings (
  id          text primary key,                -- family_id|user_id|module
  family_id   text not null references public.families(id) on delete cascade,
  user_id     text not null default '',        -- '' = family level
  module      text not null,
  enabled     boolean not null,
  updated_at  bigint not null default public.sf_now_ms()
);

create index if not exists families_updated_idx        on public.families(updated_at);
create index if not exists family_members_updated_idx  on public.family_members(updated_at);
create index if not exists invites_updated_idx         on public.invites(updated_at);
create index if not exists module_settings_updated_idx on public.module_settings(updated_at);
create index if not exists profiles_updated_idx        on public.profiles(updated_at);

do $$
declare t text;
begin
  foreach t in array array['profiles','families','family_members','invites','module_settings'] loop
    execute format('drop trigger if exists sf_touch_%1$s on public.%1$s', t);
    execute format('create trigger sf_touch_%1$s before insert or update on public.%1$s for each row execute function public.sf_touch()', t);
  end loop;
end $$;

-- ---------- membership helpers (security definer avoids RLS recursion) ----------

create or replace function public.sf_is_member(fid text) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.family_members m
    where m.family_id = fid and m.user_id = auth.uid()::text
      and m.status = 'ACTIVE' and m.deleted_at is null
  )
$$;

create or replace function public.sf_is_admin(fid text) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.family_members m
    where m.family_id = fid and m.user_id = auth.uid()::text
      and m.status = 'ACTIVE' and m.deleted_at is null and m.role in ('OWNER','ADMIN')
  )
$$;

create or replace function public.sf_shares_family(other text) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.family_members a
    join public.family_members b on a.family_id = b.family_id
    where a.user_id = auth.uid()::text and a.status = 'ACTIVE' and a.deleted_at is null
      and b.user_id = other and b.deleted_at is null
  )
$$;

-- The stored role/status of a member row, read without RLS so policies on family_members
-- can compare against it without recursing into themselves.
create or replace function public.sf_member_role_status(mid text) returns text
language sql stable security definer set search_path = public as $$
  select m.role || '|' || m.status from public.family_members m where m.id = mid
$$;

-- ---------- row-level security ----------

alter table public.profiles        enable row level security;
alter table public.families        enable row level security;
alter table public.family_members  enable row level security;
alter table public.invites         enable row level security;
alter table public.module_settings enable row level security;

-- profiles: you and the people you share a family with can read; only you can write yours.
drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles for select to authenticated
  using (id = auth.uid()::text or public.sf_shares_family(id));
drop policy if exists profiles_insert on public.profiles;
create policy profiles_insert on public.profiles for insert to authenticated
  with check (id = auth.uid()::text);
drop policy if exists profiles_update on public.profiles;
create policy profiles_update on public.profiles for update to authenticated
  using (id = auth.uid()::text) with check (id = auth.uid()::text);

-- families: members read; the creator inserts as owner; admins update; only the owner deletes (soft).
drop policy if exists families_select on public.families;
create policy families_select on public.families for select to authenticated
  using (public.sf_is_member(id) or owner_id = auth.uid()::text);
drop policy if exists families_insert on public.families;
create policy families_insert on public.families for insert to authenticated
  with check (owner_id = auth.uid()::text);
drop policy if exists families_update on public.families;
create policy families_update on public.families for update to authenticated
  using (public.sf_is_admin(id) or owner_id = auth.uid()::text)
  with check (deleted_at is null or owner_id = auth.uid()::text);

-- members: members read the family list and always their own row;
-- admins manage members; the family owner can add themselves as OWNER when creating it.
drop policy if exists members_select on public.family_members;
create policy members_select on public.family_members for select to authenticated
  using (public.sf_is_member(family_id) or user_id = auth.uid()::text);
drop policy if exists members_insert on public.family_members;
create policy members_insert on public.family_members for insert to authenticated
  with check (
    public.sf_is_admin(family_id)
    or (user_id = auth.uid()::text and role = 'OWNER'
        and exists (select 1 from public.families f where f.id = family_id and f.owner_id = auth.uid()::text))
  );
drop policy if exists members_update on public.family_members;
create policy members_update on public.family_members for update to authenticated
  using (public.sf_is_admin(family_id) or user_id = auth.uid()::text)
  with check (
    public.sf_is_admin(family_id)
    -- a non-admin may only edit their own name, never their role or status
    or (user_id = auth.uid()::text and public.sf_member_role_status(id) = role || '|' || status)
  );

-- invites: admins only. Joining goes through join_family() below.
drop policy if exists invites_all on public.invites;
create policy invites_all on public.invites for all to authenticated
  using (public.sf_is_admin(family_id)) with check (public.sf_is_admin(family_id));

-- module settings: members read; admins write family level ('' user); each user writes their own.
drop policy if exists modules_select on public.module_settings;
create policy modules_select on public.module_settings for select to authenticated
  using (public.sf_is_member(family_id));
drop policy if exists modules_insert on public.module_settings;
create policy modules_insert on public.module_settings for insert to authenticated
  with check (
    (user_id = '' and (public.sf_is_admin(family_id)
       or exists (select 1 from public.families f where f.id = family_id and f.owner_id = auth.uid()::text)))
    or (user_id = auth.uid()::text and public.sf_is_member(family_id))
  );
drop policy if exists modules_update on public.module_settings;
create policy modules_update on public.module_settings for update to authenticated
  using ((user_id = '' and public.sf_is_admin(family_id)) or (user_id = auth.uid()::text and public.sf_is_member(family_id)))
  with check ((user_id = '' and public.sf_is_admin(family_id)) or (user_id = auth.uid()::text and public.sf_is_member(family_id)));

-- ---------- join with an invite code (FAM-1, FAM-2) ----------
-- Validates the code, marks it used and adds the caller as a PENDING member for an admin to approve.

create or replace function public.join_family(p_code text, p_display_name text) returns text
language plpgsql security definer set search_path = public as $$
declare inv public.invites;
begin
  if auth.uid() is null then raise exception 'Not signed in'; end if;
  select * into inv from public.invites
   where code = upper(p_code) and deleted_at is null for update;
  if not found then raise exception 'Invite code not found'; end if;
  if inv.used_by is not null then raise exception 'This invite was already used'; end if;
  if inv.expires_at < public.sf_now_ms() then raise exception 'This invite has expired'; end if;

  insert into public.family_members (id, family_id, user_id, display_name, role, status)
  values (gen_random_uuid()::text, inv.family_id, auth.uid()::text, coalesce(p_display_name, ''), inv.role, 'PENDING')
  on conflict (family_id, user_id) do update set status = 'PENDING', deleted_at = null, role = excluded.role;

  update public.invites set used_by = auth.uid()::text where code = inv.code;
  return inv.family_id;
end $$;

revoke all on function public.join_family(text, text) from public;
grant execute on function public.join_family(text, text) to authenticated;
