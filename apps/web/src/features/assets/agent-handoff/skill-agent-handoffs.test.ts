import { describe, expect, it } from "vitest"

import {
  buildSkillDraftHandoff,
  buildSkillInstallHandoff,
  SKILL_DRAFT_SCOPES,
  SKILL_INSTALL_SCOPES,
} from "@/features/assets/agent-handoff/skill-agent-handoffs"
import { getSkillConsumer } from "@/features/assets/agent-handoff/skill-consumers"

describe("Skill agent handoffs", () => {
  it("keeps Draft publication bounded and asks instead of guessing destinations", () => {
    const handoff = buildSkillDraftHandoff("./my-skill")
    const prompt = handoff.promptTemplate ?? ""

    expect(prompt).toContain(
      "npx --yes @orgmemory/cli@0.1.1 skill validate ./my-skill",
    )
    expect(prompt).toContain("--dry-run")
    expect(prompt).toContain("more than one folder could match")
    expect(prompt).toContain("Ask me for the exact namespace, Knowledge Space UUID")
    expect(prompt).toContain("Never infer or guess them")
    expect(prompt).toContain("explicit confirmation")
    expect(prompt).toContain("private Draft")
    expect(prompt).not.toMatch(/(?:npm|pnpm|yarn|curl|wget)\s+(?:install|add)/i)
    expect(prompt).not.toMatch(/(?:sk-[a-z0-9_-]{12,}|Bearer\s+[a-z0-9._-]{12,})/i)

    for (const forbiddenAction of ["submit", "approve", "publish", "share", "delete"]) {
      expect(handoff.confirmationBoundary.toLowerCase()).toContain(forbiddenAction)
    }
  })

  it("pins the selected consumer, exact release, command, and project-local target", () => {
    const reference = "productivity/decision-record-writer@1.0.0"
    const handoff = buildSkillInstallHandoff(reference, getSkillConsumer("codex"))

    expect(handoff.cliCommand).toBe(
      `npx --yes @orgmemory/cli@0.1.1 skill add ${reference} --agent codex`,
    )
    expect(handoff.promptTemplate).toContain(reference)
    expect(handoff.promptTemplate).toContain("Codex")
    expect(handoff.promptTemplate).toContain(".agents/skills/decision-record-writer")
    expect(handoff.promptTemplate).toContain("explicit confirmation")
    expect(handoff.promptTemplate).not.toContain("Ask whether the target")
    expect(handoff.promptTemplate).not.toContain("--agent claude-code")
    expect(handoff.promptTemplate).not.toContain("http://")
    expect(handoff.promptTemplate).not.toContain("https://")
  })

  it("pins the displayed scopes to the documented CLI contract", () => {
    expect(SKILL_DRAFT_SCOPES).toEqual(["assets:read", "assets:write"])
    expect(SKILL_INSTALL_SCOPES).toEqual(["assets:read"])
  })
})
