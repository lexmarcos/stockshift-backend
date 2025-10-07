import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import { createUser, deleteUser, generateUserPayload } from '../helpers/user-fixtures';

test.describe('Users API - creation', () => {
  test('should create a new user successfully', async ({ request }) => {
    const auth = await login(request);
    const payload = generateUserPayload('MANAGER');

    const created = await createUser(request, auth.accessToken, payload);

    expect(created).toMatchObject({
      username: payload.username,
      email: payload.email,
      role: payload.role,
      active: true,
    });

    await deleteUser(request, auth.accessToken, created.id);
  });

  test('should reject duplicate username', async ({ request }) => {
    const auth = await login(request);
    const payload = generateUserPayload('SELLER');
    const created = await createUser(request, auth.accessToken, payload);

    const duplicateResponse = await request.post('/api/v1/users', {
      headers: authHeaders(auth.accessToken),
      data: payload,
    });

    expect(duplicateResponse.status()).toBeGreaterThanOrEqual(400);
    expect(duplicateResponse.status()).toBeLessThan(500);

    await deleteUser(request, auth.accessToken, created.id);
  });
});
