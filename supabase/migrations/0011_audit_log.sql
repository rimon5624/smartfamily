-- SmartFamily 2.0 - who changed what (audit log). Written by triggers on the server, read by family admins.
-- Safe to run again.

create table if not exists public.audit_log (
  id bigserial primary key,
  family_id text not null references public.families(id) on delete cascade,
  table_name text not null,
  row_id text not null,
  action text not null,           -- ADD / EDIT / DELETE
  title text not null default '',
  actor text,                     -- auth.uid() of the person whose phone made the change
  at bigint not null default public.sf_now_ms()
);
create index if not exists audit_log_family_idx on public.audit_log(family_id, at desc);
alter table public.audit_log enable row level security;
drop policy if exists audit_log_select on public.audit_log;
create policy audit_log_select on public.audit_log for select to authenticated using (public.sf_is_admin(family_id));
-- nobody writes directly; only the trigger below (security definer)

create or replace function public.sf_audit() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  r jsonb := to_jsonb(coalesce(new, old));
  act text;
begin
  if tg_op = 'INSERT' then act := 'ADD';
  elsif (new.deleted_at is not null and (old.deleted_at is null)) then act := 'DELETE';
  else act := 'EDIT';
  end if;
  -- skip the noise of a sync re-sending the same row
  if tg_op = 'UPDATE' and to_jsonb(new) - 'updated_at' = to_jsonb(old) - 'updated_at' then return new; end if;
  insert into public.audit_log(family_id, table_name, row_id, action, title, actor)
  values (r->>'family_id', tg_table_name, r->>'id', act,
          left(coalesce(r->>'title', r->>'name', r->>'question', r->>'display_name', ''), 120), auth.uid()::text);
  -- keep about the last 2000 entries per family
  delete from public.audit_log a where a.family_id = r->>'family_id'
    and a.id < (select min(id) from (select id from public.audit_log where family_id = r->>'family_id' order by id desc limit 2000) t);
  return new;
end $$;

do $$
declare t text;
begin
  foreach t in array array['shopping_lists','events','family_dates','chores','recipes','budget_entries','home_entries','trips',
                           'polls','recurring_payments','goals','split_expenses','repairs','kit_items','tree_people','family_members']
  loop
    if to_regclass('public.' || t) is not null then
      execute format('drop trigger if exists sf_audit_%1$s on public.%1$s', t);
      execute format('create trigger sf_audit_%1$s after insert or update on public.%1$s for each row execute function public.sf_audit()', t);
    end if;
  end loop;
end $$;

-- read for the app: newest first
create or replace function public.family_audit(fid text, lim int default 200)
returns table(table_name text, action text, title text, actor text, at bigint)
language sql stable security definer set search_path = public as $$
  select a.table_name, a.action, a.title, a.actor, a.at from public.audit_log a
  where a.family_id = fid and public.sf_is_admin(fid)
  order by a.id desc limit least(greatest(lim, 1), 500)
$$;
revoke all on function public.family_audit(text, int) from public, anon;
grant execute on function public.family_audit(text, int) to authenticated;
