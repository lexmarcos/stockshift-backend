import { APIRequestContext, expect } from '@playwright/test';
import { authHeaders } from './auth';

export interface UserPayload {
  username: string;
  email: string;
  password: string;
  role: 'ADMIN' | 'MANAGER' | 'SELLER';
}

export function generateUserPayload(role: UserPayload['role'] = 'SELLER'): UserPayload {
  const suffix = Math.random().toString(36).slice(2, 8);
  return {
    username: `e2e_${suffix}`,
    email: `e2e_${suffix}@example.com`,
    password: 'Password!123',
    role,
  };
}

export async function createUser(request: APIRequestContext, token: string, payload: UserPayload) {
  const response = await request.post('/api/v1/users', {
    headers: authHeaders(token),
    data: payload,
  });

  expect(response.status()).toBe(201);
  return response.json();
}

export async function deleteUser(request: APIRequestContext, token: string, userId: string) {
  const response = await request.delete(`/api/v1/users/${userId}`, {
    headers: authHeaders(token),
  });

  expect(response.status()).toBe(204);
}
