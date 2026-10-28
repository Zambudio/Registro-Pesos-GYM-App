# PROMPT DE ARRANQUE — Registro-Pesos-GYM-App (Android nativa)

> Pégalo tal cual a Opus 5.5 en la sesión de Claude Code abierta en `Z:\IA\02_Proyectos\Registro-Pesos-GYM-App`.

---

Eres el orquestador de este proyecto. Trabajas **sin parar y sin preguntarme** hasta entregar un APK firmado instalable que pueda usar mañana en el gimnasio. Todas las decisiones no cubiertas aquí las tomas tú con el criterio "lo más simple que sea correcto, escalable y mantenible", y las anotas en `docs/DECISIONES.md` (formato ADR corto: contexto / decisión / consecuencias). Nunca te bloquees esperando confirmación: si algo falla, diagnostica, prueba la alternativa indicada y sigue.

## 0. Contexto obligatorio

- Repo: `Z:\IA\02_Proyectos\Registro-Pesos-GYM-App` (ya enlazado a `https://github.com/Zambudio/Registro-Pesos-GYM-App.git`, rama `main`, solo contiene `LICENSE` y este `docs/`).
- Antes de diseñar nada, lee `Z:\IA\02_Proyectos\CLAUDE.md` y `OBSIDIAN VAULT/index.md`. Al terminar cada fase documentas en la Vault (ver §8).
- Máquina: Windows 11, PowerShell/Git Bash, **JDK 17 en `JAVA_HOME=C:\Progra~1\Java\jdk-17`** (úsalo; no uses el JDK 21 del PATH), Node 24. **NO hay Android SDK, `adb`, `gradle` ni emulador instalados.** Instálalos tú (§2, Fase 0).
- El repo está en una unidad de red SMB (`Z:`). Gradle sobre SMB es lento y puede fallar por bloqueos de fichero. Aplica obligatoriamente la mitigación de §2.
- Idioma de la UI: **español**. Unidad: **kg** (fija). Idioma del código, commits y docs técnicas: inglés para identificadores, español para comentarios/docs si el contexto lo pide; sé consistente.

## 1. Qué es la app (requisitos funcionales)

App Android para registrar los pesos levantados en el gimnasio y ver la evolución.

### 1.1 Ejercicios y grupos musculares
- Grupos musculares pre-cargados (seed en la primera ejecución): Pecho, Espalda, Hombros, Bíceps, Tríceps, Cuádriceps, Isquios, Glúteos, Gemelos, Abdomen, Cardio. Editables (crear, renombrar, reordenar; no se puede borrar un grupo con ejercicios).
- Desde el panel principal: "Añadir ejercicio" → eliges grupo muscular → ficha de ejercicio: nombre (obligatorio), notas (opcional), imagen opcional **desde galería** (Photo Picker) **o foto con la cámara** (para fotografiar la máquina). La imagen se guarda en el almacenamiento interno de la app, redimensionada a máx. 1280 px lado mayor, JPEG calidad 80 (para que quepa en Auto Backup).
- Listado de ejercicios agrupado por grupo muscular, con búsqueda. Editar y archivar (soft delete) ejercicios; un ejercicio con historial nunca se borra físicamente.

### 1.2 Rutinas (planes de entrenamiento)
- Rutina = nombre (p. ej. "Día 1" o "Pecho - Tríceps") + subtítulo/nota libre (p. ej. "4 ejer pecho, 2 tríceps") + lista ordenada de **bloques**.
- Bloque = un grupo muscular + lista ordenada de ejercicios de ese grupo (elegidos de la biblioteca) + nº de series por defecto por ejercicio (por defecto 4).
- Se pueden añadir tantos bloques como se quiera, reordenar bloques y ejercicios, editar y borrar rutinas (borrar rutina NO borra su historial).

