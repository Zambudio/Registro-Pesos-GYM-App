# Pendiente

## Cambios tras probar la v0.1.0 en el móvil
- Hechos (pendientes de versión): rutinas solo con grupos y comentario, ejercicios elegidos durante la sesión con foto, catálogo del Excel, sin "Hecha" y referencias de la última vez más tenues (ADR-018, ADR-019). Detalle en `CHANGELOG.md`.
- Siguiente paso: probar `dist/registro-pesos-gym-v0.1.0+grupos-catalogo.apk` encima de la v0.1.0 instalada (exportar antes un ZIP) y, si va bien, publicarlo como 0.2.0 (`versionCode` 2, APK en `dist/`, `INSTALAR.md`, tag).
- El Excel origen del catálogo (`docs/261013 Registro Pesos - Rutina 5 Dias.xlsx`) no se sube al repo porque es público y el Excel contiene el historial de pesos; solo hace falta para regenerar el catálogo.

## Sin verificar en dispositivo real
- Flujo nuevo de la sesión (selector con fotos a pantalla completa, recordatorio fijo arriba con el teclado abierto) y legibilidad de las referencias tenues con luz del gimnasio.
- Actualización real desde la v0.1.0 instalada (migración 1→2 y carga del catálogo en el primer arranque); probado solo con Robolectric.
- Cámara y galería, incluida la rotación EXIF de las fotos.
- Renderizado de las gráficas (Vico).
- Guardar el ZIP en Drive con el selector de documentos y compartirlo.
- "Deshacer" al quitar una serie y doble toque en "atrás".
- Ciclo completo exportar → desinstalar/reinstalar → importar.

## Deuda técnica conocida
- Los avisos del informe de importación se generan como texto en español en la capa de datos, en vez de usar string resources.
- El borrador del editor de rutinas no sobrevive a la muerte del proceso.
- Pueden quedar imágenes huérfanas si el proceso muere con la cámara abierta.
- El test de reanudación usa journal TRUNCATE: WAL no abre bajo Robolectric en Windows.
- Robolectric está fijado a SDK 35 y detekt a Kotlin 2.0.21 (ADR-008, ADR-012).
- R8 está desactivado.

## Ideas v0.2
- Temporizador de descanso.
- Añadir un grupo muscular no previsto durante la sesión.
- Ofrecer como sugerencia los ejercicios preseleccionados de las rutinas de la v1 (hoy se conservan sin uso).
- Widget de "entrenamiento en curso".
- Sync con Supabase: el modelo ya tiene UUID, `updatedAt` y tombstones.
- Migrar a AGP 9 / Kotlin 2.3.
- Activar R8.
