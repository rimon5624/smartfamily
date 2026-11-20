-- the notify Edge Function reads chats/push_tokens with the service role
grant usage on schema public to service_role;
grant select on public.chats to service_role;
grant select, update on public.push_tokens to service_role;