### 1.3 Entrenar (sesión)
- Panel principal: lista de rutinas → entrar en una muestra nombre, subtítulo y bloques. "Empezar entrenamiento" crea una `WorkoutSession` con `startedAt` = fecha/hora del móvil.
- Dentro del bloque se listan sus ejercicios; en cada ejercicio hay filas = series, cada fila con **peso (kg, decimal, paso 0,5)** y **repeticiones (entero)** y un check "hecha". Botones: añadir serie, quitar serie, +2,5 / −2,5 kg.
- **Sesión anterior en gris**: al abrir un ejercicio se muestra, como placeholder/ghost en cada fila, lo que se hizo la última vez en ese ejercicio (peso×reps por serie) y un botón "Copiar anterior" que rellena todas las filas.
- **Persistencia continua**: cada cambio en una serie se guarda en Room inmediatamente. Si la app se cierra o el móvil se apaga, al volver a abrir se ofrece **reanudar** la sesión en curso. Solo puede haber una sesión `IN_PROGRESS`.
- "Terminar entrenamiento" → confirmación → `finishedAt` = ahora, estado `FINISHED`, se descartan las series vacías (sin peso ni reps), y se muestra un resumen (duración, ejercicios, volumen total, récords conseguidos). Opción "Descartar sesión".

### 1.4 Historial
- Lista de sesiones por fecha (más reciente primero) con rutina, duración, nº ejercicios y volumen. Detalle con todas las series. Borrar sesión (con confirmación).

### 1.5 Progreso (gráficas)
- **Por ejercicio** (selector de grupo → ejercicio), series temporales por sesión: (a) peso máximo, (b) volumen total (Σ peso×reps), (c) 1RM estimado Epley `peso × (1 + reps/30)` (máximo de la sesión). Filtros de rango: 1M, 3M, 6M, 1A, Todo.
- **Por grupo muscular**: volumen semanal (barras) y nº de sesiones; mismos filtros.
- Tarjetas de récord personal (PR) por ejercicio: mejor peso, mejor e1RM, fecha.
- Librería: **Vico** (compose). Si da problemas de compatibilidad, fallback a dibujar con `Canvas` de Compose; no bloquearse.

### 1.6 Exportar / importar y no perder datos
- **Exportar**: genera un ZIP `gym-backup-YYYYMMDD-HHmm.zip` con `backup.json` (esquema versionado `schemaVersion`, todas las tablas, UUIDs), `sets.csv` (una fila por serie: fecha, hora, rutina, grupo, ejercicio, nº serie, kg, reps, e1RM) y `images/`. Se guarda mediante SAF (`CreateDocument`, el usuario elige carpeta/Drive) y además se ofrece el Share Sheet.
- **Importar**: desde ZIP, merge por UUID (`updatedAt` más reciente gana), sin duplicar; informe de lo importado.
- **Android Auto Backup** activado (`allowBackup=true`, reglas `fullBackupContent`/`dataExtractionRules` incluyendo la BD de Room y el directorio de imágenes, excluyendo caché). Documenta en README que la restauración exige la **misma clave de firma** del APK.
- **Esquema de BD con `exportSchema=true`**, carpeta `schemas/` versionada en git, y política de migraciones explícitas (nunca `fallbackToDestructiveMigration`). Es un requisito duro: actualizar la app en desarrollo no puede perder registros.

## 2. Stack y arquitectura (decidido, no lo cambies)

- **Kotlin 2.x + Jetpack Compose (BOM estable actual) + Material 3**, `minSdk 26`, `targetSdk`/`compileSdk` = último estable (35 o superior disponible).
- Un solo módulo Gradle `:app`, **package-by-feature** dentro de capas claras (monolito modular, como manda la Vault):
  - `core/` (db, di, util, theme, navigation), `data/` (Room entities, DAOs, repositorios impl, mappers), `domain/` (modelos puros, repositorios interface, casos de uso con lógica de cálculo: e1RM, volumen, PRs, previous-session), `feature/<exercises|routines|workout|history|progress|backup|settings>/` (ViewModel + screens + componentes).
