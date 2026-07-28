import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const mode = process.argv[2];
const docsRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repositoryRoot = path.resolve(docsRoot, '..', '..');
const contentRoot = path.join(docsRoot, 'content', 'docs');
const manifestPath = path.join(docsRoot, 'public-content.manifest.json');
const allowedStatuses = new Set(['public', 'draft']);
const allowedAudiences = new Set([
  'adopter',
  'user',
  'administrator',
  'developer',
  'evaluator',
]);

function fail(message) {
  throw new Error(message);
}

function toPosix(value) {
  return value.split(path.sep).join('/');
}

function listFiles(directory, extension) {
  return fs
    .readdirSync(directory, { withFileTypes: true })
    .flatMap((entry) => {
      const absolute = path.join(directory, entry.name);
      if (entry.isSymbolicLink()) {
        fail(`Symbolic links are not allowed in public content: ${absolute}`);
      }
      if (entry.isDirectory()) return listFiles(absolute, extension);
      return entry.name.endsWith(extension) ? [absolute] : [];
    })
    .sort();
}

function readFrontmatter(file) {
  const raw = fs.readFileSync(file, 'utf8');
  const match = raw.match(/^---\r?\n([\s\S]*?)\r?\n---(?:\r?\n|$)/);
  if (!match) fail(`Missing YAML frontmatter: ${toPosix(path.relative(docsRoot, file))}`);

  const data = YAML.parse(match[1]);
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    fail(`Frontmatter must be a mapping: ${toPosix(path.relative(docsRoot, file))}`);
  }
  return { data, raw };
}

function readManifest() {
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  if (manifest.schemaVersion !== 1 || !Array.isArray(manifest.entries)) {
    fail('public-content.manifest.json must use schemaVersion 1 with an entries array');
  }
  return manifest;
}

function checkContent() {
  const files = listFiles(contentRoot, '.mdx');
  if (files.length === 0) fail('At least one documentation page is required');

  for (const file of files) {
    const relative = toPosix(path.relative(docsRoot, file));
    const { data } = readFrontmatter(file);

    for (const field of [
      'title',
      'description',
      'audience',
      'status',
      'sourceRefs',
      'lastReviewed',
    ]) {
      if (data[field] === undefined) fail(`${relative}: missing ${field}`);
    }

    if (typeof data.title !== 'string' || data.title.trim().length < 3) {
      fail(`${relative}: title must be a meaningful string`);
    }
    if (typeof data.description !== 'string' || data.description.trim().length < 20) {
      fail(`${relative}: description must contain at least 20 characters`);
    }
    if (
      !Array.isArray(data.audience) ||
      data.audience.length === 0 ||
      data.audience.some((item) => !allowedAudiences.has(item))
    ) {
      fail(`${relative}: audience contains an unsupported value`);
    }
    if (!allowedStatuses.has(data.status)) {
      fail(`${relative}: status must be public or draft`);
    }
    if (
      typeof data.lastReviewed !== 'string' ||
      !/^\d{4}-\d{2}-\d{2}$/.test(data.lastReviewed) ||
      Number.isNaN(Date.parse(`${data.lastReviewed}T00:00:00Z`))
    ) {
      fail(`${relative}: lastReviewed must be a valid yyyy-MM-dd date`);
    }
    if (!Array.isArray(data.sourceRefs) || data.sourceRefs.length === 0) {
      fail(`${relative}: sourceRefs must contain at least one repository path`);
    }

    for (const sourceRef of data.sourceRefs) {
      if (
        typeof sourceRef !== 'string' ||
        path.isAbsolute(sourceRef) ||
        sourceRef.includes('\\') ||
        sourceRef.split('/').includes('..')
      ) {
        fail(`${relative}: invalid sourceRef ${String(sourceRef)}`);
      }
      if (
        sourceRef.startsWith('docs/increments/active/') ||
        sourceRef.startsWith('docs/research/')
      ) {
        fail(`${relative}: public pages cannot depend on private working material ${sourceRef}`);
      }
      if (!fs.existsSync(path.join(repositoryRoot, sourceRef))) {
        fail(`${relative}: sourceRef does not exist: ${sourceRef}`);
      }
    }
  }

  console.log(`Content check passed: ${files.length} MDX page(s)`);
}

