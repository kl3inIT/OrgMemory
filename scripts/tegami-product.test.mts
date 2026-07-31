import assert from "node:assert/strict";
import { execFile as execFileCallback } from "node:child_process";
import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { promisify } from "node:util";
import { tegami } from "tegami";
import { createCli } from "tegami/cli";
import { createOrgMemoryTegami } from "./tegami-config.mts";
import {
  PRODUCT_ID,
  PRODUCT_CHANGELOG_PREAMBLE,
  assertCurrentMain,
  normalizeProductChangelog,
  parseProductManifest,
  parseReleaseArtifacts,
  productReleasePlugins,
  type CommandRunner,
} from "./tegami-product.mts";

const shaA = "a".repeat(40);
const shaB = "b".repeat(40);
const digest = (character: string) => `sha256:${character.repeat(64)}`;
const execFile = promisify(execFileCallback);

function artifacts() {
  const components = ["api", "worker", "mcp", "web", "keycloak", "postgres-rag"];
  return {
    schemaVersion: 1,
    releaseSourceSha: shaA,
    product: {
      decisionRunId: 100,
      manifestRunId: 101,
      commitSha: shaA,
      images: components.map((component, index) => ({
        component,
        reference: `ghcr.io/kl3init/orgmemory-${component}:sha-${shaA}`,
        digest: digest(String(index + 1)),
        sourceSha: shaA,
      })),
    },
    docs: {
      decisionRunId: 103,
      manifestRunId: 102,
      commitSha: shaA,
      image: {
        reference: `ghcr.io/kl3init/orgmemory-docs:sha-${shaA}`,
        digest: digest("d"),
        sourceSha: shaA,
      },
    },
  };
}

function evidenceLoader(expected = artifacts()) {
  return async (_runId: number, _artifactName: string, filename: string) => {
    if (filename === "release-images.json") {
      return JSON.stringify({
        commitSha: expected.product.commitSha,
        images: expected.product.images.map((image) => ({ ...image, built: true })),
      });
    }
    if (filename === "docs-release.json") {
      return JSON.stringify({
        commitSha: expected.docs.commitSha,
        image: {
          reference: expected.docs.image.reference,
          digest: expected.docs.image.digest,
          sourceSha: expected.docs.image.sourceSha,
        },
      });
    }
    throw new Error(`Unexpected evidence file: ${filename}`);
  };
}

function githubRunResponse(args: readonly string[], expected = artifacts()): string | undefined {
  const endpoint = args[1] ?? "";
  const runId = endpoint.match(/\/actions\/runs\/(\d+)$/)?.[1];
  if (runId) {
    const isProduct = [expected.product.decisionRunId, expected.product.manifestRunId].includes(
      Number(runId),
    );
    return JSON.stringify({
      conclusion: "success",
      event: "workflow_run",
      head_branch: "main",
      head_sha: isProduct ? expected.product.commitSha : expected.docs.commitSha,
      head_repository: { full_name: "kl3inIT/OrgMemory" },
    });
  }
  const jobsRunId = endpoint.match(/\/actions\/runs\/(\d+)\/jobs$/)?.[1];
  if (jobsRunId) {
    const numericRunId = Number(jobsRunId);
    const name =
      numericRunId === expected.product.decisionRunId
        ? "Plan affected images"
        : numericRunId === expected.docs.decisionRunId
          ? "Plan docs image"
          : numericRunId === expected.product.manifestRunId
            ? "Verify immutable image set"
            : "Publish immutable docs image";
    return JSON.stringify({
      jobs: [
        {
          name,
          conclusion: "success",
        },
      ],
    });
  }
  return undefined;
}

