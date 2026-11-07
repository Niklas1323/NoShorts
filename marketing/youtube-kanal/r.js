const {chromium}=require('playwright');(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:2600,height:3600}});
await p.goto('file://'+process.argv[2]);await p.waitForTimeout(800);
await p.locator('#logo').screenshot({path:'logo-2048.png'});await p.locator('#banner').screenshot({path:'banner-2560x1440.png'});await b.close();})();
