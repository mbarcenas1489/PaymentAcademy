-- Agrega soporte para pagos parciales (abonos) y bimoneda (NIO/USD) con impacto mínimo en el esquema.
-- Ejecutar en el SQL Editor de Supabase.

begin;

alter table public.payments
  add column if not exists pending_amount numeric(12, 2) not null default 0
  check (pending_amount >= 0);

alter table public.payments
  add column if not exists currency text not null default 'NIO'
  check (currency in ('NIO', 'USD'));

comment on column public.payments.pending_amount is
  'Saldo pendiente por pagar del período. Si es 0, el pago está completo.';

comment on column public.payments.currency is
  'Moneda del pago: NIO (Córdobas) o USD (Dólares).';

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
  last_payment.pending_amount as last_pending_amount,
  last_payment.currency as last_currency,
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
    p.pending_amount,
    p.currency,
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

commit;

notify pgrst, 'reload schema';
