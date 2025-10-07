import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import {
  createProduct,
  deleteProduct,
  generateProductPayload,
} from '../helpers/product-fixtures';

test.describe('Products API - lifecycle', () => {
  test('should deactivate and reactivate a product', async ({ request }) => {
    const auth = await login(request);
    const created = await createProduct(request, auth.accessToken, generateProductPayload());

    const deleteResponse = await request.delete(`/api/v1/products/${created.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(deleteResponse.status()).toBe(204);

    const afterDelete = await request.get(`/api/v1/products/${created.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(afterDelete.ok()).toBeTruthy();
    const afterDeleteBody = await afterDelete.json();
    expect(afterDeleteBody.active).toBe(false);

    const reactivateResponse = await request.patch(`/api/v1/products/${created.id}/activate`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(reactivateResponse.ok()).toBeTruthy();
    const reactivated = await reactivateResponse.json();
    expect(reactivated.active).toBe(true);

    const cleanup = await request.delete(`/api/v1/products/${created.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(cleanup.status()).toBe(204);
  });
});