async function fixture() {
  const cwd = await mkdtemp(join(tmpdir(), "orgmemory-tegami-"));
  await mkdir(join(cwd, "release"), { recursive: true });
  await mkdir(join(cwd, "apps", "web"), { recursive: true });
  await mkdir(join(cwd, "infrastructure", "deployment"), { recursive: true });
  await mkdir(join(cwd, ".tegami"), { recursive: true });
  await writeFile(join(cwd, "release", "product.json"), '{"name":"orgmemory","version":"0.0.0"}\n');
  await writeFile(join(cwd, "release", "CHANGELOG.md"), `${PRODUCT_CHANGELOG_PREAMBLE}\n`);
  await writeFile(join(cwd, "release", "artifacts.json"), `${JSON.stringify(artifacts(), null, 2)}\n`);
  await writeFile(join(cwd, "package.json"), '{"name":"root","private":true,"version":"0.0.0"}\n');
  await writeFile(join(cwd, "apps", "web", "package.json"), '{"name":"web","private":true,"version":"0.0.0"}\n');
  await writeFile(join(cwd, "pnpm-workspace.yaml"), 'packages:\n  - "apps/*"\n');
  await writeFile(join(cwd, "pnpm-lock.yaml"), "lockfileVersion: '9.0'\n");
  await writeFile(join(cwd, "build.gradle.kts"), "plugins {}\n");
  await writeFile(join(cwd, "infrastructure", "deployment", "compose.yml"), "services: {}\n");
  await writeFile(
    join(cwd, ".tegami", "first-release.md"),
    "---\npackages:\n  orgmemory: minor\nsubject: Release management\n---\n\n# Release management\n\n## Features\n\nAdd product releases.\n",
  );
  return cwd;
}

test("product and artifact manifests are strict", () => {
  assert.deepEqual(parseProductManifest('{"name":"orgmemory","version":"1.2.3"}'), {
    name: "orgmemory",
    version: "1.2.3",
  });
  assert.throws(() => parseProductManifest('{"name":"orgmemory","version":"latest"}'));
  assert.throws(() => parseProductManifest('{"name":"orgmemory","version":"1.0.0-alpha..1"}'));
  assert.equal(parseReleaseArtifacts(JSON.stringify(artifacts())).product.images.length, 6);
  const incomplete = artifacts();
  incomplete.product.images.pop();
  assert.throws(() => parseReleaseArtifacts(JSON.stringify(incomplete)), /exactly/);
});

test("product changelog keeps its canonical H1 before generated releases", () => {
  const generated = `## orgmemory@0.1.0\n\n### Release management\n\n${PRODUCT_CHANGELOG_PREAMBLE}\n`;
  assert.equal(
    normalizeProductChangelog(generated),
    `${PRODUCT_CHANGELOG_PREAMBLE}\n\n## orgmemory@0.1.0\n\n### Release management\n`,
  );
  assert.throws(() => normalizeProductChangelog("## orgmemory@0.1.0\n"), /canonical preamble/);
});

test("Tegami 1.2.7 bumps only the synthetic product and writes its lock", async () => {
  const cwd = await fixture();
  try {
    const npmBefore = await readFile(join(cwd, "package.json"), "utf8");
    const webBefore = await readFile(join(cwd, "apps", "web", "package.json"), "utf8");
    const lockBefore = await readFile(join(cwd, "pnpm-lock.yaml"), "utf8");
    const gradleBefore = await readFile(join(cwd, "build.gradle.kts"), "utf8");
    const deployBefore = await readFile(join(cwd, "infrastructure", "deployment", "compose.yml"), "utf8");
    const paper = tegami({
      cwd,
      npm: { updateLockFile: false },
      ignore: [/^npm:/],
      plugins: productReleasePlugins({ verifyRemote: false }),
    });
    const draft = await paper.draft();
    assert.equal(draft.hasPending(), true);
    await draft.apply();
    assert.equal(parseProductManifest(await readFile(join(cwd, "release", "product.json"), "utf8")).version, "0.1.0");
    assert.match(await readFile(join(cwd, "release", "CHANGELOG.md"), "utf8"), /Release management/);
    assert.match(await readFile(join(cwd, ".tegami", "publish-lock.yaml"), "utf8"), new RegExp(PRODUCT_ID));
    assert.equal(await readFile(join(cwd, "package.json"), "utf8"), npmBefore);
    assert.equal(await readFile(join(cwd, "apps", "web", "package.json"), "utf8"), webBefore);
    assert.equal(await readFile(join(cwd, "pnpm-lock.yaml"), "utf8"), lockBefore);
    assert.equal(await readFile(join(cwd, "build.gradle.kts"), "utf8"), gradleBefore);
    assert.equal(await readFile(join(cwd, "infrastructure", "deployment", "compose.yml"), "utf8"), deployBefore);
    const plan = await paper.publish({ dryRun: true });
    assert.notEqual(plan, "skipped");
    if (plan !== "skipped") {
      assert.equal(plan.packages.get(PRODUCT_ID)?.git?.tag, "v0.1.0");
      assert.equal(plan.packages.get(PRODUCT_ID)?.publishResult?.type, "published");
    }
  } finally {
    await rm(cwd, { recursive: true, force: true });
  }
});

