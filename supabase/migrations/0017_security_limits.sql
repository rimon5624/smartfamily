-- 2.2 security: limit guessing of invite codes and family link codes.
create schema if not exists sf_private;
create table if not exists sf_private.attempts (user_id text not null, kind text not null, at timestamptz not null default now());
create index if not exists attempts_user_kind_at on sf_private.attempts (user_id, kind, at);
revoke all on sf_private.attempts from public, anon, authenticated;

-- true when this user failed [kind] at least [max_n] times in the last [minutes]
create or replace function sf_private.too_many(kind text, max_n int, minutes int) returns boolean
language sql stable security definer set search_path = public as $$
  select count(*) >= max_n from sf_private.attempts a
  where a.user_id = auth.uid()::text and a.kind = too_many.kind and a.at > now() - make_interval(mins => minutes)
$$;

-- join_family: a wrong code is recorded (the function returns instead of raising, so the record is kept);
-- after 5 wrong codes in 15 minutes the user must wait.
create or replace function public.join_family(p_code text, p_display_name text) returns text
language plpgsql security definer set search_path = public as $$
declare inv public.invites;
begin
  if auth.uid() is null then raise exception 'Not signed in'; end if;
  if sf_private.too_many('invite', 5, 15) then return 'ERR:too_many'; end if;
  select * into inv from public.invites where code = upper(p_code) and deleted_at is null for update;
  if not found then
    insert into sf_private.attempts (user_id, kind) values (auth.uid()::text, 'invite');
    return 'ERR:not_found';
  end if;
  if inv.used_by is not null then raise exception 'This invite was already used'; end if;
  if inv.expires_at < public.sf_now_ms() then raise exception 'This invite has expired'; end if;
  insert into public.family_members (id, family_id, user_id, display_name, role, status)
  values (gen_random_uuid()::text, inv.family_id, auth.uid()::text, coalesce(p_display_name, ''), inv.role, 'PENDING')
  on conflict (family_id, user_id) do update set status = 'PENDING', deleted_at = null, role = excluded.role;
  update public.invites set used_by = auth.uid()::text where code = inv.code;
  return inv.family_id;
end $$;

-- sf_family_name: only family admins (who send share offers) may look up a family's name by its link code,
-- members of that family always may, and lookups of unknown codes are limited too.
create or replace function public.sf_family_name(fid text) returns text
language plpgsql volatile security definer set search_path = public as $$
declare n text;
begin
  if auth.uid() is null then return null; end if;
  if public.sf_is_member(fid) then return (select name from public.families where id = fid and deleted_at is null); end if;
  if not exists (select 1 from public.family_members m where m.user_id = auth.uid()::text and m.status = 'ACTIVE'
                 and m.role in ('OWNER', 'ADMIN') and m.deleted_at is null) then return null; end if;
  if sf_private.too_many('family_code', 10, 15) then return null; end if;
  select name into n from public.families where id = fid and deleted_at is null;
  if n is null then insert into sf_private.attempts (user_id, kind) values (auth.uid()::text, 'family_code'); end if;
  return n;
end $$;
revoke execute on function public.sf_family_name(text) from anon, public;
grant execute on function public.sf_family_name(text) to authenticated;
revoke execute on function public.join_family(text, text) from anon, public;
grant execute on function public.join_family(text, text) to authenticated;
