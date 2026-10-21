-- Datos opcionales de demostración. Ejecutar después de crear al menos un usuario en Auth.
do $$
declare
  selected_organization uuid;
  selected_user uuid;
  new_member uuid;
  new_plan uuid;
  new_subscription uuid;
begin
  select om.organization_id, om.user_id
    into selected_organization, selected_user
  from public.organization_members om
  where om.role = 'owner'
  order by om.created_at
  limit 1;

  if selected_organization is null then
    raise exception 'Primero crea un usuario en Authentication > Users.';
  end if;

  insert into public.members (organization_id, full_name, email, phone)
  values (selected_organization, 'Atleta de prueba', 'atleta@example.com', '3000000000')
  returning id into new_member;

  insert into public.membership_plans (organization_id, name, price)
  values (selected_organization, 'Plan mensual', 150000)
  on conflict (organization_id, name) do update set price = excluded.price
  returning id into new_plan;

  insert into public.subscriptions (
    organization_id, member_id, plan_id, agreed_amount
  ) values (
    selected_organization, new_member, new_plan, 150000
  ) returning id into new_subscription;

  insert into public.payments (
    organization_id, member_id, subscription_id, amount,
    payment_method, reference, created_by
  ) values (
    selected_organization, new_member, new_subscription, 150000,
    'transfer', 'PAGO-DEMO-001', selected_user
  );
end $$;
