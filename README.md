# Payment Academy

Aplicación Android para registrar miembros y controlar planes, suscripciones y pagos de una academia deportiva o gimnasio. El cliente usa Supabase Auth y PostgREST con una arquitectura MVVM.

## Configuración

1. Abre **Supabase Dashboard > SQL Editor** y ejecuta, en orden, los archivos de `supabase/migrations/`. Si ya instalaste el esquema inicial, ejecuta únicamente `002_payment_type_and_validity.sql`.
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

La opción **Gestionar pagos** permite registrar un pago mediante un flujo lineal: miembro, monto y tipo, vigencia e información adicional. La vigencia propone hoy como inicio y un mes después como fin, con ambas fechas editables. Los roles `owner` y `admin` pueden editar o eliminar; `staff` puede consultar y registrar pagos. La interfaz sigue automáticamente el modo claro u oscuro configurado en el teléfono.

La clave publicable puede estar en una app móvil; la seguridad real la aplican las políticas RLS incluidas en la migración. Nunca agregues una clave `service_role` al cliente Android.
