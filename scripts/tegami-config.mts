import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { tegami, type LogGenerator } from "tegami";
import { github } from "tegami/plugins/github";
import {
  PRODUCT_ID,
  parseReleaseArtifacts,
  productReleasePlugins,
  releaseSummary,
  type ProductPluginOptions,
} from "./tegami-product.mts";

export const orgMemoryChangelogGenerator: LogGenerator = {
  generate({ pkg, packageDraft }) {
    const lines = [`## ${pkg.name}@${pkg.version}`, ""];

    for (const entry of packageDraft.changelogs ?? []) {
      const subject = entry.subject?.trim();
      if (subject) lines.push(`### ${subject}`, "");

      for (const section of entry.sections) {
        // Tegami parses the entry's H1 as an empty section. The frontmatter
        // subject already represents that title in the assembled changelog.
        if (subject === section.title.trim() && section.content.trim() === "") continue;
        lines.push(`${subject ? "####" : "###"} ${section.title}`, "", section.content, "");
      }
    }

    return lines.join("\n").trim();
  },
};

export function createOrgMemoryTegami(productOptions: ProductPluginOptions = {}) {
  const root = process.cwd();
  return tegami({
    cwd: root,
    generator: orgMemoryChangelogGenerator,
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
