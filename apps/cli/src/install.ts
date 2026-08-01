import { Buffer } from "node:buffer"
import { randomBytes } from "node:crypto"
import {
  lstat,
  mkdir,
  readFile,
  readdir,
  rename,
  rm,
  writeFile,
} from "node:fs/promises"
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

const receiptEntrySchema = z.object({
  coordinate: z.string(),
  version: z.string(),
  assetId: orgMemoryUuidSchema,
  releaseId: orgMemoryUuidSchema,
  releaseDigest: z.string(),
  packageDigest: z.string(),
  agent: z.union([z.literal("claude-code"), z.literal("codex")]),
  target: z.string(),
  installedAt: z.iso.datetime(),
})

const receiptFileSchema = z.object({
  path: z.string(),
  size: z.number().int().nonnegative(),
  sha256: z.string(),
})

const legacyReceiptEntrySchema = receiptEntrySchema.extend({
  receiptVersion: z.literal(1),
})

const verifiedReceiptEntrySchema = receiptEntrySchema.extend({
  receiptVersion: z.literal(2),
  files: z.array(receiptFileSchema),
})

const installedReceiptEntrySchema = z.discriminatedUnion("receiptVersion", [
  legacyReceiptEntrySchema,
  verifiedReceiptEntrySchema,
])

const legacyReceiptSchema = z.object({
  schemaVersion: z.literal(1),
  skills: z.record(z.string(), receiptEntrySchema),
})

const receiptSchema = z.object({
  schemaVersion: z.literal(2),
  skills: z.record(z.string(), installedReceiptEntrySchema),
})

type InstalledReceiptEntry = z.infer<typeof installedReceiptEntrySchema>
type VerifiedReceiptEntry = z.infer<typeof verifiedReceiptEntrySchema>
type LockFile = z.infer<typeof receiptSchema>
type VerificationStatus = "verified" | "modified" | "missing" | "unverifiable"

type OperationJournal = {
  schemaVersion: 1
  operation: "install" | "update" | "remove"
  phase: "prepared" | "tree-promoted" | "receipt-committed"
  key: string
  target: string
  staging?: string
  recovery: string
  previousEntry?: InstalledReceiptEntry
  nextEntry?: InstalledReceiptEntry
  startedAt: string
}

const operationJournalSchema: z.ZodType<OperationJournal> = z.object({
  schemaVersion: z.literal(1),
  operation: z.union([
    z.literal("install"),
    z.literal("update"),
    z.literal("remove"),
  ]),
  phase: z.union([
    z.literal("prepared"),
    z.literal("tree-promoted"),
    z.literal("receipt-committed"),
  ]),
  key: z.string(),
  target: z.string(),
  staging: z.string().optional(),
  recovery: z.string(),
  previousEntry: installedReceiptEntrySchema.optional(),
  nextEntry: installedReceiptEntrySchema.optional(),
  startedAt: z.iso.datetime(),
})

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
  return withScopeLock(input, async () => {
    const lock = await readReceipt(input)
    const key = receiptKey(input.agent, manifest.coordinate)
    const target = canonicalTarget(input, input.agent, manifest.coordinate)
    assertTargetAvailable(lock, key, target.relative)
    if (lock.skills[key]) {
      throw new Error(`${manifest.coordinate} is already installed; use skill update`)
    }
    if (await pathExists(target.absolute)) {
      throw new Error(
        `The Skill target ${target.absolute} already exists without an OrgMemory receipt`,
      )
    }
    const entry = verifiedEntry(input, target.relative)
    await replaceTreeTransaction({
      scope: input,
      operation: "install",
      key,
      lock,
      previousEntry: undefined,
      nextEntry: entry,
      files,
      target: target.absolute,
      promotionOperations: input.promotionOperations ?? promotionFileOperations,
    })
    return { target: target.absolute }
  })
}

export async function listInstalled(input: {
  global: boolean
  cwd: string
}): Promise<LockFile["skills"]> {
  return withScopeLock(input, async () => (await readReceipt(input)).skills)
}

