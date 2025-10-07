import { APIRequestContext, expect } from '@playwright/test';
import { authHeaders } from './auth';

export interface CategoryPayload {
  name: string;
  description?: string;
  parentId?: string;
}

export function generateCategoryPayload(overrides: Partial<CategoryPayload> = {}): CategoryPayload {
  const suffix = Math.random().toString(36).slice(2, 8);
  const base: CategoryPayload = {
    name: `E2E Category ${suffix}`,
    description: `E2E category description ${suffix}`,
  };

  return { ...base, ...overrides };
}

export async function createCategory(request: APIRequestContext, token: string, payload: CategoryPayload) {
  const response = await request.post('/api/v1/categories', {
    headers: authHeaders(token),
    data: payload,
  });

  expect(response.status()).toBe(201);
  return response.json();
}

export async function deleteCategory(request: APIRequestContext, token: string, categoryId: string) {
  const response = await request.delete(`/api/v1/categories/${categoryId}`, {
    headers: authHeaders(token),
  });

  expect(response.status()).toBe(204);
}
