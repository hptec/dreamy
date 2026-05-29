const {chromium}=require('playwright');
const path=require('path');
const fs=require('fs');
const QA=path.resolve('.qa/visual');
fs.mkdirSync(QA,{recursive:true});
(async()=>{
  const b=await chromium.launch({headless:false,slowMo:300});
  const p=await b.newPage({viewport:{width:1440,height:900}});
  const imgFails=[];
  p.on('response',r=>{ if(r.url().includes('competitor-refs')&&r.status()!==200) imgFails.push(r.url().split('/out/')[1]+' '+r.status()); });

  await p.goto('file://'+path.resolve('out/index.html'),{waitUntil:'domcontentloaded'});
  await p.waitForTimeout(2000);

  // 关闭可能出现的 newsletter 弹窗和 cookie 条
  async function dismiss(){
    try{ await p.keyboard.press('Escape'); }catch{}
    const closeBtns = await p.locator('[aria-label="Close"]').all();
    for(const btn of closeBtns){ try{ await btn.click({timeout:800}); }catch{} }
    try{ await p.locator('button', {hasText:/^Accept$/}).first().click({timeout:800}); }catch{}
    // 强制移除遮罩层，防止拦截 hover
    await p.evaluate(()=>{
      document.querySelectorAll('.fixed.inset-0').forEach(el=>{
        const cs=getComputedStyle(el);
        if(cs.position==='fixed' && (el.className.includes('z-[60]')||el.querySelector('.backdrop-blur-sm'))) el.remove();
      });
    });
    await p.waitForTimeout(200);
  }
  await dismiss();

  const menus = [
    ['WEDDING DRESSES','nav-wedding'],
    ['SPECIAL OCCASION','nav-special'],
    ['ACCESSORIES','nav-accessories'],
    ['OUTDOOR WEDDINGS','nav-outdoor']
  ];
  for(const [label,shot] of menus){
    console.log('hover',label);
    await dismiss();
    await p.locator('a',{hasText:new RegExp('^'+label+'$','i')}).first().hover();
    await p.waitForTimeout(900);
    await p.screenshot({path:QA+'/'+shot+'.png',fullPage:false});
    console.log('  截图',shot+'.png');
  }

  // 收起菜单后检查所有图片
  await p.mouse.move(700,500);
  await p.waitForTimeout(500);
  const imgStats=await p.evaluate(()=>{
    const imgs=Array.from(document.querySelectorAll('img'));
    const fail=imgs.filter(i=>!i.complete||i.naturalWidth===0);
    return {total:imgs.length,failed:fail.length,failedSrcs:fail.slice(0,8).map(i=>i.getAttribute('src'))};
  });
  console.log('\n首页图片:',JSON.stringify(imgStats));

  await b.close();
  console.log('competitor-refs fetch 失败:', imgFails.length?imgFails:'无');
})().catch(e=>{console.error(e);process.exit(1)});
