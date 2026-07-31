import { strToU8, zipSync, type Zippable } from "fflate"

export const MAX_SKILL_ARCHIVE_BYTES = 20 * 1024 * 1024
export const MAX_SKILL_CONTENT_BYTES = 50 * 1024 * 1024
export const MAX_SKILL_FILES = 300

const FIXED_ZIP_TIME = new Date(2000, 0, 1, 0, 0, 0, 0)
const SKILL_NAME = /^[a-z0-9]+(?:-[a-z0-9]+)*$/

type ScratchSkill = {
  name: string
  description: string
  instructions: string
  license?: string
  compatibility?: string
  allowedTools?: string
  supportingFiles: File[]
}

export async function normalizeSelectedSkillPackage(files: File[]): Promise<File> {
  if (files.length === 0) throw new Error("Choose a SKILL.md, ZIP, or Skill folder.")
  if (files.length === 1 && isZip(files[0]!)) {
    requireArchiveSize(files[0]!.size)
    return files[0]!
  }

  const entries = await Promise.all(
    files.map(async (file) => ({
      path: file.webkitRelativePath || file.name,
      bytes: new Uint8Array(await file.arrayBuffer()),
    })),
  )
  return packageEntries(entries, "skill-upload.zip")
}

export async function buildScratchSkillPackage(input: ScratchSkill): Promise<File> {
  const name = input.name.trim()
  if (!SKILL_NAME.test(name) || name.length > 64) {
    throw new Error("Skill name must use lowercase letters, digits, and single hyphens.")
  }
  const description = required(input.description, "Description", 1024)
  const instructions = required(input.instructions, "Instructions", 512 * 1024)
  const manifest = [
    "---",
    `name: ${JSON.stringify(name)}`,
    `description: ${JSON.stringify(description)}`,
    optionalYaml("license", input.license, 1024),
    optionalYaml("compatibility", input.compatibility, 500),
    optionalYaml("allowed-tools", input.allowedTools, 2048),
    "---",
    instructions,
    "",
  ]
    .filter((line) => line !== undefined)
    .join("\n")

  const support = await Promise.all(
    input.supportingFiles.map(async (file) => ({
      path: `${name}/references/${file.name}`,
      bytes: new Uint8Array(await file.arrayBuffer()),
    })),
  )
  return packageEntries(
    [{ path: `${name}/SKILL.md`, bytes: strToU8(manifest) }, ...support],
    `${name}.zip`,
  )
}

function packageEntries(
  entries: Array<{ path: string; bytes: Uint8Array }>,
  fileName: string,
): File {
  if (entries.length === 0 || entries.length > MAX_SKILL_FILES) {
    throw new Error("A Skill package must contain between 1 and 300 files.")
  }
  let contentBytes = 0
  const collisions = new Set<string>()
  const archive = Object.create(null) as Zippable
  for (const entry of entries.sort((left, right) =>
    left.path < right.path ? -1 : left.path > right.path ? 1 : 0,
  )) {
    requireSafePath(entry.path)
    const collisionKey = entry.path.toLowerCase()
    if (collisions.has(collisionKey)) {
      throw new Error("Skill files contain duplicate or case-colliding paths.")
    }
    collisions.add(collisionKey)
    contentBytes += entry.bytes.byteLength
    if (contentBytes > MAX_SKILL_CONTENT_BYTES) {
      throw new Error("Skill package content exceeds 50 MiB.")
    }
    archive[entry.path] = [
      entry.bytes,
      { attrs: 0o644 << 16, level: 6, mtime: FIXED_ZIP_TIME, os: 3 },
    ]
  }
  const bytes = zipSync(archive, { level: 6, mtime: FIXED_ZIP_TIME })
  requireArchiveSize(bytes.byteLength)
  return new File([bytes], fileName, { type: "application/zip" })
}

function optionalYaml(key: string, value: string | undefined, maximumLength: number) {
  const normalized = value?.trim()
  if (!normalized) return undefined
  if (normalized.length > maximumLength) {
    throw new Error(`${key} exceeds its limit.`)
  }
  return `${key}: ${JSON.stringify(normalized)}`
}

function required(value: string, label: string, maximumLength: number) {
  const normalized = value.trim()
  if (!normalized || normalized.length > maximumLength) {
    throw new Error(`${label} is required and must stay within its limit.`)
  }
  return normalized
}

function isZip(file: File) {
  return file.name.toLowerCase().endsWith(".zip") || file.type === "application/zip"
}

function requireArchiveSize(size: number) {
  if (size <= 0 || size > MAX_SKILL_ARCHIVE_BYTES) {
    throw new Error("Skill package must be a ZIP no larger than 20 MiB.")
  }
}

function requireSafePath(path: string) {
  if (
    !path ||
    path.length > 1024 ||
    path.startsWith("/") ||
    path.endsWith("/") ||
    path.includes("\\") ||
    path.includes("\0") ||
    path.includes(":") ||
    path.split("/").some((segment) => !segment || segment === "." || segment === "..")
  ) {
    throw new Error("Skill package contains an unsafe relative path.")
  }
}
