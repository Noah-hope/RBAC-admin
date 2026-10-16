import { expect, test, type Page } from '@playwright/test';

async function openRegistration(page: Page) {
  await page.goto('/');
  await page.getByRole('button', { name: '切换为总管理员（演示）', exact: true }).click();
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('button', { name: '系统与权限目录', exact: true })
    .click();
  await page.getByRole('button', { name: '登记操作 公文管理', exact: true }).click();
  return page.getByRole('dialog', { name: '登记操作权限', exact: true });
}

// Catch registration being limited to five presets, auto-granting, or hiding custom
// operations from the role editor despite successful catalog registration.
test('自定义操作登记后可独立配置到角色，登记本身不修改已有角色', async ({ page }) => {
  const registration = await openRegistration(page);
  await registration.getByLabel('操作名称', { exact: true }).fill(' 打印 ');
  await registration.getByLabel('操作编码', { exact: true }).fill(' print ');
  await registration.getByRole('button', { name: '确认登记', exact: true }).click();
  const card = page.locator('.feature-card').filter({ hasText: '公文管理' });
  await expect(card.getByText('oa:items:print', { exact: true })).toBeVisible();
  await expect(card.getByRole('switch', { name: '公文管理打印停用', exact: true })).toBeVisible();
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('button', { name: '角色权限', exact: true })
    .click();
  const row = page.getByRole('row').filter({ hasText: '第一项目经理部协作员' });
  await row.getByRole('button', { name: '配置权限', exact: true }).click();
  const config = page.getByRole('dialog', { name: '第一项目经理部协作员', exact: true });
  const print = config.getByRole('checkbox', { name: '公文管理打印', exact: true });
  await expect(print).not.toBeChecked();
  await print.check();
  await config.getByRole('button', { name: '保存配置草稿', exact: true }).click();
  await config.getByRole('button', { name: '关闭', exact: true }).click();
  await expect(row).toContainText('3 项操作');
  await row.getByRole('button', { name: '配置权限', exact: true }).click();
  await expect(print).toBeChecked();
});

// Catch malformed IDs, duplicate code under another name, duplicate display names,
// and masquerading as the distinct system-entry permission.
test('拒绝缺失字段、非法编码、重复编码及重复操作名称且不产生部分登记', async ({ page }) => {
  const dialog = await openRegistration(page);
  for (const [name, code, error] of [
    ['打印', '', '请填写操作名称和操作编码'],
    ['', 'print', '请填写操作名称和操作编码'],
    ['打印', 'Print:bad', '操作编码须以小写字母开头，可含数字及短横线'],
    ['打印', 'view', '操作编码已存在'],
    ['查看', 'print', '操作名称已存在'],
    ['进入系统', 'enter', '系统进入权限由系统登记维护'],
  ]) {
    await dialog.getByLabel('操作名称', { exact: true }).fill(name);
    await dialog.getByLabel('操作编码', { exact: true }).fill(code);
    await dialog.getByRole('button', { name: '确认登记', exact: true }).click();
    await expect(dialog.getByRole('alert')).toHaveText(error);
    await expect(
      page.locator('.feature-card').filter({ hasText: '公文管理' }).getByRole('switch'),
    ).toHaveCount(5);
  }
});

// Catch leaking system-specific action columns into unrelated systems.
test('自定义操作仅出现在所属系统的角色配置中，重新登记时表单清空', async ({ page }) => {
  const registration = await openRegistration(page);
  await registration.getByLabel('操作名称', { exact: true }).fill('打印');
  await registration.getByLabel('操作编码', { exact: true }).fill('print');
  await registration.getByRole('button', { name: '确认登记', exact: true }).click();
  await page.getByRole('button', { name: '登记操作 公文管理', exact: true }).click();
  await expect(registration.getByLabel('操作名称', { exact: true })).toHaveValue('');
  await expect(registration.getByLabel('操作编码', { exact: true })).toHaveValue('');
  await expect(registration.getByRole('button', { name: '确认登记', exact: true })).toBeDisabled();
  await registration.getByRole('button', { name: '取消', exact: true }).click();
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('button', { name: '角色权限', exact: true })
    .click();
  await page
    .getByRole('row')
    .filter({ hasText: '员工信息查询员' })
    .getByRole('button', { name: '配置权限', exact: true })
    .click();
  const config = page.getByRole('dialog', { name: '员工信息查询员', exact: true });
  await expect(config.getByRole('columnheader', { name: '打印', exact: true })).toHaveCount(0);
  await expect(config.getByRole('checkbox', { name: '员工信息查看', exact: true })).toBeVisible();
});
