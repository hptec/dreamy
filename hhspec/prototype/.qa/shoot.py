import asyncio
from playwright.async_api import async_playwright
import os

PAGES = [
    ("special-occasions", "http://localhost:5173/special-occasions"),
    ("special-occasions-guest", "http://localhost:5173/special-occasions?sub=guest"),
    ("search-lace", "http://localhost:5173/search?q=lace"),
    ("search-empty", "http://localhost:5173/search?q=xyznomatch"),
    ("search-noquery", "http://localhost:5173/search"),
    ("style-gallery", "http://localhost:5173/style-gallery"),
    ("notfound", "http://localhost:5173/a-nonexistent-url"),
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
            # dismiss any Vite error overlay (other agents may not have created their views yet)
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
            # again kill overlay if it re-appeared
            try:
                await page.evaluate(
                    """() => {
                        document.querySelectorAll('vite-error-overlay').forEach(el => el.remove());
                    }"""
                )
            except Exception:
                pass
            await page.wait_for_timeout(500)
            out = f"{OUT_DIR}/shop-{name}.png"
            await page.screenshot(path=out, full_page=True)
            print(f"OK {name} -> {out}")
            await page.close()
        await browser.close()

asyncio.run(main())
