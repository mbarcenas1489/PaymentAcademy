# Payment Academy

Aplicación Android para registrar miembros y controlar planes, suscripciones y pagos de una academia deportiva o gimnasio. El cliente usa Supabase Auth y PostgREST con una arquitectura MVVM.

## Configuración

1. Abre **Supabase Dashboard > SQL Editor** y ejecuta, en orden, los archivos de `supabase/migrations/`. Para una instalación nueva ejecuta todas las migraciones. Si ya tienes las tablas, ejecuta `003_repair_payment_schema.sql` y después `004_member_payment_summary.sql`; la migración 004 crea el resumen eficiente de miembros y último pago que usa la pantalla principal.
2. En **Authentication > Users**, crea el usuario administrador con correo y contraseña. La migración crea automáticamente su organización y le asigna el rol `owner`.
3. Opcionalmente ejecuta `supabase/seed.sql` para crear un miembro, plan, suscripción y pago de prueba.
4. Conserva `SUPABASE_URL` y `SUPABASE_PUBLISHABLE_KEY` en `local.properties`. Este archivo está ignorado por Git.
5. Sincroniza Gradle y ejecuta la app. Ingresa con el usuario creado en el paso 2.

## Arquitectura

- `data/remote`: configuración del cliente Supabase.
- `data/repository`: acceso a Auth y PostgREST.
- `domain`: contrato del repositorio.
- `ui`: estado de pantalla y `ViewModel`.
- `MainActivity`: renderizado de la vista XML y eventos de usuario.

Desde el dashboard se abre **Administrar miembros**, donde se puede consultar el listado y registrar nuevos atletas o clientes con nombre, documento, correo, teléfono y notas.

La pantalla principal contiene dos pestañas. **Resumen** muestra el total del mes actual y cada miembro con su último pago y vigencia; al tocar un miembro se abre todo su historial y se puede agregar un pago preseleccionándolo. **Movimientos** muestra los pagos de un rango de fechas y utiliza el mes actual por defecto. El registro de pagos mantiene el flujo lineal: buscar y seleccionar miembro, monto y tipo, vigencia e información adicional. Los roles `owner` y `admin` pueden editar o eliminar; `staff` puede consultar y registrar pagos. La interfaz sigue automáticamente el modo claro u oscuro configurado en el teléfono.

La clave publicable puede estar en una app móvil; la seguridad real la aplican las políticas RLS incluidas en la migración. Nunca agregues una clave `service_role` al cliente Android.
