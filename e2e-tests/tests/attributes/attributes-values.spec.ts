import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import {
  createAttributeDefinition,
  createAttributeValue,
  deactivateAttributeDefinition,
  deactivateAttributeValue,
  generateAttributeDefinitionPayload,
  generateAttributeValuePayload,
} from '../helpers/attribute-fixtures';

test.describe('Attribute Values API', () => {
  test('should create a new attribute value for definition', async ({ request }) => {
    const auth = await login(request);
    const definition = await createAttributeDefinition(request, auth.accessToken, generateAttributeDefinitionPayload());
    const valuePayload = generateAttributeValuePayload();

    const createdValue = await createAttributeValue(request, auth.accessToken, definition.id, valuePayload);

    expect(createdValue).toMatchObject({
      definitionId: definition.id,
      code: valuePayload.code,
      value: valuePayload.value,
      status: 'ACTIVE',
    });

    await deactivateAttributeValue(request, auth.accessToken, createdValue.id);
    await deactivateAttributeDefinition(request, auth.accessToken, definition.id);
  });

  test('should reject duplicate value code within the same definition', async ({ request }) => {
    const auth = await login(request);
    const definition = await createAttributeDefinition(request, auth.accessToken, generateAttributeDefinitionPayload());
    const valuePayload = generateAttributeValuePayload();
    const createdValue = await createAttributeValue(request, auth.accessToken, definition.id, valuePayload);

    const duplicate = await request.post(`/api/v1/attributes/definitions/${definition.id}/values`, {
      headers: authHeaders(auth.accessToken),
      data: valuePayload,
    });

    expect(duplicate.status()).toBe(409);

    await deactivateAttributeValue(request, auth.accessToken, createdValue.id);
    await deactivateAttributeDefinition(request, auth.accessToken, definition.id);
  });

  test('should list active values for a definition', async ({ request }) => {
    const auth = await login(request);
    const definition = await createAttributeDefinition(request, auth.accessToken, generateAttributeDefinitionPayload());
    const valuePayload = generateAttributeValuePayload();
    const createdValue = await createAttributeValue(request, auth.accessToken, definition.id, valuePayload);

    const response = await request.get(`/api/v1/attributes/definitions/${definition.id}/values?onlyActive=true&page=0&size=10`, {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(Array.isArray(body.content)).toBeTruthy();
    expect(body.content.some((value: any) => value.id === createdValue.id)).toBeTruthy();

    await deactivateAttributeValue(request, auth.accessToken, createdValue.id);
    await deactivateAttributeDefinition(request, auth.accessToken, definition.id);
  });
});
