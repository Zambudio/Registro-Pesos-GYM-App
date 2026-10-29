# Convenciones de código

Reglas que siguió la v0.1.0 y que conviene mantener al seguir desarrollando (a mano o con agentes de IA). Las decisiones de fondo están en `DECISIONES.md`.

## Estructura
- Un solo módulo `:app`, paquete raíz `com.zambudio.registropesos`, package-by-feature: `core/`, `data/`, `domain/`, `feature/<x>/`.
- Contratos en `domain/model/Models.kt`, `domain/repository/Repositories.kt`, `domain/backup/BackupSnapshot.kt`. Los casos de uso viven en `domain/usecase/`. Hilt provee repositorios, `TimeProvider`, `IdGenerator` e `ImageStore`.
- Cada feature tiene su `feature/<x>/navigation/<X>Navigation.kt`; todas las rutas tipadas están en `core/navigation/Routes.kt`.
- Piezas comunes:
  - `core/ui/StateViews.kt` (`LoadingState`, `EmptyState`, `ErrorState`, `ConfirmDialog`)
  - `core/ui/ObserveAsEvents.kt`
  - `core/util/Format.kt` (`formatKg`, `formatDuration`, `formatDate`, `parseDecimalInput`…; coma decimal, solo para mostrar)

## UI y estado
- MVVM + UDF:
  - `@HiltViewModel`.
  - `UiState` inmutable en `StateFlow`, con `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), …)`.
  - Eventos one-shot con `Channel<Event>(Channel.BUFFERED).receiveAsFlow()`, consumidos con `ObserveAsEvents`.
- Pantalla = `XRoute(viewModel = hiltViewModel(), onNavigate…)` + `XScreen(state, callbacks)` sin ViewModel (testeable y con `@Preview`). Sin lógica de negocio en Composables.
- `hiltViewModel` de `androidx.hilt.lifecycle.viewmodel.compose` (el de `hilt.navigation.compose` está deprecado).
- Cada pantalla cubre cargando / vacío / error y gestiona su propio `Scaffold` + `TopAppBar`; el scaffold raíz solo pone la barra inferior.
- Navegación disparada por el usuario: `dropUnlessResumed { … }`. Cierres disparados por un evento del ViewModel: `popBackStack<Ruta>(inclusive = true)` (ADR-014).
- Escrituras que no deben perderse al salir de la pantalla: `@ApplicationScope CoroutineScope` (ADR-013).
- Textos visibles siempre en `res/values/strings_<feature>.xml` con prefijo `<feature>_`; nada hardcodeado en Kotlin.
- Iconos `Icons.AutoMirrored.*` donde existan. `contentDescription` en iconos-botón y objetivos táctiles ≥ 48 dp.

## Datos
- PK UUID y `createdAt`/`updatedAt` en todas las tablas; tombstones en `deleted_entities` (ADR-004).
- Sin `@Upsert` ni `REPLACE` (ADR-009).
- Sin `fallbackToDestructiveMigration`. Cada cambio de esquema implica:
  - subir `VERSION`;
  - añadir una `Migration(n, n+1)` en `Migrations.ALL_MIGRATIONS`;
  - commitear `app/schemas/.../<n+1>.json`.

  `MigrationTest` lo valida.
- Si el esquema del backup cambia, sube `schemaVersion` y mantén la importación de ZIP antiguos.

## Calidad
- `allWarningsAsErrors = true`: cero avisos, incluidas deprecaciones.
- detekt limpio (`config/detekt/detekt.yml`). Corre con Kotlin 2.0.21, así que no uses sintaxis exclusiva de 2.2 (context parameters, guard en `when`, `$$`).
- Tests en `app/src/test/…`:
  - **ViewModels:** con fakes (`domain/usecase/FakeRepositories.kt`, `TestData.kt`) y `kotlinx-coroutines-test`.
  - **Robolectric:** `@RunWith(AndroidJUnit4::class)` + `@Config(sdk = [35], application = Application::class)`.
  - **Compose UI:** `createComposeRule()` sobre `XScreen`.
- Antes de commitear: `.\gradlew.bat assembleRelease test detekt` en verde.

## Git
- Commits convencionales (`feat(workout): …`, `fix(backup): …`).
- Tag `vX.Y.Z` por cada APK publicado en `dist/`.
