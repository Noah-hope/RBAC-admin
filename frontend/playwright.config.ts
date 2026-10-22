import { defineConfig } from "@playwright/test";
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
        baseURL: "http://127.0.0.1:5174",
        viewport: { width: 1440, height: 1000 },
        screenshot: "only-on-failure",
      },
    },
  ],
  webServer: process.argv.includes("--project=api")
    ? undefined
    : {
        command: "npm run dev -- --strictPort",
        url: "http://127.0.0.1:5174",
        reuseExistingServer: false,
        timeout: 30000,
      },
});
