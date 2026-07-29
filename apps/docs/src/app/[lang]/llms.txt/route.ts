import { source } from '@/lib/source';
import { llms } from 'fumadocs-core/source';
import { isDocsLanguage } from '@/lib/i18n';
import { notFound } from 'next/navigation';

export const revalidate = false;

export async function GET(
  _request: Request,
  { params }: RouteContext<'/[lang]/llms.txt'>,
) {
  const { lang } = await params;
  if (!isDocsLanguage(lang)) notFound();

  return new Response(llms(source).index(lang));
}
