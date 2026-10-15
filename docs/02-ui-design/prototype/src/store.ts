// Browser-memory interaction fixtures only. This is not a backend authorization service.
import { computed, reactive, ref } from 'vue';
import {
  companies,
  departments,
  initialData,
  type Change,
  type Employee,
  type Grant,
  type Page,
  type Role,
} from './data';

export const data = reactive(initialData());
export const identity = ref<'department' | 'global'>('department');
export const companyId = ref('municipal');
export const page = ref<Page>('employees');
export const loggedIn = ref(true);
export const scene = ref('normal');
export const notice = ref('');
export const isGlobal = computed(() => identity.value === 'global');
export const actor = computed(() => (isGlobal.value ? '总管理员' : '李乔'));
export const selectedManagement = ref('M-01');
export const currentManagement = computed(() =>
  data.management.find((m) => m.id === selectedManagement.value && m.accounts.includes('A-01')),
);
export const sessionUsable = computed(
  () =>
    isGlobal.value ||
    Boolean(
      currentManagement.value &&
      data.accounts.find((a) => a.id === 'A-01')?.active &&
      data.employees.find((e) => e.id === 'EMP-1009')?.active,
    ),
);
export const companyName = computed(
  () => companies.find((c) => c.id === companyId.value)?.name ?? '',
);
export const deptName = (id: string) => departments.find((d) => d.id === id)?.name ?? '公司共享';
export const systemName = (id: string) => data.systems.find((s) => s.id === id)?.name ?? '平台管理';
export const employeeName = (id: string) =>
  data.employees.find((e) => e.id === id)?.name ?? '总管理员';
export const roleById = (id: string) => data.roles.find((r) => r.id === id)!;
export function isDescendant(id: string, root: string): boolean {
  if (id === root) return true;
  const d = departments.find((d) => d.id === id);
  return Boolean(
    d?.parent &&
    isDescendant(d.parent, root) &&
    d.company === departments.find((x) => x.id === root)?.company,
  );
}
export const allowedDepts = computed(() =>
  departments
    .filter(
      (d) =>
        d.company === companyId.value &&
        (isGlobal.value ||
          Boolean(currentManagement.value && isDescendant(d.id, currentManagement.value.root))),
    )
    .map((d) => d.id),
);
export const allowedSystems = computed(() =>
  isGlobal.value ? data.systems.map((s) => s.id) : (currentManagement.value?.systems ?? []),
);
export const visibleEmployees = computed(() =>
  data.employees.filter((e) => e.employments.some((j) => allowedDepts.value.includes(j.dept))),
);
export const visibleRoles = computed(() =>
  data.roles.filter(
    (r) =>
      r.company === companyId.value &&
      allowedSystems.value.includes(r.system) &&
      (!r.dept || allowedDepts.value.includes(r.dept)),
  ),
);
export const visibleChanges = computed(() =>
  data.changes.filter(
    (c) =>
      c.company === companyId.value &&
      allowedSystems.value.includes(c.system) &&
      (isGlobal.value || allowedDepts.value.includes(c.dept)),
  ),
);
export const visibleAudits = computed(() =>
  data.audits.filter(
    (a) =>
      isGlobal.value ||
      (!a.global && allowedDepts.value.includes(a.dept) && allowedSystems.value.includes(a.system)),
  ),
);
export const visibleGrants = (employee: string) =>
  data.grants.filter(
    (g) =>
      g.employee === employee &&
      allowedDepts.value.includes(g.dept) &&
      allowedSystems.value.includes(roleById(g.role)?.system),
  );
export const isPending = (c: Change) =>
  !['执行成功', '已失效'].includes(c.execution) && c.approval !== '已驳回';
export const canEditRole = (r: Role) =>
  isGlobal.value ||
  Boolean(r.dept && allowedDepts.value.includes(r.dept) && allowedSystems.value.includes(r.system));
export const pageAllowed = (p: Page) =>
  sessionUsable.value &&
  (isGlobal.value || !['systems', 'administrators', 'organization'].includes(p));

