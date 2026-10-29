# Decisiones técnicas (ADR cortos)

Formato: contexto / decisión / consecuencias. Las decisiones del stack de `docs/PROMPT_KICKOFF.md` §2 se dan por adoptadas; aquí solo constan las tomadas durante la implementación.

## ADR-001 — Versiones de la toolchain
- **Contexto**: a fecha de inicio (2026-10) ya existen AGP 9.x y Gradle 9.x, con Kotlin integrado en AGP y cambios de DSL. El objetivo es un APK fiable para el día siguiente.
- **Decisión**: stack maduro y probado en conjunto: AGP 8.13.2, Gradle 8.14.3, Kotlin 2.2.21, KSP 2.2.21-2.0.5, Hilt 2.57.2, Room 2.8.4, Compose BOM 2025.12.01, Navigation 2.9.6, Vico 2.3.6, Robolectric 4.16, detekt 1.23.8. `compileSdk`/`targetSdk` 36 (estable; 37 requiere AGP 9). SDK: platform-tools 37.0.1, build-tools 36.0.0.
- **Consecuencias**: subir a AGP 9 / Kotlin 2.3+ es una tarea aparte de v0.2 (migrar a Kotlin integrado en AGP).

## ADR-002 — Repositorio en SMB: clon local en `C:\dev` (plan B activo)
- **Contexto**: el repo vive en `Z:` (SMB). Se intentó el plan A (código en `Z:`, `buildDirectory` en `C:\build\gym`). El primer build tardó 6,8 min y **KSP2 falla** con `this and base files have different roots` porque el proyecto (Z:) y las fuentes generadas (C:) están en unidades distintas.
- **Decisión**: plan B. Se trabaja en `C:\dev\Registro-Pesos-GYM-App` y se sincroniza por git con `Z:` (remote `local` en el repo de Z:, `git pull local main` tras cada fase). La redirección de `buildDirectory` a `C:\build\gym` se conserva (misma unidad que `C:\dev`, configurable con `-Pgym.buildRoot`). Gradle siempre con `--project-cache-dir C:\build\gym\.gradle`.
- **Consecuencias**: builds incrementales de ~1,5 min. `Z:` es la copia de respaldo/visible del workspace; el trabajo diario se hace en `C:\dev`.

## ADR-003 — Toolchain del SDK con la nueva "Android CLI"
- **Contexto**: las cmdline-tools actuales (16111833) sustituyen `sdkmanager` por `android sdk`; `sdkmanager --list` no devuelve nada útil.
- **Decisión**: instalación con `android.exe --sdk=C:\Android\sdk sdk install platform-tools platforms/android-36 build-tools/36.0.0`. `ANDROID_HOME` fijado a nivel usuario.
- **Consecuencias**: documentado en la Vault (`Android-SDK-Setup-CLI-Windows`).

## ADR-004 — Timestamps y tombstones en todas las tablas
- **Contexto**: el merge por UUID del import y un sync futuro necesitan `updatedAt` en todo y saber qué se borró.
- **Decisión** (propuesta del arquitecto): `createdAt`/`updatedAt` en las 7 tablas. `Routine` (+bloques+ejercicios) y `WorkoutSession` (+series) se tratan como agregados: un cambio en un hijo toca el `updatedAt` del padre y el merge reemplaza los hijos en bloque. Tabla `deleted_entities(id, entityType, deletedAt)` para tombstones de rutinas, sesiones y grupos.
- **Consecuencias**: los borrados físicos (con CASCADE) se mantienen; el import respeta borrados posteriores.