export async function verifyInstalled(input: {
  global: boolean
  cwd: string
  coordinate?: string
  agent?: Agent
}): Promise<
  Record<
    string,
    InstalledReceiptEntry & { status: VerificationStatus; reason?: string }
  >
> {
  return withScopeLock(input, async () => {
    const lock = await readReceipt(input)
    const result: Record<
      string,
      InstalledReceiptEntry & { status: VerificationStatus; reason?: string }
    > = {}
    for (const [key, entry] of Object.entries(lock.skills)) {
      if (input.coordinate && entry.coordinate !== input.coordinate) continue
      if (input.agent && entry.agent !== input.agent) continue
      result[key] = { ...entry, ...(await verifyEntry(input, entry)) }
    }
    return result
  })
}

export async function updateInstalledSkill(input: {
  coordinate: string
  agent: Agent
  global: boolean
  cwd: string
  loadRelease: () => Promise<{
    manifestLink: SkillManifestLink
    packageBytes: Uint8Array
  }>
  promotionOperations?: PromotionFileOperations
}): Promise<{ target: string; version: string }> {
  return withScopeLock(input, async () => {
    const lock = await readReceipt(input)
    const key = receiptKey(input.agent, input.coordinate)
    const previousEntry = lock.skills[key]
    if (!previousEntry) throw new Error(`${input.coordinate} is not installed for ${input.agent}`)
    const verification = await verifyEntry(input, previousEntry)
    if (verification.status !== "verified") {
      throw new Error(
        `${input.coordinate} is ${verification.status}; update requires a verified v2 installation`,
      )
    }
    const release = await input.loadRelease()
    const { manifest } = release.manifestLink
    if (manifest.coordinate !== input.coordinate) {
      throw new Error("Skill update cannot change the installed coordinate")
    }
    verifyPackage(
      release.packageBytes,
      manifest.packageDigest,
      manifest.packageLength,
    )
    const files = extractAndVerify(release.packageBytes, manifest)
    const target = canonicalTarget(input, input.agent, input.coordinate)
    assertTargetAvailable(lock, key, target.relative)
    const nextEntry = verifiedEntry(
      {
        ...input,
        manifestLink: release.manifestLink,
      },
      target.relative,
    )
    await replaceTreeTransaction({
      scope: input,
      operation: "update",
      key,
      lock,
      previousEntry,
      nextEntry,
      files,
      target: target.absolute,
      promotionOperations: input.promotionOperations ?? promotionFileOperations,
    })
    return { target: target.absolute, version: manifest.version }
  })
}