let toastTimer: ReturnType<typeof setTimeout>;
export function toast(message: string) {
  notice.value = message;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (notice.value = ''), 4500);
}
export function tryAction(action: () => unknown): boolean {
  try {
    action();
    return true;
  } catch (e) {
    toast(e instanceof Error ? e.message : '操作未完成，请重试');
    return false;
  }
}
export function resetDemo() {
  Object.assign(data, initialData());
  companyId.value = 'municipal';
  selectedManagement.value = 'M-01';
  scene.value = 'normal';
  loggedIn.value = true;
  toast('演示数据已重置');
}
export function switchIdentity(value: 'department' | 'global') {
  identity.value = value;
  companyId.value = 'municipal';
  scene.value = 'normal';
  loggedIn.value = true;
  if (value === 'department') {
    const m = data.management.find((m) => m.accounts.includes('A-01'));
    selectedManagement.value = m?.id ?? 'M-01';
    if (m) companyId.value = m.company;
  }
  page.value = 'employees';
}
const nextId = (prefix: string) => `${prefix}-${++data.serial}`;
const time = () =>
  '2026-10-15 ' +
  new Date().toLocaleTimeString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }).slice(0, 5);
export function audit(
  action: string,
  target: string,
  dept: string,
  system: string,
  result: string,
  reason: string,
  detail: string,
  request?: string,
  global = false,
) {
  data.audits.unshift({
    id: nextId('AUD'),
    time: time(),
    actor: actor.value,
    action,
    target,
    dept,
    system,
    result,
    reason,
    detail,
    request,
    global,
  });
}
export function requireScope(dept: string, system?: string) {
  if (
    !sessionUsable.value ||
    !allowedDepts.value.includes(dept) ||
    (system && !allowedSystems.value.includes(system))
  )
    throw Error('当前管理范围不覆盖此部门或系统，请切换合法范围后重试');
}
function requireGlobal() {
  if (!isGlobal.value) throw Error('此操作仅限总管理员');
}
function requireReason(reason: string) {
  if (!reason.trim()) throw Error('请填写办理原因');
}

