import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import { createUser, generateUserPayload } from '../helpers/user-fixtures';

test.describe('Users API - lifecycle', () => {
  test('should deactivate and reactivate a user', async ({ request }) => {
    const auth = await login(request);
    const payload = generateUserPayload('SELLER');
    const created = await createUser(request, auth.accessToken, payload);

    const deleteResponse = await request.delete(`/api/v1/users/${created.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(deleteResponse.status()).toBe(204);

    const afterDelete = await request.get(`/api/v1/users/${created.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(afterDelete.ok()).toBeTruthy();
    const afterDeleteBody = await afterDelete.json();
    expect(afterDeleteBody.active).toBe(false);

    const reactivateResponse = await request.patch(`/api/v1/users/${created.id}/activate`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(reactivateResponse.ok()).toBeTruthy();
    const reactivated = await reactivateResponse.json();
    expect(reactivated.active).toBe(true);

    // final cleanup
    const cleanup = await request.delete(`/api/v1/users/${created.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(cleanup.status()).toBe(204);
  });
});
