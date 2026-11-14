// Rendert jeden Bildschirm (.scr) und jede Ebene (.lay) aus screens.html als PNG nach public/ad13/
// Ablauf: python3 gen.py && node render.js && python3 post.py
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1080, height: 2424 }, deviceScaleFactor: 1 });
  await p.goto('file://' + path.join(__dirname, 'screens.html'));
  await p.evaluate(() => document.fonts.ready);
  await p.waitForTimeout(400);
  const ids = await p.$$eval('.scr, .lay', els => els.map(e => e.id));
  for (const id of ids) {
    const el = await p.$('#' + id);
    const isLayer = id.startsWith('L_');
    await el.screenshot({ path: path.join(__dirname, '..', 'public', 'ad13', id.replace(/^L_/, '') + '.png'), omitBackground: isLayer });
    console.log(id);
  }
  await b.close();
})();
