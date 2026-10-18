-- Pluton sync: one row per synced record (a card, a review, a text, a setting). The app stays local-first; this is
-- a copy that devices push to and pull from.
create table public.records (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  store text not null check (store in ('cards', 'log', 'texts', 'meta')),
  key text not null,
  data jsonb not null,
  updated_at timestamptz not null, -- device clock: when the record last changed (last write wins)
  synced_at timestamptz not null default now(), -- server clock: lets a device pull what changed since
  primary key (user_id, store, key)
);
create index records_by_sync on public.records (user_id, synced_at);

alter table public.records enable row level security;
create policy "users touch only their own records" on public.records
  for all to authenticated using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

-- Upsert a batch; an existing row is only replaced by a newer one, so an old device can't overwrite fresh progress.
create function public.push(batch jsonb) returns integer language sql security invoker set search_path = '' as $$
  with input as (
    select r ->> 'store' as store, r ->> 'key' as key, r -> 'data' as data, (r ->> 'updated_at')::timestamptz as updated_at
    from jsonb_array_elements(batch) as r
  ), written as (
    insert into public.records (user_id, store, key, data, updated_at)
    select (select auth.uid()), store, key, data, updated_at from input
    on conflict (user_id, store, key) do update
      set data = excluded.data, updated_at = excluded.updated_at, synced_at = now()
      where public.records.updated_at < excluded.updated_at
    returning 1
  )
  select count(*)::integer from written
$$;
revoke execute on function public.push(jsonb) from public, anon;
grant execute on function public.push(jsonb) to authenticated;

-- Delete the caller's account; their records go with it (on delete cascade).
create function public.delete_account() returns void language sql security definer set search_path = '' as $$
  delete from auth.users where id = (select auth.uid())
$$;
revoke execute on function public.delete_account() from public, anon;
grant execute on function public.delete_account() to authenticated;
