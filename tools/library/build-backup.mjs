// Genera un ZIP importable por la app (Más > Exportar / importar > Importar desde ZIP) con la
// biblioteca de ejercicios de tools/library/exercises.json y sus ilustraciones.
//
//   node tools/library/build-backup.mjs --base <copia.zip> [--images <carpeta>] [--out <zip>] [--with-routines]
//
// --base    Copia exportada desde la app (obligatoria). De ella salen los grupos musculares y los
//           ejercicios que ya existen, para respetar sus ids y no duplicarlos.
// --images  Ilustraciones nombradas por código (PEC-001.png o .jpg): por defecto
//           docs/imagenes-ejercicios/imagenes.
// --out     Por defecto dist/biblioteca-ejercicios.zip (con --with-routines, dist/rutinas-y-ejercicios.zip).
// --with-routines  Incluye también las rutinas de la copia (con sus grupos), para compartir la
//           configuración completa. Nunca lleva sesiones, series ni borrados.
//
// Reglas (la importación de la app mezcla por id y gana el updatedAt más reciente, ADR-010):
// - Ejercicio que ya existe (existingId) y tiene ilustración: va con su id, nombre y grupo actuales,
//   la ilustración nueva y updatedAt = ahora, así sustituye a la foto anterior.
// - Ejercicio que ya existe sin ilustración: no se incluye (no cambia nada).
// - Ejercicio nuevo: id fijo derivado de su código (reimportar no lo duplica) y la ilustración si la hay.
// - Sin --with-routines solo van grupos musculares y ejercicios. Nunca sesiones, series ni tombstones:
//   el ZIP no lleva historial de pesos y no propaga borrados a quien lo importe.
// Las imágenes se convierten a JPEG <= 1280 px, calidad 80 (convert-images.ps1, Windows).

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..', '..');

const GROUP_NUMBERS = { PEC: 1, ESP: 2, HOM: 3, BIC: 4, TRI: 5, CUA: 6, ABD: 7, PIE: 8, DEL: 9 };
const ID_PREFIX = '6a1b9e3c-2f47-4d8a-b5c0-';
const IMAGE_EXTENSIONS = ['.png', '.jpg', '.jpeg'];
// En Windows, el tar de Git (GNU) no entiende ZIP; el de System32 (bsdtar) sí.
const TAR = process.platform === 'win32' ? join(process.env.SystemRoot ?? 'C:\\Windows', 'System32', 'tar.exe') : 'tar';

function parseArgs(argv) {
  const args = { images: join(root, 'docs', 'imagenes-ejercicios', 'imagenes'), withRoutines: false };
  for (let i = 0; i < argv.length; i += 2) {
    if (argv[i] === '--with-routines') {
      args.withRoutines = true;
      i -= 1;
      continue;
    }
    const key = argv[i].replace(/^--/, '');
    if (!['base', 'images', 'out'].includes(key) || argv[i + 1] === undefined) usage(`Argumento no válido: ${argv[i]}`);
    args[key] = resolve(argv[i + 1]);
  }
  if (!args.base) usage('Falta --base <copia.zip>');
  args.out ??= join(root, 'dist', args.withRoutines ? 'rutinas-y-ejercicios.zip' : 'biblioteca-ejercicios.zip');
  return args;
}

function usage(message) {
  console.error(`${message}\nUso: node tools/library/build-backup.mjs --base <copia.zip> [--images <carpeta>] [--out <zip>] [--with-routines]`);
  process.exit(2);
}

/** Id fijo de un ejercicio nuevo: PEC-001 -> 6a1b9e3c-2f47-4d8a-b5c0-000000001001. */
function libraryId(code) {
  const match = /^([A-Z]{3})-(\d{3})$/.exec(code);
  if (!match || !GROUP_NUMBERS[match[1]]) throw new Error(`Código no válido: ${code}`);
  return ID_PREFIX + String(GROUP_NUMBERS[match[1]] * 1000 + Number(match[2])).padStart(12, '0');
}

const normalize = (text) => text.trim().toLowerCase();

