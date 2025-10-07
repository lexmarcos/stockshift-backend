import { APIRequestContext, expect } from '@playwright/test';
import { authHeaders } from './auth';

export type TransferStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELED';

export interface TransferLinePayload {
  variantId: string;
  quantity: number;
}

export interface TransferPayload {
  originWarehouseId: string;
  destinationWarehouseId: string;
  occurredAt?: string;
  notes?: string;
  lines: TransferLinePayload[];
}

export interface TransferResponse {
  id: string;
  originWarehouseId: string;
  originWarehouseCode: string;
  destinationWarehouseId: string;
  destinationWarehouseCode: string;
  status: TransferStatus;
  occurredAt: string;
  notes?: string;
  createdById: string;
  createdByUsername: string;
  createdAt: string;
  confirmedById?: string;
  confirmedByUsername?: string;
  confirmedAt?: string;
  outboundEventId?: string;
  inboundEventId?: string;
  lines: Array<{
    variantId: string;
    quantity: number;
  }>;
}

export function generateTransferPayload(
  originWarehouseId: string,
  destinationWarehouseId: string,
  lines: TransferLinePayload[],
  overrides: Partial<TransferPayload> = {},
): TransferPayload {
  const base: TransferPayload = {
    originWarehouseId,
    destinationWarehouseId,
    notes: `E2E transfer ${Math.random().toString(36).slice(2, 8)}`,
    lines,
  };

  return { ...base, ...overrides };
}

export async function createTransferDraft(
  request: APIRequestContext,
  token: string,
  payload: TransferPayload,
): Promise<TransferResponse> {
  const response = await request.post('/api/v1/stock-transfers', {
    headers: authHeaders(token),
    data: payload,
  });

  expect(response.status()).toBe(201);
  return response.json();
}

export async function confirmTransfer(
  request: APIRequestContext,
  token: string,
  transferId: string,
  idempotencyKey?: string,
): Promise<TransferResponse> {
  const headers = authHeaders(token);
  if (idempotencyKey) {
    headers['Idempotency-Key'] = idempotencyKey;
  }

  const response = await request.post(`/api/v1/stock-transfers/${transferId}/confirm`, {
    headers,
  });

  expect(response.ok()).toBeTruthy();
  return response.json();
}

export async function cancelTransfer(
  request: APIRequestContext,
  token: string,
  transferId: string,
): Promise<TransferResponse> {
  const response = await request.post(`/api/v1/stock-transfers/${transferId}/cancel`, {
    headers: authHeaders(token),
  });

  expect(response.ok()).toBeTruthy();
  return response.json();
}

export async function getTransfer(
  request: APIRequestContext,
  token: string,
  transferId: string,
): Promise<TransferResponse> {
  const response = await request.get(`/api/v1/stock-transfers/${transferId}`, {
    headers: authHeaders(token),
  });

  expect(response.ok()).toBeTruthy();
  return response.json();
}

export async function listTransfers(
  request: APIRequestContext,
  token: string,
  params: {
    status?: TransferStatus;
    originWarehouseId?: string;
    destinationWarehouseId?: string;
    occurredFrom?: string;
    occurredTo?: string;
    page?: number;
    size?: number;
    sort?: string;
  } = {},
) {
  const queryParams: string[] = [];

  if (params.status) queryParams.push(`status=${params.status}`);
  if (params.originWarehouseId) queryParams.push(`originWarehouseId=${params.originWarehouseId}`);
  if (params.destinationWarehouseId) queryParams.push(`destinationWarehouseId=${params.destinationWarehouseId}`);
  if (params.occurredFrom) queryParams.push(`occurredFrom=${params.occurredFrom}`);
  if (params.occurredTo) queryParams.push(`occurredTo=${params.occurredTo}`);
  if (params.page !== undefined) queryParams.push(`page=${params.page}`);
  if (params.size !== undefined) queryParams.push(`size=${params.size}`);
  if (params.sort) queryParams.push(`sort=${params.sort}`);

  const queryString = queryParams.join('&');
  const url = queryString ? `/api/v1/stock-transfers?${queryString}` : '/api/v1/stock-transfers';

  const response = await request.get(url, {
    headers: authHeaders(token),
  });

  if (!response.ok()) {
    const text = await response.text();
    throw new Error(`List transfers failed with status ${response.status()}: ${text}`);
  }

  return response.json();
}
