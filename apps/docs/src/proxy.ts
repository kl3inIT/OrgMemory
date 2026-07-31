import { NextFetchEvent, NextRequest, NextResponse } from 'next/server';
import { rewritePath } from 'fumadocs-core/negotiation';
import { docsContentRoute, docsRoute } from '@/lib/shared';
import { docsHome, i18n, isDocsLanguage } from '@/lib/i18n';

const { rewrite: rewriteSuffix } = rewritePath(
  `${docsRoute}{/*path}.md`,
  `${docsContentRoute}{/*path}/content.md`,
);
const internalLocaleHeader = 'x-orgmemory-docs-locale-rewrite';
const mutableDocsCacheControl = 'public, max-age=0, must-revalidate';

function withDocsCacheControl(response: NextResponse, pathname: string) {
  if (/^\/(?:en\/|vi\/)?docs(?:\/|$)/.test(pathname)) {
    response.headers.set('Cache-Control', mutableDocsCacheControl);
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

  return withDocsCacheControl(
    NextResponse.rewrite(new URL(`/${language}${pathname}`, request.nextUrl), {
      request: {
        headers: requestHeaders,
      },
    }),
    pathname,
  );
}

export default function proxy(request: NextRequest, _event: NextFetchEvent) {
  if (request.headers.has(internalLocaleHeader)) {
    return withDocsCacheControl(NextResponse.next(), request.nextUrl.pathname);
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

  if (
    request.nextUrl.pathname === `/${localized.language}` ||
    request.nextUrl.pathname.startsWith(`/${localized.language}/`)
  ) {
    return withDocsCacheControl(NextResponse.next(), request.nextUrl.pathname);
  }

  return rewriteLocalized(request, localized.language, localized.pathname);
}

export const config = {
  matcher: [
    '/((?!api|healthz|_next/static|_next/image|images|favicon.ico|icon.svg|robots.txt|sitemap.xml).*)',
  ],
};
