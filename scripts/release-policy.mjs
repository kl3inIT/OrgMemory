const IMPACTING_PATH =
  /^(?:(?:apps|build-logic|components|contracts|core|evaluation|gradle|infrastructure|integrations|patches|release)\/|\.github\/workflows\/|(?:\.dockerignore|\.gitleaks\.toml|build\.gradle\.kts|compose\.yaml|gradlew(?:\.bat)?|package\.json|pnpm-lock\.yaml|pnpm-workspace\.yaml|settings\.gradle\.kts)$)/;
const ENTRY_PATH = /^\.tegami\/[a-z0-9][a-z0-9._-]*\.md$/;
const PUBLISH_LOCK_PATH = ".tegami/publish-lock.yaml";
const RELEASE_CONTROLLED_PATH = /^release\/(?:product\.json|CHANGELOG\.md|artifacts\.json)$/;
const VERSION_GENERATED_PATH =
  /^apps\/docs\/content\/(?:(?:includes\/product-changelog(?:-archive)?\.md)|(?:docs\/changelog\/meta(?:\.vi)?\.json))$/;

function hasChanged(changes, path) {
  return changes.some((change) => /^(?:A|M)/.test(change.status) && change.path === path);
}

function isVersionTransition(changes) {
  const required = [
    "release/product.json",
    "release/CHANGELOG.md",
    PUBLISH_LOCK_PATH,
    "apps/docs/content/includes/product-changelog.md",
    "apps/docs/content/docs/changelog/meta.json",
    "apps/docs/content/docs/changelog/meta.vi.json",
  ];
  if (!required.every((path) => hasChanged(changes, path))) return false;
  if (!changes.some(({ status, path }) => status === "D" && ENTRY_PATH.test(path))) return false;

  return changes.every(({ status, path }) => {
    if (RELEASE_CONTROLLED_PATH.test(path)) return /^(?:A|M)$/.test(status);
    if (VERSION_GENERATED_PATH.test(path)) return /^(?:A|M)$/.test(status);
    if (path === PUBLISH_LOCK_PATH) return /^(?:A|M)$/.test(status);
    if (ENTRY_PATH.test(path)) return status === "D";
    return false;
  });
}

export function releaseRequirementFailure(changes, pullRequestBody) {
  const controlledReleaseChanged = changes.some(({ path }) => RELEASE_CONTROLLED_PATH.test(path));
  if (controlledReleaseChanged) {
    if (isVersionTransition(changes)) return undefined;
    return "Release manifests and canonical history may change only in a structurally valid Tegami Version PR";
  }

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
