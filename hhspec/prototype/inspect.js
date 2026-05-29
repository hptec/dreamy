const {chromium}=require('playwright');
const path=require('path');
(async()=>{
  const b=await chromium.launch({headless:true});
  const p=await b.newPage({viewport:{width:1440,height:900}});
  await p.goto('file://'+path.resolve('out/index.html'),{waitUntil:'domcontentloaded'});
  await p.waitForTimeout(2500);
  const info=await p.evaluate(()=>{
    const links=Array.from(document.querySelectorAll('a')).slice(0,25).map(a=>({
      text:a.textContent.trim().slice(0,30),
      href:a.getAttribute('href'),
      visible:a.offsetWidth>0
    }));
    const imgs=Array.from(document.querySelectorAll('img')).slice(0,10).map(i=>({
      src:i.getAttribute('src'),
      naturalW:i.naturalWidth,
      ok:i.complete&&i.naturalWidth>0
    }));
    return {links,imgs};
  });
  console.log('LINKS:',JSON.stringify(info.links,null,2));
  console.log('IMGS:',JSON.stringify(info.imgs,null,2));
  // 截图
  await p.screenshot({path:'out-inspect.png',fullPage:false});
  await b.close();
})().catch(e=>{console.error(e);process.exit(1)});
