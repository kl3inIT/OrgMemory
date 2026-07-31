import type { MetadataRoute } from 'next';
import { source } from '@/lib/source';
import { siteUrl } from '@/lib/site';
import { i18n } from '@/lib/i18n';

export default function sitemap(): MetadataRoute.Sitemap {
  return source.getPages().map((page) => {
    const languages = Object.fromEntries(
      i18n.languages.flatMap((language) => {
        const localizedPage = source.getPage(page.slugs, language);
        return localizedPage
          ? [[language, new URL(localizedPage.url, siteUrl).toString()]]
          : [];
      }),
    );

    return {
      url: new URL(page.url, siteUrl).toString(),
      lastModified: new Date(`${page.data.lastReviewed}T00:00:00Z`),
      changeFrequency: 'weekly' as const,
      priority: page.slugs.join('/') === 'getting-started' ? 0.9 : 0.7,
      alternates: { languages },
    };
  });
}