function findImage(dir, code) {
  if (!existsSync(dir)) return null;
  const files = readdirSync(dir);
  for (const ext of IMAGE_EXTENSIONS) {
    const name = files.find((f) => f.toLowerCase() === (code + ext).toLowerCase());
    if (name) return join(dir, name);
  }
  return null;
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const work = mkdtempSync(join(tmpdir(), 'gym-library-'));
  try {
    execFileSync(TAR, ['-xf', args.base, '-C', work, 'backup.json', 'sets.csv']);
    const base = JSON.parse(readFileSync(join(work, 'backup.json'), 'utf8'));
    const csvHeader = readFileSync(join(work, 'sets.csv'), 'utf8').split(/\r?\n/)[0];
    const library = JSON.parse(readFileSync(join(here, 'exercises.json'), 'utf8'));

    const groupsByName = new Map(base.muscleGroups.map((g) => [normalize(g.name), g]));
    const existingById = new Map(base.exercises.map((e) => [e.id, e]));
    const now = Date.now();

    const pack = join(work, 'pack');
    const exercises = [];
    const conversions = [];
    const missingImages = [];
    const problems = [];
    for (const item of library) {
      const group = groupsByName.get(normalize(item.group));
      if (!group) {
        problems.push(`${item.code}: el grupo "${item.group}" no existe en la copia`);
        continue;
      }
      const image = findImage(args.images, item.code);
      if (!image) missingImages.push(item.code);
      const existing = item.existingId ? existingById.get(item.existingId) : null;
      if (item.existingId && !existing) {
        problems.push(`${item.code}: el ejercicio ${item.existingId} ya no está en la copia (¿borrado?)`);
        continue;
      }
      if (existing && !image) continue; // Sin ilustración nueva, el ejercicio actual se queda como está.

      const id = existing ? existing.id : libraryId(item.code);
      const imageFile = image ? `images/exercise_${id}_lib.jpg` : null;
      if (image) conversions.push({ source: image, target: join(pack, imageFile) });
      exercises.push(
        existing
          ? { ...existing, imageFile, updatedAt: Math.max(now, existing.updatedAt + 1) }
          : {
              id,
              muscleGroupId: group.id,
              name: item.name,
              notes: null,
              imageFile,
              isArchived: false,
              createdAt: now,
              updatedAt: now,
            },
      );
    }
    const routines = args.withRoutines ? base.routines : [];
    const routineBlocks = args.withRoutines ? base.routineBlocks : [];
    // Legado de la v1 (ADR-018): se conserva para que la rutina llegue igual que en la copia.
    const routineExercises = args.withRoutines ? base.routineExercises : [];
    const includedExercises = new Set(exercises.map((e) => e.id));
    for (const link of routineExercises) {
      if (!includedExercises.has(link.exerciseId)) problems.push(`La rutina usa el ejercicio ${link.exerciseId}, que no va en el ZIP`);
    }
    if (problems.length > 0) {
      console.error(`No se genera el ZIP:\n- ${problems.join('\n- ')}`);
      process.exit(1);
    }

    const usedGroups = new Set([...exercises, ...routineBlocks].map((e) => e.muscleGroupId));
    const backup = {
      schemaVersion: 2,
      dbVersion: 2,
      appVersion: 'biblioteca-ejercicios',
      exportedAt: now,
      muscleGroups: base.muscleGroups.filter((g) => usedGroups.has(g.id)),
      exercises,
      routines,
      routineBlocks,
      routineExercises,
      sessions: [],
      sets: [],
      tombstones: [],
      sessionGroups: [],
    };

    mkdirSync(join(pack, 'images'), { recursive: true });
    writeFileSync(join(pack, 'backup.json'), JSON.stringify(backup, null, 2));
    writeFileSync(join(pack, 'sets.csv'), csvHeader + '\n');
    if (conversions.length > 0) {
      const map = join(work, 'conversions.json');
      writeFileSync(map, JSON.stringify(conversions));
      execFileSync('powershell', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', join(here, 'convert-images.ps1'), '-Map', map], { stdio: 'inherit' });
    }

    mkdirSync(dirname(args.out), { recursive: true });
    rmSync(args.out, { force: true });
    const entries = ['backup.json', 'sets.csv'].concat(conversions.length > 0 ? ['images'] : []);
    execFileSync(TAR, ['-a', '-cf', args.out, '-C', pack, ...entries]);

    const created = exercises.filter((e) => !existingById.has(e.id)).length;
    console.log(`ZIP: ${args.out}`);
    console.log(`Ejercicios: ${exercises.length} (${created} nuevos, ${exercises.length - created} con ilustración nueva)`);
    if (args.withRoutines) console.log(`Rutinas: ${routines.length} (${routineBlocks.length} grupos)`);
    console.log(`Ilustraciones incluidas: ${conversions.length} de ${library.length}`);
    if (missingImages.length > 0) console.log(`Sin ilustración todavía (${missingImages.length}): ${missingImages.join(', ')}`);
  } finally {
    rmSync(work, { recursive: true, force: true });
  }
}

main();
