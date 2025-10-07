import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import { createBrand, deleteBrand, generateBrandPayload } from '../helpers/brand-fixtures';

test.describe('Brands API - read', () => {
  test('should list active brands with pagination payload', async ({ request }) => {
    const auth = await login(request);
    const payload = generateBrandPayload();
    const created = await createBrand(request, auth.accessToken, payload);

    const response = await request.get('/api/v1/brands?onlyActive=true&page=0&size=50&sort=createdAt,desc', {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();

    expect(Array.isArray(body.content)).toBeTruthy();
    expect(body).toHaveProperty('totalElements');
    expect(body).toHaveProperty('totalPages');
    expect(body.content.some((brand: any) => brand.id === created.id)).toBeTruthy();

    await deleteBrand(request, auth.accessToken, created.id);
  });

  test('should fetch a brand by name', async ({ request }) => {
    const auth = await login(request);
    const payload = generateBrandPayload();
    const created = await createBrand(request, auth.accessToken, payload);

    const response = await request.get(`/api/v1/brands/name/${encodeURIComponent(payload.name)}`, {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(body).toMatchObject({
      id: created.id,
      name: payload.name,
      description: payload.description,
    });

    await deleteBrand(request, auth.accessToken, created.id);
  });
});
