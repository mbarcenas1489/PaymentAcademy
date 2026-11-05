# Payment Academy: contexto para agentes

Este archivo es la fuente de contexto técnico para cualquier IA o agente que trabaje en el repositorio. Léelo completo antes de modificar código, base de datos o sincronización.

## Objetivo del producto

Payment Academy es una aplicación Android para academias deportivas y gimnasios. Permite autenticar administradores, registrar miembros, registrar y consultar pagos, controlar su vigencia y compartir comprobantes como imágenes por WhatsApp.

La aplicación es multi-organización y offline-first. Supabase es la fuente remota de verdad, pero las pantallas leen principalmente desde SQLite para seguir funcionando sin conexión. La separación por `organization_id` es un requisito de seguridad, no una preferencia de implementación.

## Estado funcional actual

- Inicio y restauración de sesión con Supabase Auth usando correo y contraseña.
- Una organización se crea automáticamente para cada usuario nuevo mediante el trigger `handle_new_user`.
- Roles remotos: `owner`, `admin` y `staff`.
- Listado y registro de miembros.
- Dashboard con total pagado en el mes, resumen por miembro y movimientos por rango de fechas.
- Historial general o filtrado por miembro.
- Creación y edición de pagos mediante un formulario de pasos.
- Eliminación local con borrado lógico y sincronización posterior.
- Validación local y remota para impedir vigencias de pagos superpuestas.
- Sincronización offline con WorkManager.
- Estado global de sincronización con acciones para reintentar.
- Auditoría de solo lectura entre identificadores locales y remotos.
- Protección de cambios pendientes antes de cerrar sesión o cambiar de cuenta.
- Acciones de pagos adaptadas al rol de la organización.
- Generación de comprobantes PNG al guardar un pago.
- Regeneración de comprobantes de pagos existentes desde el menú de tres puntos.
- Envío directo a WhatsApp, WhatsApp Business o, como respaldo, el selector del sistema.
- Temas claro y oscuro mediante recursos Android.

## Stack y configuración

- Kotlin y vistas XML tradicionales; no usa Jetpack Compose.
- Patrón aproximado MVVM con `Activity`, `ViewModel`, `StateFlow` y repositorios.
- Android Gradle Plugin 9.4.1.
- `minSdk 27`, `targetSdk 37`, `compileSdk 37`.
- Java 11.
- View Binding activado.
- Supabase Kotlin 3.8.0: Auth y PostgREST.
- WorkManager 2.9.0.
- SQLite mediante `SQLiteOpenHelper`; Room está declarado como dependencia, pero no se utiliza.
- JUnit 4 para pruebas unitarias.

Las credenciales se cargan desde `local.properties`:

```properties
SUPABASE_URL=https://...
SUPABASE_PUBLISHABLE_KEY=...
```

`local.properties` no debe versionarse. Nunca colocar una clave `service_role` dentro de la app Android.

## Mapa del código

### Pantallas

- `MainActivity.kt`: login, restauración de sesión, dashboard, pestañas Resumen/Movimientos, filtro de fechas, edición/eliminación/compartir pagos y cierre de sesión.
- `MembersActivity.kt`: listado de miembros y acceso al registro.
- `RegisterMemberActivity.kt`: formulario para crear miembros.
- `PaymentsActivity.kt`: listado general o historial de un miembro; permite crear, editar, eliminar y compartir pagos.
- `PaymentFormActivity.kt`: formulario de pago en cuatro pasos. Si se recibe un miembro bloqueado comienza en el paso de monto. Tras guardar muestra la opción de enviar el comprobante.

### Capa UI

- `ui/PaymentViewModel.kt`: autenticación y carga del dashboard.
- `ui/PaymentsViewModel.kt`: historial de pagos y borrado.
- `ui/PaymentFormViewModel.kt`: validación y guardado de pagos.
- `ui/MembersViewModel.kt`: lista de miembros.
- `ui/RegisterMemberViewModel.kt`: validación y registro de miembros.
- `ui/*Adapter.kt`: adaptadores de RecyclerView.
- `ui/*Factories.kt`: creación manual de ViewModels con repositorios.

### Datos y dominio

- `data/remote/SupabaseProvider.kt`: cliente único de Supabase.
- `data/local/DatabaseHelper.kt`: SQLite `paymentacademy.db`, actualmente versión 2.
- `data/repository/OrganizationScope.kt`: resuelve y persiste la organización asociada al usuario autenticado.
- `data/repository/SupabaseMemberRepository.kt`: miembros locales/remotos.
- `data/repository/SupabasePaymentRepository.kt`: Auth, pagos, resúmenes y sincronización.
- `worker/SyncWorker.kt`: sincronización única en segundo plano.
- `domain/MemberRepository.kt` y `domain/PaymentRepository.kt`: contratos consumidos por los ViewModels.
- `data/model/*`: modelos serializables de miembros, pagos y resúmenes.

### Comprobantes

