import { test, expect, APIRequestContext } from '@playwright/test';
import { authHeaders } from '../helpers/auth';

const DEFAULT_USERNAME = process.env.E2E_ADMIN_USERNAME ?? 'testuser';
const DEFAULT_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'testpass123';

interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  username: string;
  role: string;
  expiresIn: number;
}

async function attemptLogin(
  request: APIRequestContext,
  username: string,
  password: string,
) {
  return await request.post('/api/v1/auth/login', {
    data: { username, password },
  });
}

async function performLogin(
  request: APIRequestContext,
  username: string = DEFAULT_USERNAME,
  password: string = DEFAULT_PASSWORD,
): Promise<LoginResponse> {
  const response = await attemptLogin(request, username, password);
  expect(response.ok()).toBeTruthy();
  return await response.json();
}

async function refreshAccessToken(
  request: APIRequestContext,
  refreshToken: string,
) {
  return await request.post('/api/v1/auth/refresh', {
    data: { refreshToken },
  });
}

async function logout(request: APIRequestContext, refreshToken?: string) {
  return await request.post('/api/v1/auth/logout', {
    data: { refreshToken: refreshToken ?? null },
  });
}

test.describe('Auth API', () => {
  test.describe('Login', () => {
    test('should login successfully with valid credentials', async ({ request }) => {
      const response = await attemptLogin(request, DEFAULT_USERNAME, DEFAULT_PASSWORD);

      expect(response.ok()).toBeTruthy();
      const body: LoginResponse = await response.json();

      expect(body.accessToken).toBeTruthy();
      expect(body.refreshToken).toBeTruthy();
      expect(body.username).toBe(DEFAULT_USERNAME);
      expect(body.role).toBeTruthy();
      expect(body.expiresIn).toBeGreaterThan(0);
      expect(typeof body.accessToken).toBe('string');
      expect(typeof body.refreshToken).toBe('string');
    });

    test('should reject login with invalid username', async ({ request }) => {
      const response = await attemptLogin(request, 'nonexistent-user', DEFAULT_PASSWORD);

      expect(response.ok()).toBeFalsy();
      expect(response.status()).toBe(401);
    });

    test('should reject login with invalid password', async ({ request }) => {
      const response = await attemptLogin(request, DEFAULT_USERNAME, 'wrongpassword');

      expect(response.ok()).toBeFalsy();
      expect(response.status()).toBe(401);
    });

    test('should reject login with empty username', async ({ request }) => {
      const response = await attemptLogin(request, '', DEFAULT_PASSWORD);

      expect(response.ok()).toBeFalsy();
      expect(response.status()).toBeGreaterThanOrEqual(400);
      expect(response.status()).toBeLessThan(500);
    });

    test('should reject login with empty password', async ({ request }) => {
      const response = await attemptLogin(request, DEFAULT_USERNAME, '');

      expect(response.ok()).toBeFalsy();
      expect(response.status()).toBeGreaterThanOrEqual(400);
      expect(response.status()).toBeLessThan(500);
    });

    test('should reject login with missing credentials', async ({ request }) => {
      const response = await request.post('/api/v1/auth/login', {
        data: {},
      });

      expect(response.ok()).toBeFalsy();
      expect(response.status()).toBeGreaterThanOrEqual(400);
      expect(response.status()).toBeLessThan(500);
    });
  });

  test.describe('Refresh Token', () => {
    test('should refresh access token with valid refresh token', async ({ request }) => {
      const loginBody = await performLogin(request);
      const { refreshToken: originalRefreshToken } = loginBody;

      // Wait a bit to ensure different token timestamps
      await new Promise((resolve) => setTimeout(resolve, 1000));

      const refreshResponse = await refreshAccessToken(request, originalRefreshToken);

      expect(refreshResponse.ok()).toBeTruthy();
      const refreshBody: LoginResponse = await refreshResponse.json();

      expect(refreshBody.accessToken).toBeTruthy();
      expect(refreshBody.refreshToken).toBeTruthy();
      expect(refreshBody.refreshToken).toBe(originalRefreshToken); // Refresh token stays the same
      expect(refreshBody.accessToken).not.toBe(loginBody.accessToken);
      expect(refreshBody.username).toBe(DEFAULT_USERNAME);
      expect(refreshBody.role).toBeTruthy();
      expect(refreshBody.expiresIn).toBeGreaterThan(0);
    });

    test('should reject refresh with invalid refresh token', async ({ request }) => {
      const response = await refreshAccessToken(request, 'invalid-refresh-token');

      expect(response.ok()).toBeFalsy();
      expect(response.status()).toBeGreaterThanOrEqual(400);
    });

    test('should reject refresh with empty refresh token', async ({ request }) => {
      const response = await refreshAccessToken(request, '');

      expect(response.ok()).toBeFalsy();
      expect(response.status()).toBeGreaterThanOrEqual(400);
      expect(response.status()).toBeLessThan(500);
    });

    test('should reject refresh with missing refresh token', async ({ request }) => {
      const response = await request.post('/api/v1/auth/refresh', {
        data: {},
      });

      expect(response.ok()).toBeFalsy();
      expect(response.status()).toBeGreaterThanOrEqual(400);
      expect(response.status()).toBeLessThan(500);
    });

    test('should issue new tokens that work for authenticated requests', async ({ request }) => {
      const loginBody = await performLogin(request);
      const refreshResponse = await refreshAccessToken(request, loginBody.refreshToken);

      expect(refreshResponse.ok()).toBeTruthy();
      const refreshBody: LoginResponse = await refreshResponse.json();

      // Verify new access token works by making an authenticated request
      const testResponse = await request.get('/api/v1/test/authenticated', {
        headers: authHeaders(refreshBody.accessToken),
      });

      expect(testResponse.ok()).toBeTruthy();
    });
  });

  test.describe('Logout', () => {
    test('should logout successfully with valid refresh token', async ({ request }) => {
      const loginBody = await performLogin(request);
      const logoutResponse = await logout(request, loginBody.refreshToken);

      expect(logoutResponse.status()).toBe(204);
      expect(await logoutResponse.text()).toBe('');
    });

    test('should logout successfully with null refresh token', async ({ request }) => {
      const logoutResponse = await logout(request, null);

      expect(logoutResponse.status()).toBe(204);
    });

    test('should logout successfully with undefined refresh token', async ({ request }) => {
      const logoutResponse = await logout(request);

      expect(logoutResponse.status()).toBe(204);
    });

    test('should invalidate refresh token after logout', async ({ request }) => {
      const loginBody = await performLogin(request);
      const { refreshToken } = loginBody;

      const logoutResponse = await logout(request, refreshToken);
      expect(logoutResponse.status()).toBe(204);

      // Attempt to use the same refresh token should fail
      const refreshResponse = await refreshAccessToken(request, refreshToken);
      expect(refreshResponse.ok()).toBeFalsy();
    });

    test('should reject logout with invalid refresh token', async ({ request }) => {
      // Logout with invalid token should fail
      const logoutResponse = await logout(request, 'invalid-token');

      expect(logoutResponse.ok()).toBeFalsy();
      expect(logoutResponse.status()).toBeGreaterThanOrEqual(400);
    });
  });

  test.describe('Authentication Flow', () => {
    test('should complete full authentication cycle', async ({ request }) => {
      // 1. Login
      const loginResponse = await attemptLogin(request, DEFAULT_USERNAME, DEFAULT_PASSWORD);
      expect(loginResponse.ok()).toBeTruthy();
      const loginBody: LoginResponse = await loginResponse.json();

      const { accessToken: token1, refreshToken: refresh1 } = loginBody;

      // 2. Use access token for authenticated request
      const authResponse1 = await request.get('/api/v1/test/authenticated', {
        headers: authHeaders(token1),
      });
      expect(authResponse1.ok()).toBeTruthy();

      // Wait to ensure different token timestamp
      await new Promise((resolve) => setTimeout(resolve, 1000));

      // 3. Refresh token
      const refreshResponse = await refreshAccessToken(request, refresh1);
      expect(refreshResponse.ok()).toBeTruthy();
      const refreshBody: LoginResponse = await refreshResponse.json();

      const { accessToken: token2, refreshToken: refresh2 } = refreshBody;

      // 4. Use new access token
      const authResponse2 = await request.get('/api/v1/test/authenticated', {
        headers: authHeaders(token2),
      });
      expect(authResponse2.ok()).toBeTruthy();

      // 5. Logout
      const logoutResponse = await logout(request, refresh2);
      expect(logoutResponse.status()).toBe(204);

      // 6. Verify refresh token is invalidated
      const postLogoutRefresh = await refreshAccessToken(request, refresh2);
      expect(postLogoutRefresh.ok()).toBeFalsy();
    });

    test('should reject unauthenticated requests to protected endpoints', async ({ request }) => {
      const response = await request.get('/api/v1/test/authenticated');

      expect(response.ok()).toBeFalsy();
      expect(response.status()).toBe(403); // Spring Security returns 403 for missing auth
    });

    test('should reject requests with invalid access token', async ({ request }) => {
      const response = await request.get('/api/v1/test/authenticated', {
        headers: authHeaders('invalid-access-token'),
      });

      expect(response.ok()).toBeFalsy();
      expect(response.status()).toBe(403); // Spring Security returns 403 for invalid auth
    });

    test('should reject requests with expired or malformed bearer token', async ({ request }) => {
      const response = await request.get('/api/v1/test/authenticated', {
        headers: {
          Authorization: 'Bearer expired.jwt.token',
        },
      });

      expect(response.ok()).toBeFalsy();
      expect(response.status()).toBe(403); // Spring Security returns 403 for malformed auth
    });

    test('should allow access to public endpoints without authentication', async ({ request }) => {
      const response = await request.get('/api/v1/test/public');

      expect(response.ok()).toBeTruthy();
    });
  });

  test.describe('Token Properties', () => {
    test('should return tokens with expected structure', async ({ request }) => {
      const loginBody = await performLogin(request);

      expect(loginBody).toHaveProperty('accessToken');
      expect(loginBody).toHaveProperty('refreshToken');
      expect(loginBody).toHaveProperty('username');
      expect(loginBody).toHaveProperty('role');
      expect(loginBody).toHaveProperty('expiresIn');

      expect(loginBody.accessToken.length).toBeGreaterThan(20);
      expect(loginBody.refreshToken.length).toBeGreaterThan(20);
    });

    test('should return different access and refresh tokens', async ({ request }) => {
      const loginBody = await performLogin(request);

      expect(loginBody.accessToken).not.toBe(loginBody.refreshToken);
    });

    test('should issue new access tokens on each refresh', async ({ request }) => {
      const loginBody = await performLogin(request);

      // Wait to ensure different timestamps
      await new Promise((resolve) => setTimeout(resolve, 1000));

      const refresh1Response = await refreshAccessToken(request, loginBody.refreshToken);
      expect(refresh1Response.ok()).toBeTruthy();
      const refresh1Body: LoginResponse = await refresh1Response.json();

      // Wait again
      await new Promise((resolve) => setTimeout(resolve, 1000));

      const refresh2Response = await refreshAccessToken(request, refresh1Body.refreshToken);
      expect(refresh2Response.ok()).toBeTruthy();
      const refresh2Body: LoginResponse = await refresh2Response.json();

      // Access tokens should be different
      expect(refresh1Body.accessToken).not.toBe(loginBody.accessToken);
      expect(refresh2Body.accessToken).not.toBe(refresh1Body.accessToken);

      // Refresh tokens remain the same (not rotated in this implementation)
      expect(refresh1Body.refreshToken).toBe(loginBody.refreshToken);
      expect(refresh2Body.refreshToken).toBe(refresh1Body.refreshToken);
    });
  });
});
