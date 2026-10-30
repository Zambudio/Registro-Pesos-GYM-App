# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

App Android nativa (Kotlin + Jetpack Compose) para registrar pesos del gimnasio. 100 % offline, sin backend. UI en español, unidad kg fija. Un solo módulo Gradle `:app`, paquete `com.zambudio.registropesos`.

Documentación que manda (leer antes de cambios no triviales):
- `docs/CONVENCIONES.md`: reglas de código a respetar (estructura, UI/estado, datos, calidad, git).
- `docs/DECISIONES.md`: ADR-001 a 022; citar/añadir ADR cuando se tome una decisión de fondo.
- `docs/PENDIENTE.md`: deuda técnica conocida e ideas para la v0.2.
- `README.md`: stack con versiones, firma/release, backups, puesta en marcha en otro PC.

## Comandos

Requiere JDK 17 (no 21): fijar `JAVA_HOME` antes. En el PC original: `C:\Progra~1\Java\jdk-17`, SDK en `C:\Android\sdk`.

```powershell
$env:JAVA_HOME='C:\Progra~1\Java\jdk-17'
.\gradlew.bat assembleRelease test detekt      # verificación completa antes de commitear
.\gradlew.bat assembleDebug
.\gradlew.bat detekt
.\gradlew.bat :app:testDebugUnitTest --tests "com.zambudio.registropesos.domain.usecase.PersonalRecordsTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*WorkoutRepositoryTest.someTestName"
```

- Solo existen tests JVM/Robolectric de la variante **debug** (el unit test de release está desactivado en `app/build.gradle.kts`, ADR-015). No hay tests instrumentados.
- `allWarningsAsErrors = true`: cualquier aviso (incluidas deprecaciones) rompe el build.
- detekt corre con Kotlin 2.0.21 forzado: nada de sintaxis exclusiva de Kotlin 2.2 (context parameters, guards en `when`, `$$`).
- **Repo en unidad de red `Z:` (SMB)**: compilar aquí es lento y KSP falla. El trabajo de build se hace en el clon `C:\dev\Registro-Pesos-GYM-App` y se sincroniza por git (ver README, "Particularidades del PC original", y ADR-002). `-Pgym.buildRoot` / `GYM_BUILD_ROOT` redirigen las salidas, pero deben estar en la misma unidad que el código.
- Sin `keystore.properties` en la raíz, el APK release sale sin firmar (no instalable encima del existente).

## Arquitectura

Capas package-by-feature bajo `app/src/main/java/com/zambudio/registropesos/`:

- `domain/`: Kotlin puro. Contratos en `model/Models.kt`, `repository/Repositories.kt` (interfaces), `backup/BackupSnapshot.kt`. Casos de uso en `usecase/` (e1RM Epley, volumen, PRs, sesión anterior, ciclo de vida de sesión `StartSession`/`FinishSession`/descartar, serializador de backup, CSV). `util/Providers.kt` define `TimeProvider` e `IdGenerator` para inyectar tiempo e ids (tests deterministas).
- `data/`: Room (`local/entity/Entities.kt`, `local/dao/`), `repository/*Impl`, `mapper/` (entidad↔dominio y backup), `image/AndroidImageStore`, `backup/BackupMerger` (merge de importación).
- `core/`: `db` (AppDatabase, `Migrations.ALL_MIGRATIONS`, `SeedCallback` con grupos musculares), `di` (módulos Hilt; qualifiers `@IoDispatcher`, `@ApplicationScope`), `navigation` (rutas tipadas `@Serializable` en `Routes.kt`, `AppNavHost`, `GymAppScaffold` con la barra inferior, `NavigationGuards`), `ui` (`StateViews`, `ObserveAsEvents`), `util/Format.kt` (formato con coma decimal, solo para mostrar).
- `feature/<x>/`: `exercises`, `routines`, `workout`, `history`, `progress`, `backup`, `settings`. Cada una con ViewModel(s), pantallas y `navigation/<X>Navigation.kt`.