- MVVM + UDF: `UiState` inmutable, `StateFlow`, eventos one-shot con `Channel`. Sin lógica de negocio en Composables.
- **Hilt** (DI), **Room + KSP**, **Navigation Compose con rutas tipadas** (`@Serializable`), **kotlinx.serialization**, **DataStore Preferences** (ajustes), **Coil** (imágenes), **Vico** (gráficas), `ActivityResultContracts.PickVisualMedia` + `TakePicture` con `FileProvider` (NO CameraX: innecesario).
- Gradle con **version catalog** (`gradle/libs.versions.toml`), Gradle wrapper, KSP, `kotlin.jvmTarget=17`.
- Modelo de dominio (UUID string como PK en todas las tablas, `createdAt`/`updatedAt` epoch millis, preparado para sync futuro):
  - `MuscleGroup(id, name, sortOrder, isDefault)`
  - `Exercise(id, muscleGroupId, name, notes?, imagePath?, isArchived, createdAt, updatedAt)`
  - `Routine(id, name, subtitle?, sortOrder, createdAt, updatedAt)`
  - `RoutineBlock(id, routineId, muscleGroupId, sortOrder)`
  - `RoutineExercise(id, blockId, exerciseId, sortOrder, defaultSets)`
  - `WorkoutSession(id, routineId?, routineNameSnapshot, startedAt, finishedAt?, status: IN_PROGRESS|FINISHED|DISCARDED, notes?)`
  - `WorkoutSet(id, sessionId, exerciseId, exerciseNameSnapshot, muscleGroupNameSnapshot, setIndex, weightKg: Double, reps: Int, isDone, completedAt?)`
  - Los *snapshots* garantizan que el historial sobrevive a renombrados/archivados. FKs con índices; `ON DELETE` restrictivo salvo `Routine→RoutineBlock→RoutineExercise` (cascade).
- Calidad: `ktlint` o `detekt` configurado, warnings como errores en el módulo `domain`, `README.md` con setup/build/instalación, `.gitignore` Android completo (incluye `keystore.properties`, `*.jks`, `local.properties`, `/dist` NO ignorado).

### 2.1 Entorno y mitigación SMB (obligatorio en Fase 0)
1. Instala Android **cmdline-tools** en `C:\Android\sdk` (descarga oficial `commandlinetools-win-*_latest.zip` en una carpeta vacía, extrae a `C:\Android\sdk\cmdline-tools\latest`). Con `sdkmanager` instala: `platform-tools`, `platforms;android-35` (o el último), `build-tools;35.0.0` (o el último). Acepta licencias (`--licenses`). Fija `ANDROID_HOME=C:\Android\sdk` a nivel usuario (`setx`) y crea `local.properties` con `sdk.dir`.
2. Usa `JAVA_HOME=C:\Progra~1\Java\jdk-17` en todos los comandos Gradle (`$env:JAVA_HOME` en cada llamada: el estado de shell no persiste entre llamadas).
3. **Build fuera de SMB**: configura en `settings.gradle.kts`/`build.gradle.kts` que `layout.buildDirectory` de cada proyecto apunte a `C:\build\gym\<proyecto>` y lanza Gradle siempre con `--project-cache-dir C:\build\gym\.gradle`. `GRADLE_USER_HOME` queda por defecto en `C:\Users\...\.gradle`. Si aun así Gradle falla por SMB (bloqueos, lentitud > 10 min), haz un clon local en `C:\dev\Registro-Pesos-GYM-App`, trabaja allí, y sincroniza por git con `Z:` (`git pull` en Z: tras cada push). Documenta cuál de las dos vías quedó activa en `docs/DECISIONES.md`.
4. Comprueba `./gradlew assembleDebug` verde antes de escribir una sola línea de la app.

