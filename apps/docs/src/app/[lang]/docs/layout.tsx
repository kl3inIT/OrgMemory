import { source } from '@/lib/source';
import { DocsLayout } from 'fumadocs-ui/layouts/docs';
import { baseOptions } from '@/lib/layout.shared';
import { isDocsLanguage } from '@/lib/i18n';
import {
  getDocsCategory,
  getDocsCategoryColor,
} from '@/lib/docs-category';
import { notFound } from 'next/navigation';
import type { CSSProperties } from 'react';

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
      tabs={{
        transform(option, node) {
          const meta = source.getNodeMeta(node);
          if (!meta || !node.icon) return option;

          const category = getDocsCategory(meta.path);
          return {
            ...option,
            icon: (
              <div
                className="size-full rounded-lg text-(--docs-tab-color) max-md:border max-md:bg-(--docs-tab-color)/10 max-md:p-1.5 [&_svg]:size-full"
                style={
                  {
                    '--docs-tab-color': getDocsCategoryColor(category),
                  } as CSSProperties
                }
              >
                {node.icon}
              </div>
            ),
          };
        },
      }}
      {...baseOptions(lang)}
    >
      {children}
    </DocsLayout>
  );
}
