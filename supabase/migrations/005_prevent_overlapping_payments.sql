-- Impide registrar vigencias superpuestas para un mismo miembro.
-- El bloqueo transaccional evita que dos solicitudes simultáneas pasen la validación.

begin;

create or replace function public.prevent_overlapping_payment_validity()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  perform pg_advisory_xact_lock(
    hashtextextended(new.organization_id::text || ':' || new.member_id::text, 0)
  );

  if exists (
    select 1
    from public.payments existing
    where existing.organization_id = new.organization_id
      and existing.member_id = new.member_id
      and existing.id is distinct from new.id
      and existing.valid_from <= new.valid_until
      and existing.valid_until >= new.valid_from
  ) then
    raise exception using
      errcode = '23P01',
      message = 'Ya existe un pago para este miembro dentro del período de vigencia seleccionado.',
      hint = 'Selecciona fechas que no se superpongan con otro pago.';
  end if;

  return new;
end;
$$;

drop trigger if exists payments_prevent_overlapping_validity on public.payments;
create trigger payments_prevent_overlapping_validity
before insert or update of organization_id, member_id, valid_from, valid_until
on public.payments
for each row execute function public.prevent_overlapping_payment_validity();

commit;

notify pgrst, 'reload schema';
