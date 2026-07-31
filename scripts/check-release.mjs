import { execFile as execFileCallback } from "node:child_process";
import { readFile, readdir } from "node:fs/promises";
import { extname, join, relative } from "node:path";
import process from "node:process";
import { promisify } from "node:util";
import {
  parseProductManifest,
  parseReleaseArtifacts,
  renderPublicProductChangelog,
} from "./tegami-product.mts";
import { releaseRequirementFailure } from "./release-policy.mjs";

const root = process.cwd();
const failures = [];
const execFile = promisify(execFileCallback);
const entryDir = join(root, ".tegami");
const permittedPackageLines = /^\s{2}(?:orgmemory|product:orgmemory):\s*(?:patch|minor|major)\s*$/m;
const forbidden = [
  /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/i,
  /\b(?:ghp|github_pat|glpat)-[A-Za-z0-9_-]{20,}\b/,
  /\bAKIA[0-9A-Z]{16}\b/,
  /\b(?:password|passwd|client_secret|api_key|access_token)\s*[:=]\s*[^\s$<{][^\s]{7,}/i,
  /https?:\/\/[^\s/:]+:[^\s/@]+@/i,
  /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/,
];

try {
  parseProductManifest(await readFile(join(root, "release", "product.json"), "utf8"));
} catch (error) {
  failures.push(error instanceof Error ? error.message : String(error));
}

try {
  const canonicalChangelog = await readFile(join(root, "release", "CHANGELOG.md"), "utf8");
  const publicChangelog = await readFile(
    join(root, "apps", "docs", "content", "includes", "product-changelog.md"),
    "utf8",
  );
  if (publicChangelog !== renderPublicProductChangelog(canonicalChangelog)) {
    failures.push(
      "apps/docs/content/includes/product-changelog.md is not synchronized with release/CHANGELOG.md",
    );
  }
} catch (error) {
  failures.push(error instanceof Error ? error.message : String(error));
}

try {
  parseReleaseArtifacts(await readFile(join(root, "release", "artifacts.json"), "utf8"));
} catch (error) {
  if (error?.code !== "ENOENT") failures.push(error instanceof Error ? error.message : String(error));
}

for (const name of await readdir(entryDir).catch(() => [])) {
  if (extname(name) !== ".md") continue;
  if (!/^[a-z0-9][a-z0-9._-]*\.md$/.test(name)) {
    failures.push(`Invalid Tegami entry filename: ${name}`);
    continue;
  }
  const path = join(entryDir, name);
  const content = await readFile(path, "utf8");
  if (Buffer.byteLength(content, "utf8") > 16_384) failures.push(`${name} exceeds 16 KiB`);
  if (!content.startsWith("---\n") || !content.includes("\npackages:\n")) {
    failures.push(`${name} is missing Tegami frontmatter`);
  }
  if (!permittedPackageLines.test(content)) failures.push(`${name} must bump only orgmemory`);
  const packageLines = content.match(/^\s{2}[^\s].*:\s*(?:patch|minor|major)\s*$/gm) ?? [];
  if (packageLines.length !== 1) failures.push(`${name} must contain exactly one package bump`);
  if (!/^## (?:Breaking changes|Features|Fixes|Improvements|Documentation|Operations|Security)$/m.test(content)) {
    failures.push(`${name} must contain an approved public changelog section`);
  }
  for (const pattern of forbidden) {
    if (pattern.test(content)) failures.push(`${name} contains credential-like or unsafe content`);
  }
}

const eventPath = process.env.GITHUB_EVENT_PATH;
if (process.env.GITHUB_EVENT_NAME === "pull_request" && eventPath) {
  const event = JSON.parse(await readFile(eventPath, "utf8"));
  const baseSha = event?.pull_request?.base?.sha;
  const headSha = event?.pull_request?.head?.sha;
  if (!/^[0-9a-f]{40}$/.test(baseSha ?? "") || !/^[0-9a-f]{40}$/.test(headSha ?? "")) {
    failures.push("Pull request event lacks valid base/head SHAs for release-entry enforcement");
  } else {
    const { stdout } = await execFile("git", ["diff", "--name-status", `${baseSha}...${headSha}`], {
      cwd: root,
      encoding: "utf8",
      windowsHide: true,
    });
    const changes = stdout
      .split(/\r?\n/)
      .filter(Boolean)
      .map((line) => {
        const [status = "", ...parts] = line.split("\t");
        return { status, path: (parts.at(-1) ?? "").replaceAll("\\", "/") };
      });
    const body = String(event?.pull_request?.body ?? "");
    const requirementFailure = releaseRequirementFailure(changes, body);
    if (requirementFailure) failures.push(requirementFailure);
  }
}

const workflowFiles = (await readdir(join(root, ".github", "workflows")))
  .filter((name) => name.endsWith(".yml") || name.endsWith(".yaml"));
for (const name of workflowFiles) {
  const content = await readFile(join(root, ".github", "workflows", name), "utf8");
  if (/pull_request_target\s*:/.test(content)) {
    failures.push(`${name} uses forbidden pull_request_target`);
  }
}

const releaseWorkflow = await readFile(join(root, ".github", "workflows", "release.yml"), "utf8").catch(() => "");
for (const invariant of [
  "workflow_run.conclusion == 'success'",
  "github.event.workflow_run.event == 'push'",
  "github.event.workflow_run.head_repository.full_name == github.repository",
  "github.event.workflow_run.head_branch == 'main'",
  "cancel-in-progress: false",
  "scripts/check-publish-lock.mts",
  "release-workflow-policy.mjs automatic",
  "release-workflow-policy.mjs current-main",
  "release-workflow-policy.mjs phase",
  "Revalidate current main immediately before mutation",
]) {
  if (!releaseWorkflow.includes(invariant)) failures.push(`release.yml lacks trust invariant: ${invariant}`);
}
const ciWorkflow = await readFile(join(root, ".github", "workflows", "ci.yml"), "utf8");
for (const impactPath of [
  "apps/**",
  "build-logic/**",
  "core/**",
  "gradle/**",
  "integrations/**",
  "infrastructure/**",
  "pnpm-workspace.yaml",
  ".github/workflows/**",
  ".gitleaks.toml",
]) {
  if (!ciWorkflow.includes(`- \"${impactPath}\"`)) {
    failures.push(`ci.yml must route product-impact path ${impactPath} through release validation`);
  }
}

for (const buildWorkflow of ["build-images.yml", "build-docs.yml"]) {
  const content = await readFile(join(root, ".github", "workflows", buildWorkflow), "utf8");
  for (const forbiddenPath of ["release/**", ".tegami/**"]) {
    if (content.includes(forbiddenPath)) {
      failures.push(`${buildWorkflow} must keep ${forbiddenPath} as a release-only no-op`);
    }
  }
}
const productionDeploy = await readFile(join(root, ".github", "workflows", "deploy-production.yml"), "utf8");
if (!productionDeploy.includes("Verify immutable image set")) {
  failures.push("deploy-production.yml must require a verified image-set job");
}
const docsDeploy = await readFile(join(root, ".github", "workflows", "deploy-docs.yml"), "utf8");
if (!docsDeploy.includes("Publish immutable docs image")) {
  failures.push("deploy-docs.yml must require a published docs-image job");
}

if (failures.length > 0) {
  console.error(failures.map((failure) => `- ${failure}`).join("\n"));
  process.exit(1);
}

console.log(`Release management check passed (${relative(root, entryDir) || ".tegami"}).`);
