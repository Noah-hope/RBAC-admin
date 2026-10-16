import { reactive } from 'vue';
export type Page =
  | 'employees'
  | 'roles'
  | 'requests'
  | 'systems'
  | 'administrators'
  | 'organization'
  | 'verification'
  | 'audit';
export type GrantState = '有效' | '已失效' | '已撤销';
export interface Department {
  id: string;
  name: string;
  company: string;
  parent?: string;
}
export interface Employment {
  dept: string;
  position: string;
  active: boolean;
}
export interface Employee {
  id: string;
  name: string;
  initials: string;
  color: string;
  active: boolean;
  employments: Employment[];
  updated: string;
}
export interface System {
  id: string;
  name: string;
  short: string;
  color: string;
  active: boolean;
  description: string;
}
export interface Permission {
  id: string;
  system: string;
  feature: string;
  action: string;
  active: boolean;
}
export interface Role {
  id: string;
  code: string;
  name: string;
  company: string;
  dept?: string;
  system: string;
  permissions: string[];
  active: boolean;
  description: string;
  version: number;
}
export interface Grant {
  id: string;
  employee: string;
  role: string;
  dept: string;
  state: GrantState;
  date: string;
}
export interface Change {
  id: string;
  title: string;
  kind: '分配角色' | '撤销角色' | '配置角色';
  company: string;
  dept: string;
  system: string;
  actor: string;
  reason: string;
  delivery: '待发送' | '发送失败' | '已关联 OA';
  approval: '待审批' | '已批准' | '已驳回';
  execution: '未执行' | '待执行' | '执行成功' | '执行失败' | '已失效';
  date: string;
  pairs?: { employee: string; role: string; dept: string; version?: number }[];
  grantIds?: string[];
  roleId?: string;
  permissionIds?: string[];
  diff: string[];
  events: string[];
  roleVersion?: number;
  originManagement?: string;
}
export interface Audit {
  id: string;
  time: string;
  actor: string;
  action: string;
  target: string;
  dept: string;
  system: string;
  result: string;
  reason: string;
  detail: string;
  request?: string;
  global?: boolean;
}
export interface Account {
  id: string;
  employee: string;
  login: string;
  active: boolean;
  temporary: boolean;
  global?: boolean;
}
export interface Management {
  id: string;
  company: string;
  root: string;
  systems: string[];
  accounts: string[];
  version: number;
}

export const companies = [
  { id: 'municipal', name: '市政建设集团' },
  { id: 'digital', name: '城建数字公司' },
];
export const departments = reactive<Department[]>([
  { id: 'engineering', name: '工程管理部', company: 'municipal' },
  { id: 'team-one', name: '工程一组', company: 'municipal', parent: 'engineering' },
  { id: 'team-two', name: '工程二组', company: 'municipal', parent: 'engineering' },
  { id: 'finance', name: '财务管理部', company: 'municipal' },
  { id: 'office', name: '综合办公室', company: 'municipal' },
  { id: 'research', name: '产品研发部', company: 'digital' },
]);
export const actionLabels = ['查看', '新增', '编辑', '审批', '导出'];