- `receipt/PaymentReceiptSharer.kt`: crea un bitmap de 1080 × 1350 con Canvas, lo escribe como PNG en `cacheDir/shared_receipts` y comparte un `content://` temporal.
- `res/xml/file_paths.xml`: limita el `FileProvider` a la carpeta de comprobantes del caché.
- `AndroidManifest.xml`: declara el `FileProvider` no exportado.
- `res/menu/payment_card_actions.xml`: acciones Editar, Enviar comprobante y Eliminar.

## Flujo de datos y sincronización

### Organización activa

`OrganizationScope` obtiene el usuario desde la sesión Supabase y consulta su fila en `organization_members`. Guarda `user_id`, `organization_id` y `role` en SharedPreferences `organization_scope` y mantiene la membresía en memoria con protección mediante `Mutex`. Si una instalación anterior aún no tiene el rol almacenado y está sin conexión, conserva el acceso local por organización y asume temporalmente `staff` para no habilitar acciones privilegiadas.

Actualmente la app admite una sola organización activa. Si un usuario pertenece a varias organizaciones, se usa la primera membresía devuelta por Supabase; todavía no existe selector de organización.

Regla obligatoria: toda consulta, inserción, actualización, eliminación y sincronización local o remota debe incluir el `organization_id` activo. Nunca deducir la organización tomando el primer miembro o pago guardado localmente.

### SQLite

Tablas locales:

- `members`: incluye `organization_id` y `sync_pending`.
- `payments`: incluye `organization_id`, `sync_pending` e `is_deleted`.

Hay índices por organización y por organización/miembro. Las tablas remotas de planes y suscripciones no tienen copia local ni interfaz completa en la app.

Las lecturas de pantalla se hacen desde SQLite. Los repositorios programan sincronización en segundo plano y devuelven los datos locales de la organización activa.

### Escrituras offline

- Un miembro nuevo se inserta localmente con `sync_pending = 1`, se intenta subir inmediatamente y se agenda WorkManager si falla o para confirmar sincronización.
- Un pago nuevo se inserta localmente con `sync_pending = 1` y se agenda WorkManager.
- Una edición de pago actualiza el registro local y marca `sync_pending = 1`.
- Una eliminación marca `is_deleted = 1` y `sync_pending = 1`; el worker elimina el registro remoto y después el local.
- El icono de nube en cada pago indica si todavía está pendiente de sincronización.

`SyncWorker` requiere red, utiliza trabajo único con el nombre `PaymentAcademySync` y política `KEEP`. Primero sincroniza miembros y después pagos para respetar las claves foráneas. Si no existe sesión devuelve éxito sin tocar datos; ante errores devuelve `retry()`.

El botón **Verificar** ejecuta `auditSynchronization()`. Compara los IDs de miembros y pagos de la organización activa entre SQLite y Supabase. Distingue registros pendientes de registros que figuraban sincronizados y detecta faltantes en ambos sentidos. Esta auditoría es estrictamente de solo lectura: no debe convertirse en una reparación automática sin respaldo, revisión del reporte y autorización explícita.

### Inicio y cierre de sesión

Al iniciar sesión:

1. Se autentica con Supabase.
2. Se cancela cualquier worker anterior.
3. Se comprueba que no existan cambios locales pendientes de una sesión anterior.
4. Se limpia únicamente el contexto de organización; el caché SQLite sincronizado se conserva y permanece aislado por `organization_id`.
5. Se descargan miembros y pagos de la nueva organización.
6. Si la sincronización inicial falla, se agenda WorkManager.

Antes de cerrar sesión se cuentan los miembros y pagos pendientes. Si existen, la UI exige sincronizarlos y no ofrece una salida destructiva. El repositorio vuelve a validar la condición como defensa adicional. Solo cuando no quedan pendientes se cancela el worker, se cierra la sesión y se limpian SharedPreferences y SQLite.

Advertencia: cualquier registro que exista solamente en SQLite y continúe con `sync_pending = 1` puede perderse si el usuario cierra sesión o cambia de cuenta. Antes de cambiar esta lógica o indicar al usuario que cierre sesión, verificar que los iconos de sincronización estén completos. No introducir borrados adicionales del caché sin considerar datos pendientes.

## Modelo remoto y seguridad

Las migraciones viven en `supabase/migrations/` y deben aplicarse en orden:

1. `001_initial_schema.sql`: tipos, tablas, funciones, creación automática de organización, RLS y vistas iniciales.
2. `002_payment_type_and_validity.sql`: concepto y vigencia del pago.
3. `003_repair_payment_schema.sql`: reparación idempotente para instalaciones existentes.
4. `004_member_payment_summary.sql`: vista del último pago por miembro.
5. `005_prevent_overlapping_payments.sql`: trigger transaccional contra vigencias superpuestas.
6. `006_enforce_organization_isolation.sql`: reactiva y reconstruye las políticas RLS multi-organización.

Tablas remotas principales:

- `profiles`
- `organizations`
- `organization_members`
- `members`
- `membership_plans`
- `subscriptions`
- `payments`

`organization_members` relaciona usuarios con organizaciones y roles. Las tablas de negocio llevan `organization_id`. Los pagos también contienen `created_by` y claves foráneas compuestas que impiden asociar miembros, planes o suscripciones de organizaciones diferentes.

