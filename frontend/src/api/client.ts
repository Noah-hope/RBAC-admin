import type { Operation } from "./routes";
import { routes } from "./routes";
import type { Inputs, Outputs, Appointment, Session } from "./schema";
export type Context = {
  session: Session | null;
  appointment: Appointment | null;
  epoch: number;
};
export type Options<K extends Operation> = {
  path?: Record<string, string>;
  query?: Record<string, unknown>;
  body?: Inputs[K] | FormData;
  key?: string;
  signal?: AbortSignal;
};
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public requestId = "",
    public errors: unknown[] = [],
  ) {
    super(message);
  }
}
export class Client {
  constructor(
    private context: () => Context,
    private onFailure: (e: ApiError) => void,
    private base = "",
  ) {}
  async call<K extends Operation>(
    operation: K,
    options: Options<K> = {},
  ): Promise<Outputs[K]> {
    const route = routes[operation],
      ctx = this.context(),
      epoch = ctx.epoch;
    const headers: Record<string, string> = { Accept: "application/json" };
    if (operation !== "login" && ctx.session)
      headers.Authorization = `Bearer ${ctx.session.accessToken}`;
    if (route.scoped && ctx.session?.identity === "PERMISSION_ADMIN") {
      if (!ctx.appointment)
        throw new ApiError(409, "CONTEXT_REQUIRED", "请先选择一项有效管理任命");
      headers["X-Admin-Appointment-Id"] = ctx.appointment.id;
      headers["X-Admin-Appointment-Version"] = String(ctx.appointment.version);
      headers["X-Admin-Scope-Version"] = String(ctx.appointment.scopeVersion);
    }
    if (route.idempotent) {
      if (!options.key)
        throw new ApiError(422, "KEY_REQUIRED", "缺少操作标识，请重新打开表单");
      headers["Idempotency-Key"] = options.key;
    }
    const path = route.path.replace(/\{(\w+)\}/g, (_, key) => {
      const id = options.path?.[key];
      if (!id) throw new ApiError(422, "PATH_REQUIRED", "请选择目标资源");
      return encodeURIComponent(id);
    });
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(options.query || {})) {
      if (value !== "" && value !== null && value !== undefined)
        query.set(key, String(value));
    }
    let body: BodyInit | undefined;
    if (options.body instanceof FormData) body = options.body;
    else if (options.body !== undefined) {
      headers["Content-Type"] = "application/json";
      body = JSON.stringify(options.body);
    }
    let response: Response;
    try {
      response = await fetch(
        this.base + path + (query.size ? "?" + query : ""),
        {
          method: route.method,
          headers,
          body,
          signal: options.signal,
          cache: "no-store",
          credentials: "omit",
        },
      );
    } catch (e) {
      if (options.signal?.aborted) throw e;
      throw new ApiError(
        0,
        "NETWORK_ERROR",
        "无法连接服务。写入结果可能未知，请保留本表单并使用同一操作重试。",
      );
    }
    if (this.context().epoch !== epoch)
      throw new ApiError(409, "STALE_VIEW", "管理上下文已切换，请重新读取");
    if (response.status === 204) return undefined as Outputs[K];
    const contentType = response.headers.get("content-type") || "";
    const data = contentType.includes("json") ? await response.json() : null;
    if (this.context().epoch !== epoch)
      throw new ApiError(409, "STALE_VIEW", "管理上下文已切换，请重新读取");
    if (!response.ok) {
      const error = new ApiError(
        response.status,
        data?.code || "HTTP_ERROR",
        data?.detail || `服务返回 ${response.status}，请稍后重试`,
        data?.requestId || "",
        data?.errors || [],
      );
      this.onFailure(error);
      throw error;
    }
    if (data === null)
      throw new ApiError(
        502,
        "INVALID_RESPONSE",
        "服务返回了非 JSON 内容，请核对代理地址",
      );
    return data as Outputs[K];
  }
}
export class Mutation {
  private fingerprint = "";
  private token = "";
  key(body: unknown): string {
    const next = JSON.stringify(body);
    if (next !== this.fingerprint || !this.token) {
      this.fingerprint = next;
      this.token = crypto.randomUUID();
    }
    return this.token;
  }
  clear() {
    this.fingerprint = "";
    this.token = "";
  }
}
