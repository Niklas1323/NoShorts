#!/usr/bin/env python3
"""Handy-Bildschirme für Video 13 (3D-Handy). Nachgebaut nach Niklas' Bildschirmfotos (Pixel 9, 1080x2424,
YouTube dunkel), aber mit eigenen, neutralen Vorschaubildern (keine fremde Werbung, keine fremden Videos).
Schreibt screens.html; render.js macht daraus PNGs in public/ad13/."""
import os

H = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(H, '..', 'node_modules', '@fontsource')
RED = '#E5172B'

# ------------------------------------------------------------------ Symbole (SVG)
def ico_home(c='#fff', s=64):
    return f'<svg width="{s}" height="{s}" viewBox="0 0 24 24"><path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z" fill="{c}"/></svg>'

def ico_subs(c='#fff', s=64):
    return (f'<svg width="{s}" height="{s}" viewBox="0 0 24 24" fill="none" stroke="{c}" stroke-width="1.7" stroke-linejoin="round">'
            f'<path d="M6 3.5h12M4.5 6.5h15"/><rect x="3" y="9.5" width="18" height="11.5" rx="2"/>'
            f'<path d="M10.2 12.6v5.3l4.4-2.65z" fill="{c}" stroke="none"/></svg>')

def ico_plus_circle(c='#fff', s=64):
    return (f'<svg width="{s}" height="{s}" viewBox="0 0 24 24" fill="none" stroke="{c}" stroke-width="1.7" stroke-linecap="round">'
            f'<circle cx="12" cy="12" r="9.5"/><path d="M12 7.5v9M7.5 12h9"/></svg>')

def ico_plus(c='#fff', s=64):
    return f'<svg width="{s}" height="{s}" viewBox="0 0 24 24" fill="none" stroke="{c}" stroke-width="1.8" stroke-linecap="round"><path d="M12 4.5v15M4.5 12h15"/></svg>'

def ico_shorts(c='#fff', s=64):
    # vereinfachter Shorts-Umriss: zwei versetzte Kapseln mit Play-Dreieck
    return (f'<svg width="{s}" height="{s}" viewBox="0 0 24 24" fill="none" stroke="{c}" stroke-width="1.7" stroke-linejoin="round">'
            f'<path d="M14.6 2.9c1.9-1 4.2-.3 5.2 1.6 1 1.9.3 4.2-1.6 5.2l-1.3.7 1.1.6c1.7 1 2.4 3 1.6 4.8-.4.9-1.1 1.6-2 2l-7.5 3.9c-1.9 1-4.2.3-5.2-1.6-1-1.9-.3-4.2 1.6-5.2l1.3-.7-1.1-.6C5 12.6 4.3 10.6 5.1 8.8c.4-.9 1.1-1.6 2-2z"/>'
            f'<path d="M10.2 9.2v5.6l4.6-2.8z" fill="{c}" stroke="none"/></svg>')

def ico_search(c='#fff', s=64):
    return f'<svg width="{s}" height="{s}" viewBox="0 0 24 24" fill="none" stroke="{c}" stroke-width="1.9" stroke-linecap="round"><circle cx="10.5" cy="10.5" r="6.8"/><path d="M15.6 15.6l5 5"/></svg>'

def ico_bell(c='#fff', s=64):
    return f'<svg width="{s}" height="{s}" viewBox="0 0 24 24" fill="none" stroke="{c}" stroke-width="1.7" stroke-linejoin="round"><path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15z"/><path d="M10 20.5a2.2 2.2 0 0 0 4 0"/></svg>'

def ico_chat(c='#fff', s=64):
    return (f'<svg width="{s}" height="{s}" viewBox="0 0 24 24" fill="none" stroke="{c}" stroke-width="1.7" stroke-linejoin="round">'
            f'<path d="M3.5 4.5h12v8.5h-6.5l-3.5 3v-3h-2z"/><path d="M15.5 8.5h5v8.5h-2v3l-3.5-3h-5.5v-4"/></svg>')

def ico_back(c='#fff', s=64):
    return f'<svg width="{s}" height="{s}" viewBox="0 0 24 24" fill="none" stroke="{c}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 12H5M11 5l-7 7 7 7"/></svg>'

def ico_mic(c='#fff', s=64):
    return f'<svg width="{s}" height="{s}" viewBox="0 0 24 24" fill="none" stroke="{c}" stroke-width="1.8" stroke-linecap="round"><rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21"/></svg>'

def logo_avatar(s=64):
    # F*ck-Shorts-Logo als rundes Profilbild (wie auf Niklas' Handy)
    return (f'<svg width="{s}" height="{s}" viewBox="0 0 100 100"><circle cx="50" cy="50" r="50" fill="{RED}"/>'
            f'<rect x="33" y="24" width="34" height="52" rx="8" fill="#fff"/>'
            f'<line x1="30" y1="78" x2="72" y2="24" stroke="{RED}" stroke-width="13" stroke-linecap="round"/>'
            f'<line x1="30" y1="78" x2="72" y2="24" stroke="#fff" stroke-width="5.5" stroke-linecap="round"/></svg>')

