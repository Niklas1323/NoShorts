import React, { useEffect, useState } from 'react';
import { AbsoluteFill, Audio, continueRender, delayRender, random, staticFile, useCurrentFrame } from 'remotion';
import { BarIcon, Logo, RED, ease, lin } from './Ad';
import WORDS from './words11.json';

/**
 * Werbevideo 11 "Your attention is worth money" (quer 16:9): gleicher Strich-Stil und gleiches Tempo wie Video 10, neuer Inhalt. Stil der Vorlage: schwarz, Perspektiv-Gitter, weisse Linien-Symbole mit
 * Leuchten, Satz oben, Wörter werden beim Sprechen hell. Anfang = derselbe Satz wie die Vorlage, dann F*ck Shorts.
 * Szenen-Zeiten kommen aus den Wortzeiten der Aufnahme (words11.json, Anker = erstes Wort der Szene).
 */
export const FPS11 = 30;
const F = 'Inter';
const GLOW = '0 0 14px rgba(255,255,255,.55), 0 0 34px rgba(255,255,255,.2)';
const LINE = { fill: 'none', stroke: '#fff', strokeWidth: 5, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const };

const useInter = () => {
  const [h] = useState(() => delayRender('inter'));
  useEffect(() => {
    new FontFace(F, `url(${staticFile('inter-latin-800-normal.woff2')})`, { weight: '800' }).load().then((f) => {
      document.fonts.add(f);
      continueRender(h);
    });
  }, [h]);
};

type W = [string, number, number];
const ws = (WORDS as W[]).map(([w, a, e]) => [w.toLowerCase().replace(/[^a-z0-9]/g, ''), a, e] as W);

// Szenen: Anker-Wort (erstes gesprochenes Wort), Text (| = neue Zeile)
const DEF: { k: string; anchor: string; text: string; lead?: number }[] = [
  { k: 'money', anchor: 'your', text: 'YOUR ATTENTION IS WORTH|MONEY, B*TCH.' },
  { k: 'fight', anchor: 'thats', text: "THAT'S WHY EVERY APP|IS FIGHTING FOR IT." },
  { k: 'wants', anchor: 'tiktok', text: 'TIKTOK WANTS IT. INSTAGRAM WANTS IT.|YOUTUBE SHORTS WANTS IT.' },
  { k: 'swipe', anchor: 'one', text: 'ONE SWIPE. THEN ANOTHER.|THEN 3 HOURS ARE GONE.' },
  { k: 'rich', anchor: 'they', text: 'THEY GET RICH.|YOU GET NOTHING.' },
  { k: 'fried', anchor: 'your', text: 'BRAIN? FRIED. GOALS? PAUSED.|SLEEP? GONE.' },
  { k: 'why', anchor: 'so', text: 'SO WHY THE F*CK ARE YOU|GIVING IT AWAY FOR FREE?' },
  { k: 'take', anchor: 'take', text: 'TAKE IT BACK.' },
  { k: 'delete', anchor: 'delete', text: 'DELETE THE APPS THAT EAT YOU.' },
  { k: 'keep', anchor: 'keep', text: 'KEEP YOUTUBE FOR THE LONG|VIDEOS THAT MAKE YOU SMARTER.' },
  { k: 'kill', anchor: 'but', text: 'BUT KILL THE SHORTS.' },
  { k: 'built', anchor: 'with', text: 'WITH F*CK SHORTS.' },
  { k: 'back', anchor: 'no', text: 'NO SHORTS BUTTON. NO ENDLESS FEED.|JUST YOUR TIME. BACK.' },
  { k: 'cta', anchor: 'comment', text: '' },
];
const SCENES = (() => {
  const out: { k: string; text: string; a: number; speechEnd: number; b: number; wordTimes: number[] }[] = [];
  let idx = 0;
  const starts: number[] = [];
  for (const d of DEF) {
    const anchors = d.anchor === 'ninety' ? ['ninety', '90'] : [d.anchor];
    while (idx < ws.length && !anchors.includes(ws[idx][0])) idx++;
    starts.push(Math.min(idx, ws.length - 1));
    idx++;
  }
  DEF.forEach((d, i) => {
    const si = starts[i];
    const ei = i + 1 < DEF.length ? starts[i + 1] : ws.length;
    const words = ws.slice(si, ei);
    const a = Math.max(0, words[0][1] - 0.12 - (d.lead ?? 0));
    const b = i + 1 < DEF.length ? ws[starts[i + 1]][1] - 0.12 - (DEF[i + 1].lead ?? 0) : ws[ws.length - 1][2] + 3.0;
    out.push({ k: d.k, text: d.text, a, b, speechEnd: words[words.length - 1][2], wordTimes: words.map((w) => w[1]) });
  });
  return out;
})();
export const DURATION11_S = SCENES[SCENES.length - 1].b;
const S = (k: string) => SCENES.find((s) => s.k === k)!;

