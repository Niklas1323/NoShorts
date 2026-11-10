import React, { useEffect, useState } from 'react';
import { AbsoluteFill, Audio, continueRender, delayRender, random, staticFile, useCurrentFrame } from 'remotion';
import { BarIcon, Logo, RED, ease, lin } from './Ad';
import WORDS from './words10.json';

/**
 * Werbevideo 10 = Video 9 Fassung 2 (quer 16:9): wütend, schneller, mehr Dopamin; alle Social Media löschen ausser YouTube. Stil der Vorlage: schwarz, Perspektiv-Gitter, weisse Linien-Symbole mit
 * Leuchten, Satz oben, Wörter werden beim Sprechen hell. Anfang = derselbe Satz wie die Vorlage, dann F*ck Shorts.
 * Szenen-Zeiten kommen aus den Wortzeiten der Aufnahme (words10.json, Anker = erstes Wort der Szene).
 */
export const FPS10 = 30;
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
  { k: 'lock', anchor: 'lock', text: 'LOCK THE F*CK IN,|HOE *SS B*TCH M*THERF*CKER.' },
  { k: 'tiktok', anchor: 'delete', text: 'DELETE TIKTOK.' },
  { k: 'insta', anchor: 'delete', text: 'DELETE INSTAGRAM.' },
  { k: 'snap', anchor: 'delete', text: 'DELETE SNAPCHAT.' },
  { k: 'all', anchor: 'delete', text: 'DELETE ALL THAT SH*T!' },
  { k: 'keep', anchor: 'but', text: 'BUT YOUTUBE? KEEP IT.' },
  { k: 'teach', anchor: 'long', text: 'LONG VIDEOS ACTUALLY|TEACH YOU SOMETHING.' },
  { k: 'shoved', anchor: 'but', text: 'BUT YOUTUBE SHOVED|SHORTS RIGHT IN THERE.' },
  { k: 'youtube', anchor: 'and', text: "AND THERE'S NO OFF SWITCH!" },
  { k: 'built', anchor: 'so', text: 'SO I BUILT ONE.' },
  { k: 'gone', anchor: 'button', text: 'SHORTS BUTTON? GONE.|LONG VIDEOS? STAY.', lead: 0.45 },
  { k: 'swipe', anchor: 'every', text: 'EVERY SWIPE STEALS A MINUTE.|STOP GIVING THEM AWAY!' },
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
export const DURATION10_S = SCENES[SCENES.length - 1].b;
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

const Lock: React.FC<{ t: number }> = ({ t }) => {
  const shut = ease(t, at('lock', 'in') - 0.05, at('lock', 'in') + 0.15);
  return (
    <Glow>
      <g transform="translate(960 620)">
        <path d={`M-70 0 V${-70 + 30 * shut - 30} a70 70 0 0 1 140 0 V0`} {...LINE} strokeWidth={9} transform={`translate(0 ${-30 * (1 - shut)})`} />
        <rect x={-120} y={0} width={240} height={200} rx={26} {...LINE} strokeWidth={9} />
        <circle cx={0} cy={80} r={16} fill="#fff" /><line x1={0} y1={90} x2={0} y2={135} {...LINE} strokeWidth={8} />
      </g>
    </Glow>
  );
};

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

