import assert from "node:assert/strict";
import test from "node:test";
import {
  automaticRunTrusted,
  currentMainTrusted,
  decideReleasePhase,
} from "./release-workflow-policy.mjs";

const sha = "a".repeat(40);
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
  assert.equal(decideReleasePhase({ hasEntries: false, hasLock: true }), "publish");
  assert.equal(decideReleasePhase({ hasEntries: false, hasLock: false }), "idle");
});
