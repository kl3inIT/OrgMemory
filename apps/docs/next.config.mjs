import { createMDX } from 'fumadocs-mdx/next';
import { fileURLToPath } from 'node:url';

const withMDX = createMDX();
const repositoryRoot = fileURLToPath(new URL('../..', import.meta.url));
const contentSecurityPolicy = [
  "default-src 'self'",
  "base-uri 'self'",
  "object-src 'none'",
  "frame-ancestors 'none'",
  "form-action 'self'",
  "script-src 'self' 'unsafe-inline'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  "connect-src 'self'",
  "worker-src 'self' blob:",
  'upgrade-insecure-requests',
].join('; ');
const movedDocsPages = [
  ['/docs/overview', '/docs/getting-started'],
  ['/docs/overview/quickstart', '/docs/getting-started'],
  ['/docs/getting-started/quickstart', '/docs/getting-started'],
  ['/docs/overview/core-concepts', '/docs/getting-started/core-concepts'],
  ['/docs/overview/asset-lifecycle', '/docs/product-guides/work-with-governed-assets'],
  [
    '/docs/architecture-security/asset-lifecycle',
    '/docs/product-guides/work-with-governed-assets',
  ],
  ['/docs/guides', '/docs/getting-started'],
  ['/docs/guides/administration', '/docs/architecture-security/authorization'],
  [
    '/docs/guides/administration/identity-permissions',
    '/docs/architecture-security/authorization',
  ],
  [
    '/docs/guides/administration/sources-connections',
    '/docs/architecture-security/ingestion-lifecycle',
  ],
  ['/docs/guides/deployment-operations', '/docs/architecture-security/system-description'],
  [
    '/docs/guides/deployment-operations/self-hosting',
    '/docs/architecture-security/system-description',
  ],
  ['/docs/guides/integrations', '/docs/reference/api-reference/assistant'],
  ['/docs/guides/integrations/assistant-mcp', '/docs/reference/api-reference/assistant'],
  ['/docs/admins', '/docs/architecture-security/authorization'],
  ['/docs/admins/identity-permissions', '/docs/architecture-security/authorization'],
  ['/docs/admins/sources-connections', '/docs/architecture-security/ingestion-lifecycle'],
  ['/docs/deployment', '/docs/architecture-security/system-description'],
  ['/docs/deployment/self-hosting', '/docs/architecture-security/system-description'],
  ['/docs/developers', '/docs/reference/api-reference/assistant'],
  ['/docs/developers/assistant-mcp', '/docs/reference/api-reference/assistant'],
  ['/docs/developers/api-reference', '/docs/reference/api-reference'],
];
const sectionLandingPages = [
  ['/docs/product-guides', '/docs/product-guides/work-with-governed-assets'],
];

function localizedPageRedirects(pages) {
  return pages.flatMap(([source, destination]) =>
    ['', '/vi'].flatMap((locale) =>
      ['', '.md'].map((extension) => ({
        source: `${locale}${source}${extension}`,
        destination: `${locale}${destination}${extension}`,
        permanent: true,
      })),
    ),
  );
}

/** @type {import('next').NextConfig} */
const config = {
  output: 'standalone',
  outputFileTracingRoot: repositoryRoot,
  poweredByHeader: false,
  reactStrictMode: true,
  experimental: {
    staticGenerationMaxConcurrency: 1,
    staticGenerationMinPagesPerWorker: 150,
    staticGenerationRetryCount: 1,
  },
  async redirects() {
    return [
      ...localizedPageRedirects(movedDocsPages),
      ...localizedPageRedirects(sectionLandingPages),
      {
        source: '/docs/developers/api-reference/:path*',
        destination: '/docs/reference/api-reference/:path*',
        permanent: true,
      },
      {
        source: '/vi/docs/developers/api-reference/:path*',
        destination: '/vi/docs/reference/api-reference/:path*',
        permanent: true,
      },
    ];
  },
  async headers() {
    return [
      {
        source: '/(.*)',
        headers: [
          {
            key: 'Content-Security-Policy',
            value: contentSecurityPolicy,
          },
          {
            key: 'Strict-Transport-Security',
            value: 'max-age=31536000; includeSubDomains',
          },
          {
            key: 'X-Content-Type-Options',
            value: 'nosniff',
          },
          {
            key: 'X-Frame-Options',
            value: 'DENY',
          },
          {
            key: 'Referrer-Policy',
            value: 'strict-origin-when-cross-origin',
          },
          {
            key: 'Permissions-Policy',
            value: 'camera=(), microphone=(), geolocation=()',
          },
        ],
      },
    ];
  },
};

export default withMDX(config);
