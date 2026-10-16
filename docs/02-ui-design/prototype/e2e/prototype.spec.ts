import { expect, test, type Page } from '@playwright/test';

async function navigate(page: Page, name: string) {
  if (name === '权限验证') {
    const tools = page.locator('.prototype-tools');
    await tools.evaluate((el) => ((el as HTMLDetailsElement).open = true));
    await tools.getByRole('button', { name: '权限调用演示', exact: true }).click();
    return;
  }
  if (name === '系统目录') name = '系统与权限目录';
  const mobile = await page.getByRole('button', { name: '打开导航', exact: true }).isVisible();
  if (mobile) await page.getByRole('button', { name: '打开导航', exact: true }).click();
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('button', { name: new RegExp(`^${name}(?: \\d+)?$`) })
    .click();
}

test.beforeEach(async ({ page }) => {
  await page.goto('/');
  await page.locator('.prototype-tools').evaluate((el) => ((el as HTMLDetailsElement).open = true));
});

test('部门范围、员工搜索与总管理员页面边界', async ({ page }) => {
  await expect(page.getByRole('heading', { name: '员工授权', exact: true })).toBeVisible();
  await page.getByRole('textbox', { name: '搜索员工' }).fill('江书遥');
  await expect(page.getByText('没有匹配的员工', { exact: true })).toBeVisible();
  await page.getByRole('textbox', { name: '搜索员工' }).fill('林予安');
  await expect(page.getByRole('button', { name: /予安 林予安/ })).toBeVisible();
  await expect(
    page
      .getByRole('navigation', { name: '主导航' })
      .getByRole('button', { name: '系统与权限目录', exact: true }),
  ).toHaveCount(0);
  await page.getByLabel('演示页面状态').selectOption('forbidden');
  await expect(page.getByRole('heading', { name: '当前身份无权访问此页面' })).toBeVisible();
  await page.getByLabel('演示身份').selectOption('global');
  await navigate(page, '系统目录');
  await expect(page.getByRole('heading', { name: '系统与权限目录' })).toBeVisible();
});

test('关闭工程一组来源，保留工程二组独立共享角色', async ({ page }) => {
  await page.getByRole('button', { name: /予安 林予安/ }).click();
  const drawer = page.getByRole('dialog', { name: '林予安', exact: true });
  await expect(drawer.getByText('办公基础角色', { exact: true })).toHaveCount(2);
  await drawer.getByRole('button', { name: '关闭原部门权限', exact: true }).click();
  const modal = page.getByRole('dialog', { name: '关闭原部门权限', exact: true });
  await modal.getByLabel('原授权来源部门').selectOption('team-one');
  await modal.getByRole('textbox').fill('调离工程一组，保留工程二组兼岗');
  await modal.getByRole('button', { name: '预览影响' }).click();
  await modal.getByText('我已确认影响范围与办理原因').click();
  await modal.getByRole('button', { name: '确认关闭来源' }).click();
  await expect(
    drawer
      .locator('.source-card')
      .filter({ hasText: '工程一组' })
      .getByText('已失效', { exact: true }),
  ).toHaveCount(3);
  await expect(
    drawer
      .locator('.source-card')
      .filter({ hasText: '工程二组' })
      .getByText('有效', { exact: true }),
  ).toHaveCount(1);
});

test('关闭只有旧分配申请的来源，迟到批准不能复权', async ({ page }) => {
  await page.getByRole('button', { name: /清禾 许清禾/ }).click();
  const drawer = page.getByRole('dialog', { name: '许清禾', exact: true });
  await drawer.getByRole('button', { name: '关闭原部门权限', exact: true }).click();
  const modal = page.getByRole('dialog', { name: '关闭原部门权限', exact: true });
  await modal.getByRole('textbox').fill('调离原部门，关闭待执行来源');
  await modal.getByRole('button', { name: '预览影响' }).click();
  await modal.getByText('我已确认影响范围与办理原因').click();
  await modal.getByRole('button', { name: '确认关闭来源' }).click();
  await drawer.getByRole('button', { name: '关闭对话框' }).click();
  await navigate(page, '变更申请');
  await page.getByRole('button', { name: '许清禾 · 分配员工信息查询员', exact: true }).click();
  const request = page.getByRole('dialog', { name: '申请详情' });
  await expect(request.getByText('已失效', { exact: true })).toBeVisible();
  await request.getByRole('button', { name: '原型演示工具' }).click();
  await request.getByRole('button', { name: '模拟 OA 批准', exact: true }).click();
  await expect(request.getByRole('button', { name: '模拟自动执行', exact: true })).toBeDisabled();
  await expect(request.getByText('已失效', { exact: true })).toBeVisible();
});