### 2.2 Firma y entrega
- Genera un keystore de release en `C:\Users\<usuario>\.android-keystores\gym-release.jks` (fuera del repo) con `keytool`; credenciales en `keystore.properties` (ignorado por git). Guarda una copia del keystore y de `keystore.properties` en `Z:\IA\02_Proyectos\Registro-Pesos-GYM-App\..\_secrets\Registro-Pesos-GYM-App\` (fuera del repo, en el NAS) y dilo en el informe final: **si se pierde esa clave no se podrán actualizar instalaciones existentes.**
- Entregable: `dist/registro-pesos-gym-v0.1.0.apk` (release, firmado, minify opcional desactivado en v0.1 para evitar sorpresas con R8) + `dist/INSTALAR.md` (activar "orígenes desconocidos", copiar e instalar). Yo lo copiaré al móvil a mano.

## 3. Equipo de subagentes y cómo delegar

Tú (Opus 5.5) orquestas: entiendes, repartes, integras y revisas. Agentes disponibles (`~/.claude/agents/`):

| Agente | Modelo | Uso en este proyecto |
|---|---|---|
| `arquitecto` | Fable (solo lee) | Fase 1: validar el diseño de BD/arquitectura y el reparto en paralelo; diagnóstico si algo difícil se atasca (Gradle/SMB, Room migrations, Vico). |
| `desarrollador` | Opus | Toda la implementación de dominio, datos, features y release. Varios en paralelo cuando los ficheros no se solapan. |
| `revisor` | Sonnet | Revisión de cada diff al cerrar una fase, README/INSTALAR.md, docs de la Vault, ajustes pequeños. |
| `rapido` / `delegar:rapido` | Haiku (solo lee) | Verificaciones: buscar `fallbackToDestructiveMigration`, strings hardcodeadas fuera de `strings.xml`, TODOs, ficheros sin tests, checklist final. |
| `delegar:editor` | Sonnet | Ediciones mecánicas (version catalog, índice de la Vault, `log.md`). |

Reglas de delegación: cada subagente arranca sin contexto → pásale rutas exactas, objetivo, criterios de aceptación, qué no tocar y las reglas de entorno de §2.1 (JAVA_HOME, `--project-cache-dir`). Revisa siempre su resultado (compila + tests) antes de integrarlo. Nunca dos `desarrollador` editando el mismo fichero a la vez; los puntos de contacto (navegación, DI, `strings.xml`) los integras tú o los defines antes de lanzar el paralelo.

## 4. Plan de trabajo (fases, tareas, agente, aceptación)

Mantén la lista con la herramienta de tareas (`mcp__tareas__definir`) y márcala según avanzas. Un commit por tarea cerrada (mensaje convencional: `feat(workout): ...`), con la línea de atribución que te indique la sesión. **Push a `origin/main` autorizado al cerrar cada fase.**

### Fase 0 — Entorno y esqueleto (tú mismo, secuencial)
- 0.1 Instalar SDK, fijar `ANDROID_HOME`, `local.properties` (§2.1). Acept.: `sdkmanager --list_installed` muestra platform-tools, platform y build-tools.
- 0.2 Crear proyecto `:app` con version catalog, Hilt, Room/KSP, Compose, Navigation tipada, serialization, DataStore, Coil, Vico, detekt/ktlint, `.gitignore`, build dir en `C:\build\gym`. Acept.: `assembleDebug` verde en < 10 min; commit `chore: android project skeleton`.
- 0.3 `core/theme` (Material 3, dynamic color off, paleta sobria, soporte oscuro), `core/navigation` con todas las rutas tipadas definidas (aunque las pantallas sean placeholders), scaffold con bottom bar: **Entrenar · Rutinas · Ejercicios · Progreso · Más**. Acept.: la app arranca con las 5 pestañas.

### Fase 1 — Dominio y datos (`arquitecto` valida → `desarrollador` implementa)
- 1.1 `arquitecto`: revisar el modelo de §2 y este plan; devolver riesgos y el reparto definitivo de ficheros para la Fase 2. Incorpora sus cambios a `docs/DECISIONES.md`.
- 1.2 `desarrollador`: entidades Room, DAOs (Flows), `AppDatabase` v1 con `exportSchema`, seed de grupos musculares vía `RoomDatabase.Callback`, repositorios (interface en `domain`, impl en `data`), mappers, módulos Hilt. Acept.: tests Robolectric de DAO (in-memory) para: crear ejercicio, rutina con bloques, sesión con series, consulta "última sesión por ejercicio", borrado de rutina no borra historial.
- 1.3 `desarrollador` (en paralelo con 1.2, solo `domain/`): casos de uso puros con tests JUnit: `CalculateE1rm` (Epley, redondeo 0,5 kg), `SessionVolume`, `PersonalRecords`, `PreviousSessionForExercise`, `WeeklyVolumeByMuscleGroup`, `FinishSession` (descarta series vacías), `BackupSerializer` (round-trip JSON y CSV con casos límite: decimales con coma, nombres con comas/comillas).

### Fase 2 — Features en paralelo (4 `desarrollador` simultáneos, ficheros disjuntos)
- 2.A `feature/exercises` + grupos musculares: listado agrupado con búsqueda, ficha crear/editar, Photo Picker y cámara (`FileProvider`, redimensionado a 1280 px/JPEG 80 en `Dispatchers.IO`), archivar, gestión de grupos. Acept.: flujo completo en Robolectric/Compose test básico + sin crash con permisos denegados.
- 2.B `feature/routines`: lista, editor de rutina (nombre, subtítulo, bloques por grupo, selección múltiple de ejercicios, reordenar con drag o botones ↑↓, series por defecto), borrar con confirmación.
- 2.C `feature/workout` + `feature/history`: inicio/reanudar sesión, pantalla de bloque → ejercicio con filas de series (teclado numérico decimal, +/−2,5, check), ghost de sesión anterior y "Copiar anterior", guardado inmediato, terminar/descartar con resumen (duración, volumen, PRs nuevos), historial y detalle. Acept.: test de que matar el proceso a mitad de sesión y reabrir reanuda con los datos.
- 2.D `feature/progress` + `feature/backup` + `feature/settings`: gráficas Vico por ejercicio (max, volumen, e1RM) y por grupo (volumen semanal) con filtros; tarjetas PR; exportar ZIP vía SAF + share; importar con merge; Auto Backup rules; pantalla "Más" (exportar, importar, grupos musculares, acerca de, versión).
- Integración (tú): unir navegación y DI, resolver `strings.xml`, `assembleDebug` verde, smoke manual de las rutas con `rapido` revisando que no hay rutas sin pantalla.

### Fase 3 — Calidad
- 3.1 `revisor`: revisión del diff completo de Fase 2 (bugs, estados de carga/error/vacío en cada pantalla, accesibilidad mínima, textos en `strings.xml`). Corrige con `desarrollador` lo que sea de lógica, con `revisor` lo menor.
- 3.2 `rapido`: checklist: sin `fallbackToDestructiveMigration`, `schemas/1.json` versionado, `allowBackup=true` + reglas, sin `Log.d` con datos personales, sin TODOs bloqueantes, `detekt`/`ktlint` limpio.
- 3.3 Tú: `./gradlew test detekt` verde. Prueba de migración: crear BD v1 con datos → simular cambio trivial a v2 con `Migration(1,2)` + `MigrationTestHelper` para dejar el mecanismo probado y documentado (puedes dejar la v2 si aporta; si no, deja el test de infraestructura listo).

### Fase 4 — Release y documentación
- 4.1 Keystore + `keystore.properties` + `signingConfigs.release` (§2.2). `assembleRelease` → `dist/registro-pesos-gym-v0.1.0.apk`. Verifica con `apksigner verify --print-certs`. Copia keystore a `_secrets` del NAS.
- 4.2 `revisor`: `README.md` (setup del entorno, build, estructura, decisiones), `dist/INSTALAR.md`, `docs/DECISIONES.md` final.
- 4.3 Vault (ver §8), con `delegar:editor` para `index.md` y `log.md`.
- 4.4 Commit `release: v0.1.0`, tag `v0.1.0`, push. Informe final para mí (§9).

## 5. Criterios de aceptación globales (definition of done)
- `./gradlew assembleRelease test detekt` verde desde cero en este PC.
- Flujo completo sin crash: crear grupo → ejercicio con foto → rutina con 2 bloques → entrenar rellenando series → cerrar app a mitad → reanudar → terminar → ver en historial → ver gráficas → exportar ZIP → borrar datos (desinstalar/instalar) → importar ZIP → mismos datos y fotos.
- Ninguna pantalla sin estado vacío/cargando/error.
- Ninguna migración destructiva; esquema exportado en git.
- APK firmado en `dist/` + instrucciones.

## 6. Qué NO hacer
- No login, no Supabase, no backend, no multi-módulo Gradle, no CameraX, no Play Store, no iOS. (El modelo queda preparado para sync futuro, nada más.)
- No `fallbackToDestructiveMigration`, no `allowBackup=false`, no imágenes a tamaño original.
- No pedirme confirmación para decisiones técnicas; sí para borrar algo fuera del repo o cambiar algo de mi sistema que no esté en §2.1.
- No instalar emulador salvo que lo necesites de verdad y tengas < 30 min de coste; la validación se basa en tests JUnit/Robolectric.

## 7. Restricciones de proceso
- Rama `main` directa (proyecto personal); commits pequeños y frecuentes; push al cerrar cada fase.
- El estado de shell no persiste entre llamadas: usa rutas absolutas y fija `JAVA_HOME`/`ANDROID_HOME` en cada comando.
- Si una herramienta falla 3 veces igual, cambia de enfoque (p. ej. clon local en `C:\dev` para Gradle) y anótalo.
- Trabaja de forma autónoma hasta la Fase 4 completa; solo me informas al final (o si el entorno te impide avanzar tras agotar las alternativas descritas).

## 8. Documentación en la Vault (obligatoria, Fase 4)
Siguiendo `OBSIDIAN VAULT/SCHEMA.md` (frontmatter `tags`, `updated`, `fuentes`; secciones `## Cross-references` con `[[index]]`, `[[SCHEMA]]`, `[[log]]`; `## Fuentes`; sin enlaces fuera de la Vault; sin nodos fantasma):
- `Entities/Proyecto-Registro-Pesos-GYM-App.md` — stack, arquitectura, modelo, decisiones, cómo buildear/instalar.
- `Concepts/Android-Compose-Room-Offline-First.md` — patrón MVVM/UDF + Room + Hilt + navegación tipada usado aquí (reutilizable).
- `Concepts/Android-Backup-Export-Import.md` — Auto Backup + ZIP SAF + merge por UUID + política de migraciones.
- `Concepts/Android-SDK-Setup-CLI-Windows.md` — instalación headless del SDK, JDK 17, y la mitigación de Gradle sobre SMB.
- Actualizar `index.md`, `log.md` y la ficha `Proyecto-Gastos-Android.md` (cross-ref como proyecto hermano en Kotlin).

## 9. Informe final que espero
1. Ruta del APK y cómo instalarlo. 2. Dónde está el keystore y por qué no puede perderse. 3. Qué agentes usaste en qué tareas. 4. Decisiones tomadas por tu cuenta (enlace a `docs/DECISIONES.md`). 5. Qué quedó fuera o es frágil y qué haría en la v0.2 (p. ej. sync Supabase, widgets, temporizador de descanso). 6. Resultado real de los tests (sin maquillar: si algo falla, dilo con la salida).
