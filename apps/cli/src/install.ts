import { Buffer } from "node:buffer"
import { randomBytes } from "node:crypto"
import { mkdir, readFile, rename, rm, writeFile } from "node:fs/promises"
import { homedir } from "node:os"
import { dirname, join, relative, resolve, sep } from "node:path"
import { Unzip, UnzipInflate } from "fflate"
import { z } from "zod"

import {
  orgMemoryUuidSchema,
  type SkillManifestLink,
} from "./contracts.js"
import {
  atomicWriteJson,
  isENOENT,
  requireSafeRelativePath,
  sha256,
} from "./shared.js"

export type Agent = "claude-code" | "codex"

type PromotionFileOperations = {
  rename: typeof rename
  rm: typeof rm
}

const promotionFileOperations: PromotionFileOperations = { rename, rm }

const agentDirectories: Record<Agent, string> = {
  "claude-code": ".claude/skills",
  codex: ".agents/skills",
}

const MAXIMUM_UNCOMPRESSED_PACKAGE_BYTES = 50 * 1024 * 1024

const receiptSchema = z.object({
  schemaVersion: z.literal(1),
  skills: z.record(
    z.string(),
    z.object({
      coordinate: z.string(),
      version: z.string(),
      assetId: orgMemoryUuidSchema,
      releaseId: orgMemoryUuidSchema,
      releaseDigest: z.string(),
      packageDigest: z.string(),
      agent: z.union([z.literal("claude-code"), z.literal("codex")]),
      target: z.string(),
      installedAt: z.iso.datetime(),
    }),
  ),
})

type LockFile = z.infer<typeof receiptSchema>

export async function installSkill(input: {
  manifestLink: SkillManifestLink
  packageBytes: Uint8Array
  agent: Agent
  global: boolean
  cwd: string
  promotionOperations?: PromotionFileOperations
}): Promise<{ target: string }> {
  const { manifest } = input.manifestLink
  verifyPackage(input.packageBytes, manifest.packageDigest, manifest.packageLength)
  const files = extractAndVerify(input.packageBytes, manifest)
  const base = input.global ? homedir() : resolve(input.cwd)
  const skillsDirectory = resolve(base, agentDirectories[input.agent])
  const target = resolve(skillsDirectory, manifest.slug)
  requireInside(skillsDirectory, target)
  await mkdir(skillsDirectory, { recursive: true })

  const staging = resolve(
    skillsDirectory,
    `.${manifest.slug}.orgmemory-${process.pid}-${randomBytes(6).toString("hex")}`,
  )
  const backup = `${target}.orgmemory-backup-${process.pid}-${randomBytes(6).toString("hex")}`
  requireInside(skillsDirectory, staging)
  requireInside(skillsDirectory, backup)
  await rm(staging, { recursive: true, force: true })
  let backupOwned = false
  try {
    for (const [path, bytes] of files) {
      const destination = resolve(staging, ...path.split("/"))
      requireInside(staging, destination)
      await mkdir(dirname(destination), { recursive: true })
      await writeFile(destination, bytes, { flag: "wx" })
    }
    try {
      backupOwned = await promote(
        staging,
        target,
        backup,
        input.promotionOperations ?? promotionFileOperations,
      )
    } catch (promotionFailure) {
      if (promotionFailure instanceof SkillPromotionRecoveryError) {
        backupOwned = true
      }
      throw promotionFailure
    }
    try {
      await writeReceipt({
        global: input.global,
        cwd: input.cwd,
        key: `${input.agent}:${manifest.coordinate}`,
        entry: {
          coordinate: manifest.coordinate,
          version: manifest.version,
          assetId: manifest.assetId,
          releaseId: manifest.releaseId,
          releaseDigest: manifest.releaseDigest,
          packageDigest: manifest.packageDigest,
          agent: input.agent,
          target: relative(base, target).replaceAll("\\", "/"),
          installedAt: new Date().toISOString(),
        },
      })
    } catch (receiptError) {
      try {
        await rm(target, { recursive: true, force: true })
        if (backupOwned) {
          await rename(backup, target)
          backupOwned = false
        }
      } catch (rollbackError) {
        throw new AggregateError(
          [receiptError, rollbackError],
          `The Skill receipt failed and installation rollback requires recovery from ${backup}`,
        )
      }
      throw receiptError
    }
    if (backupOwned) {
      await rm(backup, { recursive: true, force: true })
      backupOwned = false
    }
    return { target }
  } finally {
    await rm(staging, { recursive: true, force: true })
    if (!backupOwned) {
      await rm(backup, { recursive: true, force: true })
    }
  }
}

