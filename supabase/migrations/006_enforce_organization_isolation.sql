-- Refuerza el aislamiento multi-organización en instalaciones existentes.
-- Es idempotente y puede aplicarse aunque 001_initial_schema.sql ya esté instalado.

begin;

alter table public.organizations enable row level security;
alter table public.organization_members enable row level security;
alter table public.members enable row level security;
alter table public.membership_plans enable row level security;
alter table public.subscriptions enable row level security;
alter table public.payments enable row level security;

drop policy if exists "organizations_read_member" on public.organizations;
create policy "organizations_read_member"
on public.organizations for select to authenticated
using (public.is_organization_member(id));

drop policy if exists "organizations_update_admin" on public.organizations;
create policy "organizations_update_admin"
on public.organizations for update to authenticated
using (public.has_organization_role(id, array['owner','admin']::public.organization_role[]))
with check (public.has_organization_role(id, array['owner','admin']::public.organization_role[]));

drop policy if exists "organization_members_read" on public.organization_members;
create policy "organization_members_read"
on public.organization_members for select to authenticated
using (
  user_id = auth.uid()
  or public.has_organization_role(
    organization_id,
    array['owner','admin']::public.organization_role[]
  )
);

drop policy if exists "organization_members_manage" on public.organization_members;
create policy "organization_members_manage"
on public.organization_members for all to authenticated
using (
  public.has_organization_role(
    organization_id,
    array['owner','admin']::public.organization_role[]
  )
)
with check (
  public.has_organization_role(
    organization_id,
    array['owner','admin']::public.organization_role[]
  )
);

drop policy if exists "members_read" on public.members;
create policy "members_read"
on public.members for select to authenticated
using (public.is_organization_member(organization_id));

drop policy if exists "members_write" on public.members;
create policy "members_write"
on public.members for all to authenticated
using (
  public.has_organization_role(
    organization_id,
    array['owner','admin','staff']::public.organization_role[]
  )
)
with check (
  public.has_organization_role(
    organization_id,
    array['owner','admin','staff']::public.organization_role[]
  )
);

drop policy if exists "plans_read" on public.membership_plans;
create policy "plans_read"
on public.membership_plans for select to authenticated
using (public.is_organization_member(organization_id));

drop policy if exists "plans_write_admin" on public.membership_plans;
create policy "plans_write_admin"
on public.membership_plans for all to authenticated
using (
  public.has_organization_role(
    organization_id,
    array['owner','admin']::public.organization_role[]
  )
)
with check (
  public.has_organization_role(
    organization_id,
    array['owner','admin']::public.organization_role[]
  )
);

drop policy if exists "subscriptions_read" on public.subscriptions;
create policy "subscriptions_read"
on public.subscriptions for select to authenticated
using (public.is_organization_member(organization_id));

drop policy if exists "subscriptions_write" on public.subscriptions;
create policy "subscriptions_write"
on public.subscriptions for all to authenticated
using (
  public.has_organization_role(
    organization_id,
    array['owner','admin','staff']::public.organization_role[]
  )
)
with check (
  public.has_organization_role(
    organization_id,
    array['owner','admin','staff']::public.organization_role[]
  )
);

drop policy if exists "payments_read" on public.payments;
create policy "payments_read"
on public.payments for select to authenticated
using (public.is_organization_member(organization_id));

drop policy if exists "payments_insert" on public.payments;
create policy "payments_insert"
on public.payments for insert to authenticated
with check (
  public.has_organization_role(
    organization_id,
    array['owner','admin','staff']::public.organization_role[]
  )
  and created_by = auth.uid()
);

drop policy if exists "payments_update_admin" on public.payments;
create policy "payments_update_admin"
on public.payments for update to authenticated
using (
  public.has_organization_role(
    organization_id,
    array['owner','admin']::public.organization_role[]
  )
)
with check (
  public.has_organization_role(
    organization_id,
    array['owner','admin']::public.organization_role[]
  )
);

drop policy if exists "payments_delete_admin" on public.payments;
create policy "payments_delete_admin"
on public.payments for delete to authenticated
using (
  public.has_organization_role(
    organization_id,
    array['owner','admin']::public.organization_role[]
  )
);

revoke all on public.organizations, public.organization_members, public.members,
  public.membership_plans, public.subscriptions, public.payments from anon;

commit;

notify pgrst, 'reload schema';
