import { unzipSync } from "fflate"
import { describe, expect, it } from "vitest"

import {
  buildScratchSkillPackage,
  normalizeSelectedSkillPackage,
} from "@/features/assets/skill-package-browser"

describe("browser Skill packaging", () => {
  it("builds a portable scratch package with supporting files", async () => {
    const archive = await buildScratchSkillPackage({
      name: "support-triage",
      description: "Triage support requests.",
      instructions: "# Workflow\n\nUse approved evidence.",
      license: "Proprietary",
      supportingFiles: [new File(["Policy"], "policy.md")],
    })

    const files = unzipSync(new Uint8Array(await archive.arrayBuffer()))
    expect(Object.keys(files).sort()).toEqual([
      "support-triage/SKILL.md",
      "support-triage/references/policy.md",
    ])
    expect(new TextDecoder().decode(files["support-triage/SKILL.md"])).toContain(
      'name: "support-triage"',
    )
  })

  it("wraps one raw SKILL.md without pretending it was validated", async () => {
    const archive = await normalizeSelectedSkillPackage([
      new File(["---\nname: support\ndescription: Support\n---\n# Support"], "SKILL.md"),
    ])

    expect(Object.keys(unzipSync(new Uint8Array(await archive.arrayBuffer())))).toEqual([
      "SKILL.md",
    ])
  })

  it("rejects duplicate supporting file paths before upload", async () => {
    await expect(
      buildScratchSkillPackage({
        name: "support",
        description: "Support",
        instructions: "# Support",
        supportingFiles: [
          new File(["a"], "policy.md"),
          new File(["b"], "POLICY.md"),
        ],
      }),
    ).rejects.toThrow("case-colliding")
  })
})
