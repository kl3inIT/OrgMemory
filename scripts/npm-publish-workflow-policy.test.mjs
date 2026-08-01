import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const workflow = await readFile(
  new URL("../.github/workflows/publish-cli.yml", import.meta.url),
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
  assert.match(workflow, /npm pack --dry-run --json/);
  assert.match(workflow, /'dist\/index\.js', 'package\.json', 'README\.md', 'LICENSE'/);
  assert.match(workflow, /npm publish --access public --provenance/);
  assert.match(workflow, /attestations/);
  assert.match(workflow, /did not appear on npm within 120 seconds/);
  assert.match(workflow, /npm exec --yes --package=/);
  assert.doesNotMatch(workflow, /cache:/);
});