test('同一申请投递重试、批准与执行结果分开', async ({ page }) => {
  await navigate(page, '变更申请');
  await page.getByRole('button', { name: '程以宁 · 分配员工信息查询员', exact: true }).click();
  const request = page.getByRole('dialog', { name: '申请详情' });
  const id = await request.locator('.dialog-header p').textContent();
  await request.getByRole('button', { name: '重试投递同一申请' }).click();
  await expect(request.locator('.dialog-header p')).toHaveText(id!);
  await request.getByRole('button', { name: '原型演示工具' }).click();
  await request.getByRole('button', { name: '模拟 OA 批准', exact: true }).click();
  await expect(request.getByText('待执行', { exact: true })).toBeVisible();
  await request.getByRole('button', { name: '模拟执行失败', exact: true }).click();
  await expect(request.getByText('执行失败', { exact: true })).toBeVisible();
  await request.getByRole('button', { name: '模拟服务恢复', exact: true }).click();
  await expect(request.getByText('执行成功', { exact: true })).toBeVisible();
  await request.getByRole('button', { name: '关闭对话框' }).click();
  await navigate(page, '员工授权');
  await page.getByRole('textbox', { name: '搜索员工' }).fill('程以宁');
  await page.getByRole('button', { name: /以宁 程以宁/ }).click();
  await expect(
    page.getByRole('dialog', { name: '程以宁' }).getByText('员工信息查询员', { exact: true }),
  ).toBeVisible();
});

test('共享角色只读，编辑不推导查看菜单', async ({ page }) => {
  await navigate(page, '角色权限');
  await page
    .getByRole('row')
    .filter({ hasText: '办公基础角色' })
    .getByRole('button', { name: '查看配置' })
    .click();
  const role = page.getByRole('dialog', { name: '办公基础角色' });
  await expect(role.getByRole('checkbox', { name: '系统进入权限' })).toBeDisabled();
  await role.getByRole('button', { name: '关闭对话框' }).click();
  await navigate(page, '权限验证');
  await page.getByLabel('目标员工').selectOption('EMP-1002');
  await page.getByLabel('调用方绑定系统').selectOption('hr');
  await page.getByRole('button', { name: '查询有效权限' }).click();
  await expect(page.getByText('具备系统进入资格', { exact: true })).toBeVisible();
  await expect(page.getByText('没有可见菜单。编辑不自动推导查看。')).toBeVisible();
  await expect(
    page.locator('.operation-item').getByText('员工信息 / 编辑', { exact: true }),
  ).toBeVisible();
});

test('全局停用后恢复身份，旧来源不恢复', async ({ page }) => {
  await page.getByLabel('演示身份').selectOption('global');
  await page.getByRole('button', { name: /予安 林予安/ }).click();
  const drawer = page.getByRole('dialog', { name: '林予安', exact: true });
  await drawer.getByRole('button', { name: '全局停用员工', exact: true }).click();
  let modal = page.getByRole('dialog', { name: '全局停用员工', exact: true });
  await modal.getByRole('textbox').fill('演示全局离职');
  await modal.getByRole('button', { name: '预览影响' }).click();
  await modal.getByText('我已确认影响范围与办理原因').click();
  await modal.getByRole('button', { name: '确认全局停用' }).click();
  await drawer.getByRole('button', { name: '恢复员工身份', exact: true }).click();
  modal = page.getByRole('dialog', { name: '恢复员工身份', exact: true });
  await modal.getByRole('textbox').fill('演示恢复身份');
  await modal.getByRole('button', { name: '预览影响' }).click();
  await modal.getByText('我已确认影响范围与办理原因').click();
  await modal.getByRole('button', { name: '确认恢复身份' }).click();
  await expect(drawer.getByText('已失效', { exact: true })).toHaveCount(4);
  await expect(drawer.getByText('有效', { exact: true })).toHaveCount(0);
});

