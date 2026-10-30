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
- 289 tests JVM/Robolectric (y galería de capturas en claro y oscuro). Cubren la migración 1→2 con datos reales de la v1, la importación de ZIP v1, el catálogo (las 58 asociaciones nombre-foto, comprobadas byte a byte) y el flujo completo de rutina, sesión e historial. `assembleRelease test detekt` pasa sin errores.
- No se ha probado en un dispositivo real; ver `docs/PENDIENTE.md`.

### Diseño y modo oscuro (ADR-021)
- Rediseño completo con una paleta naranja deportiva:
  - tarjetas y cabeceras destacadas con degradado;
  - titulares y cifras en Barlow Condensed;
  - estados vacíos cuidados.
- En la sesión:
  - las series se numeran en círculos, que se rellenan al anotarlas;
  - las cifras son grandes;
  - la referencia de la última vez va en un recuadro propio.
- Más > Apariencia: tema Sistema, Claro u Oscuro. Se aplica también a los iconos de las barras del sistema.

### Descanso entre series (ADR-020)
- Cuenta atrás integrada en la parte inferior de la sesión.
- Arranca sola al completar peso y repeticiones de una serie. Se puede desactivar en Ajustes y también arrancar a mano.
- Botones −15 s, +15 s y Saltar. Duración por defecto en Ajustes, de 45 s a 3 min.
- Mientras corre, una notificación muestra la cuenta atrás. Al terminar, vibra y avisa aunque el móvil esté bloqueado o la app cerrada.
- Sobrevive a cerrar la app. Terminar o descartar la sesión lo para.

### Biblioteca de ejercicios (ADR-022)
- 189 ejercicios: los 60 actuales y 129 nuevos, de máquina, mancuernas, barra, polea, multipower y peso corporal.
- `dist/biblioteca-ejercicios.zip` se importa desde Más > Exportar / importar. Reimportarlo no duplica nada.
- Listado y prompt para generar las ilustraciones con ChatGPT en `docs/imagenes-ejercicios/`. Al regenerar el ZIP con ellas, la ilustración sustituye también la foto de los ejercicios existentes.
- Ya con las 189 ilustraciones (`dist/biblioteca-ejercicios.zip`, 11,6 MB).
- `build-backup.mjs --with-routines` genera `dist/rutinas-y-ejercicios.zip`: ejercicios con ilustración más las rutinas de la copia, sin sesiones, series ni borrados, para compartir la configuración con otra persona sin su historial. No se sube a git (repo público).

### APK de prueba
- `dist/registro-pesos-gym-v0.1.0+diseno-descanso.apk` (incluye todo lo anterior y sustituye al APK de prueba `+grupos-catalogo`). Está firmado con la misma clave que la v0.1.0, así que se instala encima y conserva los datos. Por dentro sigue diciendo 0.1.0.

## 0.1.0 (2026-10-29)

Primera versión: ejercicios con foto, rutinas por bloques con ejercicios y series, sesiones con guardado continuo y reanudación, historial, gráficas de progreso, récords, Auto Backup y exportación/importación ZIP.
