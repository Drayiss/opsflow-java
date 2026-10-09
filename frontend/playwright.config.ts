import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1,
  timeout: 60000,
  use: {
    baseURL: process.env.TEST_URL || "http://localhost:5174",
    headless: true,
    trace: "retain-on-failure",
  },
  reporter: "list",
});
