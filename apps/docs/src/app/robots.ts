import type { MetadataRoute } from 'next';

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: '*',
      allow: '/',
    },
    sitemap: 'https://docs.om.kl3in.tech/sitemap.xml',
    host: 'https://docs.om.kl3in.tech',
  };
}
