import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import { createBrand, deleteBrand, generateBrandPayload } from '../helpers/brand-fixtures';

test.describe('Brands API - creation', () => {
  test('should create a new brand successfully', async ({ request }) => {
    const auth = await login(request);
    const payload = generateBrandPayload();

    const created = await createBrand(request, auth.accessToken, payload);

    expect(created).toMatchObject({
      name: payload.name,
      description: payload.description,
      active: true,
    });

    await deleteBrand(request, auth.accessToken, created.id);
  });

  test('should reject duplicate brand names', async ({ request }) => {
    const auth = await login(request);
    const payload = generateBrandPayload();
    const created = await createBrand(request, auth.accessToken, payload);

    const duplicate = await request.post('/api/v1/brands', {
      headers: authHeaders(auth.accessToken),
      data: payload,
    });

    expect(duplicate.status()).toBe(409);

    await deleteBrand(request, auth.accessToken, created.id);
  });
});
