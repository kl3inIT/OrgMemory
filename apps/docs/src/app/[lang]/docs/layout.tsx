import { source } from '@/lib/source';
import { DocsLayout } from 'fumadocs-ui/layouts/docs';
import { baseOptions } from '@/lib/layout.shared';
import { isDocsLanguage } from '@/lib/i18n';
import { notFound } from 'next/navigation';

export default async function Layout({
  children,
  params,
}: LayoutProps<'/[lang]/docs'>) {
  const { lang } = await params;
  if (!isDocsLanguage(lang)) notFound();

  return (
    <DocsLayout
      tree={source.getPageTree(lang)}
      sidebar={{
        collapsible: true,
        defaultOpenLevel: 1,
      }}
      {...baseOptions(lang)}
    >
      {children}
    </DocsLayout>
  );
}