function checkManifest() {
  const manifest = readManifest();
  const files = listFiles(contentRoot, '.mdx').map((file) =>
    toPosix(path.relative(docsRoot, file)),
  );
  const contentPaths = new Set();
  const routes = new Set();

  for (const entry of manifest.entries) {
    if (!entry || typeof entry !== 'object' || Array.isArray(entry)) {
      fail('Every manifest entry must be an object');
    }
    if (
      typeof entry.route !== 'string' ||
      (entry.route !== '/docs' && !entry.route.startsWith('/docs/'))
    ) {
      fail(`Invalid public route: ${String(entry.route)}`);
    }
    if (routes.has(entry.route)) fail(`Duplicate public route: ${entry.route}`);
    routes.add(entry.route);

    if (
      typeof entry.content !== 'string' ||
      !entry.content.startsWith('content/docs/') ||
      entry.content.includes('\\') ||
      entry.content.split('/').includes('..')
    ) {
      fail(`Invalid manifest content path: ${String(entry.content)}`);
    }
    if (contentPaths.has(entry.content)) {
      fail(`Duplicate manifest content path: ${entry.content}`);
    }
    contentPaths.add(entry.content);
    if (!fs.existsSync(path.join(docsRoot, entry.content))) {
      fail(`Manifest content does not exist: ${entry.content}`);
    }

    if (!allowedStatuses.has(entry.status)) {
      fail(`${entry.route}: status must be public or draft`);
    }
    if (typeof entry.area !== 'string' || entry.area.length === 0) {
      fail(`${entry.route}: area is required`);
    }
    if (!Number.isInteger(entry.order) || entry.order < 0) {
      fail(`${entry.route}: order must be a non-negative integer`);
    }
    if (typeof entry.reviewOwner !== 'string' || entry.reviewOwner.length === 0) {
      fail(`${entry.route}: reviewOwner is required`);
    }

    const { data } = readFrontmatter(path.join(docsRoot, entry.content));
    if (data.status !== entry.status) {
      fail(`${entry.route}: manifest and frontmatter status differ`);
    }
  }

  for (const file of files) {
    if (!contentPaths.has(file)) fail(`Content is missing from manifest: ${file}`);
  }
  for (const content of contentPaths) {
    if (!files.includes(content)) fail(`Manifest contains unexpected content: ${content}`);
  }

  console.log(
    `Manifest check passed: ${routes.size} route(s), ` +
      `${manifest.entries.filter((entry) => entry.status === 'public').length} public`,
  );
}

function checkPublication() {
  if (
    process.env.DOCS_DEPLOYMENT_MODE === 'production' &&
    process.env.DOCS_INCLUDE_DRAFTS === 'true'
  ) {
    fail('DOCS_INCLUDE_DRAFTS=true is forbidden in production mode');
  }

  const forbidden = [
    ['active increment path', /docs\/increments\/active\//i],
    ['private research path', /docs\/research\//i],
    ['host deployment path', /\/apps\/orgmemory(?:\/|\b)/i],
    ['Windows absolute path', /\b[A-Z]:\\(?:Users|OrgMemory|apps)\\/i],
    [
      'secret assignment',
      /\b(?:password|token|api[_-]?key|client[_-]?secret)\s*[:=]\s*\S+/i,
    ],
  ];

  for (const file of listFiles(contentRoot, '.mdx')) {
    const relative = toPosix(path.relative(docsRoot, file));
    const { raw } = readFrontmatter(file);
    for (const [label, pattern] of forbidden) {
      if (pattern.test(raw)) fail(`${relative}: forbidden ${label}`);
    }
  }

  const sourceImplementation = fs.readFileSync(
    path.join(docsRoot, 'src', 'lib', 'source.ts'),
    'utf8',
  );
  if (
    !sourceImplementation.includes("page.status === 'public'") ||
    !sourceImplementation.includes("process.env.DOCS_INCLUDE_DRAFTS === 'true'")
  ) {
    fail('The runtime source must exclude draft pages unless explicitly enabled');
  }

  console.log('Publication policy check passed');
}

function checkRoutes() {
  const manifest = readManifest();
  const publicRoutes = manifest.entries
    .filter((entry) => entry.status === 'public')
    .map((entry) => entry.route)
    .sort();
  const draftRoutes = manifest.entries
    .filter((entry) => entry.status === 'draft')
    .map((entry) => entry.route)
    .sort();

  if (manifest.entries.length !== 15) {
    fail(`Expected exactly 15 first-release routes, found ${manifest.entries.length}`);
  }
  if (publicRoutes.some((route) => draftRoutes.includes(route))) {
    fail('A route cannot be both public and draft');
  }
  if (draftRoutes.length !== 0 || publicRoutes.length !== 15) {
    fail(
      `First-release content must contain 15 public routes and no drafts; ` +
        `found ${publicRoutes.length} public and ${draftRoutes.length} draft`,
    );
  }

  const sourceImplementation = fs.readFileSync(
    path.join(docsRoot, 'src', 'lib', 'source.ts'),
    'utf8',
  );
  if (!sourceImplementation.includes("page.status === 'public'")) {
    fail('Production routes must be derived from public frontmatter status');
  }

  console.log(
    `Route boundary passed: ${publicRoutes.length} public, ${draftRoutes.length} draft`,
  );
}

const checks = {
  content: checkContent,
  manifest: checkManifest,
  publication: checkPublication,
  routes: checkRoutes,
};

if (!checks[mode]) {
  fail('Usage: node scripts/check-docs.mjs <content|manifest|publication|routes>');
}

checks[mode]();