export const initialDepartments = JSON.parse(JSON.stringify(departments)) as Department[];
export function initialData() {
  const employees: Employee[] = [
    {
      id: 'EMP-1001',
      name: '林予安',
      initials: '予安',
      color: 'violet',
      active: true,
      employments: [
        { dept: 'team-one', position: '项目工程师', active: true },
        { dept: 'team-two', position: '项目协作', active: true },
      ],
      updated: '今天 09:42',
    },
    {
      id: 'EMP-1002',
      name: '陈知远',
      initials: '知远',
      color: 'blue',
      active: true,
      employments: [
        { dept: 'engineering', position: '部门负责人', active: true },
        { dept: 'research', position: '项目协作', active: true },
      ],
      updated: '今天 09:18',
    },
    {
      id: 'EMP-1003',
      name: '许清禾',
      initials: '清禾',
      color: 'rose',
      active: true,
      employments: [{ dept: 'team-one', position: '造价工程师', active: true }],
      updated: '昨天 16:30',
    },
    {
      id: 'EMP-1004',
      name: '沈亦川',
      initials: '亦川',
      color: 'teal',
      active: true,
      employments: [{ dept: 'team-two', position: '施工协调', active: true }],
      updated: '昨天 15:06',
    },
    {
      id: 'EMP-1005',
      name: '周晚宁',
      initials: '晚宁',
      color: 'amber',
      active: true,
      employments: [{ dept: 'team-one', position: '资料专员', active: true }],
      updated: '10 月 13 日',
    },
    {
      id: 'EMP-1006',
      name: '陆景行',
      initials: '景行',
      color: 'slate',
      active: false,
      employments: [{ dept: 'team-two', position: '项目工程师', active: false }],
      updated: '10 月 12 日',
    },
    {
      id: 'EMP-1007',
      name: '程以宁',
      initials: '以宁',
      color: 'violet',
      active: true,
      employments: [{ dept: 'team-two', position: '安全管理', active: true }],
      updated: '10 月 12 日',
    },
    {
      id: 'EMP-1008',
      name: '顾言溪',
      initials: '言溪',
      color: 'blue',
      active: true,
      employments: [{ dept: 'engineering', position: '计划专员', active: true }],
      updated: '10 月 11 日',
    },
    {
      id: 'EMP-1009',
      name: '李乔',
      initials: '李乔',
      color: 'teal',
      active: true,
      employments: [{ dept: 'engineering', position: '权限管理员', active: true }],
      updated: '10 月 10 日',
    },
    {
      id: 'EMP-1010',
      name: '周楠',
      initials: '周楠',
      color: 'rose',
      active: true,
      employments: [{ dept: 'engineering', position: '权限管理员', active: true }],
      updated: '10 月 10 日',
    },
    {
      id: 'EMP-2001',
      name: '江书遥',
      initials: '书遥',
      color: 'amber',
      active: true,
      employments: [{ dept: 'finance', position: '财务专员', active: true }],
      updated: '10 月 10 日',
    },
    {
      id: 'EMP-3001',
      name: '宋予白',
      initials: '予白',
      color: 'blue',
      active: true,
      employments: [{ dept: 'research', position: '产品工程师', active: true }],
      updated: '10 月 10 日',
    },
  ];
  const systems: System[] = [
    {
      id: 'oa',
      name: '协同办公系统',
      short: 'OA',
      color: 'blue',
      active: true,
      description: '公文流转、项目协同与日常办公',
    },
    {
      id: 'hr',
      name: '人力资源系统',
      short: 'HR',
      color: 'teal',
      active: true,
      description: '员工信息、考勤与任职管理',
    },
    {
      id: 'project',
      name: '项目管理系统',
      short: 'PM',
      color: 'violet',
      active: true,
      description: '项目台账与工程进度管理',
    },
  ];
  const permissions: Permission[] = systems.flatMap((s) => [
    { id: `${s.id}:access`, system: s.id, feature: '系统进入', action: '进入系统', active: true },
    ...[
      s.id === 'hr' ? '员工信息' : s.id === 'oa' ? '公文管理' : '项目台账',
      s.id === 'hr' ? '考勤记录' : s.id === 'oa' ? '协作事项' : '工程进度',
    ].flatMap((feature, index) =>
      actionLabels.map((action, a) => ({
        id: `${s.id}:${index ? 'records' : 'items'}:${['view', 'add', 'edit', 'approve', 'export'][a]}`,
        system: s.id,
        feature,
        action,
        active: true,
      })),
    ),
  ]);
  const roles: Role[] = [
    {
      id: 'R-01',
      code: 'OA_BASIC',
      name: '办公基础角色',
      company: 'municipal',
      system: 'oa',
      permissions: ['oa:access', 'oa:items:view', 'oa:records:view'],
      active: true,
      description: '面向公司员工的日常办公基础功能',
      version: 3,
    },
    {
      id: 'R-02',
      code: 'HR_READER',
      name: '员工信息查询员',
      company: 'municipal',
      system: 'hr',
      permissions: ['hr:access', 'hr:items:view', 'hr:records:view'],
      active: true,
      description: '员工信息与考勤记录查询',
      version: 2,
    },
    {
      id: 'R-03',
      code: 'ENG1_EDITOR',
      name: '工程一组协作员',
      company: 'municipal',
      dept: 'team-one',
      system: 'oa',
      permissions: ['oa:access', 'oa:items:view', 'oa:items:edit', 'oa:records:add'],
      active: true,
      description: '工程一组公文维护与事项登记',
      version: 4,
    },
    {
      id: 'R-04',
      code: 'ENG2_AUDITOR',
      name: '工程二组审核员',
      company: 'municipal',
      dept: 'team-two',
      system: 'oa',
      permissions: ['oa:access', 'oa:items:view', 'oa:items:approve'],
      active: true,
      description: '工程二组公文查看与审批',
      version: 2,
    },
    {
      id: 'R-05',
      code: 'ENG_HR_EDIT',
      name: '工程部信息维护员',
      company: 'municipal',
      dept: 'engineering',
      system: 'hr',
      permissions: ['hr:access', 'hr:items:edit'],
      active: true,
      description: '仅编辑员工信息，编辑不自动推导查看',
      version: 1,
    },
    {
      id: 'R-06',
      code: 'ENG_EXPORT_DRAFT',
      name: '工程一组归档员',
      company: 'municipal',
      dept: 'team-one',
      system: 'oa',
      permissions: [],
      active: false,
      description: '待配置草稿，尚不可分配',
      version: 0,
    },
    {
      id: 'R-07',
      code: 'DIGITAL_PM',
      name: '研发项目查询员',
      company: 'digital',
      dept: 'research',
      system: 'project',
      permissions: ['project:access', 'project:items:view'],
      active: true,
      description: '研发项目台账查看',
      version: 1,
    },
  ];
  const grants: Grant[] = [
    {
      id: 'G-01',
      employee: 'EMP-1001',
      role: 'R-01',
      dept: 'team-one',
      state: '有效',
      date: '2026-10-12',
    },
    {
      id: 'G-02',
      employee: 'EMP-1001',
      role: 'R-01',
      dept: 'team-two',
      state: '有效',
      date: '2026-10-14',
    },
    {
      id: 'G-03',
      employee: 'EMP-1001',
      role: 'R-03',
      dept: 'team-one',
      state: '有效',
      date: '2026-10-14',
    },
    {
      id: 'G-04',
      employee: 'EMP-1001',
      role: 'R-02',
      dept: 'team-one',
      state: '有效',
      date: '2026-10-12',
    },
    {
      id: 'G-05',
      employee: 'EMP-1002',
      role: 'R-01',
      dept: 'engineering',
      state: '有效',
      date: '2026-10-12',
    },
    {
      id: 'G-06',
      employee: 'EMP-1002',
      role: 'R-05',
      dept: 'engineering',
      state: '有效',
      date: '2026-10-12',
    },
    {
      id: 'G-07',
      employee: 'EMP-1003',
      role: 'R-03',
      dept: 'team-one',
      state: '有效',
      date: '2026-10-13',
    },
    {
      id: 'G-08',
      employee: 'EMP-1004',
      role: 'R-04',
      dept: 'team-two',
      state: '有效',
      date: '2026-10-12',
    },
    {
      id: 'G-09',
      employee: 'EMP-1005',
      role: 'R-01',
      dept: 'team-one',
      state: '有效',
      date: '2026-10-12',
    },
    {
      id: 'G-10',
      employee: 'EMP-1006',
      role: 'R-04',
      dept: 'team-two',
      state: '已失效',
      date: '2026-10-09',
    },
    {
      id: 'G-11',
      employee: 'EMP-1007',
      role: 'R-01',
      dept: 'team-two',
      state: '有效',
      date: '2026-10-12',
    },
    {
      id: 'G-12',
      employee: 'EMP-1008',
      role: 'R-02',
      dept: 'engineering',
      state: '有效',
      date: '2026-10-11',
    },
    {
      id: 'G-14',
      employee: 'EMP-1002',
      role: 'R-07',
      dept: 'research',
      state: '有效',
      date: '2026-10-10',
    },
    {
      id: 'G-13',
      employee: 'EMP-3001',
      role: 'R-07',
      dept: 'research',
      state: '有效',
      date: '2026-10-10',
    },
  ];
  const changes: Change[] = [
    {
      id: 'CHG-20261015-008',
      title: '许清禾 · 分配员工信息查询员',
      kind: '分配角色',
      company: 'municipal',
      dept: 'team-one',
      system: 'hr',
      actor: '周楠',
      reason: '新增项目考勤核对职责',
      delivery: '已关联 OA',
      approval: '待审批',
      execution: '未执行',
      date: '今天 09:38',
      pairs: [{ employee: 'EMP-1003', role: 'R-02', dept: 'team-one' }],
      diff: ['新增：员工信息查询员', '授权来源：工程一组'],
      events: ['09:38 保存固定申请', '09:38 已关联模拟 OA，等待最终结果'],
    },
    {
      id: 'CHG-20261015-007',
      title: '程以宁 · 分配员工信息查询员',
      kind: '分配角色',
      company: 'municipal',
      dept: 'team-two',
      system: 'hr',
      actor: '李乔',
      reason: '负责安全培训人员核对',
      delivery: '发送失败',
      approval: '待审批',
      execution: '未执行',
      date: '今天 09:26',
      pairs: [{ employee: 'EMP-1007', role: 'R-02', dept: 'team-two' }],
      diff: ['新增：员工信息查询员', '授权来源：工程二组'],
      events: ['09:26 保存固定申请', '09:26 模拟投递失败，未被 OA 受理'],
    },
    {
      id: 'CHG-20261014-006',
      title: '林予安 · 分配工程一组协作员',
      kind: '分配角色',
      company: 'municipal',
      dept: 'team-one',
      system: 'oa',
      actor: '李乔',
      reason: '项目资料协作',
      delivery: '已关联 OA',
      approval: '已批准',
      execution: '执行成功',
      date: '昨天 16:42',
      diff: ['新增：工程一组协作员', '来源：工程一组'],
      events: [
        '16:35 固定申请并关联 OA',
        '16:40 收到模拟 OA 最终批准',
        '16:42 模拟执行成功，授权生效',
      ],
    },
    {
      id: 'CHG-20261014-005',
      title: '工程部信息维护员 · 调整配置',
      kind: '配置角色',
      company: 'municipal',
      dept: 'engineering',
      system: 'hr',
      actor: '周楠',
      reason: '增加信息核对能力',
      delivery: '已关联 OA',
      approval: '已批准',
      execution: '执行失败',
      date: '昨天 15:20',
      roleId: 'R-05',
      roleVersion: 1,
      permissionIds: ['hr:access', 'hr:items:view', 'hr:items:edit'],
      diff: ['新增：员工信息 / 查看', '保留：系统进入、员工信息 / 编辑'],
      events: [
        '15:10 固定配置方案',
        '15:18 收到模拟 OA 批准',
        '15:20 模拟服务故障，未生效，等待系统恢复',
      ],
    },
  ];
  const accounts: Account[] = [
    {
      id: 'A-00',
      employee: '',
      login: 'platform.admin',
      active: true,
      temporary: false,
      global: true,
    },
    { id: 'A-01', employee: 'EMP-1009', login: 'li.qiao.demo', active: true, temporary: false },
    { id: 'A-02', employee: 'EMP-1010', login: 'zhou.nan.demo', active: true, temporary: false },
  ];
  const management: Management[] = [
    {
      id: 'M-01',
      company: 'municipal',
      root: 'engineering',
      systems: ['oa', 'hr'],
      accounts: ['A-01', 'A-02'],
      version: 2,
    },
  ];
  const audits: Audit[] = [
    {
      id: 'AUD-1058',
      time: '2026-10-15 09:38',
      actor: '周楠',
      action: '提交分配申请',
      target: '许清禾',
      dept: 'team-one',
      system: 'hr',
      result: '待审批',
      reason: '新增项目考勤核对职责',
      detail: '固定员工、角色及工程一组来源，提交不改变当前授权。',
      request: changes[0].id,
    },
    {
      id: 'AUD-1057',
      time: '2026-10-15 09:26',
      actor: '李乔',
      action: 'OA 投递失败',
      target: '程以宁',
      dept: 'team-two',
      system: 'hr',
      result: '发送失败',
      reason: '负责安全培训人员核对',
      detail: '本地申请已保存；模拟 OA 未受理，可重试同一固定申请。',
      request: changes[1].id,
    },
    {
      id: 'AUD-1056',
      time: '2026-10-14 16:42',
      actor: '自动执行服务',
      action: '分配执行成功',
      target: '林予安',
      dept: 'team-one',
      system: 'oa',
      result: '执行成功',
      reason: '项目资料协作',
      detail: '工程一组来源新增协作员，其他独立来源保留。',
      request: changes[2].id,
    },
    {
      id: 'AUD-1055',
      time: '2026-10-12 11:10',
      actor: '总管理员',
      action: '全局停用员工',
      target: '陆景行',
      dept: 'team-two',
      system: 'oa',
      result: '已生效',
      reason: '员工离职',
      detail: '全部来源和管理任命失效；身份恢复不恢复旧授权。',
      global: true,
    },
    {
      id: 'AUD-1054',
      time: '2026-10-10 10:00',
      actor: '总管理员',
      action: '配置管理范围',
      target: '工程管理部',
      dept: 'engineering',
      system: 'oa',
      result: '已生效',
      reason: '部门授权管理',
      detail: '指定工程管理部及下级、OA/HR 两系统，李乔与周楠协作。',
      global: true,
    },
  ];
  changes.forEach((c) => {
    c.originManagement = 'M-01';
    c.pairs?.forEach((p) => (p.version = roles.find((r) => r.id === p.role)?.version));
  });
  return {
    employees,
    systems,
    permissions,
    roles,
    grants,
    changes,
    accounts,
    management,
    audits,
    roleDrafts: {} as Record<string, { permissions: string[]; reason: string; name: string }>,
    directoryMeta: {} as Record<
      string,
      { parent: string; menu: string; order: number; navigation?: boolean; code?: string }
    >,
    organizationVersion: 0,
    serial: 20,
    featureStates: {} as Record<string, boolean>,
  };
}
