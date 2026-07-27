import type { LocalSkillPackage } from "./skill-package.js"
import {
  skillDraftPublicationSchema,
  type SkillDraftPublication,
} from "./contracts.js"

const PUBLICATION_TIMEOUT_MS = 60_000
const MAX_ERROR_BYTES = 64 * 1024

export type SkillClassification =
  | "PUBLIC"
  | "INTERNAL"
  | "CONFIDENTIAL"
  | "RESTRICTED"

export async function publishSkillDraft(input: {
  serverUrl: URL
  token: string
  skillPackage: LocalSkillPackage
  namespace: string
  knowledgeSpaceId: string
  classification: SkillClassification
}): Promise<SkillDraftPublication> {
  const form = new FormData()
  form.set(
    "file",
    new Blob([Uint8Array.from(input.skillPackage.archiveBytes)], {
      type: "application/zip",
    }),
    `${input.skillPackage.name}.zip`,
  )
  form.set("namespace", input.namespace)
  form.set("knowledgeSpaceId", input.knowledgeSpaceId)
  form.set("classification", input.classification)

  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), PUBLICATION_TIMEOUT_MS)
  try {
    const response = await fetch(publicationUrl(input.serverUrl), {
      method: "POST",
      headers: {
        Authorization: `Bearer ${input.token}`,
      },
      body: form,
      redirect: "error",
      signal: controller.signal,
    })
    if (!response.ok) {
      throw new Error(await publicationError(response))
    }
    return skillDraftPublicationSchema.parse(await response.json())
  } finally {
    clearTimeout(timeout)
  }
}

export function publicationUrl(serverUrl: URL): URL {
  const url = new URL("/skill-publications", serverUrl)
  if (
    url.origin !== serverUrl.origin ||
    url.pathname !== "/skill-publications" ||
    url.search ||
    url.hash
  ) {
    throw new Error("The Skill publication endpoint is invalid")
  }
  return url
}

async function publicationError(response: Response): Promise<string> {
  const length = Number.parseInt(response.headers.get("content-length") ?? "", 10)
  if (Number.isFinite(length) && length > MAX_ERROR_BYTES) {
    return `OrgMemory rejected Skill publication (HTTP ${response.status})`
  }
  const body = (await response.text()).slice(0, MAX_ERROR_BYTES)
  try {
    const problem = JSON.parse(body) as {
      detail?: unknown
      title?: unknown
    }
    const detail =
      typeof problem.detail === "string"
        ? problem.detail
        : typeof problem.title === "string"
          ? problem.title
          : null
    if (detail) return `OrgMemory rejected Skill publication: ${detail}`
  } catch {
    // Fall back to a bounded status-only error for non-Problem responses.
  }
  return `OrgMemory rejected Skill publication (HTTP ${response.status})`
}