def yt_logo():
    return ('<div style="display:flex;align-items:center;gap:8px">'
            '<svg width="78" height="54" viewBox="0 0 78 54"><rect width="78" height="54" rx="14" fill="#FF0033"/><path d="M31 15v24l20-12z" fill="#fff"/></svg>'
            '<span style="font-family:Oswald, Roboto;font-weight:700;font-size:56px;letter-spacing:-2px;color:#fff;transform:scaleX(.82);transform-origin:left;display:inline-block">YouTube</span></div>')

# ------------------------------------------------------------------ Vorschaubilder (eigene Zeichnungen)
def th_python(w, h):
    lines = ''.join(f'<rect x="{60 + (i % 3) * 30}" y="{70 + i * 46}" width="{180 + (i * 53) % 260}" height="18" rx="9" fill="{["#7cd3ff", "#ffd166", "#a78bfa", "#6ee7b7"][i % 4]}" opacity=".85"/>' for i in range(10))
    return (f'<svg width="{w}" height="{h}" viewBox="0 0 1080 608" preserveAspectRatio="xMidYMid slice"><defs><linearGradient id="gp" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#0d1b3e"/><stop offset="1" stop-color="#23408f"/></linearGradient></defs>'
            f'<rect width="1080" height="608" fill="url(#gp)"/>{lines}'
            f'<text x="1020" y="300" text-anchor="end" font-family="Roboto" font-weight="700" font-size="150" fill="#FFD43B">PYTHON</text>'
            f'<rect x="640" y="340" width="380" height="84" rx="16" fill="#fff"/><text x="830" y="398" text-anchor="middle" font-family="Roboto" font-weight="700" font-size="50" fill="#0d1b3e">FULL COURSE</text></svg>')

def th_mountains(w, h):
    return (f'<svg width="{w}" height="{h}" viewBox="0 0 1080 608" preserveAspectRatio="xMidYMid slice"><defs><linearGradient id="gm" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ff9a62"/><stop offset=".6" stop-color="#e2557a"/><stop offset="1" stop-color="#5b2a86"/></linearGradient></defs>'
            f'<rect width="1080" height="608" fill="url(#gm)"/><circle cx="760" cy="250" r="110" fill="#ffe3a3"/>'
            f'<path d="M0 608 L230 260 L380 430 L560 170 L800 520 L1080 300 L1080 608Z" fill="#4a2a7a"/>'
            f'<path d="M560 170 L620 260 L590 250 L560 300 L530 250 L505 255Z" fill="#fff" opacity=".85"/>'
            f'<path d="M0 608 L300 420 L520 560 L760 410 L1080 560 L1080 608Z" fill="#2c1850"/>'
            f'<text x="60" y="160" font-family="Roboto" font-weight="700" font-size="96" fill="#fff">WHY THIS SHAPE?</text></svg>')

def th_rocket(w, h):
    return (f'<svg width="{w}" height="{h}" viewBox="0 0 1080 608" preserveAspectRatio="xMidYMid slice"><defs><linearGradient id="gr" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#071a2e"/><stop offset="1" stop-color="#1d6f86"/></linearGradient></defs>'
            f'<rect width="1080" height="608" fill="url(#gr)"/>'
            + ''.join(f'<circle cx="{(i * 137) % 1080}" cy="{(i * 71) % 330}" r="{2 + i % 3}" fill="#fff" opacity=".7"/>' for i in range(40)) +
            f'<rect x="250" y="540" width="580" height="30" rx="8" fill="#ddd"/>'
            f'<g transform="translate(540 300)"><path d="M-40 160 L-40 -110 Q0 -200 40 -110 L40 160Z" fill="#f4f4f4"/><path d="M-40 120 L-85 175 L-40 160Z M40 120 L85 175 L40 160Z" fill="#c9c9c9"/>'
            f'<path d="M-28 160 Q0 260 28 160Z" fill="#ffb347"/><circle cx="0" cy="-60" r="18" fill="#1d6f86"/></g>'
            f'<text x="60" y="140" font-family="Roboto" font-weight="700" font-size="88" fill="#fff">IT LANDS ITSELF</text></svg>')

def th_coaster(w, h):
    return (f'<svg width="{w}" height="{h}" viewBox="0 0 1080 608" preserveAspectRatio="xMidYMid slice"><defs><linearGradient id="gc" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#6fb7ff"/><stop offset="1" stop-color="#cfe9ff"/></linearGradient></defs>'
            f'<rect width="1080" height="608" fill="url(#gc)"/>'
            f'<path d="M-20 520 C200 520 260 120 470 120 C700 120 640 470 820 470 C950 470 980 260 1100 260" fill="none" stroke="#2b2f3a" stroke-width="22"/>'
            + ''.join(f'<line x1="{x}" y1="608" x2="{x}" y2="{y}" stroke="#e9eef5" stroke-width="10"/>' for x, y in [(120, 500), (300, 300), (470, 140), (640, 330), (820, 470), (980, 300)]) +
            f'<text x="60" y="110" font-family="Roboto" font-weight="700" font-size="84" fill="#1b2433">EVERY ELEMENT, RANKED</text></svg>')

