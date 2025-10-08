import { test, expect, APIRequestContext } from '@playwright/test';
import { login, authHeaders } from '../helpers/auth';
import {
  createVariantTestContext,
  createVariant,
  deleteVariant,
  generateVariantPayload,
} from '../helpers/variant-fixtures';
import {
  createWarehouse,
  deactivateWarehouse,
  generateWarehousePayload,
} from '../helpers/warehouse-fixtures';
import {
  createStockEvent,
  generateInboundEventPayload,
} from '../helpers/stock-fixtures';
import {
  cancelTransfer,
  confirmTransfer,
  createTransferDraft,
} from '../helpers/stock-transfer-fixtures';

type TransferTestContext = {
  variantId: string;
  originWarehouseId: string;
  destinationWarehouseId: string;
  cleanup: () => Promise<void>;
};

async function setupTransferContext(
  request: APIRequestContext,
  token: string,
): Promise<TransferTestContext> {
  const variantSetup = await createVariantTestContext(request, token);
  const variantPayload = generateVariantPayload([
    { definitionId: variantSetup.definitionId, valueId: variantSetup.valueId },
  ]);
  const variant = await createVariant(request, token, variantSetup.productId, variantPayload);

  const originWarehouse = await createWarehouse(
    request,
    token,
    generateWarehousePayload({ type: 'DISTRIBUTION' }),
  );
  const destinationWarehouse = await createWarehouse(
    request,
    token,
    generateWarehousePayload({ type: 'STORE' }),
  );

  const cleanup = async () => {
    const actions: Array<() => Promise<void>> = [
      () => deleteVariant(request, token, variant.id),
      () => variantSetup.cleanup(),
      () => deactivateWarehouse(request, token, originWarehouse.id),
      () => deactivateWarehouse(request, token, destinationWarehouse.id),
    ];

    for (const action of actions) {
      try {
        await action();
      } catch (error) {
        // ignore cleanup failures to keep tests resilient
      }
    }
  };

  return {
    variantId: variant.id,
    originWarehouseId: originWarehouse.id,
    destinationWarehouseId: destinationWarehouse.id,
    cleanup,
  };
}

test.describe('Stock Transfers API', () => {
  test('should create a draft transfer when stock exists', async ({ request }) => {
    const auth = await login(request);
    const context = await setupTransferContext(request, auth.accessToken);

    await createStockEvent(
      request,
      auth.accessToken,
      generateInboundEventPayload(context.originWarehouseId, [
        { variantId: context.variantId, quantity: 120 },
      ]),
    );

    const transfer = await createTransferDraft(request, auth.accessToken, {
      originWarehouseId: context.originWarehouseId,
      destinationWarehouseId: context.destinationWarehouseId,
      notes: 'E2E transfer draft',
      lines: [{ variantId: context.variantId, quantity: 40 }],
    });

    expect(transfer.status).toBe('DRAFT');
    expect(transfer.lines).toHaveLength(1);
    expect(transfer.lines[0]).toMatchObject({
      variantId: context.variantId,
      quantity: 40,
    });
    expect(transfer.outboundEventId).toBeNull();
    expect(transfer.inboundEventId).toBeNull();

    await context.cleanup();
  });

  test('should confirm a transfer and allow idempotent retries', async ({ request }) => {
    const auth = await login(request);
    const context = await setupTransferContext(request, auth.accessToken);

    await createStockEvent(
      request,
      auth.accessToken,
      generateInboundEventPayload(context.originWarehouseId, [
        { variantId: context.variantId, quantity: 150 },
      ]),
    );

    const draft = await createTransferDraft(request, auth.accessToken, {
      originWarehouseId: context.originWarehouseId,
      destinationWarehouseId: context.destinationWarehouseId,
      occurredAt: new Date().toISOString(),
      notes: 'Confirm transfer',
      lines: [{ variantId: context.variantId, quantity: 60 }],
    });

    const idempotencyKey = `confirm-${Math.random().toString(36).slice(2, 10)}`;

    const firstConfirmation = await confirmTransfer(request, auth.accessToken, draft.id, {
      idempotencyKey,
    });
    expect(firstConfirmation.status).toBe('CONFIRMED');
    expect(firstConfirmation.outboundEventId).toBeTruthy();
    expect(firstConfirmation.inboundEventId).toBeTruthy();
    expect(firstConfirmation.lines).toHaveLength(1);
    expect(firstConfirmation.lines[0]).toMatchObject({
      variantId: context.variantId,
      quantity: 60,
    });

    const secondConfirmation = await confirmTransfer(request, auth.accessToken, draft.id, {
      idempotencyKey,
    });
    expect(secondConfirmation.id).toBe(firstConfirmation.id);
    expect(secondConfirmation.outboundEventId).toBe(firstConfirmation.outboundEventId);
    expect(secondConfirmation.inboundEventId).toBe(firstConfirmation.inboundEventId);

    await context.cleanup();
  });

  test('should reject confirming different transfers with the same idempotency key', async ({ request }) => {
    const auth = await login(request);
    const context = await setupTransferContext(request, auth.accessToken);

    await createStockEvent(
      request,
      auth.accessToken,
      generateInboundEventPayload(context.originWarehouseId, [
        { variantId: context.variantId, quantity: 200 },
      ]),
    );

    const firstDraft = await createTransferDraft(request, auth.accessToken, {
      originWarehouseId: context.originWarehouseId,
      destinationWarehouseId: context.destinationWarehouseId,
      lines: [{ variantId: context.variantId, quantity: 70 }],
    });
    const secondDraft = await createTransferDraft(request, auth.accessToken, {
      originWarehouseId: context.originWarehouseId,
      destinationWarehouseId: context.destinationWarehouseId,
      lines: [{ variantId: context.variantId, quantity: 30 }],
    });

    const idempotencyKey = `conflict-${Math.random().toString(36).slice(2, 10)}`;

    await confirmTransfer(request, auth.accessToken, firstDraft.id, {
      idempotencyKey,
    });

    const conflictResponse = await request.post(
      `/api/v1/stock-transfers/${secondDraft.id}/confirm`,
      {
        headers: {
          ...authHeaders(auth.accessToken),
          'Idempotency-Key': idempotencyKey,
        },
      },
    );

    expect(conflictResponse.status()).toBe(409);

    await context.cleanup();
  });

  test('should cancel a draft transfer', async ({ request }) => {
    const auth = await login(request);
    const context = await setupTransferContext(request, auth.accessToken);

    const draft = await createTransferDraft(request, auth.accessToken, {
      originWarehouseId: context.originWarehouseId,
      destinationWarehouseId: context.destinationWarehouseId,
      lines: [{ variantId: context.variantId, quantity: 15 }],
    });

    const canceled = await cancelTransfer(request, auth.accessToken, draft.id);
    expect(canceled.status).toBe('CANCELED');

    await context.cleanup();
  });

  test('should reject transfers when origin and destination are the same', async ({ request }) => {
    const auth = await login(request);
    const context = await setupTransferContext(request, auth.accessToken);

    const response = await request.post('/api/v1/stock-transfers', {
      headers: authHeaders(auth.accessToken),
      data: {
        originWarehouseId: context.originWarehouseId,
        destinationWarehouseId: context.originWarehouseId,
        lines: [{ variantId: context.variantId, quantity: 10 }],
      },
    });

    expect(response.status()).toBe(400);

    await context.cleanup();
  });
});
