import { expect, test } from '@playwright/test';

test('real tenant session opens the tenant-bound work hub', async ({ page, request }) => {
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const tenantKey = `browser-${suffix}`;
  const ownerEmail = `owner-${suffix}@example.com`;
  const password = 'StrongPassword1!';

  const csrf = await request.get('/api/v1/auth/csrf');
  expect(csrf.ok()).toBeTruthy();
  const csrfBody = await csrf.json() as { token: string };
  const onboarding = await request.post('/api/v1/onboarding/tenants', {
    headers: { 'X-XSRF-TOKEN': csrfBody.token },
    data: {
      tenantKey,
      legalName: `Browser E2E ${suffix} LLC`,
      displayName: `Browser E2E ${suffix}`,
      timezone: 'Asia/Kolkata',
      ownerEmail,
      ownerDisplayName: 'Browser Owner',
      ownerPassword: password,
      enabledFeatures: ['PROJECTS', 'WORK_MANAGEMENT', 'TIME_TRACKING'],
    },
  });
  expect(onboarding.status()).toBe(201);

  const login = await request.post('/api/v1/auth/login', {
    headers: { 'X-XSRF-TOKEN': csrfBody.token },
    data: { tenantKey, email: ownerEmail, password },
  });
  expect(login.status()).toBe(200);
  const sessionState = await request.storageState();
  await page.context().addCookies(sessionState.cookies);
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Good morning, Browser Owner' })).toBeVisible();
  const sessionCookie = (await page.context().cookies()).find((cookie) => cookie.name === 'company_os_session');
  expect(sessionCookie?.httpOnly).toBeTruthy();
  expect(sessionCookie?.secure).toBeFalsy();

  await page.getByRole('button', { name: 'Open task' }).click();
  await expect(page.getByRole('heading', { name: 'My Work' })).toBeVisible();
  await expect(page.getByText('No assigned work yet.')).toBeVisible();
});
