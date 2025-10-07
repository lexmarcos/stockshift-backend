import { APIRequestContext, expect } from '@playwright/test';
import { authHeaders } from './auth';

export interface AttributeDefinitionPayload {
  name: string;
  code: string;
  type: 'ENUM' | 'MULTI_ENUM' | 'TEXT' | 'NUMBER' | 'BOOLEAN';
  description?: string;
  isVariantDefining?: boolean;
  isRequired?: boolean;
  applicableCategoryIds?: string[];
  sortOrder?: number;
}

export interface AttributeValuePayload {
  value: string;
  code: string;
  description?: string;
  swatchHex?: string;
}

export function generateAttributeDefinitionPayload(
  overrides: Partial<AttributeDefinitionPayload> = {},
): AttributeDefinitionPayload {
  const suffix = Math.random().toString(36).slice(2, 8).toUpperCase();
  const base: AttributeDefinitionPayload = {
    name: `E2E Attribute ${suffix}`,
    code: `E2E_ATTR_${suffix}`,
    type: 'ENUM',
    description: `E2E attribute definition ${suffix}`,
    isVariantDefining: true,
    isRequired: false,
    applicableCategoryIds: [],
    sortOrder: 0,
  };

  return { ...base, ...overrides };
}

export function generateAttributeValuePayload(
  overrides: Partial<AttributeValuePayload> = {},
): AttributeValuePayload {
  const suffix = Math.random().toString(36).slice(2, 8).toUpperCase();
  const base: AttributeValuePayload = {
    value: `Value ${suffix}`,
    code: `VAL_${suffix}`,
    description: `E2E attribute value ${suffix}`,
  };

  return { ...base, ...overrides };
}

export async function createAttributeDefinition(
  request: APIRequestContext,
  token: string,
  payload: AttributeDefinitionPayload,
) {
  const response = await request.post('/api/v1/attributes/definitions', {
    headers: authHeaders(token),
    data: payload,
  });

  expect(response.status()).toBe(201);
  return response.json();
}

export async function deactivateAttributeDefinition(
  request: APIRequestContext,
  token: string,
  definitionId: string,
) {
  const response = await request.delete(`/api/v1/attributes/definitions/${definitionId}`, {
    headers: authHeaders(token),
  });

  expect(response.status()).toBe(204);
}

export async function createAttributeValue(
  request: APIRequestContext,
  token: string,
  definitionId: string,
  payload: AttributeValuePayload,
) {
  const response = await request.post(`/api/v1/attributes/definitions/${definitionId}/values`, {
    headers: authHeaders(token),
    data: payload,
  });

  expect(response.status()).toBe(201);
  return response.json();
}

export async function deactivateAttributeValue(
  request: APIRequestContext,
  token: string,
  valueId: string,
) {
  const response = await request.delete(`/api/v1/attributes/values/${valueId}`, {
    headers: authHeaders(token),
  });

  expect(response.status()).toBe(204);
}
