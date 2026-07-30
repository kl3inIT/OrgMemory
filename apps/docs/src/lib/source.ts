import { docs } from 'collections/server';
import { loader } from 'fumadocs-core/source';
import { lucideIconsPlugin } from 'fumadocs-core/source/lucide-icons';
import { toFumadocsSource } from 'fumadocs-mdx/runtime/server';
import { docsContentRoute, docsImageRoute, docsRoute } from './shared';
import { openapi } from './openapi';
import { i18n, isDocsLanguage, withLocale } from './i18n';

export const includeDrafts = process.env.DOCS_INCLUDE_DRAFTS === 'true';
const visibleDocs = includeDrafts
  ? docs.docs
  : docs.docs.filter((page) => page.status === 'public');

export const source = loader({
  baseUrl: docsRoute,
  i18n,
  source: toFumadocsSource(visibleDocs, docs.meta),
  plugins: [openapi.loaderPlugin(), lucideIconsPlugin()],
});

function getPageLanguage(page: (typeof source)['$inferPage']) {
  const candidate = page.locale;
  return candidate && isDocsLanguage(candidate) ? candidate : i18n.defaultLanguage;
}

export function getPageImageUrl(page: (typeof source)['$inferPage']) {
  const segments = [...page.slugs, 'image.png'];
  const pathname = '/' + [docsImageRoute, ...segments].join('/').replaceAll('//', '/');

  return {
    segments,
    url: withLocale(pathname, getPageLanguage(page)),
  };
}

export function getPageMarkdownUrl(page: (typeof source)['$inferPage']) {
  const segments = [...page.slugs, 'content.md'];
  const pathname = '/' + [docsContentRoute, ...segments].join('/').replaceAll('//', '/');

  return {
    segments,
    url: withLocale(pathname, getPageLanguage(page)),
  };
}

export async function getLLMText(page: (typeof source)['$inferPage']) {
  const processed = await page.data.getText('processed');

  return `# ${page.data.title} (${page.url})

${processed}`;
}