export function createChange(
  input: Omit<Change, 'id' | 'actor' | 'date' | 'delivery' | 'approval' | 'execution' | 'events'>,
) {
  const c: Change = {
    ...input,
    originManagement: isGlobal.value ? undefined : selectedManagement.value,
    id: nextId('CHG-20261015'),
    actor: actor.value,
    date: '刚刚',
    delivery: '待发送',
    approval: '待审批',
    execution: '未执行',
    events: ['刚刚 固定申请内容，等待投递模拟 OA'],
  };
  data.changes.unshift(c);
  audit(
    '提交' + c.kind + '申请',
    c.title,
    c.dept,
    c.system,
    '待发送',
    c.reason,
    c.diff.join('；'),
    c.id,
    c.kind === '配置角色' && !c.dept,
  );
  toast('申请已保存，尚未生效。可在变更申请中查看投递状态');
  return c;
}
export function assignRoles(ids: string[], roleIds: string[], dept: string, reason: string) {
  requireReason(reason);
  if (!ids.length || !roleIds.length) throw Error('请选择员工和角色');
  const roles = roleIds.map(roleById);
  if (new Set(roles.map((r) => r.system)).size !== 1)
    throw Error('本次演示批次请选择同一系统的角色');
  if (!data.systems.find((s) => s.id === roles[0].system)?.active)
    throw Error('目标业务系统已停用，不能新增授权');
  requireScope(dept, roles[0].system);
  const pairs: { employee: string; role: string; dept: string; version?: number }[] = [];
  for (const id of ids) {
    const e = data.employees.find((e) => e.id === id);
    if (!e?.active || !e.employments.some((j) => j.dept === dept && j.active))
      throw Error(`${e?.name ?? id} 没有所选来源部门的有效任职，整批未提交`);
    for (const r of roles) {
      if (
        !r.active ||
        r.company !== companyId.value ||
        (r.dept && !e.employments.some((j) => j.dept === r.dept && j.active))
      )
        throw Error(`${r.name} 的状态或任职条件不适用，整批未提交`);
      if (
        data.grants.some(
          (g) => g.employee === id && g.role === r.id && g.dept === dept && g.state === '有效',
        )
      )
        continue;
      if (
        data.changes.some(
          (c) =>
            isPending(c) &&
            c.pairs?.some((p) => p.employee === id && p.role === r.id && p.dept === dept),
        )
      )
        throw Error(`${e.name} 的同来源分配已有未结束申请，请先查看该申请`);
      pairs.push({ employee: id, role: r.id, dept, version: r.version });
    }
  }
  if (!pairs.length) throw Error('所选来源已有相同有效角色，本次没有变化');
  return createChange({
    title: `${ids.length === 1 ? employeeName(ids[0]) : `${ids.length} 名员工`} · 分配角色`,
    kind: '分配角色',
    company: companyId.value,
    dept,
    system: roles[0].system,
    reason,
    pairs,
    diff: pairs.map(
      (p) => `新增：${employeeName(p.employee)} / ${roleById(p.role).name} / ${deptName(p.dept)}`,
    ),
  });
}
export function revokeGrant(g: Grant, reason: string) {
  requireReason(reason);
  requireScope(g.dept, roleById(g.role).system);
  if (g.state !== '有效') throw Error('已失效或已撤销的来源无需再次撤销');
  if (data.changes.some((c) => isPending(c) && c.grantIds?.includes(g.id)))
    throw Error('此授权关系已有未结束撤销申请');
  return createChange({
    title: `${employeeName(g.employee)} · 撤销${roleById(g.role).name}`,
    kind: '撤销角色',
    company: companyId.value,
    dept: g.dept,
    system: roleById(g.role).system,
    reason,
    grantIds: [g.id],
    diff: [`移除指定来源：${roleById(g.role).name} / ${deptName(g.dept)}`, '保留其他独立有效来源'],
  });
}
export function closeSource(employee: string, dept: string, systems: string[], reason: string) {
  requireReason(reason);
  if (!systems.length) throw Error('至少选择一个获准系统');
  systems.forEach((s) => requireScope(dept, s));
  const e = data.employees.find((e) => e.id === employee);
  if (!e || !e.employments.some((j) => j.dept === dept))
    throw Error('员工没有此来源部门的任职记录');
  data.grants
    .filter(
      (g) =>
        g.employee === employee &&
        g.dept === dept &&
        systems.includes(roleById(g.role).system) &&
        g.state === '有效',
    )
    .forEach((g) => (g.state = '已失效'));
  data.changes
    .filter(
      (c) =>
        isPending(c) &&
        systems.includes(c.system) &&
        (c.pairs?.some((p) => p.employee === employee && p.dept === dept) ||
          c.grantIds?.some((id) =>
            data.grants.find((g) => g.id === id && g.employee === employee && g.dept === dept),
          )),
    )
    .forEach((c) => {
      c.execution = '已失效';
      c.events.push('刚刚 来源已关闭，旧申请不得执行');
    });
  e.updated = '刚刚';
  systems.forEach((s) =>
    audit(
      '关闭原部门权限',
      e.name,
      dept,
      s,
      '已生效',
      reason,
      '此来源已关闭；旧申请失效，其他来源保留。',
    ),
  );
  toast('原部门来源已关闭，其他独立有效来源保留');
}
export function setEmployeeActive(e: Employee, active: boolean, reason: string) {
  requireGlobal();
  requireReason(reason);
  e.active = active;
  e.updated = '刚刚';
  if (!active) {
    data.grants
      .filter((g) => g.employee === e.id && g.state === '有效')
      .forEach((g) => (g.state = '已失效'));
    data.changes
      .filter((c) => isPending(c) && c.pairs?.some((p) => p.employee === e.id))
      .forEach((c) => {
        c.execution = '已失效';
        c.events.push('员工全局停用，旧分配失效');
      });
    const accounts = data.accounts.filter((a) => a.employee === e.id).map((a) => a.id);
    data.management.forEach(
      (m) => (m.accounts = m.accounts.filter((id) => !accounts.includes(id))),
    );
  }
  audit(
    active ? '恢复员工身份' : '全局停用员工',
    e.name,
    e.employments[0].dept,
    '',
    '已生效',
    reason,
    active ? '只恢复员工身份；旧授权和任命不恢复。' : '全部业务来源及管理任命失效。',
    undefined,
    true,
  );
  toast(active ? '员工身份已恢复，旧授权和任命未恢复' : '员工已全局停用，全部授权与任命失效');
}
export function saveRoleConfig(r: Role, permissions: string[], reason: string) {
  requireReason(reason);
  if (!canEditRole(r)) throw Error('公司级共享角色仅总管理员可维护');
  if (!permissions.length) throw Error('至少选择一项系统进入或功能权限');
  if (
    permissions.some(
      (id) => !data.permissions.find((p) => p.id === id && p.system === r.system && p.active),
    )
  )
    throw Error('配置包含无效或停用的权限，请先移除这些项');
  if (data.changes.some((c) => c.roleId === r.id && isPending(c)))
    throw Error('此角色已有未结束配置方案，请先处理原申请');
  const add = permissions.filter((p) => !r.permissions.includes(p));
  const remove = r.permissions.filter((p) => !permissions.includes(p));
  if (!add.length && !remove.length) throw Error('权限配置没有变化');
  return createChange({
    title: `${r.name} · 调整配置`,
    kind: '配置角色',
    company: r.company,
    dept: r.dept ?? '',
    system: r.system,
    reason,
    roleId: r.id,
    roleVersion: r.version,
    permissionIds: [...permissions],
    diff: [
      ...add.map((p) => `新增：${permissionLabel(p)}`),
      ...remove.map((p) => `移除：${permissionLabel(p)}`),
    ],
  });
}
export const permissionLabel = (id: string) => {
  const p = data.permissions.find((p) => p.id === id);
  return p ? `${p.feature} / ${p.action}` : id;
};
export function retryDelivery(c: Change, fail = false) {
  if (c.execution === '已失效' || c.delivery === '已关联 OA') throw Error('当前申请不可再次投递');
  c.delivery = fail ? '发送失败' : '已关联 OA';
  c.events.push(fail ? '模拟投递失败，保留同一固定申请' : '模拟投递成功，关联 OA，申请编号不变');
  audit(
    'OA 投递' + (fail ? '失败' : '成功'),
    c.title,
    c.dept,
    c.system,
    c.delivery,
    c.reason,
    '同一固定申请，不重复创建。',
    c.id,
  );
  toast(fail ? '模拟投递失败，申请内容保留' : '模拟 OA 已接收，等待审批结果');
}
export function simulateApproval(c: Change, approved: boolean) {
  if (c.delivery !== '已关联 OA') throw Error('尚未关联 OA，不能收到此申请的最终结果');
  if (c.approval !== '待审批') throw Error('已有最终审批结果，不覆盖历史结果');
  c.approval = approved ? '已批准' : '已驳回';
  if (c.execution !== '已失效') c.execution = approved ? '待执行' : '未执行';
  c.events.push(
    approved ? '收到模拟外部 OA 批准；尚未执行成功' : '收到模拟外部 OA 驳回；当前授权不变',
  );
  audit(
    '接收模拟 OA 结果',
    c.title,
    c.dept,
    c.system,
    c.approval,
    c.reason,
    c.execution === '已失效' ? '来源已关闭，批准不能使旧申请执行。' : '审批结果与执行结果分开。',
    c.id,
  );
  toast(
    c.execution === '已失效'
      ? '旧申请已失效，批准不会恢复权限'
      : approved
        ? '模拟批准已接收，等待执行'
        : '模拟驳回，授权未改变',
  );
}
export function simulateExecution(c: Change, fail = false) {
  if (c.execution === '执行成功') {
    toast('返回历史成功结果，未重复执行');
    return;
  }
  if (c.execution === '已失效') throw Error('旧方案已失效，不能执行或恢复权限');
  if (c.approval !== '已批准') throw Error('必须先收到最终批准');
  const r = c.roleId ? roleById(c.roleId) : undefined;
  const originalScope = c.originManagement
    ? data.management.find((m) => m.id === c.originManagement)
    : undefined;
  const scopeInvalid = Boolean(
    c.originManagement &&
    (!originalScope ||
      !originalScope.systems.includes(c.system) ||
      !isDescendant(c.dept, originalScope.root)),
  );
  const invalid =
    scopeInvalid ||
    (r && r.version !== c.roleVersion) ||
    c.pairs?.some((p) => {
      const e = data.employees.find((e) => e.id === p.employee);
      const role = roleById(p.role);
      return (
        !e?.active ||
        !role.active ||
        (p.version !== undefined && p.version !== role.version) ||
        !e.employments.some((j) => j.dept === p.dept && j.active) ||
        (role.dept && !e.employments.some((j) => j.dept === role.dept && j.active))
      );
    }) ||
    c.grantIds?.some((id) => data.grants.find((g) => g.id === id)?.state !== '有效');
  if (invalid) {
    c.execution = '已失效';
    c.events.push('模拟执行前复核未通过，整批失效');
    toast('目标条件已变化，整批未执行');
    return;
  }
  if (fail) {
    c.execution = '执行失败';
    c.events.push('模拟服务故障，整批未生效，等待恢复');
    toast('已模拟执行失败，当前授权不变');
    return;
  }
  if (r && c.permissionIds) {
    r.permissions = [...c.permissionIds];
    r.active = true;
    r.version++;
  }
  c.pairs?.forEach((p) => {
    if (
      !data.grants.some(
        (g) =>
          g.employee === p.employee && g.role === p.role && g.dept === p.dept && g.state === '有效',
      )
    )
      data.grants.push({ ...p, id: nextId('G'), state: '有效', date: '2026-10-15' });
  });
  c.grantIds?.forEach((id) => {
    const g = data.grants.find((g) => g.id === id);
    if (g) g.state = '已撤销';
  });
  c.execution = '执行成功';
  c.events.push('模拟执行成功，当前授权已更新');
  audit(
    c.kind + '执行成功',
    c.title,
    c.dept,
    c.system,
    '执行成功',
    c.reason,
    c.diff.join('；'),
    c.id,
  );
  toast('模拟执行成功，可在员工授权和权限验证中查看变化');
}

