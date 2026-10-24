import { test, expect } from "@playwright/test";
test("application entry renders without script errors", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  page.on("console", (m) => {
    if (m.type() === "error") errors.push(m.text());
  });
  await page.goto("/");
  await page.waitForTimeout(1500);
  expect(errors).toEqual([]);
  await expect(
    page.getByRole("heading", { name: "登录权限中心" }),
  ).toBeVisible();
});
