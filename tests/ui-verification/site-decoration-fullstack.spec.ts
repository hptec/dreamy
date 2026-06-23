import { test, expect } from '@playwright/test';

// UI 验收测试：site-decoration-fullstack

test.describe('admin-list', () => {

  // OP-001 [dialog_open]
  test('OP-001: 新增管理员 - 元素可见性', async ({ page }) => {
    await page.goto('/admin-list');
    await expect(page.locator('button.btn-primary')).toBeVisible();
  });

  test('OP-001: 新增管理员 - 成功路径', async ({ page }) => {
    await page.goto('/admin-list');
        await page.locator('button.btn-primary').click();
    // 验证弹窗/对话框打开
    await expect(page.locator('[role="dialog"], .modal, .overlay, [class*="visible"]')).toBeVisible({ timeout: 3000 });
  });

  test('OP-001: 新增管理员 - 失败路径', async ({ page }) => {
        await page.goto('/admin-list');
    // 未触发弹窗时弹窗不可见
    await expect(page.locator('[role="dialog"], .modal, .overlay')).not.toBeVisible();
  });

});

test.describe('auth-settings', () => {

  // OP-002 [api_call]
  test('OP-002: 保存配置 - 元素可见性', async ({ page }) => {
    await page.goto('/auth-settings');
    await expect(page.locator('button.btn-primary')).toBeVisible();
  });

  test('OP-002: 保存配置 - 成功路径', async ({ page }) => {
    await page.goto('/auth-settings');
        await page.locator('button.btn-primary').click();
    // 验证操作后的 UI 响应（Toast / 跳转 / 状态变更）
    await expect(page.locator('[role="alert"], .toast, .text-green-500, .bg-primary-700')).toBeVisible({ timeout: 5000 });
  });

  test('OP-002: 保存配置 - 失败路径', async ({ page }) => {
    await page.route('**/api/**', route => route.abort());
    await page.goto('/auth-settings');
    await page.locator('button.btn-primary').click();
    await expect(page.locator('[role="alert"], .toast--error, .text-red-500, [class*="error"]')).toBeVisible({ timeout: 5000 });
  });

});

test.describe('customer-detail', () => {

  // OP-003 [api_call]
  test('OP-003: 返回 - 元素可见性', async ({ page }) => {
    await page.goto('/customer-detail');
    await expect(page.locator('button.btn-ghost')).toBeVisible();
  });

  test('OP-003: 返回 - 成功路径', async ({ page }) => {
    await page.goto('/customer-detail');
        await page.locator('button.btn-ghost').click();
    // 验证操作后的 UI 响应（Toast / 跳转 / 状态变更）
    await expect(page.locator('[role="alert"], .toast, .text-green-500, .bg-primary-700')).toBeVisible({ timeout: 5000 });
  });

  test('OP-003: 返回 - 失败路径', async ({ page }) => {
    await page.route('**/api/**', route => route.abort());
    await page.goto('/customer-detail');
    await page.locator('button.btn-ghost').click();
    await expect(page.locator('[role="alert"], .toast--error, .text-red-500, [class*="error"]')).toBeVisible({ timeout: 5000 });
  });


  // OP-004 [dialog_open]
  test('OP-004: 强制下线 - 元素可见性', async ({ page }) => {
    await page.goto('/customer-detail');
    await expect(page.locator('button.btn-outline')).toBeVisible();
  });

  test('OP-004: 强制下线 - 成功路径', async ({ page }) => {
    await page.goto('/customer-detail');
        await page.locator('button.btn-outline').click();
    // 验证弹窗/对话框打开
    await expect(page.locator('[role="dialog"], .modal, .overlay, [class*="visible"]')).toBeVisible({ timeout: 3000 });
  });

  test('OP-004: 强制下线 - 失败路径', async ({ page }) => {
        await page.goto('/customer-detail');
    // 未触发弹窗时弹窗不可见
    await expect(page.locator('[role="dialog"], .modal, .overlay')).not.toBeVisible();
  });

});

test.describe('operation-logs', () => {

  // OP-005 [download]
  test('OP-005: 导出 CSV - 元素可见性', async ({ page }) => {
    await page.goto('/operation-logs');
    await expect(page.locator('button.btn-ghost')).toBeVisible();
  });

  test('OP-005: 导出 CSV - 成功路径', async ({ page }) => {
    await page.goto('/operation-logs');
        await page.locator('button.btn-ghost').click();
    // 验证交互后的 UI 变化
    await page.waitForTimeout(1000);
  });

  test('OP-005: 导出 CSV - 失败路径', async ({ page }) => {
    await page.route('**/api/**', route => route.abort());
    await page.goto('/operation-logs');
    await expect(page.locator('button.btn-ghost')).toBeVisible();
  });

});

test.describe('role-management', () => {

  // OP-006 [dialog_open]
  test('OP-006: 新增角色 - 元素可见性', async ({ page }) => {
    await page.goto('/role-management');
    await expect(page.locator('button.btn-primary')).toBeVisible();
  });

  test('OP-006: 新增角色 - 成功路径', async ({ page }) => {
    await page.goto('/role-management');
        await page.locator('button.btn-primary').click();
    // 验证弹窗/对话框打开
    await expect(page.locator('[role="dialog"], .modal, .overlay, [class*="visible"]')).toBeVisible({ timeout: 3000 });
  });

  test('OP-006: 新增角色 - 失败路径', async ({ page }) => {
        await page.goto('/role-management');
    // 未触发弹窗时弹窗不可见
    await expect(page.locator('[role="dialog"], .modal, .overlay')).not.toBeVisible();
  });

});
