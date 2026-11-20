-- SmartFamily 2.1.2 - instant chat notifications through Firebase (free). Applied via MCP on 2026-11-20.
-- Only a wake-up with the chat id is sent (Edge Function "notify"); the text stays end-to-end encrypted.
create extension if not exists pg_net with schema extensions;
create table if not exists public.push_tokens (
  id text primary key, user_id text not null, token text not null,
  updated_at bigint not null default public.sf_now_ms(), deleted_at bigint
);
create index if not exists push_tokens_user_idx on public.push_tokens(user_id);
drop trigger if exists sf_touch_push_tokens on public.push_tokens;
create trigger sf_touch_push_tokens before insert or update on public.push_tokens for each row execute function public.sf_touch();
alter table public.push_tokens enable row level security;
drop policy if exists push_tokens_own on public.push_tokens;
create policy push_tokens_own on public.push_tokens for all to authenticated using (user_id = auth.uid()::text) with check (user_id = auth.uid()::text);
grant select, insert, update, delete on public.push_tokens to authenticated;
create schema if not exists sf_private;
revoke all on schema sf_private from public, anon, authenticated;
grant usage on schema sf_private to service_role;
create table if not exists sf_private.config (key text primary key, value text not null);
grant select on sf_private.config to service_role;
insert into sf_private.config(key, value) values ('hook_secret', encode(extensions.gen_random_bytes(24), 'hex')) on conflict (key) do nothing;
create or replace function public.sf_hook_ok(secret text) returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from sf_private.config where key = 'hook_secret' and value = secret) $$;
revoke all on function public.sf_hook_ok(text) from public, anon, authenticated;
grant execute on function public.sf_hook_ok(text) to service_role;
create or replace function public.sf_notify_chat() returns trigger language plpgsql security definer set search_path = public, extensions as $$
declare s text;
begin
  select value into s from sf_private.config where key = 'hook_secret';
  perform net.http_post(url := 'https://giqafbtcyimbifyjvpgo.supabase.co/functions/v1/notify',
    body := jsonb_build_object('chat_id', new.chat_id, 'sender_id', new.sender_id),
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-sf-hook', s));
  return new;
end $$;
revoke all on function public.sf_notify_chat() from public, anon, authenticated;
drop trigger if exists sf_notify_chat on public.chat_messages;
create trigger sf_notify_chat after insert on public.chat_messages for each row execute function public.sf_notify_chat();
