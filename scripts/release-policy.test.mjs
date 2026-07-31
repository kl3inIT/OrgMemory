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

test("release-only Version PR changes do not demand another entry", () => {
  assert.equal(
    releaseRequirementFailure(
      [
        { status: "M", path: "release/product.json" },
        { status: "D", path: ".tegami/feature.md" },
      ],
      "",
    ),
    undefined,
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