export async function listInstalled(input: {
  global: boolean
  cwd: string
}): Promise<LockFile["skills"]> {
  const path = lockPath(input.global, input.cwd)
  try {
    return receiptSchema.parse(JSON.parse(await readFile(path, "utf8"))).skills
  } catch (error) {
    if (isENOENT(error)) {
      return {}
    }
    throw new Error(`OrgMemory Skill lock is unreadable at ${path}`, { cause: error })
  }
}

export async function readBoundedPackage(
  response: Response,
  maximumBytes = 20 * 1024 * 1024,
): Promise<Uint8Array> {
  if (!response.ok) {
    throw new Error(
      response.status === 401 || response.status === 403 || response.status === 404
        ? "The Skill package is not available to this identity"
        : "OrgMemory Skill package delivery is temporarily unavailable",
    )
  }
  const declared = Number(response.headers.get("content-length"))
  if (Number.isFinite(declared) && declared > maximumBytes) {
    throw new Error("The Skill package exceeds the 20 MiB client limit")
  }
  if (!response.body) throw new Error("OrgMemory returned an empty Skill package")
  const reader = response.body.getReader()
  const chunks: Uint8Array[] = []
  let length = 0
  while (true) {
    const result = await reader.read()
    if (result.done) break
    length += result.value.byteLength
    if (length > maximumBytes) {
      await reader.cancel()
      throw new Error("The Skill package exceeds the 20 MiB client limit")
    }
    chunks.push(result.value)
  }
  return Buffer.concat(chunks, length)
}

function verifyPackage(bytes: Uint8Array, expectedDigest: string, expectedLength: number): void {
  if (bytes.byteLength !== expectedLength || sha256(bytes) !== expectedDigest) {
    throw new Error("The downloaded Skill package failed its release integrity check")
  }
}

function extractAndVerify(
  bytes: Uint8Array,
  manifest: SkillManifestLink["manifest"],
): Map<string, Uint8Array> {
  const declaredSize = manifest.files.reduce((total, file) => total + file.size, 0)
  if (
    !Number.isSafeInteger(declaredSize) ||
    declaredSize > MAXIMUM_UNCOMPRESSED_PACKAGE_BYTES
  ) {
    throw new Error("The released Skill package exceeds the 50 MiB extraction limit")
  }
  const archiveFiles = [...streamArchive(bytes)]
  const skillMarkdown = archiveFiles
    .map(([path]) => path)
    .filter((path) => path === "SKILL.md" || path.endsWith("/SKILL.md"))
  if (skillMarkdown.length !== 1) {
    throw new Error("The released Skill package does not contain one SKILL.md")
  }
  const root =
    skillMarkdown[0] === "SKILL.md"
      ? ""
      : `${skillMarkdown[0]?.slice(0, -"/SKILL.md".length)}/`
  const extracted = new Map<string, Uint8Array>()
  for (const [archivePath, content] of archiveFiles) {
    if (!archivePath.startsWith(root)) {
      throw new Error("The released Skill package contains files outside its Skill root")
    }
    const path = archivePath.slice(root.length)
    // Root-stripping of unique safe paths cannot introduce traversal; re-check as defense-in-depth.
    requireSafeRelativePath(path)
    if (extracted.has(path)) {
      throw new Error("The released Skill package contains duplicate paths")
    }
    extracted.set(path, content)
  }
  if (extracted.size !== manifest.files.length) {
    throw new Error("The released Skill package file set does not match its manifest")
  }
  for (const file of manifest.files) {
    requireSafeRelativePath(file.path)
    const content = extracted.get(file.path)
    if (
      !content ||
      content.byteLength !== file.size ||
      sha256(content) !== file.sha256
    ) {
      throw new Error(`The released Skill file failed integrity verification: ${file.path}`)
    }
  }
  return extracted
}

