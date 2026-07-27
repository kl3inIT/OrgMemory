import { z } from "zod"

const digestSchema = z.string().regex(/^[0-9a-f]{64}$/)
const namespaceSchema = z
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

const assetSchema = z.object({
  assetId: z.uuid(),
  type: z.literal("SKILL"),
  namespace: namespaceSchema,
  slug: slugSchema,
  title: z.string(),
  summary: z.string(),
  releaseId: z.uuid(),
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
    assetId: z.uuid(),
    releaseId: z.uuid(),
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
  packagePath: z.string().startsWith("/skill-packages/"),
})

export type SkillManifestLink = z.infer<typeof skillManifestLinkSchema>

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
