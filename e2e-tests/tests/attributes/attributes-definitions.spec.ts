import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import {
  createAttributeDefinition,
  deactivateAttributeDefinition,
  generateAttributeDefinitionPayload,
} from '../helpers/attribute-fixtures';

test.describe('Attribute Definitions API', () => {
  test('should create a new attribute definition successfully', async ({ request }) => {
    const auth = await login(request);
    const payload = generateAttributeDefinitionPayload();

    const created = await createAttributeDefinition(request, auth.accessToken, payload);

    expect(created).toMatchObject({
      name: payload.name,
      code: payload.code,
      type: payload.type,
      status: 'ACTIVE',
      isVariantDefining: payload.isVariantDefining,
      isRequired: payload.isRequired,
    });

    await deactivateAttributeDefinition(request, auth.accessToken, created.id);
  });

  test('should reject duplicate definition code', async ({ request }) => {
    const auth = await login(request);
    const payload = generateAttributeDefinitionPayload();
    const created = await createAttributeDefinition(request, auth.accessToken, payload);

    const duplicate = await request.post('/api/v1/attributes/definitions', {
      headers: authHeaders(auth.accessToken),
      data: payload,
    });

    expect(duplicate.status()).toBe(409);

    await deactivateAttributeDefinition(request, auth.accessToken, created.id);
  });

  test('should fetch definition by code', async ({ request }) => {
    const auth = await login(request);
    const payload = generateAttributeDefinitionPayload();
    const created = await createAttributeDefinition(request, auth.accessToken, payload);

    const response = await request.get(`/api/v1/attributes/definitions/code/${payload.code}`, {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(body).toMatchObject({
      id: created.id,
      code: payload.code,
      name: payload.name,
    });

    await deactivateAttributeDefinition(request, auth.accessToken, created.id);
  });

  test('should list active definitions with pagination', async ({ request }) => {
    const auth = await login(request);
    const payload = generateAttributeDefinitionPayload();
    const created = await createAttributeDefinition(request, auth.accessToken, payload);

    const response = await request.get('/api/v1/attributes/definitions?onlyActive=true&page=0&size=50&sort=createdAt,desc', {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(Array.isArray(body.content)).toBeTruthy();
    expect(body.content.some((definition: any) => definition.id === created.id)).toBeTruthy();

    await deactivateAttributeDefinition(request, auth.accessToken, created.id);
  });
});
