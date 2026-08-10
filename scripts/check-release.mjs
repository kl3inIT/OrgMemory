import { execFile as execFileCallback } from "node:child_process";
import { readFile, readdir } from "node:fs/promises";
import { extname, join, relative } from "node:path";
import process from "node:process";
import { promisify } from "node:util";
import {
  parseProductManifest,
  parseReleaseArtifacts,
  renderArchivedProductChangelog,
  renderPublicProductChangelog,
  renderReleaseNavigationMeta,
  validateProductReleaseHistory,
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

let productManifest;
try {
  productManifest = parseProductManifest(
    await readFile(join(root, "release", "product.json"), "utf8"),
  );
} catch (error) {
  failures.push(error instanceof Error ? error.message : String(error));
}

try {
  const canonicalChangelog = await readFile(join(root, "release", "CHANGELOG.md"), "utf8");
  validateProductReleaseHistory(canonicalChangelog, productManifest?.version);
  const generatedChangelogFiles = new Map([
    [
      "apps/docs/content/includes/product-changelog.md",
      renderPublicProductChangelog(canonicalChangelog),
    ],
    [
      "apps/docs/content/includes/product-changelog-archive.md",
      renderArchivedProductChangelog(canonicalChangelog),
    ],
    [
      "apps/docs/content/docs/changelog/meta.json",
      renderReleaseNavigationMeta(canonicalChangelog, "en"),
    ],
    [
      "apps/docs/content/docs/changelog/meta.vi.json",
      renderReleaseNavigationMeta(canonicalChangelog, "vi"),
    ],
  ]);
  for (const [path, expected] of generatedChangelogFiles) {
    const actual = await readFile(join(root, ...path.split("/")), "utf8").catch(() => "");
    if (actual !== expected) failures.push(`${path} is not synchronized with release/CHANGELOG.md`);
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
const productImageWorkflow = await readFile(
  join(root, ".github", "workflows", "build-images.yml"),
  "utf8",
);
const normalizedProductImageWorkflow = productImageWorkflow.replaceAll("\r\n", "\n");
for (const image of ["api", "worker"]) {
  const marker = `            ${image}:\n`;
  const blockStart = normalizedProductImageWorkflow.indexOf(marker);
  const remaining = normalizedProductImageWorkflow.slice(blockStart + marker.length);
  const nextFilter = remaining.search(/^            [a-z0-9_-]+:\s*$/m);
  const block = blockStart < 0
    ? ""
    : remaining.slice(0, nextFilter < 0 ? undefined : nextFilter);
  if (!block.includes('- "integrations/ai-model-gateways/**"')) {
    failures.push(
      `build-images.yml must rebuild the ${image} image when ai-model-gateways changes`,
    );
  }
}
if (normalizedProductImageWorkflow.includes('source="ghcr.io/kl3init/orgmemory-${image}:sha-${BASE_SHA}"')) {
  failures.push("build-images.yml must not carry forward from a mutable commit tag");
}
for (const requiredDigestContract of [
  'base_images: ${{ steps.base.outputs.base_images }}',
  '--name "production-image-set-${candidate}"',
  'source="ghcr.io/kl3init/orgmemory-${image}@${source_digest}"',
  ".[$image].digest // empty",
  ".[$image].sourceSha // empty",
  'git merge-base --is-ancestor "$source_sha" "$candidate"',
  "github.event.workflow_run.event == 'push'",
  "github.event.workflow_run.head_repository.full_name == github.repository",
]) {
  if (!normalizedProductImageWorkflow.includes(requiredDigestContract)) {
    failures.push(`build-images.yml is missing immutable carry-forward contract: ${requiredDigestContract}`);
  }
}
if (normalizedProductImageWorkflow.includes('source_sha="$BASE_SHA"')) {
  failures.push("build-images.yml must preserve the original source SHA across carry-forwards");
}
const productionDeploy = await readFile(join(root, ".github", "workflows", "deploy-production.yml"), "utf8");
if (!productionDeploy.includes("Verify immutable image set")) {
  failures.push("deploy-production.yml must require a verified image-set job");
}
if (!productionDeploy.includes("worktree add --detach")) {
  failures.push("deploy-production.yml must deploy from a clean linked worktree");
}
if (productionDeploy.includes("git checkout --detach '$COMMIT_SHA'")) {
  failures.push("deploy-production.yml must not mutate the operator checkout");
}
for (const durableDeployContract of [
  "run-detached-deploy.sh",
  "reconcile-detached-launch.sh",
  "DEPLOY_KILL_AFTER_SECONDS",
  "DEPLOY_INTERVENTION_LATCH",
  "deployment-intervention-required",
  "github.event.workflow_run.head_repository.full_name == github.repository",
  "github.event.workflow_run.head_branch == 'main'",
  "launch-acknowledged",
  "controller-started",
  "cleanup-requested",
  "mv -T --",
  "Redundant independent reconcilers",
  "verify-detached-controller.sh",
  "signal-qualified-process.py",
  "for reconciler_id in 1 2",
  "ORGMEMORY_RECONCILER_READY_MARKER",
  "markers_ready",
  "for marker in '$state_directory/reconciler-ready.1'",
  "diff-index --quiet",
  "ls-files --others --exclude-standard",
  "ORGMEMORY_TEAM_DEV_COORDINATION_SCRIPT",
  "setsid -f",
  "ORGMEMORY_KEYCLOAK_CONFIGURATION_SCRIPT",
  "remote_status",
  "timeout --signal=TERM --kill-after=10s 120s",
  "touch \"$state_directory.cleanup-requested\"",
  "cleanup_complete",
  "deploy-checkout",
  "exec 198>'$state_directory/ownership.lease'",
]) {
  if (!productionDeploy.includes(durableDeployContract)) {
    failures.push(`deploy-production.yml is missing durable controller contract: ${durableDeployContract}`);
  }
}
const detachedDeployController = await readFile(
  join(root, "infrastructure", "deployment", "scripts", "run-detached-deploy.sh"),
  "utf8",
);
const detachedLaunchReconciler = await readFile(
  join(root, "infrastructure", "deployment", "scripts", "reconcile-detached-launch.sh"),
  "utf8",
);
const detachedControllerVerifier = await readFile(
  join(root, "infrastructure", "deployment", "scripts", "verify-detached-controller.sh"),
  "utf8",
);
const qualifiedSignalHelper = await readFile(
  join(root, "infrastructure", "deployment", "scripts", "signal-qualified-process.py"),
  "utf8",
);
for (const finalizerCleanupContract of [
  'if ! rm -rf -- "$state_directory/docker-config"',
  "record_intervention credential-cleanup-failed",
  "record_intervention terminal-status-publish-failed",
]) {
  if (!detachedDeployController.includes(finalizerCleanupContract)) {
    failures.push(`detached controller finalizer is missing fail-closed cleanup: ${finalizerCleanupContract}`);
  }
}
const finalizerInstall = detachedDeployController.indexOf("trap finalize EXIT");
const controllerLease = detachedDeployController.indexOf("flock -n 198");
if (!detachedDeployController.includes('/proc/$controller_pid/fd/198')) {
  failures.push("detached controller must preserve the launcher-inherited lease");
}
const controllerClaim = detachedDeployController.indexOf(
  'ln -s "$ownership_target" "$ownership_file"',
);
const controllerStarted = detachedDeployController.indexOf(
  'touch "$state_directory/controller-started"',
);
if (
  finalizerInstall < 0 ||
  controllerLease < finalizerInstall ||
  controllerClaim < controllerLease ||
  controllerStarted < controllerClaim
) {
  failures.push(
    "detached controller must install its finalizer, acquire the inherited lease, atomically claim ownership, then publish controller-started",
  );
}
for (const claimWindowContract of [
  'controller_pid="$BASHPID"',
  'controller_starttime="$(cut -d \' \' -f 22 "/proc/$controller_pid/stat")"',
  'ownership_target="controller.$controller_pid.$controller_starttime"',
  "trap 'exit 143' TERM",
  'readlink "$ownership_file"',
]) {
  if (!detachedDeployController.includes(claimWindowContract)) {
    failures.push(
      `detached controller ownership-claim window is missing ${claimWindowContract}`,
    );
  }
}
for (const reconcilerCleanupContract of [
  'tombstone="${state_directory}.cleanup.${BASHPID}.${RANDOM}"',
  'mv -T -- "$state_directory" "$tombstone"',
  "flock -n 197",
  "publish_dead_active_status",
  "cleanup_tombstones_if_detached",
  "cleanup_terminal_under_lease",
  "terminal-status-retained-registry-credentials",
  "signal_qualified_controller",
  "cleanup_deploy_checkout",
  "git -C /apps/orgmemory worktree remove --force",
  "exec 198>&-",
  "terminal_elapsed",
  "terminal-cleanup-exceeded-hard-deadline",
  "pre-active-lease-exceeded-launch-timeout",
  "reconciler_ready_marker",
  'reconciler_pid="$BASHPID"',
  'reconciler_starttime="$(cut -d \' \' -f 22 "/proc/$reconciler_pid/stat")"',
  "/tmp/orgmemory-deploy.*/repo",
]) {
  if (!detachedLaunchReconciler.includes(reconcilerCleanupContract)) {
    failures.push(
      `detached launch cleanup must atomically tombstone state before recursive deletion: ${reconcilerCleanupContract}`,
    );
  }
}
for (const verifierContract of [
  "controller-started",
  "^controller\\.([1-9][0-9]*)\\.([1-9][0-9]*)$",
  '[[ -d "$state_directory" && ! -L "$state_directory" ]]',
  '"$current_uid:700"',
  '! -L "$state_directory/status"',
  '"$current_uid:600"',
  "verify_terminal_status()",
  "stat -c '%s'",
  '(( deployment_status <= 255 ))',
  '! -L "$state_directory/docker-config"',
  "flock -n 197",
]) {
  if (!detachedControllerVerifier.includes(verifierContract)) {
    failures.push(`detached controller handshake verifier is missing ${verifierContract}`);
  }
}
const terminalStatusProbe = 'if [[ -e "$state_directory/status" || -L "$state_directory/status" ]]';
if (
  detachedControllerVerifier.split(terminalStatusProbe).length - 1 < 3 ||
  detachedControllerVerifier.split("verify_terminal_status").length - 1 < 4
) {
  failures.push(
    "detached controller verifier must fully validate terminal status before ACTIVE validation, while the lease is held, and after a concurrent lease release",
  );
}
for (const pidfdContract of ["SYS_PIDFD_OPEN", "SYS_PIDFD_SEND_SIGNAL", "libc.syscall", "observed_starttime"]) {
  if (!qualifiedSignalHelper.includes(pidfdContract)) {
    failures.push(`qualified controller signaling must use pidfd identity: ${pidfdContract}`);
  }
}
const reconcilerReady = productionDeploy.indexOf("reconcilers_ready=true");
const strictRemotePreparation = productionDeploy.indexOf('"set -euo pipefail');
const stateDirectoryCreation = productionDeploy.indexOf("mkdir --mode=0700 '$state_directory'");
const launcherLease = productionDeploy.indexOf("exec 198>'$state_directory/ownership.lease'");
const linkedWorktree = productionDeploy.indexOf("git worktree add --detach");
const registryLogin = productionDeploy.indexOf("docker login ghcr.io");
if (
  strictRemotePreparation < 0 ||
  stateDirectoryCreation < strictRemotePreparation ||
  reconcilerReady < 0 ||
  launcherLease < reconcilerReady ||
  linkedWorktree < launcherLease ||
  registryLogin < linkedWorktree
) {
  failures.push(
    "deploy-production.yml must enable strict remote preparation before exclusive state creation, then acknowledge reconcilers before lease, worktree, and login",
  );
}
const controllerLaunch = productionDeploy.indexOf("if ! setsid -f");
const launchAcknowledgement = productionDeploy.indexOf("touch '$state_directory/launch-acknowledged'");
if (controllerLaunch < 0 || launchAcknowledgement < controllerLaunch) {
  failures.push("deploy-production.yml must acknowledge launch only after the controller starts");
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
