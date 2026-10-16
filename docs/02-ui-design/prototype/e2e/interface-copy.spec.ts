import { expect, test } from '@playwright/test';

test('手机端可以打开当前页面帮助并返回列表', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await page.getByRole('button', { name: '打开导航', exact: true }).click();
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('button', { name: '角色权限', exact: true })
    .click();
  const button = page.getByRole('button', { name: '页面帮助', exact: true });
  await expect(button).toBeVisible();
  await button.click();
  const help = page.getByRole('dialog', { name: '页面帮助', exact: true });
  await expect(help).toContainText('公司级共享角色仅由总管理员维护');
  await help.getByRole('button', { name: '关闭', exact: true }).click();
  await expect(page.getByRole('button', { name: '新建角色', exact: true })).toBeVisible();
});

// Catch losing access to the business rules when removing permanent banners,
// or showing stale rules after navigating to another module.
test('页面帮助按当前模块展示规则，关闭后可继续办理', async ({ page }) => {
  await page.goto('/');
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('button', { name: '角色权限', exact: true })
    .click();
  const helpButton = page.getByRole('button', { name: '页面帮助', exact: true });
  await expect(helpButton).toBeVisible();
  await helpButton.click();
  const help = page.getByRole('dialog', { name: '页面帮助', exact: true });
  await expect(
    help.getByText(
      '公司级共享角色仅由总管理员维护。权限管理员可查看，并为范围内合格员工申请分配。',
      { exact: true },
    ),
  ).toBeVisible();
  await help.getByRole('button', { name: '关闭', exact: true }).click();
  await page.getByRole('button', { name: '新建角色', exact: true }).click();
  await expect(page.getByRole('dialog', { name: '新建角色草稿', exact: true })).toBeVisible();
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: '切换为总管理员（演示）', exact: true }).click();
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('button', { name: '员工组织', exact: true })
    .click();
  await helpButton.click();
  await expect(
    help.getByText('新增任职不自动授予角色；结束任职使对应来源授权失效，其他独立来源保留。', {
      exact: true,
    }),
  ).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(helpButton).toBeFocused();
});

// Catch hiding the separate OA delivery, approval and execution meanings along
// with the explanatory flow strip, leaving users unable to interpret statuses.
test('申请页面帮助可解释三个状态，页面继续分别展示投递审批执行', async ({ page }) => {
  await page.goto('/');
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('button', { name: '变更申请', exact: true })
    .click();
  const helpButton = page.getByRole('button', { name: '页面帮助', exact: true });
  await expect(helpButton).toBeVisible();
  await helpButton.click();
  const help = page.getByRole('dialog', { name: '页面帮助', exact: true });
  await expect(
    help.getByText('OA 投递、审批结果、执行结果分别记录；只有执行成功才改变有效授权。', {
      exact: true,
    }),
  ).toBeVisible();
  await help.getByRole('button', { name: '关闭', exact: true }).click();
  for (const name of ['OA 投递', '审批结果', '执行结果']) {
    await expect(page.getByRole('columnheader', { name, exact: true })).toBeVisible();
  }
  await page
    .getByRole('row')
    .filter({ hasText: 'CHG-20261015-008' })
    .getByRole('button', { name: '查看', exact: true })
    .click();
  await expect(page.getByRole('dialog', { name: '申请详情', exact: true })).toContainText(
    '提交与批准均不代表已生效',
  );
});
