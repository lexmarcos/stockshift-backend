import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import {
  createWarehouse,
  deactivateWarehouse,
  generateWarehousePayload,
} from '../helpers/warehouse-fixtures';

test.describe('Warehouses API - creation', () => {
  test('should create a warehouse successfully', async ({ request }) => {
    const auth = await login(request);
    const payload = generateWarehousePayload();

    const created = await createWarehouse(request, auth.accessToken, payload);

    expect(created).toMatchObject({
      code: payload.code,
      name: payload.name,
      type: payload.type,
      active: true,
    });

    await deactivateWarehouse(request, auth.accessToken, created.id);
  });

  test('should reject duplicate warehouse code', async ({ request }) => {
    const auth = await login(request);
    const payload = generateWarehousePayload();
    const created = await createWarehouse(request, auth.accessToken, payload);

    const duplicate = await request.post('/api/v1/warehouses', {
      headers: authHeaders(auth.accessToken),
      data: payload,
    });

    expect(duplicate.status()).toBe(409);

    await deactivateWarehouse(request, auth.accessToken, created.id);
  });
});
