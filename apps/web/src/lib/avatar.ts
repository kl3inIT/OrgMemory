export function avatarInitials(name?: string, email?: string) {
  const source = name?.trim() || email?.trim()
  if (!source) return "OM"

  return source
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase()
}
