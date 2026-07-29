import { getLLMText, source } from '@/lib/source';
import { isDocsLanguage } from '@/lib/i18n';
import { notFound } from 'next/navigation';

export const revalidate = false;

export async function GET(
  _request: Request,
  { params }: RouteContext<'/[lang]/llms-full.txt'>,
) {
  const { lang } = await params;
  if (!isDocsLanguage(lang)) notFound();

  const scan = source.getPages(lang).map(getLLMText);
  const scanned = await Promise.all(scan);

  return new Response(scanned.join('\n\n'));
}
