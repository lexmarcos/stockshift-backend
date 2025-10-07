import { APIRequestContext, expect } from '@playwright/test';
import { authHeaders } from './auth';

export interface BrandPayload {
  name: string;
  description?: string;
}

export function generateBrandPayload(): BrandPayload {
  const suffix = Math.random().toString(36).slice(2, 8);
  return {
    name: `E2E Brand ${suffix}`,
    description: `E2E brand description ${suffix}`,
  };
}

export async function createBrand(request: APIRequestContext, token: string, payload: BrandPayload) {
  const response = await request.post('/api/v1/brands', {
    headers: authHeaders(token),
    data: payload,
  });

  expect(response.status()).toBe(201);
  return response.json();
}

export async function deleteBrand(request: APIRequestContext, token: string, brandId: string) {
  const response = await request.delete(`/api/v1/brands/${brandId}`, {
    headers: authHeaders(token),
  });

  expect(response.status()).toBe(204);
}