## ADR-005 — Integridad referencial
- **Decisión**: FKs `Exercise→MuscleGroup` RESTRICT; `RoutineBlock→Routine` CASCADE, `→MuscleGroup` RESTRICT; `RoutineExercise→Block` CASCADE, `→Exercise` RESTRICT; `WorkoutSession.routineId` **SET NULL** (el nombre queda en el snapshot); `WorkoutSet→Session` CASCADE, `→Exercise` RESTRICT. Índices en todas las FK. Ejercicios nunca se borran (solo `isArchived`).
- **Una sola sesión IN_PROGRESS**: se garantiza en una transacción del DAO (no con índice parcial, que choca con la validación de esquema de Room).
- **Grupos por defecto con UUID fijos** en el seed, para que dos instalaciones no dupliquen "Pecho" al importar. Al importar, un grupo con mismo nombre (sin mayúsculas) y otro id se remapea al local.

## ADR-006 — Series con peso/reps anulables y orden de ejercicio
- **Contexto**: hay que distinguir "fila vacía" de "0 kg" (peso corporal) y mostrar el *ghost* de la sesión anterior en filas sin rellenar. Además la sesión debe conservar el orden de la rutina aunque luego se edite la rutina.
- **Decisión**: `WorkoutSet.weightKg: Double?`, `reps: Int?`; serie vacía = sin peso útil y sin reps. Se añade `WorkoutSet.exerciseOrder`. Al empezar se crean `defaultSets` filas vacías por ejercicio. Al terminar se borran las vacías.
- **Consecuencias**: desviación menor del modelo de §2 del kickoff, justificada.

## ADR-007 — Imágenes con ruta relativa
- **Decisión**: `Exercise.imagePath` guarda el nombre relativo a `filesDir/images`. Redimensionado a 1280 px lado mayor, JPEG 80.
- **Consecuencias**: Auto Backup y ZIP portables entre dispositivos.

## ADR-008 — "Warnings como errores"
- **Contexto**: en un módulo único no se puede activar `-Werror` solo para el paquete `domain`.
- **Decisión**: `allWarningsAsErrors = true` para todo `:app` (más estricto que lo pedido) + detekt con la config por defecto ajustada a Compose.
- **Consecuencias**: el código no puede dejar avisos de compilación. detekt 1.23.8 se ejecuta con Kotlin 2.0.21 forzado en su classpath: no usar sintaxis exclusiva de Kotlin 2.2 (context parameters, guard en `when`, `$$`).

## ADR-009 — Escrituras sin `@Upsert` ni `REPLACE`
- **Contexto**: `@Upsert` descarta en silencio una fila que choca con un índice único (p. ej. una serie con el mismo `set_index`) y `REPLACE` borra la fila y dispara los `CASCADE`.
- **Decisión**: los DAOs hacen *update* y, si no había fila, *insert*. Sin índice único de `sort_order` en bloques/ejercicios de rutina (dificulta reemplazar hijos); `RoutineRepository.save` renumera `sortOrder` según la posición.
- **Consecuencias**: un guardado nunca borra hijos por efecto lateral.

## ADR-010 — Import conservador
- **Decisión**: seed de grupos con `updatedAt = 0` (un grupo por defecto sin tocar nunca pisa una edición real). El remapeo de grupos por nombre solo se aplica si el id entrante no existe en local. Una sesión IN_PROGRESS local nunca se pisa ni se borra por un import. En agregados importados se omite solo el hijo con FK rota (con aviso). Una sesión cuya rutina no existe se guarda con `routineId = null`.

## ADR-011 — Infraestructura de migraciones probada
- **Contexto**: requisito duro de no perder datos al actualizar.
- **Decisión**: `Migrations.ALL_MIGRATIONS` explícito (vacío en v1) y `MigrationTest` (Robolectric + `MigrationTestHelper`): comprueba que existe el esquema exportado de cada versión, que hay `Migration(n, n+1)` para cada salto, valida cada migración contra el esquema exportado y que una BD con datos sobrevive a reabrirse con la app actual. Los esquemas se exponen como assets solo de la variante `debug` (Robolectric lee los assets de la variante, no los de `test`); el APK release no los incluye. En Windows hay que pasar rutas absolutas a `createDatabase`.
- **Consecuencias**: para crear la v2 basta con subir `VERSION`, añadir la `Migration(1, 2)` y commitear `2.json`; el test la valida sin cambios.

