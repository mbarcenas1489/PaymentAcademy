-- Payment Academy: esquema multi-organización para academias y gimnasios.
-- Ejecutar completo en Supabase Dashboard > SQL Editor.

create extension if not exists pgcrypto;

do $$ begin
  create type public.business_type as enum ('academy', 'gym');
exception when duplicate_object then null; end $$;
do $$ begin
  create type public.organization_role as enum ('owner', 'admin', 'staff');
exception when duplicate_object then null; end $$;
do $$ begin
  create type public.member_status as enum ('active', 'inactive', 'frozen');
exception when duplicate_object then null; end $$;
do $$ begin
  create type public.subscription_status as enum ('active', 'paused', 'cancelled', 'expired');
exception when duplicate_object then null; end $$;
do $$ begin
  create type public.payment_method as enum ('cash', 'card', 'transfer', 'online', 'other');
exception when duplicate_object then null; end $$;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.organizations (
  id uuid primary key default gen_random_uuid(),
  name text not null check (length(trim(name)) > 0),
  business_type public.business_type not null default 'academy',
  currency_code char(3) not null default 'COP',
  created_by uuid not null references public.profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.organization_members (
  organization_id uuid not null references public.organizations(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  role public.organization_role not null default 'staff',
  created_at timestamptz not null default now(),
  primary key (organization_id, user_id)
);

create table if not exists public.members (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  full_name text not null check (length(trim(full_name)) > 0),
  document_number text,
  email text,
  phone text,
  status public.member_status not null default 'active',
  joined_on date not null default current_date,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (id, organization_id)
);

create table if not exists public.membership_plans (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  name text not null check (length(trim(name)) > 0),
  price numeric(12,2) not null check (price >= 0),
  billing_period_months smallint not null default 1 check (billing_period_months > 0),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (organization_id, name),
  unique (id, organization_id)
);

create table if not exists public.subscriptions (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  member_id uuid not null,
  plan_id uuid not null,
  starts_on date not null default current_date,
  ends_on date,
  agreed_amount numeric(12,2) not null check (agreed_amount >= 0),
  status public.subscription_status not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint subscriptions_member_fk foreign key (member_id, organization_id)
    references public.members(id, organization_id) on delete cascade,
  constraint subscriptions_plan_fk foreign key (plan_id, organization_id)
    references public.membership_plans(id, organization_id),
  unique (id, organization_id)
);

create table if not exists public.payments (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  member_id uuid not null,
  subscription_id uuid,
  amount numeric(12,2) not null check (amount > 0),
  paid_on date not null default current_date,
  payment_method public.payment_method not null default 'cash',
  reference text,
  notes text,
  created_by uuid not null default auth.uid() references public.profiles(id),
  created_at timestamptz not null default now(),
  constraint payments_member_fk foreign key (member_id, organization_id)
    references public.members(id, organization_id),
  constraint payments_subscription_fk foreign key (subscription_id, organization_id)
    references public.subscriptions(id, organization_id)
);

create index if not exists members_organization_idx on public.members(organization_id);
create index if not exists subscriptions_organization_idx on public.subscriptions(organization_id);
create index if not exists subscriptions_member_idx on public.subscriptions(member_id);
create index if not exists payments_organization_paid_on_idx
  on public.payments(organization_id, paid_on desc);
create index if not exists payments_member_idx on public.payments(member_id);

create or replace function public.touch_updated_at()
returns trigger language plpgsql set search_path = public as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists profiles_touch_updated_at on public.profiles;
create trigger profiles_touch_updated_at before update on public.profiles
for each row execute function public.touch_updated_at();
drop trigger if exists organizations_touch_updated_at on public.organizations;
create trigger organizations_touch_updated_at before update on public.organizations
for each row execute function public.touch_updated_at();
drop trigger if exists members_touch_updated_at on public.members;
create trigger members_touch_updated_at before update on public.members
for each row execute function public.touch_updated_at();
drop trigger if exists plans_touch_updated_at on public.membership_plans;
create trigger plans_touch_updated_at before update on public.membership_plans
for each row execute function public.touch_updated_at();
drop trigger if exists subscriptions_touch_updated_at on public.subscriptions;
create trigger subscriptions_touch_updated_at before update on public.subscriptions
for each row execute function public.touch_updated_at();

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  new_organization_id uuid;
  organization_name text;
  selected_type public.business_type;
begin
  organization_name := coalesce(
    nullif(trim(new.raw_user_meta_data ->> 'business_name'), ''),
    split_part(coalesce(new.email, 'Mi organización'), '@', 1)
  );
  selected_type := case
    when new.raw_user_meta_data ->> 'business_type' = 'gym' then 'gym'::public.business_type
    else 'academy'::public.business_type
  end;

  insert into public.profiles (id, full_name)
  values (new.id, coalesce(new.raw_user_meta_data ->> 'full_name', ''))
  on conflict (id) do nothing;

  insert into public.organizations (name, business_type, created_by)
  values (organization_name, selected_type, new.id)
  returning id into new_organization_id;

  insert into public.organization_members (organization_id, user_id, role)
  values (new_organization_id, new.id, 'owner');
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- Crea la organización para cuentas que existían antes de ejecutar esta migración.
do $$
declare
  account record;
  new_organization_id uuid;
begin
  for account in
    select u.id, u.email, u.raw_user_meta_data
    from auth.users u
    left join public.profiles p on p.id = u.id
    where p.id is null
  loop
    insert into public.profiles (id, full_name)
    values (account.id, coalesce(account.raw_user_meta_data ->> 'full_name', ''));
    insert into public.organizations (name, business_type, created_by)
    values (
      coalesce(
        nullif(trim(account.raw_user_meta_data ->> 'business_name'), ''),
        split_part(coalesce(account.email, 'Mi organización'), '@', 1)
      ),
      case when account.raw_user_meta_data ->> 'business_type' = 'gym'
        then 'gym'::public.business_type else 'academy'::public.business_type end,
      account.id
    ) returning id into new_organization_id;
    insert into public.organization_members (organization_id, user_id, role)
    values (new_organization_id, account.id, 'owner');
  end loop;
end $$;

create or replace function public.is_organization_member(target_organization_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.organization_members
    where organization_id = target_organization_id and user_id = auth.uid()
  );
$$;

create or replace function public.has_organization_role(
  target_organization_id uuid,
  allowed_roles public.organization_role[]
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.organization_members
    where organization_id = target_organization_id
      and user_id = auth.uid()
      and role = any(allowed_roles)
  );
$$;

alter table public.profiles enable row level security;
alter table public.organizations enable row level security;
alter table public.organization_members enable row level security;
alter table public.members enable row level security;
alter table public.membership_plans enable row level security;
alter table public.subscriptions enable row level security;
alter table public.payments enable row level security;

drop policy if exists "profiles_read_own" on public.profiles;
create policy "profiles_read_own" on public.profiles for select to authenticated
using (id = auth.uid());
drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own" on public.profiles for update to authenticated
using (id = auth.uid()) with check (id = auth.uid());

drop policy if exists "organizations_read_member" on public.organizations;
create policy "organizations_read_member" on public.organizations for select to authenticated
using (public.is_organization_member(id));
drop policy if exists "organizations_update_admin" on public.organizations;
create policy "organizations_update_admin" on public.organizations for update to authenticated
using (public.has_organization_role(id, array['owner','admin']::public.organization_role[]))
with check (public.has_organization_role(id, array['owner','admin']::public.organization_role[]));

drop policy if exists "organization_members_read" on public.organization_members;
create policy "organization_members_read" on public.organization_members for select to authenticated
using (public.is_organization_member(organization_id));
drop policy if exists "organization_members_manage" on public.organization_members;
create policy "organization_members_manage" on public.organization_members for all to authenticated
using (public.has_organization_role(organization_id, array['owner','admin']::public.organization_role[]))
with check (public.has_organization_role(organization_id, array['owner','admin']::public.organization_role[]));

drop policy if exists "members_read" on public.members;
create policy "members_read" on public.members for select to authenticated
using (public.is_organization_member(organization_id));
drop policy if exists "members_write" on public.members;
create policy "members_write" on public.members for all to authenticated
using (public.has_organization_role(organization_id, array['owner','admin','staff']::public.organization_role[]))
with check (public.has_organization_role(organization_id, array['owner','admin','staff']::public.organization_role[]));

drop policy if exists "plans_read" on public.membership_plans;
create policy "plans_read" on public.membership_plans for select to authenticated
using (public.is_organization_member(organization_id));
drop policy if exists "plans_write_admin" on public.membership_plans;
create policy "plans_write_admin" on public.membership_plans for all to authenticated
using (public.has_organization_role(organization_id, array['owner','admin']::public.organization_role[]))
with check (public.has_organization_role(organization_id, array['owner','admin']::public.organization_role[]));

drop policy if exists "subscriptions_read" on public.subscriptions;
create policy "subscriptions_read" on public.subscriptions for select to authenticated
using (public.is_organization_member(organization_id));
drop policy if exists "subscriptions_write" on public.subscriptions;
create policy "subscriptions_write" on public.subscriptions for all to authenticated
using (public.has_organization_role(organization_id, array['owner','admin','staff']::public.organization_role[]))
with check (public.has_organization_role(organization_id, array['owner','admin','staff']::public.organization_role[]));

drop policy if exists "payments_read" on public.payments;
create policy "payments_read" on public.payments for select to authenticated
using (public.is_organization_member(organization_id));
drop policy if exists "payments_insert" on public.payments;
create policy "payments_insert" on public.payments for insert to authenticated
with check (
  public.has_organization_role(organization_id, array['owner','admin','staff']::public.organization_role[])
  and created_by = auth.uid()
);
drop policy if exists "payments_update_admin" on public.payments;
create policy "payments_update_admin" on public.payments for update to authenticated
using (public.has_organization_role(organization_id, array['owner','admin']::public.organization_role[]))
with check (public.has_organization_role(organization_id, array['owner','admin']::public.organization_role[]));
drop policy if exists "payments_delete_admin" on public.payments;
create policy "payments_delete_admin" on public.payments for delete to authenticated
using (public.has_organization_role(organization_id, array['owner','admin']::public.organization_role[]));

create or replace view public.payment_overview
with (security_invoker = true)
as
select
  p.id,
  p.organization_id,
  p.member_id,
  m.full_name as member_name,
  p.subscription_id,
  mp.name as plan_name,
  p.amount,
  p.paid_on,
  p.payment_method,
  p.reference,
  p.notes,
  p.created_at
from public.payments p
join public.members m on m.id = p.member_id and m.organization_id = p.organization_id
left join public.subscriptions s on s.id = p.subscription_id and s.organization_id = p.organization_id
left join public.membership_plans mp on mp.id = s.plan_id and mp.organization_id = p.organization_id;

revoke all on public.profiles, public.organizations, public.organization_members,
  public.members, public.membership_plans, public.subscriptions, public.payments
from anon;
grant select, insert, update, delete on public.profiles, public.organizations,
  public.organization_members, public.members, public.membership_plans,
  public.subscriptions, public.payments to authenticated;
grant select on public.payment_overview to authenticated;
revoke execute on function public.is_organization_member(uuid) from public, anon;
revoke execute on function public.has_organization_role(uuid, public.organization_role[]) from public, anon;
grant execute on function public.is_organization_member(uuid) to authenticated;
grant execute on function public.has_organization_role(uuid, public.organization_role[]) to authenticated;

comment on view public.payment_overview is
  'Vista segura usada por la aplicación Android para listar pagos recientes.';
