import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import {
  createProduct,
  deleteProduct,
  generateProductPayload,
  provisionProductDependencies,
} from '../helpers/product-fixtures';

import { createBrand, deleteBrand, generateBrandPayload } from '../helpers/brand-fixtures';
import { createCategory, deleteCategory, generateCategoryPayload } from '../helpers/category-fixtures';

test.describe('Products API - read', () => {
  test('should list active products with pagination', async ({ request }) => {
    const auth = await login(request);
    const created = await createProduct(request, auth.accessToken, generateProductPayload());

    const response = await request.get('/api/v1/products?onlyActive=true&page=0&size=50&sort=createdAt,desc', {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(Array.isArray(body.content)).toBeTruthy();
    expect(body.content.some((product: any) => product.id === created.id)).toBeTruthy();

    await deleteProduct(request, auth.accessToken, created.id);
  });

  test('should search products by name', async ({ request }) => {
    const auth = await login(request);
    const payload = generateProductPayload();
    const created = await createProduct(request, auth.accessToken, payload);

    const response = await request.get(
      `/api/v1/products/search?q=${encodeURIComponent(payload.name.slice(0, 6))}&page=0&size=50&sort=createdAt,desc`,
      {
        headers: authHeaders(auth.accessToken),
      },
    );

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(Array.isArray(body.content)).toBeTruthy();
    expect(body.content.some((product: any) => product.id === created.id)).toBeTruthy();

    await deleteProduct(request, auth.accessToken, created.id);
  });

  test('should filter products by brand', async ({ request }) => {
    const auth = await login(request);
    const brand = await createBrand(request, auth.accessToken, generateBrandPayload());
    const payload = generateProductPayload({ brandId: brand.id });
    const created = await createProduct(request, auth.accessToken, payload);

    const response = await request.get(`/api/v1/products/brand/${brand.id}?page=0&size=10`, {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(Array.isArray(body.content)).toBeTruthy();
    expect(body.content.some((product: any) => product.id === created.id)).toBeTruthy();

    await deleteProduct(request, auth.accessToken, created.id);
    await deleteBrand(request, auth.accessToken, brand.id);
  });

  test('should filter products by category', async ({ request }) => {
    const auth = await login(request);
    const category = await createCategory(request, auth.accessToken, generateCategoryPayload());
    const payload = generateProductPayload({ categoryId: category.id });
    const created = await createProduct(request, auth.accessToken, payload);

    const response = await request.get(`/api/v1/products/category/${category.id}?page=0&size=10`, {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(Array.isArray(body.content)).toBeTruthy();
    expect(body.content.some((product: any) => product.id === created.id)).toBeTruthy();

    await deleteProduct(request, auth.accessToken, created.id);
    await deleteCategory(request, auth.accessToken, category.id);
  });
});