def th_vertical(i, w, h, text):
    pal = [('#ff4f8b', '#ff9a3c'), ('#7a3cff', '#ff4fb0'), ('#ff7a00', '#ffd400'), ('#00b3ff', '#7a3cff'), ('#ff3b45', '#ffb02e'), ('#22c55e', '#06b6d4')][i % 6]
    face = ('<g transform="translate(270 430)"><circle r="150" fill="#ffb46b"/><path d="M-130 -60 L-150 -190 L-50 -120Z M130 -60 L150 -190 L50 -120Z" fill="#ffb46b"/>'
            '<circle cx="-55" cy="-10" r="22" fill="#2b1b10"/><circle cx="55" cy="-10" r="22" fill="#2b1b10"/><path d="M-30 55 Q0 80 30 55" stroke="#2b1b10" stroke-width="10" fill="none" stroke-linecap="round"/>'
            '<path d="M-12 30 L12 30 L0 44Z" fill="#e2557a"/></g>')
    return (f'<svg width="{w}" height="{h}" viewBox="0 0 540 960" preserveAspectRatio="xMidYMid slice"><defs><linearGradient id="gv{i}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{pal[0]}"/><stop offset="1" stop-color="{pal[1]}"/></linearGradient></defs>'
            f'<rect width="540" height="960" fill="url(#gv{i})"/>{face}'
            f'<text x="270" y="140" text-anchor="middle" font-family="Roboto" font-weight="700" font-size="54" fill="#fff" stroke="#000" stroke-width="3" paint-order="stroke">{text}</text></svg>')

# ------------------------------------------------------------------ Bausteine
def status_bar(bg='#060606'):
    return (f'<div style="position:absolute;left:0;top:0;width:1080px;height:170px;background:{bg};"></div>'
            '<div style="position:absolute;left:82px;top:62px;font:500 46px Roboto;color:#fff">9:41</div>'
            '<div style="position:absolute;right:82px;top:66px;display:flex;align-items:center;gap:18px;color:#fff;font:700 42px Roboto">'
            '<svg width="40" height="40" viewBox="0 0 24 24"><path d="M2 20h3v-4H2zm5 0h3v-8H7zm5 0h3V8h-3zm5 0h3V4h-3z" fill="#fff"/></svg>'
            '<span>5G</span><div style="width:64px;height:32px;border-radius:9px;background:#fff;position:relative"><div style="position:absolute;right:-7px;top:10px;width:5px;height:12px;border-radius:2px;background:#888"></div></div></div>')

def yt_header(tab='Home'):
    tabs = ''.join(f'<div style="position:absolute;left:{x}px;top:338px;font:500 41px Roboto;color:#fff">{t}</div>' for t, x in [('Home', 48), ('Subs', 228), ('Music', 389), ('Live', 578), ('More', 737)])
    under = '<div style="position:absolute;left:48px;top:398px;width:113px;height:5px;border-radius:3px;background:#FF0033"></div>'
    return (f'<div style="position:absolute;left:0;top:170px;width:1080px;height:255px;background:#0f0f0f"></div>'
            f'<div style="position:absolute;left:42px;top:208px">{yt_logo()}</div>'
            f'<div style="position:absolute;left:730px;top:202px">{ico_chat(s=72)}</div>'
            f'<div style="position:absolute;left:852px;top:202px">{ico_bell(s=72)}</div>'
            '<div style="position:absolute;left:892px;top:198px;width:54px;height:54px;border-radius:27px;background:#e8001f;color:#fff;font:500 30px Roboto;display:flex;align-items:center;justify-content:center">9+</div>'
            f'<div style="position:absolute;left:978px;top:204px">{ico_search(s=72)}</div>'
            + tabs + under)

def video_card(top, thumb, dur, title, chan, meta, av_col, av_txt):
    th = thumb(1080, 608)
    return (f'<div style="position:absolute;left:0;top:{top}px;width:1080px;height:608px;overflow:hidden">{th}'
            f'<div style="position:absolute;right:20px;bottom:20px;background:rgba(0,0,0,.8);color:#fff;font:500 33px Roboto;padding:4px 12px;border-radius:10px">{dur}</div></div>'
            f'<div style="position:absolute;left:30px;top:{top + 640}px;width:96px;height:96px;border-radius:48px;background:{av_col};color:#fff;font:700 40px Roboto;display:flex;align-items:center;justify-content:center">{av_txt}</div>'
            f'<div style="position:absolute;left:148px;top:{top + 632}px;width:820px;font:500 41px/1.28 Roboto;color:#f1f1f1">{title}</div>'
            f'<div style="position:absolute;left:148px;top:{top + 740}px;font:400 33px Roboto;color:#aaa">{chan} · {meta}</div>'
            f'<div style="position:absolute;left:990px;top:{top + 640}px;font:700 44px Roboto;color:#fff;letter-spacing:2px">⋮</div>')

