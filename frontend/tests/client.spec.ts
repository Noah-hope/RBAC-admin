import { test, expect } from "@playwright/test";
import { createServer, type Server } from "node:http";
import { Client, Mutation, ApiError, type Context } from "../src/api/client";
let server: Server,
  base: string,
  seen: { url: string; headers: any; body: string }[] = [];
let status = 200,
  response: unknown = {},
  delay = 0;
let context: Context;
test.beforeAll(async () => {
  server = createServer(async (req, res) => {
    let body = "";
    for await (const chunk of req) body += chunk;
    seen.push({ url: req.url!, headers: req.headers, body });
    await new Promise((r) => setTimeout(r, delay));
    res.writeHead(status, { "Content-Type": "application/json" });
    res.end(status === 204 ? undefined : JSON.stringify(response));
  });
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
  base = `http://127.0.0.1:${(server.address() as any).port}`;
});
test.afterAll(() => server.close());
test.beforeEach(() => {
  seen = [];
  status = 200;
  response = { items: [], total: 0, page: 1, pageSize: 20 };
  delay = 0;
  context = {
    epoch: 0,
    session: {
      sessionId: "s",
      accountId: "a",
      employeeId: null,
      identity: "SUPER_ADMIN",
      mode: "NORMAL",
      accessToken: "test-only-token",
      tokenType: "Bearer",
      expiresAt: "2099-01-01T00:00:00Z",
    },
    appointment: null,
  };
});
test("login sends credentials without a stale Authorization header", async () => {
  await new Client(
    () => context,
    () => {},
    base,
  ).call("login", {
    body: { loginName: "user", password: "unchanged password " },
  });
  expect(seen[0].headers.authorization).toBeUndefined();
  expect(JSON.parse(seen[0].body).password).toBe("unchanged password ");
});
test("query omits blank filters, preserves false, and encodes names", async () => {
  await new Client(
    () => context,
    () => {},
    base,
  ).call("listAccounts", {
    query: {
      q: "王 & 李",
      enabled: false,
      employeeId: "",
      page: 1,
      pageSize: 20,
    },
  });
  expect(seen[0].url).toBe(
    "/api/v1/admin/accounts?q=%E7%8E%8B+%26+%E6%9D%8E&enabled=false&page=1&pageSize=20",
  );
  expect(seen[0].headers.authorization).toBe("Bearer test-only-token");
  expect(seen[0].headers["x-admin-appointment-id"]).toBeUndefined();
});
test("permission administrator sends one complete versioned appointment", async () => {
  context.session!.identity = "PERMISSION_ADMIN";
  context.appointment = {
    id: "ap",
    scopeId: "sc",
    accountId: "a",
    employeeId: "e",
    status: "ACTIVE",
    version: 3,
    scopeVersion: 8,
    companyId: "co",
    departmentId: "dep",
    systemIds: ["sys"],
  };
  await new Client(
    () => context,
    () => {},
    base,
  ).call("listRoles");
  expect(seen[0].headers["x-admin-appointment-id"]).toBe("ap");
  expect(seen[0].headers["x-admin-appointment-version"]).toBe("3");
  expect(seen[0].headers["x-admin-scope-version"]).toBe("8");
});
test("missing appointment cannot issue a scoped request", async () => {
  context.session!.identity = "PERMISSION_ADMIN";
  await expect(
    new Client(
      () => context,
      () => {},
      base,
    ).call("listRoles"),
  ).rejects.toMatchObject({ code: "CONTEXT_REQUIRED" });
  expect(seen).toHaveLength(0);
});
test("mutation requires a key and same content retry reuses it", async () => {
  const m = new Mutation(),
    a = m.key({ name: "role", version: 0 });
  expect(a).toMatch(/^[0-9a-f-]{36}$/);
  expect(m.key({ name: "role", version: 0 })).toBe(a);
  expect(m.key({ name: "other", version: 0 })).not.toBe(a);
  await expect(
    new Client(
      () => context,
      () => {},
      base,
    ).call("createSystem", {
      body: { code: "s", name: "System", reason: "test" },
    }),
  ).rejects.toMatchObject({ code: "KEY_REQUIRED" });
  expect(seen).toHaveLength(0);
});
test("204 is empty and logout keeps token only on outbound request", async () => {
  status = 204;
  const result = await new Client(
    () => context,
    () => {},
    base,
  ).call("logout");
  expect(result).toBeUndefined();
  expect(seen[0].headers.authorization).toBe("Bearer test-only-token");
});
test("structured authentication failure invokes session handling", async () => {
  status = 401;
  response = {
    code: "SESSION_REVOKED",
    detail: "会话已失效",
    requestId: "req",
    errors: [],
  };
  let failure: ApiError | undefined;
  await expect(
    new Client(
      () => context,
      (e) => (failure = e),
      base,
    ).call("listAccounts"),
  ).rejects.toMatchObject({ status: 401, code: "SESSION_REVOKED" });
  expect(failure?.requestId).toBe("req");
});
test("late response after switching context must not populate the new view", async () => {
  delay = 100;
  const pending = new Client(
    () => context,
    () => {},
    base,
  ).call("listRoles");
  context.epoch++;
  await expect(pending).rejects.toMatchObject({ code: "STALE_VIEW" });
});
test("switching context while a response body is arriving cannot revoke the new session", async () => {
  const original = globalThis.fetch;
  let failures = 0;
  globalThis.fetch = async () =>
    ({
      status: 401,
      ok: false,
      headers: new Headers({ "content-type": "application/json" }),
      json: async () => {
        context.epoch++;
        return { code: "SESSION_REVOKED" };
      },
    }) as Response;
  try {
    await expect(
      new Client(
        () => context,
        () => failures++,
        base,
      ).call("listRoles"),
    ).rejects.toMatchObject({ code: "STALE_VIEW" });
    expect(failures).toBe(0);
  } finally {
    globalThis.fetch = original;
  }
});
