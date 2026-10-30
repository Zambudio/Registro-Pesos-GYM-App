# Registro de Pesos GYM

App Android nativa (Kotlin + Jetpack Compose) para registrar los pesos levantados en el gimnasio y ver la evolución. Funciona 100 % sin conexión: sin login, sin backend, sin Play Store. Idioma de la interfaz: español. Unidad: kg.

- Versión publicada: `0.1.0` (`versionCode 1`), `applicationId` `com.zambudio.registropesos`.
- Estado actual: `main` lleva los cambios de la siguiente versión (rutinas por grupos, ejercicios elegidos en la sesión, catálogo con fotos, sin "Hecha", rediseño con modo oscuro, descanso entre series y biblioteca de ejercicios), todavía sin número de versión. APK de prueba en `dist/registro-pesos-gym-v0.1.0+diseno-descanso.apk`. Detalle en [CHANGELOG.md](CHANGELOG.md).
- `minSdk 26`, `targetSdk`/`compileSdk 36`.
- Instalación en el móvil: ver [dist/INSTALAR.md](dist/INSTALAR.md).
- Decisiones técnicas (ADR-001 a 022): [docs/DECISIONES.md](docs/DECISIONES.md).
- Convenciones de código para seguir desarrollando: [docs/CONVENCIONES.md](docs/CONVENCIONES.md).
- Pendientes y siguiente versión: [docs/PENDIENTE.md](docs/PENDIENTE.md).

## Funcionalidades

- **Ejercicios y grupos musculares**: grupos precargados (Pecho, Espalda, Hombros, Bíceps, Tríceps, Cuádriceps, Isquios, Glúteos, Gemelos, Abdomen, Cardio), editables. Ficha de ejercicio con notas e imagen (galería o cámara), redimensionada a 1280 px y JPEG 80. Archivado en lugar de borrado físico.
- **Catálogo incluido**: 58 ejercicios con foto del Excel de la rutina de 5 días, con los grupos "Pierna" y "Deltoides posterior". Se carga al arrancar sin duplicar ni pisar ejercicios o fotos existentes (ADR-019).
- **Rutinas**: nombre, comentario opcional y grupos musculares ordenados. Los ejercicios no se eligen aquí.
- **Entrenar**: antes de empezar se ven los grupos y el comentario de la rutina. En la sesión, el comentario queda fijo arriba como recordatorio y cada grupo tiene un botón + para elegir ejercicios con foto; se pueden quitar con "Deshacer". Series con peso (paso 0,5, atajos +/-2,5 kg) y repeticiones; la sesión anterior se ve como referencia tenue y en cursiva, y "Copiar anterior" la convierte en datos de hoy. Guardado inmediato en cada cambio y reanudación tras cierre del proceso; resumen final con volumen y récords (ADR-018).
- **Historial**: sesiones por fecha, detalle y borrado.
- **Progreso**: gráficas Vico por ejercicio (peso máximo, volumen, 1RM estimado Epley) y por grupo (volumen semanal, sesiones), filtros 1M/3M/6M/1A/Todo y tarjetas de récord personal.
- **Descanso entre series**: cuenta atrás integrada en la sesión. Arranca sola al completar peso y repeticiones de una serie, o a mano; admite ±15 s y "Saltar". Al terminar vibra y avisa con una notificación aunque el móvil esté bloqueado (ADR-020).
- **Diseño y modo oscuro**: tema propio naranja deportivo, con cifras en Barlow Condensed. En Más se elige Sistema, Claro u Oscuro (ADR-021).
- **Biblioteca de ejercicios**: 189 ejercicios por grupo muscular, importables con un ZIP, con ilustraciones generadas aparte (ADR-022, `docs/imagenes-ejercicios/README.md`).
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

## Continuar en otro PC

El repo de GitHub (`https://github.com/Zambudio/Registro-Pesos-GYM-App`) es la fuente de verdad: contiene todo lo necesario salvo el SDK, el JDK y la clave de firma. Nada del build depende de rutas de este PC.

1. **Clonar en un disco local** (no en una unidad de red, ver ADR-002):

   ```powershell
   git clone https://github.com/Zambudio/Registro-Pesos-GYM-App.git C:\dev\Registro-Pesos-GYM-App
   ```

