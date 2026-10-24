import { defineConfig } from "@playwright/test";
const port = Number(process.env.FRONTEND_TEST_PORT || 5174);
export default defineConfig({
  testDir: ".",
  testMatch: ["tests/**/*.spec.ts", "e2e/**/*.spec.ts"],
  workers: 1,
  fullyParallel: false,
  reporter: "list",
  timeout: 30000,
  projects: [
    { name: "api", testMatch: "tests/**/*.spec.ts" },
    {
      name: "chromium",
      testMatch: "e2e/**/*.spec.ts",
      use: {
        browserName: "chromium",
        channel: process.env.PLAYWRIGHT_CHANNEL || "msedge",
        baseURL: `http://127.0.0.1:${port}`,
        viewport: { width: 1440, height: 1000 },
        screenshot: "only-on-failure",
      },
    },
  ],
  webServer: process.argv.includes("--project=api")
    ? undefined
    : {
        command: `npm run dev -- --port ${port} --strictPort`,
        url: `http://127.0.0.1:${port}`,
        reuseExistingServer: false,
        timeout: 30000,
      },
});
