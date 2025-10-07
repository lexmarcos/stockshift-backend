import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import {
  createAttributeDefinition,
  createAttributeValue,
  deactivateAttributeDefinition,
  generateAttributeDefinitionPayload,
  generateAttributeValuePayload,
} from '../helpers/attribute-fixtures';

test.describe('Attributes API - lifecycle', () => {
  test('should deactivate and reactivate an attribute definition', async ({ request }) => {
    const auth = await login(request);
    const definition = await createAttributeDefinition(request, auth.accessToken, generateAttributeDefinitionPayload());

    const deleteResponse = await request.delete(`/api/v1/attributes/definitions/${definition.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(deleteResponse.status()).toBe(204);

    const afterDelete = await request.get(`/api/v1/attributes/definitions/${definition.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(afterDelete.ok()).toBeTruthy();
    const afterDeleteBody = await afterDelete.json();
    expect(afterDeleteBody.status).toBe('INACTIVE');

    const reactivateResponse = await request.patch(`/api/v1/attributes/definitions/${definition.id}/activate`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(reactivateResponse.ok()).toBeTruthy();
    const reactivated = await reactivateResponse.json();
    expect(reactivated.status).toBe('ACTIVE');

    await deactivateAttributeDefinition(request, auth.accessToken, definition.id);
  });

  test('should deactivate and reactivate an attribute value', async ({ request }) => {
    const auth = await login(request);
    const definition = await createAttributeDefinition(request, auth.accessToken, generateAttributeDefinitionPayload());
    const value = await createAttributeValue(request, auth.accessToken, definition.id, generateAttributeValuePayload());

    const deleteResponse = await request.delete(`/api/v1/attributes/values/${value.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(deleteResponse.status()).toBe(204);

    const afterDelete = await request.get(`/api/v1/attributes/values/${value.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(afterDelete.ok()).toBeTruthy();
    const afterDeleteBody = await afterDelete.json();
    expect(afterDeleteBody.status).toBe('INACTIVE');

    const reactivateResponse = await request.patch(`/api/v1/attributes/values/${value.id}/activate`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(reactivateResponse.ok()).toBeTruthy();
    const reactivated = await reactivateResponse.json();
    expect(reactivated.status).toBe('ACTIVE');

    await deactivateAttributeDefinition(request, auth.accessToken, definition.id);
  });
});
