-- SmartFamily 0.9.0 - sharing between families. Run before 0009_z_sharing_policies.sql. Safe to run again.
-- A family admin offers a whole module or one item (a recipe, a trip, a list, a chat...) to another
-- family, read-only or editable. Nothing is visible to the other family until one of its admins accepts.

create table if not exists public.family_shares (
  id text primary key,
  family_id text not null references public.families(id) on delete cascade,
  target_family_id text not null references public.families(id) on delete cascade,
  source_name text not null default '',
  target_name text not null default '',
  module text not null,
  item_id text not null default '',
  item_title text not null default '',
  can_write boolean not null default false,
  status text not null default 'PENDING',
  created_by text not null,
  decided_by text not null default '',
  updated_at bigint not null default public.sf_now_ms(),
  deleted_at bigint
);
create index if not exists family_shares_updated_idx on public.family_shares(updated_at);
create index if not exists family_shares_family_idx on public.family_shares(family_id);
create index if not exists family_shares_target_idx on public.family_shares(target_family_id);
drop trigger if exists sf_touch_family_shares on public.family_shares;
create trigger sf_touch_family_shares before insert or update on public.family_shares for each row execute function public.sf_touch();
alter table public.family_shares enable row level security;

-- Is [item] of module [mod] in family [fid] shared (and accepted) with a family I belong to?
-- item null = only whole-module shares count.
create or replace function public.sf_shared(fid text, mod text, item text, w boolean) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.family_shares s
    where s.family_id = fid and s.status = 'ACCEPTED' and s.deleted_at is null
      and s.module = mod and (s.item_id = '' or s.item_id = item)
      and (not w or s.can_write)
      and public.sf_is_member(s.target_family_id)
  )
$$;

-- Am I listed in this chat? Keeps shared chats private to their own members.
create or replace function public.sf_chat_member(cid text) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.chats c
    where c.id = cid and position(',' || auth.uid()::text || ',' in ',' || c.members || ',') > 0
  )
$$;

-- Families linked to mine by an accepted share, in either direction (to show names and chat keys).
create or replace function public.sf_linked(fid text, mod text) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.family_shares s
    where s.status = 'ACCEPTED' and s.deleted_at is null and (mod is null or s.module = mod)
      and ((s.family_id = fid and public.sf_is_member(s.target_family_id))
        or (s.target_family_id = fid and public.sf_is_member(s.family_id)))
  )
$$;

-- Only the name of a family, for the "share with" dialog (the link code is the family id).
create or replace function public.sf_family_name(fid text) returns text
language sql stable security definer set search_path = public as $$
  select name from public.families where id = fid and deleted_at is null
$$;

-- The receiving family's admin accepts or declines. Accepting a chat share also opens the chat
-- link the other way, so people from both families can start chats with each other.
create or replace function public.decide_share(p_id text, p_accept boolean) returns void
language plpgsql security definer set search_path = public as $$
declare s public.family_shares;
begin
  select * into s from public.family_shares where id = p_id and deleted_at is null;
  if s.id is null then raise exception 'share not found'; end if;
  if not public.sf_is_admin(s.target_family_id) then raise exception 'not allowed'; end if;
  perform set_config('sf.deciding', '1', true);
  update public.family_shares
     set status = case when p_accept then 'ACCEPTED' else 'DECLINED' end, decided_by = auth.uid()::text
   where id = p_id;
  if p_accept and s.module = 'chat' and s.item_id = '' then
    insert into public.family_shares (id, family_id, target_family_id, source_name, target_name, module, item_id, can_write, status, created_by, decided_by)
    values (s.id || '-back', s.target_family_id, s.family_id, s.target_name, s.source_name, 'chat', '', true, 'ACCEPTED', auth.uid()::text, auth.uid()::text)
    on conflict (id) do update set status = 'ACCEPTED', deleted_at = null;
  elsif not p_accept then
    update public.family_shares set deleted_at = public.sf_now_ms() where id = s.id || '-back';
  end if;
end $$;

revoke execute on function public.sf_shared(text, text, text, boolean) from anon, public;
revoke execute on function public.sf_chat_member(text) from anon, public;
revoke execute on function public.sf_linked(text, text) from anon, public;
revoke execute on function public.sf_family_name(text) from anon, public;
revoke execute on function public.decide_share(text, boolean) from anon, public;
grant execute on function public.sf_shared(text, text, text, boolean) to authenticated;
grant execute on function public.sf_chat_member(text) to authenticated;
grant execute on function public.sf_linked(text, text) to authenticated;
grant execute on function public.sf_family_name(text) to authenticated;
grant execute on function public.decide_share(text, boolean) to authenticated;

-- family_shares: both families read; only the offering family's admins create or change it,
-- and a new share always starts as PENDING (the other side decides through decide_share).
drop policy if exists family_shares_select on public.family_shares;
create policy family_shares_select on public.family_shares for select to authenticated
  using (public.sf_is_member(family_id) or public.sf_is_member(target_family_id));
