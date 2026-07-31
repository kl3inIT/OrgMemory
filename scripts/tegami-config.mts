import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { tegami } from "tegami";
import { simpleGenerator } from "tegami/generators/simple";
import { github } from "tegami/plugins/github";
import {
  PRODUCT_ID,
  parseReleaseArtifacts,
  productReleasePlugins,
  releaseSummary,
  type ProductPluginOptions,
} from "./tegami-product.mts";

export function createOrgMemoryTegami(productOptions: ProductPluginOptions = {}) {
  const root = process.cwd();
  return tegami({
    cwd: root,
    generator: simpleGenerator(),
    npm: {
      updateLockFile: false,
    },
    ignore: [/^npm:/],
    plugins: [
      productReleasePlugins(productOptions),
      github({
        repo: "kl3inIT/OrgMemory",
        versionPr: {
          base: "main",
          branch: "tegami/version-packages",
          commit: ({ type }) => ({
            title:
              type === "version-packages"
                ? "chore(release): version OrgMemory"
                : "chore(release): update publish lock",
          }),
        },
        release: {
          async create({ tag, plan }) {
            const artifacts = parseReleaseArtifacts(
              await readFile(join(root, "release", "artifacts.json"), "utf8"),
            );
            const changelog = plan.packages
              .get(PRODUCT_ID)
              ?.changelogs.flatMap((entry) =>
                entry.sections.flatMap((section) => [
                  `### ${section.title}`,
                  "",
                  section.content,
                  "",
                ]),
              )
              .join("\n")
              .trim();
            return {
              title: `OrgMemory ${tag}`,
              notes: [changelog, "## Immutable artifacts", "", releaseSummary(artifacts)]
                .filter(Boolean)
                .join("\n\n"),
            };
          },
        },
      }),
    ],
  });
}
