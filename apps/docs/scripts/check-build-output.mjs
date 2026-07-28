import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const docsRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const staticRoot = path.join(docsRoot, '.next', 'static');

if (!fs.existsSync(staticRoot)) {
  throw new Error('Missing .next/static; run the production build before check:output');
}

function listFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const absolute = path.join(directory, entry.name);
    return entry.isDirectory() ? listFiles(absolute) : [absolute];
  });
}

const forbidden = [
  ['sourceRefs metadata', /sourceRefs/],
  ['repository source path', /contracts\/openapi\.json/],
  ['active increment path', /docs\/increments\/active\//],
  ['private research path', /docs\/research\//],
  ['Windows workspace path', /\b[A-Z]:\\(?:Users|OrgMemory|apps)\\/],
];

for (const file of listFiles(staticRoot)) {
  const content = fs.readFileSync(file);
  if (content.includes(0)) continue;
  const text = content.toString('utf8');
  for (const [label, pattern] of forbidden) {
    if (pattern.test(text)) {
      throw new Error(`${path.relative(docsRoot, file)} exposes ${label}`);
    }
  }
}

console.log('Client output audit passed: no repository evidence metadata in .next/static');