def real_bar(glass=True, with_shorts=True, standalone=False):
    bg = 'rgba(38,42,48,.78)' if glass else '#1c1c1c'
    blur = 'backdrop-filter:blur(30px);' if glass else ''
    rad = 'border-radius:44px;' if standalone else ''
    hgt, cy = (150, 75) if standalone else (189, 85)
    shorts = f'<div style="position:absolute;left:{405 - 36}px;top:{cy - 36}px">{ico_shorts(s=72)}</div>' if with_shorts else ''
    gest = '' if standalone else '<div style="position:absolute;left:398px;top:158px;width:284px;height:10px;border-radius:5px;background:#fff"></div>'
    return (f'<div style="position:absolute;left:0;top:0;width:1080px;height:{hgt}px;background:{bg};{blur}{rad}">'
            f'<div style="position:absolute;left:{135 - 36}px;top:{cy - 36}px">{ico_home(s=72)}</div>{shorts}'
            f'<div style="position:absolute;left:{675 - 36}px;top:{cy - 36}px">{ico_plus_circle(s=72)}</div>'
            f'<div style="position:absolute;left:{945 - 32}px;top:{cy - 32}px">{logo_avatar(64)}</div>{gest}</div>')

def fs_bar(standalone=False, sel='you'):
    rad = 'border-radius:44px;' if standalone else ''
    gest = '' if standalone else '<div style="position:absolute;left:398px;top:158px;width:284px;height:10px;border-radius:5px;background:#fff"></div>'
    lab = lambda t, x: f'<div style="position:absolute;left:{x - 150}px;width:300px;top:86px;text-align:center;font:400 30px Roboto;color:#f1f1f1">{t}</div>'
    hgt = 150 if standalone else 189
    return (f'<div style="position:absolute;left:0;top:0;width:1080px;height:{hgt}px;background:#1c1c1c;{rad}">'
            f'<div style="position:absolute;left:{135 - 32}px;top:22px">{ico_home(s=64)}</div>{lab("Home", 135)}'
            f'<div style="position:absolute;left:{405 - 32}px;top:22px">{ico_subs(s=64)}</div>{lab("Subscriptions", 405)}'
            f'<div style="position:absolute;left:{675 - 28}px;top:24px">{logo_avatar(56)}</div>{lab("You", 675)}'
            f'<div style="position:absolute;left:{945 - 48}px;top:{56 - 48 + 12}px;width:96px;height:96px;border-radius:48px;background:#2a2a2a;display:flex;align-items:center;justify-content:center">{ico_plus(s=58)}</div>'
            f'{gest}</div>')

def shorts_shelf(top):
    cards = ''
    for i, (t, v) in enumerate([('wait for it 😭', '4.8M views'), ('POV: 3 hours later', '12M views')]):
        x = 30 + i * 520
        cards += (f'<div style="position:absolute;left:{x}px;top:{top + 110}px;width:500px;height:888px;border-radius:24px;overflow:hidden">{th_vertical(i, 500, 888, "")}'
                  f'<div style="position:absolute;left:24px;bottom:84px;width:440px;font:500 38px/1.2 Roboto;color:#fff;text-shadow:0 2px 6px #000">{t}</div>'
                  f'<div style="position:absolute;left:24px;bottom:34px;font:400 30px Roboto;color:#eee;text-shadow:0 2px 6px #000">{v}</div></div>')
    return (f'<div style="position:absolute;left:30px;top:{top + 26}px;display:flex;align-items:center;gap:16px">{ico_shorts("#FF0033", 60)}'
            f'<span style="font:700 44px Roboto;color:#fff">Shorts</span></div>' + cards)

def screen(id_, inner, bg='#0f0f0f'):
    return f'<div id="{id_}" class="scr" style="background:{bg}">{inner}</div>'

def layer(id_, w, h, inner):
    return f'<div id="{id_}" class="lay" style="width:{w}px;height:{h}px">{inner}</div>'

# ------------------------------------------------------------------ Bildschirme
feed_real = (video_card(425, th_rocket, '18:42', 'How Rockets Land Themselves (Explained)', 'Orbit Lab', '1.2M views · 2 days ago', '#1d6f86', 'O')
             + shorts_shelf(1265))
yt_real = screen('yt_real', status_bar() + feed_real + yt_header()
                 + f'<div style="position:absolute;left:0;top:2235px">{real_bar(glass=True)}</div>')

feed_fs = (video_card(425, th_python, '1:02:10', 'Learn Python in One Video – Full Course for Beginners', 'Code Simple', '3.4M views · 1 year ago', '#23408f', 'C')
           + video_card(1250, th_mountains, '24:05', 'Why Mountains Are Shaped Like This', 'Earth Explained', '890K views · 3 weeks ago', '#7a3cff', 'E'))
yt_fs = screen('yt_fs', status_bar() + feed_fs + yt_header()
               + f'<div style="position:absolute;left:0;top:2235px">{fs_bar()}</div>')

