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
  generateStockEventPayload,
  getStockEvent,
  listStockEvents,
} from '../helpers/stock-fixtures';

interface StockEventTestContext {
  variantId: string;
  warehouseId: string;
  cleanup: () => Promise<void>;
}

async function setupStockEventContext(
  request: APIRequestContext,
  token: string,
): Promise<StockEventTestContext> {
  const variantSetup = await createVariantTestContext(request, token);
  const variantPayload = generateVariantPayload([
    { definitionId: variantSetup.definitionId, valueId: variantSetup.valueId },
  ]);
  const variant = await createVariant(request, token, variantSetup.productId, variantPayload);

  const warehouse = await createWarehouse(
    request,
    token,
    generateWarehousePayload({ type: 'MAIN' }),
  );

  const cleanup = async () => {
    const tasks: Array<() => Promise<void>> = [
      () => deleteVariant(request, token, variant.id),
      () => variantSetup.cleanup(),
      () => deactivateWarehouse(request, token, warehouse.id),
    ];

    for (const task of tasks) {
      try {
        await task();
      } catch (error) {
        // ignore cleanup issues to keep tests isolated
      }
    }
  };

  return {
    variantId: variant.id,
    warehouseId: warehouse.id,
    cleanup,
  };
}

test.describe('Stock Events API', () => {
  test('should create an inbound stock event and retrieve it', async ({ request }) => {
    const auth = await login(request);
    const context = await setupStockEventContext(request, auth.accessToken);

    const inboundPayload = generateInboundEventPayload(context.warehouseId, [
      { variantId: context.variantId, quantity: 120 },
    ], { notes: 'Inbound restock E2E' });

    const createdEvent = await createStockEvent(request, auth.accessToken, inboundPayload);

    expect(createdEvent.type).toBe('INBOUND');
    expect(createdEvent.warehouseId).toBe(context.warehouseId);
    expect(createdEvent.lines).toHaveLength(1);
    expect(createdEvent.lines[0]).toMatchObject({
      variantId: context.variantId,
      quantity: 120,
    });

    const fetched = await getStockEvent(request, auth.accessToken, createdEvent.id);
    expect(fetched.id).toBe(createdEvent.id);
    expect(fetched.lines).toHaveLength(1);
    expect(fetched.lines[0].quantity).toBe(120);

    await context.cleanup();
  });

  test('should create outbound events and support idempotent retries', async ({ request }) => {
    const auth = await login(request);
    const context = await setupStockEventContext(request, auth.accessToken);

    await createStockEvent(
      request,
      auth.accessToken,
      generateInboundEventPayload(context.warehouseId, [
        { variantId: context.variantId, quantity: 200 },
      ]),
    );

    const outboundPayload = generateStockEventPayload('OUTBOUND', context.warehouseId, [
      { variantId: context.variantId, quantity: 75 },
    ], { notes: 'Outbound allocation E2E' });

    const idempotencyKey = `outbound-${Math.random().toString(36).slice(2, 10)}`;
    const headers = {
      ...authHeaders(auth.accessToken),
      'Idempotency-Key': idempotencyKey,
    };

    const firstResponse = await request.post('/api/v1/stock-events', {
      headers,
      data: outboundPayload,
    });
    expect(firstResponse.status()).toBe(201);
    const firstBody = await firstResponse.json();
    expect(firstBody.type).toBe('OUTBOUND');
    expect(firstBody.lines[0].quantity).toBe(-75);

    const secondResponse = await request.post('/api/v1/stock-events', {
      headers,
      data: outboundPayload,
    });
    expect(secondResponse.status()).toBe(201);
    const secondBody = await secondResponse.json();
    expect(secondBody.id).toBe(firstBody.id);
    expect(secondBody.lines[0].quantity).toBe(firstBody.lines[0].quantity);

    await context.cleanup();
  });

  test('should reject outbound events when stock is insufficient', async ({ request }) => {
    const auth = await login(request);
    const context = await setupStockEventContext(request, auth.accessToken);

    const outboundPayload = generateStockEventPayload('OUTBOUND', context.warehouseId, [
      { variantId: context.variantId, quantity: 10 },
    ]);

    const response = await request.post('/api/v1/stock-events', {
      headers: authHeaders(auth.accessToken),
      data: outboundPayload,
    });

    expect(response.status()).toBe(409);

    await context.cleanup();
  });

  test('should list stock events filtered by type and warehouse', async ({ request }) => {
    const auth = await login(request);
    const context = await setupStockEventContext(request, auth.accessToken);

    const inbound = await createStockEvent(
      request,
      auth.accessToken,
      generateInboundEventPayload(context.warehouseId, [
        { variantId: context.variantId, quantity: 50 },
      ], { notes: 'List filter inbound' }),
    );

    const page = await listStockEvents(request, auth.accessToken, {
      type: 'INBOUND',
      warehouseId: context.warehouseId,
    });

    expect(Array.isArray(page.content)).toBeTruthy();
    const match = page.content.find((event: { id: string }) => event.id === inbound.id);
    expect(match).toBeTruthy();

    await context.cleanup();
  });
});
