import { mkdtemp, mkdir, rm, writeFile } from "node:fs/promises"
import { tmpdir } from "node:os"
import { join } from "node:path"

import { unzipSync } from "fflate"
import { afterEach, describe, expect, it } from "vitest"

import { buildLocalSkillPackage } from "./skill-package.js"

const temporaryRoots: string[] = []

afterEach(async () => {
  await Promise.all(
    temporaryRoots.splice(0).map((root) =>
      rm(root, { recursive: true, force: true }),
    ),
  )
})

describe("local Skill packages", () => {
  it("builds stable, root-based ZIP bytes from sorted validated files", async () => {
    const folder = await skillFolder()
    await mkdir(join(folder, "references"))
    await writeFile(join(folder, "references", "policy.md"), "Policy\n")

    const first = await buildLocalSkillPackage(folder)
    const second = await buildLocalSkillPackage(folder)

    expect(second.packageDigest).toBe(first.packageDigest)
    expect(second.archiveBytes).toEqual(first.archiveBytes)
    expect(first.files.map((file) => file.path)).toEqual([
      "SKILL.md",
      "references/policy.md",
    ])
    expect(Object.keys(unzipSync(first.archiveBytes)).sort()).toEqual([
      "SKILL.md",
      "references/policy.md",
    ])
  })

  it("rejects unsupported frontmatter before creating an archive", async () => {
    const folder = await skillFolder(
      `---
name: expense-review
description: Review one expense
unexpected: value
---
# Expense review
`,
    )

    await expect(buildLocalSkillPackage(folder)).rejects.toThrow(
      "unsupported fields",
    )
  })

  it("requires the portable Skill name to match its folder", async () => {
    const folder = await skillFolder(
      `---
name: another-skill
description: Review one expense
---
# Expense review
`,
    )

    await expect(buildLocalSkillPackage(folder)).rejects.toThrow(
      "match its folder name",
    )
  })

  it("rejects whitespace-only metadata keys like the canonical inspector", async () => {
    const folder = await skillFolder(
      `---
name: expense-review
description: Review one expense
metadata:
  "   ": finance
---
# Expense review
`,
    )

    await expect(buildLocalSkillPackage(folder)).rejects.toThrow(
      "bounded scalar entries",
    )
  })
})

async function skillFolder(
  skillMarkdown = `---
name: expense-review
description: Review one expense using approved company policy
metadata:
  owner: finance
---
# Expense review
`,
): Promise<string> {
  const root = await mkdtemp(join(tmpdir(), "orgmemory-skill-test-"))
  temporaryRoots.push(root)
  const folder = join(root, "expense-review")
  await mkdir(folder)
  await writeFile(join(folder, "SKILL.md"), skillMarkdown)
  return folder
}
