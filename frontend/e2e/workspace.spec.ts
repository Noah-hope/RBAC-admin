import { monitor } from "./contract";
import { test, expect, type Page } from "@playwright/test";
let frontendErrors: string[] = [];
test.beforeEach(({ page }) => {
  frontendErrors = monitor(page);
});
test.afterEach(() => expect(frontendErrors).toEqual([]));
const session = {
  sessionId: "s1",
  accountId: "a1",
  employeeId: null,
  identity: "SUPER_ADMIN",
  mode: "NORMAL",
  accessToken: "fixture-only-not-valid",
  tokenType: "Bearer",
  expiresAt: "2099-01-01T00:00:00Z",
};
const employee = {
  id: "emp1",
  employeeKey: "person.one",
  name: "测试员工",
  status: "ACTIVE",
  version: 2,
  createdAt: "2026-10-01T00:00:00Z",
};
async function setup(page: Page, override: Record<string, unknown> = {}) {
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    let data: unknown = {
      items: [],
      page: Number(url.searchParams.get("page") || 1),
      pageSize: 20,
      total: 0,
    };
    let status = 200;
    if (path === "/api/v1/admin/sessions") {
      data = { ...session, ...override };
      status = 201;
    }
    if (path.endsWith("/sessions/current")) data = session;
    if (path.endsWith("/employees"))
      data = { items: [employee], page: 1, pageSize: 20, total: 1 };
    if (path.endsWith("/companies"))
      data = {
        items: [{ id: "co1", code: "CO", name: "示例公司", version: 0 }],
        page: 1,
        pageSize: 20,
        total: 1,
      };
    if (path.endsWith("/departments"))
      data = {
        items: [
          {
            id: "dep1",
            companyId: "co1",
            code: "DEP",
            name: "工程部",
            version: 0,
          },
        ],
        page: 1,
        pageSize: 20,
        total: 1,
      };
    if (path.endsWith("/my-appointments"))
      data = {
        items: [
          {
            id: "ap1",
            scopeId: "sc1",
            accountId: "a1",
            employeeId: "emp1",
            status: "ACTIVE",
            version: 2,
            scopeVersion: 3,
            companyId: "co1",
            departmentId: "dep1",
            systemIds: ["sys1"],
          },
        ],
        page: 1,
        pageSize: 20,
        total: 1,
      };
    if (path.endsWith("/password")) {
      status = 204;
      data = undefined;
    }
    await route.fulfill({
      status,
      contentType: "application/json",
      body: status === 204 ? undefined : JSON.stringify(data),
    });
  });
  await page.goto("/");
}
async function login(page: Page) {
  await page.getByLabel("登录名", { exact: true }).fill("test.user");
  await page.getByLabel("密码", { exact: true }).fill("fixture-password");
  await page.getByRole("button", { name: "登录", exact: true }).click();
}
test("real login shows employee response, keeps token out of storage and refresh requires login", async ({
  page,
}) => {
  await setup(page);
  let authorization = "unset";
  page.on("request", (r) => {
    if (r.url().endsWith("/sessions"))
      authorization = r.headers().authorization || "";
  });
  await login(page);
  await expect(
    page.getByRole("heading", { name: "员工授权", exact: true }),
  ).toBeVisible();
  await expect(page.getByText("测试员工", { exact: true })).toBeVisible();
  expect(authorization).toBe("");
  expect(
    await page.evaluate(() => [localStorage.length, sessionStorage.length]),
  ).toEqual([0, 0]);
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "登录权限中心" }),
  ).toBeVisible();
});
test("restricted token can only change password; successful change returns to login", async ({
  page,
}) => {
  await setup(page, { mode: "PASSWORD_CHANGE_ONLY" });
  await login(page);
  await expect(
    page.getByRole("heading", { name: "首次登录，请修改密码" }),
  ).toBeVisible();
  await expect(page.getByRole("navigation")).toHaveCount(0);
  await page.getByLabel("当前密码").fill("fixture-password");
  await page.getByLabel("新密码", { exact: true }).fill("next-password-long");
  await page.getByLabel("确认新密码").fill("next-password-long");
  await page.getByRole("button", { name: "修改密码并重新登录" }).click();
  await expect(
    page.getByRole("heading", { name: "登录权限中心" }),
  ).toBeVisible();
});
test("permission administrator uses appointment context and cannot open platform pages", async ({
  page,
}) => {
  await setup(page, { identity: "PERMISSION_ADMIN", employeeId: "emp1" });
  const request = page.waitForRequest((r) => r.url().includes("/employees"));
  await login(page);
  const r = await request;
  expect(r.headers()["x-admin-appointment-id"]).toBe("ap1");
  expect(r.headers()["x-admin-scope-version"]).toBe("3");
  await expect(page.getByRole("link", { name: "管理员与范围" })).toHaveCount(0);
  await page.goto("/#/administrators");
  await expect(page.getByText("此页面仅总管理员可使用")).toBeVisible();
});
test("401 discards the current workspace and displays reauthentication guidance", async ({
  page,
}) => {
  await setup(page);
  await login(page);
  await expect(page.getByText("测试员工", { exact: true })).toBeVisible();
  await page.route("**/api/v1/admin/employees?**", (r) =>
    r.fulfill({
      status: 401,
      contentType: "application/problem+json",
      body: JSON.stringify({
        code: "SESSION_REVOKED",
        detail: "会话失效",
        requestId: "test-request",
      }),
    }),
  );
  await page.getByRole("button", { name: "刷新列表" }).click();
  await expect(
    page.getByRole("heading", { name: "登录权限中心" }),
  ).toBeVisible();
  await expect(page.getByRole("alert")).toContainText("重新登录");
});
test("responsive navigation and empty/error states remain usable", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await setup(page);
  await login(page);
  await page.getByRole("button", { name: "打开导航" }).click();
  await page.getByRole("link", { name: "审计记录", exact: true }).click();
  await expect(page.getByText("暂无符合条件的记录")).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
});
test("failed logout keeps workspace and shows actionable error", async ({
  page,
}) => {
  await setup(page);
  await login(page);
  await expect(page.getByText("测试员工", { exact: true })).toBeVisible();
  await page.route("**/api/v1/admin/sessions/current", (r) =>
    r.fulfill({
      status: 503,
      contentType: "application/problem+json",
      body: JSON.stringify({
        code: "UNAVAILABLE",
        detail: "退出暂时不可用，请重试",
        requestId: "logout-test",
      }),
    }),
  );
  await page.getByRole("button", { name: "退出登录", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("退出暂时不可用");
  await expect(
    page.getByRole("heading", { name: "员工授权", exact: true }),
  ).toBeVisible();
});
test("capture desktop and mobile layouts with controlled API data", async ({
  page,
}, info) => {
  await setup(page);
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: info.outputPath("login.png"), fullPage: true });
  await login(page);
  await expect(page.getByText("测试员工", { exact: true })).toBeVisible();
  await page.screenshot({
    path: info.outputPath("desktop.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: "打开导航" }).click();
  await page.screenshot({
    path: info.outputPath("mobile.png"),
    fullPage: true,
  });
});
