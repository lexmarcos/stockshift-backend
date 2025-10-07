import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import { createCategory, deleteCategory, generateCategoryPayload } from '../helpers/category-fixtures';

test.describe('Categories API - creation', () => {
  test('should create a new category successfully', async ({ request }) => {
    const auth = await login(request);
    const payload = generateCategoryPayload();

    const created = await createCategory(request, auth.accessToken, payload);

    expect(created).toMatchObject({
      name: payload.name,
      description: payload.description,
      active: true,
    });
    expect(created.parentId).toBeNull();

    await deleteCategory(request, auth.accessToken, created.id);
  });

  test('should reject duplicate category names', async ({ request }) => {
    const auth = await login(request);
    const payload = generateCategoryPayload();
    const created = await createCategory(request, auth.accessToken, payload);

    const duplicate = await request.post('/api/v1/categories', {
      headers: authHeaders(auth.accessToken),
      data: payload,
    });

    expect(duplicate.status()).toBe(409);

    await deleteCategory(request, auth.accessToken, created.id);
  });
});