function streamArchive(bytes: Uint8Array): Map<string, Uint8Array> {
  const extracted = new Map<string, Uint8Array>()
  const seen = new Set<string>()
  const pending = new Set<string>()
  let knownUncompressedSize = 0
  let actualUncompressedSize = 0
  let extractionFailure: Error | undefined
  const unzip = new Unzip((file) => {
    const archivePath = file.name
    const relativePath = archivePath.endsWith("/")
      ? archivePath.slice(0, -1)
      : archivePath
    try {
      requireSafeRelativePath(relativePath)
    } catch (error) {
      extractionFailure =
        error instanceof Error ? error : new Error("Unsafe Skill package path")
      return
    }
    if (archivePath.endsWith("/")) return
    if (seen.has(archivePath)) {
      extractionFailure = new Error(
        "The released Skill package contains duplicate paths",
      )
      return
    }
    seen.add(archivePath)
    if (file.originalSize !== undefined) {
      knownUncompressedSize += file.originalSize
      if (
        !Number.isSafeInteger(knownUncompressedSize) ||
        knownUncompressedSize > MAXIMUM_UNCOMPRESSED_PACKAGE_BYTES
      ) {
        extractionFailure = new Error(
          "The released Skill package exceeds the 50 MiB extraction limit",
        )
        return
      }
    }
    const chunks: Uint8Array[] = []
    let fileSize = 0
    pending.add(archivePath)
    file.ondata = (error, chunk, final) => {
      if (extractionFailure) {
        file.terminate()
        pending.delete(archivePath)
        return
      }
      if (error) {
        extractionFailure = new Error(
          "The released Skill package is not a readable ZIP",
          { cause: error },
        )
        pending.delete(archivePath)
        return
      }
      if (chunk) {
        actualUncompressedSize += chunk.byteLength
        fileSize += chunk.byteLength
        if (
          actualUncompressedSize > MAXIMUM_UNCOMPRESSED_PACKAGE_BYTES ||
          !Number.isSafeInteger(actualUncompressedSize)
        ) {
          extractionFailure = new Error(
            "The released Skill package exceeds the 50 MiB extraction limit",
          )
          file.terminate()
          pending.delete(archivePath)
          return
        }
        chunks.push(chunk)
      }
      if (final) {
        extracted.set(archivePath, Buffer.concat(chunks, fileSize))
        pending.delete(archivePath)
      }
    }
    try {
      file.start()
    } catch (error) {
      extractionFailure = new Error(
        "The released Skill package is not a readable ZIP",
        { cause: error },
      )
      pending.delete(archivePath)
    }
  })
  unzip.register(UnzipInflate)
  try {
    unzip.push(bytes, true)
  } catch (error) {
    throw new Error("The released Skill package is not a readable ZIP", {
      cause: extractionFailure ?? error,
    })
  }
  if (extractionFailure) throw extractionFailure
  if (pending.size > 0) {
    throw new Error("The released Skill package is not a readable ZIP")
  }
  return extracted
}

async function promote(
  staging: string,
  target: string,
  backup: string,
  operations: PromotionFileOperations,
): Promise<boolean> {
  let backedUp = false
  try {
    try {
      await operations.rename(target, backup)
      backedUp = true
    } catch (error) {
      if (!isENOENT(error)) {
        throw error
      }
    }
    await operations.rename(staging, target)
    return backedUp
  } catch (error) {
    if (backedUp) {
      try {
        await operations.rm(target, { recursive: true, force: true })
        await operations.rename(backup, target)
      } catch (rollbackError) {
        throw new SkillPromotionRecoveryError(
          [error, rollbackError],
          `Skill promotion failed and rollback requires recovery from ${backup}`,
        )
      }
    }
    throw error
  }
}

class SkillPromotionRecoveryError extends AggregateError {}

async function writeReceipt(input: {
  global: boolean
  cwd: string
  key: string
  entry: LockFile["skills"][string]
}): Promise<void> {
  const path = lockPath(input.global, input.cwd)
  const existing = await listInstalled(input)
  const lock: LockFile = {
    schemaVersion: 1,
    skills: { ...existing, [input.key]: input.entry },
  }
  await mkdir(dirname(path), { recursive: true })
  await atomicWriteJson(path, lock)
}

function lockPath(global: boolean, cwd: string): string {
  const base = global ? join(homedir(), ".orgmemory") : join(resolve(cwd), ".orgmemory")
  return join(base, "skills.lock.json")
}

function requireInside(base: string, target: string): void {
  const normalizedBase = resolve(base)
  const normalizedTarget = resolve(target)
  if (normalizedTarget !== normalizedBase && !normalizedTarget.startsWith(`${normalizedBase}${sep}`)) {
    throw new Error("The Skill install path escapes its agent directory")
  }
}
