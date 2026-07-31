import { lstat, readdir, readFile, realpath } from "node:fs/promises"
import { basename, relative, resolve, sep } from "node:path"

import { zipSync, type Zippable } from "fflate"
import { parseDocument } from "yaml"

import { requireSafeRelativePath, sha256 } from "./shared.js"

const MAX_ARCHIVE_BYTES = 20 * 1024 * 1024
const MAX_UNPACKED_BYTES = 50 * 1024 * 1024
const MAX_FILES = 300
const MAX_SKILL_MD_BYTES = 512 * 1024
// fflate writes DOS local-time fields, so construct the fixed value in local
// time to keep the encoded fields identical across host time zones.
const FIXED_ZIP_TIME = new Date(2000, 0, 1, 0, 0, 0, 0)
const FRONTMATTER_FIELDS = new Set([
  "name",
  "description",
  "license",
  "compatibility",
  "metadata",
  "allowed-tools",
])
const SKILL_NAME = /^[a-z0-9]+(?:-[a-z0-9]+)*$/

export type LocalSkillPackage = {
  folder: string
  name: string
  description: string
  fileCount: number
  contentBytes: number
  archiveBytes: Uint8Array<ArrayBuffer>
  packageDigest: string
  files: Array<{ path: string; size: number; sha256: string }>
}

type SkillMetadata = {
  name: string
  description: string
}

type CollectedFile = {
  path: string
  bytes: Uint8Array
  sha256: string
}

export async function buildLocalSkillPackage(
  folderInput: string,
): Promise<LocalSkillPackage> {
  const folder = resolve(folderInput)
  const folderInfo = await lstat(folder).catch(() => null)
  if (!folderInfo?.isDirectory()) {
    throw new Error("Skill path must be a directory")
  }
  if (folderInfo.isSymbolicLink()) {
    throw new Error("Skill path must not be a symbolic link")
  }

  const canonicalFolder = await realpath(folder)
  const files = await collectFiles(canonicalFolder)
  const skillMarkdown = files.find((file) => file.path === "SKILL.md")
  if (!skillMarkdown) {
    throw new Error("Skill folder requires root SKILL.md")
  }
  if (skillMarkdown.bytes.byteLength > MAX_SKILL_MD_BYTES) {
    throw new Error("SKILL.md exceeds 512 KiB")
  }
  const metadata = parseSkillMarkdown(skillMarkdown.bytes)
  if (basename(canonicalFolder) !== metadata.name) {
    throw new Error("Skill name must match its folder name")
  }

  const archiveInput = Object.create(null) as Zippable
  for (const file of files) {
    archiveInput[file.path] = [
      file.bytes,
      {
        attrs: 0o644 << 16,
        level: 6,
        mtime: FIXED_ZIP_TIME,
        os: 3,
      },
    ]
  }
  const archiveBytes = zipSync(archiveInput, {
    level: 6,
    mtime: FIXED_ZIP_TIME,
  }) as Uint8Array<ArrayBuffer>
  if (archiveBytes.byteLength > MAX_ARCHIVE_BYTES) {
    throw new Error("Skill package exceeds 20 MiB compressed")
  }

  return {
    folder: canonicalFolder,
    name: metadata.name,
    description: metadata.description,
    fileCount: files.length,
    contentBytes: files.reduce((total, file) => total + file.bytes.byteLength, 0),
    archiveBytes,
    packageDigest: sha256(archiveBytes),
    files: files.map(({ path, bytes, sha256: digest }) => ({
      path,
      size: bytes.byteLength,
      sha256: digest,
    })),
  }
}

