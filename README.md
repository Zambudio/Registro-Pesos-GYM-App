# Registro de Pesos GYM

App Android nativa (Kotlin + Jetpack Compose) para registrar los pesos levantados en el gimnasio y ver la evolución. Funciona 100 % sin conexión: sin login, sin backend, sin Play Store. Idioma de la interfaz: español. Unidad: kg.

- Versión actual: `0.1.0` (`versionCode 1`), `applicationId` `com.zambudio.registropesos`.
- `minSdk 26`, `targetSdk`/`compileSdk 36`.
- Instalación en el móvil: ver [dist/INSTALAR.md](dist/INSTALAR.md).
- Decisiones técnicas (ADR-001 a 012): [docs/DECISIONES.md](docs/DECISIONES.md).

## Funcionalidades

- **Ejercicios y grupos musculares**: grupos precargados (Pecho, Espalda, Hombros, Bíceps, Tríceps, Cuádriceps, Isquios, Glúteos, Gemelos, Abdomen, Cardio), editables. Ficha de ejercicio con notas e imagen (galería o cámara), redimensionada a 1280 px y JPEG 80. Archivado en lugar de borrado físico.
- **Rutinas**: nombre, subtítulo y bloques ordenados (grupo muscular + ejercicios + series por defecto).
- **Entrenar**: series con peso (paso 0,5, atajos +/-2,5 kg), repeticiones y check de hecha; "sesión anterior" en gris y botón "Copiar anterior"; guardado inmediato en cada cambio y reanudación tras cierre del proceso; resumen final con volumen y récords.
- **Historial**: sesiones por fecha, detalle y borrado.
- **Progreso**: gráficas Vico por ejercicio (peso máximo, volumen, 1RM estimado Epley) y por grupo (volumen semanal, sesiones), filtros 1M/3M/6M/1A/Todo y tarjetas de récord personal.
- **Copias de seguridad**: Auto Backup de Android y exportación/importación ZIP (ver más abajo).

## Stack

Kotlin 2.2.21, AGP 8.13.2, Gradle 8.14.3 (wrapper), Compose BOM 2025.12.01 + Material 3, Navigation Compose 2.9.6 con rutas tipadas (`@Serializable`), Hilt 2.57.2, Room 2.8.4 + KSP, kotlinx.serialization, DataStore Preferences, Coil 3, Vico 2.3.6, detekt 1.23.8, Robolectric 4.16. Versiones en `gradle/libs.versions.toml`.

## Estructura de paquetes

Un solo módulo Gradle `:app`, package-by-feature dentro de capas, en `app/src/main/java/com/zambudio/registropesos/`:

| Paquete | Contenido |
|---|---|
| `core/` | `db` (AppDatabase, migraciones), `di` (módulos Hilt), `navigation` (rutas tipadas, scaffold), `theme`, `ui`, `util` |
| `data/` | `local` (entidades y DAOs Room), `repository` (implementaciones), `mapper`, `image` (almacén de imágenes), `backup` (merge de importación) |
| `domain/` | `model` puros, `repository` (interfaces), `usecase` (e1RM, volumen, PRs, sesión anterior, ciclo de sesión, serializador de backup, CSV), `backup`, `util` |
| `feature/` | `exercises`, `routines`, `workout`, `history`, `progress`, `backup`, `settings`: ViewModel + pantallas + navegación de cada función |

MVVM + flujo unidireccional: `UiState` inmutable en `StateFlow`, eventos one-shot por `Channel`, sin lógica de negocio en los Composables.

## Preparar el entorno (Windows)

1. **JDK 17** (no el 21 del PATH): usar `C:\Progra~1\Java\jdk-17`.
2. **Android SDK** en `C:\Android\sdk` con las cmdline-tools actuales (16111833). Traen la nueva "Android CLI"; `sdkmanager` está deprecado y `--list` no devuelve nada útil:

   ```powershell
   C:\Android\sdk\cmdline-tools\latest\bin\android.exe --sdk=C:\Android\sdk sdk install platform-tools platforms/android-36 build-tools/36.0.0
   ```

   Las licencias se aceptan automáticamente. Fijar `ANDROID_HOME` a nivel usuario: `setx ANDROID_HOME C:\Android\sdk`.