test('模拟文件预检与新任职无自动授权', async ({ page }) => {
  await page.getByLabel('演示身份').selectOption('global');
  await navigate(page, '员工组织');
  await page.getByRole('button', { name: '导入员工变更' }).click();
  const modal = page.getByRole('dialog', { name: '导入员工变更' });
  await modal.getByRole('button', { name: '载入演示批次' }).click();
  await modal.getByRole('button', { name: '开始预检' }).click();
  await expect(modal.getByText('预检通过 · 3 条模拟明细')).toBeVisible();
  await modal.getByRole('textbox').fill('人员入职与调岗演示');
  await modal.getByText('已核对变更影响，确认应用此模拟批次').click();
  await modal.getByRole('button', { name: '确认应用模拟批次' }).click();
  await expect(modal.getByRole('heading', { name: '模拟批次已完成' })).toBeVisible();
  await modal.getByRole('button', { name: '完成', exact: true }).click();
  await navigate(page, '员工授权');
  await page.getByRole('textbox', { name: '搜索员工' }).fill('叶初晴');
  await expect(page.getByRole('cell', { name: '待分配', exact: true })).toBeVisible();
});

test('小屏导航、页面状态和键盘关闭抽屉', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('button', { name: '打开导航', exact: true })).toBeVisible();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBe(390);
  await navigate(page, '角色权限');
  await expect(page.getByRole('heading', { name: '角色权限', exact: true })).toBeVisible();
  await page
    .getByRole('row')
    .filter({ hasText: '办公基础角色' })
    .getByRole('button', { name: '查看配置' })
    .click();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await page.getByLabel('演示页面状态').selectOption('error');
  await expect(page.getByRole('heading', { name: '暂时无法读取数据' })).toBeVisible();
  await page.getByRole('button', { name: '重新加载' }).click();
  await expect(page.getByRole('heading', { name: '角色权限', exact: true })).toBeVisible();
});

test('分配角色先固定申请，执行前不改变已有授权', async ({ page }) => {
  await page.getByRole('button', { name: /亦川 沈亦川/ }).click();
  const drawer = page.getByRole('dialog', { name: '沈亦川', exact: true });
  await drawer.getByRole('button', { name: '申请分配角色', exact: true }).click();
  const assignment = page.getByRole('dialog', { name: '申请分配角色', exact: true });
  await assignment.getByRole('checkbox', { name: /办公基础角色/ }).check();
  await assignment.getByRole('textbox').fill('新增协作办公职责');
  await assignment.getByRole('button', { name: '预览分配', exact: true }).click();
  await assignment.getByRole('button', { name: '保存并等待 OA 投递' }).click();
  await expect(drawer.getByText('办公基础角色', { exact: true })).toHaveCount(0);
  await drawer.getByRole('button', { name: '关闭对话框' }).click();
  await navigate(page, '变更申请');
  await page.getByRole('button', { name: '沈亦川 · 分配角色', exact: true }).click();
  const request = page.getByRole('dialog', { name: '申请详情' });
  await request.getByRole('button', { name: '投递至模拟 OA', exact: true }).click();
  await request.getByRole('button', { name: '原型演示工具' }).click();
  await request.getByRole('button', { name: '模拟 OA 批准', exact: true }).click();
  await expect(request.getByText('待执行', { exact: true })).toBeVisible();
  await request.getByRole('button', { name: '模拟自动执行', exact: true }).click();
  await request.getByRole('button', { name: '关闭对话框' }).click();
  await navigate(page, '员工授权');
  await page.getByRole('button', { name: /亦川 沈亦川/ }).click();
  await expect(drawer.getByText('办公基础角色', { exact: true })).toBeVisible();
});

test('角色草稿独立配置操作，通过模拟批准与执行后激活', async ({ page }) => {
  await navigate(page, '角色权限');
  await page.getByRole('button', { name: '新建角色', exact: true }).click();
  const draft = page.getByRole('dialog', { name: '新建角色草稿', exact: true });
  await draft.getByLabel('角色名称').fill('资料审阅员');
  await draft.getByLabel('唯一编码').fill('ENG1_REVIEW_DEMO');
  await draft.getByLabel('角色归属').selectOption('team-one');
  await draft.getByRole('button', { name: '保存角色草稿' }).click();
  const config = page.getByRole('dialog', { name: '资料审阅员', exact: true });
  await config.getByRole('checkbox', { name: '系统进入权限', exact: true }).check();
  await config.getByRole('checkbox', { name: '公文管理查看', exact: true }).check();
  await expect(
    config.getByRole('checkbox', { name: '公文管理编辑', exact: true }),
  ).not.toBeChecked();
  await config.getByRole('textbox', { name: /变更原因/ }).fill('只授予公文查看功能');
  await config.getByRole('button', { name: '预览变更' }).click();
  await config.getByRole('button', { name: '保存并等待 OA 投递' }).click();
  await expect(
    page.getByRole('row').filter({ hasText: 'ENG1_REVIEW_DEMO' }).getByText('待配置'),
  ).toBeVisible();
  await navigate(page, '变更申请');
  await page.getByRole('button', { name: '资料审阅员 · 调整配置', exact: true }).click();
  const request = page.getByRole('dialog', { name: '申请详情' });
  await request.getByRole('button', { name: '投递至模拟 OA' }).click();
  await request.getByRole('button', { name: '原型演示工具' }).click();
  await request.getByRole('button', { name: '模拟 OA 批准', exact: true }).click();
  await request.getByRole('button', { name: '模拟自动执行', exact: true }).click();
  await request.getByRole('button', { name: '关闭对话框' }).click();
  await navigate(page, '角色权限');
  await expect(
    page.getByRole('row').filter({ hasText: 'ENG1_REVIEW_DEMO' }).getByText('已生效'),
  ).toBeVisible();
});

