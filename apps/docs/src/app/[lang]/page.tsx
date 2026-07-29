import { redirect } from 'next/navigation';
import { docsHome, isDocsLanguage } from '@/lib/i18n';

export default async function HomePage({ params }: PageProps<'/[lang]'>) {
  const { lang } = await params;
  if (!isDocsLanguage(lang)) redirect(docsHome('en'));

  redirect(docsHome(lang));
}
