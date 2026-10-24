import { computed, reactive } from "vue";
import { Client, ApiError, type Context } from "./api/client";
import type { Appointment, Session } from "./api/schema";
export const state = reactive<
  Context & {
    appointments: Appointment[];
    notice: string;
    contextError: string;
  }
>({
  session: null,
  appointment: null,
  epoch: 0,
  appointments: [],
  notice: "",
  contextError: "",
});
export const isSuper = computed(
  () => state.session?.identity === "SUPER_ADMIN",
);
export function clearSession(message = "") {
  state.epoch++;
  state.session = null;
  state.appointment = null;
  state.appointments = [];
  state.contextError = "";
  state.notice = message;
}
export const api = new Client(
  () => state,
  (e) => {
    if (e.status === 401 && state.session)
      clearSession("会话已失效，请重新登录");
    if (e.code === "CONTEXT_CHANGED") {
      state.epoch++;
      state.appointment = null;
      state.contextError = "管理范围已变化，请刷新任命后重新选择";
    }
  },
);
export async function loadAppointments() {
  const list: Appointment[] = [];
  let page = 1;
  while (true) {
    const r = await api.call("myAppointments", {
      query: { page, pageSize: 100 },
    });
    list.push(...r.items);
    if (page * 100 >= r.total) break;
    page++;
  }
  state.appointments = list.filter((a) => a.status === "ACTIVE");
  state.contextError = "";
}
export async function establish(session: Session) {
  state.epoch++;
  state.session = session;
  state.notice = "";
  if (session.identity === "PERMISSION_ADMIN" && session.mode === "NORMAL") {
    await loadAppointments();
    state.appointment = state.appointments[0] || null;
  }
}
export function chooseAppointment(id: string) {
  state.epoch++;
  state.appointment = state.appointments.find((a) => a.id === id) || null;
  state.contextError = "";
}
export function errorText(e: unknown) {
  if (e instanceof ApiError) {
    if (e.code === "STALE_VIEW") return "";
    const detail = e.errors
      .map((x: any) => `${x.pointer || ""} ${x.message || ""}`)
      .join("；");
    return `${e.message}${e.status === 409 ? "。请重新读取或重新预览，勿强行覆盖。" : ""}${detail ? "：" + detail : ""}${e.requestId ? "（请求号 " + e.requestId + "）" : ""}`;
  }
  return e instanceof Error ? e.message : "操作失败，请重试";
}
