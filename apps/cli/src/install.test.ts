import { createHash } from "node:crypto"
import { access, mkdtemp, readFile, writeFile } from "node:fs/promises"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { zipSync } from "fflate"
import { afterEach, describe, expect, it } from "vitest"

import type { SkillManifestLink } from "./contracts.js"
import { installSkill } from "./install.js"

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
      skills: Record<string, { version: string; packageDigest: string }>
    }
    expect(lock.skills["codex:support/support-triage"]).toMatchObject({
      version: "1.0.0",
      packageDigest: sha256(packageBytes),
    })
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
})

async function temporaryDirectory(): Promise<string> {
  const path = await mkdtemp(join(tmpdir(), "orgmemory-cli-"))
  temporaryDirectories.push(path)
  return path
}

function manifest(
  packageBytes: Uint8Array,
  skillMarkdown: Uint8Array,
): SkillManifestLink {
  return {
    manifest: {
      assetId: "85000000-0000-0000-0000-000000000003",
      releaseId: "85000000-0000-0000-0000-000000000004",
      namespace: "support",
      slug: "support-triage",
      coordinate: "support/support-triage",
      version: "1.0.0",
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
