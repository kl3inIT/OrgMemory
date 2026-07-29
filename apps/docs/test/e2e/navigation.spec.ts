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

test('site root enters the technical documentation directly', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/docs\/overview$/);
  await expect(
    page.getByRole('heading', { level: 1, name: 'Welcome to OrgMemory' }),
  ).toBeVisible();
});

test('public corpus exposes the complete audience-oriented page tree', async ({
  page,
}, testInfo) => {
  await page.goto('/docs/architecture-security/system-description');

  await expect(
    page.getByRole('heading', { level: 1, name: 'System description' }),
  ).toBeVisible();
  await expect(
    page.getByRole('img', { name: /high-level orgmemory architecture/i }),
  ).toBeVisible();
  await expect(
    page.getByRole('img', { name: /high-level orgmemory architecture/i }),
  ).toHaveAttribute('src', '/images/architecture/system-overview.webp');
  await expect(page.getByText('Editorial preview')).toHaveCount(0);
  if (testInfo.project.name === 'mobile-chromium') {
    await page.getByRole('button', { name: 'Open Sidebar' }).click();
  }
  await expect(page.getByRole('link', { name: 'Overview', exact: true }).first()).toBeVisible();
  await expect(
    page.getByRole('link', { name: 'Architecture & Security', exact: true }).first(),
  ).toBeVisible();
  if (testInfo.project.name === 'chromium') {
    await expect(page.getByText('On this page')).toBeVisible();
  }
});

test('docs root redirects to the published overview', async ({ page }) => {
  await page.goto('/docs');
  await expect(page).toHaveURL(/\/docs\/overview$/);
  await expect(
    page.getByRole('heading', { level: 1, name: 'Welcome to OrgMemory' }),
  ).toBeVisible();
});

test('quickstart exposes executable commands and observable health', async ({ page }) => {
  await page.goto('/docs/overview/quickstart');
  await expect(
    page.getByRole('heading', { level: 1, name: 'Quickstart and POC demo' }),
  ).toBeVisible();
  await expect(page.getByText('.\\gradlew.bat demoBootstrap', { exact: false })).toBeVisible();
  await expect(page.getByText('http://localhost:8080/api/health')).toBeVisible();
});

test('keyboard navigation reaches the primary action', async ({ page }) => {
  await page.goto('/');
  await page.keyboard.press('Tab');

  const focused = page.locator(':focus');
  await expect(focused).toBeVisible();
  await expect(focused).toHaveAttribute('href');
});

test('root and docs pages pass automated accessibility smoke checks', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });

  for (const route of ['/', '/docs/overview', '/docs/architecture-security/system-description']) {
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

test('responses carry the application-owned security headers', async ({ request }) => {
  const response = await request.get('/docs/overview');

  expect(response.headers()['x-content-type-options']).toBe('nosniff');
  expect(response.headers()['x-frame-options']).toBe('DENY');
  expect(response.headers()['referrer-policy']).toBe('strict-origin-when-cross-origin');
  expect(response.headers()['permissions-policy']).toBe(
    'camera=(), microphone=(), geolocation=()',
  );
});

test('generated API reference renders with its playground disabled', async ({ page }) => {
  await page.goto('/docs/developers/api-reference/search-catalog');

  await expect(
    page.getByRole('heading', { level: 1, name: 'Search and catalog' }),
  ).toBeVisible();
  await expect(page.getByText('/api/knowledge/search', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: /send request/i })).toHaveCount(0);
});

test('server-side search discovers the key product vocabulary', async ({ request }, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium', 'Run the corpus contract once');

  for (const term of ['Asset', 'OpenFGA', 'GraphRAG', 'MCP', 'connector']) {
    const response = await request.get(`/api/search?query=${encodeURIComponent(term)}`);
    expect(response.ok(), `Search request failed for ${term}`).toBeTruthy();
    expect((await response.text()).toLowerCase(), `No search result for ${term}`).toContain(
      term.toLowerCase(),
    );
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

    const markdownRoute = `/llms.mdx${route}/content.md`;
    const markdown = await request.get(markdownRoute);
    expect(markdown.status(), markdownRoute).toBe(200);
    expect(await markdown.text(), `${markdownRoute} leaked repository evidence`).not.toMatch(
      forbidden,
    );
  }

  for (const route of ['/llms.txt', '/llms-full.txt', '/sitemap.xml', '/robots.txt']) {
    const response = await request.get(route);
    expect(response.status(), route).toBe(200);
    expect(await response.text(), `${route} leaked repository evidence`).not.toMatch(forbidden);
  }

  const sitemap = await (await request.get('/sitemap.xml')).text();
  for (const route of publicRoutes) {
    expect(sitemap, `Sitemap is missing ${route}`).toContain(route);
  }
});