/** Satz oben, Wörter werden beim Sprechen hell. */
const Caption: React.FC<{ t: number; sc: (typeof SCENES)[number] }> = ({ t, sc }) => {
  const lines = sc.text.split('|');
  const all = lines.join(' ').split(' ');
  const n = all.length;
  let wi = 0;
  return (
    <div style={{ position: 'absolute', left: 0, right: 0, top: 110, textAlign: 'center' }}>
      {lines.map((ln, li) => (
        <div key={li} style={{ fontFamily: F, fontWeight: 800, fontSize: 86, lineHeight: 1.12, color: '#fff', letterSpacing: 1 }}>
          {ln.split(' ').map((w, j) => {
            const i = wi++;
            const at = sc.wordTimes[Math.min(sc.wordTimes.length - 1, Math.floor((i / n) * sc.wordTimes.length))] ?? sc.a;
            const on = ease(t, at - 0.05, at + 0.08);
            return <span key={j} style={{ opacity: 0.28 + 0.72 * on, textShadow: on > 0.5 ? GLOW : 'none' }}>{w}{j < ln.split(' ').length - 1 ? ' ' : ''}</span>;
          })}
        </div>
      ))}
    </div>
  );
};

/** Hintergrund: schwarz, Perspektiv-Boden, senkrechte Linien, Staub. */
const Backdrop: React.FC<{ t: number }> = ({ t }) => (
  <svg viewBox="0 0 1920 1080" width={1920} height={1080} style={{ position: 'absolute', inset: 0 }}>
    <defs>
      <radialGradient id="vig" cx="50%" cy="45%" r="70%"><stop offset="0" stopColor="#151515" /><stop offset="1" stopColor="#000" /></radialGradient>
    </defs>
    <rect width={1920} height={1080} fill="url(#vig)" />
    {Array.from({ length: 13 }, (_, i) => <line key={i} x1={i * 160} y1={0} x2={i * 160} y2={700} stroke="#fff" strokeOpacity={0.035} strokeWidth={2} />)}
    {Array.from({ length: 25 }, (_, i) => <line key={`r${i}`} x1={960} y1={700} x2={-2400 + i * 280} y2={1080} stroke="#fff" strokeOpacity={0.05} strokeWidth={1.5} />)}
    {Array.from({ length: 9 }, (_, i) => {
      const y = 700 + Math.pow((i + ((t * 0.25) % 1)) / 9, 2) * 380;
      return <line key={`h${i}`} x1={0} y1={y} x2={1920} y2={y} stroke="#fff" strokeOpacity={0.05} strokeWidth={1.5} />;
    })}
    {Array.from({ length: 60 }, (_, i) => {
      const x = (random(`dx${i}`) * 1920 + t * (8 + random(`ds${i}`) * 14)) % 1920;
      const y = random(`dy${i}`) * 1080;
      return <circle key={`d${i}`} cx={x} cy={y} r={1 + random(`dr${i}`) * 1.6} fill="#fff" opacity={0.15 + 0.35 * random(`do${i}`)} />;
    })}
  </svg>
);

const Glow: React.FC<{ children: React.ReactNode; red?: boolean }> = ({ children, red }) => (
  <svg viewBox="0 0 1920 1080" width={1920} height={1080} style={{ position: 'absolute', inset: 0, filter: `drop-shadow(0 0 10px ${red ? RED : 'rgba(255,255,255,.75)'})` }}>{children}</svg>
);

/** Handy-Umriss mit Inhalt (Inhalt bleibt im Bildschirm). */
const PhoneOutline: React.FC<{ x: number; y: number; s?: number; children?: React.ReactNode; clipId: string }> = ({ x, y, s = 1, children, clipId }) => (
  <g transform={`translate(${x} ${y}) scale(${s})`}>
    <clipPath id={clipId}><rect x={-110} y={-210} width={220} height={420} rx={34} /></clipPath>
    <g clipPath={`url(#${clipId})`}>{children}</g>
    <rect x={-120} y={-220} width={240} height={440} rx={40} {...LINE} />
    <line x1={-30} y1={196} x2={30} y2={196} {...LINE} strokeWidth={4} />
  </g>
);

