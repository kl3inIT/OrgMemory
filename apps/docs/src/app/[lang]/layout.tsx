import { RootProvider } from 'fumadocs-ui/provider/next';
import { i18nProvider } from 'fumadocs-ui/i18n';
import '../global.css';
import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { siteUrl } from '@/lib/site';
import {
  docsHome,
  i18n,
  isDocsLanguage,
  translations,
  type DocsLanguage,
} from '@/lib/i18n';

const descriptions: Record<DocsLanguage, string> = {
  en: 'Architecture, deployment, administration, and integration guidance for OrgMemory.',
  vi: 'Tài liệu kiến trúc, triển khai, quản trị và tích hợp OrgMemory.',
};

export function generateStaticParams() {
  return i18n.languages.map((lang) => ({ lang }));
}

export async function generateMetadata({
  params,
}: LayoutProps<'/[lang]'>): Promise<Metadata> {
  const { lang } = await params;
  if (!isDocsLanguage(lang)) notFound();

  return {
    metadataBase: new URL(siteUrl),
    title: {
      default: 'OrgMemory Documentation',
      template: '%s | OrgMemory Documentation',
    },
    description: descriptions[lang],
    alternates: {
      canonical: docsHome(lang),
      languages: {
        en: docsHome('en'),
        vi: docsHome('vi'),
      },
    },
    openGraph: {
      title: 'OrgMemory Documentation',
      description: descriptions[lang],
      url: docsHome(lang),
      siteName: 'OrgMemory Documentation',
      type: 'website',
    },
  };
}

export default async function Layout({
  children,
  params,
}: LayoutProps<'/[lang]'>) {
  const { lang } = await params;
  if (!isDocsLanguage(lang)) notFound();

  return (
    <html lang={lang} suppressHydrationWarning>
      <body className="flex min-h-screen flex-col">
        <RootProvider i18n={i18nProvider(translations, lang)}>{children}</RootProvider>
      </body>
    </html>
  );
}
