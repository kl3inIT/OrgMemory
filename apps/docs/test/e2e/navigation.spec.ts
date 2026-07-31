import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

const authoredManifest = JSON.parse(
  fs.readFileSync(path.resolve('public-content.manifest.json'), 'utf8'),
) as { entries: { route: string }[] };
const generatedManifest = JSON.parse(
  fs.readFileSync(path.resolve('generated-api.manifest.json'), 'utf8'),
) as { entries: { route: string }[] };
const publicRoutes = [...authoredManifest.entries, ...generatedManifest.entries].map(
  (entry) => entry.route,
);

function machineReadableRoute(route: string): string {
  const localized = route.match(/^\/([a-z]{2})(\/docs(?:\/.*)?)$/);
  if (localized) {
    return `/${localized[1]}/llms.mdx${localized[2]}/content.md`;
  }
  return `/llms.mdx${route}/content.md`;
}

test('site root enters the technical documentation directly', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/docs\/getting-started$/);
  await expect(
    page.getByRole('heading', {
      level: 1,
      name: 'What is Organizational AI Memory?',
    }),
  ).toBeVisible();
  await expect(
    page.getByRole('link', { name: 'Organizational AI Memory', exact: true }).first(),
  ).toBeVisible();
});

test('public corpus exposes the section switcher and focused page tree', async ({
  page,
}, testInfo) => {
  await page.goto('/docs/architecture-security/system-description');

  await expect(
    page.getByRole('heading', { level: 1, name: 'System description' }),
  ).toBeVisible();
  await expect(
    page.getByRole('img', {
      name: /organizational ai memory architecture/i,
    }),
  ).toBeVisible();
  await expect(
    page.getByRole('img', {
      name: /organizational ai memory architecture/i,
    }),
  ).toHaveAttribute('src', '/images/architecture/system-overview.webp');
  await expect(page.getByText('Editorial preview')).toHaveCount(0);
  if (testInfo.project.name === 'mobile-chromium') {
    await page.getByRole('button', { name: 'Open Sidebar' }).click();
  }
  await page.getByRole('button', { name: /Architecture & Security/ }).first().click();
  for (const section of [
    'Getting Started',
    'Product Guides',
    'Architecture & Security',
    'Reference',
  ]) {
    await expect(page.getByText(section, { exact: true }).last()).toBeVisible();
  }
  if (testInfo.project.name === 'chromium') {
    await expect(page.getByText('On this page')).toBeVisible();
  }
});

test('category visual identity follows the active root and locale', async ({ page }) => {
  const categories = {
    'getting-started': '/docs/getting-started',
    'product-guides': '/docs/product-guides/work-with-governed-assets',
    'architecture-security': '/docs/architecture-security/system-description',
    reference: '/docs/reference/api-reference',
  } as const;
  const colors = new Map<string, string>();

  for (const [category, route] of Object.entries(categories)) {
    await page.goto(route);
    await expect(page.locator('body')).toHaveClass(new RegExp(`\\b${category}\\b`));
    const color = await page.locator('body').evaluate((body) =>
      getComputedStyle(body).getPropertyValue('--color-fd-primary').trim(),
    );
    expect(color, route).not.toBe('');
    colors.set(category, color);
  }

  expect(new Set(colors.values()).size).toBe(Object.keys(categories).length);

  await page.goto('/vi/docs/architecture-security/system-description');
  await expect(page.locator('body')).toHaveClass(/\barchitecture-security\b/);
  await expect(page.locator('body')).toHaveCSS(
    '--color-fd-primary',
    colors.get('architecture-security')!,
  );
});

