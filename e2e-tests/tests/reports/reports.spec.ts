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
} from '../helpers/stock-fixtures';

interface ReportTestContext {
  productId: string;
  variantId: string;
  warehouseId: string;
  cleanup: () => Promise<void>;
}

async function setupReportContext(
  request: APIRequestContext,
  token: string,
  options: { productExpiryDaysAhead?: number } = {},
): Promise<ReportTestContext> {
  const productOverrides = options.productExpiryDaysAhead
    ? {
        expiryDate: new Date(Date.now() + options.productExpiryDaysAhead * 24 * 60 * 60 * 1000)
          .toISOString()
          .split('T')[0],
      }
    : undefined;

  const variantSetup = await createVariantTestContext(request, token, {
    productOverrides,
  });
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
        // ignore cleanup issues to keep other assertions intact
      }
    }
  };

  return {
    productId: variantSetup.productId,
    variantId: variant.id,
    warehouseId: warehouse.id,
    cleanup,
  };
}

test.describe('Reports API', () => {
  test('should return stock snapshot for a warehouse and variant', async ({ request }) => {
    const auth = await login(request);
    const context = await setupReportContext(request, auth.accessToken);

    await createStockEvent(
      request,
      auth.accessToken,
      generateInboundEventPayload(context.warehouseId, [
        { variantId: context.variantId, quantity: 150 },
      ]),
    );

    const response = await request.get('/api/v1/reports/stock-snapshot', {
      headers: authHeaders(auth.accessToken),
      params: {
        warehouseId: context.warehouseId,
        variantId: context.variantId,
        page: '0',
        size: '50',
      },
    });

    expect(response.ok()).toBeTruthy();
    const page = await response.json();
    expect(Array.isArray(page.content)).toBeTruthy();
    const match = page.content.find((item: any) => item.variantId === context.variantId);
    expect(match).toBeTruthy();
    expect(match.quantity).toBe(150);

    await context.cleanup();
  });

  test('should return stock history ordered by occurrence', async ({ request }) => {
    const auth = await login(request);
    const context = await setupReportContext(request, auth.accessToken);

    const now = Date.now();
    const earlier = new Date(now - 60_000).toISOString();
    const later = new Date(now - 30_000).toISOString();

    await createStockEvent(
      request,
      auth.accessToken,
      generateInboundEventPayload(
        context.warehouseId,
        [{ variantId: context.variantId, quantity: 90 }],
        { occurredAt: earlier, notes: 'History inbound' },
      ),
    );

    await createStockEvent(
      request,
      auth.accessToken,
      generateStockEventPayload(
        'OUTBOUND',
        context.warehouseId,
        [{ variantId: context.variantId, quantity: 40 }],
        { occurredAt: later, notes: 'History outbound' },
      ),
    );

    const response = await request.get('/api/v1/reports/stock-history', {
      headers: authHeaders(auth.accessToken),
      params: {
        warehouseId: context.warehouseId,
        variantId: context.variantId,
        sort: 'occurredAt,asc',
        page: '0',
        size: '50',
      },
    });

    expect(response.ok()).toBeTruthy();
    const page = await response.json();
    expect(Array.isArray(page.content)).toBeTruthy();
    expect(page.content.length).toBeGreaterThanOrEqual(2);

    const inboundEntry = page.content.find((entry: any) => entry.notes === 'History inbound');
    const outboundEntry = page.content.find((entry: any) => entry.notes === 'History outbound');

    expect(inboundEntry).toBeTruthy();
    expect(inboundEntry.quantityChange).toBe(90);
    expect(outboundEntry).toBeTruthy();
    expect(outboundEntry.quantityChange).toBe(-40);
    expect(outboundEntry.balanceBefore).toBeGreaterThan(outboundEntry.balanceAfter);

    await context.cleanup();
  });

  test('should surface low stock items below threshold', async ({ request }) => {
    const auth = await login(request);
    const context = await setupReportContext(request, auth.accessToken);

    await createStockEvent(
      request,
      auth.accessToken,
      generateInboundEventPayload(context.warehouseId, [
        { variantId: context.variantId, quantity: 60 },
      ], { notes: 'Low stock inbound' }),
    );

    await createStockEvent(
      request,
      auth.accessToken,
      generateStockEventPayload('OUTBOUND', context.warehouseId, [
        { variantId: context.variantId, quantity: 50 },
      ], { notes: 'Low stock outbound' }),
    );

    const response = await request.get('/api/v1/reports/low-stock', {
      headers: authHeaders(auth.accessToken),
      params: {
        warehouseId: context.warehouseId,
        threshold: '40',
        page: '0',
        size: '50',
      },
    });

    expect(response.ok()).toBeTruthy();
    const page = await response.json();
    expect(Array.isArray(page.content)).toBeTruthy();
    const item = page.content.find((entry: any) => entry.variantId === context.variantId);
    expect(item).toBeTruthy();
    expect(item.quantity).toBe(10);
    expect(item.threshold).toBe(40);
    expect(Math.abs(item.deficit)).toBe(30);
    expect(item.deficit).toBeLessThan(0);

    await context.cleanup();
  });

  test('should list items that are expiring soon', async ({ request }) => {
    const auth = await login(request);
    const context = await setupReportContext(request, auth.accessToken, {
      productExpiryDaysAhead: 7,
    });

    await createStockEvent(
      request,
      auth.accessToken,
      generateInboundEventPayload(context.warehouseId, [
        { variantId: context.variantId, quantity: 80 },
      ], { notes: 'Expiring inbound' }),
    );

    const response = await request.get('/api/v1/reports/expiring', {
      headers: authHeaders(auth.accessToken),
      params: {
        warehouseId: context.warehouseId,
        daysAhead: '10',
        page: '0',
        size: '50',
      },
    });

    expect(response.ok()).toBeTruthy();
    const page = await response.json();
    expect(Array.isArray(page.content)).toBeTruthy();
    const item = page.content.find((entry: any) => entry.variantId === context.variantId);
    expect(item).toBeTruthy();
    expect(item.quantity).toBe(80);
    expect(item.daysUntilExpiry).toBeGreaterThanOrEqual(0);

    await context.cleanup();
  });
});
