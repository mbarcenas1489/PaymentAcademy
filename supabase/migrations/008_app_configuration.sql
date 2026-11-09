-- Crea la tabla AppConfiguration para almacenar configuraciones del sistema en formato JSON
-- e inserta el registro inicial PlaymentSetting.
-- Ejecutar en el SQL Editor de Supabase.

begin;

create table if not exists public."AppConfiguration" (
  "Id" uuid primary key default gen_random_uuid(),
  "SettingName" text not null unique,
  "value" jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now())
);

alter table public."AppConfiguration" enable row level security;

drop policy if exists "authenticated_select_app_configuration" on public."AppConfiguration";
create policy "authenticated_select_app_configuration"
  on public."AppConfiguration"
  for select
  to authenticated
  using (true);

drop policy if exists "authenticated_manage_app_configuration" on public."AppConfiguration";
create policy "authenticated_manage_app_configuration"
  on public."AppConfiguration"
  for all
  to authenticated
  using (true)
  with check (true);

grant select, insert, update on public."AppConfiguration" to authenticated;

insert into public."AppConfiguration" ("SettingName", "value")
values (
  'PlaymentSetting',
  '{
    "AmountMonthDollar": 20,
    "AmountMonthCordoba": 750,
    "AmountBiweek": 10,
    "AmountDay": 3
  }'::jsonb
)
on conflict ("SettingName") do update
set
  "value" = excluded."value",
  updated_at = timezone('utc', now());

commit;

notify pgrst, 'reload schema';
