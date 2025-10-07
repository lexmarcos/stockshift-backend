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
  idempotencyKey?: string;
}

export async function createStockEvent(
  request: APIRequestContext,
  token: string,
  payload: StockEventPayload,
) {
  const headers = authHeaders(token);
  if (payload.idempotencyKey) {
    headers['Idempotency-Key'] = payload.idempotencyKey;
  }

  const response = await request.post('/api/v1/stock-events', {
    headers,
    data: {
      type: payload.type,
      warehouseId: payload.warehouseId,
      occurredAt: payload.occurredAt,
      reasonCode: payload.reasonCode,
      notes: payload.notes,
      lines: payload.lines,
    },
  });

  expect(response.status()).toBe(201);
  return response.json();
}
