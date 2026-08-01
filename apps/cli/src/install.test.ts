import { createHash } from "node:crypto"
import { spawn } from "node:child_process"
import { once } from "node:events"
import {
  access,
  mkdir,
  mkdtemp,
  readdir,
  rename,
  rm,
  symlink,
  writeFile,
  readFile,
} from "node:fs/promises"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { zipSync } from "fflate"
import { afterEach, describe, expect, it, vi } from "vitest"

import type { SkillManifestLink } from "./contracts.js"
import {
  installSkill,
  listInstalled,
  removeInstalledSkill,
  updateInstalledSkill,
  verifyInstalled,
} from "./install.js"

const temporaryDirectories: string[] = []

afterEach(async () => {
  const { rm } = await import("node:fs/promises")
  await Promise.all(
    temporaryDirectories.splice(0).map((path) =>
      rm(path, { recursive: true, force: true }),
    ),
  )
})

describe("installSkill", () => {
  it("verifies and installs an exact package with a lock receipt", async () => {
    const cwd = await temporaryDirectory()
    const skillMarkdown = new TextEncoder().encode(
      "---\nname: support-triage\ndescription: Triage support\n---\n\nUse the runbook.\n",
    )
    const packageBytes = zipSync({ "support-triage/SKILL.md": skillMarkdown })
    const link = manifest(packageBytes, skillMarkdown)

    const result = await installSkill({
      manifestLink: link,
      packageBytes,
      agent: "codex",
      global: false,
      cwd,
    })

    expect(result.target).toBe(join(cwd, ".agents", "skills", "support-triage"))
    expect(
      await readFile(join(result.target, "SKILL.md"), "utf8"),
    ).toContain("Use the runbook.")
    const lock = JSON.parse(
      await readFile(join(cwd, ".orgmemory", "skills.lock.json"), "utf8"),
    ) as {
      schemaVersion: number
      skills: Record<
        string,
        {
          version: string
          packageDigest: string
          files?: Array<{ path: string; size: number; sha256: string }>
        }
      >
    }
    expect(lock.skills["codex:support/support-triage"]).toMatchObject({
      version: "1.0.0",
      packageDigest: sha256(packageBytes),
    })
    expect(await listInstalled({ global: false, cwd })).toHaveProperty(
      "codex:support/support-triage",
    )
    expect(
      await verifyInstalled({
        global: false,
        cwd,
        coordinate: "support/support-triage",
        agent: "codex",
      }),
    ).toMatchObject({
      "codex:support/support-triage": { status: "verified" },
    })
    expect(lock.schemaVersion).toBe(2)
    expect(lock.skills["codex:support/support-triage"]?.files).toEqual([
      {
        path: "SKILL.md",
        size: skillMarkdown.byteLength,
        sha256: sha256(skillMarkdown),
      },
    ])
  })

  it("marks additions, links, and changed bytes as modified", async () => {
    const cwd = await temporaryDirectory()
    const skillMarkdown = new TextEncoder().encode("original")
    const packageBytes = zipSync({ "support-triage/SKILL.md": skillMarkdown })
    const installed = await installSkill({
      manifestLink: manifest(packageBytes, skillMarkdown),
      packageBytes,
      agent: "codex",
      global: false,
      cwd,
    })

    const link = join(installed.target, "link")
    await symlink(".", link, "junction")

    expect(
      await verifyInstalled({
        global: false,
        cwd,
        coordinate: "support/support-triage",
        agent: "codex",
      }),
    ).toMatchObject({
      "codex:support/support-triage": {
        status: "modified",
        reason: expect.stringContaining("contains a link"),
      },
    })

    await rm(link, { recursive: true, force: true })
    await writeFile(join(installed.target, "extra.sh"), "echo unexpected")

    expect(
      await verifyInstalled({
        global: false,
        cwd,
        coordinate: "support/support-triage",
        agent: "codex",
      }),
    ).toMatchObject({
      "codex:support/support-triage": { status: "modified" },
    })

    await rm(join(installed.target, "extra.sh"))
    await writeFile(join(installed.target, "SKILL.md"), "changed")
    expect(
      await verifyInstalled({
        global: false,
        cwd,
        coordinate: "support/support-triage",
        agent: "codex",
      }),
    ).toMatchObject({
      "codex:support/support-triage": { status: "modified" },
    })
  })

  it("refuses a different coordinate that maps to an owned consumer target", async () => {
    const cwd = await temporaryDirectory()
    const firstContent = new TextEncoder().encode("first namespace")
    const firstPackage = zipSync({ "support-triage/SKILL.md": firstContent })
    await installSkill({
      manifestLink: manifest(firstPackage, firstContent),
      packageBytes: firstPackage,
      agent: "codex",
      global: false,
      cwd,
    })
    const secondContent = new TextEncoder().encode("second namespace")
    const secondPackage = zipSync({ "support-triage/SKILL.md": secondContent })

    await expect(
      installSkill({
        manifestLink: manifest(secondPackage, secondContent, {
          namespace: "operations",
        }),
        packageBytes: secondPackage,
        agent: "codex",
        global: false,
        cwd,
      }),
    ).rejects.toThrow("already owned")

    expect(
      await readFile(
        join(cwd, ".agents", "skills", "support-triage", "SKILL.md"),
        "utf8",
      ),
    ).toBe("first namespace")
  })

  it("serializes concurrent installs without losing receipt entries", async () => {
    const cwd = await temporaryDirectory()
    const install = async (slug: string) => {
      const content = new TextEncoder().encode(slug)
      const bytes = zipSync({ [`${slug}/SKILL.md`]: content })
      await installSkill({
        manifestLink: manifest(bytes, content, { slug }),
        packageBytes: bytes,
        agent: "codex",
        global: false,
        cwd,
      })
    }

    await Promise.all([install("support-triage"), install("incident-response")])

    const installed = await listInstalled({ global: false, cwd })
    expect(Object.keys(installed).sort()).toEqual([
      "codex:support/incident-response",
      "codex:support/support-triage",
    ])
  })

  it("reclaims a lifecycle lock owned by an exited process", async () => {
    const cwd = await temporaryDirectory()
    const exited = spawn(process.execPath, ["-e", "process.exit(0)"], {
      stdio: "ignore",
    })
    const exitedPid = exited.pid
    if (exitedPid === undefined) throw new Error("The exited lock owner did not start")
    await once(exited, "exit")
    const mutex = join(cwd, ".orgmemory", "skills.lifecycle.lock")
    await mkdir(mutex, { recursive: true })
    await writeFile(
      join(mutex, "owner.json"),
      JSON.stringify({ pid: exitedPid, createdAt: new Date().toISOString() }),
    )
    const content = new TextEncoder().encode("recovered lock")
    const bytes = zipSync({ "support-triage/SKILL.md": content })

    await expect(
      installSkill({
        manifestLink: manifest(bytes, content),
        packageBytes: bytes,
        agent: "codex",
        global: false,
        cwd,
      }),
    ).resolves.toMatchObject({
      target: join(cwd, ".agents", "skills", "support-triage"),
    })
  })

  it("does not reclaim a lifecycle lock owned by a live foreign process", async () => {
    const cwd = await temporaryDirectory()
    const owner = spawn(process.execPath, ["-e", "setInterval(() => {}, 1000)"], {
      stdio: "ignore",
    })
    await once(owner, "spawn")
    const ownerPid = owner.pid
    if (ownerPid === undefined) throw new Error("The live lock owner did not start")
    const mutex = join(cwd, ".orgmemory", "skills.lifecycle.lock")
    await mkdir(mutex, { recursive: true })
    await writeFile(
      join(mutex, "owner.json"),
      JSON.stringify({ pid: ownerPid, createdAt: new Date().toISOString() }),
    )
    const content = new TextEncoder().encode("active lock")
    const bytes = zipSync({ "support-triage/SKILL.md": content })
    let now = 0
    const clock = vi.spyOn(Date, "now").mockImplementation(() => {
      now += 20_000
      return now
    })

    try {
      await expect(
        installSkill({
          manifestLink: manifest(bytes, content),
          packageBytes: bytes,
          agent: "codex",
          global: false,
          cwd,
        }),
      ).rejects.toThrow("Another OrgMemory Skill lifecycle operation is active")
      expect(
        JSON.parse(await readFile(join(mutex, "owner.json"), "utf8")),
      ).toMatchObject({ pid: ownerPid })
    } finally {
      clock.mockRestore()
      owner.kill()
      await once(owner, "exit")
    }
  })

  it("updates only the same coordinate to an explicit exact version", async () => {
    const cwd = await temporaryDirectory()
    const original = new TextEncoder().encode("version one")
    const originalPackage = zipSync({ "support-triage/SKILL.md": original })
    await installSkill({
      manifestLink: manifest(originalPackage, original),
      packageBytes: originalPackage,
      agent: "codex",
      global: false,
      cwd,
    })
    const replacement = new TextEncoder().encode("version two")
    const replacementPackage = zipSync({ "support-triage/SKILL.md": replacement })

    await updateInstalledSkill({
      coordinate: "support/support-triage",
      agent: "codex",
      global: false,
      cwd,
      loadRelease: async () => ({
        manifestLink: manifest(replacementPackage, replacement, { version: "2.0.0" }),
        packageBytes: replacementPackage,
      }),
    })

    expect(
      await readFile(
        join(cwd, ".agents", "skills", "support-triage", "SKILL.md"),
        "utf8",
      ),
    ).toBe("version two")
    expect(
      (await listInstalled({ global: false, cwd }))[
        "codex:support/support-triage"
      ]?.version,
    ).toBe("2.0.0")
  })

  it("refuses update and removal after local modification", async () => {
    const cwd = await temporaryDirectory()
    const content = new TextEncoder().encode("original")
    const bytes = zipSync({ "support-triage/SKILL.md": content })
    const installed = await installSkill({
      manifestLink: manifest(bytes, content),
      packageBytes: bytes,
      agent: "codex",
      global: false,
      cwd,
    })
    await writeFile(join(installed.target, "SKILL.md"), "locally edited")

    await expect(
      updateInstalledSkill({
        coordinate: "support/support-triage",
        agent: "codex",
        global: false,
        cwd,
        loadRelease: async () => ({ manifestLink: manifest(bytes, content), packageBytes: bytes }),
      }),
    ).rejects.toThrow("modified")
    await expect(
      removeInstalledSkill({
        coordinate: "support/support-triage",
        agent: "codex",
        global: false,
        cwd,
      }),
    ).rejects.toThrow("modified")
    expect(await readFile(join(installed.target, "SKILL.md"), "utf8")).toBe(
      "locally edited",
    )
  })

  it("removes only a verified v2 install and its receipt", async () => {
    const cwd = await temporaryDirectory()
    const content = new TextEncoder().encode("verified")
    const bytes = zipSync({ "support-triage/SKILL.md": content })
    const installed = await installSkill({
      manifestLink: manifest(bytes, content),
      packageBytes: bytes,
      agent: "claude-code",
      global: false,
      cwd,
    })

    await removeInstalledSkill({
      coordinate: "support/support-triage",
      agent: "claude-code",
      global: false,
      cwd,
    })

    await expect(access(installed.target)).rejects.toThrow()
    expect(await listInstalled({ global: false, cwd })).toEqual({})
  })

  it("keeps schema-v1 receipts readable but unverifiable and non-removable", async () => {
    const cwd = await temporaryDirectory()
    const target = join(cwd, ".agents", "skills", "support-triage")
    const { mkdir } = await import("node:fs/promises")
    await mkdir(target, { recursive: true })
    await writeFile(join(target, "SKILL.md"), "legacy")
    await mkdir(join(cwd, ".orgmemory"), { recursive: true })
    await writeFile(
      join(cwd, ".orgmemory", "skills.lock.json"),
      JSON.stringify({
        schemaVersion: 1,
        skills: {
          "codex:support/support-triage": {
            coordinate: "support/support-triage",
            version: "1.0.0",
            assetId: "85000000-0000-0000-0000-000000000003",
            releaseId: "85000000-0000-0000-0000-000000000004",
            releaseDigest: "c".repeat(64),
            packageDigest: "d".repeat(64),
            agent: "codex",
            target: ".agents/skills/support-triage",
            installedAt: new Date().toISOString(),
          },
        },
      }),
    )

    expect(
      await verifyInstalled({
        global: false,
        cwd,
        coordinate: "support/support-triage",
        agent: "codex",
      }),
    ).toMatchObject({
      "codex:support/support-triage": { status: "unverifiable" },
    })
    await expect(
      removeInstalledSkill({
        coordinate: "support/support-triage",
        agent: "codex",
        global: false,
        cwd,
      }),
    ).rejects.toThrow("unverifiable")
  })

  it("leaves an active install untouched when package integrity fails", async () => {
    const cwd = await temporaryDirectory()
    const target = join(cwd, ".claude", "skills", "support-triage")
    const { mkdir } = await import("node:fs/promises")
    await mkdir(target, { recursive: true })
    await writeFile(join(target, "SKILL.md"), "existing")
    const skillMarkdown = new TextEncoder().encode("replacement")
    const packageBytes = zipSync({ "support-triage/SKILL.md": skillMarkdown })
    const link = manifest(packageBytes, skillMarkdown)
    link.manifest.packageDigest = "0".repeat(64)

    await expect(
      installSkill({
        manifestLink: link,
        packageBytes,
        agent: "claude-code",
        global: false,
        cwd,
      }),
    ).rejects.toThrow("integrity")

    expect(await readFile(join(target, "SKILL.md"), "utf8")).toBe("existing")
    await expect(access(join(cwd, ".orgmemory", "skills.lock.json"))).rejects.toThrow()
  })

  it("rejects a manifest above the bounded extraction budget before install", async () => {
    const cwd = await temporaryDirectory()
    const skillMarkdown = new TextEncoder().encode("bounded")
    const packageBytes = zipSync({ "support-triage/SKILL.md": skillMarkdown })
    const link = manifest(packageBytes, skillMarkdown)
    link.manifest.files.push({
      path: "references/oversized.md",
      size: 50 * 1024 * 1024,
      sha256: "d".repeat(64),
    })

    await expect(
      installSkill({
        manifestLink: link,
        packageBytes,
        agent: "codex",
        global: false,
        cwd,
      }),
    ).rejects.toThrow("50 MiB extraction limit")
  })

  it("restores the active install when the lock receipt cannot be committed", async () => {
    const cwd = await temporaryDirectory()
    const target = join(cwd, ".agents", "skills", "support-triage")
    const { mkdir, readdir } = await import("node:fs/promises")
    await mkdir(target, { recursive: true })
    await writeFile(join(target, "SKILL.md"), "existing")
    await writeFile(join(cwd, ".orgmemory"), "blocks the receipt directory")
    const skillMarkdown = new TextEncoder().encode("replacement")
    const packageBytes = zipSync({ "support-triage/SKILL.md": skillMarkdown })

    await expect(
      installSkill({
        manifestLink: manifest(packageBytes, skillMarkdown),
        packageBytes,
        agent: "codex",
        global: false,
        cwd,
      }),
    ).rejects.toThrow()

    expect(await readFile(join(target, "SKILL.md"), "utf8")).toBe("existing")
    expect(
      (await readdir(join(cwd, ".agents", "skills"))).filter((name) =>
        name.includes(".orgmemory-"),
      ),
    ).toEqual([])
  })

  it("restores the active install when promotion fails after backup", async () => {
    const cwd = await temporaryDirectory()
    const target = join(cwd, ".agents", "skills", "support-triage")
    const original = new TextEncoder().encode("existing")
    const originalPackage = zipSync({ "support-triage/SKILL.md": original })
    await installSkill({
      manifestLink: manifest(originalPackage, original),
      packageBytes: originalPackage,
      agent: "codex",
      global: false,
      cwd,
    })
    const skillMarkdown = new TextEncoder().encode("replacement")
    const packageBytes = zipSync({ "support-triage/SKILL.md": skillMarkdown })
    let renameCount = 0

    await expect(
      updateInstalledSkill({
        coordinate: "support/support-triage",
        agent: "codex",
        global: false,
        cwd,
        loadRelease: async () => ({
          manifestLink: manifest(packageBytes, skillMarkdown, { version: "2.0.0" }),
          packageBytes,
        }),
        promotionOperations: {
          rename: async (source, destination) => {
            renameCount += 1
            if (renameCount === 2) throw new Error("promotion failed")
            await rename(source, destination)
          },
          rm,
        },
      }),
    ).rejects.toThrow("promotion failed")

    expect(await readFile(join(target, "SKILL.md"), "utf8")).toBe("existing")
    expect(
      (await readdir(join(cwd, ".agents", "skills"))).filter((name) =>
        name.includes("orgmemory-backup"),
      ),
    ).toEqual([])
  })

  it("uses the durable journal when immediate promotion rollback fails", async () => {
    const cwd = await temporaryDirectory()
    const skillsDirectory = join(cwd, ".agents", "skills")
    const target = join(skillsDirectory, "support-triage")
    const original = new TextEncoder().encode("existing")
    const originalPackage = zipSync({ "support-triage/SKILL.md": original })
    await installSkill({
      manifestLink: manifest(originalPackage, original),
      packageBytes: originalPackage,
      agent: "codex",
      global: false,
      cwd,
    })
    const skillMarkdown = new TextEncoder().encode("replacement")
    const packageBytes = zipSync({ "support-triage/SKILL.md": skillMarkdown })
    let renameCount = 0

    await expect(
      updateInstalledSkill({
        coordinate: "support/support-triage",
        agent: "codex",
        global: false,
        cwd,
        loadRelease: async () => ({
          manifestLink: manifest(packageBytes, skillMarkdown, { version: "2.0.0" }),
          packageBytes,
        }),
        promotionOperations: {
          rename: async (source, destination) => {
            renameCount += 1
            if (renameCount === 2) throw new Error("promotion failed")
            if (renameCount === 3) throw new Error("rollback failed")
            await rename(source, destination)
          },
          rm,
        },
      }),
    ).rejects.toThrow("requires recovery")

    const backups = (await readdir(skillsDirectory)).filter((name) =>
      name.includes("orgmemory-backup"),
    )
    expect(backups).toHaveLength(0)
    expect(await readFile(join(target, "SKILL.md"), "utf8")).toBe("existing")
    await expect(
      access(join(cwd, ".orgmemory", "skills.operation.json")),
    ).rejects.toThrow()
  })

  it("recovers an interrupted promoted tree before the next lifecycle command", async () => {
    const cwd = await temporaryDirectory()
    const original = new TextEncoder().encode("original")
    const originalPackage = zipSync({ "support-triage/SKILL.md": original })
    const installed = await installSkill({
      manifestLink: manifest(originalPackage, original),
      packageBytes: originalPackage,
      agent: "codex",
      global: false,
      cwd,
    })
    const receiptPath = join(cwd, ".orgmemory", "skills.lock.json")
    const lock = JSON.parse(await readFile(receiptPath, "utf8")) as {
      skills: Record<string, Record<string, unknown>>
    }
    const previousEntry = lock.skills["codex:support/support-triage"]
    const backup = `${installed.target}.orgmemory-backup-crash-fixture`
    await rename(installed.target, backup)
    const { mkdir } = await import("node:fs/promises")
    await mkdir(installed.target, { recursive: true })
    await writeFile(join(installed.target, "SKILL.md"), "uncommitted replacement")
    await writeFile(
      join(cwd, ".orgmemory", "skills.operation.json"),
      JSON.stringify({
        schemaVersion: 1,
        operation: "update",
        phase: "tree-promoted",
        key: "codex:support/support-triage",
        target: ".agents/skills/support-triage",
        recovery: ".agents/skills/support-triage.orgmemory-backup-crash-fixture",
        previousEntry,
        nextEntry: { ...previousEntry, version: "2.0.0" },
        startedAt: new Date().toISOString(),
      }),
    )

    const recovered = await listInstalled({ global: false, cwd })

    expect(await readFile(join(installed.target, "SKILL.md"), "utf8")).toBe(
      "original",
    )
    expect(recovered["codex:support/support-triage"]?.version).toBe("1.0.0")
    await expect(
      access(join(cwd, ".orgmemory", "skills.operation.json")),
    ).rejects.toThrow()
  })
})

