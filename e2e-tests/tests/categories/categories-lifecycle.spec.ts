import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import { createCategory, generateCategoryPayload } from '../helpers/category-fixtures';

test.describe('Categories API - lifecycle', () => {
  test('should deactivate and reactivate a category', async ({ request }) => {
    const auth = await login(request);
    const created = await createCategory(request, auth.accessToken, generateCategoryPayload());

    const deleteResponse = await request.delete(`/api/v1/categories/${created.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(deleteResponse.status()).toBe(204);

    const afterDelete = await request.get(`/api/v1/categories/${created.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(afterDelete.ok()).toBeTruthy();
    const afterDeleteBody = await afterDelete.json();
    expect(afterDeleteBody.active).toBe(false);

    const reactivateResponse = await request.patch(`/api/v1/categories/${created.id}/activate`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(reactivateResponse.ok()).toBeTruthy();
    const reactivated = await reactivateResponse.json();
    expect(reactivated.active).toBe(true);

    const cleanup = await request.delete(`/api/v1/categories/${created.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(cleanup.status()).toBe(204);
  });
});
