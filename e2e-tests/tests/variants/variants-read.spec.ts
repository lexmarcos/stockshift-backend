import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import {
  createVariantTestContext,
  createVariant,
  deleteVariant,
  generateVariantPayload,
} from '../helpers/variant-fixtures';

import { createAttributeValue, generateAttributeValuePayload } from '../helpers/attribute-fixtures';

function createVariantAttributes(definitionId: string, valueId: string) {
  return [
    { definitionId, valueId },
  ];
}

test.describe('Product Variants API - read', () => {
  test('should list variants for a product', async ({ request }) => {
    const auth = await login(request);
    const setup = await createVariantTestContext(request, auth.accessToken);

    const created = await createVariant(
      request,
      auth.accessToken,
      setup.productId,
      generateVariantPayload(createVariantAttributes(setup.definitionId, setup.valueId)),
    );

    const response = await request.get(`/api/v1/products/${setup.productId}/variants?onlyActive=true&page=0&size=10`, {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(Array.isArray(body.content)).toBeTruthy();
    expect(body.content.some((variant: any) => variant.id === created.id)).toBeTruthy();

    await deleteVariant(request, auth.accessToken, created.id);
    await setup.cleanup();
  });

  test('should list active variants globally', async ({ request }) => {
    const auth = await login(request);
    const setup = await createVariantTestContext(request, auth.accessToken);

    const created = await createVariant(
      request,
      auth.accessToken,
      setup.productId,
      generateVariantPayload(createVariantAttributes(setup.definitionId, setup.valueId)),
    );

    const response = await request.get('/api/v1/variants?onlyActive=true&page=0&size=50&sort=createdAt,desc', {
      headers: authHeaders(auth.accessToken),
    });

    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(Array.isArray(body.content)).toBeTruthy();
    expect(body.content.some((variant: any) => variant.id === created.id)).toBeTruthy();

    await deleteVariant(request, auth.accessToken, created.id);
    await setup.cleanup();
  });

  test('should fetch variant by SKU and GTIN', async ({ request }) => {
    const auth = await login(request);
    const setup = await createVariantTestContext(request, auth.accessToken);

    const payload = generateVariantPayload(createVariantAttributes(setup.definitionId, setup.valueId));
    const created = await createVariant(request, auth.accessToken, setup.productId, payload);

    const bySku = await request.get(`/api/v1/variants/sku/${payload.sku}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(bySku.ok()).toBeTruthy();
    const bySkuBody = await bySku.json();
    expect(bySkuBody.id).toBe(created.id);

    const byGtin = await request.get(`/api/v1/variants/gtin/${payload.gtin}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(byGtin.ok()).toBeTruthy();
    const byGtinBody = await byGtin.json();
    expect(byGtinBody.id).toBe(created.id);

    await deleteVariant(request, auth.accessToken, created.id);
    await setup.cleanup();
  });
});
