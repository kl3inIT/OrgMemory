import { createHash, randomBytes } from "node:crypto"
import { rename, rm, writeFile } from "node:fs/promises"

export function isENOENT(error: unknown): boolean {
  return (
    error instanceof Error &&
    "code" in error &&
    (error as NodeJS.ErrnoException).code === "ENOENT"
  )
}

export function requireSafeRelativePath(value: string): void {
  if (
    !value ||
    value.length > 1024 ||
    value.startsWith("/") ||
    value.endsWith("/") ||
    value.includes("\\") ||
    value.includes("\0") ||
    value.includes(":") ||
    value.split("/").some((segment) => !segment || segment === "." || segment === "..")
  ) {
    throw new Error("Skill package contains an unsafe relative path")
  }
}

export function sha256(bytes: Uint8Array): string {
  return createHash("sha256").update(bytes).digest("hex")
}

export async function atomicWriteJson(path: string, value: unknown): Promise<void> {
  const temporary = `${path}.${process.pid}.${randomBytes(6).toString("hex")}.tmp`
  try {
    await writeFile(temporary, `${JSON.stringify(value, null, 2)}\n`, {
      encoding: "utf8",
      mode: 0o600,
    })
    await rename(temporary, path)
  } finally {
    await rm(temporary, { force: true })
  }
}
