import { createMDX } from 'fumadocs-mdx/next';
import { fileURLToPath } from 'node:url';

const withMDX = createMDX();
const repositoryRoot = fileURLToPath(new URL('../..', import.meta.url));
const movedDocsPages = [
  ['/docs/overview', '/docs/getting-started'],
  ['/docs/overview/quickstart', '/docs/getting-started/quickstart'],
  ['/docs/overview/core-concepts', '/docs/getting-started/core-concepts'],
  ['/docs/overview/asset-lifecycle', '/docs/architecture-security/asset-lifecycle'],
  ['/docs/admins', '/docs/guides/administration/identity-permissions'],
  [
    '/docs/admins/identity-permissions',
    '/docs/guides/administration/identity-permissions',
  ],
  [
    '/docs/admins/sources-connections',
    '/docs/guides/administration/sources-connections',
  ],
  ['/docs/deployment', '/docs/guides/deployment-operations/self-hosting'],
  ['/docs/deployment/self-hosting', '/docs/guides/deployment-operations/self-hosting'],
  ['/docs/developers', '/docs/guides/integrations/assistant-mcp'],
  ['/docs/developers/assistant-mcp', '/docs/guides/integrations/assistant-mcp'],
  ['/docs/developers/api-reference', '/docs/reference/api-reference'],
];

function movedPageRedirects() {
  return movedDocsPages.flatMap(([source, destination]) =>
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
  reactStrictMode: true,
  async redirects() {
    return [
      ...movedPageRedirects(),
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