const ShortsGlyph: React.FC<{ x: number; y: number; s: number; c?: string }> = ({ x, y, s, c = '#fff' }) => (
  <g transform={`translate(${x} ${y}) scale(${s})`}>
    <rect x={-22} y={-34} width={44} height={68} rx={12} {...LINE} stroke={c} />
    <path d="M-8 -14 L12 0 L-8 14 Z" fill={c} />
  </g>
);

const Cross: React.FC<{ x: number; y: number; r: number; p: number; red?: boolean }> = ({ x, y, r, p, red }) => (
  <g stroke={red ? RED : '#fff'} strokeWidth={7} strokeLinecap="round">
    <line x1={x - r} y1={y - r} x2={x - r + 2 * r * Math.min(1, p * 2)} y2={y - r + 2 * r * Math.min(1, p * 2)} />
    {p > 0.5 && <line x1={x + r} y1={y - r} x2={x + r - 2 * r * Math.min(1, (p - 0.5) * 2)} y2={y - r + 2 * r * Math.min(1, (p - 0.5) * 2)} />}
  </g>
);

const at = (k: string, word: string, nth = 0) => {
  const sc = S(k);
  const list = ws.filter((w) => w[1] >= sc.a && w[1] < sc.b && w[0] === word);
  return list[nth]?.[1] ?? sc.a;
};

// ------------------------------------------------------------------------------------------- Szenen-Inhalte
const SwipeScene: React.FC<{ t: number }> = ({ t }) => {
  const sc = S('swipe');
  const lt = Math.max(0, t - sc.a);
  const k = Math.floor(lt / 0.45);
  const sp = ease(lt % 0.45, 0, 0.16);
  const min = ease(t, at('swipe', 'minute') - 0.05, at('swipe', 'minute') + 0.2);
  return (
    <Glow>
      <PhoneOutline x={760} y={640} clipId="ps">
        {[k - 1, k].map((j) => j >= 0 && (
          <g key={j} transform={`translate(0 ${j === k ? 420 * (1 - sp) : -420 * sp})`}>
            <ShortsGlyph x={0} y={-10} s={1.6} />
            <line x1={-70} y1={140} x2={40} y2={140} {...LINE} strokeWidth={4} opacity={0.6} />
          </g>
        ))}
      </PhoneOutline>
      <g transform="translate(1200 640)">
        <circle r={120} {...LINE} strokeWidth={7} />
        <line x1={0} y1={0} x2={0} y2={-80} {...LINE} strokeWidth={7} transform={`rotate(${lt * 120})`} />
        <line x1={0} y1={0} x2={50} y2={0} {...LINE} strokeWidth={7} />
      </g>
      {Array.from({ length: Math.min(k + 1, 8) }, (_, i) => {
        const p = lin(lt - i * 0.45, 0, 0.9);
        return p > 0 && p < 1 ? <text key={i} x={1200 + 140} y={540 - p * 120} fontFamily={F} fontWeight={800} fontSize={40} fill="#fff" opacity={(1 - p) * (0.4 + 0.6 * min)}>−1 MIN</text> : null;
      })}
    </Glow>
  );
};

const BuiltScene: React.FC<{ t: number }> = ({ t }) => {
  const sc = S('built');
  const lt = t - sc.a;
  const logoAt = ws.find((w) => w[1] > sc.a && (w[0] === 'fuck' || w[0] === 'fck' || w[0] === 'f'))?.[1] ?? sc.a + 1.4;
  const logo = ease(t, logoAt - 0.05, logoAt + 0.25);
  return (
    <>
      <Glow>
        <g transform="translate(960 660)" opacity={1 - logo}>
          <rect x={-260} y={-150} width={520} height={300} rx={16} {...LINE} strokeWidth={6} />
          <path d="M-320 170 H320" {...LINE} strokeWidth={8} />
          {[0, 1, 2, 3, 4].map((i) => <line key={i} x1={-220} y1={-100 + i * 50} x2={-220 + (120 + random(`c${i}`) * 260) * Math.min(1, Math.max(0, lt * 1.5 - i * 0.2))} y2={-100 + i * 50} {...LINE} strokeWidth={5} />)}
        </g>
      </Glow>
      <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', paddingTop: 160, opacity: logo }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 36, transform: `scale(${0.7 + 0.3 * logo})`, filter: 'drop-shadow(0 0 30px rgba(229,23,43,.65))' }}>
          <Logo size={190} />
          <div style={{ fontFamily: F, fontWeight: 800, fontSize: 120, color: '#fff', textShadow: GLOW }}>F<span style={{ color: RED }}>*</span>CK SHORTS</div>
        </div>
      </AbsoluteFill>
    </>
  );
};

