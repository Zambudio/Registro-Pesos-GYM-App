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
