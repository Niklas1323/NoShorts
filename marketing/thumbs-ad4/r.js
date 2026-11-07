const {chromium}=require('playwright');(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1300,height:2700}});
await p.goto('file://'+process.argv[2]);await p.waitForTimeout(800);
await p.locator('#yt').screenshot({path:'thumbnail-youtube-1280x720.png'});await p.locator('#tt').screenshot({path:'cover-tiktok-instagram-1080x1920.png'});await b.close();})();