3. **Mitigación SMB**: el repo de referencia está en `Z:\IA\02_Proyectos\Registro-Pesos-GYM-App` (unidad de red). Con el código en `Z:` y el directorio de build en `C:`, KSP2 falla (`this and base files have different roots`) y el primer build tarda ~7 min. Por eso se trabaja en un clon local:
   - Clon de trabajo: `C:\dev\Registro-Pesos-GYM-App`; build dir en `C:\build\gym` (se cambia con `-Pgym.buildRoot=...` o la variable `GYM_BUILD_ROOT`).
   - Gradle siempre con `--project-cache-dir C:\build\gym\.gradle`.
   - Sincronización con `Z:` por git: el repo de `Z:` tiene `receive.denyCurrentBranch=updateInstead` y el clon de `C:` empuja allí; en sentido inverso, `git pull local main` (remote `local` en el repo de `Z:`). Un `git clone` directo desde `Z:` falla por `safe.directory` (propietario SMB distinto).
   - En PowerShell, `-Pgym.buildRoot=...` debe ir entre comillas simples.

## Compilar y probar

```powershell
$env:JAVA_HOME='C:\Progra~1\Java\jdk-17'; $env:ANDROID_HOME='C:\Android\sdk'
.\gradlew.bat assembleRelease test detekt --project-cache-dir C:\build\gym\.gradle
```

- Los builds incrementales rondan 1,5 min.
- ~240 tests JVM/Robolectric. Los de Robolectric fijan `@Config(sdk = [35], application = Application::class)` (4.16 falla con SDK 36 en JDK 17).
- detekt se ejecuta con Kotlin 2.0.21 forzado en su classpath: no usar sintaxis exclusiva de Kotlin 2.2 (context parameters, guard en `when`, `$$`).
- `allWarningsAsErrors = true` en todo `:app`.
- Lint de release (`lintVitalRelease`): las rutas `<exclude>` de las reglas de backup deben estar dentro de un `<include>`; no se excluye `exports/` porque vive en caché y ya queda fuera.

## Firma y release

- Keystore: `C:\Users\pedrojavier.zambudio\.android-keystores\gym-release.jks` (alias `gym-release`, PKCS12, RSA 4096).
- Credenciales en `keystore.properties` en la raíz del repo (ignorado por git, igual que `*.jks`).
- Copia de ambos ficheros fuera del repo: `Z:\IA\02_Proyectos\_secrets\Registro-Pesos-GYM-App\`.
- Huella SHA-256 del certificado: `a6438d2bb7177f904040395cef156ff8a973f92b56d8923cf21d52766de5f86e` (comprobar con `apksigner verify --print-certs`).
- **Si se pierde el keystore no se podrán actualizar las instalaciones existentes ni restaurar Auto Backup**: Android exige el mismo firmante.
- APK release: `C:\build\gym\app\outputs\apk\release\app-release.apk`; se copia a `dist\registro-pesos-gym-v0.1.0.apk`. R8 desactivado en v0.1.

## Copias de seguridad

- **Auto Backup** (`allowBackup=true`, reglas en `res/xml/backup_rules.xml` y `data_extraction_rules.xml`): respalda BD Room, `images/` y `datastore/`, nunca la caché. **La restauración solo funciona si el APK instalado está firmado con la misma clave** que el original.
- **Exportar** (pestaña Más): ZIP `gym-backup-YYYYMMDD-HHmm.zip` con `backup.json` (con `schemaVersion`), `sets.csv` y `images/`. Se guarda con el selector de documentos (SAF) y se puede compartir.
- **Importar**: merge por UUID; gana el `updatedAt` más reciente; respeta borrados (tombstones); no pisa una sesión en curso; muestra un informe. Rechaza ZIP con rutas peligrosas o entradas desmesuradas.

## Política de migraciones de base de datos

- `exportSchema = true`; los esquemas se versionan en `app/schemas/` (hay que commitear cada `N.json`).
- Prohibido `fallbackToDestructiveMigration`. Las migraciones son explícitas en `Migrations.ALL_MIGRATIONS` (vacío en la v1).
- Para pasar a la versión 2: subir `VERSION` de la base de datos, añadir `Migration(1, 2)` y commitear `2.json`. `MigrationTest` (Robolectric + `MigrationTestHelper`) valida el salto sin cambios. Los esquemas se exponen como assets solo de la variante debug; en Windows `createDatabase` necesita rutas absolutas.

## Notas de desarrollo

- `hiltViewModel` de `androidx.hilt.navigation.compose` está deprecado en hilt-navigation-compose 1.3.0: usar `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`.
- En tests de ViewModel, `toRoute()` del `SavedStateHandle` (Navigation 2.9) necesita Robolectric.

## Licencia

Ver `LICENSE`.