const CtaScene: React.FC<{ t: number }> = ({ t }) => {
  const sc = S('cta');
  const b1 = ease(t, at('cta', 'test') - 0.1, at('cta', 'test') + 0.15);
  const signAt = at('cta', 'sign');
  const b2 = ease(t, signAt - 0.05, signAt + 0.2);
  const tst = ease(t, at('cta', '12') - 0.1, at('cta', '12') + 0.2);
  return (
    <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', gap: 34, opacity: ease(t, sc.a, sc.a + 0.2) }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 28, filter: 'drop-shadow(0 0 26px rgba(229,23,43,.55))' }}>
        <Logo size={130} />
        <div style={{ fontFamily: F, fontWeight: 800, fontSize: 84, color: '#fff', textShadow: GLOW }}>F<span style={{ color: RED }}>*</span>CK SHORTS</div>
      </div>
      <div style={{ opacity: b1, transform: `scale(${0.85 + 0.15 * b1 + 0.03 * Math.sin(t * 8)})`, background: '#fff', color: '#000', fontFamily: F, fontWeight: 800, fontSize: 60, padding: '20px 60px', borderRadius: 70, boxShadow: GLOW }}>COMMENT "TEST"</div>
      <div style={{ opacity: b2, transform: `scale(${0.85 + 0.15 * b2 + 0.03 * Math.sin(t * 8 + 1)})`, background: RED, color: '#fff', fontFamily: F, fontWeight: 800, fontSize: 50, padding: '18px 52px', borderRadius: 70, boxShadow: `0 0 30px ${RED}` }}>SIGN UP 👉 LINK IN BIO</div>
      <div style={{ opacity: tst, fontFamily: F, fontWeight: 800, fontSize: 32, color: '#ddd', letterSpacing: 2 }}>WE NEED 12 TESTERS FOR 14 DAYS · COMING SOON TO GOOGLE PLAY</div>
    </AbsoluteFill>
  );
};

/** App-Kachel (Umriss, nur Name, keine fremden Logos). */
const AppTile: React.FC<{ x: number; y: number; s?: number; name: string; cross: number; fall: number; o?: number }> = ({ x, y, s = 1, name, cross, fall, o = 1 }) => (
  <g transform={`translate(${x} ${y + fall * fall * 900}) rotate(${fall * 50}) scale(${s})`} opacity={o * (1 - Math.min(1, fall * 1.5))}>
    <rect x={-110} y={-110} width={220} height={220} rx={52} {...LINE} strokeWidth={8} />
    <text x={0} y={175} textAnchor="middle" fontFamily={F} fontWeight={800} fontSize={40} fill="#fff">{name}</text>
    <Cross x={0} y={0} r={80} p={cross} red />
  </g>
);

const TeachScene: React.FC<{ t: number }> = ({ t }) => {
  const sc = S('teach');
  return (
    <Glow>
      {[-1, 0, 1].map((i) => {
        const p = ease(t, sc.a + 0.1 + (i + 1) * 0.15, sc.a + 0.35 + (i + 1) * 0.15);
        return (
          <g key={i} transform={`translate(${960 + i * 440} ${640 + 40 * (1 - p)})`} opacity={p}>
            <rect x={-190} y={-110} width={380} height={214} rx={20} {...LINE} strokeWidth={6} />
            <path d="M-24 -34 L34 0 L-24 34 Z" fill="#fff" />
            <rect x={-170} y={80} width={340 * (0.3 + 0.2 * (i + 1))} height={8} rx={4} fill="#fff" />
            <text x={170} y={66} textAnchor="end" fontFamily={F} fontWeight={800} fontSize={26} fill="#fff">{['18:42', '1:02:10', '24:05'][i + 1]}</text>
          </g>
        );
      })}
    </Glow>
  );
};


const Person: React.FC<{ x: number; y: number; s?: number; o?: number }> = ({ x, y, s = 1, o = 1 }) => (
  <g transform={`translate(${x} ${y}) scale(${s})`} opacity={o}>
    <circle cx={0} cy={-80} r={28} {...LINE} strokeWidth={6} />
    <path d="M-46 60 V10 q0 -50 46 -50 q46 0 46 50 V60" {...LINE} strokeWidth={6} />
  </g>
);

