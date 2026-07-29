export const docsCategories = [
  'getting-started',
  'guides',
  'architecture-security',
  'reference',
] as const;

export type DocsCategory = (typeof docsCategories)[number];

const docsCategorySet = new Set<string>(docsCategories);

export function getDocsCategory(path?: string | string[]): DocsCategory {
  const segments = Array.isArray(path) ? path : path?.split('/');
  const category = segments?.find((segment) => docsCategorySet.has(segment));

  return (category as DocsCategory | undefined) ?? 'getting-started';
}

export function getDocsCategoryColor(category: DocsCategory) {
  return `var(--docs-${category}-color, var(--color-fd-foreground))`;
}
