import process from "node:process";
import { pathToFileURL } from "node:url";

export function automaticRunTrusted(run, repository) {
  return (
    run?.conclusion === "success" &&
    run?.event === "push" &&
    run?.head_repository?.full_name === repository &&
    run?.head_branch === "main" &&
    /^[0-9a-f]{40}$/.test(run?.head_sha ?? "")
  );
}

export function decideReleasePhase({ hasEntries, hasLock, lockStatus }) {
  if (hasEntries && hasLock) {
    if (lockStatus === "pending") {
      throw new Error("A pending failed publish lock must be recovered before versioning");
    }
    if (lockStatus !== "success") {
      throw new Error("Unable to prove the existing publish lock is complete");
    }
    return "version";
  }
  if (hasEntries) return "version";
  if (hasLock) return "publish";
  return "idle";
}

export function currentMainTrusted({ candidateSha, originMainSha, isAncestor }) {
  return (
    /^[0-9a-f]{40}$/.test(candidateSha) &&
    candidateSha === originMainSha &&
    isAncestor === true
  );
}

function requiredBoolean(name) {
  const value = process.env[name];
  if (value === "true") return true;
  if (value === "false") return false;
  throw new Error(`${name} must be true or false`);
}

function runCli() {
  const command = process.argv[2];
  if (command === "automatic") {
    const run = JSON.parse(process.env.WORKFLOW_RUN_JSON ?? "null");
    if (!automaticRunTrusted(run, process.env.REPOSITORY ?? "")) {
      console.error("The automatic release trigger is not a trusted green main push");
      process.exitCode = 64;
      return;
    }
    console.log(run.head_sha);
    return;
  }
  if (command === "current-main") {
    console.log(
      currentMainTrusted({
        candidateSha: process.env.CANDIDATE_SHA ?? "",
        originMainSha: process.env.ORIGIN_MAIN_SHA ?? "",
        isAncestor: requiredBoolean("IS_ANCESTOR"),
      }),
    );
    return;
  }
  if (command === "phase") {
    try {
      const mode = decideReleasePhase({
        hasEntries: requiredBoolean("HAS_ENTRIES"),
        hasLock: requiredBoolean("HAS_LOCK"),
        lockStatus: process.env.LOCK_STATUS,
      });
      console.log(`mode=${mode}`);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      console.error(message);
      process.exitCode = message.startsWith("A pending failed") ? 65 : 66;
    }
    return;
  }
  console.error("Usage: release-workflow-policy.mjs <automatic|current-main|phase>");
  process.exitCode = 64;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  runCli();
}