const Eye: React.FC<{ x: number; y: number; s?: number }> = ({ x, y, s = 1 }) => (
  <g transform={`translate(${x} ${y}) scale(${s})`}>
    <path d="M-150 0 Q0 -110 150 0 Q0 110 -150 0 Z" {...LINE} strokeWidth={7} />
    <circle r={46} {...LINE} strokeWidth={7} /><circle r={16} fill="#fff" />
  </g>
);

/** 1: Deine Aufmerksamkeit = Geld (Auge, Dollar fliegen raus). */
const MoneyScene: React.FC<{ t: number }> = ({ t }) => {
  const sc = S('money');
  const lt = t - sc.a;
  const m = ease(t, at('money', 'money') - 0.05, at('money', 'money') + 0.2);
  return (
    <Glow>
      <Eye x={960} y={640} s={1.2} />
      {Array.from({ length: 8 }, (_, i) => {
        const p = ((lt * 0.9 + i / 8) % 1) * m;
        const ang = (i / 8) * Math.PI * 2;
        return <text key={i} x={960 + Math.cos(ang) * (190 + 260 * p)} y={650 + Math.sin(ang) * (120 + 160 * p)} textAnchor="middle" fontFamily={F} fontWeight={800} fontSize={60} fill="#3FD06A" opacity={m * (1 - p)}>$</text>;
      })}
    </Glow>
  );
};

/** 2: Jede App zerrt an dir. */
const FightScene: React.FC<{ t: number }> = ({ t }) => {
  const sc = S('fight');
  const lt = t - sc.a;
  const tug = Math.sin(lt * 14) * 10;
  const tiles = [[560, 600], [1360, 600], [960, 450]];
  return (
    <Glow>
      <Person x={960 + tug} y={830} s={1.4} />
      {tiles.map(([x, y], i) => {
        const p = ease(lt, 0.1 + i * 0.12, 0.3 + i * 0.12);
        return (
          <g key={i} opacity={p}>
            <line x1={x} y1={y} x2={960 + tug} y2={770} {...LINE} strokeWidth={3} strokeDasharray="12 10" />
            <rect x={x - 60} y={y - 60} width={120} height={120} rx={30} {...LINE} strokeWidth={6} fill="#000" />
            <ShortsGlyph x={x} y={y} s={0.9} c={i === 2 ? RED : '#fff'} />
          </g>
        );
      })}
    </Glow>
  );
};

/** 3: TikTok / Instagram / Shorts wollen es. */
const WantsScene: React.FC<{ t: number }> = ({ t }) => {
  const names = ['TIKTOK', 'INSTAGRAM', 'SHORTS'];
  const times = [at('wants', 'tiktok'), at('wants', 'instagram'), at('wants', 'shorts')];
  return (
    <Glow>
      {names.map((n, i) => {
        const p = ease(t, times[i] - 0.05, times[i] + 0.15);
        const x = 560 + i * 400;
        return (
          <g key={n} transform={`translate(${x} ${640}) scale(${0.6 + 0.4 * p})`} opacity={p}>
            <rect x={-110} y={-110} width={220} height={220} rx={52} {...LINE} strokeWidth={8} stroke={i === 2 ? RED : '#fff'} />
            {i === 2 ? <ShortsGlyph x={0} y={0} s={1.5} c={RED} /> : <text x={0} y={14} textAnchor="middle" fontFamily={F} fontWeight={800} fontSize={i === 1 ? 30 : 40} fill="#fff">{n}</text>}
            <text x={0} y={175} textAnchor="middle" fontFamily={F} fontWeight={800} fontSize={34} fill="#fff">WANTS IT</text>
          </g>
        );
      })}
    </Glow>
  );
};

/** 5: Sie werden reich, du bekommst nichts. */
const RichScene: React.FC<{ t: number }> = ({ t }) => {
  const sc = S('rich');
  const g = ease(t, sc.a + 0.1, sc.a + 0.9);
  const you = ease(t, at('rich', 'you') - 0.05, at('rich', 'you') + 0.2);
  return (
    <Glow>
      <g transform="translate(640 820)">
        {[0, 1, 2, 3, 4].map((i) => <rect key={i} x={-200 + i * 85} y={-(80 + i * 70) * g} width={60} height={(80 + i * 70) * g} {...LINE} strokeWidth={6} />)}
        <text x={0} y={70} textAnchor="middle" fontFamily={F} fontWeight={800} fontSize={34} fill="#fff">THEM</text>
        <text x={160} y={-460 * g} fontFamily={F} fontWeight={800} fontSize={70} fill="#3FD06A" opacity={g}>$$$</text>
      </g>
      <g opacity={you}>
        <Person x={1300} y={720} s={1.3} />
        <text x={1300} y={900} textAnchor="middle" fontFamily={F} fontWeight={800} fontSize={34} fill="#fff">YOU</text>
        <text x={1460} y={600} fontFamily={F} fontWeight={800} fontSize={80} fill={RED}>$0</text>
      </g>
    </Glow>
  );
};

