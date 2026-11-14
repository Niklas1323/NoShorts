#!/usr/bin/env python3
"""Nach render.js: unscharfe Bildschirme (*_blur.png), Schatten der Ebenen (*_sh.png), Staub-Punkte des Shorts-Symbols
(src/ad13/particles13.json) und Papier-Körnung (grain.png) erzeugen."""
import os, json
import numpy as np
from PIL import Image, ImageFilter
H = os.path.dirname(os.path.abspath(__file__))
P = os.path.join(H, '..', 'public', 'ad13')
for n in ['yt_real', 'yt_fs', 'lock', 'lock_mon', 'search', 'app_home']:
    im = Image.open(f'{P}/{n}.png').convert('RGB').resize((540, 1212), Image.LANCZOS).filter(ImageFilter.GaussianBlur(9))
    im.save(f'{P}/{n}_blur.png')
for n in ['bar_real', 'bar_real_full', 'bar_fs', 'bar_fs_empty', 'notif0', 'notif1', 'notif2', 'card0', 'card1', 'card2',
          'shield', 'closed', 'header', 'vcard0', 'vcard1', 'vcard2']:
    im = Image.open(f'{P}/{n}.png').convert('RGBA'); pad = 70; a = im.split()[3]
    big = Image.new('L', (im.width + 2 * pad, im.height + 2 * pad), 0); big.paste(a, (pad, pad)); big = big.filter(ImageFilter.GaussianBlur(26))
    sh = Image.new('RGBA', big.size, (20, 12, 6, 0)); sh.putalpha(big.point(lambda v: int(v * 0.75))); sh.save(f'{P}/{n}_sh.png')
im = np.asarray(Image.open(f'{P}/bar_real_full.png').convert('RGBA')).astype(float)
pts = [[x, y] for y in range(35, 115, 2) for x in range(365, 445, 2) if im[y, x, 3] > 200 and im[y, x, :3].mean() > 150]
json.dump(pts, open(os.path.join(H, '..', 'src', 'ad13', 'particles13.json'), 'w'))
rng = np.random.default_rng(3)
g = np.clip(128 + rng.normal(0, 1, (256, 256)) * 40, 0, 255).astype(np.uint8)
Image.fromarray(np.dstack([g, g, g, np.full((256, 256), 14, np.uint8)]), 'RGBA').save(f'{P}/grain.png')
print('ok', len(pts))
