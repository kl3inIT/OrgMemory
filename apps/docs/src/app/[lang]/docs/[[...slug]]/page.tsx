import {
  getPageImageUrl,
  getPageMarkdownUrl,
  isFallbackTranslation,
  source,
} from '@/lib/source';
import {
  DocsBody,
  DocsDescription,
  DocsPage,
  DocsTitle,
  MarkdownCopyButton,
  PageLastUpdate,
  ViewOptionsPopover,
} from 'fumadocs-ui/layouts/docs/page';
import { notFound, redirect } from 'next/navigation';
import { getMDXComponents } from '@/components/mdx';
import type { Metadata } from 'next';
import { createRelativeLink } from 'fumadocs-ui/mdx';
import { gitConfig } from '@/lib/shared';
import { openapi } from '@/lib/openapi';
import { OpenAPIPage } from '@/components/openapi-page';
import { docsHome, i18n, isDocsLanguage } from '@/lib/i18n';

export default async function Page(props: PageProps<'/[lang]/docs/[[...slug]]'>) {
  const params = await props.params;
  if (!isDocsLanguage(params.lang)) notFound();
  if (!params.slug || params.slug.length === 0) {
    redirect(docsHome(params.lang));
  }
  const page = source.getPage(params.slug, params.lang);
  if (!page) notFound();

  const MDX = page.data.body;
  const markdownUrl = getPageMarkdownUrl(page).url;

  return (
    <DocsPage
      role="main"
      toc={page.data.toc}
      full={page.data.full}
      tableOfContent={{ enabled: true }}
      tableOfContentPopover={{ enabled: true }}
      footer={{ enabled: true }}
    >
      <DocsTitle>{page.data.title}</DocsTitle>
      <DocsDescription className="mb-0">{page.data.description}</DocsDescription>
      {isFallbackTranslation(page, params.lang) ? (
        <div className="mb-2 rounded-lg border border-amber-500/35 bg-amber-500/10 px-4 py-3 text-sm text-fd-muted-foreground">
          Trang này chưa có bản dịch tiếng Việt đã được duyệt. Nội dung tiếng Anh đang được
          hiển thị tạm thời.
        </div>
      ) : null}
      <div className="flex flex-wrap items-center gap-2 border-b pb-6">
        {page.data.status === 'draft' ? (
          <span className="rounded-full border border-amber-500/40 bg-amber-500/10 px-2.5 py-1 text-xs font-medium text-amber-900 dark:text-amber-200">
            Editorial preview
          </span>
        ) : null}
        <MarkdownCopyButton markdownUrl={markdownUrl} />
        <ViewOptionsPopover
          markdownUrl={markdownUrl}
          githubUrl={`https://github.com/${gitConfig.user}/${gitConfig.repo}/blob/${gitConfig.branch}/apps/docs/content/docs/${page.path}`}
        />
      </div>
      <DocsBody>
        <MDX
          components={getMDXComponents({
            OpenAPIPage: async (openapiPageProps) => (
              <OpenAPIPage
                {...(await openapi.preloadOpenAPIPage(page))}
                {...openapiPageProps}
              />
            ),
            // this allows you to link to other pages with relative file paths
            a: createRelativeLink(source, page),
          })}
        />
      </DocsBody>
      <PageLastUpdate date={new Date(`${page.data.lastReviewed}T00:00:00Z`)} />
    </DocsPage>
  );
}

export async function generateStaticParams() {
  return source.generateParams();
}

export async function generateMetadata(
  props: PageProps<'/[lang]/docs/[[...slug]]'>,
): Promise<Metadata> {
  const params = await props.params;
  if (!isDocsLanguage(params.lang)) notFound();
  const slug = params.slug?.length ? params.slug : ['getting-started'];
  const page = source.getPage(slug, params.lang);
  if (!page) notFound();

  return {
    title: page.data.title,
    description: page.data.description,
    alternates: {
      canonical: page.url,
      languages: Object.fromEntries(
        i18n.languages.map((language) => [
          language,
          source.getPage(slug, language)?.url ?? page.url,
        ]),
      ),
    },
    openGraph: {
      title: page.data.title,
      description: page.data.description,
      url: page.url,
      type: 'article',
      images: getPageImageUrl(page).url,
    },
  };
}
