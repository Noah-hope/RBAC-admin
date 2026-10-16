import { expect, test, type Page } from '@playwright/test';

async function nav(page: Page, name: string) {
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('button', { name, exact: true })
    .click();
}

test('权限管理员仅看到本次指定部门，其他部门的员工和授权来源不可办理', async ({ page }) => {
  await page.goto('/');
  // Catch the old inherited scope that exposed employees from two additional departments.
  await page.getByLabel('搜索员工', { exact: true }).fill('沈亦川');
  await expect(page.getByText('没有匹配的员工', { exact: true })).toBeVisible();
  await page.getByLabel('搜索员工', { exact: true }).fill('林予安');
  await page.getByRole('button', { name: /予安 林予安/ }).click();
  const detail = page.getByRole('dialog', { name: '林予安', exact: true });
  await expect(detail.getByText('办公基础角色', { exact: true })).toHaveCount(1);
  await detail.getByRole('button', { name: '关闭原部门权限', exact: true }).click();
  const close = page.getByRole('dialog', { name: '关闭原部门权限', exact: true });
  await expect(close.getByLabel('原授权来源部门').locator('option')).toHaveCount(1);
  await expect(close.getByLabel('原授权来源部门')).toHaveValue('team-one');
});

test('切换独立部门任命后员工与角色范围切换，任命之间不合并', async ({ page }) => {
  await page.goto('/');
  const management = page.getByLabel('当前管理任命');
  await management.selectOption('M-02');
  await page.getByLabel('搜索员工', { exact: true }).fill('沈亦川');
  await expect(page.getByRole('button', { name: /亦川 沈亦川/ })).toBeVisible();
  await page.getByLabel('搜索员工', { exact: true }).fill('许清禾');
  await expect(page.getByText('没有匹配的员工', { exact: true })).toBeVisible();
  await management.selectOption('M-01');
  await nav(page, '角色权限');
  await page.getByRole('button', { name: '新建角色', exact: true }).click();
  const create = page.getByRole('dialog', { name: '新建角色草稿', exact: true });
  await expect(create.getByLabel('角色归属').locator('option')).toHaveCount(1);
  await expect(create.getByLabel('角色归属')).toHaveValue('team-one');
});

test('总管理员为一个明确部门配置任命，范围不扩展到其他部门', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: '切换为总管理员（演示）', exact: true }).click();
  await nav(page, '管理员与范围');
  await page.getByRole('button', { name: '管理范围', exact: true }).click();
  await page
    .locator('.management-card')
    .first()
    .getByRole('button', { name: '编辑范围', exact: true })
    .click();
  const scope = page.getByRole('dialog', { name: '配置管理范围', exact: true });
  await scope.getByLabel('指定部门', { exact: true }).selectOption('engineering');
  await scope.getByRole('checkbox', { name: /李乔/ }).check();
  await scope.getByRole('textbox').fill('调整为工程技术部独立任命');
  await scope.getByRole('button', { name: '确认保存范围', exact: true }).click();
  await page.getByRole('button', { name: '切换为部门权限管理员（演示）', exact: true }).click();
  await page.getByLabel('搜索员工', { exact: true }).fill('沈亦川');
  await expect(page.getByText('没有匹配的员工', { exact: true })).toBeVisible();
  await page.getByLabel('搜索员工', { exact: true }).fill('顾言溪');
  await expect(page.getByRole('button', { name: /言溪 顾言溪/ })).toBeVisible();
});
