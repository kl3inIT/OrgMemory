export const ADMIN_PAGE_SIZE = 10

export function pageItems<T>(items: T[], page: number, pageSize = ADMIN_PAGE_SIZE) {
  const pageCount = Math.max(1, Math.ceil(items.length / pageSize))
  const safePage = Math.min(Math.max(page, 1), pageCount)
  const start = (safePage - 1) * pageSize
  return items.slice(start, start + pageSize)
}
