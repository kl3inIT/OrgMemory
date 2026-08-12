import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import test from "node:test";
import { fileURLToPath } from "node:url";
import {
  automaticRunTrusted,
  currentMainTrusted,
  decideReleasePhase,
} from "./release-workflow-policy.mjs";

const sha = "a".repeat(40);
const policyCli = fileURLToPath(new URL("./release-workflow-policy.mjs", import.meta.url));
const releaseWorkflow = readFileSync(
  fileURLToPath(new URL("../.github/workflows/release.yml", import.meta.url)),
  "utf8",
);
const trusted = {
  conclusion: "success",
  event: "push",
  head_repository: { full_name: "kl3inIT/OrgMemory" },
  head_branch: "main",
  head_sha: sha,
};

test("only a same-repository successful main push is trusted", () => {
  assert.equal(automaticRunTrusted(trusted, "kl3inIT/OrgMemory"), true);
  for (const override of [
    { conclusion: "failure" },
    { event: "pull_request" },
    { head_repository: { full_name: "fork/OrgMemory" } },
    { head_branch: "feature" },
    { head_sha: "short" },
  ]) {
    assert.equal(automaticRunTrusted({ ...trusted, ...override }, "kl3inIT/OrgMemory"), false);
  }
});

test("stale or non-ancestor main candidates are rejected", () => {
  assert.equal(currentMainTrusted({ candidateSha: sha, originMainSha: sha, isAncestor: true }), true);
  assert.equal(currentMainTrusted({ candidateSha: sha, originMainSha: "b".repeat(40), isAncestor: true }), false);
  assert.equal(currentMainTrusted({ candidateSha: sha, originMainSha: sha, isAncestor: false }), false);
});

test("pending failed locks block newer entries", () => {
  assert.throws(
    () => decideReleasePhase({ hasEntries: true, hasLock: true, lockStatus: "pending" }),
    /must be recovered/,
  );
  assert.equal(
    decideReleasePhase({ hasEntries: true, hasLock: true, lockStatus: "success" }),
    "version",
  );
  assert.equal(
    decideReleasePhase({ hasEntries: false, hasLock: true, lockStatus: "pending" }),
    "publish",
  );
  assert.equal(
    decideReleasePhase({ hasEntries: false, hasLock: true, lockStatus: "success" }),
    "idle",
  );
  assert.throws(
    () => decideReleasePhase({ hasEntries: false, hasLock: true, lockStatus: "unknown" }),
    /Unable to prove/,
  );
  assert.equal(decideReleasePhase({ hasEntries: false, hasLock: false }), "idle");
  assert.equal(decideReleasePhase({ hasEntries: true, hasLock: false }), "version");
  assert.throws(
    () => decideReleasePhase({ hasEntries: true, hasLock: true, lockStatus: "unknown" }),
    /Unable to prove/,
  );
});

test("phase CLI preserves recovery exit codes", () => {
  const run = (lockStatus) =>
    spawnSync(process.execPath, [policyCli, "phase"], {
      encoding: "utf8",
      env: {
        ...process.env,
        HAS_ENTRIES: "true",
        HAS_LOCK: "true",
        LOCK_STATUS: lockStatus,
      },
    });
  assert.equal(run("pending").status, 65);
  assert.equal(run("unknown").status, 66);
  const success = run("success");
  assert.equal(success.status, 0);
  assert.equal(success.stdout.trim(), "mode=version");
});

test("idle release phases cannot reach artifact resolution or mutation", () => {
  assert.match(
    releaseWorkflow,
    /- name: Resolve immutable artifact evidence for a pending version\n\s+if: .*steps\.phase\.outputs\.mode == 'version'/,
  );
  for (const step of [
    "Revalidate current main immediately before mutation",
    "Version or publish product release",
  ]) {
    assert.match(
      releaseWorkflow,
      new RegExp(
        `- name: ${step}\\n\\s+if: >-\\n` +
          `\\s+steps\\.trust\\.outputs\\.current == 'true' &&\\n` +
          `\\s+\\(steps\\.phase\\.outputs\\.mode == 'version' \\|\\| ` +
          `steps\\.phase\\.outputs\\.mode == 'publish'\\)`,
      ),
    );
  }
});
