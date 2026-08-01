import { describe, expect, it } from "vitest"

import {
  buildSkillDraftHandoff,
  buildSkillInstallHandoff,
  SKILL_DRAFT_SCOPES,
  SKILL_INSTALL_SCOPES,
} from "@/features/assets/agent-handoff/skill-agent-handoffs"

describe("Skill agent handoffs", () => {
  it("keeps Draft publication bounded and asks instead of guessing destinations", () => {
    const handoff = buildSkillDraftHandoff("./my-skill")
    const prompt = handoff.promptTemplate ?? ""

    expect(prompt).toContain("orgmemory skill validate ./my-skill")
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

  it("pins exact install commands for each supported coding agent", () => {
    const reference = "productivity/decision-record-writer@1.0.0"
    const handoff = buildSkillInstallHandoff(reference)

    expect(handoff.agentTargets).toEqual([
      {
        id: "claude-code",
        label: "Claude Code",
        command: `orgmemory skill add ${reference} --agent claude-code`,
      },
      {
        id: "codex",
        label: "Codex",
        command: `orgmemory skill add ${reference} --agent codex`,
      },
    ])
    expect(handoff.promptTemplate).toContain(reference)
    expect(handoff.promptTemplate).toContain("explicit confirmation")
    expect(handoff.promptTemplate).not.toContain("http://")
    expect(handoff.promptTemplate).not.toContain("https://")
  })

  it("pins the displayed scopes to the documented CLI contract", () => {
    expect(SKILL_DRAFT_SCOPES).toEqual(["assets:read", "assets:write"])
    expect(SKILL_INSTALL_SCOPES).toEqual(["assets:read"])
  })
})
