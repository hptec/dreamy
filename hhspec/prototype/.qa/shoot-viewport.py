import asyncio
from playwright.async_api import async_playwright
import os

# Test specific viewports of each page
PAGES = [
    ("about-top", "http://localhost:5173/about", 0),
    ("atelier-mid", "http://localhost:5173/atelier", 2200),
    ("size-guide-chart", "http://localhost:5173/size-guide", 900),
    ("shipping-side", "http://localhost:5173/shipping-returns", 600),
    ("faq-top", "http://localhost:5173/faq", 600),
    ("contact-form", "http://localhost:5173/contact", 500),
    ("lookbook-cover", "http://localhost:5173/lookbook/eden-2026", 0),
]

OUT_DIR = "/Volumes/MAC/workspace/dreamy/hhspec/prototype/.qa"

async def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    async with async_playwright() as pw:
        browser = await pw.chromium.launch()
        context = await browser.new_context(viewport={"width": 1440, "height": 900}, device_scale_factor=1)
        for name, url, scroll in PAGES:
            page = await context.new_page()
            try:
                await page.goto(url, wait_until="networkidle", timeout=30000)
            except Exception as e:
                print(f"{name}: nav warning {e}")
            await page.wait_for_timeout(1100)
            try:
                await page.evaluate("() => document.querySelectorAll('vite-error-overlay').forEach(el => el.remove())")
            except Exception:
                pass
            try:
                btn = page.locator('button:has-text("Skip for now")').first
                if await btn.is_visible(timeout=400):
                    await btn.click()
                    await page.wait_for_timeout(300)
            except Exception:
                pass
            try:
                btn = page.locator('button:has-text("Accept")').first
                if await btn.is_visible(timeout=400):
                    await btn.click()
                    await page.wait_for_timeout(200)
            except Exception:
                pass
            try:
                btn = page.locator('button[aria-label="Close"]').first
                if await btn.is_visible(timeout=400):
                    await btn.click()
                    await page.wait_for_timeout(200)
            except Exception:
                pass
            if scroll > 0:
                await page.evaluate(f"window.scrollTo(0, {scroll})")
                await page.wait_for_timeout(700)
            try:
                await page.evaluate("() => document.querySelectorAll('vite-error-overlay').forEach(el => el.remove())")
            except Exception:
                pass
            out = f"{OUT_DIR}/viewport-{name}.png"
            await page.screenshot(path=out)  # viewport only, not full page
            print(f"OK {name} -> {out}")
            await page.close()
        await browser.close()

asyncio.run(main())
