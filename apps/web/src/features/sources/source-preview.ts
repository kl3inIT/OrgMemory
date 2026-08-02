const INLINE_IMAGE_TYPES = new Set(["image/png", "image/jpeg", "image/gif", "image/webp"])

export type SourcePreviewKind = "pdf" | "image" | "text" | "download"

export function sourcePreviewKind(mediaType: string): SourcePreviewKind {
  const normalized = mediaType.split(";", 1)[0]?.trim().toLowerCase() ?? ""
  if (normalized === "application/pdf") return "pdf"
  if (INLINE_IMAGE_TYPES.has(normalized)) return "image"
  if (normalized === "text/plain") return "text"
  return "download"
}