async function collectFiles(folder: string): Promise<CollectedFile[]> {
  const collected: CollectedFile[] = []
  const collisionKeys = new Set<string>()
  let totalBytes = 0

  async function visit(directory: string): Promise<void> {
    const entries = await readdir(directory, { withFileTypes: true })
    entries.sort((left, right) => comparePaths(left.name, right.name))
    for (const entry of entries) {
      if (entry.name === ".git") continue
      const absolute = resolve(directory, entry.name)
      const info = await lstat(absolute)
      if (info.isSymbolicLink()) {
        throw new Error(`Skill package must not contain symbolic links: ${entry.name}`)
      }
      if (info.isDirectory()) {
        await visit(absolute)
        continue
      }
      if (!info.isFile()) {
        throw new Error(`Skill package contains an unsupported filesystem entry: ${entry.name}`)
      }
      const path = relative(folder, absolute).split(sep).join("/")
      requireSafeRelativePath(path)
      const collisionKey = path.toLowerCase()
      if (!collisionKeys.add(collisionKey)) {
        throw new Error("Skill package contains duplicate or case-colliding paths")
      }
      if (collected.length >= MAX_FILES) {
        throw new Error("Skill package contains more than 300 files")
      }
      const bytes = new Uint8Array(await readFile(absolute))
      totalBytes += bytes.byteLength
      if (totalBytes > MAX_UNPACKED_BYTES) {
        throw new Error("Skill package exceeds 50 MiB of content")
      }
      collected.push({ path, bytes, sha256: sha256(bytes) })
    }
  }

  await visit(folder)
  if (collected.length === 0) {
    throw new Error("Skill package contains no files")
  }
  collected.sort((left, right) => comparePaths(left.path, right.path))
  return collected
}

function parseSkillMarkdown(bytes: Uint8Array): SkillMetadata {
  const text = new TextDecoder("utf-8", { fatal: true }).decode(bytes)
  const normalized = text.replaceAll("\r\n", "\n").replaceAll("\r", "\n")
  if (!normalized.startsWith("---\n")) {
    throw new Error("SKILL.md requires YAML frontmatter")
  }
  const closing = normalized.indexOf("\n---\n", 4)
  if (closing < 0) {
    throw new Error("SKILL.md frontmatter is not closed")
  }
  const document = parseDocument(normalized.slice(4, closing), {
    schema: "core",
    uniqueKeys: true,
  })
  if (document.errors.length > 0) {
    throw new Error(`SKILL.md frontmatter is invalid YAML: ${document.errors[0]?.message}`)
  }
  const frontmatter = document.toJS({ maxAliasCount: 0 }) as unknown
  if (
    !frontmatter ||
    typeof frontmatter !== "object" ||
    Array.isArray(frontmatter)
  ) {
    throw new Error("SKILL.md frontmatter must be a mapping")
  }
  const fields = frontmatter as Record<string, unknown>
  if (Object.keys(fields).some((key) => !FRONTMATTER_FIELDS.has(key))) {
    throw new Error("SKILL.md frontmatter contains unsupported fields")
  }
  const name = requiredString(fields, "name", 64)
  if (!SKILL_NAME.test(name)) {
    throw new Error("Skill name must use lowercase letters, digits, and single hyphens")
  }
  validateOptionalString(fields, "license", 1024)
  validateOptionalString(fields, "compatibility", 500)
  validateOptionalString(fields, "allowed-tools", 2048)
  validateMetadata(fields.metadata)
  return {
    name,
    description: requiredString(fields, "description", 1024),
  }
}

function requiredString(
  source: Record<string, unknown>,
  key: string,
  maximumLength: number,
): string {
  const value = source[key]
  if (typeof value !== "string") {
    throw new Error(`SKILL.md ${key} must be a string`)
  }
  const normalized = value.trim()
  if (!normalized || normalized.length > maximumLength) {
    throw new Error(`SKILL.md ${key} is blank or exceeds its limit`)
  }
  return normalized
}

function validateOptionalString(
  source: Record<string, unknown>,
  key: string,
  maximumLength: number,
): void {
  if (!(key in source)) return
  requiredString(source, key, maximumLength)
}

function validateMetadata(value: unknown): void {
  if (value === undefined || value === null) return
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error("Skill metadata must be a bounded mapping")
  }
  const entries = Object.entries(value)
  if (entries.length > 100) {
    throw new Error("Skill metadata must be a bounded mapping")
  }
  for (const [key, item] of entries) {
    if (
      !key.trim() ||
      key.length > 256 ||
      !["string", "number", "boolean"].includes(typeof item) ||
      String(item).length > 2048
    ) {
      throw new Error("Skill metadata must contain bounded scalar entries")
    }
  }
}

function comparePaths(left: string, right: string): number {
  return left < right ? -1 : left > right ? 1 : 0
}
