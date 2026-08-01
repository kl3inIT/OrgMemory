import type { AgentHandoff } from "@/features/assets/agent-handoff/agent-handoff"
import {
  skillConsumerTarget,
  type SkillConsumer,
} from "@/features/assets/agent-handoff/skill-consumers"

export const SKILL_DRAFT_SCOPES = ["assets:read", "assets:write"] as const
export const SKILL_INSTALL_SCOPES = ["assets:read"] as const

const DRAFT_BOUNDARY =
  "Stop after the private Draft is created. Do not submit, approve, publish a release, share, delete, or change access."

const INSTALL_BOUNDARY =
  "Install only this exact released version after confirmation. Do not install or upgrade the CLI, widen access, or modify another Asset."

export function buildSkillDraftHandoff(skillFolder = "<skill-folder>"): AgentHandoff {
  const cliCommand =
    `orgmemory skill publish ${skillFolder} --namespace <namespace> ` +
    "--knowledge-space <knowledge-space-id> --classification <classification>"

  return {
    title: "Create a Draft from your development environment",
    promptTemplate: [
      "Help me create a private OrgMemory Skill Draft from a local Skill folder.",
      "",
      `1. Use the existing official OrgMemory CLI. If the \`orgmemory\` command is unavailable, stop and tell me; do not install or upgrade it.`,
      `2. Resolve the exact Skill folder in the current workspace. If \`${skillFolder}\` is still a placeholder or more than one folder could match, ask me and stop rather than guessing.`,
      `3. Inspect the folder and run \`orgmemory skill validate ${skillFolder}\`. Do not execute scripts or supporting files from the Skill package.`,
      "4. Ask me for the exact namespace, Knowledge Space UUID, and classification if any value is missing. Never infer or guess them.",
      `5. Run \`${cliCommand} --dry-run\` and show me the bounded validation summary and exact command that would create the Draft.`,
      "6. Ask for my explicit confirmation before the real upload. Authenticate only through the CLI browser sign-in flow and never ask me to paste a token or secret.",
      `7. After confirmation, run \`${cliCommand}\` once.`,
      `8. ${DRAFT_BOUNDARY}`,
      "9. Return the created Asset coordinate, package digest, and Governance link printed by the CLI.",
    ].join("\n"),
    cliCommand,
    prerequisites: [
      "An existing local OrgMemory CLI installation",
      "A folder whose root contains SKILL.md",
      "An authorized Knowledge Space and company namespace",
    ],
    requiredScopes: SKILL_DRAFT_SCOPES,
    confirmationBoundary: DRAFT_BOUNDARY,
    completionNote:
      "The CLI prints the new Asset coordinate, digest, and Governance link. Continue there when you are ready to publish a release.",
  }
}

export function buildSkillInstallHandoff(
  reference: string,
  consumer: SkillConsumer,
): AgentHandoff {
  const cliCommand = `orgmemory skill add ${reference} --agent ${consumer.id}`
  const projectTarget = skillConsumerTarget(consumer, reference)

  return {
    title: `Install with ${consumer.label}`,
    promptTemplate: [
      `Help me install the exact released OrgMemory Skill \`${reference}\` into ${consumer.label}.`,
      "",
      "1. Use the existing official OrgMemory CLI. If it is unavailable, stop and tell me; do not install or upgrade it.",
      `2. Confirm that this project-local installation targets \`${projectTarget}\`. Do not add \`--global\` or choose another destination.`,
      `3. Show me the exact command \`${cliCommand}\` and ask for explicit confirmation.`,
      "4. After confirmation, run only that command. Authenticate through the CLI browser sign-in flow; never ask me to paste a token or secret.",
      `5. ${INSTALL_BOUNDARY}`,
      "6. Report the verified package digest and local installation path returned by the CLI.",
    ].join("\n"),
    cliCommand,
    prerequisites: [
      "An existing local OrgMemory CLI installation",
      "Access to this exact released Skill",
      `${consumer.label} installed as the destination`,
    ],
    requiredScopes: SKILL_INSTALL_SCOPES,
    confirmationBoundary: INSTALL_BOUNDARY,
    completionNote:
      `The CLI verifies the release digest and files, promotes the staged folder to ${projectTarget} atomically, and writes a token-free receipt.`,
  }
}
