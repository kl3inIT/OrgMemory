import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createOpenAPI } from 'fumadocs-openapi/server';
import { generateFilesOnly } from 'fumadocs-openapi';
import type { OpenAPIV3_2 } from 'fumadocs-openapi';

type JsonObject = Record<string, unknown>;

const docsRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repositoryRoot = path.resolve(docsRoot, '..', '..');
const contractPath = path.join(repositoryRoot, 'contracts', 'openapi.json');
const publicContractPath = path.join(docsRoot, 'generated', 'openapi.public.json');
const generatedManifestPath = path.join(docsRoot, 'generated-api.manifest.json');
const contentRoot = path.join(docsRoot, 'content', 'docs', 'reference', 'api-reference');
const checkOnly = process.argv.includes('--check');
const schemaId = 'orgmemory-public';
const generatedBanner =
  'This file is generated from contracts/openapi.json by scripts/generate-openapi.ts. Do not edit it directly.';

const domains = [
  {
    slug: 'administration',
    title: 'Administration',
    description: 'Identity, roles, invitations, permissions, and organization administration endpoints.',
    owner: 'identity-and-authorization-maintainers',
    matches: (tag: string) =>
      tag.startsWith('admin-') &&
      !tag.includes('connector') &&
      !tag.includes('source') &&
      !tag.includes('provisioning'),
  },
  {
    slug: 'sources-connections',
    title: 'Sources and connections',
    description: 'Source registration, connector credentials, crawls, provisioning, and access mapping endpoints.',
    owner: 'connector-maintainers',
    matches: (tag: string) =>
      tag.includes('connector') ||
      tag.includes('source') ||
      tag.includes('provisioning'),
  },
  {
    slug: 'assets',
    title: 'Assets',
    description: 'Governed asset registry, lifecycle, release, delivery, and consumption endpoints.',
    owner: 'asset-registry-maintainers',
    matches: (tag: string) => tag.includes('asset') && !tag.includes('assistant'),
  },
  {
    slug: 'assistant',
    title: 'Assistant',
    description: 'Permission-aware conversations and assistant tool endpoints.',
    owner: 'assistant-and-mcp-maintainers',
    matches: (tag: string) => tag.includes('assistant'),
  },
  {
    slug: 'knowledge-graph',
    title: 'Knowledge graph',
    description: 'Knowledge graph indexing, exploration, curation, job control, and export endpoints.',
    owner: 'retrieval-maintainers',
    matches: (tag: string) => tag.includes('graph'),
  },
  {
    slug: 'search-catalog',
    title: 'Search and catalog',
    description: 'Permission-aware knowledge search, catalog, and citation-content endpoints.',
    owner: 'retrieval-maintainers',
    matches: (tag: string) =>
      tag.includes('search') || tag.includes('catalog') || tag.includes('citation'),
  },
  {
    slug: 'platform',
    title: 'Platform',
    description: 'Session, current-user, organization-context, health, and knowledge-space endpoints.',
    owner: 'api-maintainers',
    matches: () => true,
  },
] as const;

function fail(message: string): never {
  throw new Error(message);
}

function toPosix(value: string): string {
  return value.split(path.sep).join('/');
}

function deepClone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

function stripUnsafeExamples(value: unknown, propertyName = ''): unknown {
  if (Array.isArray(value)) return value.map((item) => stripUnsafeExamples(item, propertyName));
  if (!value || typeof value !== 'object') return value;

  const result: JsonObject = {};
  for (const [key, child] of Object.entries(value as JsonObject)) {
    if (key === 'servers' || key === 'externalDocs' || key.startsWith('x-')) continue;
    if (key === 'example' || key === 'examples') continue;
    if (
      key === 'default' &&
      /(?:password|token|secret|credential|api[-_]?key|private[-_]?key)/i.test(propertyName)
    ) {
      continue;
    }
    result[key] = stripUnsafeExamples(child, key === 'properties' ? propertyName : key);
  }
  return result;
}

function domainFor(originalTag: string) {
  return domains.find((domain) => domain.matches(originalTag)) ?? domains.at(-1)!;
}

