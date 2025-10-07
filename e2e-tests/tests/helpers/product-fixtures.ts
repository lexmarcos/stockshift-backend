import { APIRequestContext, expect } from '@playwright/test';
import { authHeaders } from './auth';
import { createBrand, deleteBrand, generateBrandPayload } from './brand-fixtures';
import { createCategory, deleteCategory, generateCategoryPayload } from './category-fixtures';

export interface ProductPayload {
  name: string;
  description?: string;
  basePrice: number;
  brandId?: string;
  categoryId?: string;
  expiryDate?: string;
}

export interface ProductContext {
  payload: ProductPayload;
  cleanup: () => Promise<void>;
}

export function generateProductPayload(overrides: Partial<ProductPayload> = {}): ProductPayload {
  const suffix = Math.random().toString(36).slice(2, 8);
  const base: ProductPayload = {
    name: `E2E Product ${suffix}`,
    description: `E2E product description ${suffix}`,
    basePrice: 1000,
  };

  return { ...base, ...overrides };
}

export async function provisionProductDependencies(
  request: APIRequestContext,
  token: string,
  options: { withBrand?: boolean; withCategory?: boolean } = {},
): Promise<{ brandId?: string; categoryId?: string; cleanup: () => Promise<void> }> {
  const cleanups: Array<() => Promise<void>> = [];
  let brandId: string | undefined;
  let categoryId: string | undefined;

  if (options.withBrand) {
    const brand = await createBrand(request, token, generateBrandPayload());
    brandId = brand.id;
    cleanups.push(async () => deleteBrand(request, token, brand.id));
  }

  if (options.withCategory) {
    const category = await createCategory(request, token, generateCategoryPayload());
    categoryId = category.id;
    cleanups.push(async () => deleteCategory(request, token, category.id));
  }

  const cleanup = async () => {
    for (const action of cleanups.reverse()) {
      await action();
    }
  };

  return { brandId, categoryId, cleanup };
}

export async function createProduct(
  request: APIRequestContext,
  token: string,
  payload: ProductPayload,
) {
  const response = await request.post('/api/v1/products', {
    headers: authHeaders(token),
    data: payload,
  });

  expect(response.status()).toBe(201);
  return response.json();
}

export async function updateProduct(
  request: APIRequestContext,
  token: string,
  productId: string,
  payload: Partial<ProductPayload>,
) {
  const response = await request.put(`/api/v1/products/${productId}`, {
    headers: authHeaders(token),
    data: payload,
  });

  expect(response.ok()).toBeTruthy();
  return response.json();
}

export async function deleteProduct(
  request: APIRequestContext,
  token: string,
  productId: string,
) {
  const response = await request.delete(`/api/v1/products/${productId}`, {
    headers: authHeaders(token),
  });

  expect(response.status()).toBe(204);
}
