import { RootProvider } from 'fumadocs-ui/provider/next';
import './global.css';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  metadataBase: new URL('https://docs.om.kl3in.tech'),
  title: {
    default: 'OrgMemory Documentation',
    template: '%s | OrgMemory Documentation',
  },
  description:
    'Architecture, deployment, administration, and integration guidance for OrgMemory.',
};

export default function Layout({ children }: LayoutProps<'/'>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="flex flex-col min-h-screen">
        <RootProvider>{children}</RootProvider>
      </body>
    </html>
  );
}
