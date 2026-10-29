# Historial de cambios

## Sin publicar (siguiente versión, prevista 0.2.0)

Cambios hechos tras probar la v0.1.0 en el móvil. Aún no tienen número de versión: `versionCode` sigue en 1 / `versionName` "0.1.0".

### Catálogo de ejercicios (ADR-019)
- La app incluye 58 ejercicios con foto, sacados de las pestañas "Día 1" a "Día 5" del Excel de la rutina de 5 días: 74 filas con imagen, una vez deduplicadas por grupo y nombre. Hay 51 imágenes distintas en JPEG, 1,6 MB en `assets/catalog`. Los nombres se copian tal cual del Excel.
- Se añaden los grupos musculares "Pierna" y "Deltoides posterior".
- Se carga en cada arranque y nunca duplica nada. No crea un ejercicio si ya existe su id fijo o el mismo nombre en su grupo. No modifica ejercicios ni fotos existentes. No recrea grupos que el usuario borró.
- Se regenera con `tools/catalog/` (Node + PowerShell); los pasos están en el README.

### Rutinas por grupos (ADR-018)
- Una rutina es ahora un nombre, un comentario opcional y una lista ordenada de grupos musculares. Ya no lleva ejercicios ni series.
- Se puede guardar una rutina con grupos y sin ningún ejercicio. Los grupos se pueden reordenar y quitar.
- En las listas de rutinas se ven los nombres de los grupos en lugar de recuentos.
- Los ejercicios elegidos en las rutinas de la v0.1.0 se conservan en la base de datos, pero ya no se usan.

### Entrenar
- Antes de empezar se ven los grupos y el comentario de la rutina. El botón pasa a llamarse "Comenzar entrenamiento".
- El comentario queda fijo en la parte de arriba durante la sesión. Se guarda una copia al empezar, así que al reanudar se sigue viendo el mismo aunque edites la rutina mientras tanto.
- Todos los grupos se ven desde el principio, cada uno con su botón "+".
- El botón "+" abre un selector a pantalla completa con foto y nombre legible. Desde ahí también se puede crear un ejercicio nuevo.
- Elegir un ejercicio lo añade con filas vacías (tantas como "Series por defecto" en Ajustes) y lo despliega.
- Un grupo admite varios ejercicios. Si el ejercicio ya está en la sesión, solo se abre, sin duplicarlo.
- "Quitar ejercicio" pide confirmación si tiene datos y siempre ofrece "Deshacer".

### Registro de series
- Se elimina la casilla "Hecha" y sus contadores.
- Los valores de la última sesión se muestran como referencia tenue y en cursiva, con la etiqueta "Última vez (referencia)" y una leyenda. Esto vale tanto en la fila de referencia como en los textos de ejemplo de los campos.
- Los datos de hoy se ven en color normal y negrita.
- "Copiar anterior" guarda los valores copiados como datos normales de hoy.

### Datos y compatibilidad
- La base de datos pasa a la v2. La migración solo añade cosas: la tabla `workout_session_groups` y la columna `routine_comment_snapshot`. No hay migración destructiva.
- Si hay una sesión en curso de la v1, la migración le crea sus grupos y le copia el comentario de su rutina.
- Las columnas `is_done` y `completed_at` se quedan sin uso, porque SQLite en API 26 no permite borrar columnas.
- Las copias de seguridad pasan a `schemaVersion` 2. Los ZIP de la v0.1.0 se siguen importando, y al importar uno no se pierde el comentario ni los grupos de las sesiones.

### Calidad
- 270 tests JVM/Robolectric. Cubren la migración 1→2 con datos reales de la v1, la importación de ZIP v1, el catálogo (las 58 asociaciones nombre-foto, comprobadas byte a byte) y el flujo completo de rutina, sesión e historial. `assembleRelease test detekt` pasa sin errores.
- No se ha probado en un dispositivo real; ver `docs/PENDIENTE.md`.

### APK de prueba
- `dist/registro-pesos-gym-v0.1.0+grupos-catalogo.apk`. Está firmado con la misma clave que la v0.1.0, así que se instala encima y conserva los datos. Por dentro sigue diciendo 0.1.0.

## 0.1.0 (2026-10-29)

Primera versión: ejercicios con foto, rutinas por bloques con ejercicios y series, sesiones con guardado continuo y reanudación, historial, gráficas de progreso, récords, Auto Backup y exportación/importación ZIP.
