import { includeDrafts, source } from '@/lib/source';
import { DocsLayout } from 'fumadocs-ui/layouts/docs';
import { baseOptions } from '@/lib/layout.shared';

const previewTabs = [
  {
    title: 'Overview',
    description: 'Understand OrgMemory and begin a useful workflow.',
    url: '/docs/overview',
  },
  {
    title: 'Deployment',
    description: 'Run OrgMemory with explicit operational boundaries.',
    url: '/docs/deployment/self-hosting',
  },
  {
    title: 'Admins',
    description: 'Operate identity, permissions, and governed sources.',
    url: '/docs/admins/identity-permissions',
  },
  {
    title: 'Developers',
    description: 'Integrate through supported API and MCP contracts.',
    url: '/docs/developers/assistant-mcp',
  },
  {
    title: 'Architecture & Security',
    description: 'Inspect responsibilities, trust boundaries, and evidence.',
    url: '/docs/architecture-security/system-description',
  },
];

export default function Layout({ children }: LayoutProps<'/docs'>) {
  return (
    <DocsLayout
      tree={source.getPageTree()}
      tabs={includeDrafts ? previewTabs : false}
      tabMode="top"
      sidebar={{
        collapsible: true,
        defaultOpenLevel: 1,
      }}
      {...baseOptions()}
    >
      {children}
    </DocsLayout>
  );
}