dialog = ('<div style="position:absolute;inset:0;background:rgba(0,0,0,.62)"></div>'
          '<div style="position:absolute;left:110px;top:880px;width:860px;padding:64px 64px 40px;border-radius:72px;background:#2b2930;box-sizing:border-box">'
          '<div style="display:flex;justify-content:center"><svg width="120" height="84" viewBox="0 0 78 54"><rect width="78" height="54" rx="14" fill="#FF0033"/><path d="M31 15v24l20-12z" fill="#fff"/></svg></div>'
          '<div style="margin-top:34px;text-align:center;font:400 58px Roboto;color:#e6e0e9">YouTube</div>'
          '<div style="margin-top:26px;text-align:center;font:400 40px/1.4 Roboto;color:#cac4d0">Do you want to uninstall this app?</div>'
          '<div style="margin-top:60px;display:flex;justify-content:flex-end;gap:70px;font:500 42px Roboto;color:#d0bcff;padding-right:16px"><span>Cancel</span><span>OK</span></div></div>')
uninstall = screen('uninstall', status_bar() + feed_real + yt_header() + f'<div style="position:absolute;left:0;top:2235px">{real_bar(glass=True)}</div>' + dialog)

stars = ''.join(f'<circle cx="{(i * 197) % 1080}" cy="{(i * 113) % 1500}" r="{1.5 + (i % 3)}" fill="#fff" opacity="{0.35 + (i % 5) / 8}"/>' for i in range(90))
lock_bg = ('<svg style="position:absolute;inset:0" width="1080" height="2424" viewBox="0 0 1080 2424"><defs><linearGradient id="gl" x1="0" y1="0" x2="0" y2="1">'
           '<stop offset="0" stop-color="#070b24"/><stop offset=".55" stop-color="#16205a"/><stop offset="1" stop-color="#2a2466"/></linearGradient></defs>'
           f'<rect width="1080" height="2424" fill="url(#gl)"/>{stars}'
           '<circle cx="760" cy="1420" r="120" fill="#efe7c8"/><circle cx="805" cy="1385" r="112" fill="#16205a"/>'
           '<path d="M0 2424 L0 1900 Q220 1760 420 1880 Q640 2010 820 1860 Q960 1750 1080 1820 L1080 2424Z" fill="#141a45"/>'
           '<path d="M0 2424 L0 2080 Q300 1960 560 2090 Q820 2210 1080 2060 L1080 2424Z" fill="#0d1236"/></svg>')
lock_inner = (lock_bg + '<div style="position:absolute;left:0;top:70px;width:1080px;text-align:center;font:500 40px Roboto;color:#fff">9:41</div>'
              '<div style="position:absolute;left:0;top:300px;width:1080px;text-align:center;font:400 46px Roboto;color:#e8e8f5">Thursday, November 19</div>'
              '<div style="position:absolute;left:0;top:360px;width:1080px;text-align:center;font:300 300px/1 Roboto;color:#f3f1ff;letter-spacing:-6px">9:41</div>'
              '<div style="position:absolute;left:510px;top:2240px"><svg width="60" height="60" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="1.8"><rect x="5" y="10.5" width="14" height="10" rx="2"/><path d="M8 10.5V7.5a4 4 0 0 1 8 0v3"/></svg></div>')
lock = screen('lock', lock_inner, bg='#070b24')
lock_mon = screen('lock_mon', lock_inner.replace('Thursday, November 19', 'Monday, November 16'), bg='#070b24')

NOTIFS = [
    ('Code Simple', 'New: Learn Python in One Video – Full Course', '#23408f', 'C'),
    ('Earth Explained', 'New: Why Mountains Are Shaped Like This', '#7a3cff', 'E'),
    ('Orbit Lab', 'Live in 10 min: Rocket landing, explained', '#1d6f86', 'O'),
]
def notif(i):
    ch, txt, col, ini = NOTIFS[i]
    return ('<div style="position:absolute;left:0;top:0;width:960px;height:214px;border-radius:52px;background:rgba(38,38,48,.94);box-sizing:border-box;padding:34px 40px;display:flex;gap:30px;align-items:flex-start">'
            '<div style="flex:none;width:92px;height:92px;border-radius:46px;background:#FF0033;display:flex;align-items:center;justify-content:center">'
            '<svg width="52" height="36" viewBox="0 0 78 54"><path d="M27 8v38l32-19z" fill="#fff"/></svg></div>'
            '<div style="flex:1;min-width:0"><div style="font:400 30px Roboto;color:#b9b9c6">YouTube · now</div>'
            f'<div style="margin-top:6px;font:500 38px Roboto;color:#fff">{ch}</div>'
            f'<div style="margin-top:4px;font:400 34px Roboto;color:#d6d6e2;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{txt}</div></div></div>')