## ADR-012 — Robolectric con SDK 35
- **Contexto**: Robolectric 4.16 falla con el SDK 36 en esta máquina (JDK 17).
- **Decisión**: los tests Robolectric fijan `@Config(sdk = [35], application = Application::class)` (sin Hilt en tests).

## ADR-013 — Escrituras de la sesión en un scope de aplicación
- **Contexto**: la revisión detectó que las escrituras de series en cola (cola FIFO del `WorkoutViewModel`) se cancelaban al salir de la pantalla porque corrían en `viewModelScope`.
- **Decisión**: `@ApplicationScope CoroutineScope` (SupervisorJob + IO) en `core/di`; el consumidor de la cola corre ahí y `onCleared()` cierra el canal para que se vacíe y termine. Importar backup (BD + copia de fotos) corre en `NonCancellable` y bloquea el back mientras trabaja.
- **Consecuencias**: ninguna pulsación registrada en la UI se pierde al salir de la sesión.

## ADR-014 — Navegación robusta ante doble toque
- **Decisión**: navegaciones disparadas por el usuario envueltas en `dropUnlessResumed`; cierres por evento del ViewModel con `popBackStack<Ruta>(inclusive = true)`, idempotente.
- **Consecuencias**: un doble toque en "atrás" ya no vacía el `NavHost` (pantalla en blanco).

## ADR-015 — Tests unitarios solo en la variante debug
- **Contexto**: `./gradlew test` también lanzaba `testReleaseUnitTest`, donde no existen `ui-test-manifest` ni los esquemas de Room como assets (solo debug): 15 fallos espurios.
- **Decisión**: `androidComponents.beforeVariants` desactiva los host tests de release.
- **Consecuencias**: `./gradlew assembleRelease test detekt` verde desde cero (5,2 min sin caché, 242 tests).

