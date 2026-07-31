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
