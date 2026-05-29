const {chromium}=require('playwright');
const path=require('path');
(async()=>{
  const b=await chromium.launch({headless:true});
  const p=await b.newPage({viewport:{width:1440,height:900}});
  const errors=[];
  p.on('pageerror',e=>errors.push(e.message));
  p.on('console',m=>{ if(m.type()==='error') errors.push('CONSOLE: '+m.text()); });
  await p.goto('file://'+path.resolve('out/index.html'),{waitUntil:'domcontentloaded'});
  await p.waitForTimeout(3000);
  console.log('ERRORS:',errors.slice(0,5));
  await b.close();
})().catch(e=>{console.error(e);process.exit(1)});
