# Pendiente

## Cambios tras probar la v0.1.0 en el móvil
_(por concretar: anotar aquí lo detectado en la prueba real)_

## Sin verificar en dispositivo real
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
- Ejercicios fuera de rutina dentro de una sesión.
- Widget de "entrenamiento en curso".
- Sync con Supabase: el modelo ya tiene UUID, `updatedAt` y tombstones.
- Migrar a AGP 9 / Kotlin 2.3.
- Activar R8.