export function effectivePermissions(employee: string, company: string, system: string) {
  const e = data.employees.find((e) => e.id === employee);
  if (!e?.active || !data.systems.find((s) => s.id === system)?.active)
    return { canAccess: false, operations: [], menus: [] } as {
      canAccess: boolean;
      operations: string[];
      menus: string[];
    };
  const grants = data.grants.filter(
    (g) =>
      g.employee === employee &&
      g.state === '有效' &&
      departments.find((d) => d.id === g.dept)?.company === company &&
      e.employments.some((j) => j.dept === g.dept && j.active),
  );
  const ids = new Set(
    grants.flatMap((g) => {
      const r = roleById(g.role);
      return r.active &&
        r.system === system &&
        r.company === company &&
        (!r.dept || e.employments.some((j) => j.dept === r.dept && j.active))
        ? r.permissions
        : [];
    }),
  );
  const active = data.permissions.filter(
    (p) =>
      p.system === system &&
      p.active &&
      ids.has(p.id) &&
      (p.action === '进入系统' || data.featureStates[`${system}/${p.feature}`] !== false),
  );
  const canAccess = active.some((p) => p.action === '进入系统');
  return {
    canAccess,
    operations: canAccess ? active.filter((p) => p.action !== '进入系统').map((p) => p.id) : [],
    menus: canAccess
      ? [...new Set(active.filter((p) => p.action === '查看').map((p) => p.feature))]
      : [],
  };
}