const ToggleScene: React.FC<{ t: number }> = ({ t }) => {
  const sc = S('youtube');
  const lt = t - sc.a;
  const tryP = (s: number) => (lt > s && lt < s + 0.45 ? Math.sin(((lt - s) / 0.45) * Math.PI) : 0);
  const knob = Math.max(tryP(0.9), tryP(1.6)) * 0.35;
  return (
    <Glow>
      <g transform="translate(960 640)">
        <rect x={-200} y={-90} width={400} height={180} rx={90} {...LINE} strokeWidth={8} />
        <circle cx={110 - 220 * knob} cy={0} r={66} fill="#fff" />
        <text x={-110} y={14} textAnchor="middle" fontFamily={F} fontWeight={800} fontSize={44} fill="#fff">ON</text>
        <g transform="translate(250 -120)">
          <path d="M-30 0 V-26 a30 30 0 0 1 60 0 V0" {...LINE} strokeWidth={7} />
          <rect x={-46} y={0} width={92} height={74} rx={12} {...LINE} strokeWidth={7} />
        </g>
      </g>
      <text x={960} y={830} textAnchor="middle" fontFamily={F} fontWeight={800} fontSize={30} fill="#fff" opacity={0.7}>SHORTS</text>
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

const GoneScene: React.FC<{ t: number }> = ({ t }) => {
  const goneAt = at('gone', 'gone');
  const rm = lin(t, goneAt - 0.05, goneAt + 0.35);
  const vids = ease(t, at('gone', 'long') - 0.1, at('gone', 'long') + 0.3);
  const kinds = ['home', 'shorts', 'plus', 'you'] as const;
  return (
    <>
      <Glow>
        <g transform="translate(960 560)">
          {[-1, 0, 1].map((i) => (
            <g key={i} transform={`translate(${i * 400} ${30 * (1 - vids)})`} opacity={vids}>
              <rect x={-170} y={-100} width={340} height={190} rx={18} {...LINE} strokeWidth={6} />
              <path d="M-20 -30 L30 0 L-20 30 Z" fill="#fff" />
              <text x={140} y={70} textAnchor="end" fontFamily={F} fontWeight={800} fontSize={24} fill="#fff">{['18:42', '1:02:10', '24:05'][i + 1]}</text>
            </g>
          ))}
        </g>
      </Glow>
      <div style={{ position: 'absolute', left: 960 - 340, top: 820, width: 680, height: 120, borderRadius: 30, border: '5px solid #fff', boxShadow: GLOW, display: 'flex', justifyContent: 'space-around', alignItems: 'center' }}>
        {kinds.map((kd) => (kd === 'shorts' && rm >= 1 ? null : (
          <div key={kd} style={{ width: 70, height: 70, display: 'flex', alignItems: 'center', justifyContent: 'center', transform: kd === 'shorts' ? `scale(${1.25 * (1 - rm)}) rotate(${rm * 90}deg)` : 'scale(1.25)', opacity: kd === 'shorts' ? 1 - rm : 1, filter: 'drop-shadow(0 0 8px rgba(255,255,255,.7))' }}>
            <BarIcon kind={kd} color="#fff" />
          </div>
        )))}
      </div>
      {rm > 0 && rm < 1 && Array.from({ length: 12 }, (_, i) => {
        const a = (i / 12) * Math.PI * 2;
        return <div key={i} style={{ position: 'absolute', left: 960 - 105 + Math.cos(a) * 120 * rm, top: 880 + Math.sin(a) * 70 * rm, width: 10, height: 10, background: '#fff', opacity: 1 - rm, boxShadow: GLOW }} />;
      })}
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

const AppDelete: React.FC<{ t: number; k: string; name: string }> = ({ t, k, name }) => {
  const sc = S(k);
  const cross = ease(t, sc.a + 0.15, sc.a + 0.45);
  const fall = Math.max(0, t - sc.a - 0.6);
  return <Glow><AppTile x={960} y={620} s={1.3} name={name} cross={cross} fall={fall} /></Glow>;
};

const AllScene: React.FC<{ t: number }> = ({ t }) => {
  const sc = S('all');
  const boom = at('all', 'shit') || sc.a + 0.6;
  const p = lin(t, boom, boom + 0.7);
  const names = ['TIKTOK', 'INSTA', 'SNAP', 'X', 'REELS', 'BEREAL', 'FB', 'THREADS'];
  return (
    <Glow>
      {names.map((n, i) => {
        const col = i % 4, row = Math.floor(i / 4);
        const x0 = 450 + col * 340, y0 = 520 + row * 280;
        const ang = random(`b${i}`) * Math.PI * 2;
        return (
          <g key={i} transform={`translate(${x0 + Math.cos(ang) * 500 * p} ${y0 + Math.sin(ang) * 300 * p + 600 * p * p}) rotate(${(random(`r${i}`) - 0.5) * 300 * p}) scale(0.62)`} opacity={1 - p}>
            <rect x={-110} y={-110} width={220} height={220} rx={52} {...LINE} strokeWidth={9} />
            <text x={0} y={16} textAnchor="middle" fontFamily={F} fontWeight={800} fontSize={n.length > 5 ? 34 : 44} fill="#fff">{n}</text>
          </g>
        );
      })}
    </Glow>
  );
};

const KeepScene: React.FC<{ t: number }> = ({ t }) => {
  const sc = S('keep');
  const ok = ease(t, at('keep', 'keep') - 0.05, at('keep', 'keep') + 0.2);
  return (
    <Glow>
      <g transform={`translate(960 640) scale(${1 + 0.08 * ok})`}>
        <rect x={-200} y={-140} width={400} height={280} rx={60} {...LINE} strokeWidth={9} />
        <path d="M-40 -60 L70 0 L-40 60 Z" fill="#fff" />
        <text x={0} y={210} textAnchor="middle" fontFamily={F} fontWeight={800} fontSize={42} fill="#fff">YOUTUBE</text>
      </g>
      <g transform="translate(1260 470)" opacity={ok}>
        <circle r={70} fill="none" stroke="#3FD06A" strokeWidth={9} />
        <path d="M-32 0 L-8 26 L36 -24" fill="none" stroke="#3FD06A" strokeWidth={11} strokeLinecap="round" strokeLinejoin="round" />
      </g>
    </Glow>
  );
};

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

const ShovedScene: React.FC<{ t: number }> = ({ t }) => {
  const sc = S('shoved');
  const inP = ease(t, at('shoved', 'shorts') - 0.05, at('shoved', 'shorts') + 0.25);
  const pulse = 1 + 0.12 * Math.max(0, Math.sin((t - sc.a) * 12));
  const kinds = ['home', 'shorts', 'plus', 'you'] as const;
  return (
    <div style={{ position: 'absolute', left: 960 - 380, top: 620, width: 760, height: 150, borderRadius: 34, border: '6px solid #fff', boxShadow: GLOW, display: 'flex', justifyContent: 'space-around', alignItems: 'center' }}>
      {kinds.map((kd) => {
        const isS = kd === 'shorts';
        return (
          <div key={kd} style={{ width: 90, height: 90, display: 'flex', alignItems: 'center', justifyContent: 'center', transform: isS ? `translateY(${-260 * (1 - inP)}px) scale(${1.5 * pulse})` : 'scale(1.4)', opacity: isS ? inP : 1, filter: isS ? `drop-shadow(0 0 16px ${RED})` : 'drop-shadow(0 0 8px rgba(255,255,255,.7))' }}>
            <BarIcon kind={kd} color={isS ? RED : '#fff'} />
          </div>
        );
      })}
    </div>
  );
};

const CONTENT: Record<string, React.FC<{ t: number }>> = {
  lock: Lock,
  tiktok: ({ t }) => <AppDelete t={t} k="tiktok" name="TIKTOK" />,
  insta: ({ t }) => <AppDelete t={t} k="insta" name="INSTAGRAM" />,
  snap: ({ t }) => <AppDelete t={t} k="snap" name="SNAPCHAT" />,
  all: AllScene, keep: KeepScene, teach: TeachScene, shoved: ShovedScene,
  youtube: ToggleScene, built: BuiltScene, gone: GoneScene, swipe: SwipeScene, cta: CtaScene,
};

// Dopamin: Stoss auf jedem Szenenwechsel + Schlüsselwörtern, Wackeln + Blitz + Rot bei Schimpfwörtern
const SWEAR = ['fuck', 'bitch', 'motherfucker', 'shit', 'fck', 'no'];
const KEY = ['delete', 'keep', 'shorts', 'gone', 'stay', 'stop', 'test', 'twelve', '12', 'switch', 'built'];
const SHAKE = ws.filter((w) => SWEAR.some((s) => w[0].includes(s))).map((w) => w[1]);
const PUNCH = [...SCENES.map((s) => s.a + 0.12), ...ws.filter((w) => KEY.includes(w[0])).map((w) => w[1]), ...SHAKE];
const FLASH = [...SHAKE, ...SCENES.slice(1).map((s) => s.a + 0.12)];

export const Ad10: React.FC<{ voice: boolean }> = ({ voice }) => {
  useInter();
  const t = useCurrentFrame() / FPS10;
  const sc = SCENES.find((s) => t >= s.a && t < s.b) ?? SCENES[t < SCENES[0].a ? 0 : SCENES.length - 1];
  const C = CONTENT[sc.k];
  const pk = Math.max(0, ...PUNCH.map((p) => (t >= p ? Math.exp(-(t - p) / 0.09) : 0)));
  const sk = Math.max(0, ...SHAKE.map((p) => (t >= p && t < p + 0.32 ? 1 - (t - p) / 0.32 : 0)));
  const fl = Math.max(0, ...FLASH.map((p) => (t >= p && t < p + 0.1 ? 1 - (t - p) / 0.1 : 0)));
  const pop = 1.12 - 0.12 * ease(t, sc.a, sc.a + 0.15);
  return (
    <AbsoluteFill style={{ background: '#000', overflow: 'hidden' }}>
      {voice && <Audio src={staticFile('voice10.wav')} />}
      {voice && <Audio src={staticFile('sfx10.wav')} volume={0.4} />}
      <Backdrop t={t} />
      <AbsoluteFill style={{ transform: `translate(${sk * 22 * Math.sin(t * 97)}px, ${sk * 16 * Math.cos(t * 83)}px) scale(${pop * (1 + 0.08 * pk)})` }}>
        {sc.text && <Caption t={t} sc={sc} />}
        <C t={t} />
      </AbsoluteFill>
      <AbsoluteFill style={{ background: '#fff', opacity: fl * 0.25, pointerEvents: 'none' }} />
      <AbsoluteFill style={{ background: `radial-gradient(70% 60% at 50% 50%, transparent 50%, rgba(229,23,43,${0.35 * sk}) 100%)`, pointerEvents: 'none' }} />
    </AbsoluteFill>
  );
};
