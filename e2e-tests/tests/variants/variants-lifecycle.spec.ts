import { test, expect } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import {
  createVariantTestContext,
  createVariant,
  generateVariantPayload,
} from '../helpers/variant-fixtures';

test.describe('Product Variants API - lifecycle', () => {
  test('should deactivate and reactivate a variant', async ({ request }) => {
    const auth = await login(request);
    const setup = await createVariantTestContext(request, auth.accessToken);

    const created = await createVariant(
      request,
      auth.accessToken,
      setup.productId,
      generateVariantPayload([
        { definitionId: setup.definitionId, valueId: setup.valueId },
      ]),
    );

    const deactivate = await request.delete(`/api/v1/variants/${created.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(deactivate.status()).toBe(204);

    const afterDelete = await request.get(`/api/v1/variants/${created.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(afterDelete.ok()).toBeTruthy();
    const afterDeleteBody = await afterDelete.json();
    expect(afterDeleteBody.active).toBe(false);

    const reactivate = await request.patch(`/api/v1/variants/${created.id}/activate`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(reactivate.ok()).toBeTruthy();
    const reactivated = await reactivate.json();
    expect(reactivated.active).toBe(true);

    const cleanup = await request.delete(`/api/v1/variants/${created.id}`, {
      headers: authHeaders(auth.accessToken),
    });
    expect(cleanup.status()).toBe(204);

    await setup.cleanup();
  });
});
