-- 0.6.0: private bucket for end-to-end encrypted chat media.
-- Files are encrypted on the phone before upload; the path starts with the family id,
-- so only members of that family can upload or download them.
insert into storage.buckets (id, name, public, file_size_limit)
values ('chat-media', 'chat-media', false, 26214400)
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit;

drop policy if exists chat_media_select on storage.objects;
create policy chat_media_select on storage.objects for select to authenticated
  using (bucket_id = 'chat-media' and public.sf_is_member((storage.foldername(name))[1]));

drop policy if exists chat_media_insert on storage.objects;
create policy chat_media_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'chat-media' and public.sf_is_member((storage.foldername(name))[1]));
