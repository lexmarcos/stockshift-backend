import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import { createCategory, deleteCategory, generateCategoryPayload } from '../helpers/category-fixtures';

test.describe('Categories API - hierarchy', () => {
  test('should list subcategories for a parent category', async ({ request }) => {
    const auth = await login(request);
    const parent = await createCategory(request, auth.accessToken, generateCategoryPayload());
    const childPayload = generateCategoryPayload({ parentId: parent.id });
    const child = await createCategory(request, auth.accessToken, childPayload);

    const response = await request.get(`/api/v1/categories/${parent.id}/subcategories?page=0&size=10`, {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(Array.isArray(body.content)).toBeTruthy();
    expect(body.content.some((category: any) => category.id === child.id)).toBeTruthy();

    await deleteCategory(request, auth.accessToken, parent.id);
  });

  test('should list descendants for a category tree', async ({ request }) => {
    const auth = await login(request);
    const parent = await createCategory(request, auth.accessToken, generateCategoryPayload());
    const child = await createCategory(request, auth.accessToken, generateCategoryPayload({ parentId: parent.id }));
    const grandChild = await createCategory(request, auth.accessToken, generateCategoryPayload({ parentId: child.id }));

    const response = await request.get(`/api/v1/categories/${parent.id}/descendants`, {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(Array.isArray(body)).toBeTruthy();
    expect(body.some((category: any) => category.id === child.id)).toBeTruthy();
    expect(body.some((category: any) => category.id === grandChild.id)).toBeTruthy();

    await deleteCategory(request, auth.accessToken, parent.id);
  });
});