test('建立账号与任命分别办理，停用账号不修改业务角色', async ({ page }) => {
  await page.getByLabel('演示身份').selectOption('global');
  await navigate(page, '管理员与范围');
  await page.getByRole('button', { name: '建立后台账号' }).click();
  let modal = page.getByRole('dialog', { name: '建立后台账号' });
  await modal.getByLabel('关联有效员工').selectOption('EMP-1008');
  await modal.getByLabel('平台登录名').fill('gu.demo');
  await modal.getByLabel('临时密码', { exact: true }).fill('example-only-input');
  await modal.getByLabel('确认临时密码', { exact: true }).fill('example-only-input');
  await modal.getByRole('textbox', { name: '办理原因' }).fill('演示建立管理员账号');
  await modal.getByRole('button', { name: '创建演示账号' }).click();
  await expect(
    page
      .getByRole('row')
      .filter({ hasText: 'gu.demo' })
      .getByRole('cell', { name: '未任命', exact: true }),
  ).toBeVisible();
  await page.getByRole('button', { name: '管理范围', exact: true }).click();
  await page.getByRole('button', { name: '编辑范围' }).click();
  modal = page.getByRole('dialog', { name: '配置管理范围' });
  await modal.getByRole('checkbox', { name: /顾言溪/ }).check();
  await modal.getByRole('textbox').fill('增加同范围协作管理员');
  await modal.getByRole('button', { name: '确认保存范围' }).click();
  await page.getByRole('button', { name: '后台账号', exact: true }).click();
  await page
    .getByRole('row')
    .filter({ hasText: 'gu.demo' })
    .getByRole('button', { name: '停用', exact: true })
    .click();
  modal = page.getByRole('dialog', { name: '停用后台账号' });
  await modal.getByRole('textbox').fill('暂停后台访问');
  await modal.getByRole('button', { name: '确认变更' }).click();
  await expect(
    page.getByRole('row').filter({ hasText: 'gu.demo' }).getByText('已停用'),
  ).toBeVisible();
  await navigate(page, '员工授权');
  await page.getByRole('textbox', { name: '搜索员工' }).fill('顾言溪');
  await page.getByRole('button', { name: /言溪 顾言溪/ }).click();
  await expect(
    page.getByRole('dialog', { name: '顾言溪' }).getByText('有效', { exact: true }),
  ).toBeVisible();
});

test('系统停用返回调用错误，区别于空权限结果', async ({ page }) => {
  await page.getByLabel('演示身份').selectOption('global');
  await navigate(page, '系统目录');
  await page.getByRole('button', { name: '停用系统', exact: true }).click();
  const modal = page.getByRole('dialog', { name: '停用协同办公系统' });
  await modal.getByRole('textbox').fill('演示维护停用');
  await modal.getByRole('button', { name: '确认停用' }).click();
  await navigate(page, '权限验证');
  await page.getByRole('button', { name: '查询有效权限' }).click();
  await expect(page.getByRole('alert')).toHaveText(
    '调用系统已停用，拒绝本次查询；这不是空权限结果',
  );
  await expect(page.getByText('不具备系统进入资格', { exact: true })).toHaveCount(0);
});

test('审计只读且不向部门管理员展示平台任命事件', async ({ page }) => {
  await navigate(page, '审计记录');
  await page.getByRole('textbox', { name: '搜索审计记录' }).fill('配置管理范围');
  await expect(page.getByText('没有匹配的审计记录', { exact: true })).toBeVisible();
  await page.getByLabel('演示身份').selectOption('global');
  await navigate(page, '审计记录');
  await page.getByRole('textbox', { name: '搜索审计记录' }).fill('配置管理范围');
  await expect(page.getByRole('row').filter({ hasText: '配置管理范围' })).toHaveCount(1);
  await page
    .getByRole('row')
    .filter({ hasText: '配置管理范围' })
    .getByRole('button', { name: '详情' })
    .click();
  await expect(
    page.getByRole('dialog').getByText('历史不代表当前权限；本页面不能修改、重放或删除事件。'),
  ).toBeVisible();
});

