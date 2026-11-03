import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test('employee can move from home to task details and task-linked time entry', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Good morning, Rahul' })).toBeVisible();
  await page.getByRole('button', { name: 'Open task' }).click();
  await expect(page.getByRole('heading', { name: 'My Work' })).toBeVisible();

  await page.getByRole('button', { name: 'Finalize checkout API contract' }).click();
  await expect(page.getByRole('heading', { name: 'Finalize checkout API contract' })).toBeVisible();
  await page.getByRole('button', { name: 'Log time on this task' }).click();

  await expect(page.getByLabel('Work item')).toHaveValue('demo-1');
  await expect(page.getByLabel('Description')).toHaveAttribute('required', '');
});

test('manager can filter workload to members needing attention', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'My Team' }).first().click();

  await expect(page.getByRole('heading', { name: 'My Team' })).toBeVisible();
  await expect(page.getByText('Ananya Nair')).toBeVisible();
  await page.getByRole('button', { name: /Needs attention/ }).click();

  await expect(page.getByText('Ananya Nair')).toBeVisible();
  await expect(page.getByText('Vikram Kumar')).toBeHidden();
  await expect(page.getByText('Sara Patel')).toBeHidden();
});

test('sprint workspace keeps work scoped to the selected sprint', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Sprints' }).first().click();
  await page.getByRole('button', { name: 'Open workspace' }).click();

  await expect(page.getByRole('heading', { name: 'Checkout Sprint 12' })).toBeVisible();
  await expect(page.getByText('Resolve tax calculation dependency')).toBeVisible();
  await page.getByRole('button', { name: 'Log time' }).first().click();
  await expect(page.getByRole('heading', { name: 'Log time without leaving the sprint' })).toBeVisible();
  await expect(page.getByLabel('Work item')).toHaveValue('demo-checkout-api');
  await page.getByRole('button', { name: 'Cancel' }).click();
  await page.getByRole('button', { name: 'Blocked 1' }).click();
  await expect(page.getByText('Resolve tax calculation dependency')).toBeVisible();
  await expect(page.getByText('Complete checkout API contract')).toBeHidden();
});

test('navigation remains usable at desktop and mobile widths', async ({ page }) => {
  await page.goto('/');
  const viewport = page.viewportSize();
  const mobileNavigation = page.locator('.mobile-nav');

  if (viewport && viewport.width <= 760) {
    await expect(mobileNavigation).toBeVisible();
    await expect(mobileNavigation.getByRole('button', { name: 'My Work' })).toBeVisible();
  } else {
    await expect(mobileNavigation).toBeHidden();
    await expect(page.locator('.sidebar')).toBeVisible();
  }
});

test('home page has no automatically detectable accessibility violations', async ({ page }) => {
  await page.goto('/');
  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations).toEqual([]);
});

test('home page visual baseline is stable', async ({ page }, testInfo) => {
  await page.goto('/');
  await expect(page).toHaveScreenshot(`home-${testInfo.project.name}.png`, {
    fullPage: true,
    animations: 'disabled',
  });
});