Patrones transversales que no son obvios leyendo un solo fichero:
- **Pantallas**: `XRoute(viewModel = hiltViewModel(), …)` conecta el ViewModel; `XScreen(state, callbacks)` es puro (se testea con `createComposeRule()` y tiene `@Preview`). `UiState` inmutable en `StateFlow` con `stateIn(..., WhileSubscribed(5_000), …)`; eventos one-shot por `Channel` consumidos con `ObserveAsEvents`. `hiltViewModel` de `androidx.hilt.lifecycle.viewmodel.compose` (el de navigation-compose está deprecado).
- **Navegación**: acciones del usuario envueltas en `dropUnlessResumed {}`; cierres disparados por eventos del ViewModel con `popBackStack<Ruta>(inclusive = true)` (ADR-014).
- **Rutinas y sesión (BD v2, ADR-018)**: una rutina es nombre + comentario (`routines.subtitle`) + grupos ordenados; `routine_exercises` es legado de la v1 (se conserva, no se usa). `StartSession` copia los grupos a `workout_session_groups` y el comentario a `routine_comment_snapshot`; la sesión empieza sin ejercicios. Elegir un ejercicio en el selector del grupo = crear sus filas vacías (`WorkoutViewModel.onPickExercise`); no existe tabla de "ejercicios de la sesión". `workout_sets.is_done/completed_at` son columnas heredadas sin uso ("Hecha" se retiró).
- **Sesión de entrenamiento**: cada cambio de serie se guarda al instante; las escrituras van por una cola FIFO en el `@ApplicationScope CoroutineScope` para no perderse al salir de la pantalla (ADR-013). La copia en memoria del ViewModel es la verdad de la pantalla. Solo puede haber una sesión `IN_PROGRESS`, que se reanuda tras matar el proceso.
- **Diseño (ADR-021)**: `core/theme` (paleta naranja, Barlow Condensed para títulos y cifras) y `core/ui/Components.kt` (`GymCard`, `SectionHeader`, `IconBadge`, `HeroCard`...). En las pantallas no hay colores fijos. `AppSettings.themeMode` lo aplica `MainActivity`. Para revisar el aspecto sin dispositivo: `--tests "*ScreenshotGallery*" -Pgym.screenshots=true` → `app/build/screenshots/*.png` (con Read se ven las imágenes).
- **Descanso (ADR-020)**: `RestTimer` (dominio) → `data/rest/AndroidRestTimer`, con el estado en DataStore, alarma exacta, `RestTimerReceiver` y notificaciones (`RestAlerts`). `WorkoutViewModel` lo arranca al pasar una serie a completa (`WorkoutSet.isComplete`).
- **Biblioteca (ADR-022)**: `tools/library/exercises.json` + `build-backup.mjs` generan `dist/biblioteca-ejercicios.zip` (formato de copia de seguridad, ids fijos), a partir de una copia exportada; `--with-routines` añade las rutinas (`dist/rutinas-y-ejercicios.zip`, ignorado por git), nunca sesiones ni tombstones. No va dentro del APK.
- **Catálogo incluido (ADR-019)**: `assets/catalog/` (58 ejercicios con foto, ids fijos) generado con `tools/catalog/` desde el Excel de `docs/`; `data/catalog/CatalogSeeder` lo siembra en cada arranque desde `GymApplication`, sin duplicar ni modificar ejercicios existentes. No renumerar ids publicados.
- **Modelo de datos**: PK UUID + `createdAt`/`updatedAt` en todas las tablas; los borrados dejan tombstone en `deleted_entities` (ADR-004); ejercicios se archivan, no se borran. Prohibido `@Upsert` y `OnConflictStrategy.REPLACE` (ADR-009).
- **Backup**: export ZIP (`backup.json` con `schemaVersion` + `sets.csv` + `images/`) vía SAF; import = merge por UUID, gana el `updatedAt` más reciente, respeta tombstones, no pisa la sesión en curso. Si cambia el formato, subir `schemaVersion` y seguir aceptando ZIP antiguos. Auto Backup depende de `res/xml/backup_rules.xml` y `data_extraction_rules.xml` (lint exige que cada `<exclude>` esté dentro de un `<include>`).
- **Imágenes**: guardadas en almacenamiento interno con ruta relativa (ADR-007), redimensionadas a 1280 px, JPEG 80.

## Migraciones de Room

`exportSchema = true`, esquemas en `app/schemas/` (también expuestos como assets solo en debug para `MigrationTest`). Nunca `fallbackToDestructiveMigration`. Cada cambio de esquema: subir `VERSION` en `AppDatabase`, añadir `Migration(n, n+1)` a `Migrations.ALL_MIGRATIONS` y commitear el `<n+1>.json` generado. La BD va por la v2; la SQL de cada migración debe coincidir exactamente con el `createSql` del JSON exportado (`MigrationTest` lo valida y prueba la 1→2 con datos de la v1).

## Tests

En `app/src/test/`. ViewModels y casos de uso con fakes (`domain/usecase/FakeRepositories.kt`, `TestData.kt`, `feature/exercises/ExercisesFakes.kt`) y `kotlinx-coroutines-test`. Los tests de repositorio heredan de `data/DataTestBase.kt` (Room en memoria). Los de Robolectric llevan `@RunWith(AndroidJUnit4::class)` + `@Config(sdk = [35], application = Application::class)` (Robolectric 4.16 falla con SDK 36 en JDK 17, ADR-012). `toRoute()` sobre `SavedStateHandle` en tests de ViewModel necesita Robolectric.

## Strings, git y release

- Textos visibles solo en `res/values/strings_<feature>.xml` con prefijo `<feature>_`; nada hardcodeado en Kotlin.
- Commits convencionales (`feat(workout): …`, `fix(backup): …`).
- Nueva versión: `versionCode` +1 y `versionName` en `app/build.gradle.kts`, compilar release firmado, copiar APK a `dist/registro-pesos-gym-vX.Y.Z.apk`, actualizar `dist/INSTALAR.md`, tag `vX.Y.Z`. Perder el keystore impide actualizar instalaciones y restaurar Auto Backup.
