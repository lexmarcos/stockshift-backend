import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import { createBrand, generateBrandPayload } from '../helpers/brand-fixtures';

test.describe('Brands API - lifecycle', () => {
  test('should deactivate and reactivate a brand', async ({ request }) => {
    const auth = await login(request);
    const payload = generateBrandPayload();
    const created = await createBrand(request, auth.accessToken, payload);

    const deleteResponse = await request.delete(`/api/v1/brands/${created.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(deleteResponse.status()).toBe(204);

    const afterDelete = await request.get(`/api/v1/brands/${created.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(afterDelete.ok()).toBeTruthy();
    const afterDeleteBody = await afterDelete.json();
    expect(afterDeleteBody.active).toBe(false);

    const reactivateResponse = await request.patch(`/api/v1/brands/${created.id}/activate`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(reactivateResponse.ok()).toBeTruthy();
    const reactivated = await reactivateResponse.json();
    expect(reactivated.active).toBe(true);

    const cleanup = await request.delete(`/api/v1/brands/${created.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(cleanup.status()).toBe(204);
  });
});
