import { APIRequestContext, expect, APIResponse } from '@playwright/test';
import { authHeaders } from './auth';

export interface TransferLinePayload {
  variantId: string;
  quantity: number;
}

export interface TransferPayload {
  originWarehouseId: string;
  destinationWarehouseId: string;
  occurredAt?: string;
  notes?: string | null;
  lines: TransferLinePayload[];
}

export async function createTransferDraft(
  request: APIRequestContext,
  token: string,
  payload: TransferPayload,
) {
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
  options: { idempotencyKey?: string } = {},
) {
  const response = await request.post(`/api/v1/stock-transfers/${transferId}/confirm`, {
    headers: {
      ...authHeaders(token),
      ...(options.idempotencyKey ? { 'Idempotency-Key': options.idempotencyKey } : {}),
    },
  });

  expect(response.ok()).toBeTruthy();
  return response.json();
}

export async function cancelTransfer(
  request: APIRequestContext,
  token: string,
  transferId: string,
) {
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
) {
  const response = await request.get(`/api/v1/stock-transfers/${transferId}`, {
    headers: authHeaders(token),
  });

  expect(response.ok()).toBeTruthy();
  return response.json();
}

export async function listTransfers(
  request: APIRequestContext,
  token: string,
  searchParams?: Record<string, string>,
): Promise<APIResponse> {
  return request.get('/api/v1/stock-transfers', {
    headers: authHeaders(token),
    params: searchParams,
  });
}
