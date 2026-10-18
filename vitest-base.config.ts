import { defineConfig } from 'vitest/config';

const maxWorkers = Number(process.env['VITEST_MAX_WORKERS'] ?? 2);

export default defineConfig({
  test: {
    testTimeout: 20000,
    hookTimeout: 20000,
    pool: 'forks',
    maxWorkers,
    execArgv: ['--max-old-space-size=4096'],
  },
});
