-- SmartFamily 2.0.1 - voice notes, photos and videos in a chat shared between families: the other family may upload and read too.
drop policy if exists chat_media_insert on storage.objects;
create policy chat_media_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'chat-media' and (public.sf_is_member((storage.foldername(name))[1]) or public.sf_linked((storage.foldername(name))[1], 'chat')));
drop policy if exists chat_media_select on storage.objects;
create policy chat_media_select on storage.objects for select to authenticated
  using (bucket_id = 'chat-media' and (public.sf_is_member((storage.foldername(name))[1]) or public.sf_linked((storage.foldername(name))[1], 'chat')));