test('Vietnamese shell and authored Getting Started pages are localized', async ({
  page,
}, testInfo) => {
  await page.goto('/vi/docs/getting-started');

  await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
  await expect(
    page.getByRole('heading', { level: 1, name: 'Organizational AI Memory là gì?' }),
  ).toBeVisible();
  await expect(page.getByText('chưa có bản dịch', { exact: false })).toHaveCount(0);

  await page.goto('/vi/docs/getting-started/core-concepts');
  await expect(
    page.getByRole('heading', { level: 1, name: 'Các khái niệm cốt lõi' }),
  ).toBeVisible();
  await expect(page.getByText('Không gian tri thức', { exact: true }).first()).toBeVisible();

  await page.goto('/vi/docs/getting-started');
  if (testInfo.project.name === 'mobile-chromium') {
    await page.getByRole('button', { name: 'Mở thanh bên' }).click();
  }

  await page.getByRole('button', { name: /Bắt đầu/ }).first().click();
  for (const section of [
    'Bắt đầu',
    'Hướng dẫn sản phẩm',
    'Kiến trúc & bảo mật',
    'Tham chiếu',
  ]) {
    await expect(page.getByText(section, { exact: true }).last()).toBeVisible();
  }
  await page.keyboard.press('Escape');

  await page.getByRole('button', { name: 'Chọn ngôn ngữ' }).first().click();
  await page.getByRole('button', { name: 'English', exact: true }).last().click();
  await expect(page).toHaveURL(/\/docs\/getting-started$/);
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
});

test('docs root redirects to Getting Started', async ({ page }) => {
  await page.goto('/docs');
  await expect(page).toHaveURL(/\/docs\/getting-started$/);
  await expect(
    page.getByRole('heading', {
      level: 1,
      name: 'What is Organizational AI Memory?',
    }),
  ).toBeVisible();
});

test('Getting Started explains the core model and first governed journey', async ({ page }) => {
  await page.goto('/docs/getting-started/core-concepts');
  await expect(page.getByRole('heading', { level: 1, name: 'Core concepts' })).toBeVisible();
  await expect(page.getByText('Knowledge Space', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('Registry Asset', { exact: true })).toHaveCount(0);

  await page.goto('/docs/getting-started/first-governed-journey');
  await expect(
    page.getByRole('heading', { level: 1, name: 'Your first governed journey' }),
  ).toBeVisible();
  await expect(
    page.getByRole('img', {
      name: 'Organizational AI Memory Assets catalog showing a Capability Pack, Work Instruction, Prompt Template, and Skill available to the current user.',
    }),
  ).toBeVisible();
  await expect(page.getByText('repository access', { exact: false })).toBeVisible();
});

test('Product Guides teaches the exact governed Asset workflow in both languages', async ({
  page,
}) => {
  await page.goto('/docs/product-guides/work-with-governed-assets');
  await expect(
    page.getByRole('heading', { level: 1, name: 'Work with governed Assets' }),
  ).toBeVisible();
  await expect(
    page.getByRole('img', {
      name: /Asset detail showing a Capability Pack, its current version/i,
    }),
  ).toHaveAttribute('src', '/images/product-guides/asset-release-provenance.png');
  await expect(page.getByText('Version and provenance', { exact: true }).first()).toBeVisible();

  await page.goto('/vi/docs/product-guides/work-with-governed-assets');
  await expect(
    page.getByRole('heading', { level: 1, name: 'Làm việc với Asset được quản trị' }),
  ).toBeVisible();
  await expect(
    page.getByRole('heading', { level: 3, name: 'Xác minh đúng bản phát hành' }),
  ).toBeVisible();
});

test('legacy docs URLs permanently redirect to the new taxonomy', async ({
  request,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium', 'Run the redirect contract once');

  const redirects = new Map([
    ['/docs/overview', '/docs/getting-started'],
    [
      '/vi/docs/product-guides',
      '/vi/docs/product-guides/work-with-governed-assets',
    ],
    [
      '/vi/docs/admins/identity-permissions',
      '/vi/docs/architecture-security/authorization',
    ],
    [
      '/docs/deployment/self-hosting.md',
      '/docs/architecture-security/system-description.md',
    ],
    [
      '/docs/getting-started/quickstart',
      '/docs/getting-started',
    ],
    [
      '/docs/guides/administration/identity-permissions',
      '/docs/architecture-security/authorization',
    ],
    [
      '/docs/developers/api-reference/search-catalog.md',
      '/docs/reference/api-reference/search-catalog.md',
    ],
  ]);

  for (const [source, destination] of redirects) {
    const response = await request.get(source, { maxRedirects: 0 });
    expect(response.status(), source).toBe(308);
    expect(response.headers().location, source).toBe(destination);
  }
});

test('keyboard navigation reaches the primary action', async ({ page }) => {
  await page.goto('/');
  let focusedHref: string | null = null;
  for (let attempt = 0; attempt < 8 && !focusedHref; attempt += 1) {
    await page.keyboard.press('Tab');
    focusedHref = await page.evaluate(() => {
      const activeElement = document.activeElement;
      return activeElement instanceof HTMLAnchorElement ? activeElement.getAttribute('href') : null;
    });
  }

  expect(focusedHref).toBeTruthy();
});

test('root and docs pages pass automated accessibility smoke checks', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });

  for (const route of [
    '/',
    '/docs/getting-started',
    '/vi/docs/getting-started',
    '/docs/getting-started/core-concepts',
    '/docs/getting-started/first-governed-journey',
    '/docs/product-guides/work-with-governed-assets',
    '/docs/architecture-security/system-description',
  ]) {
    await page.goto(route);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await page.evaluate(async () => {
      await new Promise<void>((resolve) => {
        requestAnimationFrame(() => requestAnimationFrame(() => resolve()));
      });
      for (const animation of document.getAnimations()) {
        animation.finish();
      }
    });
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
      .analyze();
    expect(results.violations, `Accessibility violations on ${route}`).toEqual([]);
  }
});

