import asyncio
from playwright.async_api import async_playwright
import os

PAGES = [
    ("auth", "http://localhost:5173/account/auth"),
    ("dashboard", "http://localhost:5173/account"),
    ("profile", "http://localhost:5173/account/profile"),
    ("orders", "http://localhost:5173/account/orders"),
    ("order-detail", "http://localhost:5173/account/orders/ME-2026-04812"),
    ("wishlist", "http://localhost:5173/account/wishlist"),
    ("addresses", "http://localhost:5173/account/addresses"),
    ("settings", "http://localhost:5173/account/settings"),
]

OUT_DIR = "/Volumes/MAC/workspace/dreamy/hhspec/prototype/.qa"

async def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    async with async_playwright() as pw:
        browser = await pw.chromium.launch()
        context = await browser.new_context(viewport={"width": 1440, "height": 900}, device_scale_factor=1)
        # Pre-seed localStorage so the region modal and cookie banner do not block view
        page0 = await context.new_page()
        await page0.goto("http://localhost:5173/", wait_until="domcontentloaded")
        await page0.evaluate("""() => {
            localStorage.setItem('me_region', '1');
            localStorage.setItem('me_cookie', 'accepted');
            localStorage.setItem('me_newsletter', '1');
            // seed a few wishlist ids so we can see populated wishlist
            localStorage.setItem('me_wishlist', JSON.stringify(['p-wedding-1', 'p-wedding-5', 'p-wedding-9', 'p-evening-12']));
        }""")
        await page0.close()

        for name, url in PAGES:
            page = await context.new_page()
            errs = []
            page.on('pageerror', lambda e: errs.append(str(e)))
            page.on('console', lambda msg: errs.append(msg.text) if msg.type == 'error' else None)
            try:
                await page.goto(url, wait_until="networkidle", timeout=30000)
            except Exception as e:
                print(f"{name}: nav warning {e}")
            await page.wait_for_timeout(1100)
            # dismiss Vite error overlay if any
            try:
                await page.evaluate("""() => {
                    document.querySelectorAll('vite-error-overlay').forEach(el => el.remove());
                }""")
            except Exception:
                pass
            # close region modal if visible
            try:
                btn = page.locator('button:has-text("Skip for now")').first
                if await btn.is_visible(timeout=400):
                    await btn.click()
                    await page.wait_for_timeout(300)
            except Exception:
                pass
            # close cookie banner
            try:
                btn = page.locator('button:has-text("Accept")').first
                if await btn.is_visible(timeout=400):
                    await btn.click()
                    await page.wait_for_timeout(200)
            except Exception:
                pass
            try:
                await page.evaluate("""() => {
                    document.querySelectorAll('vite-error-overlay').forEach(el => el.remove());
                }""")
            except Exception:
                pass
            await page.wait_for_timeout(500)
            out = f"{OUT_DIR}/acct-{name}.png"
            await page.screenshot(path=out, full_page=True)
            if errs:
                print(f"ERR {name}: {errs[:3]}")
            print(f"OK {name} -> {out}")
            await page.close()
        await browser.close()

asyncio.run(main())
