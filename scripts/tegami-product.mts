import { execFile as execFileCallback } from "node:child_process";
import { mkdir, mkdtemp, readdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { promisify } from "node:util";
import GithubSlugger from "github-slugger";
import {
  WorkspacePackage,
  type PackagePublishResult,
  type PublishPlan,
  type TegamiPlugin,
} from "tegami";

const execFile = promisify(execFileCallback);
const SEMANTIC_VERSION_PATTERN =
  /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:(?:0|[1-9]\d*)|(?:\d*[A-Za-z-][0-9A-Za-z-]*))(?:\.(?:(?:0|[1-9]\d*)|(?:\d*[A-Za-z-][0-9A-Za-z-]*)))*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$/;

export const PRODUCT_ID = "product:orgmemory";
export const PRODUCT_NAME = "orgmemory";
export const PRODUCT_MANAGER = "product";
export const PRODUCT_CHANGELOG_PREAMBLE =
  "# OrgMemory changelog\n\nProduct releases are assembled from reviewed entries under `.tegami/`.";
export const PUBLIC_CHANGELOG_MARKER =
  "[//]: # (Generated from release/CHANGELOG.md by Tegami. Do not edit manually.)";
const PUBLIC_CHANGELOG_INCLUDE = "apps/docs/content/includes/product-changelog.md";
const PUBLIC_CHANGELOG_ARCHIVE_INCLUDE =
  "apps/docs/content/includes/product-changelog-archive.md";
const PUBLIC_CHANGELOG_META = "apps/docs/content/docs/changelog/meta.json";
const PUBLIC_CHANGELOG_META_VI = "apps/docs/content/docs/changelog/meta.vi.json";
export const RECENT_RELEASE_LIMIT = 10;
export const REQUIRED_COMPONENTS = [
  "api",
  "worker",
  "mcp",
  "web",
  "keycloak",
  "postgres-rag",
] as const;
const VERSION_DIFF_ALLOWLIST = [
  /^\.tegami\/[A-Za-z0-9._-]+\.md$/,
  /^\.tegami\/publish-lock\.yaml$/,
  /^release\/product\.json$/,
  /^release\/CHANGELOG\.md$/,
  /^release\/artifacts\.json$/,
  /^apps\/docs\/content\/includes\/product-changelog\.md$/,
  /^apps\/docs\/content\/includes\/product-changelog-archive\.md$/,
  /^apps\/docs\/content\/docs\/changelog\/meta(?:\.vi)?\.json$/,
];

export interface ProductManifest {
  name: typeof PRODUCT_NAME;
  version: string;
}

export interface ImageEvidence {
  component: (typeof REQUIRED_COMPONENTS)[number];
  reference: string;
  digest: string;
  sourceSha: string;
}

export interface ReleaseArtifacts {
  schemaVersion: 1;
  releaseSourceSha: string;
  product: {
    decisionRunId: number;
    manifestRunId: number;
    commitSha: string;
    images: ImageEvidence[];
  };
  docs: {
    decisionRunId: number;
    manifestRunId: number;
    commitSha: string;
    image: {
      reference: string;
      digest: string;
      sourceSha: string;
    };
  };
}

export interface CommandResult {
  stdout: string;
  stderr: string;
}

export type CommandRunner = (
  command: string,
  args: readonly string[],
  cwd: string,
) => Promise<CommandResult>;

export type WorkflowEvidenceLoader = (
  runId: number,
  artifactName: string,
  filename: string,
  cwd: string,
) => Promise<string>;

const defaultCommandRunner: CommandRunner = async (command, args, cwd) => {
  const result = await execFile(command, [...args], {
    cwd,
    encoding: "utf8",
    windowsHide: true,
    maxBuffer: 10 * 1024 * 1024,
    timeout: 5 * 60_000,
  });
  return { stdout: result.stdout, stderr: result.stderr };
};

