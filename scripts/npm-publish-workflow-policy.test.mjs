import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const workflow = await readFile(
  new URL("../.github/workflows/publish-cli.yml", import.meta.url),
  "utf8",
);
const packageJson = JSON.parse(
  await readFile(new URL("../apps/cli/package.json", import.meta.url), "utf8"),
);
const cliSource = await readFile(
  new URL("../apps/cli/src/index.ts", import.meta.url),
  "utf8",
);

test("CLI publication is manual, approved, OIDC-only, and serialized", () => {
  assert.match(workflow, /workflow_dispatch:/);
  assert.match(workflow, /environment: npm-production/);
  assert.match(workflow, /id-token: write/);
  assert.match(workflow, /group: orgmemory-cli-publish/);
  assert.match(workflow, /cancel-in-progress: false/);
  assert.match(workflow, /CONFIRM_PUBLISH.*PUBLISH/s);
  assert.doesNotMatch(workflow, /NODE_AUTH_TOKEN|NPM_TOKEN/);
});

test("CLI publication pins a green current main SHA and exact package version", () => {
  assert.match(workflow, /git rev-parse origin\/main/);
  assert.match(workflow, /gh run list --workflow CI --commit/);
  assert.match(workflow, /headBranch == "main"/);
  assert.match(workflow, /apps\/cli\/package\.json/);
  assert.match(workflow, /PACKAGE_VERSION.*actual_version/s);
});

test("CLI publication verifies Node 24, the tarball, provenance, and npx execution", () => {
  assert.match(workflow, /node-version: 24/);
  assert.match(workflow, /pnpm install --frozen-lockfile/);
  assert.match(workflow, /npm pack --json --pack-destination/);
  assert.match(workflow, /'dist\/index\.js', 'package\.json', 'README\.md', 'LICENSE'/);
  assert.match(workflow, /--package="\$tarball" -- orgmemory --version/);
  assert.match(workflow, /PACKAGE_VERSION.*orgmemory --version/s);
  assert.match(workflow, /npm publish --access public --provenance/);
  assert.match(workflow, /attestations/);
  assert.match(workflow, /--prefer-online/);
  assert.match(workflow, /expected_integrity/);
  assert.match(workflow, /Exact package already exists with matching integrity/);
  assert.match(workflow, /did not become fully verifiable on npm within 180 seconds/);
  assert.doesNotMatch(workflow, /if \[\[ -n "\$published" \]\]; then break; fi/);
  assert.match(workflow, /npm exec --yes --package=/);
  assert.match(workflow, /npm install[\s\S]*@orgmemory\/cli@\$PACKAGE_VERSION/);
  assert.match(workflow, /npm audit signatures/);
  assert.ok(
    workflow.indexOf("npm audit signatures") <
      workflow.lastIndexOf("npm exec --yes --package="),
    "registry signatures must be audited before the published CLI executes",
  );
  assert.doesNotMatch(
    workflow,
    /^\s{12,}NODE$/m,
    "nested heredoc terminators are indented after YAML block stripping",
  );
  assert.doesNotMatch(workflow, /cache:/);
});

test("CLI package preserves the executable and binds provenance to this repository", () => {
  assert.equal(packageJson.bin?.orgmemory, "dist/index.js");
  assert.equal(packageJson.repository?.type, "git");
  assert.equal(
    packageJson.repository?.url,
    "git+https://github.com/kl3inIT/OrgMemory.git",
  );
  assert.equal(packageJson.repository?.directory, "apps/cli");
  assert.match(cliSource, /^#!\/usr\/bin\/env node\r?\n/);
});
