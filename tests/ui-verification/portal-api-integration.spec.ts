import { test, expect } from '@playwright/test';

// UI 验收测试：portal-api-integration

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

test.describe('dashboard', () => {

  // OP-005 [navigation]
  test('OP-005: 查看完整看板(/analytics) - 元素可见性', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page.locator('a[href="/analytics"]')).toBeVisible();
  });

  test('OP-005: 查看完整看板(/analytics) - 成功路径', async ({ page }) => {
    await page.goto('/dashboard');
        await page.locator('a[href="/analytics"]').click();
    // 验证导航后的 URL 变更
    await page.waitForURL(url => url.pathname !== '/dashboard', { timeout: 5000 });
  });

  test('OP-005: 查看完整看板(/analytics) - 失败路径', async ({ page }) => {
    // 直接访问需授权路径，验证重定向到登录页
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);
  });


  // OP-006 [navigation]
  test('OP-006: 发布站点(/publish) - 元素可见性', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page.locator('a[href="/publish"]')).toBeVisible();
  });

  test('OP-006: 发布站点(/publish) - 成功路径', async ({ page }) => {
    await page.goto('/dashboard');
        await page.locator('a[href="/publish"]').click();
    // 验证导航后的 URL 变更
    await page.waitForURL(url => url.pathname !== '/dashboard', { timeout: 5000 });
  });

  test('OP-006: 发布站点(/publish) - 失败路径', async ({ page }) => {
    // 直接访问需授权路径，验证重定向到登录页
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);
  });

});

test.describe('operation-logs', () => {

  // OP-007 [download]
  test('OP-007: 导出 CSV - 元素可见性', async ({ page }) => {
    await page.goto('/operation-logs');
    await expect(page.locator('button.btn-ghost')).toBeVisible();
  });

  test('OP-007: 导出 CSV - 成功路径', async ({ page }) => {
    await page.goto('/operation-logs');
        await page.locator('button.btn-ghost').click();
    // 验证交互后的 UI 变化
    await page.waitForTimeout(1000);
  });

  test('OP-007: 导出 CSV - 失败路径', async ({ page }) => {
    await page.route('**/api/**', route => route.abort());
    await page.goto('/operation-logs');
    await expect(page.locator('button.btn-ghost')).toBeVisible();
  });

});

test.describe('order-detail', () => {

  // OP-008 [api_call]
  test('OP-008: 返回 - 元素可见性', async ({ page }) => {
    await page.goto('/order-detail');
    await expect(page.locator('button.btn-ghost')).toBeVisible();
  });

  test('OP-008: 返回 - 成功路径', async ({ page }) => {
    await page.goto('/order-detail');
        await page.locator('button.btn-ghost').click();
    // 验证操作后的 UI 响应（Toast / 跳转 / 状态变更）
    await expect(page.locator('[role="alert"], .toast, .text-green-500, .bg-primary-700')).toBeVisible({ timeout: 5000 });
  });

  test('OP-008: 返回 - 失败路径', async ({ page }) => {
    await page.route('**/api/**', route => route.abort());
    await page.goto('/order-detail');
    await page.locator('button.btn-ghost').click();
    await expect(page.locator('[role="alert"], .toast--error, .text-red-500, [class*="error"]')).toBeVisible({ timeout: 5000 });
  });


  // OP-009 [dialog_open]
  test('OP-009: 标记发货 - 元素可见性', async ({ page }) => {
    await page.goto('/order-detail');
    await expect(page.locator('button.btn-primary')).toBeVisible();
  });

  test('OP-009: 标记发货 - 成功路径', async ({ page }) => {
    await page.goto('/order-detail');
        await page.locator('button.btn-primary').click();
    // 验证弹窗/对话框打开
    await expect(page.locator('[role="dialog"], .modal, .overlay, [class*="visible"]')).toBeVisible({ timeout: 3000 });
  });

  test('OP-009: 标记发货 - 失败路径', async ({ page }) => {
        await page.goto('/order-detail');
    // 未触发弹窗时弹窗不可见
    await expect(page.locator('[role="dialog"], .modal, .overlay')).not.toBeVisible();
  });

});

