import { defineConfig, devices } from '@playwright/test';

const baseURL = process.env.API_BASE_URL ?? 'http://localhost:8080';

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  timeout: 30 * 1000,
  workers: 1,
  expect: {
    timeout: 5 * 1000,
  },
  use: {
    baseURL,
  },
  projects: [
    {
      name: 'api',
      testIgnore: ['users/**/*.spec.ts'],
      use: {
        // Even for API tests we can reuse default settings
      },
    },
    {
      name: 'api-users',
      testMatch: ['users/**/*.spec.ts'],
      fullyParallel: false,
      workers: 1,
    },
  ],
  reporter: [['list']],
});