/** 6: Gehirn / Ziele / Schlaf – je durchgestrichen. */
const FriedScene: React.FC<{ t: number }> = ({ t }) => {
  const items = [
    { w: 'fried', x: 560, d: 'M-80 10 q-20 -90 50 -100 q30 -40 80 -10 q60 -10 60 50 q40 30 10 80 q10 60 -60 60 h-90 q-60 0 -50 -80 z', label: 'BRAIN' },
    { w: 'paused', x: 960, d: 'M0 -110 a110 110 0 1 1 -0.1 0 M0 -60 a60 60 0 1 1 -0.1 0 M0 -16 a16 16 0 1 1 -0.1 0', label: 'GOALS' },
    { w: 'gone', x: 1360, d: 'M30 -100 a100 100 0 1 0 70 170 a80 80 0 1 1 -70 -170 z', label: 'SLEEP' },
  ];
  return (
    <Glow>
      {items.map((it) => {
        const ta = at('fried', it.w);
        const show = ease(t, ta - 0.6, ta - 0.4);
        const x = ease(t, ta - 0.05, ta + 0.25);
        return (
          <g key={it.label} opacity={Math.max(0.25, show)}>
            <path transform={`translate(${it.x} 630)`} d={it.d} {...LINE} strokeWidth={7} />
            <text x={it.x} y={820} textAnchor="middle" fontFamily={F} fontWeight={800} fontSize={34} fill="#fff">{it.label}</text>
            <Cross x={it.x} y={630} r={110} p={x} red />
          </g>
        );
      })}
    </Glow>
  );
};

/** 7: Warum gibst du es gratis her? – Preisschild $0. */
const WhyScene: React.FC<{ t: number }> = ({ t }) => {
  const free = ease(t, at('why', 'free') - 0.1, at('why', 'free') + 0.15);
  return (
    <Glow>
      <Eye x={760} y={660} s={1} />
      <g transform={`translate(1240 640) rotate(${-12 + 6 * free}) scale(${0.8 + 0.25 * free})`} opacity={0.4 + 0.6 * free}>
        <path d="M-150 -80 H110 L170 0 L110 80 H-150 Z" {...LINE} strokeWidth={7} stroke={RED} />
        <circle cx={105} cy={0} r={12} {...LINE} strokeWidth={5} stroke={RED} />
        <text x={-30} y={30} textAnchor="middle" fontFamily={F} fontWeight={800} fontSize={90} fill={RED}>$0</text>
      </g>
    </Glow>
  );
};

/** 8: Hol sie zurück – Uhr dreht rückwärts. */
const TakeScene: React.FC<{ t: number }> = ({ t }) => {
  const lt = t - S('take').a;
  return (
    <Glow>
      <g transform="translate(960 640)">
        <circle r={170} {...LINE} strokeWidth={8} />
        <line x1={0} y1={0} x2={0} y2={-120} {...LINE} strokeWidth={9} transform={`rotate(${-lt * 720})`} />
        <line x1={0} y1={0} x2={80} y2={0} {...LINE} strokeWidth={9} transform={`rotate(${-lt * 60})`} />
        <path d="M-230 -60 a240 240 0 0 1 120 -170" {...LINE} strokeWidth={8} />
        <path d="M-130 -250 l20 22 l-30 8" {...LINE} strokeWidth={8} />
      </g>
    </Glow>
  );
};

/** 9: Apps löschen. */
const DeleteAll: React.FC<{ t: number }> = ({ t }) => {
  const sc = S('delete');
  const cr = ease(t, sc.a + 0.15, sc.a + 0.45);
  const fall = Math.max(0, t - at('delete', 'eat'));
  return (
    <Glow>
      {['TIKTOK', 'INSTAGRAM', 'SNAPCHAT'].map((n, i) => <AppTile key={n} x={560 + i * 400} y={620} s={1} name={n} cross={cr} fall={fall * (0.9 + i * 0.15)} />)}
    </Glow>
  );
};