test("preflight rejects a version tag that targets another commit", async () => {
  const cwd = await fixture();
  const run: CommandRunner = async (command, args) => {
    if (command === "git" && args[0] === "diff") return { stdout: "", stderr: "" };
    if (command === "git" && args[0] === "ls-files") return { stdout: "", stderr: "" };
    if (command === "git" && args[0] === "rev-parse") return { stdout: `${shaA}\n`, stderr: "" };
    if (command === "git" && args[0] === "ls-remote") {
      return { stdout: `${shaB}\trefs/tags/v0.1.0\n`, stderr: "" };
    }
    throw new Error(`Unexpected command: ${command} ${args.join(" ")}`);
  };
  try {
    const paper = tegami({ cwd, ignore: [/^npm:/], npm: { updateLockFile: false }, plugins: productReleasePlugins({ run, verifyRemote: true, verifyArtifacts: false, verifyCurrentMain: false }) });
    const draft = await paper.draft();
    await draft.apply();
    await assert.rejects(() => paper.publish({ dryRun: true }), /targets .* expected/);
  } finally {
    await rm(cwd, { recursive: true, force: true });
  }
});

test("publish fails closed when a registry digest differs", async () => {
  const cwd = await fixture();
  const previousRepo = process.env.GITHUB_REPOSITORY;
  process.env.GITHUB_REPOSITORY = "kl3inIT/OrgMemory";
  const run: CommandRunner = async (command, args) => {
    if (command === "git" && args[0] === "merge-base") return { stdout: "", stderr: "" };
    if (command === "git" && args[0] === "diff") return { stdout: "", stderr: "" };
    if (command === "git" && args[0] === "rev-parse") return { stdout: `${shaA}\n`, stderr: "" };
    if (command === "git" && args[0] === "ls-remote") return { stdout: "", stderr: "" };
    if (command === "gh" && args[0] === "api") {
      const response = githubRunResponse(args);
      if (response) return { stdout: response, stderr: "" };
    }
    if (command === "docker") return { stdout: `${JSON.stringify(digest("f"))}\n`, stderr: "" };
    throw new Error(`Unexpected command: ${command} ${args.join(" ")}`);
  };
  try {
    const paper = tegami({ cwd, ignore: [/^npm:/], npm: { updateLockFile: false }, plugins: productReleasePlugins({ run, verifyRemote: true, verifyCurrentMain: false, loadEvidence: evidenceLoader() }) });
    const draft = await paper.draft();
    await draft.apply();
    await assert.rejects(() => paper.publish({ dryRun: true }), /resolves to .* expected/);
  } finally {
    if (previousRepo === undefined) delete process.env.GITHUB_REPOSITORY;
    else process.env.GITHUB_REPOSITORY = previousRepo;
    await rm(cwd, { recursive: true, force: true });
  }
});

test("current-main guard rejects a stale checkout immediately before mutation", async () => {
  const cwd = await fixture();
  const run: CommandRunner = async (command, args) => {
    if (command === "git" && args[0] === "fetch") return { stdout: "", stderr: "" };
    if (command === "git" && args[0] === "rev-parse" && args[1] === "HEAD") {
      return { stdout: `${shaA}\n`, stderr: "" };
    }
    if (command === "git" && args[0] === "rev-parse" && args[1] === "origin/main") {
      return { stdout: `${shaB}\n`, stderr: "" };
    }
    throw new Error(`Unexpected command: ${command} ${args.join(" ")}`);
  };
  try {
    await assert.rejects(() => assertCurrentMain(run, cwd), /is stale/);
  } finally {
    await rm(cwd, { recursive: true, force: true });
  }
});

