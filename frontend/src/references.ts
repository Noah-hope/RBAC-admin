import { api, state } from "./state";
import type { Operation } from "./api/routes";
export type ReferenceKind =
  | "company"
  | "department"
  | "position"
  | "system"
  | "role"
  | "employee"
  | "account";
const lists: Partial<Record<ReferenceKind, Operation>> = {
  company: "listCompanies",
  department: "listDepartments",
  position: "listPositions",
  system: "listSystems",
};
let epoch = -1;
const cache = new Map<string, Promise<Map<string, string>>>();
export async function resolveName(
  kind: ReferenceKind,
  id: string,
): Promise<string> {
  if (kind === "account" && state.session?.identity !== "SUPER_ADMIN")
    return id;
  if (epoch !== state.epoch) {
    cache.clear();
    epoch = state.epoch;
  }
  const list = lists[kind],
    key = list ? kind : kind + ":" + id;
  if (!cache.has(key))
    cache.set(
      key,
      (async () => {
        const names = new Map<string, string>();
        try {
          if (list) {
            for (let page = 1; ; page++) {
              const r = (await api.call(list, {
                query: { page, pageSize: 100 },
              })) as any;
              for (const row of r.items) names.set(row.id, row.name);
              if (page * 100 >= r.total) break;
            }
          } else {
            const r =
              kind === "role"
                ? await api.call("getRole", { path: { roleId: id } })
                : kind === "employee"
                  ? await api.call("getEmployee", { path: { employeeId: id } })
                  : await api.call("getAccount", { path: { accountId: id } });
            names.set(id, "name" in r ? r.name : r.loginName);
          }
        } catch {
          /* A label is optional; it must never widen scope or convert a failed business operation into success. */
        }
        return names;
      })(),
    );
  return (await cache.get(key)!).get(id) || id;
}
export const referenceFields: Record<string, ReferenceKind> = {
  companyId: "company",
  sourceCompanyId: "company",
  departmentId: "department",
  sourceDepartmentId: "department",
  positionId: "position",
  systemId: "system",
  roleId: "role",
  employeeId: "employee",
  accountId: "account",
};
