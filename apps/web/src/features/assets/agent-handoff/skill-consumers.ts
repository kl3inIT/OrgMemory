export type SkillConsumer = {
  id: "claude-code" | "codex"
  label: string
  projectDirectory: ".claude/skills" | ".agents/skills"
  installSupport: "SUPPORTED"
  runtimeCertification: "NOT_CERTIFIED"
}

export const SKILL_CONSUMERS = [
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
] as const satisfies readonly SkillConsumer[]

export type SkillConsumerId = (typeof SKILL_CONSUMERS)[number]["id"]

export function getSkillConsumer(id: SkillConsumerId): (typeof SKILL_CONSUMERS)[number] {
  const consumer = SKILL_CONSUMERS.find((candidate) => candidate.id === id)
  if (!consumer) throw new Error(`Unsupported Skill consumer: ${id}`)
  return consumer
}

export function skillConsumerTarget(consumer: SkillConsumer, reference: string): string {
  const versionSeparator = reference.lastIndexOf("@")
  const coordinate = versionSeparator > 0 ? reference.slice(0, versionSeparator) : reference
  const slug = coordinate.split("/").at(-1) || "<skill>"
  return `${consumer.projectDirectory}/${slug}`
}

