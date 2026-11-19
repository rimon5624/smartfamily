-- SmartFamily - security hardening (applied after 0004_app_0_4.sql). Safe to run again.

-- Fixed search_path on the timestamp helpers.
alter function public.sf_now_ms() set search_path = public, pg_temp;
alter function public.sf_touch() set search_path = public, pg_temp;

-- Membership helpers are used inside row-level security policies: signed-in users need them,
-- anonymous callers do not.
revoke execute on function public.sf_is_member(text) from public, anon;
revoke execute on function public.sf_is_admin(text) from public, anon;
revoke execute on function public.sf_shares_family(text) from public, anon;
revoke execute on function public.sf_member_role_status(text) from public, anon;
revoke execute on function public.sf_my_role(text) from public, anon;
grant execute on function public.sf_is_member(text) to authenticated;
grant execute on function public.sf_is_admin(text) to authenticated;
grant execute on function public.sf_shares_family(text) to authenticated;
grant execute on function public.sf_member_role_status(text) to authenticated;
grant execute on function public.sf_my_role(text) to authenticated;
revoke execute on function public.join_family(text, text) from anon;

-- Only reveal a member's role/status to that member or to people in the same family.
create or replace function public.sf_member_role_status(mid text) returns text
language sql stable security definer set search_path = public as $$
  select m.role || '|' || m.status from public.family_members m
  where m.id = mid and (m.user_id = auth.uid()::text or public.sf_is_member(m.family_id))
$$;
