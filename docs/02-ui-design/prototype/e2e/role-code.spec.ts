import { expect, test, type Page } from '@playwright/test';

async function createRole(page: Page, name: string, shared = false) {
  await page.getByRole('button', { name: '新建角色', exact: true }).click();
  const create = page.getByRole('dialog', { name: '新建角色草稿', exact: true });
  await create.getByLabel('角色名称').fill(name);
  if (shared) await create.getByLabel('角色归属').selectOption('');
  await create.getByRole('button', { name: '保存角色草稿', exact: true }).click();
  return page.getByRole('dialog', { name, exact: true });
}

test('无需填写编码即可创建角色，更名并重新打开后编码保持不变', async ({ page }) => {
  await page.goto('/');
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('button', { name: '角色权限', exact: true })
    .click();
  await page.getByRole('button', { name: '新建角色', exact: true }).click();
  const create = page.getByRole('dialog', { name: '新建角色草稿', exact: true });
  // Catch a return to mandatory/manual role codes and regeneration during editing.
  await expect(create.getByLabel('角色编码')).toHaveAttribute('readonly', '');
  await create.getByRole('button', { name: '保存角色草稿', exact: true }).click();
  await expect(create.getByLabel('角色名称')).toHaveAttribute('aria-invalid', 'true');
  await create.getByLabel('角色名称').fill('资料归档协作员');
  await create.getByRole('button', { name: '保存角色草稿', exact: true }).click();
  const detail = page.getByRole('dialog', { name: '资料归档协作员', exact: true });
  const code = detail.getByLabel('角色编码');
  await expect(code).toHaveAttribute('readonly', '');
  const generated = await code.inputValue();
  expect(generated).toMatch(/^ROLE_\d+$/);
  await detail.getByLabel('草稿角色名称').fill('资料归档协作员（更名）');
  await detail.getByRole('button', { name: '保存配置草稿', exact: true }).click();
  await page
    .getByRole('dialog', { name: '资料归档协作员（更名）', exact: true })
    .getByRole('button', { name: '关闭', exact: true })
    .click();
  await page.getByLabel('搜索角色', { exact: true }).fill(generated);
  const row = page.getByRole('row').filter({ hasText: '资料归档协作员（更名）' });
  await expect(row).toContainText(generated);
  await row.getByRole('button', { name: '配置权限', exact: true }).click();
  await expect(
    page
      .getByRole('dialog', { name: '资料归档协作员（更名）', exact: true })
      .getByLabel('角色编码'),
  ).toHaveValue(generated);
});

test('不同公司的同名角色自动生成不同编码，切回公司后原编码保留', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: '切换为总管理员（演示）', exact: true }).click();
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('button', { name: '角色权限', exact: true })
    .click();
  const first = await createRole(page, '项目资料查询员', true);
  const firstCode = await first.getByLabel('角色编码').inputValue();
  await first.getByRole('button', { name: '关闭', exact: true }).click();
  await page.getByLabel('当前公司', { exact: true }).selectOption('digital');
  const second = await createRole(page, '项目资料查询员', true);
  const secondCode = await second.getByLabel('角色编码').inputValue();
  expect(secondCode).toMatch(/^ROLE_\d+$/);
  expect(secondCode).not.toBe(firstCode);
  await second.getByRole('button', { name: '关闭', exact: true }).click();
  await page.getByLabel('当前公司', { exact: true }).selectOption('municipal');
  await expect(page.getByRole('row').filter({ hasText: '项目资料查询员' })).toContainText(
    firstCode,
  );
});
