-- SmartFamily - table privileges for signed-in users (Supabase no longer grants them by default on new projects).
-- Row-level security still decides which rows each user can see or change. Safe to run again.
do $$
declare t text;
begin
  for t in select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
           where n.nspname = 'public' and c.relkind = 'r' and c.relname <> 'audit_log' loop
    execute format('grant select, insert, update, delete on public.%I to authenticated', t);
  end loop;
end $$;
grant select on public.audit_log to authenticated;
grant usage on schema public to authenticated;
grant usage, select on all sequences in schema public to authenticated;
alter default privileges in schema public grant select, insert, update, delete on tables to authenticated;
alter default privileges in schema public grant usage, select on sequences to authenticated;