test('responses carry security headers and use explicit Markdown URLs', async ({ request }) => {
  for (const route of ['/docs/getting-started', '/vi/docs/getting-started']) {
    const response = await request.get(route, {
      headers: {
        Accept: 'text/html',
      },
    });

    expect(response.headers()['x-content-type-options']).toBe('nosniff');
    expect(response.headers()['x-frame-options']).toBe('DENY');
    expect(response.headers()['referrer-policy']).toBe('strict-origin-when-cross-origin');
    expect(response.headers()['permissions-policy']).toBe(
      'camera=(), microphone=(), geolocation=()',
    );
    expect(response.headers()['content-security-policy']).toContain("default-src 'self'");
    expect(response.headers()['strict-transport-security']).toBe(
      'max-age=31536000; includeSubDomains',
    );
    expect(response.headers()['x-powered-by']).toBeUndefined();
    expect(response.headers()['cache-control']).toBe('public, max-age=0, must-revalidate');

    const negotiated = await request.get(route, {
      headers: {
        Accept: 'text/markdown',
      },
    });
    expect(negotiated.headers()['content-type']).toContain('text/html');

    const markdown = await request.get(`${route}.md`);
    expect(markdown.headers()['content-type']).toContain('text/markdown');
  }
});

test('global changelog navigation is localized and renders Tegami history', async ({
  page,
}, testInfo) => {
  for (const localized of [
    { route: '/docs/getting-started', label: 'Changelog', href: '/docs/changelog' },
    {
      route: '/vi/docs/getting-started',
      label: 'Nhật ký thay đổi',
      href: '/vi/docs/changelog',
    },
  ]) {
    await page.goto(localized.route);
    if (testInfo.project.name === 'mobile-chromium') {
      await page
        .getByRole('button', {
          name: localized.route.startsWith('/vi/') ? 'Mở thanh bên' : 'Open Sidebar',
        })
        .click();
    }
    await page.getByRole('link', { name: localized.label, exact: true }).first().click();
    await expect(page).toHaveURL(new RegExp(`${localized.href}$`));
    await expect(page.getByRole('heading', { level: 2, name: 'orgmemory@0.1.0' })).toBeVisible();
    await expect(
      page.getByRole('heading', { level: 3, name: 'Product release management' }),
    ).toBeVisible();
  }
});

