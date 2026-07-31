function nonEmptyString(value: unknown) {
  return typeof value === "string" && value.trim() ? value.trim() : undefined
}

export function apiErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error) return nonEmptyString(error.message) ?? fallback
  if (typeof error === "string") return nonEmptyString(error) ?? fallback
  if (!error || typeof error !== "object") return fallback

  const problem = error as Record<string, unknown>
  return nonEmptyString(problem.detail) ?? nonEmptyString(problem.title) ?? fallback
}
