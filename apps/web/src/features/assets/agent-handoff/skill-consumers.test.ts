import { describe, expect, it } from "vitest"

import {
  getSkillConsumer,
  SKILL_CONSUMERS,
  skillConsumerTarget,
} from "@/features/assets/agent-handoff/skill-consumers"

describe("Skill consumers", () => {
  it("projects exactly the CLI adapters and their project-local directories", () => {
    expect(SKILL_CONSUMERS).toEqual([
      {
        id: "claude-code",
        label: "Claude Code",
        projectDirectory: ".claude/skills",
        installSupport: "SUPPORTED",
        runtimeCertification: "NOT_CERTIFIED",
      },
      {
        id: "codex",
        label: "Codex",
        projectDirectory: ".agents/skills",
        installSupport: "SUPPORTED",
        runtimeCertification: "NOT_CERTIFIED",
      },
    ])
  })

  it("binds the selected consumer to one exact target path", () => {
    const consumer = getSkillConsumer("codex")

    expect(skillConsumerTarget(consumer, "productivity/decision-record-writer@1.0.0")).toBe(
      ".agents/skills/decision-record-writer",
    )
  })
})