test('generated API reference renders with its playground disabled', async ({ page }) => {
  await page.goto('/docs/reference/api-reference/search-catalog');

  await expect(
    page.getByRole('heading', { level: 1, name: 'Search and catalog' }),
  ).toBeVisible();
  await expect(page.getByText('/api/knowledge/search', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: /send request/i })).toHaveCount(0);
});

test('server-side search discovers the key product vocabulary', async ({ request }, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium', 'Run the corpus contract once');

  const queries = [
    ...['Asset', 'OpenFGA', 'GraphRAG', 'MCP', 'connector'].flatMap((term) =>
      ['en', 'vi'].map((locale) => ({ term, locale })),
    ),
    { term: 'Không gian tri thức', locale: 'vi' },
    { term: 'bản phát hành', locale: 'vi' },
  ];

  for (const { term, locale } of queries) {
      const response = await request.get(
        `/api/search?query=${encodeURIComponent(term)}&locale=${locale}`,
      );
      expect(response.ok(), `Search request failed for ${term} in ${locale}`).toBeTruthy();
      const searchableResponse = (await response.text())
        .replaceAll(/<\/?mark>/g, '')
        .toLowerCase();
      expect(
        searchableResponse,
        `No search result for ${term} in ${locale}`,
      ).toContain(term.toLowerCase());
  }
});

test('every manifest route and machine-readable output is public-safe', async ({
  request,
}, testInfo) => {
  test.setTimeout(180_000);
  test.skip(testInfo.project.name !== 'chromium', 'Run the publication audit once');

  const forbidden = /sourceRefs|contracts\/openapi\.json|docs\/increments\/active\/|docs\/research\/|[A-Z]:\\(?:Users|OrgMemory|apps)\\/i;
  for (const route of publicRoutes) {
    const response = await request.get(route);
    expect(response.status(), route).toBe(200);
    expect(await response.text(), `${route} leaked repository evidence`).not.toMatch(forbidden);

    const markdownRoute = machineReadableRoute(route);
    const markdown = await request.get(markdownRoute);
    expect(markdown.status(), markdownRoute).toBe(200);
    expect(await markdown.text(), `${markdownRoute} leaked repository evidence`).not.toMatch(
      forbidden,
    );

    if (route.startsWith('/docs/')) {
      const vietnameseRoute = `/vi${route}`;
      const vietnamese = await request.get(vietnameseRoute);
      expect(vietnamese.status(), vietnameseRoute).toBe(200);
      expect(
        await vietnamese.text(),
        `${vietnameseRoute} leaked repository evidence`,
      ).not.toMatch(forbidden);

      const vietnameseMarkdownRoute = `/vi/llms.mdx${route}/content.md`;
      const vietnameseMarkdown = await request.get(vietnameseMarkdownRoute);
      expect(vietnameseMarkdown.status(), vietnameseMarkdownRoute).toBe(200);
      expect(
        await vietnameseMarkdown.text(),
        `${vietnameseMarkdownRoute} leaked repository evidence`,
      ).not.toMatch(forbidden);
    }
  }

  for (const route of ['/llms.txt', '/llms-full.txt', '/sitemap.xml', '/robots.txt']) {
    const response = await request.get(route);
    expect(response.status(), route).toBe(200);
    expect(await response.text(), `${route} leaked repository evidence`).not.toMatch(forbidden);
  }

  const sitemap = await (await request.get('/sitemap.xml')).text();
  for (const route of publicRoutes) {
    expect(sitemap, `Sitemap is missing ${route}`).toContain(route);
    if (route.startsWith('/docs/')) {
      expect(sitemap, `Sitemap is missing /vi${route}`).toContain(`/vi${route}`);
    }
  }
  expect(sitemap).toContain('hreflang="en"');
  expect(sitemap).toContain('hreflang="vi"');
});
