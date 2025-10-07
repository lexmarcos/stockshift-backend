import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import {
  createWarehouse,
  deactivateWarehouse,
  generateWarehousePayload,
  updateWarehouse,
} from '../helpers/warehouse-fixtures';

function expectWarehouseListIncludes(body: any, warehouseId: string) {
  expect(Array.isArray(body.content)).toBeTruthy();
  expect(body.content.some((warehouse: any) => warehouse.id === warehouseId)).toBeTruthy();
}

test.describe('Warehouses API - read', () => {
  test('should list active warehouses', async ({ request }) => {
    const auth = await login(request);
    const created = await createWarehouse(request, auth.accessToken, generateWarehousePayload());

    const response = await request.get('/api/v1/warehouses?onlyActive=true&page=0&size=50&sort=createdAt,desc', {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expectWarehouseListIncludes(body, created.id);

    await deactivateWarehouse(request, auth.accessToken, created.id);
  });

  test('should filter warehouses by type', async ({ request }) => {
    const auth = await login(request);
    const created = await createWarehouse(
      request,
      auth.accessToken,
      generateWarehousePayload({ type: 'STORE' }),
    );

    const response = await request.get('/api/v1/warehouses/type/STORE?onlyActive=true&page=0&size=50&sort=createdAt,desc', {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expectWarehouseListIncludes(body, created.id);

    await deactivateWarehouse(request, auth.accessToken, created.id);
  });

  test('should search warehouses by name or code', async ({ request }) => {
    const auth = await login(request);
    const payload = generateWarehousePayload();
    const created = await createWarehouse(request, auth.accessToken, payload);

    const response = await request.get(`/api/v1/warehouses/search?query=${encodeURIComponent(payload.code.slice(0, 6))}&page=0&size=10`, {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expectWarehouseListIncludes(body, created.id);

    await deactivateWarehouse(request, auth.accessToken, created.id);
  });

  test('should fetch warehouse by code and update details', async ({ request }) => {
    const auth = await login(request);
    const payload = generateWarehousePayload({ type: 'DISTRIBUTION' });
    const created = await createWarehouse(request, auth.accessToken, payload);

    const byCode = await request.get(`/api/v1/warehouses/code/${payload.code}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(byCode.ok()).toBeTruthy();
    const byCodeBody = await byCode.json();
    expect(byCodeBody).toMatchObject({
      id: created.id,
      code: payload.code,
      type: payload.type,
    });

    const updated = await updateWarehouse(request, auth.accessToken, created.id, {
      name: `${payload.name} Updated`,
      managerName: 'Updated Manager',
    });
    expect(updated.name).toBe(`${payload.name} Updated`);
    expect(updated.managerName).toBe('Updated Manager');

    await deactivateWarehouse(request, auth.accessToken, created.id);
  });
});