test("successful publication verifies the tag and attaches artifact evidence idempotently", async () => {
  const cwd = await fixture();
  const calls: string[] = [];
  const expectedArtifacts = artifacts();
  let uploaded = false;
  const run: CommandRunner = async (command, args) => {
    calls.push(`${command} ${args.join(" ")}`);
    if (command === "git" && args[0] === "merge-base") return { stdout: "", stderr: "" };
    if (command === "git" && args[0] === "diff") return { stdout: "", stderr: "" };
    if (command === "git" && args[0] === "rev-parse") return { stdout: `${shaA}\n`, stderr: "" };
    if (command === "git" && args[0] === "ls-remote") {
      return { stdout: `${shaA}\trefs/tags/v0.1.0\n`, stderr: "" };
    }
    if (command === "docker") {
      const reference = args[3];
      const image = [
        ...expectedArtifacts.product.images,
        { component: "docs", ...expectedArtifacts.docs.image },
      ].find((candidate) => candidate.reference === reference);
      if (!image) throw new Error(`Unknown image reference: ${reference}`);
      return { stdout: `${JSON.stringify(image.digest)}\n`, stderr: "" };
    }
    if (command === "gh" && args[0] === "api") {
      const runResponse = githubRunResponse(args, expectedArtifacts);
      if (runResponse) return { stdout: runResponse, stderr: "" };
      if (args[1]?.includes("/releases/assets/")) {
        return { stdout: JSON.stringify(expectedArtifacts), stderr: "" };
      }
      return {
        stdout: JSON.stringify({ tag_name: "v0.1.0", assets: uploaded ? [{ id: 501, name: "artifacts.json" }] : [] }),
        stderr: "",
      };
    }
    if (command === "gh" && args[0] === "release") {
      uploaded = true;
      return { stdout: "", stderr: "" };
    }
    throw new Error(`Unexpected command: ${command} ${args.join(" ")}`);
  };
  const previousRepo = process.env.GITHUB_REPOSITORY;
  process.env.GITHUB_REPOSITORY = "kl3inIT/OrgMemory";
  try {
    const paper = tegami({ cwd, ignore: [/^npm:/], npm: { updateLockFile: false }, plugins: productReleasePlugins({ run, verifyRemote: true, verifyCurrentMain: false, loadEvidence: evidenceLoader(expectedArtifacts) }) });
    const draft = await paper.draft();
    await draft.apply();
    const first = await paper.publish();
    assert.notEqual(first, "skipped");
    assert.equal(uploaded, true);
    assert.ok(calls.some((call) => call.includes("gh release upload v0.1.0")));
    assert.equal((await paper.getPublishStatus()).status, "success");
    assert.equal(await paper.publish(), "skipped");
  } finally {
    if (previousRepo === undefined) delete process.env.GITHUB_REPOSITORY;
    else process.env.GITHUB_REPOSITORY = previousRepo;
    await rm(cwd, { recursive: true, force: true });
  }
});

