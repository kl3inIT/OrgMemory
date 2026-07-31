import { mkdtemp, readFile, readdir, rm, stat } from "node:fs/promises"
import { tmpdir } from "node:os"
import { join } from "node:path"

import { afterEach, describe, expect, it } from "vitest"

import {
  atomicWriteJson,
  isENOENT,
  requireSafeRelativePath,
  sha256,
} from "./shared.js"

const temporaryDirectories: string[] = []

afterEach(async () => {
  await Promise.all(
    temporaryDirectories.splice(0).map((path) =>
      rm(path, { recursive: true, force: true }),
    ),
  )
})

describe("shared CLI boundaries", () => {
  it.each(["SKILL.md", "references/policy.md", "nested/path/file.txt"])(
    "accepts safe relative path %s",
    (path) => {
      expect(() => requireSafeRelativePath(path)).not.toThrow()
    },
  )

  it.each([
    "",
    "/SKILL.md",
    "SKILL.md/",
    "nested\\file.txt",
    "nested\0file.txt",
    "C:SKILL.md",
    "./SKILL.md",
    "nested/../SKILL.md",
    "nested//SKILL.md",
    "x".repeat(1025),
  ])("rejects unsafe relative path %j", (path) => {
    expect(() => requireSafeRelativePath(path)).toThrow("unsafe relative path")
  })

  it("recognizes only ENOENT errors", () => {
    expect(isENOENT(Object.assign(new Error("missing"), { code: "ENOENT" }))).toBe(true)
    expect(isENOENT(Object.assign(new Error("denied"), { code: "EACCES" }))).toBe(false)
    expect(isENOENT({ code: "ENOENT" })).toBe(false)
  })

  it("computes lowercase SHA-256", () => {
    expect(sha256(new TextEncoder().encode("abc"))).toBe(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
    )
  })

  it("atomically writes private formatted JSON and removes the temporary file", async () => {
    const directory = await mkdtemp(join(tmpdir(), "orgmemory-shared-test-"))
    temporaryDirectories.push(directory)
    const path = join(directory, "state.json")

    await atomicWriteJson(path, { enabled: true })

    expect(await readFile(path, "utf8")).toBe('{\n  "enabled": true\n}\n')
    expect(await readdir(directory)).toEqual(["state.json"])
    if (process.platform !== "win32") {
      expect((await stat(path)).mode & 0o777).toBe(0o600)
    }
  })
})
