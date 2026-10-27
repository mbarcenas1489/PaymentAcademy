-- Resumen de cada miembro junto con su pago más reciente.
-- Ejecutar después de 003_repair_payment_schema.sql.

begin;

create index if not exists payments_member_latest_idx
  on public.payments (organization_id, member_id, paid_on desc, created_at desc);

drop view if exists public.member_payment_summary;

create view public.member_payment_summary
with (security_invoker = true)
as
select
  m.id as member_id,
  m.organization_id,
  m.full_name,
  m.document_number,
  m.email,
  m.phone,
  m.status as member_status,
  last_payment.id as last_payment_id,
  last_payment.amount as last_amount,
  last_payment.paid_on as last_paid_on,
  last_payment.payment_type as last_payment_type,
  last_payment.payment_method as last_payment_method,
  last_payment.valid_from as last_valid_from,
  last_payment.valid_until as last_valid_until
from public.members m
left join lateral (
  select
    p.id,
    p.amount,
    p.paid_on,
    p.payment_type,
    p.payment_method,
    p.valid_from,
    p.valid_until
  from public.payments p
  where p.organization_id = m.organization_id
    and p.member_id = m.id
  order by p.paid_on desc, p.created_at desc
  limit 1
) last_payment on true;

grant select on public.member_payment_summary to authenticated;

comment on view public.member_payment_summary is
  'Miembros y su último pago visible según las políticas RLS del usuario.';

commit;

notify pgrst, 'reload schema';
