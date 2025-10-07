import { APIRequestContext, expect } from '@playwright/test';
import { authHeaders } from './auth';

export type WarehouseType =
  | 'MAIN'
  | 'DISTRIBUTION'
  | 'STORE'
  | 'TRANSIT'
  | 'SUPPLIER'
  | 'CUSTOMER';

export interface WarehousePayload {
  code: string;
  name: string;
  description?: string;
  type: WarehouseType;
  address?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  country?: string;
  phone?: string;
  email?: string;
  managerName?: string;
}

export function generateWarehousePayload(
  overrides: Partial<WarehousePayload> = {},
): WarehousePayload {
  const suffix = Math.random().toString(36).slice(2, 8).toUpperCase();
  const base: WarehousePayload = {
    code: `E2EWH${suffix}`,
    name: `E2E Warehouse ${suffix}`,
    description: `E2E warehouse description ${suffix}`,
    type: 'MAIN',
    address: `123 E2E Street ${suffix}`,
    city: 'Test City',
    state: 'TC',
    postalCode: '12345',
    country: 'Testland',
    phone: '+1-555-0101',
    email: `warehouse_${suffix.toLowerCase()}@example.com`,
    managerName: `Manager ${suffix}`,
  };

  return { ...base, ...overrides };
}

export async function createWarehouse(
  request: APIRequestContext,
  token: string,
  payload: WarehousePayload,
) {
  const response = await request.post('/api/v1/warehouses', {
    headers: authHeaders(token),
    data: payload,
  });

  expect(response.status()).toBe(201);
  return response.json();
}

export async function updateWarehouse(
  request: APIRequestContext,
  token: string,
  warehouseId: string,
  payload: Partial<WarehousePayload>,
) {
  const response = await request.put(`/api/v1/warehouses/${warehouseId}`, {
    headers: authHeaders(token),
    data: payload,
  });

  expect(response.ok()).toBeTruthy();
  return response.json();
}

export async function deactivateWarehouse(
  request: APIRequestContext,
  token: string,
  warehouseId: string,
) {
  const response = await request.delete(`/api/v1/warehouses/${warehouseId}`, {
    headers: authHeaders(token),
  });

  expect(response.status()).toBe(204);
}

export async function activateWarehouse(
  request: APIRequestContext,
  token: string,
  warehouseId: string,
) {
  const response = await request.patch(`/api/v1/warehouses/${warehouseId}/activate`, {
    headers: authHeaders(token),
  });

  expect(response.ok()).toBeTruthy();
  return response.json();
}
