const { test, expect } = require('@playwright/test');

test.beforeEach(async ({ page }) => {
  await page.goto('/');
  await expect(page.getByText('Infinix Note 30 Pro', { exact: true })).toBeVisible();
});

test('opens Settings from the app grid and returns with Back', async ({ page }) => {
  await page.getByRole('button', { name: /Настройки/ }).first().click();
  await expect(page.locator('#appTitle')).toHaveText('Настройки');
  await page.getByRole('button', { name: 'Назад', exact: true }).click();
  await expect(page.locator('#home')).toBeVisible();
  await expect(page.locator('#appView')).toBeHidden();
});

test('agent command opens Settings', async ({ page }) => {
  await page.locator('#command').fill('открыть настройки');
  await page.getByRole('button', { name: 'Выполнить' }).click();
  await expect(page.locator('#appTitle')).toHaveText('Настройки');
  await expect(page.getByRole('log')).toContainText('Открыто: Настройки');
});

test('Home returns from an app to the desktop', async ({ page }) => {
  await page.getByRole('button', { name: /Камера/ }).click();
  await expect(page.locator('#appTitle')).toHaveText('Камера');
  await page.getByRole('button', { name: 'Домой', exact: true }).click();
  await expect(page.locator('#home')).toBeVisible();
});

test('scroll command records an action', async ({ page }) => {
  await page.locator('#command').fill('прокрутка вниз');
  await page.getByRole('button', { name: 'Выполнить' }).click();
  await expect(page.getByRole('log')).toContainText('Прокрутка выполнена');
});

test('basic regression button logs its result', async ({ page }) => {
  await page.getByRole('button', { name: 'Базовый тест' }).click();
  await expect(page.getByRole('log')).toContainText('Проверка открытия и возврата: PASS');
  await expect(page.locator('body')).not.toHaveAttribute('data-test-failure', 'back-navigation');
});
