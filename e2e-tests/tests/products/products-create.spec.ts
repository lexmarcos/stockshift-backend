import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import {
  createProduct,
  deleteProduct,
  generateProductPayload,
  provisionProductDependencies,
} from '../helpers/product-fixtures';

test.describe('Products API - creation', () => {
  test('should create a product without associations', async ({ request }) => {
    const auth = await login(request);
    const payload = generateProductPayload();

    const created = await createProduct(request, auth.accessToken, payload);

    expect(created).toMatchObject({
      name: payload.name,
      description: payload.description,
      basePrice: payload.basePrice,
      brandId: null,
      categoryId: null,
      active: true,
    });

    await deleteProduct(request, auth.accessToken, created.id);
  });

  test('should create a product linked to brand and category', async ({ request }) => {
    const auth = await login(request);
    const deps = await provisionProductDependencies(request, auth.accessToken, {
      withBrand: true,
      withCategory: true,
    });

    const payload = generateProductPayload({
      brandId: deps.brandId,
      categoryId: deps.categoryId,
    });

    const created = await createProduct(request, auth.accessToken, payload);

    expect(created).toMatchObject({
      name: payload.name,
      brandId: deps.brandId,
      categoryId: deps.categoryId,
      active: true,
    });

    await deleteProduct(request, auth.accessToken, created.id);
    await deps.cleanup();
  });

  test('should reject duplicate product name', async ({ request }) => {
    const auth = await login(request);
    const payload = generateProductPayload();
    const created = await createProduct(request, auth.accessToken, payload);

    const duplicate = await request.post('/api/v1/products', {
      headers: authHeaders(auth.accessToken),
      data: payload,
    });

    expect(duplicate.status()).toBe(409);

    await deleteProduct(request, auth.accessToken, created.id);
  });
});
