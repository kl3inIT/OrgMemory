import { NextFetchEvent, NextRequest, NextResponse } from 'next/server';
import { isMarkdownPreferred, rewritePath } from 'fumadocs-core/negotiation';
import { docsContentRoute, docsRoute } from '@/lib/shared';
import { docsHome, i18n, isDocsLanguage } from '@/lib/i18n';

const { rewrite: rewriteDocs } = rewritePath(
  `${docsRoute}{/*path}`,
  `${docsContentRoute}{/*path}/content.md`,
);
const { rewrite: rewriteSuffix } = rewritePath(
  `${docsRoute}{/*path}.md`,
  `${docsContentRoute}{/*path}/content.md`,
);
const internalLocaleHeader = 'x-orgmemory-docs-locale-rewrite';

function applyNegotiationBoundary(response: NextResponse) {
  const vary = response.headers.get('Vary');
  const fields = vary
    ?.split(',')
    .map((field) => field.trim().toLowerCase())
    .filter(Boolean);

  if (!fields?.includes('accept')) {
    response.headers.set('Vary', vary ? `${vary}, Accept` : 'Accept');
  }
  return response;
}

function parseLocale(pathname: string) {
  const segments = pathname.split('/').filter(Boolean);
  const candidate = segments[0];

  if (candidate && isDocsLanguage(candidate)) {
    return {
      language: candidate,
      pathname: '/' + segments.slice(1).join('/'),
    };
  }

  return {
    language: i18n.defaultLanguage,
    pathname,
  };
}

function rewriteLocalized(request: NextRequest, language: string, pathname: string) {
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set(internalLocaleHeader, language);

  return applyNegotiationBoundary(
    NextResponse.rewrite(new URL(`/${language}${pathname}`, request.nextUrl), {
      request: {
        headers: requestHeaders,
      },
    }),
  );
}

export default function proxy(request: NextRequest, _event: NextFetchEvent) {
  if (request.headers.has(internalLocaleHeader)) {
    return applyNegotiationBoundary(NextResponse.next());
  }

  if (request.nextUrl.pathname === '/') {
    return NextResponse.redirect(new URL(docsHome('en'), request.nextUrl));
  }
  for (const language of i18n.languages) {
    if (
      request.nextUrl.pathname === `/${language}` ||
      request.nextUrl.pathname === `/${language}/`
    ) {
      return NextResponse.redirect(new URL(docsHome(language), request.nextUrl));
    }
  }

  if (
    request.nextUrl.pathname === `/${i18n.defaultLanguage}` ||
    request.nextUrl.pathname.startsWith(`/${i18n.defaultLanguage}/`)
  ) {
    const pathname = request.nextUrl.pathname.slice(i18n.defaultLanguage.length + 1) || '/';
    return NextResponse.redirect(new URL(pathname, request.nextUrl));
  }

  const localized = parseLocale(request.nextUrl.pathname);
  const result = rewriteSuffix(localized.pathname);
  if (result) {
    return rewriteLocalized(request, localized.language, result);
  }

  if (isMarkdownPreferred(request)) {
    const result = rewriteDocs(localized.pathname);

    if (result) {
      return rewriteLocalized(request, localized.language, result);
    }
  }

  if (
    request.nextUrl.pathname === `/${localized.language}` ||
    request.nextUrl.pathname.startsWith(`/${localized.language}/`)
  ) {
    return applyNegotiationBoundary(NextResponse.next());
  }

  return rewriteLocalized(request, localized.language, localized.pathname);
}

export const config = {
  matcher: [
    '/((?!api|healthz|_next/static|_next/image|images|favicon.ico|icon.svg|robots.txt|sitemap.xml).*)',
  ],
};
