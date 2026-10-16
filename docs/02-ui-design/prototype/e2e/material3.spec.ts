import { expect, test, type Page } from '@playwright/test';

async function tools(page: Page) {
  const panel = page.locator('.prototype-tools');
  if (await panel.count()) await panel.evaluate((el) => ((el as HTMLDetailsElement).open = true));
}
async function nav(page: Page, name: string) {
  const menu = page.getByRole('button', { name: '打开导航', exact: true });
  if (await menu.isVisible()) await menu.click();
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('button', { name: new RegExp(`^${name}(?: \\d+)?$`) })
    .click();
}
test.beforeEach(async ({ page }) => {
  await page.goto('/');
  await tools(page);
});

test('未提交角色草稿可以保存后继续编辑并删除，员工授权不随草稿改变', async ({ page }) => {
  await nav(page, '角色权限');
  await page
    .getByRole('row')
    .filter({ hasText: '第一项目经理部归档员' })
    .getByRole('button', { name: '配置权限' })
    .click();
  const dialog = page.getByRole('dialog', { name: '第一项目经理部归档员', exact: true });
  await dialog.getByRole('checkbox', { name: '系统进入权限', exact: true }).check();
  await dialog.getByRole('button', { name: '保存配置草稿', exact: true }).click();
  await dialog.getByRole('button', { name: '关闭', exact: true }).click();
  await page
    .getByRole('row')
    .filter({ hasText: '第一项目经理部归档员' })
    .getByRole('button', { name: '配置权限' })
    .click();
  await expect(dialog.getByRole('checkbox', { name: '系统进入权限', exact: true })).toBeChecked();
  await dialog.getByRole('button', { name: '删除未提交草稿', exact: true }).click();
  await page
    .getByRole('dialog', { name: '删除未提交草稿', exact: true })
    .getByRole('button', { name: '确认删除草稿' })
    .click();
  await expect(page.getByRole('row').filter({ hasText: '第一项目经理部归档员' })).toHaveCount(0);
  await nav(page, '员工授权');
  await expect(page.getByRole('button', { name: /予安 林予安/ })).toBeVisible();
});

test('驳回的分配申请复制为新草稿，修改原因重提后保留原驳回记录', async ({ page }) => {
  await page.getByLabel('当前管理任命').selectOption('M-02');
  await nav(page, '变更申请');
  await page.getByRole('button', { name: '程以宁 · 分配员工信息查询员', exact: true }).click();
  const detail = page.getByRole('dialog', { name: '申请详情', exact: true });
  await detail.getByRole('button', { name: '重试投递同一申请' }).click();
  await detail.getByRole('button', { name: '原型演示工具', exact: true }).click();
  await detail.getByRole('button', { name: '模拟 OA 驳回', exact: true }).click();
  await detail.getByRole('button', { name: '复制为新草稿', exact: true }).click();
  const draft = page.getByRole('dialog', { name: '编辑重提草稿', exact: true });
  await draft.getByRole('textbox', { name: '办理原因' }).fill('补充岗位职责依据后重新申请');
  await draft.getByRole('button', { name: '提交新申请', exact: true }).click();
  await expect(detail.getByText('待发送', { exact: true })).toBeVisible();
  await detail.getByRole('button', { name: '关闭对话框' }).click();
  await expect(
    page.getByRole('row').filter({ hasText: '已驳回' }).filter({ hasText: '程以宁' }),
  ).toHaveCount(1);
  await expect(page.getByRole('row').filter({ hasText: '程以宁' })).toHaveCount(2);
});

