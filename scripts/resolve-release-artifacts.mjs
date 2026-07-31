import { execFile as execFileCallback } from "node:child_process";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { promisify } from "node:util";
import process from "node:process";
import { parseReleaseArtifacts } from "./tegami-product.mts";

const execFile = promisify(execFileCallback);
const root = process.cwd();
const sourceSha = process.argv[2] ?? process.env.RELEASE_SOURCE_SHA;
if (!sourceSha || !/^[0-9a-f]{40}$/.test(sourceSha)) {
  throw new Error("A 40-character release source SHA is required");
}

async function run(command, args, options = {}) {
  return execFile(command, args, {
    cwd: root,
    encoding: "utf8",
    windowsHide: true,
    maxBuffer: 10 * 1024 * 1024,
    ...options,
  });
}

async function ghJson(args) {
  const { stdout } = await run("gh", args);
  return JSON.parse(stdout);
}

async function sleep(milliseconds) {
  await new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function waitForExactRun(workflow) {
  const deadline = Date.now() + 20 * 60_000;
  while (Date.now() < deadline) {
    const runs = await ghJson([
      "run", "list", "--workflow", workflow, "--commit", sourceSha, "--limit", "20",
      "--json", "databaseId,headSha,status,conclusion,event",
    ]);
    const exact = runs.find((run) => run.headSha === sourceSha && run.event === "workflow_run");
    if (exact?.status === "completed") {
      if (exact.conclusion !== "success") {
        throw new Error(`${workflow} failed for ${sourceSha} with ${exact.conclusion}`);
      }
      return exact;
    }
    await sleep(15_000);
  }
  throw new Error(`Timed out waiting for ${workflow} at ${sourceSha}`);
}

async function hasSuccessfulJob(runId, jobName) {
  const data = await ghJson(["run", "view", String(runId), "--json", "jobs"]);
  return data.jobs.some((job) => job.name === jobName && job.conclusion === "success");
}

async function isAncestor(candidate) {
  try {
    await run("git", ["merge-base", "--is-ancestor", candidate, sourceSha]);
    return true;
  } catch {
    return false;
  }
}

async function findEvidenceRun(workflow, jobName, exactRun) {
  if (await hasSuccessfulJob(exactRun.databaseId, jobName)) return exactRun;
  const candidates = await ghJson([
    "run", "list", "--workflow", workflow, "--branch", "main", "--limit", "100",
    "--status", "success", "--json", "databaseId,headSha,status,conclusion,createdAt,event",
  ]);
  for (const candidate of candidates) {
    if (candidate.event !== "workflow_run") continue;
    if (!/^[0-9a-f]{40}$/.test(candidate.headSha)) continue;
    if (!(await isAncestor(candidate.headSha))) continue;
    if (await hasSuccessfulJob(candidate.databaseId, jobName)) return candidate;
  }
  throw new Error(`No successful ${jobName} evidence is an ancestor of ${sourceSha}`);
}

async function downloadJson(runId, artifactName, filename) {
  const directory = await mkdtemp(join(tmpdir(), "orgmemory-release-evidence-"));
  try {
    await run("gh", ["run", "download", String(runId), "--name", artifactName, "--dir", directory]);
    return JSON.parse(await readFile(join(directory, filename), "utf8"));
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
}

await run("git", ["fetch", "--no-tags", "origin", "main"]);
await run("git", ["cat-file", "-e", `${sourceSha}^{commit}`]);
await run("git", ["merge-base", "--is-ancestor", sourceSha, "origin/main"]);

const [exactProduct, exactDocs] = await Promise.all([
  waitForExactRun("Build production images"),
  waitForExactRun("Build docs image"),
]);
const [productRun, docsRun] = await Promise.all([
  findEvidenceRun("Build production images", "Verify immutable image set", exactProduct),
  findEvidenceRun("Build docs image", "Publish immutable docs image", exactDocs),
]);
const [product, docs] = await Promise.all([
  downloadJson(
    productRun.databaseId,
    `production-image-set-${productRun.headSha}`,
    "release-images.json",
  ),
  downloadJson(docsRun.databaseId, `docs-release-${docsRun.headSha}`, "docs-release.json"),
]);

const combined = {
  schemaVersion: 1,
  releaseSourceSha: sourceSha,
  product: {
    decisionRunId: exactProduct.databaseId,
    manifestRunId: productRun.databaseId,
    commitSha: product.commitSha,
    images: product.images.map(({ component, reference, digest, sourceSha: imageSourceSha }) => ({
      component,
      reference,
      digest,
      sourceSha: imageSourceSha,
    })),
  },
  docs: {
    decisionRunId: exactDocs.databaseId,
    manifestRunId: docsRun.databaseId,
    commitSha: docs.commitSha,
    image: {
      reference: docs.image.reference,
      digest: docs.image.digest,
      sourceSha: docs.commitSha,
    },
  },
};
parseReleaseArtifacts(JSON.stringify(combined));
await writeFile(join(root, "release", "artifacts.json"), `${JSON.stringify(combined, null, 2)}\n`);
console.log(
  `Resolved product run ${productRun.databaseId} and docs run ${docsRun.databaseId} for ${sourceSha}.`,
);