test('本人改密清空输入并返回演示登录，普通员工不开放注册', async ({ page }) => {
  await page.getByRole('button', { name: /李乔 部门权限管理员/ }).click();
  await page.getByRole('button', { name: '修改本人密码' }).click();
  const modal = page.getByRole('dialog', { name: '修改本人密码' });
  await modal.getByLabel('当前密码', { exact: true }).fill('example-only-input');
  await modal.getByLabel('新密码', { exact: true }).fill('another-example-input');
  await modal.getByLabel('确认新密码', { exact: true }).fill('another-example-input');
  await modal.getByRole('button', { name: '修改并重新登录' }).click();
  await expect(page.getByRole('heading', { name: '登录权限中心' })).toBeVisible();
  await expect(page.getByLabel('演示密码', { exact: true })).toBeEmpty();
  await page.getByLabel('演示密码', { exact: true }).fill('example-only-input');
  await page.getByRole('button', { name: '登录演示工作区' }).click();
  await expect(page.getByRole('heading', { name: '员工授权', exact: true })).toBeVisible();
});

test('多项任命选择明确范围，空系统不能新增角色授权', async ({ page }) => {
  await page.getByLabel('演示身份').selectOption('global');
  await navigate(page, '管理员与范围');
  await page.getByRole('button', { name: '管理范围', exact: true }).click();
  await page.getByRole('button', { name: '新增管理范围' }).click();
  let modal = page.getByRole('dialog', { name: '配置管理范围' });
  await modal.getByRole('checkbox', { name: '人力资源系统', exact: true }).check();
  await modal.getByRole('checkbox', { name: /李乔/ }).check();
  await modal.getByRole('textbox').fill('新增 HR 专项演示任命');
  await modal.getByRole('button', { name: '确认保存范围' }).click();
  await page.getByLabel('演示身份').selectOption('department');
  const scopeSelect = page.getByLabel('当前管理任命');
  await expect(scopeSelect.locator('option')).toHaveCount(2);
  const id = await scopeSelect.locator('option').last().getAttribute('value');
  await scopeSelect.selectOption(id!);
  await expect(page.locator('.context-systems .small-tag')).toHaveText(['HR']);
  await page.getByLabel('演示身份').selectOption('global');
  await navigate(page, '管理员与范围');
  await page.getByRole('button', { name: '管理范围', exact: true }).click();
  await page.locator('.management-card').first().getByRole('button', { name: '编辑范围' }).click();
  modal = page.getByRole('dialog', { name: '配置管理范围' });
  await modal.getByRole('checkbox', { name: '人力资源系统', exact: true }).uncheck();
  await modal.getByRole('checkbox', { name: '协同办公系统', exact: true }).uncheck();
  await modal.getByRole('textbox').fill('演示无可管理系统状态');
  await modal.getByRole('button', { name: '确认保存范围' }).click();
  await page.getByLabel('演示身份').selectOption('department');
  await expect(page.getByRole('button', { name: '分配角色', exact: true })).toBeDisabled();
  await expect(
    page.getByText('当前任命未配置可管理系统，暂时没有可操作系统。请联系总管理员核对范围。'),
  ).toBeVisible();
});

test('跨公司兼岗分别计算功能授权，不合并另一公司角色', async ({ page }) => {
  await page.getByLabel('演示身份').selectOption('global');
  await page.getByRole('combobox', { name: '当前公司', exact: true }).selectOption('digital');
  await navigate(page, '权限验证');
  await page.getByLabel('目标员工').selectOption('EMP-1002');
  await page.getByLabel('调用方绑定系统').selectOption('project');
  await page.getByRole('button', { name: '查询有效权限' }).click();
  await expect(page.getByText('具备系统进入资格', { exact: true })).toBeVisible();
  await page.getByLabel('调用方绑定系统').selectOption('oa');
  await page.getByRole('button', { name: '查询有效权限' }).click();
  await expect(page.getByText('不具备系统进入资格', { exact: true })).toBeVisible();
  await page.getByRole('combobox', { name: '当前公司', exact: true }).selectOption('municipal');
  await page.getByLabel('目标员工').selectOption('EMP-1002');
  await page.getByLabel('调用方绑定系统').selectOption('oa');
  await page.getByRole('button', { name: '查询有效权限' }).click();
  await expect(page.getByText('具备系统进入资格', { exact: true })).toBeVisible();
});