test("publish rejects artifact bytes that do not match the named workflow run", async () => {
  const cwd = await fixture();
  const expected = artifacts();
  const previousRepo = process.env.GITHUB_REPOSITORY;
  process.env.GITHUB_REPOSITORY = "kl3inIT/OrgMemory";
  const run: CommandRunner = async (command, args) => {
    if (command === "git" && ["merge-base", "diff"].includes(args[0] ?? "")) {
      return { stdout: "", stderr: "" };
    }
    if (command === "gh" && args[0] === "api") {
      const response = githubRunResponse(args, expected);
      if (response) return { stdout: response, stderr: "" };
    }
    throw new Error(`Unexpected command: ${command} ${args.join(" ")}`);
  };
  const tamperedLoader = async (_runId: number, _artifactName: string, filename: string) => {
    if (filename === "release-images.json") {
      return JSON.stringify({ commitSha: expected.product.commitSha, images: expected.product.images.toReversed() });
    }
    return evidenceLoader(expected)(_runId, _artifactName, filename);
  };
  try {
    const paper = tegami({ cwd, ignore: [/^npm:/], npm: { updateLockFile: false }, plugins: productReleasePlugins({ run, verifyRemote: true, verifyCurrentMain: false, loadEvidence: tamperedLoader }) });
    const draft = await paper.draft();
    await draft.apply();
    await assert.rejects(() => paper.publish({ dryRun: true }), /manifest bytes do not match/);
  } finally {
    if (previousRepo === undefined) delete process.env.GITHUB_REPOSITORY;
    else process.env.GITHUB_REPOSITORY = previousRepo;
    await rm(cwd, { recursive: true, force: true });
  }
});

test("a same-name GitHub Release asset with different content stays pending", async () => {
  const cwd = await fixture();
  const expected = artifacts();
  const tampered = artifacts();
  tampered.releaseSourceSha = shaB;
  const previousRepo = process.env.GITHUB_REPOSITORY;
  process.env.GITHUB_REPOSITORY = "kl3inIT/OrgMemory";
  const run: CommandRunner = async (command, args) => {
    if (command === "git" && args[0] === "rev-parse") return { stdout: `${shaA}\n`, stderr: "" };
    if (command === "git" && args[0] === "ls-remote") {
      return { stdout: `${shaA}\trefs/tags/v0.1.0\n`, stderr: "" };
    }
    if (command === "gh" && args[0] === "api" && args[1]?.includes("/releases/assets/")) {
      return { stdout: JSON.stringify(tampered), stderr: "" };
    }
    if (command === "gh" && args[0] === "api") {
      return { stdout: JSON.stringify({ tag_name: "v0.1.0", assets: [{ id: 9, name: "artifacts.json" }] }), stderr: "" };
    }
    throw new Error(`Unexpected command: ${command} ${args.join(" ")}`);
  };
  try {
    const paper = tegami({ cwd, ignore: [/^npm:/], npm: { updateLockFile: false }, plugins: productReleasePlugins({ run, verifyRemote: true, verifyArtifacts: false, verifyCurrentMain: false }) });
    const draft = await paper.draft();
    await draft.apply();
    assert.equal((await paper.getPublishStatus()).status, "pending");
  } finally {
    if (previousRepo === undefined) delete process.env.GITHUB_REPOSITORY;
    else process.env.GITHUB_REPOSITORY = previousRepo;
    await rm(cwd, { recursive: true, force: true });
  }
});

test("lock-status mode recognizes a completed release tag on an ancestor", async () => {
  const cwd = await fixture();
  const expected = artifacts();
  const previousRepo = process.env.GITHUB_REPOSITORY;
  process.env.GITHUB_REPOSITORY = "kl3inIT/OrgMemory";
  const run: CommandRunner = async (command, args) => {
    if (command === "git" && args[0] === "rev-parse") return { stdout: `${shaB}\n`, stderr: "" };
    if (command === "git" && args[0] === "ls-remote") {
      return { stdout: `${shaA}\trefs/tags/v0.1.0\n`, stderr: "" };
    }
    if (command === "git" && args[0] === "merge-base") return { stdout: "", stderr: "" };
    if (command === "gh" && args[0] === "api" && args[1]?.includes("/releases/assets/")) {
      return { stdout: JSON.stringify(expected), stderr: "" };
    }
    if (command === "gh" && args[0] === "api") {
      return { stdout: JSON.stringify({ tag_name: "v0.1.0", assets: [{ id: 10, name: "artifacts.json" }] }), stderr: "" };
    }
    throw new Error(`Unexpected command: ${command} ${args.join(" ")}`);
  };
  try {
    const paper = tegami({ cwd, ignore: [/^npm:/], npm: { updateLockFile: false }, plugins: productReleasePlugins({ run, verifyRemote: true, verifyArtifacts: false, verifyCurrentMain: false, statusOnly: true }) });
    const draft = await paper.draft();
    await draft.apply();
    assert.equal((await paper.getPublishStatus()).status, "success");
  } finally {
    if (previousRepo === undefined) delete process.env.GITHUB_REPOSITORY;
    else process.env.GITHUB_REPOSITORY = previousRepo;
    await rm(cwd, { recursive: true, force: true });
  }
});

