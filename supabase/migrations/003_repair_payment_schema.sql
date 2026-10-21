-- Repara bases existentes cuya tabla payments y vista payment_overview
-- todavía tienen la estructura anterior de la aplicación.
-- Es idempotente: se puede ejecutar aunque 002 haya fallado parcialmente.

begin;

alter table public.payments
  add column if not exists payment_type text,
  add column if not exists valid_from date,
  add column if not exists valid_until date;

update public.payments
set
  payment_type = coalesce(payment_type, 'monthly_fee'),
  valid_from = coalesce(valid_from, paid_on),
  valid_until = coalesce(valid_until, (paid_on + interval '1 month')::date)
where payment_type is null or valid_from is null or valid_until is null;

-- Corrige datos inconsistentes antes de aplicar la restricción de vigencia.
update public.payments
set valid_until = valid_from
where valid_until < valid_from;

alter table public.payments
  alter column payment_type set default 'monthly_fee',
  alter column payment_type set not null,
  alter column valid_from set default current_date,
  alter column valid_from set not null,
  alter column valid_until set default ((current_date + interval '1 month')::date),
  alter column valid_until set not null;

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'payments_type_check'
      and conrelid = 'public.payments'::regclass
  ) then
    alter table public.payments
      add constraint payments_type_check
      check (payment_type in (
        'monthly_fee', 'enrollment', 'class_package', 'personal_training', 'other'
      ));
  end if;

  if not exists (
    select 1
    from pg_constraint
    where conname = 'payments_validity_check'
      and conrelid = 'public.payments'::regclass
  ) then
    alter table public.payments
      add constraint payments_validity_check
      check (valid_until >= valid_from);
  end if;
end $$;

-- DROP + CREATE evita el error de PostgreSQL al modificar el orden de las
-- columnas de una vista existente. No se usa CASCADE para proteger objetos
-- ajenos que pudieran depender de esta vista.
drop view if exists public.payment_overview;

create view public.payment_overview
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
  p.payment_type,
  p.payment_method,
  p.valid_from,
  p.valid_until,
  p.reference,
  p.notes,
  p.created_at
from public.payments p
join public.members m
  on m.id = p.member_id
 and m.organization_id = p.organization_id
left join public.subscriptions s
  on s.id = p.subscription_id
 and s.organization_id = p.organization_id
left join public.membership_plans mp
  on mp.id = s.plan_id
 and mp.organization_id = p.organization_id;

grant select on public.payment_overview to authenticated;

comment on view public.payment_overview is
  'Vista segura usada por la aplicación Android para listar pagos recientes.';

commit;

-- Fuerza a la API de Supabase/PostgREST a detectar las columnas nuevas.
notify pgrst, 'reload schema';

-- Resultado de verificación: debe devolver las tres filas.
select column_name, data_type, is_nullable, column_default
from information_schema.columns
where table_schema = 'public'
  and table_name = 'payments'
  and column_name in ('payment_type', 'valid_from', 'valid_until')
order by column_name;
