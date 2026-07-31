export const MAX_SKILL_ARCHIVE_BYTES = 20 * 1024 * 1024

const NAMESPACE = /^[a-z0-9]+(?:[._-][a-z0-9]+)*$/

export type SkillUploadValidation =
  | { ok: true; namespace: string }
  | { ok: false; message: string }

export function validateSkillUpload({
  file,
  namespace,
  knowledgeSpaceId,
}: {
  file?: Pick<File, "name" | "size">
  namespace: string
  knowledgeSpaceId: string
}): SkillUploadValidation {
  const normalizedNamespace = namespace.trim().toLowerCase()
  if (!file) return { ok: false, message: "Choose a Skill ZIP package." }
  if (!file.name.toLowerCase().endsWith(".zip")) {
    return { ok: false, message: "The package must be a ZIP file." }
  }
  if (file.size > MAX_SKILL_ARCHIVE_BYTES) {
    return { ok: false, message: "The ZIP package must be 20 MB or smaller." }
  }
  if (
    !normalizedNamespace ||
    normalizedNamespace.length > 128 ||
    !NAMESPACE.test(normalizedNamespace)
  ) {
    return {
      ok: false,
      message: "Use lowercase letters and numbers separated by '.', '_', or '-'.",
    }
  }
  if (!knowledgeSpaceId) return { ok: false, message: "Choose a Knowledge Space." }
  return { ok: true, namespace: normalizedNamespace }
}