function defaultEvidenceLoader(run: CommandRunner): WorkflowEvidenceLoader {
  return async (runId, artifactName, filename, cwd) => {
    const directory = await mkdtemp(join(tmpdir(), "orgmemory-release-proof-"));
    try {
      await run(
        "gh",
        ["run", "download", String(runId), "--name", artifactName, "--dir", directory],
        cwd,
      );
      return await readFile(join(directory, filename), "utf8");
    } finally {
      await rm(directory, { recursive: true, force: true });
    }
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function requireString(record: Record<string, unknown>, key: string): string {
  const value = record[key];
  if (typeof value !== "string" || value.length === 0) {
    throw new Error(`Expected non-empty string at ${key}`);
  }
  return value;
}

function requireSha(value: string, field: string): string {
  if (!/^[0-9a-f]{40}$/.test(value)) throw new Error(`Invalid commit SHA at ${field}`);
  return value;
}

function requireDigest(value: string, field: string): string {
  if (!/^sha256:[0-9a-f]{64}$/.test(value)) throw new Error(`Invalid digest at ${field}`);
  return value;
}

function requireRunId(value: unknown, field: string): number {
  if (!Number.isSafeInteger(value) || Number(value) <= 0) {
    throw new Error(`Invalid GitHub Actions run id at ${field}`);
  }
  return Number(value);
}

function requireGhcrReference(value: string, field: string): string {
  if (!/^ghcr\.io\/kl3init\/orgmemory-[a-z0-9-]+:sha-[0-9a-f]{40}$/.test(value)) {
    throw new Error(`Invalid immutable image reference at ${field}`);
  }
  return value;
}

export function parseProductManifest(raw: string): ProductManifest {
  const value: unknown = JSON.parse(raw);
  if (!isRecord(value) || value.name !== PRODUCT_NAME) {
    throw new Error(`release/product.json must name ${PRODUCT_NAME}`);
  }
  const version = requireString(value, "version");
  if (!SEMANTIC_VERSION_PATTERN.test(version)) {
    throw new Error(`Invalid semantic product version: ${version}`);
  }
  return { name: PRODUCT_NAME, version };
}

export function parseReleaseArtifacts(raw: string): ReleaseArtifacts {
  const value: unknown = JSON.parse(raw);
  if (!isRecord(value) || value.schemaVersion !== 1) {
    throw new Error("release/artifacts.json must use schemaVersion 1");
  }
  const releaseSourceSha = requireSha(requireString(value, "releaseSourceSha"), "releaseSourceSha");
  if (!isRecord(value.product) || !isRecord(value.docs)) {
    throw new Error("release/artifacts.json must contain product and docs evidence");
  }

  const product = value.product;
  const productCommit = requireSha(requireString(product, "commitSha"), "product.commitSha");
  if (!Array.isArray(product.images)) throw new Error("product.images must be an array");
  const seen = new Set<string>();
  const images = product.images.map((item, index): ImageEvidence => {
    if (!isRecord(item)) throw new Error(`Invalid product image at index ${index}`);
    const component = requireString(item, "component");
    if (!REQUIRED_COMPONENTS.includes(component as ImageEvidence["component"])) {
      throw new Error(`Unexpected product component: ${component}`);
    }
    if (seen.has(component)) throw new Error(`Duplicate product component: ${component}`);
    seen.add(component);
    return {
      component: component as ImageEvidence["component"],
      reference: requireGhcrReference(requireString(item, "reference"), `product.images.${component}.reference`),
      digest: requireDigest(requireString(item, "digest"), `product.images.${component}.digest`),
      sourceSha: requireSha(requireString(item, "sourceSha"), `product.images.${component}.sourceSha`),
    };
  });
  if (seen.size !== REQUIRED_COMPONENTS.length) {
    throw new Error(`Product evidence must contain exactly: ${REQUIRED_COMPONENTS.join(", ")}`);
  }

  const docs = value.docs;
  if (!isRecord(docs.image)) throw new Error("docs.image must be an object");
  const docsCommit = requireSha(requireString(docs, "commitSha"), "docs.commitSha");
  const docsImage = docs.image;
  const parsed: ReleaseArtifacts = {
    schemaVersion: 1,
    releaseSourceSha,
    product: {
      decisionRunId: requireRunId(product.decisionRunId, "product.decisionRunId"),
      manifestRunId: requireRunId(product.manifestRunId, "product.manifestRunId"),
      commitSha: productCommit,
      images,
    },
    docs: {
      decisionRunId: requireRunId(docs.decisionRunId, "docs.decisionRunId"),
      manifestRunId: requireRunId(docs.manifestRunId, "docs.manifestRunId"),
      commitSha: docsCommit,
      image: {
        reference: requireGhcrReference(requireString(docsImage, "reference"), "docs.image.reference"),
        digest: requireDigest(requireString(docsImage, "digest"), "docs.image.digest"),
        sourceSha: requireSha(requireString(docsImage, "sourceSha"), "docs.image.sourceSha"),
      },
    },
  };
  if (!parsed.docs.image.reference.includes("orgmemory-docs:")) {
    throw new Error("docs.image.reference must identify orgmemory-docs");
  }
  for (const image of parsed.product.images) {
    if (!image.reference.endsWith(`:sha-${parsed.product.commitSha}`)) {
      throw new Error(`${image.component} reference must use product.commitSha`);
    }
  }
  if (!parsed.docs.image.reference.endsWith(`:sha-${parsed.docs.commitSha}`)) {
    throw new Error("docs image reference must use docs.commitSha");
  }
  return parsed;
}

export function normalizeProductChangelog(raw: string): string {
  const occurrences = raw.split(PRODUCT_CHANGELOG_PREAMBLE).length - 1;
  if (occurrences !== 1) {
    throw new Error(
      `release/CHANGELOG.md must contain exactly one canonical preamble; found ${occurrences}`,
    );
  }
  const releases = raw.replace(PRODUCT_CHANGELOG_PREAMBLE, "").trim();
  return `${PRODUCT_CHANGELOG_PREAMBLE}${releases ? `\n\n${releases}` : ""}\n`;
}

export interface ProductReleaseNote {
  version: string;
  markdown: string;
}

interface ParsedSemanticVersion {
  core: [number, number, number];
  prerelease: string[];
}

function parseSemanticVersion(version: string): ParsedSemanticVersion {
  const match = version.match(SEMANTIC_VERSION_PATTERN);
  if (!match) throw new Error(`Invalid semantic product version: ${version}`);
  return {
    core: [Number(match[1]), Number(match[2]), Number(match[3])],
    prerelease: match[4]?.split(".") ?? [],
  };
}

function compareSemanticVersions(left: string, right: string): number {
  const a = parseSemanticVersion(left);
  const b = parseSemanticVersion(right);
  for (let index = 0; index < a.core.length; index += 1) {
    const difference = (a.core[index] ?? 0) - (b.core[index] ?? 0);
    if (difference !== 0) return Math.sign(difference);
  }
  if (a.prerelease.length === 0 || b.prerelease.length === 0) {
    return a.prerelease.length === b.prerelease.length ? 0 : a.prerelease.length === 0 ? 1 : -1;
  }
  const length = Math.max(a.prerelease.length, b.prerelease.length);
  for (let index = 0; index < length; index += 1) {
    const leftPart = a.prerelease[index];
    const rightPart = b.prerelease[index];
    if (leftPart === undefined || rightPart === undefined) {
      return leftPart === rightPart ? 0 : leftPart === undefined ? -1 : 1;
    }
    if (leftPart === rightPart) continue;
    const leftNumeric = /^\d+$/.test(leftPart);
    const rightNumeric = /^\d+$/.test(rightPart);
    if (leftNumeric && rightNumeric) return Math.sign(Number(leftPart) - Number(rightPart));
    if (leftNumeric !== rightNumeric) return leftNumeric ? -1 : 1;
    return leftPart < rightPart ? -1 : 1;
  }
  return 0;
}

export function parseProductReleases(raw: string): ProductReleaseNote[] {
  const normalized = normalizeProductChangelog(raw);
  const body = normalized.slice(PRODUCT_CHANGELOG_PREAMBLE.length).trim();
  if (!body) return [];

  const heading = /^## orgmemory@([^\s]+)\s*$/gm;
  const matches = [...body.matchAll(heading)];
  if (matches.length === 0 || matches[0]?.index !== 0) {
    throw new Error("release/CHANGELOG.md must contain only orgmemory release sections after its preamble");
  }

  const seen = new Set<string>();
  return matches.map((match, index) => {
    const version = match[1];
    if (!version) throw new Error("Product release heading must contain a semantic version");
    parseSemanticVersion(version);
    if (seen.has(version)) throw new Error(`Duplicate product release in changelog: ${version}`);
    seen.add(version);
    const start = match.index ?? 0;
    const end = matches[index + 1]?.index ?? body.length;
    return { version, markdown: body.slice(start, end).trim() };
  });
}

export function validateProductReleaseHistory(
  raw: string,
  currentVersion?: string,
): ProductReleaseNote[] {
  const releases = parseProductReleases(raw);
  if (currentVersion && releases[0]?.version !== currentVersion) {
    throw new Error(
      `Latest changelog version ${releases[0]?.version ?? "missing"} does not match product version ${currentVersion}`,
    );
  }
  for (let index = 1; index < releases.length; index += 1) {
    const previous = releases[index - 1];
    const current = releases[index];
    if (!previous || !current || compareSemanticVersions(previous.version, current.version) <= 0) {
      throw new Error("Product changelog versions must be strictly descending by semantic version");
    }
  }
  return releases;
}

function renderReleaseFragment(releases: ProductReleaseNote[], emptyMessage = ""): string {
  const body = releases
    .map((release) => {
      const publicMarkdown = release.markdown.replace(
        /^## orgmemory@[^\s]+[ \t]*$/m,
        `## ${publicReleaseHeading(release.version)}`,
      );
      return publicMarkdown;
    })
    .join("\n\n");
  const content = body || emptyMessage;
  return `${PUBLIC_CHANGELOG_MARKER}${content ? `\n\n${content}` : ""}\n`;
}

export function renderPublicProductChangelog(raw: string): string {
  return renderReleaseFragment(validateProductReleaseHistory(raw).slice(0, RECENT_RELEASE_LIMIT));
}

export function renderArchivedProductChangelog(raw: string): string {
  return renderReleaseFragment(
    validateProductReleaseHistory(raw).slice(RECENT_RELEASE_LIMIT),
    "No releases have moved to the archive yet.",
  );
}

function publicReleaseHeading(version: string): string {
  return `Organizational AI Memory v${version}`;
}

export function renderReleaseNavigationMeta(raw: string, language: "en" | "vi"): string {
  const localized = language === "vi";
  const base = localized ? "/vi/docs/changelog" : "/docs/changelog";
  const releases = validateProductReleaseHistory(raw).slice(0, RECENT_RELEASE_LIMIT);
  const slugger = new GithubSlugger();
  const pages = [
    `[${localized ? "Mới nhất" : "Latest"}](${base})`,
    ...releases.map(
      ({ version }) => `[v${version}](${base}#${slugger.slug(publicReleaseHeading(version))})`,
    ),
    "archive",
  ];
  return `${JSON.stringify(
    {
      title: localized
        ? "Ghi chú phát hành Organizational AI Memory"
        : "Organizational AI Memory Release Notes",
      description: localized
        ? "Xem các bản phát hành Organizational AI Memory gần đây và toàn bộ lịch sử phát hành."
        : "Browse recent Organizational AI Memory product releases and the complete release archive.",
      icon: "ClockArrowUp",
      root: true,
      pagesIndex: "index",
      pages,
    },
    null,
    2,
  )}\n`;
}

class OrgMemoryProductPackage extends WorkspacePackage {
  readonly name = PRODUCT_NAME;
  readonly manager = PRODUCT_MANAGER;
  version: string;

  constructor(readonly path: string, version: string) {
    super();
    this.version = version;
  }
}

function productPlan(plan: PublishPlan) {
  return plan.packages.get(PRODUCT_ID);
}

function selectRemoteTagTarget(output: string): string | undefined {
  const lines = output.trim().split(/\r?\n/).filter(Boolean);
  const peeled = lines.find((line) => line.endsWith("^{}"));
  const selected = peeled ?? lines[0];
  if (!selected) return undefined;
  const target = selected.split(/\s+/)[0];
  return target && /^[0-9a-f]{40}$/.test(target) ? target : undefined;
}

async function headSha(run: CommandRunner, cwd: string): Promise<string> {
  const { stdout } = await run("git", ["rev-parse", "HEAD"], cwd);
  return requireSha(stdout.trim(), "git HEAD");
}

async function remoteTagTarget(run: CommandRunner, cwd: string, tag: string): Promise<string | undefined> {
  const { stdout } = await run(
    "git",
    ["ls-remote", "--tags", "origin", `refs/tags/${tag}`, `refs/tags/${tag}^{}`],
    cwd,
  );
  return selectRemoteTagTarget(stdout);
}

async function releaseIsComplete(
  run: CommandRunner,
  cwd: string,
  tag: string,
  expectedSha: string,
  expectedArtifacts: ReleaseArtifacts,
): Promise<boolean> {
  try {
    if ((await remoteTagTarget(run, cwd, tag)) !== expectedSha) return false;
    const repo = process.env.GITHUB_REPOSITORY;
    if (!repo || !/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repo)) return false;
    const result = await run("gh", ["api", `repos/${repo}/releases/tags/${tag}`], cwd);
    const value: unknown = JSON.parse(result.stdout);
    if (!isRecord(value) || value.tag_name !== tag || !Array.isArray(value.assets)) return false;
    const asset = value.assets.find(
      (candidate) => isRecord(candidate) && candidate.name === "artifacts.json",
    );
    if (!isRecord(asset) || typeof asset.id !== "number") return false;
    const downloaded = await run(
      "gh",
      [
        "api",
        `repos/${repo}/releases/assets/${asset.id}`,
        "-H",
        "Accept: application/octet-stream",
      ],
      cwd,
    );
    const actual = parseReleaseArtifacts(downloaded.stdout);
    return JSON.stringify(actual) === JSON.stringify(expectedArtifacts);
  } catch {
    return false;
  }
}

async function assertTagAvailable(
  run: CommandRunner,
  cwd: string,
  tag: string,
  expectedSha: string,
): Promise<void> {
  const existing = await remoteTagTarget(run, cwd, tag);
  if (existing && existing !== expectedSha) {
    throw new Error(`Remote tag ${tag} targets ${existing}, expected ${expectedSha}`);
  }
}

export async function assertCurrentMain(run: CommandRunner, cwd: string): Promise<string> {
  await run("git", ["fetch", "--no-tags", "origin", "main"], cwd);
  const currentHead = await headSha(run, cwd);
  const { stdout } = await run("git", ["rev-parse", "origin/main"], cwd);
  const mainHead = requireSha(stdout.trim(), "origin/main");
  if (currentHead !== mainHead) {
    throw new Error(`Release SHA ${currentHead} is stale; origin/main is ${mainHead}`);
  }
  return currentHead;
}

async function assertArtifactEvidence(
  run: CommandRunner,
  cwd: string,
  artifacts: ReleaseArtifacts,
  loadEvidence: WorkflowEvidenceLoader,
): Promise<void> {
  const ancestry = new Set([
    artifacts.product.commitSha,
    artifacts.docs.commitSha,
    ...artifacts.product.images.map((image) => image.sourceSha),
    artifacts.docs.image.sourceSha,
  ]);
  for (const candidate of ancestry) {
    await run("git", ["merge-base", "--is-ancestor", candidate, artifacts.releaseSourceSha], cwd);
  }
  await run("git", ["merge-base", "--is-ancestor", artifacts.releaseSourceSha, "HEAD"], cwd);
  const changed = await run(
    "git",
    ["diff", "--name-only", `${artifacts.releaseSourceSha}..HEAD`],
    cwd,
  );
  const unrelated = changed.stdout
    .split(/\r?\n/)
    .map((path) => path.trim().replaceAll("\\", "/"))
    .filter(Boolean)
    .filter((path) => !VERSION_DIFF_ALLOWLIST.some((pattern) => pattern.test(path)));
  if (unrelated.length > 0) {
    throw new Error(`Release contains changes newer than its evidence: ${unrelated.join(", ")}`);
  }

  const repo = process.env.GITHUB_REPOSITORY;
  if (!repo || !/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repo)) {
    throw new Error("GITHUB_REPOSITORY is required to validate workflow evidence");
  }
  async function readRun(runId: number, commitSha: string): Promise<Record<string, unknown>[]> {
    const metadata = await run("gh", ["api", `repos/${repo}/actions/runs/${runId}`], cwd);
    const value: unknown = JSON.parse(metadata.stdout);
    if (
      !isRecord(value) ||
      value.conclusion !== "success" ||
      value.event !== "workflow_run" ||
      value.head_branch !== "main" ||
      value.head_sha !== commitSha ||
      !isRecord(value.head_repository) ||
      value.head_repository.full_name !== repo
    ) {
      throw new Error(`Workflow run ${runId} does not prove green main commit ${commitSha}`);
    }
    const jobsResult = await run(
      "gh",
      ["api", `repos/${repo}/actions/runs/${runId}/jobs`, "--paginate"],
      cwd,
    );
    const jobs: unknown = JSON.parse(jobsResult.stdout);
    if (!isRecord(jobs) || !Array.isArray(jobs.jobs)) {
      throw new Error(`Workflow run ${runId} returned invalid jobs`);
    }
    return jobs.jobs.filter(isRecord);
  }
  async function assertManifestRun(
    runId: number,
    commitSha: string,
    jobName: string,
  ): Promise<void> {
    const jobs = await readRun(runId, commitSha);
    if (!jobs.some((job) => job.name === jobName && job.conclusion === "success")) {
      throw new Error(`Workflow run ${runId} lacks successful job ${jobName}`);
    }
  }
  async function assertDecisionRun(
    runId: number,
    manifestRunId: number,
    planJob: string,
    publishJob: string,
  ): Promise<void> {
    const jobs = await readRun(runId, artifacts.releaseSourceSha);
    if (!jobs.some((job) => job.name === planJob && job.conclusion === "success")) {
      throw new Error(`Decision run ${runId} lacks successful job ${planJob}`);
    }
    const published = jobs.some(
      (job) => job.name === publishJob && job.conclusion === "success",
    );
    if (published && runId !== manifestRunId) {
      throw new Error(`Decision run ${runId} published artifacts but manifest run is ${manifestRunId}`);
    }
  }
  await assertManifestRun(
    artifacts.product.manifestRunId,
    artifacts.product.commitSha,
    "Verify immutable image set",
  );
  await assertManifestRun(
    artifacts.docs.manifestRunId,
    artifacts.docs.commitSha,
    "Publish immutable docs image",
  );
  await assertDecisionRun(
    artifacts.product.decisionRunId,
    artifacts.product.manifestRunId,
    "Plan affected images",
    "Verify immutable image set",
  );
  await assertDecisionRun(
    artifacts.docs.decisionRunId,
    artifacts.docs.manifestRunId,
    "Plan docs image",
    "Publish immutable docs image",
  );

  const productProof: unknown = JSON.parse(
    await loadEvidence(
      artifacts.product.manifestRunId,
      `production-image-set-${artifacts.product.commitSha}`,
      "release-images.json",
      cwd,
    ),
  );
  if (!isRecord(productProof) || productProof.commitSha !== artifacts.product.commitSha) {
    throw new Error("Production image manifest commit does not match release evidence");
  }
  const normalizedImages = Array.isArray(productProof.images)
    ? productProof.images.map((image) => {
        if (!isRecord(image)) return image;
        return {
          component: image.component,
          reference: image.reference,
          digest: image.digest,
          sourceSha: image.sourceSha,
        };
      })
    : undefined;
  if (JSON.stringify(normalizedImages) !== JSON.stringify(artifacts.product.images)) {
    throw new Error("Production image manifest bytes do not match release evidence");
  }
  const docsProof: unknown = JSON.parse(
    await loadEvidence(
      artifacts.docs.manifestRunId,
      `docs-release-${artifacts.docs.commitSha}`,
      "docs-release.json",
      cwd,
    ),
  );
  if (
    !isRecord(docsProof) ||
    docsProof.commitSha !== artifacts.docs.commitSha ||
    !isRecord(docsProof.image) ||
    docsProof.image.reference !== artifacts.docs.image.reference ||
    docsProof.image.digest !== artifacts.docs.image.digest ||
    docsProof.image.sourceSha !== artifacts.docs.image.sourceSha
  ) {
    throw new Error("Docs image manifest does not match release evidence");
  }

  const images = [...artifacts.product.images, { component: "docs", ...artifacts.docs.image }];
  for (const item of images) {
    const result = await run(
      "docker",
      ["buildx", "imagetools", "inspect", item.reference, "--format", "{{json .Manifest.Digest}}"],
      cwd,
    );
    let actual: unknown;
    try {
      actual = JSON.parse(result.stdout.trim());
    } catch {
      actual = result.stdout.trim();
    }
    if (actual !== item.digest) {
      throw new Error(`${item.component} resolves to ${String(actual)}, expected ${item.digest}`);
    }
  }
}