/** 10: YouTube behalten, lange Videos. */
const KeepScene: React.FC<{ t: number }> = ({ t }) => {
  const sc = S('keep');
  const ok = ease(t, sc.a + 0.1, sc.a + 0.3);
  return (
    <Glow>
      <g transform="translate(560 640)">
        <rect x={-160} y={-115} width={320} height={230} rx={50} {...LINE} strokeWidth={8} />
        <path d="M-30 -48 L56 0 L-30 48 Z" fill="#fff" />
        <g transform="translate(150 -110)" opacity={ok}>
          <circle r={50} fill="#000" stroke="#3FD06A" strokeWidth={8} />
          <path d="M-22 0 L-6 18 L26 -18" fill="none" stroke="#3FD06A" strokeWidth={9} strokeLinecap="round" strokeLinejoin="round" />
        </g>
      </g>
      {[0, 1].map((i) => {
        const p = ease(t, at('keep', 'long') - 0.1 + i * 0.15, at('keep', 'long') + 0.15 + i * 0.15);
        return (
          <g key={i} transform={`translate(1270 ${540 + i * 230})`} opacity={p}>
            <rect x={-200} y={-95} width={400} height={190} rx={18} {...LINE} strokeWidth={6} />
            <path d="M-20 -30 L30 0 L-20 30 Z" fill="#fff" />
            <text x={180} y={75} textAnchor="end" fontFamily={F} fontWeight={800} fontSize={24} fill="#fff">{['1:02:10', '24:05'][i]}</text>
          </g>
        );
      })}
    </Glow>
  );
};

/** 11: Shorts töten – Shorts-Knopf in der Leiste wird rot durchgestrichen. */
const KillScene: React.FC<{ t: number }> = ({ t }) => {
  const k = ease(t, at('kill', 'shorts') - 0.1, at('kill', 'shorts') + 0.2);
  return (
    <Glow>
      <g transform="translate(960 640)">
        <rect x={-380} y={-75} width={760} height={150} rx={34} {...LINE} strokeWidth={6} />
        <ShortsGlyph x={-95} y={0} s={1.3} c={RED} />
        <Cross x={-95} y={0} r={70} p={k} red />
        <circle cx={95} cy={0} r={34} {...LINE} strokeWidth={6} /><path d="M95 -18 V18 M77 0 H113" {...LINE} strokeWidth={6} />
        <path d="M-300 20 V-8 L-275 -30 L-250 -8 V20 Z" {...LINE} strokeWidth={6} />
        <circle cx={285} cy={-12} r={14} {...LINE} strokeWidth={6} /><path d="M255 28 q30 -36 60 0" {...LINE} strokeWidth={6} />
      </g>
    </Glow>
  );
};

/** 12: Logo. */
const LogoScene: React.FC<{ t: number }> = ({ t }) => {
  const sc = S('built');
  const logoAt = ws.find((w) => w[1] > sc.a && (w[0] === 'fuck' || w[0] === 'fck' || w[0] === 'f'))?.[1] ?? sc.a + 0.3;
  const p = ease(t, logoAt - 0.08, logoAt + 0.18);
  return (
    <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', paddingTop: 160, opacity: p }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 36, transform: `scale(${0.6 + 0.4 * p})`, filter: 'drop-shadow(0 0 34px rgba(229,23,43,.7))' }}>
        <Logo size={200} />
        <div style={{ fontFamily: F, fontWeight: 800, fontSize: 124, color: '#fff', textShadow: GLOW }}>F<span style={{ color: RED }}>*</span>CK SHORTS</div>
      </div>
    </AbsoluteFill>
  );
};

/** 13: Kein Shorts-Knopf, kein Endlos-Feed, deine Zeit zurück. */
const BackScene: React.FC<{ t: number }> = ({ t }) => {
  const sc = S('back');
  const fill = ease(t, at('back', 'time') - 0.1, sc.speechEnd + 0.2);
  const kinds = ['home', 'plus', 'you'] as const;
  return (
    <>
      <div style={{ position: 'absolute', left: 960 - 520, top: 620, width: 520, height: 140, borderRadius: 32, border: '5px solid #fff', boxShadow: GLOW, display: 'flex', justifyContent: 'space-around', alignItems: 'center' }}>
        {kinds.map((kd) => <div key={kd} style={{ width: 70, height: 70, display: 'flex', alignItems: 'center', justifyContent: 'center', transform: 'scale(1.3)', filter: 'drop-shadow(0 0 8px rgba(255,255,255,.7))' }}><BarIcon kind={kd} color="#fff" /></div>)}
      </div>
      <Glow>
        <g transform="translate(1300 690)">
          <circle r={130} {...LINE} strokeWidth={7} stroke="#3FD06A" />
          <path d={`M0 0 L0 -130 A130 130 0 ${fill > 0.5 ? 1 : 0} 1 ${130 * Math.sin(fill * Math.PI * 2 - 0.0001)} ${-130 * Math.cos(fill * Math.PI * 2 - 0.0001)} Z`} fill="#3FD06A" opacity={0.35} />
          <text x={0} y={200} textAnchor="middle" fontFamily={F} fontWeight={800} fontSize={34} fill="#3FD06A">YOUR TIME</text>
        </g>
      </Glow>
    </>
  );
};