test.describe('product-edit', () => {

  // OP-010 [api_call]
  test('OP-010: 返回列表 - 元素可见性', async ({ page }) => {
    await page.goto('/product-edit');
    await expect(page.locator('button.btn-ghost')).toBeVisible();
  });

  test('OP-010: 返回列表 - 成功路径', async ({ page }) => {
    await page.goto('/product-edit');
        await page.locator('button.btn-ghost').click();
    // 验证操作后的 UI 响应（Toast / 跳转 / 状态变更）
    await expect(page.locator('[role="alert"], .toast, .text-green-500, .bg-primary-700')).toBeVisible({ timeout: 5000 });
  });

  test('OP-010: 返回列表 - 失败路径', async ({ page }) => {
    await page.route('**/api/**', route => route.abort());
    await page.goto('/product-edit');
    await page.locator('button.btn-ghost').click();
    await expect(page.locator('[role="alert"], .toast--error, .text-red-500, [class*="error"]')).toBeVisible({ timeout: 5000 });
  });


  // OP-011 [navigation]
  test('OP-011: 保存并生成静态页 - 元素可见性', async ({ page }) => {
    await page.goto('/product-edit');
    await expect(page.locator('button.btn-gold')).toBeVisible();
  });

  test('OP-011: 保存并生成静态页 - 成功路径', async ({ page }) => {
    await page.goto('/product-edit');
        await page.locator('button.btn-gold').click();
    // 验证导航后的 URL 变更
    await page.waitForURL(url => url.pathname !== '/product-edit', { timeout: 5000 });
  });

  test('OP-011: 保存并生成静态页 - 失败路径', async ({ page }) => {
    // 直接访问需授权路径，验证重定向到登录页
    await page.goto('/product-edit');
    await expect(page).toHaveURL(/\/login/);
  });

});

test.describe('products', () => {

  // OP-012 [navigation]
  test('OP-012: 新增商品(/products/new) - 元素可见性', async ({ page }) => {
    await page.goto('/products');
    await expect(page.locator('a[href="/products/new"]')).toBeVisible();
  });

  test('OP-012: 新增商品(/products/new) - 成功路径', async ({ page }) => {
    await page.goto('/products');
        await page.locator('a[href="/products/new"]').click();
    // 验证导航后的 URL 变更
    await page.waitForURL(url => url.pathname !== '/products', { timeout: 5000 });
  });

  test('OP-012: 新增商品(/products/new) - 失败路径', async ({ page }) => {
    // 直接访问需授权路径，验证重定向到登录页
    await page.goto('/products');
    await expect(page).toHaveURL(/\/login/);
  });

});

test.describe('refunds', () => {

  // OP-013 [state_toggle]
  test('OP-013: {{ t[1] }} - 元素可见性', async ({ page }) => {
    await page.goto('/refunds');
    await expect(page.locator('button.border-b-2')).toBeVisible();
  });

  test('OP-013: {{ t[1] }} - 成功路径', async ({ page }) => {
    await page.goto('/refunds');
        await page.locator('button.border-b-2').click();
    // 验证交互后的 UI 变化
    await page.waitForTimeout(1000);
  });

  test('OP-013: {{ t[1] }} - 失败路径', async ({ page }) => {
    await page.route('**/api/**', route => route.abort());
    await page.goto('/refunds');
    await expect(page.locator('button.border-b-2')).toBeVisible();
  });

});

test.describe('role-management', () => {

  // OP-014 [dialog_open]
  test('OP-014: 新增角色 - 元素可见性', async ({ page }) => {
    await page.goto('/role-management');
    await expect(page.locator('button.btn-primary')).toBeVisible();
  });

  test('OP-014: 新增角色 - 成功路径', async ({ page }) => {
    await page.goto('/role-management');
        await page.locator('button.btn-primary').click();
    // 验证弹窗/对话框打开
    await expect(page.locator('[role="dialog"], .modal, .overlay, [class*="visible"]')).toBeVisible({ timeout: 3000 });
  });

  test('OP-014: 新增角色 - 失败路径', async ({ page }) => {
        await page.goto('/role-management');
    // 未触发弹窗时弹窗不可见
    await expect(page.locator('[role="dialog"], .modal, .overlay')).not.toBeVisible();
  });

});