function sanitizeContract(contract: JsonObject): JsonObject {
  const sanitized = stripUnsafeExamples(deepClone(contract)) as JsonObject;
  const paths = sanitized.paths as Record<string, JsonObject> | undefined;
  if (!paths || Object.keys(paths).length === 0) fail('The OpenAPI contract contains no paths');

  for (const pathItem of Object.values(paths)) {
    for (const [method, operationValue] of Object.entries(pathItem)) {
      if (!['get', 'post', 'put', 'patch', 'delete', 'options', 'head', 'trace'].includes(method)) {
        continue;
      }
      const operation = operationValue as JsonObject;
      const originalTag =
        Array.isArray(operation.tags) && typeof operation.tags[0] === 'string'
          ? operation.tags[0]
          : 'platform';
      operation.tags = [domainFor(originalTag).title];
    }
  }

  sanitized.info = {
    ...(sanitized.info as JsonObject),
    title: 'Organizational AI Memory HTTP API',
    description:
      'Sanitized, deployment-neutral HTTP contract for Organizational AI Memory. Authorization is evaluated for every caller and governed resource.',
  };
  sanitized.servers = [
    {
      url: 'https://api.example.invalid',
      description: 'Deployment-specific Organizational AI Memory API origin',
    },
  ];
  sanitized.tags = domains.map(({ title, description }) => ({ name: title, description }));

  const serialized = JSON.stringify(sanitized);
  const forbidden = [
    ['localhost address', /localhost|127\.0\.0\.1|\[::1\]/i],
    ['private IPv4 address', /\b(?:10\.\d{1,3}|192\.168\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3})\.\d{1,3}\b/],
    ['private deployment path', /\/apps\/orgmemory(?:\/|\b)/i],
    ['Windows path', /\b[A-Z]:\\(?:Users|OrgMemory|apps)\\/i],
    ['secret assignment', /\b(?:password|token|api[_-]?key|client[_-]?secret)\s*[:=]\s*[^\s"',}]+/i],
  ] as const;
  for (const [label, pattern] of forbidden) {
    if (pattern.test(serialized)) fail(`Sanitized OpenAPI contract still contains ${label}`);
  }

  return sanitized;
}

function stableJson(value: unknown): string {
  return `${JSON.stringify(value, null, 2)}\n`;
}

function expectedManifest(files: { path: string; content: string }[]) {
  const entries = files
    .filter((file) => file.path.endsWith('.mdx'))
    .map((file, index) => {
      const slug = file.path.slice(0, -'.mdx'.length);
      const domain = domains.find((candidate) => candidate.slug === slug);
      if (!domain) fail(`Unexpected generated OpenAPI page: ${file.path}`);
      return {
        route: `/docs/reference/api-reference/${slug}`,
        content: `content/docs/reference/api-reference/${file.path}`,
        area: 'reference',
        order: 40 + index * 10,
        status: 'public',
        reviewOwner: domain.owner,
      };
    });
  return { schemaVersion: 1, entries };
}

function compareFile(filePath: string, expected: string): void {
  if (!fs.existsSync(filePath)) fail(`Generated file is missing: ${toPosix(path.relative(docsRoot, filePath))}`);
  const actual = fs.readFileSync(filePath, 'utf8').replaceAll('\r\n', '\n');
  if (actual !== expected.replaceAll('\r\n', '\n')) {
    fail(`Generated file is stale: ${toPosix(path.relative(docsRoot, filePath))}`);
  }
}

const rawContract = JSON.parse(fs.readFileSync(contractPath, 'utf8')) as JsonObject;
const publicContract = sanitizeContract(rawContract);
const api = createOpenAPI({
  input: { [schemaId]: publicContract as unknown as OpenAPIV3_2.Document },
});
const files = await generateFilesOnly({
  input: api,
  per: 'tag',
  name(output) {
    const domain = domains.find((candidate) => candidate.title === output.tag?.name);
    if (!domain) fail(`No public domain mapping exists for OpenAPI tag ${output.tag?.name}`);
    return domain.slug;
  },
  includeDescription: true,
  addGeneratedComment: generatedBanner,
  frontmatter(title, description) {
    return {
      description:
        description ??
        `${title} endpoints generated from the sanitized Organizational AI Memory OpenAPI contract.`,
      audience: ['developer'],
      status: 'public',
      sourceRefs: ['contracts/openapi.json'],
      lastReviewed: title === 'Assistant' ? '2026-08-10' : '2026-07-29',
    };
  },
});

const publicContractText = stableJson(publicContract);
const manifest = expectedManifest(files);
const manifestText = stableJson(manifest);

if (checkOnly) {
  compareFile(publicContractPath, publicContractText);
  compareFile(generatedManifestPath, manifestText);
  for (const file of files) compareFile(path.join(contentRoot, file.path), `${file.content}\n`);
  console.log(
    `OpenAPI generation check passed: ${Object.keys(publicContract.paths as object).length} paths, ${files.length} endpoint groups`,
  );
} else {
  fs.mkdirSync(path.dirname(publicContractPath), { recursive: true });
  fs.mkdirSync(contentRoot, { recursive: true });

  if (fs.existsSync(generatedManifestPath)) {
    const previous = JSON.parse(fs.readFileSync(generatedManifestPath, 'utf8')) as {
      entries?: { content?: string }[];
    };
    for (const entry of previous.entries ?? []) {
      if (
        typeof entry.content !== 'string' ||
        !entry.content.startsWith('content/docs/reference/api-reference/')
      ) {
        continue;
      }
      const stalePath = path.resolve(docsRoot, entry.content);
      if (!stalePath.startsWith(`${contentRoot}${path.sep}`)) {
        fail(`Refusing to remove generated content outside ${contentRoot}`);
      }
      if (!manifest.entries.some((candidate) => candidate.content === entry.content)) {
        fs.rmSync(stalePath, { force: true });
      }
    }
  }

  fs.writeFileSync(publicContractPath, publicContractText);
  fs.writeFileSync(generatedManifestPath, manifestText);
  for (const file of files) fs.writeFileSync(path.join(contentRoot, file.path), `${file.content}\n`);
  console.log(
    `Generated sanitized OpenAPI docs: ${Object.keys(publicContract.paths as object).length} paths, ${files.length} endpoint groups`,
  );
}
