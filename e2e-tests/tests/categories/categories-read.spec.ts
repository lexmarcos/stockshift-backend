import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import { createCategory, deleteCategory, generateCategoryPayload } from '../helpers/category-fixtures';

test.describe('Categories API - read', () => {
  test('should list active categories with pagination data', async ({ request }) => {
    const auth = await login(request);
    const payload = generateCategoryPayload();
    const created = await createCategory(request, auth.accessToken, payload);

    const response = await request.get('/api/v1/categories?onlyActive=true&page=0&size=10', {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();

    expect(Array.isArray(body.content)).toBeTruthy();
    expect(body).toHaveProperty('totalElements');
    expect(body).toHaveProperty('totalPages');
    expect(body.content.some((category: any) => category.id === created.id)).toBeTruthy();

    await deleteCategory(request, auth.accessToken, created.id);
  });

  test('should fetch a category by name', async ({ request }) => {
    const auth = await login(request);
    const payload = generateCategoryPayload();
    const created = await createCategory(request, auth.accessToken, payload);

    const response = await request.get(`/api/v1/categories/name/${encodeURIComponent(payload.name)}`, {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(body).toMatchObject({
      id: created.id,
      name: payload.name,
      description: payload.description,
    });

    await deleteCategory(request, auth.accessToken, created.id);
  });

  test('should list root categories', async ({ request }) => {
    const auth = await login(request);
    const payload = generateCategoryPayload();
    const created = await createCategory(request, auth.accessToken, payload);

    const response = await request.get('/api/v1/categories/root?page=0&size=10', {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();

    expect(Array.isArray(body.content)).toBeTruthy();
    expect(body.content.some((category: any) => category.id === created.id)).toBeTruthy();

    await deleteCategory(request, auth.accessToken, created.id);
  });
});
