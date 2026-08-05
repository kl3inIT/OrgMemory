const INLINE_IMAGE_TYPES = new Set(["image/png", "image/jpeg", "image/gif", "image/webp"])

export type SourcePreviewKind = "pdf" | "image" | "markdown" | "text" | "download"

export function sourcePreviewKind(
  responseMediaType: string,
  declaredMediaType?: string,
): SourcePreviewKind {
  const responseType = normalizeMediaType(responseMediaType)
  const declaredType = normalizeMediaType(declaredMediaType ?? "")
  if (responseType === "application/pdf") return "pdf"
  if (INLINE_IMAGE_TYPES.has(responseType)) return "image"
  if (responseType === "text/markdown") return "markdown"
  if (responseType === "text/plain") {
    return declaredType === "text/markdown" ? "markdown" : "text"
  }
  return "download"
}

export function sourceFormatLabel(mediaType?: string, fileName?: string) {
  const normalized = normalizeMediaType(mediaType ?? "")
  switch (normalized) {
    case "application/pdf":
      return "PDF"
    case "text/markdown":
      return "Markdown"
    case "text/plain":
      return "Plain text"
    case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
      return "Word document"
    case "application/vnd.openxmlformats-officedocument.presentationml.presentation":
      return "PowerPoint"
    case "image/png":
      return "PNG image"
    case "image/jpeg":
      return "JPEG image"
    case "image/gif":
      return "GIF image"
    case "image/webp":
      return "WebP image"
  }
  const extension = fileName?.split(".").pop()?.trim()
  return extension && extension !== fileName && extension.length <= 8
    ? `${extension.toUpperCase()} file`
    : "Document"
}

function normalizeMediaType(mediaType: string) {
  return mediaType.split(";", 1)[0]?.trim().toLowerCase() ?? ""
}
