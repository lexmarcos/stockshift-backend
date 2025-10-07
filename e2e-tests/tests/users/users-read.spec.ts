import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import { createUser, deleteUser, generateUserPayload } from '../helpers/user-fixtures';

test.describe('Users API - read', () => {
  test('should list users with pagination data', async ({ request }) => {
    const auth = await login(request);

    const response = await request.get('/api/v1/users?page=0&size=5', {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(Array.isArray(body.content)).toBeTruthy();
    expect(body).toHaveProperty('totalElements');
    expect(body).toHaveProperty('totalPages');
  });

  test('should fetch user by username', async ({ request }) => {
    const auth = await login(request);
    const payload = generateUserPayload('SELLER');
    const created = await createUser(request, auth.accessToken, payload);

    const response = await request.get(`/api/v1/users/username/${payload.username}`, {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(body).toMatchObject({
      id: created.id,
      username: payload.username,
      email: payload.email,
    });

    await deleteUser(request, auth.accessToken, created.id);
  });
});
