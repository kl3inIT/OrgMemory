import type { BaseLayoutProps } from 'fumadocs-ui/layouts/shared';
import { appName, gitConfig } from './shared';

export function baseOptions(): BaseLayoutProps {
  const includeDrafts = process.env.DOCS_INCLUDE_DRAFTS === 'true';

  return {
    nav: {
      title: appName,
    },
    links: [
      {
        text: 'Overview',
        url: includeDrafts ? '/docs/overview' : '/docs',
        active: 'nested-url',
      },
      ...(includeDrafts
        ? [
            {
              text: 'Deployment',
              url: '/docs/deployment/self-hosting',
              active: 'nested-url' as const,
            },
            {
              text: 'Admins',
              url: '/docs/admins/identity-permissions',
              active: 'nested-url' as const,
            },
            {
              text: 'Developers',
              url: '/docs/developers/assistant-mcp',
              active: 'nested-url' as const,
            },
            {
              text: 'Architecture & Security',
              url: '/docs/architecture-security/system-description',
              active: 'nested-url' as const,
            },
          ]
        : []),
      {
        text: 'Changelog',
        url: `https://github.com/${gitConfig.user}/${gitConfig.repo}/releases`,
        external: true,
      },
      {
        text: 'GitHub',
        url: `https://github.com/${gitConfig.user}/${gitConfig.repo}`,
        external: true,
      },
      {
        text: 'Product',
        url: 'https://om.kl3in.tech',
        external: true,
      },
    ],
  };
}