test('目录显示名称编辑保留稳定编码，菜单可见条件可以独立查看', async ({ page }) => {
  await page.getByLabel('演示身份', { exact: true }).selectOption('global');
  await nav(page, '系统与权限目录');
  await page.getByRole('button', { name: '编辑系统资料', exact: true }).click();
  const edit = page.getByRole('dialog', { name: '编辑系统资料', exact: true });
  await expect(edit.getByRole('textbox', { name: '稳定编码' })).toBeDisabled();
  await edit.getByRole('textbox', { name: '名称', exact: true }).fill('协同办公平台');
  await edit.getByRole('button', { name: '保存资料', exact: true }).click();
  await expect(page.getByRole('heading', { name: '协同办公平台', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '配置菜单 公文管理', exact: true }).click();
  const menu = page.getByRole('dialog', { name: '配置功能目录', exact: true });
  await expect(menu.getByLabel('菜单可见权限')).toHaveValue('oa:items:view');
  await expect(menu.getByLabel('菜单可见权限').getByRole('option', { name: /新增/ })).toHaveCount(
    0,
  );
});

test('审计结果筛选及每页条数切换后保持正确事件', async ({ page }) => {
  await nav(page, '审计记录');
  await page.getByLabel('审计结果', { exact: true }).selectOption('执行成功');
  await expect(page.locator('tbody tr').filter({ hasNotText: '执行成功' })).toHaveCount(0);
  await expect(page.getByLabel('每页条数')).toBeVisible();
  await page.getByLabel('每页条数').selectOption('20');
  await expect(page.locator('tbody tr')).toHaveCount(1);
  await expect(page.locator('tbody tr')).toContainText('AUD-1056');
});

test('组织资料预检明确阻断已有引用的跨公司迁移', async ({ page }) => {
  await page.getByLabel('演示身份', { exact: true }).selectOption('global');
  await nav(page, '员工组织');
  await page.getByRole('button', { name: '导入组织资料', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '组织资料预检', exact: true });
  await dialog.getByLabel('资料类型').selectOption('organization');
  await dialog.getByLabel('演示场景').selectOption('migration');
  await dialog.getByRole('button', { name: '运行预检', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('已有角色、任职或授权历史引用');
  await expect(dialog.getByRole('button', { name: '应用模拟批次' })).toBeDisabled();
});

test('批量分配为不同部门员工保留各自来源，并在提交前预览固定配对', async ({ page }) => {
  await page.getByLabel('演示身份', { exact: true }).selectOption('global');
  await page.getByRole('button', { name: '分配角色', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '申请分配角色', exact: true });
  await dialog.getByLabel('选择分配员工').selectOption(['EMP-1003', 'EMP-1004']);
  await dialog.getByLabel('许清禾的授权来源').selectOption('team-one');
  await dialog.getByLabel('沈亦川的授权来源').selectOption('team-two');
  await dialog.getByRole('checkbox', { name: /办公基础角色/ }).check();
  await dialog.getByRole('textbox').fill('按不同任职来源分配基础办公角色');
  await dialog.getByRole('button', { name: '预览分配', exact: true }).click();
  await expect(dialog.getByRole('row').filter({ hasText: '许清禾' })).toContainText(
    '第一项目经理部',
  );
  await expect(dialog.getByRole('row').filter({ hasText: '沈亦川' })).toContainText(
    '第二项目经理部',
  );
  await dialog.getByRole('button', { name: '保存并等待 OA 投递', exact: true }).click();
  await nav(page, '变更申请');
  await page.getByRole('button', { name: '2 名员工 · 分配角色', exact: true }).click();
  const request = page.getByRole('dialog', { name: '申请详情', exact: true });
  await expect(
    request.getByText('新增：许清禾 / 办公基础角色 / 第一项目经理部', { exact: true }),
  ).toBeVisible();
  await expect(
    request.getByText('新增：沈亦川 / 办公基础角色 / 第二项目经理部', { exact: true }),
  ).toBeVisible();
});

test('离开未保存的角色配置时可以继续编辑，放弃后不改变有效配置', async ({ page }) => {
  await nav(page, '角色权限');
  await page
    .getByRole('row')
    .filter({ hasText: '第一项目经理部归档员' })
    .getByRole('button', { name: '配置权限' })
    .click();
  const config = page.getByRole('dialog', { name: '第一项目经理部归档员', exact: true });
  await config.getByRole('checkbox', { name: '系统进入权限', exact: true }).check();
  await page.keyboard.press('Escape');
  const confirmation = page.getByRole('alertdialog', { name: '放弃未保存修改', exact: true });
  await expect(confirmation).toBeVisible();
  await confirmation.getByRole('button', { name: '继续编辑' }).click();
  await expect(config.getByRole('checkbox', { name: '系统进入权限', exact: true })).toBeChecked();
  await page.keyboard.press('Escape');
  await confirmation.getByRole('button', { name: '放弃修改' }).click();
  await page
    .getByRole('row')
    .filter({ hasText: '第一项目经理部归档员' })
    .getByRole('button', { name: '配置权限' })
    .click();
  await expect(
    config.getByRole('checkbox', { name: '系统进入权限', exact: true }),
  ).not.toBeChecked();
});

test('停用父导航后子功能不参与权限结果，保留独立系统进入资格', async ({ page }) => {
  await page.getByLabel('演示身份', { exact: true }).selectOption('global');
  await nav(page, '系统与权限目录');
  await page.getByRole('button', { name: '新增功能', exact: true }).click();
  const create = page.getByRole('dialog', { name: '新增功能目录', exact: true });
  await create.getByLabel('目录类型').selectOption('navigation');
  await create.getByLabel('名称').fill('办公导航');
  await create.getByLabel('稳定编码').fill('office-nav');
  await create.getByRole('button', { name: '确认登记', exact: true }).click();
  await page.getByRole('button', { name: '配置菜单 公文管理', exact: true }).click();
  const config = page.getByRole('dialog', { name: '配置功能目录', exact: true });
  await config.getByLabel('父级目录').selectOption('办公导航');
  await config.getByRole('button', { name: '保存资料', exact: true }).click();
  await page.getByRole('button', { name: '停用导航 办公导航', exact: true }).click();
  const confirm = page.getByRole('dialog', { name: /停用.*办公导航/ });
  await confirm.getByRole('textbox').fill('模拟父目录暂停使用');
  await confirm.getByRole('button', { name: '确认停用', exact: true }).click();
  await page
    .locator('.prototype-tools')
    .getByRole('button', { name: '权限调用演示', exact: true })
    .click();
  await page.getByLabel('目标员工').selectOption('EMP-1001');
  await page.getByLabel('调用方绑定系统').selectOption('oa');
  await page.getByRole('button', { name: '查询有效权限', exact: true }).click();
  await expect(page.getByText('具备系统进入资格', { exact: true })).toBeVisible();
  await expect(page.locator('.operation-item').filter({ hasText: '公文管理' })).toHaveCount(0);
});

test('模拟调用故障展示错误而非成功空权限，恢复后可以正常查询', async ({ page }) => {
  await page
    .locator('.prototype-tools')
    .getByRole('button', { name: '权限调用演示', exact: true })
    .click();
  await page.getByLabel('模拟调用场景').selectOption('service-error');
  await page.getByRole('button', { name: '查询有效权限', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('服务暂不可用');
  await expect(page.getByText('不具备系统进入资格', { exact: true })).toHaveCount(0);
  await page.getByLabel('模拟调用场景').selectOption('normal');
  await page.getByRole('button', { name: '查询有效权限', exact: true }).click();
  await expect(page.getByText('具备系统进入资格', { exact: true })).toBeVisible();
});

test('审计关联申请打开同一固定申请详情', async ({ page }) => {
  await nav(page, '审计记录');
  await page
    .getByRole('row')
    .filter({ hasText: '许清禾' })
    .getByRole('button', { name: '详情' })
    .click();
  const audit = page.getByRole('dialog', { name: '审计事件详情', exact: true });
  await audit.getByRole('button', { name: '查看关联申请', exact: true }).click();
  const request = page.getByRole('dialog', { name: '申请详情', exact: true });
  await expect(request.getByText('许清禾 · 分配员工信息查询员', { exact: true })).toBeVisible();
});

test('缺少查看绑定的新功能保持停用，不能直接启用可打开菜单', async ({ page }) => {
  await page.getByLabel('演示身份', { exact: true }).selectOption('global');
  await nav(page, '系统与权限目录');
  await page.getByRole('button', { name: '新增功能', exact: true }).click();
  const create = page.getByRole('dialog', { name: '新增功能目录', exact: true });
  await create.getByLabel('名称').fill('仅编辑功能');
  await create.getByLabel('稳定编码').fill('edit-only-feature');
  await create.getByRole('checkbox', { name: '查看', exact: true }).uncheck();
  await create.getByRole('checkbox', { name: '新增', exact: true }).uncheck();
  await create.getByRole('button', { name: '确认登记', exact: true }).click();
  const feature = page.locator('.feature-card').filter({ hasText: '仅编辑功能' });
  await feature.getByRole('button', { name: '启用功能', exact: true }).click();
  const confirm = page.getByRole('dialog', { name: '启用仅编辑功能', exact: true });
  await confirm.getByRole('textbox').fill('核对菜单进入条件');
  await confirm.getByRole('button', { name: '确认启用', exact: true }).click();
  await expect(confirm).toBeVisible();
  await expect(confirm.getByRole('alert')).toContainText('查看/进入权限');
});