drop policy if exists family_shares_insert on public.family_shares;
create policy family_shares_insert on public.family_shares for insert to authenticated
  with check (public.sf_is_admin(family_id) and target_family_id <> family_id);
drop policy if exists family_shares_update on public.family_shares;
create policy family_shares_update on public.family_shares for update to authenticated
  using (public.sf_is_admin(family_id))
  with check (public.sf_is_admin(family_id) and status in ('PENDING', 'ACCEPTED', 'DECLINED'));

-- Only decide_share may accept or decline: an offer always starts (or goes back to) PENDING
-- when the offering family writes it, and widening an accepted offer needs a new acceptance.
create or replace function public.sf_share_guard() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if coalesce(current_setting('sf.deciding', true), '') = '1' then return new; end if;
  if tg_op = 'INSERT' then
    new.status := 'PENDING';
    new.decided_by := '';
  elsif new.module <> old.module or new.item_id <> old.item_id or new.target_family_id <> old.target_family_id
     or (new.can_write and not old.can_write) then
    new.status := 'PENDING';
  else
    new.status := old.status;
    new.decided_by := old.decided_by;
  end if;
  return new;
end $$;
revoke execute on function public.sf_share_guard() from anon, public;
drop trigger if exists sf_share_guard on public.family_shares;
create trigger sf_share_guard before insert or update on public.family_shares for each row execute function public.sf_share_guard();

-- Families and member names are visible to linked families (names in shared chats, chores...).
drop policy if exists families_select on public.families;
create policy families_select on public.families for select to authenticated
  using (public.sf_is_member(id) or owner_id = auth.uid()::text or public.sf_linked(id, null));
drop policy if exists members_select on public.family_members;
create policy members_select on public.family_members for select to authenticated
  using (public.sf_is_member(family_id) or user_id = auth.uid()::text or public.sf_linked(family_id, null));

-- Chat device keys: visible to families linked by an accepted chat share, so keys can be sealed to them.
drop policy if exists user_keys_select on public.user_keys;
create policy user_keys_select on public.user_keys for select to authenticated
  using (public.sf_is_member(family_id) or public.sf_linked(family_id, 'chat'));

-- Shopping (hand-written tables from 0.2): shared lists and their items.
drop policy if exists shopping_lists_select on public.shopping_lists;
create policy shopping_lists_select on public.shopping_lists for select to authenticated
  using (public.sf_is_member(family_id) or public.sf_shared(family_id, 'shopping', id, false));
drop policy if exists shopping_lists_update on public.shopping_lists;
create policy shopping_lists_update on public.shopping_lists for update to authenticated
  using (public.sf_my_role(family_id) in ('OWNER','ADMIN','MEMBER') or public.sf_shared(family_id, 'shopping', id, true))
  with check (public.sf_my_role(family_id) in ('OWNER','ADMIN','MEMBER') or public.sf_shared(family_id, 'shopping', id, true));
drop policy if exists list_items_select on public.list_items;
create policy list_items_select on public.list_items for select to authenticated
  using (public.sf_is_member(family_id) or public.sf_shared(family_id, 'shopping', list_id, false));
drop policy if exists list_items_insert on public.list_items;
create policy list_items_insert on public.list_items for insert to authenticated
  with check (
    public.sf_my_role(family_id) in ('OWNER','ADMIN','MEMBER')
    or (public.sf_my_role(family_id) = 'CHILD' and status = 'REQUESTED' and added_by = auth.uid()::text)
    or public.sf_shared(family_id, 'shopping', list_id, true)
  );
drop policy if exists list_items_update on public.list_items;
create policy list_items_update on public.list_items for update to authenticated
  using (public.sf_my_role(family_id) in ('OWNER','ADMIN','MEMBER','CHILD') or public.sf_shared(family_id, 'shopping', list_id, true))
  with check (
    public.sf_my_role(family_id) in ('OWNER','ADMIN','MEMBER')
    or (public.sf_my_role(family_id) = 'CHILD' and (status = 'REQUESTED' or added_by <> auth.uid()::text))
    or public.sf_shared(family_id, 'shopping', list_id, true)
  );
drop policy if exists inventory_items_select on public.inventory_items;
create policy inventory_items_select on public.inventory_items for select to authenticated
  using (public.sf_is_member(family_id) or public.sf_shared(family_id, 'inventory', null, false));
drop policy if exists inventory_items_update on public.inventory_items;
create policy inventory_items_update on public.inventory_items for update to authenticated
  using (public.sf_my_role(family_id) in ('OWNER','ADMIN','MEMBER') or public.sf_shared(family_id, 'inventory', null, true))
  with check (public.sf_my_role(family_id) in ('OWNER','ADMIN','MEMBER') or public.sf_shared(family_id, 'inventory', null, true));
drop policy if exists inventory_items_write on public.inventory_items;
create policy inventory_items_write on public.inventory_items for insert to authenticated
  with check (public.sf_my_role(family_id) in ('OWNER','ADMIN','MEMBER') or public.sf_shared(family_id, 'inventory', null, true));