export async function removeInstalledSkill(input: {
  coordinate: string
  agent: Agent
  global: boolean
  cwd: string
}): Promise<void> {
  await withScopeLock(input, async () => {
    const lock = await readReceipt(input)
    const key = receiptKey(input.agent, input.coordinate)
    const previousEntry = lock.skills[key]
    if (!previousEntry) throw new Error(`${input.coordinate} is not installed for ${input.agent}`)
    const verification = await verifyEntry(input, previousEntry)
    if (verification.status !== "verified") {
      throw new Error(
        `${input.coordinate} is ${verification.status}; remove requires a verified v2 installation`,
      )
    }
    const target = canonicalTarget(input, input.agent, input.coordinate)
    assertTargetAvailable(lock, key, target.relative)
    const quarantine = `${target.absolute}.orgmemory-quarantine-${process.pid}-${randomBytes(6).toString("hex")}`
    requireInside(target.directory, quarantine)
    const journal: OperationJournal = {
      schemaVersion: 1,
      operation: "remove",
      phase: "prepared",
      key,
      target: target.relative,
      recovery: relative(scopeBase(input), quarantine).replaceAll("\\", "/"),
      previousEntry,
      startedAt: new Date().toISOString(),
    }
    await writeJournal(input, journal)
    try {
      await rename(target.absolute, quarantine)
      await writeJournal(input, { ...journal, phase: "tree-promoted" })
      const nextLock = { ...lock, skills: { ...lock.skills } }
      delete nextLock.skills[key]
      await persistReceipt(input, nextLock)
      await writeJournal(input, { ...journal, phase: "receipt-committed" })
      await rm(quarantine, { recursive: true, force: true })
      await rm(journalPath(input), { force: true })
    } catch (error) {
      try {
        await recoverOperation(input)
      } catch (recoveryError) {
        throw new AggregateError(
          [error, recoveryError],
          `Skill removal failed and recovery remains blocked by ${journalPath(input)}`,
        )
      }
      throw error
    }
  })
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

async function replaceTreeTransaction(input: {
  scope: { global: boolean; cwd: string }
  operation: "install" | "update"
  key: string
  lock: LockFile
  previousEntry: InstalledReceiptEntry | undefined
  nextEntry: VerifiedReceiptEntry
  files: Map<string, Uint8Array>
  target: string
  promotionOperations: PromotionFileOperations
}): Promise<void> {
  const targetDirectory = dirname(input.target)
  await mkdir(targetDirectory, { recursive: true })
  const nonce = `${process.pid}-${randomBytes(6).toString("hex")}`
  const slug = input.nextEntry.coordinate.split("/")[1]
  const staging = resolve(targetDirectory, `.${slug}.orgmemory-${nonce}`)
  const backup = `${input.target}.orgmemory-backup-${nonce}`
  requireInside(targetDirectory, staging)
  requireInside(targetDirectory, backup)
  const journal: OperationJournal = {
    schemaVersion: 1,
    operation: input.operation,
    phase: "prepared",
    key: input.key,
    target: input.nextEntry.target,
    staging: relative(scopeBase(input.scope), staging).replaceAll("\\", "/"),
    recovery: relative(scopeBase(input.scope), backup).replaceAll("\\", "/"),
    ...(input.previousEntry ? { previousEntry: input.previousEntry } : {}),
    nextEntry: input.nextEntry,
    startedAt: new Date().toISOString(),
  }
  await rm(staging, { recursive: true, force: true })
  await writeJournal(input.scope, journal)
  try {
    for (const [path, bytes] of input.files) {
      const destination = resolve(staging, ...path.split("/"))
      requireInside(staging, destination)
      await mkdir(dirname(destination), { recursive: true })
      await writeFile(destination, bytes, { flag: "wx" })
    }
    await promote(
      staging,
      input.target,
      backup,
      input.promotionOperations,
    )
    await writeJournal(input.scope, { ...journal, phase: "tree-promoted" })
    await persistReceipt(input.scope, {
      schemaVersion: 2,
      skills: { ...input.lock.skills, [input.key]: input.nextEntry },
    })
    await writeJournal(input.scope, { ...journal, phase: "receipt-committed" })
    await rm(backup, { recursive: true, force: true })
    await rm(journalPath(input.scope), { force: true })
  } catch (error) {
    try {
      await recoverOperation(input.scope)
    } catch (recoveryError) {
      throw new AggregateError(
        [error, recoveryError],
        `Skill ${input.operation} failed and recovery remains blocked by ${journalPath(input.scope)}`,
      )
    }
    throw error
  } finally {
    await rm(staging, { recursive: true, force: true })
  }
}

function verifiedEntry(
  input: {
    manifestLink: SkillManifestLink
    agent: Agent
  },
  target: string,
): VerifiedReceiptEntry {
  const { manifest } = input.manifestLink
  return {
    receiptVersion: 2,
    coordinate: manifest.coordinate,
    version: manifest.version,
    assetId: manifest.assetId,
    releaseId: manifest.releaseId,
    releaseDigest: manifest.releaseDigest,
    packageDigest: manifest.packageDigest,
    agent: input.agent,
    target,
    installedAt: new Date().toISOString(),
    files: [...manifest.files]
      .sort((left, right) => left.path.localeCompare(right.path))
      .map((file) => ({
        path: file.path,
        size: file.size,
        sha256: file.sha256,
      })),
  }
}

async function verifyEntry(
  scope: { global: boolean; cwd: string },
  entry: InstalledReceiptEntry,
): Promise<{ status: VerificationStatus; reason?: string }> {
  if (entry.receiptVersion === 1) {
    return { status: "unverifiable", reason: "schema-v1 receipt has no file manifest" }
  }
  let target: ReturnType<typeof canonicalTarget>
  try {
    target = canonicalTarget(scope, entry.agent, entry.coordinate)
  } catch {
    return { status: "unverifiable", reason: "receipt coordinate is invalid" }
  }
  if (entry.target !== target.relative) {
    return { status: "modified", reason: "receipt target does not match its canonical consumer target" }
  }
  let targetStat
  try {
    targetStat = await lstat(target.absolute)
  } catch (error) {
    if (isENOENT(error)) return { status: "missing", reason: "installed tree is missing" }
    throw error
  }
  if (!targetStat.isDirectory() || targetStat.isSymbolicLink()) {
    return { status: "modified", reason: "installed target is not a regular directory" }
  }
  const actual = await readInstalledTree(target.absolute)
  if (actual.invalidEntry) {
    return { status: "modified", reason: actual.invalidEntry }
  }
  const expectedDirectories = new Set<string>()
  for (const file of entry.files) {
    const parts = file.path.split("/")
    for (let index = 1; index < parts.length; index += 1) {
      expectedDirectories.add(parts.slice(0, index).join("/"))
    }
  }
  if (
    actual.files.size !== entry.files.length ||
    actual.directories.size !== expectedDirectories.size ||
    [...actual.directories].some((path) => !expectedDirectories.has(path))
  ) {
    return { status: "modified", reason: "installed tree contains missing or extra entries" }
  }
  for (const expected of entry.files) {
    const found = actual.files.get(expected.path)
    if (
      !found ||
      found.size !== expected.size ||
      found.sha256 !== expected.sha256
    ) {
      return { status: "modified", reason: `installed file changed: ${expected.path}` }
    }
  }
  return { status: "verified" }
}

async function readInstalledTree(root: string): Promise<{
  files: Map<string, { size: number; sha256: string }>
  directories: Set<string>
  invalidEntry?: string
}> {
  const files = new Map<string, { size: number; sha256: string }>()
  const directories = new Set<string>()
  let invalidEntry: string | undefined
  const visit = async (directory: string, prefix: string): Promise<void> => {
    const entries = await readdir(directory)
    entries.sort((left, right) => left.localeCompare(right))
    for (const name of entries) {
      const absolute = join(directory, name)
      const path = prefix ? `${prefix}/${name}` : name
      const details = await lstat(absolute)
      if (details.isSymbolicLink()) {
        invalidEntry ??= `installed tree contains a link: ${path}`
      } else if (details.isDirectory()) {
        directories.add(path)
        await visit(absolute, path)
      } else if (details.isFile()) {
        const bytes = await readFile(absolute)
        files.set(path, { size: bytes.byteLength, sha256: sha256(bytes) })
      } else {
        invalidEntry ??= `installed tree contains a non-regular entry: ${path}`
      }
    }
  }
  await visit(root, "")
  return {
    files,
    directories,
    ...(invalidEntry ? { invalidEntry } : {}),
  }
}

async function withScopeLock<T>(
  input: { global: boolean; cwd: string },
  action: () => Promise<T>,
): Promise<T> {
  const mutex = mutexPath(input)
  await mkdir(dirname(mutex), { recursive: true })
  await acquireMutex(mutex)
  try {
    await recoverOperation(input)
    return await action()
  } finally {
    await rm(mutex, { recursive: true, force: true })
  }
}

async function acquireMutex(path: string): Promise<void> {
  const deadline = Date.now() + 15_000
  while (true) {
    try {
      await mkdir(path)
      try {
        await atomicWriteJson(join(path, "owner.json"), {
          pid: process.pid,
          createdAt: new Date().toISOString(),
        })
      } catch (error) {
        await rm(path, { recursive: true, force: true })
        throw error
      }
      return
    } catch (error) {
      if (!hasErrorCode(error, "EEXIST")) throw error
      if (await mutexIsAbandoned(path)) {
        await rm(path, { recursive: true, force: true })
        continue
      }
      if (Date.now() >= deadline) {
        throw new Error("Another OrgMemory Skill lifecycle operation is active in this scope")
      }
      await new Promise((resolvePromise) => setTimeout(resolvePromise, 25))
    }
  }
}

async function mutexIsAbandoned(path: string): Promise<boolean> {
  try {
    const owner = z
      .object({ pid: z.number().int().positive(), createdAt: z.iso.datetime() })
      .parse(JSON.parse(await readFile(join(path, "owner.json"), "utf8")))
    try {
      process.kill(owner.pid, 0)
      return false
    } catch {
      return true
    }
  } catch {
    try {
      return Date.now() - (await lstat(path)).mtimeMs > 10_000
    } catch (error) {
      return isENOENT(error)
    }
  }
}

async function readReceipt(input: {
  global: boolean
  cwd: string
}): Promise<LockFile> {
  const path = lockPath(input)
  try {
    const raw: unknown = JSON.parse(await readFile(path, "utf8"))
    const version = z.object({ schemaVersion: z.number() }).parse(raw).schemaVersion
    if (version === 1) {
      const legacy = legacyReceiptSchema.parse(raw)
      return {
        schemaVersion: 2,
        skills: Object.fromEntries(
          Object.entries(legacy.skills).map(([key, entry]) => [
            key,
            { ...entry, receiptVersion: 1 as const },
          ]),
        ),
      }
    }
    return receiptSchema.parse(raw)
  } catch (error) {
    if (isENOENT(error)) return { schemaVersion: 2, skills: {} }
    throw new Error(`OrgMemory Skill lock is unreadable at ${path}`, { cause: error })
  }
}

async function persistReceipt(
  input: { global: boolean; cwd: string },
  lock: LockFile,
): Promise<void> {
  const path = lockPath(input)
  await mkdir(dirname(path), { recursive: true })
  await atomicWriteJson(path, receiptSchema.parse(lock))
}

async function writeJournal(
  input: { global: boolean; cwd: string },
  journal: OperationJournal,
): Promise<void> {
  const path = journalPath(input)
  await mkdir(dirname(path), { recursive: true })
  await atomicWriteJson(path, operationJournalSchema.parse(journal))
}

async function recoverOperation(input: {
  global: boolean
  cwd: string
}): Promise<void> {
  let journal: OperationJournal
  try {
    journal = operationJournalSchema.parse(
      JSON.parse(await readFile(journalPath(input), "utf8")),
    )
  } catch (error) {
    if (isENOENT(error)) return
    throw new Error(`OrgMemory Skill recovery journal is unreadable at ${journalPath(input)}`, {
      cause: error,
    })
  }
  const identity = journal.nextEntry ?? journal.previousEntry
  if (!identity) throw new Error("OrgMemory Skill recovery journal has no Skill identity")
  const target = canonicalTarget(input, identity.agent, identity.coordinate)
  if (journal.target !== target.relative) {
    throw new Error("OrgMemory Skill recovery journal target is not canonical")
  }
  const recovery = resolve(scopeBase(input), ...journal.recovery.split("/"))
  requireInside(target.directory, recovery)
  const staging = journal.staging
    ? resolve(scopeBase(input), ...journal.staging.split("/"))
    : undefined
  if (staging) requireInside(target.directory, staging)

  if (journal.phase === "receipt-committed") {
    const lock = await readReceipt(input)
    if (journal.operation === "remove") {
      if (lock.skills[journal.key] || (await pathExists(target.absolute))) {
        throw new Error("Committed Skill removal does not match its receipt or target")
      }
    } else {
      const committed = lock.skills[journal.key]
      if (!committed || (await verifyEntry(input, committed)).status !== "verified") {
        throw new Error("Committed Skill update does not match its receipt or target")
      }
    }
    if (staging) await rm(staging, { recursive: true, force: true })
    await rm(recovery, { recursive: true, force: true })
    await rm(journalPath(input), { force: true })
    return
  }

  if (journal.operation === "remove") {
    if (await pathExists(recovery)) {
      if (await pathExists(target.absolute)) {
        throw new Error("Skill removal recovery found both target and quarantine")
      }
      await rename(recovery, target.absolute)
    } else if (journal.phase === "tree-promoted") {
      throw new Error("Skill removal recovery quarantine is missing")
    }
  } else if (await pathExists(recovery)) {
    await rm(target.absolute, { recursive: true, force: true })
    await rename(recovery, target.absolute)
  } else if (!journal.previousEntry) {
    await rm(target.absolute, { recursive: true, force: true })
  } else if (journal.phase === "tree-promoted") {
    throw new Error("Skill update recovery backup is missing")
  }
  const lock = await readReceipt(input)
  const restored = { ...lock, skills: { ...lock.skills } }
  if (journal.previousEntry) restored.skills[journal.key] = journal.previousEntry
  else delete restored.skills[journal.key]
  await persistReceipt(input, restored)
  if (staging) await rm(staging, { recursive: true, force: true })
  await rm(recovery, { recursive: true, force: true })
  await rm(journalPath(input), { force: true })
}

function assertTargetAvailable(lock: LockFile, key: string, target: string): void {
  for (const [otherKey, entry] of Object.entries(lock.skills)) {
    if (otherKey === key) continue
    let canonical: string
    try {
      canonical = canonicalTarget({ global: false, cwd: "." }, entry.agent, entry.coordinate).relative
    } catch {
      throw new Error(`Installed Skill receipt ${otherKey} has an invalid coordinate`)
    }
    if (canonical === target || entry.target === target) {
      throw new Error(`The Skill target ${target} is already owned by ${entry.coordinate}`)
    }
  }
}

function canonicalTarget(
  input: { global: boolean; cwd: string },
  agent: Agent,
  coordinate: string,
): { absolute: string; relative: string; directory: string } {
  const parts = coordinate.split("/")
  if (
    parts.length !== 2 ||
    !parts[0] ||
    !parts[1] ||
    !/^[a-z0-9]+(?:[._-][a-z0-9]+)*$/.test(parts[0]) ||
    !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(parts[1])
  ) {
    throw new Error("The installed Skill coordinate is invalid")
  }
  const base = scopeBase(input)
  const directory = resolve(base, agentDirectories[agent])
  const absolute = resolve(directory, parts[1])
  requireInside(directory, absolute)
  return {
    absolute,
    directory,
    relative: relative(base, absolute).replaceAll("\\", "/"),
  }
}

function receiptKey(agent: Agent, coordinate: string): string {
  return `${agent}:${coordinate}`
}

function scopeBase(input: { global: boolean; cwd: string }): string {
  return input.global ? homedir() : resolve(input.cwd)
}

function scopeStateDirectory(input: { global: boolean; cwd: string }): string {
  return join(scopeBase(input), ".orgmemory")
}

function lockPath(input: { global: boolean; cwd: string }): string {
  return join(scopeStateDirectory(input), "skills.lock.json")
}

function mutexPath(input: { global: boolean; cwd: string }): string {
  return join(scopeStateDirectory(input), "skills.lifecycle.lock")
}

function journalPath(input: { global: boolean; cwd: string }): string {
  return join(scopeStateDirectory(input), "skills.operation.json")
}

async function pathExists(path: string): Promise<boolean> {
  try {
    await lstat(path)
    return true
  } catch (error) {
    if (isENOENT(error)) return false
    throw error
  }
}

function hasErrorCode(error: unknown, code: string): boolean {
  return (
    typeof error === "object" &&
    error !== null &&
    "code" in error &&
    (error as { code?: unknown }).code === code
  )
}

function requireInside(base: string, target: string): void {
  const normalizedBase = resolve(base)
  const normalizedTarget = resolve(target)
  if (normalizedTarget !== normalizedBase && !normalizedTarget.startsWith(`${normalizedBase}${sep}`)) {
    throw new Error("The Skill install path escapes its agent directory")
  }
}
