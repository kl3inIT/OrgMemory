import { z } from "zod"

const digestSchema = z.string().regex(/^[0-9a-f]{64}$/)
export const orgMemoryUuidSchema = z
  .string()
  .regex(
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/,
  )
export const namespaceSchema = z
  .string()
  .max(128)
  .regex(/^[a-z0-9]+(?:[._-][a-z0-9]+)*$/)
const slugSchema = z
  .string()
  .max(128)
  .regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/)
const versionSchema = z
  .string()
  .max(64)
  .regex(/^[a-z0-9]+(?:[._-][a-z0-9]+)*$/)
const packagePathPattern =
  /^\/skill-packages\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\/releases\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/

const assetSchema = z.object({
  assetId: orgMemoryUuidSchema,
  type: z.literal("SKILL"),
  namespace: namespaceSchema,
  slug: slugSchema,
  title: z.string(),
  summary: z.string(),
  releaseId: orgMemoryUuidSchema,
  versionLabel: versionSchema,
  releaseDigest: digestSchema,
})

export const skillSearchSchema = z.object({
  assets: z.array(
    z.object({
      asset: assetSchema,
      resourceUri: z.string(),
      releaseResourceUri: z.string(),
    }),
  ),
})

const skillFileSchema = z.object({
  path: z.string().min(1).max(1024),
  size: z.number().int().nonnegative().max(50 * 1024 * 1024),
  sha256: digestSchema,
})

export const skillManifestLinkSchema = z.object({
  manifest: z.object({
    assetId: orgMemoryUuidSchema,
    releaseId: orgMemoryUuidSchema,
    namespace: namespaceSchema,
    slug: slugSchema,
    coordinate: z.string().min(3).max(257),
    version: versionSchema,
    title: z.string(),
    description: z.string(),
    releaseDigest: digestSchema,
    packageDigest: digestSchema,
    packageLength: z.number().int().positive().max(20 * 1024 * 1024),
    mediaType: z.union([z.literal("application/zip"), z.literal("application/octet-stream")]),
    license: z.string(),
    compatibility: z.string(),
    allowedTools: z.string(),
    metadata: z.record(z.string(), z.string()),
    files: z.array(skillFileSchema).min(1).max(300),
  }),
  packagePath: z.string().regex(packagePathPattern),
})

export type SkillManifestLink = z.infer<typeof skillManifestLinkSchema>

export const skillDraftPublicationSchema = z.object({
  id: orgMemoryUuidSchema,
  type: z.literal("SKILL"),
  namespace: namespaceSchema,
  slug: slugSchema,
  knowledgeSpaceId: orgMemoryUuidSchema,
  draft: z.object({
    id: orgMemoryUuidSchema,
    lockVersion: z.number().int().nonnegative(),
    title: z.string(),
    summary: z.string(),
  }),
})

export type SkillDraftPublication = z.infer<
  typeof skillDraftPublicationSchema
>

export function resolvePackageUrl(
  serverUrl: URL,
  link: SkillManifestLink,
): URL {
  const url = new URL(link.packagePath, serverUrl)
  const match = packagePathPattern.exec(url.pathname)
  if (
    url.origin !== serverUrl.origin ||
    url.search ||
    url.hash ||
    !match ||
    match[1] !== link.manifest.assetId ||
    match[2] !== link.manifest.releaseId
  ) {
    throw new Error("OrgMemory returned an invalid Skill package location")
  }
  return url
}

export function parseSkillReference(reference: string): {
  namespace: string
  slug: string
  version: string
} {
  const separator = reference.lastIndexOf("@")
  if (separator <= 0 || separator === reference.length - 1) {
    throw new Error("Use an exact Skill reference: <namespace>/<slug>@<version>")
  }
  const coordinate = reference.slice(0, separator)
  const version = reference.slice(separator + 1)
  const parts = coordinate.split("/")
  if (parts.length !== 2) {
    throw new Error("Use an exact Skill reference: <namespace>/<slug>@<version>")
  }
  const [namespace, slug] = parts
  if (
    !namespace ||
    !slug ||
    !namespaceSchema.safeParse(namespace).success ||
    !slugSchema.safeParse(slug).success ||
    !versionSchema.safeParse(version).success
  ) {
    throw new Error("The Skill coordinate or version is invalid")
  }
  return { namespace, slug, version }
}
