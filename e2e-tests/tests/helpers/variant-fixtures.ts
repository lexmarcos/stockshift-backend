import { APIRequestContext, expect } from '@playwright/test';
import { authHeaders } from './auth';
import {
  createAttributeDefinition,
  createAttributeValue,
  deactivateAttributeDefinition,
  generateAttributeDefinitionPayload,
  generateAttributeValuePayload,
} from './attribute-fixtures';
import {
  createProduct,
  deleteProduct,
  generateProductPayload,
  provisionProductDependencies,
  type ProductPayload,
} from './product-fixtures';

export interface VariantAttributeInput {
  definitionId: string;
  valueId: string;
}

export interface VariantPayload {
  sku: string;
  gtin?: string;
  attributes: VariantAttributeInput[];
  price?: number;
  weight?: number;
  length?: number;
  width?: number;
  height?: number;
}

export interface VariantSetup {
  productId: string;
  definitionId: string;
  valueId: string;
  cleanup: () => Promise<void>;
}

export async function createVariantTestContext(
  request: APIRequestContext,
  token: string,
  options: { withGtin?: boolean; productOverrides?: Partial<ProductPayload> } = {},
): Promise<VariantSetup> {
  const cleanups: Array<() => Promise<void>> = [];

  const deps = await provisionProductDependencies(request, token, {
    withBrand: true,
    withCategory: true,
  });
  cleanups.push(deps.cleanup);

  const productPayload = generateProductPayload({
    brandId: deps.brandId,
    categoryId: deps.categoryId,
    ...options.productOverrides,
  });

  const product = await createProduct(request, token, productPayload);
  cleanups.push(() => deleteProduct(request, token, product.id));

  const definition = await createAttributeDefinition(
    request,
    token,
    generateAttributeDefinitionPayload({ applicableCategoryIds: [deps.categoryId!] }),
  );
  const value = await createAttributeValue(
    request,
    token,
    definition.id,
    generateAttributeValuePayload(),
  );
  cleanups.push(() => deactivateAttributeDefinition(request, token, definition.id));

  return {
    productId: product.id,
    definitionId: definition.id,
    valueId: value.id,
    cleanup: async () => {
      for (const fn of cleanups.reverse()) {
        await fn();
      }
    },
  };
}

export function generateVariantPayload(
  attributes: VariantAttributeInput[],
  overrides: Partial<VariantPayload> = {},
): VariantPayload {
  const suffix = Math.random().toString(36).slice(2, 8).toUpperCase();
  const base: VariantPayload = {
    sku: `E2E-SKU-${suffix}`,
    gtin: overrides.gtin ?? `E2EGTIN${suffix}`,
    attributes,
    price: 1500,
    weight: 100,
    length: 10,
    width: 10,
    height: 10,
  };

  return { ...base, ...overrides, attributes };
}

export async function createVariant(
  request: APIRequestContext,
  token: string,
  productId: string,
  payload: VariantPayload,
) {
  const response = await request.post(`/api/v1/products/${productId}/variants`, {
    headers: authHeaders(token),
    data: payload,
  });

  expect(response.status()).toBe(201);
  return response.json();
}

export async function deleteVariant(
  request: APIRequestContext,
  token: string,
  variantId: string,
) {
  const response = await request.delete(`/api/v1/variants/${variantId}`, {
    headers: authHeaders(token),
  });

  expect(response.status()).toBe(204);
}
