-- 2.2 live chat: the app listens for changes on these tables (rows stay encrypted; RLS applies)
alter publication supabase_realtime add table public.chat_messages, public.chat_reactions, public.chat_reads, public.chats, public.chat_keys;
