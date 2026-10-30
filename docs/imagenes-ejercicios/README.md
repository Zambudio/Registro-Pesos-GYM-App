# Biblioteca de ejercicios e ilustraciones

189 ejercicios de gimnasio repartidos en los grupos musculares de la app: los 60 que ya había y 129 nuevos, de máquina, mancuernas, barra, polea, multipower y peso corporal. La fuente es `tools/library/exercises.json`.

| Fichero | Para qué |
|---|---|
| `LISTADO.md` | Lista legible por grupo, con código, nombre y equipo, y si el ejercicio es nuevo. |
| `PROMPT_CHATGPT.md` | Estilo común y lista por lotes para que ChatGPT genere una ilustración por ejercicio. |
| `imagenes/` | Donde se guardan las ilustraciones, con el nombre `<código>.png` (`PEC-001.png`...). No se sube a git. |
| `dist/biblioteca-ejercicios.zip` | ZIP importable en la app: Más > Exportar / importar > Importar desde ZIP. |
| `dist/rutinas-y-ejercicios.zip` | Lo mismo más las rutinas, para pasárselo a otra persona. No se sube a git. |

## Pasos

1. Genera las ilustraciones con `PROMPT_CHATGPT.md` y guárdalas en `imagenes/`. No hace falta tenerlas todas para empezar.
2. Regenera el ZIP a partir de una copia reciente exportada desde la app. La copia aporta los grupos y los ejercicios actuales, para no duplicar nada:

   ```powershell
   node tools/library/build-backup.mjs --base "docs\gym-backup-AAAAMMDD-HHmm.zip"
   ```

   Necesita Node 18+ y Windows PowerShell, que convierte las imágenes a JPEG de 1280 px como máximo. Al terminar, el script dice qué códigos siguen sin ilustración.
3. Importa `dist/biblioteca-ejercicios.zip` en la app.

Para compartir tu configuración con otra persona (ejercicios con ilustración y rutinas, sin tu historial de pesos), añade `--with-routines`: genera `dist/rutinas-y-ejercicios.zip`. No uses para eso la exportación de la app, que lleva tus entrenamientos.

## Qué hace la importación

- **Ejercicios nuevos:** se crean con un id fijo derivado de su código. Por eso puedes importar el ZIP las veces que quieras sin duplicarlos: cada versión nueva del ZIP solo actualiza las ilustraciones.
- **Ejercicios que ya tenías:** solo cambian si su ilustración está en `imagenes/`. En ese caso la ilustración sustituye a la foto anterior y el nombre y el grupo se mantienen. Si no hay ilustración, el ejercicio no se toca.
- **El resto de tus datos:** el ZIP no lleva sesiones, series ni borrados. Sin `--with-routines` tampoco lleva rutinas; con él, las rutinas que ya tengas solo se actualizan si las de la copia son más recientes.
- **Ediciones posteriores:** si editas en la app un ejercicio de la biblioteca después de importarlo, un ZIP generado más tarde vuelve a ponerle su nombre de la biblioteca, porque es más reciente.
