// Extrae el catálogo de ejercicios del Excel de rutinas a app/src/main/assets/catalog/.
//
// Uso (Node 18+, sin dependencias):
//   node tools/catalog/extract-catalog.mjs "<ruta al .xlsx>"
//   powershell -File tools/catalog/convert-images.ps1
//
// Solo se leen las pestañas "Día 1" a "Día 5" y solo las filas de la tabla (columna Grupo y
// Ejercicio) que tienen una imagen anclada en la columna "Miniatura". Se deduplica por
// (grupo, nombre) y se conservan los nombres literales. No se extrae ningún otro dato.
// Los ids son fijos: NO reordenar ni renumerar entradas ya publicadas (las instalaciones
// existentes se basan en ellos para no duplicar ejercicios).

import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const xlsx = process.argv[2];
if (!xlsx) throw new Error('Falta la ruta del .xlsx');

const repoRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), '..', '..');
const outDir = path.join(repoRoot, 'app', 'src', 'main', 'assets', 'catalog');
const rawDir = path.join(repoRoot, 'tools', 'catalog', 'raw');

const work = fs.mkdtempSync(path.join(os.tmpdir(), 'gym-catalog-'));
// El .xlsx es un ZIP: bsdtar de Windows lo abre; en otros sistemas, unzip.
if (process.platform === 'win32') {
  execFileSync(path.join(process.env.SystemRoot, 'System32', 'tar.exe'), ['-xf', xlsx, '-C', work]);
} else {
  execFileSync('unzip', ['-q', xlsx, '-d', work]);
}
const read = (p) => fs.readFileSync(path.join(work, p), 'utf8');
const decode = (s) => s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&');

const shared = [...read('xl/sharedStrings.xml').matchAll(/<si>([\s\S]*?)<\/si>/g)]
  .map((m) => decode([...m[1].matchAll(/<t[^>]*>([\s\S]*?)<\/t>/g)].map((t) => t[1]).join('')));

// Hoja visible -> fichero, en el orden del libro.
const rels = Object.fromEntries([...read('xl/_rels/workbook.xml.rels').matchAll(/Id="(rId\d+)"[^>]*Target="([^"]+)"/g)].map((m) => [m[1], m[2]]));
const sheets = [...read('xl/workbook.xml').matchAll(/<sheet [^>]*name="([^"]+)"[^>]*r:id="(rId\d+)"/g)]
  .map((m) => ({ name: decode(m[1]), file: rels[m[2]] }))
  .filter((s) => /^Día [1-5]\b/.test(s.name));
if (sheets.length !== 5) throw new Error(`Se esperaban 5 pestañas "Día N", hay ${sheets.length}`);

const COL_GROUP = 'B';
const COL_NAME = 'C';
const COL_IMAGE = 7; // H, base 0

function cells(sheetFile) {
  const rows = {};
  for (const m of read(`xl/${sheetFile}`).matchAll(/<c r="([A-Z]+)(\d+)"([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
    const [, col, row, attrs, inner] = m;
    if (!inner) continue;
    const v = (inner.match(/<v>([\s\S]*?)<\/v>/) || [])[1];
    const inline = (inner.match(/<t[^>]*>([\s\S]*?)<\/t>/) || [])[1];
    const value = /t="s"/.test(attrs) ? shared[+v] : inline !== undefined ? decode(inline) : v;
    if (value !== undefined && value !== '') (rows[row] ??= {})[col] = value;
  }
  return rows;
}

function images(sheetFile) {
  const relFile = `xl/worksheets/_rels/${path.basename(sheetFile)}.rels`;
  const drawing = read(relFile).match(/drawings\/(drawing\d+\.xml)/)?.[1];
  if (!drawing) return {};
  const media = Object.fromEntries([...read(`xl/drawings/_rels/${drawing}.rels`).matchAll(/Id="(rId\d+)"[^>]*Target="\.\.\/media\/([^"]+)"/g)].map((m) => [m[1], m[2]]));
  const byRow = {};
  const anchor = /<xdr:from><xdr:col>(\d+)<\/xdr:col><xdr:colOff>[^<]*<\/xdr:colOff><xdr:row>(\d+)<\/xdr:row>[\s\S]*?r:embed="(rId\d+)"/g;
  for (const m of read(`xl/drawings/${drawing}`).matchAll(anchor)) {
    if (+m[1] !== COL_IMAGE) continue;
    const row = String(+m[2] + 1);
    if (byRow[row]) throw new Error(`${sheetFile}: dos imágenes en la fila ${row}`);
    byRow[row] = media[m[3]];
  }
  return byRow;
}

const GROUP_IDS = {
  Pecho: '8d6e3b1c-5a2f-4c71-9e40-000000000001',
  Espalda: '8d6e3b1c-5a2f-4c71-9e40-000000000002',
  Hombros: '8d6e3b1c-5a2f-4c71-9e40-000000000003',
  'Bíceps': '8d6e3b1c-5a2f-4c71-9e40-000000000004',
  'Tríceps': '8d6e3b1c-5a2f-4c71-9e40-000000000005',
  Abdomen: '8d6e3b1c-5a2f-4c71-9e40-000000000010',
  Pierna: '8d6e3b1c-5a2f-4c71-9e40-000000000012',
  'Deltoides posterior': '8d6e3b1c-5a2f-4c71-9e40-000000000013',
};

const exercises = new Map();
let rowsWithImage = 0;
for (const sheet of sheets) {
  const data = cells(sheet.file);
  for (const [row, media] of Object.entries(images(sheet.file))) {
    const group = data[row]?.[COL_GROUP]?.trim();
    const name = data[row]?.[COL_NAME]?.trim();
    if (!group || !name) throw new Error(`${sheet.name} fila ${row}: imagen sin grupo o nombre`);
    rowsWithImage++;
    const key = `${group}|${name}`;
    const previous = exercises.get(key);
    if (previous && previous.media !== media) throw new Error(`${key}: imágenes distintas (${previous.media}, ${media})`);
    if (!previous) exercises.set(key, { group, name, media });
  }
}

const unknown = [...new Set([...exercises.values()].map((e) => e.group))].filter((g) => !GROUP_IDS[g]);
if (unknown.length) throw new Error(`Grupos sin id fijo: ${unknown.join(', ')}`);

fs.rmSync(rawDir, { recursive: true, force: true });
fs.mkdirSync(rawDir, { recursive: true });
fs.mkdirSync(outDir, { recursive: true });
const list = [...exercises.values()].map((e, index) => {
  fs.copyFileSync(path.join(work, 'xl', 'media', e.media), path.join(rawDir, e.media));
  return {
    id: `5f0c2a9e-7d41-4b8e-a3c6-${String(index + 1).padStart(12, '0')}`,
    groupId: GROUP_IDS[e.group],
    group: e.group,
    name: e.name,
    // convert-images.ps1 convierte cada imagen a JPEG con el mismo nombre base.
    image: e.media.replace(/\.(png|jpe?g)$/i, '.jpg'),
  };
});
const groups = Object.entries(GROUP_IDS)
  .filter(([name]) => list.some((e) => e.group === name))
  .map(([name, id]) => ({ id, name }));

fs.writeFileSync(path.join(outDir, 'catalog.json'), JSON.stringify({ version: 1, groups, exercises: list }, null, 2) + '\n');
fs.rmSync(work, { recursive: true, force: true });
console.log(`Filas con imagen: ${rowsWithImage}; ejercicios únicos: ${list.length}; imágenes: ${new Set(list.map((e) => e.image)).size}`);
