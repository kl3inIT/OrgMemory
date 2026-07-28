import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

test('home routes readers to quickstart and product', async ({ page }) => {
  await page.goto('/');

  await expect(
    page.getByRole('heading', {
      level: 1,
      name: /secure context for people and ai/i,
    }),
  ).toBeVisible();
  await expect(page.getByRole('link', { name: /start the quickstart/i })).toHaveAttribute(
    'href',
    '/docs/overview/quickstart',
  );
  await expect(page.getByRole('link', { name: /open the product/i })).toHaveAttribute(
    'href',
    'https://om.kl3in.tech',
  );
  await expect(
    page.getByAltText(/documents pass through a permission boundary/i),
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

test('home and docs page pass automated accessibility smoke checks', async ({ page }) => {
  for (const route of ['/', '/docs/overview', '/docs/architecture-security/system-description']) {
    await page.goto(route);
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
      .analyze();
    expect(results.violations, `Accessibility violations on ${route}`).toEqual([]);
  }
});
