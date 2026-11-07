const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1080,height:1920},deviceScaleFactor:1});
await p.goto('file://'+process.argv[2]);await p.waitForTimeout(800);
for(let i=1;i<=5;i++){await p.locator('#s'+i).screenshot({path:process.argv[3]+'/slide'+i+'.png'});}
await b.close();})();
