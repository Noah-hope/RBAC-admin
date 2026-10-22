import { monitor } from "./contract";
import { test, expect, type Page } from "@playwright/test";
let frontendErrors: string[] = [];
test.beforeEach(({ page }) => {
  frontendErrors = monitor(page);
});
test.afterEach(() => expect(frontendErrors).toEqual([]));
const time = "2026-10-01T00:00:00Z";
const role = {
  id: "role1",
  code: "R001",
  name: "工程查看员",
  description: "",
  companyId: "co1",
  departmentId: "dep1",
  systemId: "sys1",
  state: "EFFECTIVE",
  metadataVersion: 2,
  configurationVersion: 3,
  permissionIds: ["access"],
  activeChangeRequestId: null,
  createdAt: time,
};
const preview = {
  id: "preview1",
  contentDigest: "a".repeat(64),
  expiresAt: "2099-01-01T00:00:00Z",
  impact: {
    visibleEmployeeCount: 2,
    visibleLosingAccessCount: 0,
    visibleRetainingAccessCount: 2,
    visibleGainingAccessCount: 0,
    visibility: "COMPLETE",
    hasUndisclosedImpact: false,
    warning: "",
    visibleRoleCount: 1,
    visibleGrantCount: 2,
    visibleSourceCount: 1,
    visibleAppointmentCount: 0,
    visiblePendingRequestCount: 0,
  },
  warnings: [],
};
let draft: any;
async function boot(page: Page) {
  draft = {
    id: "draft1",
    content: {
      kind: "ROLE_CONFIGURATION",
      companyId: "co1",
      roleId: "role1",
      expectedConfigurationVersion: 3,
      permissionIds: ["access"],
    },
    reason: "日常配置",
    draftVersion: 4,
    submittedVersion: null,
    contentDigest: null,
    statusVersion: 0,
    state: "DRAFT",
    deliveryStatus: "NOT_SUBMITTED",
    approvalResult: "NONE",
    executionStatus: "NOT_STARTED",
    originalAuthority: null,
    submittedByAccountId: null,
    oaApprovalId: null,
    retryable: false,
    failureCode: null,
    nextRetryAt: null,
    createdAt: time,
    submittedAt: null,
  };
  await page.route("**/api/v1/**", async (r) => {
    const u = new URL(r.request().url()),
      p = u.pathname;
    let result: any = { items: [], page: 1, pageSize: 20, total: 0 };
    let status = 200;
    if (p.endsWith("/sessions")) {
      result = {
        sessionId: "s",
        accountId: "a",
        employeeId: null,
        identity: "SUPER_ADMIN",
        mode: "NORMAL",
        accessToken: "not-a-real-token",
        tokenType: "Bearer",
        expiresAt: "2099-01-01T00:00:00Z",
      };
      status = 201;
    }
    const lists: Record<string, any[]> = {
      roles: [role],
      companies: [{ id: "co1", code: "CO", name: "示例公司", version: 1 }],
      departments: [
        {
          id: "dep1",
          companyId: "co1",
          code: "DEP",
          name: "工程部",
          version: 1,
        },
      ],
      systems: [
        {
          id: "sys1",
          code: "SYS",
          name: "业务系统",
          description: "",
          enabled: true,
          version: 1,
          catalogVersion: 1,
          systemAccessPermissionId: "access",
          createdAt: time,
        },
      ],
      permissions: [
        {
          id: "access",
          systemId: "sys1",
          code: "SYS:access",
          name: "系统进入",
          permissionType: "SYSTEM_ACCESS",
          functionId: null,
          menuEligible: false,
          enabled: true,
          version: 1,
        },
        {
          id: "view",
          systemId: "sys1",
          code: "order:view",
          name: "查看订单",
          permissionType: "FUNCTION_OPERATION",
          functionId: "fn1",
          menuEligible: true,
          enabled: true,
          version: 1,
        },
      ],
      "change-requests": [draft],
      accounts: [
        {
          id: "a2",
          employeeId: "emp1",
          loginName: "member.one",
          enabled: true,
          mustChangePassword: false,
          version: 7,
          createdAt: time,
        },
      ],
      employees: [
        {
          id: "emp1",
          employeeKey: "person.one",
          name: "测试员工",
          status: "ACTIVE",
          version: 2,
          createdAt: time,
        },
      ],
    };
    if (r.request().method() === "GET" && lists[p.split("/").at(-1)!]) {
      const items = lists[p.split("/").at(-1)!];
      result = { items, total: items.length, page: 1, pageSize: 20 };
    }
    if (p.endsWith("/roles/role1")) result = role;
    if (p.endsWith("/systems/sys1")) result = lists.systems[0];
    if (p.endsWith("/accounts/a2")) result = lists.accounts[0];
    if (p.endsWith("/employees/emp1")) result = lists.employees[0];
    if (p === "/api/v1/admin/previews/preview1/employees")
      result = {
        items: [
          {
            employeeId: "emp1",
            companyId: "co1",
            systemId: "sys1",
            canAccessBefore: true,
            canAccessAfter: true,
            resultCompleteness: "COMPLETE",
            visibleGrantIds: [],
          },
        ],
        page: 1,
        pageSize: 20,
        total: 1,
      };
    if (p.endsWith("/change-requests/draft1")) {
      if (r.request().method() === "PUT") {
        draft = { ...draft, ...r.request().postDataJSON(), draftVersion: 5 };
      }
      result = draft;
    }
    if (p.endsWith("/change-requests") && r.request().method() === "POST") {
      draft = { ...draft, ...r.request().postDataJSON() };
      result = draft;
      status = 201;
    }
    if (p.endsWith("/previews"))
      result = {
        preview,
        draftId: "draft1",
        draftVersion: draft.draftVersion,
        addedPermissionIds: ["view"],
        removedPermissionIds: [],
        effectiveContent: draft.content,
        noChangeItems: [],
      };
    if (p.endsWith("/submissions")) {
      result = {
        outcome: "SUBMITTED",
        request: { ...draft, state: "PENDING", deliveryStatus: "PENDING_SEND" },
        noChangeItems: [],
      };
      status = 202;
    }
    if (p.endsWith("/management-scope-previews")) result = preview;
    if (
      p.endsWith("/catalog-previews") ||
      p.endsWith("/source-closure-previews") ||
      p.endsWith("/state-previews")
    )
      result = preview;
    if (p.endsWith("/catalog-changes"))
      result = {
        systemId: "sys1",
        catalogVersion: 2,
        outcome: "APPLIED",
        createdIds: [],
        auditEventIds: [],
      };
    if (p.endsWith("/source-baselines"))
      result = {
        items: [
          {
            source: {
              employeeId: "emp1",
              companyId: "co1",
              departmentId: "dep1",
              systemId: "sys1",
            },
            sourceEpoch: 4,
            version: 6,
          },
        ],
      };
    if (p.endsWith("/source-closures"))
      result = {
        outcome: "APPLIED",
        closedSourceCount: 1,
        invalidatedGrantCount: 1,
        invalidatedRequestCount: 0,
        visibility: "COMPLETE",
        hasUndisclosedImpact: false,
        auditEventIds: [],
      };
    if (p.endsWith("/grant-baselines"))
      result = {
        items: [
          {
            key: {
              employeeId: "emp1",
              roleId: "role1",
              sourceCompanyId: "co1",
              sourceDepartmentId: "dep1",
            },
            systemId: "sys1",
            roleConfigurationVersion: 3,
            sourceEpoch: 4,
            relationVersion: 6,
            employmentIds: ["job1", "job2"],
            activeGrantId: null,
          },
        ],
      };
    if (p.endsWith("/management-scopes") && r.request().method() === "POST") {
      result = {
        scope: {
          id: "sc1",
          ...r.request().postDataJSON().input,
          version: 0,
          createdAt: time,
        },
        auditEventIds: [],
        outcome: "APPLIED",
        appointmentsChangedCount: 1,
      };
      status = 201;
    }
    if (p.endsWith("/accounts/a2/password")) result = lists.accounts[0];
    if (p.endsWith("/import-files") && r.request().method() === "POST") {
      result = {
        id: "file1",
        fileName: "org.csv",
        sha256: "a".repeat(64),
        sizeBytes: 20,
        sheetNames: ["CSV"],
        createdAt: time,
      };
      status = 201;
    }
    if (
      (p.endsWith("/import-batches") && r.request().method() === "POST") ||
      p.endsWith("/import-batches/batch1") ||
      p.endsWith("/import-batches/batch1/commits")
    ) {
      result = {
        id: "batch1",
        input:
          r.request().method() === "POST" && p.endsWith("/import-batches")
            ? r.request().postDataJSON()
            : {},
        version: 2,
        contentDigest: "b".repeat(64),
        state: p.endsWith("/commits") ? "SUCCEEDED" : "READY",
        plannedCounts: {
          created: 2,
          updated: 0,
          unchanged: 0,
          endedEmployments: 0,
          disabledEmployees: 0,
          restoredEmployees: 0,
          invalidatedGrants: 0,
          invalidatedAppointments: 0,
        },
        actualCounts: null,
        blockingIssueCount: 0,
        warningCount: 0,
        failureCode: null,
        createdAt: time,
      };
    }
    await r.fulfill({
      status,
      contentType: "application/json",
      body: JSON.stringify(result),
    });
  });
  await page.goto("/");
  await page.getByLabel("登录名", { exact: true }).fill("admin");
  await page.getByLabel("密码", { exact: true }).fill("not-real-password");
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "员工授权", exact: true }),
  ).toBeVisible();
}
test("role configuration creates an approval draft without pretending to grant immediately", async ({
  page,
}) => {
  await boot(page);
  await page.getByRole("link", { name: "角色权限", exact: true }).click();
  await page.getByRole("button", { name: "配置权限", exact: true }).click();
  await page.getByLabel("查看订单").check();
  await page.getByLabel("办理原因").fill("添加查看权限");
  const req = page.waitForRequest(
    (r) => r.method() === "POST" && r.url().endsWith("/change-requests"),
  );
  await page.getByRole("button", { name: "保存配置草稿" }).click();
  const body = (await req).postDataJSON();
  expect(body.content).toEqual({
    kind: "ROLE_CONFIGURATION",
    companyId: "co1",
    roleId: "role1",
    expectedConfigurationVersion: 3,
    permissionIds: ["access", "view"],
  });
  await expect(
    page.getByRole("heading", { name: "变更申请", exact: true }),
  ).toBeVisible();
});
test("draft submission displays impact and sends exact preview and draft version once", async ({
  page,
}) => {
  await boot(page);
  await page.getByRole("link", { name: "变更申请", exact: true }).click();
  await page.getByRole("button", { name: "详情", exact: true }).click();
  await page.getByRole("button", { name: "预览并提交", exact: true }).click();
  await expect(page.getByRole("heading", { name: "影响预览" })).toBeVisible();
  const req = page.waitForRequest((r) => r.url().endsWith("/submissions"));
  await page.getByRole("button", { name: "确认提交 OA" }).click();
  const r = await req;
  expect(r.postDataJSON()).toEqual({
    expectedDraftVersion: 4,
    previewId: "preview1",
  });
  expect(r.headers()["idempotency-key"]).toMatch(/^[a-f0-9-]{36}$/);
  await expect(page.getByText("已提交 OA，批准执行后才会生效")).toBeVisible();
});
test("account password reset uses loaded version and never displays password in result", async ({
  page,
}) => {
  await boot(page);
  await page.getByRole("link", { name: "管理员与范围" }).click();
  await page.getByRole("button", { name: "重置密码", exact: true }).click();
  await page.getByLabel("临时密码", { exact: true }).fill("new-fixture-pass");
  await page.getByLabel("确认密码", { exact: true }).fill("new-fixture-pass");
  await page.getByLabel("办理原因").fill("账号恢复");
  const req = page.waitForRequest((r) =>
    r.url().endsWith("/accounts/a2/password"),
  );
  await page.getByRole("button", { name: "保存", exact: true }).click();
  expect((await req).postDataJSON()).toMatchObject({
    expectedVersion: 7,
    temporaryPassword: "new-fixture-pass",
    confirmPassword: "new-fixture-pass",
  });
  await expect(page.getByRole("dialog")).toHaveCount(0);
});
test("all management pages load real lists without prototype data", async ({
  page,
}) => {
  await boot(page);
  for (const label of [
    "角色权限",
    "变更申请",
    "审计记录",
    "系统与权限目录",
    "管理员与范围",
    "员工组织",
  ]) {
    await page.getByRole("link", { name: label, exact: true }).click();
    await expect(
      page.getByRole("heading", { name: label, exact: true }),
    ).toBeVisible();
    await expect(page.getByRole("table").first()).toBeVisible();
  }
  await expect(page.getByText("虚构数据")).toHaveCount(0);
});
test("scope preview freezes selected arrays and submits the same reviewed input", async ({
  page,
}) => {
  await boot(page);
  await page.getByRole("link", { name: "管理员与范围" }).click();
  await page.getByRole("button", { name: "管理范围", exact: true }).click();
  await page.getByRole("button", { name: "创建管理范围" }).click();
  const dialog = page.getByRole("dialog");
  await dialog
    .getByRole("combobox", { name: "公司", exact: true })
    .selectOption("co1");
  await dialog
    .getByRole("combobox", { name: "管理部门", exact: true })
    .selectOption("dep1");
  await dialog
    .getByRole("combobox", { name: "可管理系统", exact: true })
    .selectOption("sys1");
  await dialog
    .getByRole("combobox", { name: "协作管理员账号", exact: true })
    .selectOption("a2");
  await dialog.getByLabel("办理原因", { exact: true }).fill("建立范围");
  const pre = page.waitForRequest((r) =>
    r.url().endsWith("/management-scope-previews"),
  );
  await dialog.getByRole("button", { name: "预览影响" }).click();
  const input = (await pre).postDataJSON();
  await expect(dialog.getByRole("heading", { name: "影响预览" })).toBeVisible();
  const req = page.waitForRequest(
    (r) => r.url().endsWith("/management-scopes") && r.method() === "POST",
  );
  await dialog.getByRole("button", { name: "确认办理" }).click();
  expect((await req).postDataJSON()).toEqual({ input, previewId: "preview1" });
  expect(input).toEqual({
    companyId: "co1",
    departmentId: "dep1",
    systemIds: ["sys1"],
    accountIds: ["a2"],
    reason: "建立范围",
  });
});
test("grant draft preserves all employment and relation baselines from the server", async ({
  page,
}) => {
  await boot(page);
  await page.getByRole("button", { name: "申请分配角色", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog
    .getByRole("combobox", { name: "来源公司", exact: true })
    .selectOption("co1");
  await dialog
    .getByRole("combobox", { name: "员工", exact: true })
    .selectOption("emp1");
  await dialog
    .getByRole("combobox", { name: "角色", exact: true })
    .selectOption("role1");
  await dialog
    .getByRole("combobox", { name: "来源部门", exact: true })
    .selectOption("dep1");
  await dialog.getByLabel("办理原因", { exact: true }).fill("入岗授权");
  const req = page.waitForRequest(
    (r) => r.url().endsWith("/change-requests") && r.method() === "POST",
  );
  await dialog.getByRole("button", { name: "保存分配草稿" }).click();
  expect((await req).postDataJSON().content).toEqual({
    kind: "ROLE_GRANT",
    companyId: "co1",
    items: [
      {
        employeeId: "emp1",
        roleId: "role1",
        sourceCompanyId: "co1",
        sourceDepartmentId: "dep1",
        expectedRoleConfigurationVersion: 3,
        expectedSourceEpoch: 4,
        expectedRelationVersion: 6,
        employmentIds: ["job1", "job2"],
      },
    ],
  });
});
test("source closure obtains current epochs before preview and confirms reviewed systems", async ({
  page,
}) => {
  await boot(page);
  await page.getByRole("button", { name: "关闭来源", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog
    .getByRole("combobox", { name: "来源公司", exact: true })
    .selectOption("co1");
  await dialog
    .getByRole("combobox", { name: "来源部门", exact: true })
    .selectOption("dep1");
  await dialog
    .getByRole("combobox", { name: "业务系统", exact: true })
    .selectOption("sys1");
  await dialog.getByLabel("办理原因", { exact: true }).fill("离岗关闭来源");
  await dialog.getByRole("button", { name: "预览影响" }).click();
  await expect(dialog.getByRole("heading", { name: "影响预览" })).toBeVisible();
  const req = page.waitForRequest((r) => r.url().endsWith("/source-closures"));
  await dialog.getByRole("button", { name: "确认办理" }).click();
  expect((await req).postDataJSON()).toEqual({
    input: {
      employeeId: "emp1",
      companyId: "co1",
      departmentId: "dep1",
      systems: [
        { systemId: "sys1", expectedSourceEpoch: 4, expectedVersion: 6 },
      ],
      reason: "离岗关闭来源",
    },
    previewId: "preview1",
  });
});
test("new functional menu and its visibility permission are submitted in one reviewed catalog change", async ({
  page,
}) => {
  await boot(page);
  await page.getByRole("link", { name: "系统与权限目录" }).click();
  await page.getByRole("button", { name: "管理目录" }).click();
  await page.getByRole("button", { name: "登记功能", exact: true }).click();
  const dialog = page.getByRole("dialog").last();
  await dialog.getByLabel("名称", { exact: true }).fill("订单");
  await dialog.getByLabel("稳定编码", { exact: true }).fill("orders");
  await dialog
    .getByLabel("菜单可见操作编码", { exact: true })
    .fill("orders:view");
  await dialog.getByLabel("菜单可见操作名称", { exact: true }).fill("查看订单");
  await dialog.getByLabel("办理原因", { exact: true }).fill("新增订单功能");
  await dialog.getByRole("button", { name: "预览影响" }).click();
  await expect(dialog.getByRole("heading", { name: "影响预览" })).toBeVisible();
  const req = page.waitForRequest((r) => r.url().endsWith("/catalog-changes"));
  await dialog.getByRole("button", { name: "确认办理" }).click();
  const body = (await req).postDataJSON();
  expect(body.expectedCatalogVersion).toBe(1);
  expect(body.commands).toHaveLength(2);
  expect(body.commands[0]).toMatchObject({
    kind: "NODE_CREATE",
    nodeType: "FUNCTION",
    visibilityPermissionLocalKey: "new-view",
  });
  expect(body.commands[1]).toMatchObject({
    kind: "PERMISSION_CREATE",
    functionLocalKey: "new-node",
    menuEligible: true,
  });
});
test("CSV upload requires precheck and explicit whole-batch confirmation before commit", async ({
  page,
}) => {
  await boot(page);
  await page.getByRole("link", { name: "员工组织" }).click();
  await page.getByRole("button", { name: "导入资料", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog
    .getByLabel("选择文件")
    .setInputFiles({
      name: "org.csv",
      mimeType: "text/csv",
      buffer: Buffer.from(
        "action,companyCode,companyName,departmentCode,departmentName,positionCode,positionName,expectedVersion\nCREATE_COMPANY,CO,Example,,,,,\n",
      ),
    });
  await dialog.getByRole("button", { name: "上传文件", exact: true }).click();
  await dialog.getByLabel("来源系统", { exact: true }).fill("HR");
  await dialog.getByLabel("办理原因", { exact: true }).fill("首次导入");
  const pre = page.waitForRequest(
    (r) => r.url().endsWith("/import-batches") && r.method() === "POST",
  );
  await dialog.getByRole("button", { name: "生成预检报告" }).click();
  expect((await pre).postDataJSON()).toMatchObject({
    fileId: "file1",
    worksheet: "CSV",
    importType: "ORGANIZATION",
    sourceSystem: "HR",
  });
  const commit = page.getByRole("button", { name: "确认整批提交" });
  await expect(commit).toBeDisabled();
  await page
    .getByLabel("我已核对全部变更及警告，确认按本次冻结计划整批提交")
    .check();
  const req = page.waitForRequest((r) => r.url().endsWith("/commits"));
  await commit.click();
  expect((await req).postDataJSON()).toEqual({
    expectedVersion: 2,
    contentDigest: "b".repeat(64),
  });
});
