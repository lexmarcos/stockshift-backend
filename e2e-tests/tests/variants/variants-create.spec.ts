import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import {
  createVariantTestContext,
  createVariant,
  deleteVariant,
  generateVariantPayload,
} from '../helpers/variant-fixtures';

test.describe('Product Variants API - creation', () => {
  test('should create a variant for a product', async ({ request }) => {
    const auth = await login(request);
    const setup = await createVariantTestContext(request, auth.accessToken);

    const payload = generateVariantPayload([
      { definitionId: setup.definitionId, valueId: setup.valueId },
    ]);

    const created = await createVariant(request, auth.accessToken, setup.productId, payload);

    expect(created).toMatchObject({
      sku: payload.sku,
      gtin: payload.gtin,
      productId: setup.productId,
      active: true,
    });
    expect(created.attributes).toHaveLength(1);

    await deleteVariant(request, auth.accessToken, created.id);
    await setup.cleanup();
  });

  test('should reject duplicate SKU for different variants', async ({ request }) => {
    const auth = await login(request);
    const setup = await createVariantTestContext(request, auth.accessToken);

    const basePayload = generateVariantPayload([
      { definitionId: setup.definitionId, valueId: setup.valueId },
    ]);

    const created = await createVariant(request, auth.accessToken, setup.productId, basePayload);

    const duplicate = await request.post(`/api/v1/products/${setup.productId}/variants`, {
      headers: authHeaders(auth.accessToken),
      data: { ...basePayload, gtin: `${basePayload.gtin}-DIFF` },
    });

    expect(duplicate.status()).toBe(409);

    await deleteVariant(request, auth.accessToken, created.id);
    await setup.cleanup();
  });

  test('should reject duplicate attribute combination for same product', async ({ request }) => {
    const auth = await login(request);
    const setup = await createVariantTestContext(request, auth.accessToken);

    const payload = generateVariantPayload([
      { definitionId: setup.definitionId, valueId: setup.valueId },
    ]);

    const created = await createVariant(request, auth.accessToken, setup.productId, payload);

    const duplicateCombination = await request.post(`/api/v1/products/${setup.productId}/variants`, {
      headers: authHeaders(auth.accessToken),
      data: generateVariantPayload([
        { definitionId: setup.definitionId, valueId: setup.valueId },
      ], { sku: `ALT-${payload.sku}`, gtin: `ALT${payload.gtin}` }),
    });

    expect(duplicateCombination.status()).toBe(409);

    await deleteVariant(request, auth.accessToken, created.id);
    await setup.cleanup();
  });
});
