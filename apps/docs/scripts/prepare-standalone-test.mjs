import { cpSync, mkdirSync, rmSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const docsRoot = fileURLToPath(new URL('..', import.meta.url));
const runtimeRoot = fileURLToPath(new URL('../.next/standalone/apps/docs', import.meta.url));
const runtimeStatic = fileURLToPath(
  new URL('../.next/standalone/apps/docs/.next/static', import.meta.url),
);
const runtimePublic = fileURLToPath(
  new URL('../.next/standalone/apps/docs/public', import.meta.url),
);

mkdirSync(runtimeRoot, { recursive: true });
rmSync(runtimeStatic, { recursive: true, force: true });
rmSync(runtimePublic, { recursive: true, force: true });
cpSync(fileURLToPath(new URL('../.next/static', import.meta.url)), runtimeStatic, {
  recursive: true,
});
cpSync(fileURLToPath(new URL('../public', import.meta.url)), runtimePublic, {
  recursive: true,
});

console.log(`Prepared standalone docs runtime from ${docsRoot}.`);
