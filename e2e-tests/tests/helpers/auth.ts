import { APIRequestContext, expect } from '@playwright/test';

export interface AuthContext {
  accessToken: string;
  username: string;
  role: string;
}

const DEFAULT_USERNAME = process.env.E2E_ADMIN_USERNAME ?? 'testuser';
const DEFAULT_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'testpass123';

export async function login(request: APIRequestContext): Promise<AuthContext> {
  const response = await request.post('/api/v1/auth/login', {
    data: {
      username: DEFAULT_USERNAME,
      password: DEFAULT_PASSWORD,
    },
  });

  expect(response.ok()).toBeTruthy();

  const body = await response.json();

  return {
    accessToken: body.accessToken as string,
    username: body.username as string,
    role: body.role as string,
  };
}

export function authHeaders(token: string) {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };
}