const CONTENT: Record<string, React.FC<{ t: number }>> = {
  money: MoneyScene, fight: FightScene, wants: WantsScene, swipe: SwipeScene, rich: RichScene, fried: FriedScene,
  why: WhyScene, take: TakeScene, delete: DeleteAll, keep: KeepScene, kill: KillScene, built: LogoScene, back: BackScene, cta: CtaScene,
};

/** Oben rechts: Zähler "Zeit auf Shorts" läuft hoch, bis F*ck Shorts kommt – dann 0 und grün. */
const Hud: React.FC<{ t: number }> = ({ t }) => {
  const stopAt = S('built').a;
  const done = t >= stopAt;
  const secs = done ? 0 : Math.floor(t * 600);
  const h = Math.floor(secs / 3600), m = Math.floor((secs % 3600) / 60), s = secs % 60;
  const pad = (n: number) => String(n).padStart(2, '0');
  return (
    <div style={{ position: 'absolute', right: 60, top: 40, textAlign: 'right', fontFamily: F, fontWeight: 800, color: done ? '#3FD06A' : '#fff', opacity: 0.8 }}>
      <div style={{ fontSize: 18, letterSpacing: 3 }}>TIME ON SHORTS</div>
      <div style={{ fontSize: 30 }}>{`${h}h ${pad(m)}m ${pad(s)}s`}</div>
    </div>
  );
};

const SWEAR = ['fuck', 'bitch', 'shit'];
const KEY = ['money', 'tiktok', 'instagram', 'shorts', 'gone', 'rich', 'nothing', 'fried', 'paused', 'free', 'take', 'delete', 'keep', 'kill', 'back', 'test', '12'];
const SHAKE = ws.filter((w) => SWEAR.some((s) => w[0].includes(s))).map((w) => w[1]);
const PUNCH = [...SCENES.map((s) => s.a + 0.12), ...ws.filter((w) => KEY.includes(w[0])).map((w) => w[1]), ...SHAKE];
const FLASH = [...SHAKE, ...SCENES.slice(1).map((s) => s.a + 0.12)];

export const Ad11: React.FC<{ voice: boolean }> = ({ voice }) => {
  useInter();
  const t = useCurrentFrame() / FPS11;
  const sc = SCENES.find((s) => t >= s.a && t < s.b) ?? SCENES[t < SCENES[0].a ? 0 : SCENES.length - 1];
  const C = CONTENT[sc.k];
  const pk = Math.max(0, ...PUNCH.map((p) => (t >= p ? Math.exp(-(t - p) / 0.09) : 0)));
  const sk = Math.max(0, ...SHAKE.map((p) => (t >= p && t < p + 0.32 ? 1 - (t - p) / 0.32 : 0)));
  const fl = Math.max(0, ...FLASH.map((p) => (t >= p && t < p + 0.1 ? 1 - (t - p) / 0.1 : 0)));
  const pop = 1.12 - 0.12 * ease(t, sc.a, sc.a + 0.15);
  return (
    <AbsoluteFill style={{ background: '#000', overflow: 'hidden' }}>
      {voice && <Audio src={staticFile('voice11.wav')} />}
      {voice && <Audio src={staticFile('sfx11.wav')} volume={0.4} />}
      <Backdrop t={t} />
      <AbsoluteFill style={{ transform: `translate(${sk * 22 * Math.sin(t * 97)}px, ${sk * 16 * Math.cos(t * 83)}px) scale(${pop * (1 + 0.08 * pk)})` }}>
        {sc.text && <Caption t={t} sc={sc} />}
        <C t={t} />
      </AbsoluteFill>
      {sc.k !== 'cta' && <Hud t={t} />}
      <AbsoluteFill style={{ background: '#fff', opacity: fl * 0.25, pointerEvents: 'none' }} />
      <AbsoluteFill style={{ background: `radial-gradient(70% 60% at 50% 50%, transparent 50%, rgba(229,23,43,${0.35 * sk}) 100%)`, pointerEvents: 'none' }} />
    </AbsoluteFill>
  );
};