2. **JDK 17.** Con Android Studio vale su JDK embebido (JBR). Desde terminal, apuntar `JAVA_HOME` a un JDK 17. Con un JDK 21 en el PATH ya hubo problemas, así que mejor fijarlo.
3. **Android SDK** con `platform-tools`, `platforms/android-36` y `build-tools/36.0.0`. Hay dos formas:
   - **Android Studio:** lo instala solo y crea `local.properties`.
   - **Solo terminal:** usar las cmdline-tools actuales, que traen la nueva "Android CLI" (`sdkmanager` está deprecado):

     ```powershell
     <sdk>\cmdline-tools\latest\bin\android.exe --sdk=<sdk> sdk install platform-tools platforms/android-36 build-tools/36.0.0
     ```

     Después, fijar `ANDROID_HOME=<sdk>` o crear `local.properties` con `sdk.dir=C\:/ruta/al/sdk`. Usa barras `/`: con `\` sin escapar falla.
4. **Clave de firma.** Solo hace falta para generar el APK que se instala encima del actual.
   - Copia `gym-release.jks` y `keystore.properties` desde `Z:\IA\02_Proyectos\_secrets\Registro-Pesos-GYM-App\`, o desde tu copia de seguridad.
   - `keystore.properties` va en la raíz del repo y está ignorado por git.
   - Ajusta en él `storeFile=` a la ruta del `.jks` en ese PC. Admite una ruta absoluta o una relativa a la raíz del repo.
   - Sin `keystore.properties` todo compila igual, pero el APK de release sale **sin firmar** y no se puede instalar.
5. Compilar y probar (siguiente sección). La primera vez Gradle y Robolectric descargan dependencias (varios minutos).

Opcional: para sacar las salidas de build a otra carpeta, usa `-Pgym.buildRoot=<carpeta>` o la variable `GYM_BUILD_ROOT` (en PowerShell, el `-P…` entre comillas simples). Debe estar en la **misma unidad** que el código, porque KSP falla con unidades distintas. Por defecto se usa el `build/` estándar.

### Particularidades del PC original

Nada de esto hace falta en otro PC:

- **Repo en red:** el repo de referencia está en `Z:\IA\02_Proyectos\Registro-Pesos-GYM-App`, una unidad SMB. Allí se compilaba lento y KSP fallaba, así que se trabajó en un clon `C:\dev\Registro-Pesos-GYM-App`.
- **Sincronización Z: ↔ C:\dev:**
  - Desde `Z:`: `git pull local main`. El remote `local` es configuración local, no está en el repo.
  - Desde `C:\dev`: se empuja a `Z:`, que tiene `receive.denyCurrentBranch=updateInstead`.
- **Si se sigue en otro PC:** al volver a este, basta un `git pull` en `C:\dev` y otro en `Z:`.
- **SDK y JDK:** el SDK está en `C:\Android\sdk` y el JDK 17 en `C:\Progra~1\Java\jdk-17`.

## Compilar y probar

```powershell
$env:JAVA_HOME='<ruta al JDK 17>'
.\gradlew.bat assembleRelease test detekt
```

- La primera compilación completa ronda los 5 min; los builds incrementales, ~1,5 min.
- ~290 tests JVM/Robolectric.
- Galería de capturas (claro y oscuro, sin dispositivo): `.\gradlew.bat :app:testDebugUnitTest --tests "*ScreenshotGallery*" -Pgym.screenshots=true` deja los PNG en `app/build/screenshots`. Sin la propiedad, esos tests se omiten. Los de Robolectric fijan `@Config(sdk = [35], application = Application::class)` (4.16 falla con SDK 36 en JDK 17).
- detekt se ejecuta con Kotlin 2.0.21 forzado en su classpath: no usar sintaxis exclusiva de Kotlin 2.2 (context parameters, guard en `when`, `$$`).
- `allWarningsAsErrors = true` en todo `:app`.
- Lint de release (`lintVitalRelease`): las rutas `<exclude>` de las reglas de backup deben estar dentro de un `<include>`; no se excluye `exports/` porque vive en caché y ya queda fuera.

## Firma y release

- Keystore: `C:\Users\pedrojavier.zambudio\.android-keystores\gym-release.jks` (alias `gym-release`, PKCS12, RSA 4096).
- Credenciales en `keystore.properties` en la raíz del repo (ignorado por git, igual que `*.jks`).
- Copia de ambos ficheros fuera del repo: `Z:\IA\02_Proyectos\_secrets\Registro-Pesos-GYM-App\`.
- Huella SHA-256 del certificado: `a6438d2bb7177f904040395cef156ff8a973f92b56d8923cf21d52766de5f86e` (comprobar con `apksigner verify --print-certs`).
- **Si se pierde el keystore no se podrán actualizar las instalaciones existentes ni restaurar Auto Backup**: Android exige el mismo firmante.
- APK release: `app\build\outputs\apk\release\app-release.apk` (o `<gym.buildRoot>\app\outputs\...` si se redirige el build); se copia a `dist\registro-pesos-gym-vX.Y.Z.apk`. R8 desactivado en v0.1.
- Nueva versión: subir `versionCode` (siempre +1) y `versionName` en `app/build.gradle.kts`, compilar, copiar el APK a `dist/`, actualizar `dist/INSTALAR.md` y crear el tag `vX.Y.Z`.

## Copias de seguridad

- **Auto Backup** (`allowBackup=true`, reglas en `res/xml/backup_rules.xml` y `data_extraction_rules.xml`): respalda BD Room, `images/` y `datastore/`, nunca la caché. **La restauración solo funciona si el APK instalado está firmado con la misma clave** que el original.
- **Exportar** (pestaña Más): ZIP `gym-backup-YYYYMMDD-HHmm.zip` con `backup.json` (con `schemaVersion`, hoy 2; los ZIP v1 de la 0.1.0 se siguen importando), `sets.csv` y `images/`. Se guarda con el selector de documentos (SAF) y se puede compartir.
- **Importar**: merge por UUID; gana el `updatedAt` más reciente; respeta borrados (tombstones); no pisa una sesión en curso; muestra un informe. Rechaza ZIP con rutas peligrosas o entradas desmesuradas.

## Política de migraciones de base de datos

- `exportSchema = true`; los esquemas se versionan en `app/schemas/` (hay que commitear cada `N.json`).
- Prohibido `fallbackToDestructiveMigration`. Las migraciones son explícitas en `Migrations.ALL_MIGRATIONS`. La BD va por la versión 2 (`MIGRATION_1_2`, ADR-018).
- Para la versión siguiente: subir `VERSION`, añadir `Migration(n, n+1)` y commitear `<n+1>.json`. `MigrationTest` (Robolectric + `MigrationTestHelper`) valida cada salto contra el esquema exportado y prueba la 1→2 con datos de la v1. Los esquemas se exponen como assets solo de la variante debug; en Windows `createDatabase` necesita rutas absolutas.

## Catálogo de ejercicios

`app/src/main/assets/catalog/` (`catalog.json` + `images/`) se genera desde el Excel con `tools/catalog/` (ADR-019). Hace falta Node 18+ y Windows PowerShell:

```powershell
node tools/catalog/extract-catalog.mjs "docs\261013 Registro Pesos - Rutina 5 Dias.xlsx"
powershell -File tools/catalog/convert-images.ps1
```

No reordenes ni renumeres los ids ya publicados: las instalaciones existentes se basan en ellos para no duplicar ejercicios.

## Biblioteca de ejercicios

`tools/library/exercises.json` (189 ejercicios) se distribuye como un ZIP importable, `dist/biblioteca-ejercicios.zip`. Se regenera a partir de una copia exportada desde la app, que aporta los ids de los grupos y de los ejercicios existentes:

```powershell
node tools/library/build-backup.mjs --base "docs\gym-backup-AAAAMMDD-HHmm.zip"
```

Con `--with-routines` genera `dist/rutinas-y-ejercicios.zip`, que además lleva las rutinas de la copia para compartir la configuración completa. Ninguno de los dos ZIP lleva sesiones, series ni borrados.

Las ilustraciones (`docs/imagenes-ejercicios/imagenes/<código>.png`) no se suben a git. El flujo completo está en `docs/imagenes-ejercicios/README.md`.

## Notas de desarrollo

- `hiltViewModel` de `androidx.hilt.navigation.compose` está deprecado en hilt-navigation-compose 1.3.0: usar `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`.
- En tests de ViewModel, `toRoute()` del `SavedStateHandle` (Navigation 2.9) necesita Robolectric.

## Licencia

Ver `LICENSE`.