async function temporaryDirectory(): Promise<string> {
  const path = await mkdtemp(join(tmpdir(), "orgmemory-cli-"))
  temporaryDirectories.push(path)
  return path
}

function manifest(
  packageBytes: Uint8Array,
  skillMarkdown: Uint8Array,
  overrides: { namespace?: string; slug?: string; version?: string } = {},
): SkillManifestLink {
  const namespace = overrides.namespace ?? "support"
  const slug = overrides.slug ?? "support-triage"
  return {
    manifest: {
      assetId: "85000000-0000-0000-0000-000000000003",
      releaseId: "85000000-0000-0000-0000-000000000004",
      namespace,
      slug,
      coordinate: `${namespace}/${slug}`,
      version: overrides.version ?? "1.0.0",
      title: "Support triage",
      description: "Triage support",
      releaseDigest: "c".repeat(64),
      packageDigest: sha256(packageBytes),
      packageLength: packageBytes.byteLength,
      mediaType: "application/zip",
      license: "MIT",
      compatibility: "Claude Code and Codex",
      allowedTools: "Read",
      metadata: {},
      files: [
        {
          path: "SKILL.md",
          size: skillMarkdown.byteLength,
          sha256: sha256(skillMarkdown),
        },
      ],
    },
    packagePath:
      "/skill-packages/85000000-0000-0000-0000-000000000003/releases/85000000-0000-0000-0000-000000000004",
  }
}

function sha256(bytes: Uint8Array): string {
  return createHash("sha256").update(bytes).digest("hex")
}
