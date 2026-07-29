export const docsCategories = [
  'overview',
  'architecture-security',
  'deployment',
  'admins',
  'developers',
] as const;

export type DocsCategory = (typeof docsCategories)[number];

const docsCategorySet = new Set<string>(docsCategories);

export function getDocsCategory(path?: string | string[]): DocsCategory {
  const segments = Array.isArray(path) ? path : path?.split('/');
  const category = segments?.find((segment) => docsCategorySet.has(segment));

  return (category as DocsCategory | undefined) ?? 'overview';
}

export function getDocsCategoryColor(category: DocsCategory) {
  return `var(--docs-${category}-color, var(--color-fd-foreground))`;
}
