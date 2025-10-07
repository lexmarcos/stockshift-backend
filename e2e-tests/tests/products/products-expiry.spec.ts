import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import {
  createProduct,
  deleteProduct,
  generateProductPayload,
  updateProduct,
} from '../helpers/product-fixtures';

test.describe('Products API - expiry monitoring', () => {
  test('should list products expiring soon within configured window', async ({ request }) => {
    const auth = await login(request);
    const expiryDate = new Date();
    expiryDate.setDate(expiryDate.getDate() + 5);
    const payload = generateProductPayload({
      expiryDate: expiryDate.toISOString().split('T')[0],
    });

    const created = await createProduct(request, auth.accessToken, payload);

    const response = await request.get('/api/v1/products/expiring-soon?days=10&page=0&size=50&sort=updatedAt,desc', {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(Array.isArray(body.content)).toBeTruthy();
    expect(body.content.some((product: any) => product.id === created.id)).toBeTruthy();

    await deleteProduct(request, auth.accessToken, created.id);
  });

  test('should list expired products', async ({ request }) => {
    const auth = await login(request);
    const futureExpiry = new Date();
    futureExpiry.setDate(futureExpiry.getDate() + 5);
    const created = await createProduct(request, auth.accessToken, generateProductPayload({
      expiryDate: futureExpiry.toISOString().split('T')[0],
    }));

    const pastExpiry = new Date();
    pastExpiry.setDate(pastExpiry.getDate() - 2); // Use -2 days to avoid timezone boundary issues
    await updateProduct(request, auth.accessToken, created.id, {
      expiryDate: pastExpiry.toISOString().split('T')[0],
    });

    const response = await request.get('/api/v1/products/expired?page=0&size=50&sort=updatedAt,desc', {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(Array.isArray(body.content)).toBeTruthy();
    expect(body.content.some((product: any) => product.id === created.id)).toBeTruthy();

    const byId = await request.get(`/api/v1/products/${created.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(byId.ok()).toBeTruthy();
    const byIdBody = await byId.json();
    expect(byIdBody.expired).toBe(true);

    await deleteProduct(request, auth.accessToken, created.id);
  });
});
