import assert from "node:assert/strict";
import test from "node:test";
import { releaseRequirementFailure } from "./release-policy.mjs";

test("product code requires a release entry", () => {
  assert.match(
    releaseRequirementFailure([{ status: "M", path: "core/src/main/java/Example.java" }], "") ?? "",
    /require a \.tegami entry/,
  );
});

test("a valid added release entry satisfies the gate", () => {
  assert.equal(
    releaseRequirementFailure(
      [
        { status: "M", path: "apps/web/src/app.tsx" },
        { status: "A", path: ".tegami/web-change.md" },
      ],
      "",
    ),
    undefined,
  );
});

test("an explicit skip-release reason satisfies the gate", () => {
  assert.equal(
    releaseRequirementFailure(
      [{ status: "M", path: "integrations/example/Test.java" }],
      "skip-release: test-only refactor with no runtime behavior change",
    ),
    undefined,
  );
});

test("a structurally valid Tegami Version PR does not recurse", () => {
  assert.equal(
    releaseRequirementFailure(
      [
        { status: "M", path: "release/product.json" },
        { status: "M", path: "release/CHANGELOG.md" },
        { status: "M", path: ".tegami/publish-lock.yaml" },
        { status: "M", path: "apps/docs/content/includes/product-changelog.md" },
        { status: "M", path: "apps/docs/content/includes/product-changelog-archive.md" },
        { status: "M", path: "apps/docs/content/docs/changelog/meta.json" },
        { status: "M", path: "apps/docs/content/docs/changelog/meta.vi.json" },
        { status: "D", path: ".tegami/feature.md" },
      ],
      "",
    ),
    undefined,
  );
});

for (const path of [
  "apps/docs/content/includes/product-changelog.md",
  "apps/docs/content/includes/product-changelog-archive.md",
  "apps/docs/content/docs/changelog/meta.json",
  "apps/docs/content/docs/changelog/meta.vi.json",
]) {
  test(`${path} is an exact Version PR generated output`, () => {
    assert.equal(
      releaseRequirementFailure(
        [
          { status: "M", path },
          { status: "A", path: ".tegami/generated-navigation.md" },
        ],
        "",
      ),
      undefined,
    );
  });
}

test("a synchronized manual changelog rewrite cannot impersonate a Version PR", () => {
  assert.match(
    releaseRequirementFailure(
      [
        { status: "M", path: "release/CHANGELOG.md" },
        { status: "M", path: "apps/docs/content/includes/product-changelog.md" },
        { status: "M", path: "apps/docs/content/docs/changelog/meta.json" },
        { status: "A", path: ".tegami/manual-edit.md" },
      ],
      "",
    ) ?? "",
    /only in a structurally valid Tegami Version PR/,
  );
});

test("other docs changes still require a release entry", () => {
  assert.match(
    releaseRequirementFailure(
      [{ status: "M", path: "apps/docs/content/docs/getting-started/index.mdx" }],
      "",
    ) ?? "",
    /require a \.tegami entry/,
  );
});

for (const path of [
  "build-logic/src/main/kotlin/conventions.gradle.kts",
  "gradle/libs.versions.toml",
  "patches/dependency.patch",
  ".dockerignore",
  ".gitleaks.toml",
]) {
  test(`${path} is a product-impacting build input`, () => {
    assert.match(
      releaseRequirementFailure([{ status: "M", path }], "") ?? "",
      /require a \.tegami entry/,
    );
  });
}