RLS debe permanecer activado. La app utiliza una clave publicable y depende de las políticas para la seguridad real. Los filtros Android son defensa adicional y evitan mezclar el caché, pero no sustituyen RLS.

Permisos previstos:

- Todos los miembros de una organización pueden leer sus datos.
- `owner`, `admin` y `staff` pueden registrar miembros y pagos.
- Solo `owner` y `admin` pueden editar o eliminar pagos remotamente.
- Solo `owner` y `admin` administran membresías y planes.

La interfaz consulta el rol de `organization_members`. Editar y Eliminar se ocultan para `staff`, el toque directo sobre una tarjeta no abre edición y el repositorio vuelve a comprobar el rol antes de modificar SQLite. RLS continúa siendo la protección definitiva.

Las vistas `payment_overview` y `member_payment_summary` existen en Supabase con `security_invoker = true`, pero la implementación Android actual arma los resúmenes desde SQLite; no asumir que las vistas se consumen en producción.

## Reglas de pagos

- `amount` debe ser mayor que cero.
- `paid_on`, `valid_from` y `valid_until` usan ISO `yyyy-MM-dd` internamente.
- `valid_until` no puede ser anterior a `valid_from`.
- No pueden existir períodos de vigencia superpuestos para el mismo miembro y organización.
- La validación de superposición existe en SQLite para respuesta rápida y en PostgreSQL mediante trigger para garantizar consistencia concurrente.
- Tipos soportados: `monthly_fee`, `enrollment`, `class_package`, `personal_training`, `other`.
- Métodos soportados: `cash`, `card`, `transfer`, `online`, `other`.
- Los importes se muestran como moneda `es-CO`/COP.

## Comprobantes y WhatsApp

El comprobante incluye miembro, monto, concepto, método, fecha de pago, vigencia y referencia opcional. Se puede generar inmediatamente después de guardar o desde los tres puntos de un pago existente.

El envío prueba en este orden:

1. WhatsApp: `com.whatsapp`.
2. WhatsApp Business: `com.whatsapp.w4b`.
3. Selector genérico de Android.

No se solicitan permisos de almacenamiento. El archivo vive en caché, se comparte con `FLAG_GRANT_READ_URI_PERMISSION` y el generador elimina comprobantes anteriores antes de crear uno nuevo.

Si se agregan datos al comprobante, actualizar tanto `PaymentReceiptData` como la conversión desde `PaymentSummary` y el flujo posterior al guardado en `PaymentFormActivity`.

## Convenciones para cambios

- Conservar la arquitectura existente salvo que el cambio justifique una refactorización mayor.
- Usar View Binding y recursos de `strings.xml`, `colors.xml` y `dimens.xml`; evitar textos visibles hardcodeados nuevos.
- Mantener las fechas persistidas en formato ISO y localizar solamente al presentar.
- Mantener la defensa multi-organización en Supabase y SQLite.
- No modificar migraciones ya aplicadas para reparar producción; crear una nueva migración idempotente.
- No borrar ni recrear la base local durante un upgrade. `DatabaseHelper.onUpgrade` debe ser incremental.
- Respetar datos pendientes de sincronización.
- No añadir secretos al repositorio ni imprimir tokens en logs.
- Preservar cambios ajenos y archivos de IDE no relacionados.
- Para compartir archivos externos usar `FileProvider`; nunca rutas `file://`.

## Validación antes de entregar

Desde PowerShell en la raíz:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
git diff --check
```

APK de desarrollo:

`app/build/outputs/apk/debug/app-debug.apk`

Pruebas manuales mínimas para cambios de pagos o sincronización:

1. Iniciar sesión y confirmar que solo aparecen miembros/pagos de la organización activa.
2. Crear un miembro y un pago sin red; confirmar los indicadores pendientes.
3. Recuperar la red y verificar que ambos cambian a sincronizados.
4. Intentar crear vigencias superpuestas y confirmar que se rechazan.
5. Generar el comprobante al guardar y desde los tres puntos de un pago existente.
6. Probar WhatsApp normal, Business o el selector genérico.
7. Cerrar sesión únicamente después de sincronizar y verificar que el siguiente usuario no ve el caché anterior.
8. Ejecutar **Verificar** y confirmar que el reporte no modifica registros aunque encuentre diferencias.

## Limitaciones y deuda técnica conocida

- Solo hay una organización activa por sesión y no existe selector para usuarios multi-organización.
- No existe interfaz completa para planes, suscripciones ni administración de usuarios/roles.
- El rol se aplica a pagos; todavía no existe una pantalla para administrar usuarios o cambiar roles.
- SQLite está implementado manualmente aunque Room figure en dependencias.
- WorkManager aparece dos veces en `app/build.gradle.kts`; es una duplicación inocua pendiente de limpieza.
- La cobertura automática es reducida: hay pruebas de validación de registro, pero faltan pruebas de repositorios, SQLite, sincronización, RLS y comprobantes.
- Si la sincronización inicial falla después del login, el dashboard puede aparecer vacío hasta que WorkManager termine y el usuario actualice la pantalla.
- Supabase CLI no está instalado ni enlazado en este workspace; las migraciones se aplican desde SQL Editor o desde un pipeline externo.

