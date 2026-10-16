import { expect, test } from '@playwright/test';

test('无需展开演示工具即可切换总管理员并访问平台管理，切回后隐藏无权菜单', async ({ page }) => {
  await page.setViewportSize({ width: 1063, height: 704 });
  await page.goto('/');
  await expect(page.getByLabel('演示身份', { exact: true })).not.toBeVisible();
  await page.getByRole('button', { name: '切换为总管理员（演示）', exact: true }).click();
  const navigation = page.getByRole('navigation', { name: '主导航' });
  for (const name of ['系统与权限目录', '管理员与范围', '员工组织']) {
    await navigation.getByRole('button', { name, exact: true }).click();
    await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
  }
  await page.getByRole('button', { name: '切换为部门权限管理员（演示）', exact: true }).click();
  await expect(page.getByRole('heading', { name: '员工授权', exact: true })).toBeVisible();
  await expect(navigation.getByRole('button', { name: '管理员与范围', exact: true })).toHaveCount(
    0,
  );
});

for (const viewport of [
  { width: 1063, height: 704 },
  { width: 1440, height: 900 },
  { width: 390, height: 844 },
]) {
  test(`分配弹窗在 ${viewport.width}px 保持紧凑选择区，文字可读且可以预览分配`, async ({
    page,
  }) => {
    await page.setViewportSize(viewport);
    await page.goto('/');
    await page.getByRole('button', { name: '分配角色', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: '申请分配角色', exact: true });
    const employeeSelect = dialog.getByLabel('选择分配员工');
    // Catch full-width checkbox rules that squeeze role text into a vertical column.
    for (const checkbox of await dialog.getByRole('checkbox').all()) {
      const box = await checkbox.boundingBox();
      expect.soft(box!.width, '复选框不能占据整行').toBeLessThanOrEqual(24);
      expect.soft(box!.height).toBeLessThanOrEqual(24);
    }
    const selectBox = await employeeSelect.boundingBox();
    expect.soft(selectBox!.height, '员工选择区应有明确高度上限').toBeLessThanOrEqual(120);
    for (const row of await dialog.locator('.choice-row').all()) {
      const box = await row.boundingBox();
      expect.soft(box!.height, '角色说明应横向阅读').toBeLessThanOrEqual(110);
    }
    const overflow = await dialog
      .locator('.dialog-body')
      .evaluate((el) => el.scrollWidth - el.clientWidth);
    expect.soft(overflow, '弹窗内容不得整体横向溢出').toBeLessThanOrEqual(1);
    await employeeSelect.selectOption('EMP-1004');
    await dialog.getByLabel('实际来源部门').selectOption('team-two');
    await dialog.getByRole('checkbox', { name: /办公基础角色/ }).check();
    await dialog.getByRole('textbox').fill('为工程二组新任职申请基础办公角色');
    await dialog.getByRole('button', { name: '预览分配', exact: true }).click();
    await expect(dialog.getByRole('row').filter({ hasText: '沈亦川' })).toContainText('工程二组');
    await expect(
      dialog.getByRole('button', { name: '保存并等待 OA 投递', exact: true }),
    ).toBeVisible();
  });
}