## ADR-016 — Release v0.1.0
- **Decisión**: APK release sin R8, firmado (esquema v2, suficiente para minSdk 26) con `gym-release.jks` (RSA 4096, PKCS12, fuera del repo; copia en `Z:\IA\02_Proyectos\_secrets\Registro-Pesos-GYM-App\`). Entregable versionado en `dist/registro-pesos-gym-v0.1.0.apk` (excepción explícita en `.gitignore`).

## ADR-017 — Build portable entre PCs
- **Contexto**: el desarrollo puede seguir en otro PC. El `buildDirectory` tenía por defecto `C:/build/gym`, una ruta propia de este PC (ADR-002).
- **Decisión**: la redirección del build pasa a ser opcional (`-Pgym.buildRoot` / `GYM_BUILD_ROOT`); sin ella se usa el `build/` estándar. `storeFile` de `keystore.properties` admite rutas relativas a la raíz del repo. Sin `keystore.properties` el release compila sin firmar.
- **Consecuencias**: un clon nuevo de GitHub compila con solo JDK 17 y el SDK. Los pasos están en el README, sección "Continuar en otro PC". En el clon `C:\dev` de este PC el build pasa a `C:\dev\Registro-Pesos-GYM-App\**\build` (misma unidad, sin problema con KSP). `C:\build\gym*` quedó obsoleto y se borró.

## ADR-018 — Rutinas por grupos y ejercicios elegidos durante la sesión (BD v2)
- **Contexto**: tras usar la v0.1.0, la rutina debe ser solo nombre, comentario y grupos musculares ordenados; los ejercicios se eligen en el gimnasio, dentro de cada grupo. Se retira la casilla "Hecha". Hay instalaciones con datos de la v1 que no se pueden perder.
- **Decisión**:
  - El comentario de la rutina es el campo existente `routines.subtitle` (sin cambio de esquema).
  - `routine_exercises` se conserva (tabla, backup y borrador del editor), pero ya no se edita ni se usa al empezar: las rutinas de la v1 mantienen sus filas sin efecto.
  - Nueva tabla `workout_session_groups` (sesión CASCADE, grupo SET NULL + nombre en snapshot): al empezar se copian los grupos de la rutina, sin repetir. Nueva columna `workout_sessions.routine_comment_snapshot`: el recordatorio no cambia aunque luego se edite la rutina.
  - Elegir un ejercicio = crear sus filas vacías (`AppSettings.defaultSets`) por la cola FIFO de siempre (ADR-013). No hay tabla de "ejercicios de la sesión": la elección se guarda igual que las series y se reanuda igual. Un ejercicio nunca se duplica (comprobación en memoria + índice único). Quitar un ejercicio borra sus filas, pide confirmación si tiene datos y ofrece "Deshacer".
  - "Hecha" sale del dominio y de la UI. `workout_sets.is_done/completed_at` se quedan en el esquema sin uso (SQLite de API 26 no tiene DROP COLUMN y reconstruir la tabla no aporta nada): las filas existentes conservan su valor y las nuevas llevan false/null. Terminar sigue conservando solo las series con peso o reps.
  - `Migration(1, 2)`: añade la tabla y la columna; a una sesión en curso de la v1 le crea sus grupos (los de sus ejercicios, en orden) y le copia el comentario de su rutina. No borra ni modifica ninguna fila existente.
  - Backup `schemaVersion` 2: `sessionGroups` y `SessionDto.routineCommentSnapshot`. Un ZIP v1 se importa igual (campos nuevos por defecto; no toca los grupos de sesión locales) e `isDone` se sigue exportando e importando tal cual.
- **Consecuencias**: la sesión ya no depende de que la rutina tenga ejercicios. Un ejercicio cuyo grupo no está previsto (sesión antigua o ejercicio movido de grupo) se muestra al final, sin botón de añadir. Cubierto por `MigrationTest`, `WorkoutRepositoryTest`, `BackupRepositoryTest`, `WorkoutViewModelTest` y `WorkoutResumeAfterProcessDeathTest`.

## ADR-019 — Catálogo de ejercicios incluido en la app
- **Contexto**: el Excel "261013 Registro Pesos - Rutina 5 Dias.xlsx" (pestañas "Día 1" a "Día 5") tiene 74 filas de ejercicio con foto, que son 58 combinaciones distintas de grupo y nombre y 51 imágenes. Deben estar en el móvil sin conexión y sin el Excel.
- **Decisión**:
  - `tools/catalog/extract-catalog.mjs` (Node, sin dependencias) lee solo grupo, nombre e imagen anclada de las filas con foto, deduplica por (grupo, nombre) conservando los nombres literales y escribe `app/src/main/assets/catalog/catalog.json`. `tools/catalog/convert-images.ps1` pasa las imágenes a JPEG 80 con lado mayor ≤ 1280 px (1,6 MB en total). Las cuatro notas fuera de las tablas no tienen foto y no entran.
  - Ids fijos: ejercicios `5f0c2a9e-7d41-4b8e-a3c6-0000000000NN`; los grupos existentes usan los ids del seed y hay dos nuevos, "Pierna" (`…000000000012`) y "Deltoides posterior" (`…000000000013`), sin reasignar sus ejercicios a otros grupos. No se renumeran nunca las entradas publicadas.
  - `CatalogSeeder` se ejecuta en cada arranque (`GymApplication`, scope de aplicación) y es idempotente: crea un ejercicio solo si no existe su id ni otro con el mismo nombre en su grupo; nunca modifica uno existente ni su foto; reutiliza el grupo por id o por nombre; no recrea un grupo borrado (tombstone). Copia la foto a `filesDir/images/exercise_<id>.jpg` antes de insertar la fila. Marcas de tiempo 0, como el seed de grupos (ADR-010).
- **Consecuencias**: el APK crece ~1,6 MB. Las fotos del catálogo viajan en Auto Backup y en el ZIP como cualquier otra. `CatalogSeederTest` comprueba recuento, asociación nombre-imagen e idempotencia. Cómo regenerarlo: README, sección "Catálogo de ejercicios".
