import { expect, test, type Page } from '@playwright/test';

async function openDirectory(page: Page) {
  await page.goto('/');
  await page.getByRole('button', { name: '切换为总管理员（演示）', exact: true }).click();
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('button', { name: '系统与权限目录', exact: true })
    .click();
}

test('1063px 图标栏可以展开为文字导航，再次收起并正常切换页面', async ({ page }) => {
  await page.setViewportSize({ width: 1063, height: 704 });
  await page.goto('/');
  await page.getByRole('button', { name: '展开侧栏', exact: true }).click();
  await expect
    .poll(async () => (await page.locator('.sidebar').boundingBox())!.width)
    .toBeGreaterThan(200);
  await expect(
    page.getByRole('navigation', { name: '主导航' }).getByText('角色权限', { exact: true }),
  ).toBeVisible();
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('button', { name: '角色权限', exact: true })
    .click();
  await expect(page.getByRole('heading', { name: '角色权限', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '收起侧栏', exact: true }).click();
  await expect
    .poll(async () => (await page.locator('.sidebar').boundingBox())!.width)
    .toBeLessThan(100);
});

test('未选择或填写操作时禁止空提交，所属功能只读而非待编辑必填字段', async ({ page }) => {
  await openDirectory(page);
  await page.getByRole('button', { name: '登记操作 公文管理', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '登记操作权限', exact: true });
  await expect(dialog.getByRole('button', { name: '确认登记', exact: true })).toBeDisabled();
  await expect(
    dialog.getByText('常用操作已全部登记，可在下方新增自定义操作。', { exact: true }),
  ).toBeVisible();
  await expect(dialog.getByLabel('所属功能', { exact: true })).toHaveAttribute('readonly', '');
  await expect(dialog.getByLabel('功能编码', { exact: true })).toHaveValue('items');
});

test('功能操作可以单项和批量收起展开，操作开关状态保持不变', async ({ page }) => {
  await openDirectory(page);
  const card = page.locator('.feature-card').filter({ hasText: '公文管理' });
  await expect(card.getByRole('switch', { name: '公文管理查看停用', exact: true })).toHaveAttribute(
    'aria-checked',
    'true',
  );
  await card.getByRole('button', { name: '收起操作 公文管理', exact: true }).click();
  await expect(card.getByRole('switch')).toHaveCount(0);
  await card.getByRole('button', { name: '展开操作 公文管理', exact: true }).click();
  await expect(card.getByRole('switch', { name: '公文管理查看停用', exact: true })).toHaveAttribute(
    'aria-checked',
    'true',
  );
  await page.getByRole('button', { name: '收起全部操作', exact: true }).click();
  await expect(page.locator('.feature-card').getByRole('switch')).toHaveCount(0);
  await page.getByRole('button', { name: '展开全部操作', exact: true }).click();
  await expect(card.getByRole('switch')).toHaveCount(5);
});

test('登记只补充尚未存在的操作，重新进入时已登记项不再可选', async ({ page }) => {
  await openDirectory(page);
  await page.getByRole('button', { name: '新增功能', exact: true }).click();
  const create = page.getByRole('dialog', { name: '新增功能目录', exact: true });
  await create.getByLabel('名称').fill('归档查询');
  await create.getByLabel('稳定编码').fill('archive-query');
  await create.getByRole('checkbox', { name: '新增', exact: true }).uncheck();
  await create.getByRole('checkbox', { name: '编辑', exact: true }).uncheck();
  await create.getByRole('button', { name: '确认登记', exact: true }).click();
  await page.getByRole('button', { name: '登记操作 归档查询', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '登记操作权限', exact: true });
  await expect(dialog.getByRole('button', { name: '确认登记', exact: true })).toBeDisabled();
  await expect(dialog.getByRole('checkbox', { name: '查看', exact: true })).toHaveCount(0);
  await dialog.getByRole('checkbox', { name: '编辑', exact: true }).check();
  await dialog.getByRole('button', { name: '确认登记', exact: true }).click();
  await expect(
    page
      .locator('.feature-card')
      .filter({ hasText: '归档查询' })
      .getByText('oa:archive-query:edit', { exact: true }),
  ).toBeVisible();
  await page.getByRole('button', { name: '登记操作 归档查询', exact: true }).click();
  await expect(dialog.getByRole('checkbox', { name: '编辑', exact: true })).toHaveCount(0);
});
