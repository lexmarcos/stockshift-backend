import { APIRequestContext, expect } from '@playwright/test';
import { authHeaders } from './auth';

export type StockEventType = 'INBOUND' | 'OUTBOUND' | 'ADJUST';
export type StockReasonCode =
  | 'PURCHASE'
  | 'SALE'
  | 'COUNT_CORRECTION'
  | 'DAMAGE'
  | 'DISCARD_EXPIRED'
  | 'OTHER';

export interface StockEventLinePayload {
  variantId: string;
  quantity: number;
}

export interface StockEventPayload {
  type: StockEventType;
  warehouseId: string;
  occurredAt?: string;
  reasonCode?: StockReasonCode;
  notes?: string;
  lines: StockEventLinePayload[];
}

export function generateInboundEventPayload(
  warehouseId: string,
  lines: StockEventLinePayload[],
  overrides: Partial<StockEventPayload> = {},
): StockEventPayload {
  if (!warehouseId) {
    throw new Error('warehouseId is required to generate stock event payloads');
  }
  if (!lines.length) {
    throw new Error('lines are required to generate stock event payloads');
  }

  const base: StockEventPayload = {
    type: 'INBOUND',
    warehouseId,
    occurredAt: overrides.occurredAt ?? new Date().toISOString(),
    reasonCode: overrides.reasonCode ?? 'PURCHASE',
    notes: overrides.notes ?? 'E2E inbound restock',
    lines,
  };

  return { ...base, ...overrides, warehouseId, lines };
}

export function generateStockEventPayload(
  type: StockEventType,
  warehouseId: string,
  lines: StockEventLinePayload[],
  overrides: Partial<StockEventPayload> = {},
): StockEventPayload {
  if (!warehouseId) {
    throw new Error('warehouseId is required to generate stock event payloads');
  }
  if (!lines.length) {
    throw new Error('lines are required to generate stock event payloads');
  }

  const base: StockEventPayload = {
    type,
    warehouseId,
    occurredAt: overrides.occurredAt ?? new Date().toISOString(),
    reasonCode: overrides.reasonCode ?? 'OTHER',
    notes: overrides.notes ?? `E2E ${type.toLowerCase()} event`,
    lines,
  };

  return { ...base, ...overrides, warehouseId, lines, type };
}

export async function createStockEvent(
  request: APIRequestContext,
  token: string,
  payload: StockEventPayload,
) {
  const response = await request.post('/api/v1/stock-events', {
    headers: authHeaders(token),
    data: payload,
  });

  expect(response.status()).toBe(201);
  return response.json();
}

export async function getStockEvent(
  request: APIRequestContext,
  token: string,
  eventId: string,
) {
  const response = await request.get(`/api/v1/stock-events/${eventId}`, {
    headers: authHeaders(token),
  });

  expect(response.ok()).toBeTruthy();
  return response.json();
}

export async function listStockEvents(
  request: APIRequestContext,
  token: string,
  searchParams?: Record<string, string>,
) {
  const response = await request.get('/api/v1/stock-events', {
    headers: authHeaders(token),
    params: searchParams,
  });

  expect(response.ok()).toBeTruthy();
  return response.json();
}
