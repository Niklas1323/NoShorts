const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1080,height:1920},deviceScaleFactor:1});
await p.goto('file://'+process.argv[2]);await p.waitForTimeout(800);
const ids=[1,3,4,5];for(let k=0;k<ids.length;k++){await p.locator('#s'+ids[k]).screenshot({path:process.argv[3]+'/final'+(k+1)+'.png'});}
await b.close();})();