grid_cards = [('funny cat moments 😹', '18M views'), ('my cat vs. a cucumber', '16M views'), ('kitten learns to meow', '9.1M views'), ('he did NOT expect that', '22M views')]
grid = ''
for i, (t, v) in enumerate(grid_cards):
    x = 32 + (i % 2) * 516
    y = 300 + (i // 2) * 960
    grid += (f'<div style="position:absolute;left:{x}px;top:{y}px;width:496px;height:880px;border-radius:26px;overflow:hidden">{th_vertical(i + 2, 496, 880, "")}'
             f'<div style="position:absolute;left:22px;bottom:70px;width:440px;font:500 38px/1.2 Roboto;color:#fff;text-shadow:0 2px 6px #000">{t}</div>'
             f'<div style="position:absolute;left:22px;bottom:24px;font:400 30px Roboto;color:#eee;text-shadow:0 2px 6px #000">{v}</div>'
             f'<div style="position:absolute;right:18px;top:12px;font:700 40px Roboto;color:#fff">⋮</div></div>')
search_hdr = (f'<div style="position:absolute;left:0;top:170px;width:1080px;height:120px;background:#0f0f0f"></div>'
              f'<div style="position:absolute;left:36px;top:196px">{ico_back(s=64)}</div>'
              '<div style="position:absolute;left:146px;top:186px;width:690px;height:88px;border-radius:44px;background:#272727;font:400 42px Roboto;color:#fff;display:flex;align-items:center;padding-left:34px;box-sizing:border-box">cats</div>'
              f'<div style="position:absolute;left:868px;top:186px;width:88px;height:88px;border-radius:44px;background:#272727;display:flex;align-items:center;justify-content:center">{ico_mic(s=52)}</div>'
              '<div style="position:absolute;left:1000px;top:196px;font:700 52px Roboto;color:#fff">⋮</div>')
search = screen('search', status_bar() + grid + search_hdr + f'<div style="position:absolute;left:0;top:2235px">{fs_bar()}</div>')

short_full = screen('short_full', f'<div style="position:absolute;inset:0">{th_vertical(2, 1080, 2424, "")}</div>'
                    '<div style="position:absolute;left:0;top:0;width:1080px;height:2424px;background:linear-gradient(transparent 70%, rgba(0,0,0,.55))"></div>'
                    + ''.join(f'<div style="position:absolute;right:40px;top:{1420 + k * 170}px;width:96px;height:96px;border-radius:48px;background:rgba(0,0,0,.35)"></div>' for k in range(4)) +
                    '<div style="position:absolute;left:40px;top:2060px;font:500 42px Roboto;color:#fff">@catclips · Subscribe</div>'
                    '<div style="position:absolute;left:40px;top:2130px;width:820px;font:400 38px Roboto;color:#fff">kitten learns to meow 😭 #cats #funny</div>', bg='#000')

# App-Startseite: Niklas' echtes Bildschirmfoto, nur die Statusleiste sauber ersetzt
app_home = screen('app_home', '<img src="app_home_src.png" style="position:absolute;left:0;top:0;width:1080px;height:2424px">'
                  '<div style="position:absolute;left:0;top:0;width:1080px;height:150px;background:#100d0e"></div>'
                  '<div style="position:absolute;left:82px;top:62px;font:500 46px Roboto;color:#fff">9:41</div>'
                  '<div style="position:absolute;right:82px;top:66px;display:flex;align-items:center;gap:18px;color:#fff;font:700 42px Roboto">'
                  '<svg width="40" height="40" viewBox="0 0 24 24"><path d="M2 20h3v-4H2zm5 0h3v-8H7zm5 0h3V8h-3zm5 0h3V4h-3z" fill="#fff"/></svg><span>5G</span>'
                  '<div style="width:64px;height:32px;border-radius:9px;background:#fff"></div></div>'
                  '<div style="position:absolute;left:100px;top:1466px;width:200px;height:96px;background:linear-gradient(rgb(145,31,38),rgb(151,32,39))"></div>'
                  '<div style="position:absolute;left:108px;top:1462px;font:700 96px/1 Roboto;color:#fff;letter-spacing:-1px">47</div>'
                  '<div style="position:absolute;left:192px;top:1712px;width:300px;height:80px;background:linear-gradient(rgb(157,33,40),rgb(159,33,40))"></div>'
                  '<div style="position:absolute;left:200px;top:1724px;font:700 50px/1 Roboto;color:#fff;letter-spacing:-1.5px">≈ 1 h 10 min</div>', bg='#100d0e')

# Ebenen (freigestellt, für das Herausspringen in 3D)
layers = (layer('L_bar_real', 1080, 150, real_bar(glass=False, with_shorts=False, standalone=True).replace('#1c1c1c', '#202227'))
          + layer('L_bar_real_full', 1080, 150, real_bar(glass=False, with_shorts=True, standalone=True).replace('#1c1c1c', '#202227'))
          + layer('L_shorts_icon', 150, 150, f'<div style="position:absolute;left:39px;top:39px">{ico_shorts(s=72)}</div>')
          + layer('L_bar_fs', 1080, 150, fs_bar(standalone=True))
          + ''.join(layer(f'L_notif{i}', 960, 214, notif(i)) for i in range(3))
          + layer('L_card0', 1000, 760, f'<div style="position:absolute;inset:0;border-radius:36px;overflow:hidden;background:#1c1c1c">{th_python(1000, 563)}'
                  '<div style="position:absolute;left:30px;top:590px;width:940px;font:500 44px/1.25 Roboto;color:#fff">Learn Python in One Video – Full Course</div>'
                  '<div style="position:absolute;left:30px;top:700px;font:400 32px Roboto;color:#aaa">Code Simple · 1:02:10</div></div>')
          + layer('L_card1', 1000, 760, f'<div style="position:absolute;inset:0;border-radius:36px;overflow:hidden;background:#1c1c1c">{th_mountains(1000, 563)}'
                  '<div style="position:absolute;left:30px;top:590px;width:940px;font:500 44px/1.25 Roboto;color:#fff">Why Mountains Are Shaped Like This</div>'
                  '<div style="position:absolute;left:30px;top:700px;font:400 32px Roboto;color:#aaa">Earth Explained · 24:05</div></div>')
          + layer('L_card2', 1000, 760, f'<div style="position:absolute;inset:0;border-radius:36px;overflow:hidden;background:#1c1c1c">{th_coaster(1000, 563)}'
                  '<div style="position:absolute;left:30px;top:590px;width:940px;font:500 44px/1.25 Roboto;color:#fff">Every Roller Coaster Element, Ranked</div>'
                  '<div style="position:absolute;left:30px;top:700px;font:400 32px Roboto;color:#aaa">Coaster Nerd · 32:17</div></div>')
          + layer('L_shield', 440, 440, '<div style="position:absolute;inset:0;border-radius:50%;overflow:hidden;background:url(app_home_src.png) -320px -436px / 1080px 2424px"></div>')
          + layer('L_closed', 520, 120, '<div style="position:absolute;inset:0;border-radius:60px;background:#1fbf6a;color:#fff;font:700 46px Roboto;display:flex;align-items:center;justify-content:center;gap:16px">'
                  '<svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>Closed instantly</div>'))


hdr = (f'<div style="position:absolute;inset:0;border-radius:40px;background:#0f0f0f"></div>'
       f'<div style="position:absolute;left:42px;top:38px">{yt_logo()}</div>'
       f'<div style="position:absolute;left:730px;top:32px">{ico_chat(s=72)}</div>'
       f'<div style="position:absolute;left:852px;top:32px">{ico_bell(s=72)}</div>'
       '<div style="position:absolute;left:892px;top:28px;width:54px;height:54px;border-radius:27px;background:#e8001f;color:#fff;font:500 30px Roboto;display:flex;align-items:center;justify-content:center">9+</div>'
       f'<div style="position:absolute;left:978px;top:34px">{ico_search(s=72)}</div>')
def vcard(i, t):
    return (f'<div style="position:absolute;inset:0;border-radius:30px;overflow:hidden">{th_vertical(i, 400, 712, "")}'
            f'<div style="position:absolute;left:22px;bottom:30px;width:360px;font:500 32px/1.2 Roboto;color:#fff;text-shadow:0 2px 6px #000">{t}</div></div>')
ring = ('<svg width="300" height="300" viewBox="0 0 300 300"><defs><filter id="gw" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="7" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>'
        '<circle cx="150" cy="150" r="104" fill="none" stroke="#F5C451" stroke-width="12" stroke-dasharray="26 15" stroke-linecap="round" filter="url(#gw)"/></svg>')
ring_rect = ('<svg width="1000" height="260" viewBox="0 0 1000 260"><defs><filter id="gw2" x="-10%" y="-30%" width="120%" height="160%"><feGaussianBlur stdDeviation="8" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>'
             '<rect x="18" y="20" width="964" height="220" rx="60" fill="none" stroke="#F5C451" stroke-width="9" filter="url(#gw2)"/></svg>')
gleam = ('<svg width="600" height="120" viewBox="0 0 600 120"><defs><linearGradient id="gg" x1="0" x2="1"><stop offset="0" stop-color="#FFE7A8" stop-opacity="0"/><stop offset=".5" stop-color="#FFF3CF"/><stop offset="1" stop-color="#FFE7A8" stop-opacity="0"/></linearGradient>'
         '<filter id="gb"><feGaussianBlur stdDeviation="3"/></filter></defs><rect x="0" y="52" width="600" height="16" rx="8" fill="url(#gg)" filter="url(#gb)"/><rect x="60" y="57" width="480" height="6" rx="3" fill="url(#gg)"/></svg>')
stop = ('<svg width="300" height="300" viewBox="0 0 300 300"><circle cx="150" cy="150" r="118" fill="none" stroke="#E5172B" stroke-width="26"/>'
        '<line x1="68" y1="232" x2="232" y2="68" stroke="#E5172B" stroke-width="26" stroke-linecap="round"/></svg>')
finger = ('<svg width="160" height="160" viewBox="0 0 160 160"><circle cx="80" cy="80" r="52" fill="rgba(255,255,255,.55)" stroke="#fff" stroke-width="6"/></svg>')
layers += (layer('L_header', 1080, 160, hdr)
           + layer('L_bar_fs_empty', 1080, 150, fs_bar(standalone=True)
                   .replace(f'<div style="position:absolute;left:{405 - 32}px;top:22px">{ico_subs(s=64)}</div>', '')
                   .replace('>Subscriptions<', '><'))
           + layer('L_subs_slot', 300, 150, f'<div style="position:absolute;left:{150 - 32}px;top:22px">{ico_subs(s=64)}</div>'
                   '<div style="position:absolute;left:0;width:300px;top:86px;text-align:center;font:400 30px Roboto;color:#f1f1f1">Subscriptions</div>')
           + layer('L_shorts_slot', 300, 150, f'<div style="position:absolute;left:{150 - 36}px;top:{75 - 36 - 12}px">{ico_shorts(s=72)}</div>')
           + ''.join(layer(f'L_vcard{i}', 400, 712, vcard(i, t)) for i, t in enumerate(['wait for it 😭', 'POV: 3 hours later', 'part 47 💀']))
           + layer('L_ring', 300, 300, ring) + layer('L_ring_rect', 1000, 260, ring_rect) + layer('L_gleam', 600, 120, gleam)
           + layer('L_stop', 300, 300, stop) + layer('L_finger', 160, 160, finger))

# ---- Video 14: weitere Shorts zum Weiterwischen, Freitag-Sperrbildschirm, Bildschirmzeit-Karte
def short_screen(id_, pal, chan, cap):
    return screen(id_, f'<div style="position:absolute;inset:0">{th_vertical(pal, 1080, 2424, "")}</div>'
                  '<div style="position:absolute;left:0;top:0;width:1080px;height:2424px;background:linear-gradient(transparent 70%, rgba(0,0,0,.55))"></div>'
                  + ''.join(f'<div style="position:absolute;right:40px;top:{1420 + k * 170}px;width:96px;height:96px;border-radius:48px;background:rgba(0,0,0,.35)"></div>' for k in range(4)) +
                  f'<div style="position:absolute;left:40px;top:2060px;font:500 42px Roboto;color:#fff">{chan} · Subscribe</div>'
                  f'<div style="position:absolute;left:40px;top:2130px;width:820px;font:400 38px Roboto;color:#fff">{cap}</div>', bg='#000')
short_full2 = short_screen('short_full2', 4, '@dailyclips', 'POV: it is 3am and you are still here 💀')
short_full3 = short_screen('short_full3', 5, '@funnyfails', 'part 47 of 120 #fyp')
lock_fri = screen('lock_fri', lock_inner.replace('Thursday, November 19', 'Friday, November 20'), bg='#070b24')
st = ('<div style="position:absolute;inset:0;border-radius:52px;background:rgba(30,30,34,.97);box-sizing:border-box;padding:44px 52px">'
      '<div style="font:500 34px Roboto;color:#b9b9c6">Screen time today</div>'
      '<div style="margin-top:6px;font:700 104px/1.05 Roboto;color:#fff;letter-spacing:-2px">3 h 12 min</div>'
      '<div style="margin-top:30px;height:22px;border-radius:11px;background:#3a3a42;overflow:hidden"><div style="width:89%;height:100%;background:#FF0033"></div></div>'
      '<div style="margin-top:18px;display:flex;justify-content:space-between;font:400 32px Roboto;color:#d6d6e2"><span>YouTube Shorts</span><span>2 h 51 min</span></div></div>')
layers += layer('L_screentime', 900, 400, st)

html = f'''<!doctype html><html><head><meta charset="utf-8"><style>
@font-face{{font-family:Roboto;font-weight:300;src:url(file://{FONTS}/roboto/files/roboto-latin-300-normal.woff2)}}
@font-face{{font-family:Roboto;font-weight:400;src:url(file://{FONTS}/roboto/files/roboto-latin-400-normal.woff2)}}
@font-face{{font-family:Roboto;font-weight:500;src:url(file://{FONTS}/roboto/files/roboto-latin-500-normal.woff2)}}
@font-face{{font-family:Roboto;font-weight:700;src:url(file://{FONTS}/roboto/files/roboto-latin-700-normal.woff2)}}
@font-face{{font-family:Oswald;font-weight:700;src:url(file://{FONTS}/anton/files/anton-latin-400-normal.woff2)}}
*{{margin:0;padding:0;box-sizing:content-box}} body{{background:transparent;width:1080px}}
.scr{{position:relative;width:1080px;height:2424px;overflow:hidden}}
.lay{{position:relative;overflow:visible;background:transparent}}
</style></head><body>
{yt_real}{yt_fs}{uninstall}{lock}{lock_mon}{lock_fri}{short_full2}{short_full3}{search}{short_full}{app_home}{layers}
</body></html>'''
open(os.path.join(H, 'screens.html'), 'w').write(html)
print('ok')
