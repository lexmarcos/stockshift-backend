import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import {
  activateWarehouse,
  createWarehouse,
  deactivateWarehouse,
  generateWarehousePayload,
} from '../helpers/warehouse-fixtures';

test.describe('Warehouses API - lifecycle', () => {
  test('should deactivate and reactivate a warehouse', async ({ request }) => {
    const auth = await login(request);
    const created = await createWarehouse(request, auth.accessToken, generateWarehousePayload());

    await deactivateWarehouse(request, auth.accessToken, created.id);

    const afterDelete = await request.get(`/api/v1/warehouses/${created.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(afterDelete.ok()).toBeTruthy();
    const afterDeleteBody = await afterDelete.json();
    expect(afterDeleteBody.active).toBe(false);

    const activated = await activateWarehouse(request, auth.accessToken, created.id);
    expect(activated.active).toBe(true);

    await deactivateWarehouse(request, auth.accessToken, created.id);
  });
});
