-- SmartFamily · Supabase schema v2 (app 0.2.0): shopping lists, list items, purchase history, inventory.
-- Run once after 0001_init.sql: SQL Editor -> New query -> paste -> Run. Safe to run again.

create table if not exists public.shopping_lists (
  id              text primary key,
  family_id       text not null references public.families(id) on delete cascade,
  name            text not null,
  kind            text not null default 'supermarket',
  is_template     boolean not null default false,
  shopper_id      text,
  shopping_since  bigint,
  created_by      text not null,
  created_at      bigint not null default public.sf_now_ms(),
  updated_at      bigint not null default public.sf_now_ms(),
  deleted_at      bigint
);

create table if not exists public.list_items (
  id          text primary key,
  list_id     text not null references public.shopping_lists(id) on delete cascade,
  family_id   text not null references public.families(id) on delete cascade,
  name        text not null,
  quantity    double precision not null default 1,
  unit        text not null default '',
  category    text not null default 'other',
  brand       text not null default '',
  any_brand   boolean not null default true,
  note        text not null default '',
  kashrut     text not null default '',
  barcode     text not null default '',
  status      text not null default 'ACTIVE' check (status in ('ACTIVE','REQUESTED')),
  recurring   boolean not null default false,
  checked     boolean not null default false,
  checked_by  text,
  checked_at  bigint,
  added_by    text not null,
  created_at  bigint not null default public.sf_now_ms(),
  updated_at  bigint not null default public.sf_now_ms(),
  deleted_at  bigint
);

create table if not exists public.purchase_history (
  id          text primary key,
  family_id   text not null references public.families(id) on delete cascade,
  name        text not null,
  category    text not null default 'other',
  quantity    double precision not null default 1,
  unit        text not null default '',
  barcode     text not null default '',
  bought_at   bigint not null,
  bought_by   text not null,
  updated_at  bigint not null default public.sf_now_ms(),
  deleted_at  bigint
);

create table if not exists public.inventory_items (
  id            text primary key,
  family_id     text not null references public.families(id) on delete cascade,
  name          text not null,
  location      text not null default 'pantry',
  quantity      double precision not null default 1,
  unit          text not null default '',
  min_quantity  double precision not null default 0,
  expires_at    bigint,
  opened_at     bigint,
  kashrut       text not null default '',
  category      text not null default 'other',
  barcode       text not null default '',
  added_by      text not null,
  added_at      bigint not null default public.sf_now_ms(),
  updated_at    bigint not null default public.sf_now_ms(),
  deleted_at    bigint
);

create index if not exists shopping_lists_updated_idx   on public.shopping_lists(updated_at);
create index if not exists list_items_updated_idx       on public.list_items(updated_at);
create index if not exists purchase_history_updated_idx on public.purchase_history(updated_at);
create index if not exists inventory_items_updated_idx  on public.inventory_items(updated_at);

do $$
declare t text;
begin
  foreach t in array array['shopping_lists','list_items','purchase_history','inventory_items'] loop
    execute format('drop trigger if exists sf_touch_%1$s on public.%1$s', t);
    execute format('create trigger sf_touch_%1$s before insert or update on public.%1$s for each row execute function public.sf_touch()', t);
    execute format('alter table public.%1$s enable row level security', t);
  end loop;
end $$;

-- Role of the caller in a family (NULL when not an active member).
create or replace function public.sf_my_role(fid text) returns text
language sql stable security definer set search_path = public as $$
  select m.role from public.family_members m
  where m.family_id = fid and m.user_id = auth.uid()::text and m.status = 'ACTIVE' and m.deleted_at is null
$$;

-- Lists, purchases and inventory: every active member except guests can read and write.
do $$
declare t text;
begin
  foreach t in array array['shopping_lists','purchase_history','inventory_items'] loop
    execute format('drop policy if exists %1$s_select on public.%1$s', t);
    execute format('create policy %1$s_select on public.%1$s for select to authenticated using (public.sf_is_member(family_id))', t);
    execute format('drop policy if exists %1$s_write on public.%1$s', t);
    execute format($p$create policy %1$s_write on public.%1$s for insert to authenticated
                   with check (public.sf_my_role(family_id) in ('OWNER','ADMIN','MEMBER'))$p$, t);
    execute format('drop policy if exists %1$s_update on public.%1$s', t);
    execute format($p$create policy %1$s_update on public.%1$s for update to authenticated
                   using (public.sf_my_role(family_id) in ('OWNER','ADMIN','MEMBER'))
                   with check (public.sf_my_role(family_id) in ('OWNER','ADMIN','MEMBER'))$p$, t);
  end loop;
end $$;

-- List items: members read; adults write; a child may add items only as REQUESTED and tick items (SHP-8).
drop policy if exists list_items_select on public.list_items;
create policy list_items_select on public.list_items for select to authenticated
  using (public.sf_is_member(family_id));
drop policy if exists list_items_insert on public.list_items;
create policy list_items_insert on public.list_items for insert to authenticated
  with check (
    public.sf_my_role(family_id) in ('OWNER','ADMIN','MEMBER')
    or (public.sf_my_role(family_id) = 'CHILD' and status = 'REQUESTED' and added_by = auth.uid()::text)
  );
drop policy if exists list_items_update on public.list_items;
create policy list_items_update on public.list_items for update to authenticated
  using (public.sf_my_role(family_id) in ('OWNER','ADMIN','MEMBER','CHILD'))
  with check (
    public.sf_my_role(family_id) in ('OWNER','ADMIN','MEMBER')
    or (public.sf_my_role(family_id) = 'CHILD' and (status = 'REQUESTED' or added_by <> auth.uid()::text))
  );