async function collectWorkspaceMetadata(root: string): Promise<Map<string, Buffer>> {
  const snapshots = new Map<string, Buffer>();
  const excluded = new Set([".git", ".gradle", ".next", "build", "dist", "node_modules", "tmp"]);
  async function visit(directory: string): Promise<void> {
    for (const entry of await readdir(directory, { withFileTypes: true }).catch(() => [])) {
      if (entry.isDirectory()) {
        if (!excluded.has(entry.name)) await visit(join(directory, entry.name));
        continue;
      }
      if (entry.name !== "package.json") continue;
      const path = join(directory, entry.name);
      snapshots.set(path, await readFile(path));
    }
  }
  await visit(root);
  for (const name of ["pnpm-lock.yaml", "pnpm-workspace.yaml"]) {
    const path = join(root, name);
    const content = await readFile(path).catch(() => undefined);
    if (content) snapshots.set(path, content);
  }
  return snapshots;
}

export interface ProductPluginOptions {
  run?: CommandRunner;
  verifyRemote?: boolean;
  verifyArtifacts?: boolean;
  verifyCurrentMain?: boolean;
  loadEvidence?: WorkflowEvidenceLoader;
  statusOnly?: boolean;
}

export function productReleasePlugins(options: ProductPluginOptions = {}): TegamiPlugin[] {
  const run = options.run ?? defaultCommandRunner;
  const verifyRemote = options.verifyRemote ?? process.env.CI === "true";
  const verifyArtifacts = options.verifyArtifacts ?? verifyRemote;
  const verifyCurrentMain = options.verifyCurrentMain ?? verifyRemote;
  const loadEvidence = options.loadEvidence ?? defaultEvidenceLoader(run);
  const statusOnly = options.statusOnly ?? false;
  let product: OrgMemoryProductPackage | undefined;
  let workspaceMetadata = new Map<string, Buffer>();

  const provider: TegamiPlugin = {
    name: "orgmemory-product-provider",
    enforce: "pre",
    async init() {
      workspaceMetadata = await collectWorkspaceMetadata(this.cwd);
    },
    async resolve() {
      const productPath = join(this.cwd, "release");
      const manifest = parseProductManifest(await readFile(join(productPath, "product.json"), "utf8"));
      product = new OrgMemoryProductPackage(productPath, manifest.version);
      this.graph.add(product);
    },
    async applyDraft(draft) {
      if (!product) throw new Error("OrgMemory product package was not resolved");
      const packageDraft = draft.getPackageDraft(PRODUCT_ID);
      if (!packageDraft) return;
      const nextVersion = packageDraft.bumpVersion(product);
      if (!nextVersion || nextVersion === product.version) return;
      const target = join(product.path, "product.json");
      const temporary = `${target}.${process.pid}.tmp`;
      await writeFile(
        temporary,
        `${JSON.stringify({ name: PRODUCT_NAME, version: nextVersion }, null, 2)}\n`,
        { encoding: "utf8", flag: "wx" },
      );
      await rename(temporary, target);
      product.version = nextVersion;
    },
    async initPublishPlan({ plan }) {
      if (!product) return;
      const packagePlan = productPlan(plan);
      if (!packagePlan) return;
      packagePlan.git ??= {};
      packagePlan.git.tag = `v${product.version}`;
    },
    async applyCliDraft() {
      if (verifyCurrentMain) await assertCurrentMain(run, this.cwd);
      const changelogPath = join(this.cwd, "release", "CHANGELOG.md");
      const normalizedChangelog = normalizeProductChangelog(
        await readFile(changelogPath, "utf8"),
      );
      validateProductReleaseHistory(normalizedChangelog, product?.version);
      await writeFile(changelogPath, normalizedChangelog, "utf8");
      await mkdir(join(this.cwd, "apps", "docs", "content", "includes"), { recursive: true });
      await mkdir(join(this.cwd, "apps", "docs", "content", "docs", "changelog"), {
        recursive: true,
      });
      const generatedFiles = new Map([
        [PUBLIC_CHANGELOG_INCLUDE, renderPublicProductChangelog(normalizedChangelog)],
        [PUBLIC_CHANGELOG_ARCHIVE_INCLUDE, renderArchivedProductChangelog(normalizedChangelog)],
        [PUBLIC_CHANGELOG_META, renderReleaseNavigationMeta(normalizedChangelog, "en")],
        [PUBLIC_CHANGELOG_META_VI, renderReleaseNavigationMeta(normalizedChangelog, "vi")],
      ]);
      for (const [path, content] of generatedFiles) {
        await writeFile(join(this.cwd, ...path.split("/")), content, "utf8");
      }
      const tracked = await run("git", ["diff", "--name-only", "--relative", "HEAD"], this.cwd);
      const untracked = await run(
        "git",
        ["ls-files", "--others", "--exclude-standard"],
        this.cwd,
      );
      const changed = new Set(
        `${tracked.stdout}\n${untracked.stdout}`
          .split(/\r?\n/)
          .map((path) => path.trim().replaceAll("\\", "/"))
          .filter(Boolean),
      );
      const unexpected = [...changed].filter(
        (path) => !VERSION_DIFF_ALLOWLIST.some((pattern) => pattern.test(path)),
      );
      if (unexpected.length > 0) {
        throw new Error(`Version Packages diff contains unexpected paths: ${unexpected.join(", ")}`);
      }
    },
    async publishPreflight({ pkg }) {
      if (pkg.id !== PRODUCT_ID || !pkg.version) return;
      const artifacts = parseReleaseArtifacts(
        await readFile(join(this.cwd, "release", "artifacts.json"), "utf8"),
      );
      if (verifyArtifacts) await assertArtifactEvidence(run, this.cwd, artifacts, loadEvidence);
      if (verifyRemote) {
        // GitHub's Version PR hook runs package preflights after creating the
        // release commit, so HEAD is intentionally no longer origin/main here.
        // Main freshness is enforced before draft mutation and again by the
        // whole-plan hooks immediately before and after a real publication.
        const expectedSha = await headSha(run, this.cwd);
        const tag = `v${pkg.version}`;
        if (statusOnly) {
          const existing = await remoteTagTarget(run, this.cwd, tag);
          if (existing) {
            await run("git", ["merge-base", "--is-ancestor", existing, expectedSha], this.cwd);
          }
        } else {
          await assertTagAvailable(run, this.cwd, tag, expectedSha);
        }
      }
      return { shouldPublish: true };
    },
    async resolvePlanStatus({ plan }) {
      const packagePlan = productPlan(plan);
      if (!packagePlan?.preflight?.shouldPublish || packagePlan.publishResult) return;
      if (!verifyRemote || !product?.version) return "pending";
      const head = await headSha(run, this.cwd);
      const artifacts = parseReleaseArtifacts(
        await readFile(join(this.cwd, "release", "artifacts.json"), "utf8"),
      );
      const tag = `v${product.version}`;
      const expectedSha = statusOnly ? await remoteTagTarget(run, this.cwd, tag) : head;
      if (!expectedSha) return "pending";
      if (statusOnly) {
        try {
          await run("git", ["merge-base", "--is-ancestor", expectedSha, head], this.cwd);
        } catch {
          return "pending";
        }
      }
      if (!(await releaseIsComplete(run, this.cwd, tag, expectedSha, artifacts))) {
        return "pending";
      }
    },
    async publish({ pkg }): Promise<PackagePublishResult | undefined> {
      if (pkg.id !== PRODUCT_ID) return;
      parseReleaseArtifacts(await readFile(join(this.cwd, "release", "artifacts.json"), "utf8"));
      return { type: "published" };
    },
    async beforePublishAll() {
      if (verifyCurrentMain) await assertCurrentMain(run, this.cwd);
    },
    async afterPublishAll({ plan }) {
      const packagePlan = productPlan(plan);
      if (
        verifyCurrentMain &&
        !plan.options.dryRun &&
        packagePlan?.preflight?.shouldPublish &&
        packagePlan.publishResult?.type === "published"
      ) {
        await assertCurrentMain(run, this.cwd);
      }
    },
  };

  const verifier: TegamiPlugin = {
    name: "orgmemory-release-verifier",
    enforce: "post",
    async applyDraft() {
      await Promise.all(
        [...workspaceMetadata].map(async ([path, content]) => {
          const current = await readFile(path).catch(() => undefined);
          if (!current || !current.equals(content)) await writeFile(path, content);
        }),
      );
    },
    async afterPublishAll({ plan }) {
      if (plan.options.dryRun || !verifyRemote || !product?.version) return;
      const packagePlan = productPlan(plan);
      if (!packagePlan?.preflight?.shouldPublish || packagePlan.publishResult?.type !== "published") return;
      const expectedSha = await headSha(run, this.cwd);
      const tag = `v${product.version}`;
      const actualSha = await remoteTagTarget(run, this.cwd, tag);
      if (actualSha !== expectedSha) {
        throw new Error(`Published tag ${tag} targets ${actualSha ?? "nothing"}, expected ${expectedSha}`);
      }
      const repo = process.env.GITHUB_REPOSITORY;
      if (!repo || !/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repo)) {
        throw new Error("GITHUB_REPOSITORY is required to verify a release");
      }
      const release = await run("gh", ["api", `repos/${repo}/releases/tags/${tag}`], this.cwd);
      const releaseJson: unknown = JSON.parse(release.stdout);
      if (!isRecord(releaseJson) || releaseJson.tag_name !== tag) {
        throw new Error(`GitHub Release for ${tag} is missing or mismatched`);
      }
      await run(
        "gh",
        ["release", "upload", tag, "release/artifacts.json#OrgMemory immutable artifact manifest", "--clobber"],
        this.cwd,
      );
      const artifacts = parseReleaseArtifacts(
        await readFile(join(this.cwd, "release", "artifacts.json"), "utf8"),
      );
      if (!(await releaseIsComplete(run, this.cwd, tag, expectedSha, artifacts))) {
        throw new Error(`GitHub Release ${tag} is missing its verified artifacts.json asset`);
      }
    },
  };

  return [provider, verifier];
}

export function releaseSummary(artifacts: ReleaseArtifacts): string {
  const images = artifacts.product.images
    .map((image) => `- ${image.component}: \`${image.reference}@${image.digest}\``)
    .join("\n");
  return [
    `Immutable artifacts selected from green main commit \`${artifacts.releaseSourceSha}\`.`,
    "",
    images,
    `- docs: \`${artifacts.docs.image.reference}@${artifacts.docs.image.digest}\``,
  ].join("\n");
}
