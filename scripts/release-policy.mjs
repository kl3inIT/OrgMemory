const IMPACTING_PATH =
  /^(?:(?:apps|build-logic|components|contracts|core|evaluation|gradle|infrastructure|integrations|patches)\/|\.github\/workflows\/|(?:\.dockerignore|\.gitleaks\.toml|build\.gradle\.kts|compose\.yaml|gradlew(?:\.bat)?|package\.json|pnpm-lock\.yaml|pnpm-workspace\.yaml|settings\.gradle\.kts)$)/;
const ENTRY_PATH = /^\.tegami\/[a-z0-9][a-z0-9._-]*\.md$/;

export function releaseRequirementFailure(changes, pullRequestBody) {
  const impacting = changes.some(({ path }) => IMPACTING_PATH.test(path));
  if (!impacting) return undefined;
  const addedEntry = changes.some(
    ({ status, path }) => /^(?:A|M)/.test(status) && ENTRY_PATH.test(path),
  );
  if (addedEntry) return undefined;
  const skipReason = String(pullRequestBody)
    .match(/(?:^|\n)\s*skip-release\s*:\s*(.{10,})/i)?.[1]
    ?.trim();
  if (skipReason) return undefined;
  return "Product-impacting pull requests require a .tegami entry or `skip-release: <reason>` (at least 10 characters) in the PR body";
}