test("pinned Tegami contract versions in a temporary repository with a bare remote", { concurrency: false }, async () => {
  const cwd = await fixture();
  const remote = await mkdtemp(join(tmpdir(), "orgmemory-tegami-remote-"));
  const originalCwd = process.cwd();
  const git = (directory: string, args: string[]) =>
    execFile("git", args, { cwd: directory, encoding: "utf8", windowsHide: true });
  try {
    await git(cwd, ["init", "--initial-branch=main"]);
    await git(cwd, ["config", "user.name", "OrgMemory Test"]);
    await git(cwd, ["config", "user.email", "test@orgmemory.invalid"]);
    await git(cwd, ["add", "-A"]);
    await git(cwd, ["commit", "-m", "fixture"]);
    await git(remote, ["init", "--bare"]);
    await git(cwd, ["remote", "add", "origin", remote]);
    await git(cwd, ["push", "-u", "origin", "main"]);
    process.chdir(cwd);
    const paper = createOrgMemoryTegami({ verifyRemote: false, verifyCurrentMain: false });
    await createCli(paper).parseAsync(["version"]);
    const changelog = await readFile(join(cwd, "release", "CHANGELOG.md"), "utf8");
    assert.equal(changelog.startsWith(`${PRODUCT_CHANGELOG_PREAMBLE}\n\n## orgmemory@0.1.0`), true);
    assert.equal((changelog.match(/### Release management/g) ?? []).length, 1);
    assert.equal(parseProductManifest(await readFile(join(cwd, "release", "product.json"), "utf8")).version, "0.1.0");
    assert.match(await readFile(join(cwd, ".tegami", "publish-lock.yaml"), "utf8"), /product:orgmemory/);
    const plan = await paper.publish();
    assert.notEqual(plan, "skipped");
    const { stdout: tagTarget } = await git(cwd, ["rev-list", "-n", "1", "v0.1.0"]);
    const { stdout: head } = await git(cwd, ["rev-parse", "HEAD"]);
    assert.equal(tagTarget.trim(), head.trim());
    process.chdir(originalCwd);
  } finally {
    process.chdir(originalCwd);
    await rm(cwd, { recursive: true, force: true });
    await rm(remote, { recursive: true, force: true });
  }
});

test("production GitHub hook retries after Release creation fails behind a successful tag", { concurrency: false }, async () => {
  const cwd = await fixture();
  const remote = await mkdtemp(join(tmpdir(), "orgmemory-tegami-github-remote-"));
  const originalCwd = process.cwd();
  const originalActions = process.env.GITHUB_ACTIONS;
  const originalCi = process.env.CI;
  const originalToken = process.env.GITHUB_TOKEN;
  const originalFetch = globalThis.fetch;
  const git = (directory: string, args: string[]) =>
    execFile("git", args, { cwd: directory, encoding: "utf8", windowsHide: true });
  let releaseExists = false;
  let createAttempts = 0;
  globalThis.fetch = async (_input, init) => {
    if (init?.method === "HEAD") {
      return new Response("", { status: releaseExists ? 200 : 404 });
    }
    if (init?.method === "POST") {
      createAttempts++;
      if (createAttempts === 1) return new Response("injected failure", { status: 500 });
      releaseExists = true;
      return new Response("{}", { status: 201, headers: { "content-type": "application/json" } });
    }
    throw new Error(`Unexpected GitHub request method: ${init?.method ?? "GET"}`);
  };
  // The test exercises the release hook against a bare Git remote. Do not let
  // the ambient GitHub Actions context turn the preceding `version` command
  // into a real Version PR push to the checked-out repository.
  delete process.env.GITHUB_ACTIONS;
  delete process.env.CI;
  process.env.GITHUB_TOKEN = "test-token";
  try {
    await git(cwd, ["init", "--initial-branch=main"]);
    await git(cwd, ["config", "user.name", "OrgMemory Test"]);
    await git(cwd, ["config", "user.email", "test@orgmemory.invalid"]);
    await git(cwd, ["add", "-A"]);
    await git(cwd, ["commit", "-m", "fixture"]);
    await git(remote, ["init", "--bare"]);
    await git(cwd, ["remote", "add", "origin", remote]);
    await git(cwd, ["push", "-u", "origin", "main"]);
    process.chdir(cwd);
    const first = createOrgMemoryTegami({ verifyRemote: false, verifyCurrentMain: false });
    await createCli(first).parseAsync(["version"]);
    await assert.rejects(() => first.publish(), /Failed to create GitHub release/);
    await git(cwd, ["rev-parse", "refs/tags/v0.1.0"]);

    const retry = createOrgMemoryTegami({ verifyRemote: false, verifyCurrentMain: false });
    assert.notEqual(await retry.publish(), "skipped");
    assert.equal(createAttempts, 2);
    assert.equal(releaseExists, true);
  } finally {
    process.chdir(originalCwd);
    globalThis.fetch = originalFetch;
    if (originalActions === undefined) delete process.env.GITHUB_ACTIONS;
    else process.env.GITHUB_ACTIONS = originalActions;
    if (originalCi === undefined) delete process.env.CI;
    else process.env.CI = originalCi;
    if (originalToken === undefined) delete process.env.GITHUB_TOKEN;
    else process.env.GITHUB_TOKEN = originalToken;
    await rm(cwd, { recursive: true, force: true });
    await rm(remote, { recursive: true, force: true });
  }
});

test("production Git hook retries a failed tag push from a fresh runner", { concurrency: false }, async () => {
  const cwd = await fixture();
  const remote = await mkdtemp(join(tmpdir(), "orgmemory-tegami-push-remote-"));
  const originalCwd = process.cwd();
  const originalCi = process.env.CI;
  const originalToken = process.env.GITHUB_TOKEN;
  const git = (directory: string, args: string[]) =>
    execFile("git", args, { cwd: directory, encoding: "utf8", windowsHide: true });
  process.env.CI = "true";
  delete process.env.GITHUB_TOKEN;
  try {
    await git(cwd, ["init", "--initial-branch=main"]);
    await git(cwd, ["config", "user.name", "OrgMemory Test"]);
    await git(cwd, ["config", "user.email", "test@orgmemory.invalid"]);
    await git(cwd, ["add", "-A"]);
    await git(cwd, ["commit", "-m", "fixture"]);
    await git(remote, ["init", "--bare"]);
    await git(cwd, ["remote", "add", "origin", remote]);
    await git(cwd, ["push", "-u", "origin", "main"]);
    process.chdir(cwd);
    const first = createOrgMemoryTegami({ verifyRemote: false, verifyCurrentMain: false });
    await createCli(first).parseAsync(["version"]);
    await git(cwd, ["remote", "set-url", "origin", join(cwd, "missing-remote.git")]);
    await assert.rejects(() => first.publish(), /Failed to push Git tags/);

    await git(cwd, ["remote", "set-url", "origin", remote]);
    await git(cwd, ["tag", "--delete", "v0.1.0"]);
    const retry = createOrgMemoryTegami({ verifyRemote: false, verifyCurrentMain: false });
    assert.notEqual(await retry.publish(), "skipped");
    const { stdout } = await git(remote, ["show-ref", "--verify", "refs/tags/v0.1.0"]);
    assert.match(stdout, /^[0-9a-f]{40}\s+refs\/tags\/v0\.1\.0/m);
  } finally {
    process.chdir(originalCwd);
    if (originalCi === undefined) delete process.env.CI;
    else process.env.CI = originalCi;
    if (originalToken === undefined) delete process.env.GITHUB_TOKEN;
    else process.env.GITHUB_TOKEN = originalToken;
    await rm(cwd, { recursive: true, force: true });
    await rm(remote, { recursive: true, force: true });
  }
});
