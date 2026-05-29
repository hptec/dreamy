import asyncio
from playwright.async_api import async_playwright
import os

PAGES = [
    ("about", "http://localhost:5173/about"),
    ("atelier", "http://localhost:5173/atelier"),
    ("size-guide", "http://localhost:5173/size-guide"),
    ("shipping-returns", "http://localhost:5173/shipping-returns"),
    ("faq", "http://localhost:5173/faq"),
    ("contact", "http://localhost:5173/contact"),
    ("lookbook-eden-2026", "http://localhost:5173/lookbook/eden-2026"),
]

OUT_DIR = "/Volumes/MAC/workspace/dreamy/hhspec/prototype/.qa"

async def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    async with async_playwright() as pw:
        browser = await pw.chromium.launch()
        context = await browser.new_context(viewport={"width": 1440, "height": 900}, device_scale_factor=1)
        for name, url in PAGES:
            page = await context.new_page()
            try:
                await page.goto(url, wait_until="networkidle", timeout=30000)
            except Exception as e:
                print(f"{name}: nav warning {e}")
            await page.wait_for_timeout(1100)
            # dismiss any Vite error overlay
            try:
                await page.evaluate(
                    """() => {
                        document.querySelectorAll('vite-error-overlay').forEach(el => el.remove());
                    }"""
                )
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
            # close newsletter modal if visible
            try:
                btn = page.locator('button[aria-label="Close"]').first
                if await btn.is_visible(timeout=400):
                    await btn.click()
                    await page.wait_for_timeout(200)
            except Exception:
                pass
            # again kill overlay if it re-appeared
            try:
                await page.evaluate(
                    """() => {
                        document.querySelectorAll('vite-error-overlay').forEach(el => el.remove());
                    }"""
                )
            except Exception:
                pass
            await page.wait_for_timeout(700)
            out = f"{OUT_DIR}/content-{name}.png"
            await page.screenshot(path=out, full_page=True)
            print(f"OK {name} -> {out}")
            await page.close()
        await browser.close()

asyncio.run(main())
