import type { MetadataRoute } from 'next';
import { source } from '@/lib/source';
import { siteUrl } from '@/lib/site';

export default function sitemap(): MetadataRoute.Sitemap {
  return [
    {
      url: siteUrl,
      changeFrequency: 'weekly',
      priority: 1,
    },
    ...source.getPages().map((page) => ({
      url: new URL(page.url, siteUrl).toString(),
      lastModified: new Date(`${page.data.lastReviewed}T00:00:00Z`),
      changeFrequency: 'weekly' as const,
      priority: page.url === '/docs/overview' ? 0.9 : 0.7,
    })),
  ];
}
