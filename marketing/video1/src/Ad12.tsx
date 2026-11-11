import React, { useEffect, useState } from 'react';
import { AbsoluteFill, Audio, Sequence, continueRender, delayRender, random, staticFile, useCurrentFrame } from 'remotion';
import { BarIcon, Logo, RED, ease, lin } from './Ad';
import WORDS from './words12.json';

/**
 * Werbevideo 12 "Lock in." (hochkant 9:16). Format der Vorlage nachgebaut, ohne echte Person:
 * 1,6 s Köder (bunter "lustiger Short"), dann harter Schnitt auf eine dunkle Schwarz-Weiss-Nachtszene, eine Stimme redet
 * wie ein enttäuschter Freund (Cevin, public/voice12.wav). Oben steht immer klein "Lock in.", unten Untertitel.
 */
export const FPS12 = 30;
const BAIT = 1.6;
const F = 'Inter';
const W = 1080, H = 1920;

const useInter = () => {
  const [h] = useState(() => delayRender('inter'));
  useEffect(() => {
    new FontFace(F, `url(${staticFile('inter-latin-800-normal.woff2')})`, { weight: '800' }).load().then((f) => {
      document.fonts.add(f);
      continueRender(h);
    });
  }, [h]);
};

type Wd = [string, number, number];
const ws = (WORDS as Wd[]).map(([w, a, e]) => ({ raw: w, w: w.toLowerCase().replace(/[^a-z0-9]/g, ''), a: a + BAIT, e: e + BAIT }));
export const DURATION12_S = ws[ws.length - 1].e + 2.4;
const at = (word: string, nth = 0, after = 0) => ws.filter((x) => x.w === word && x.a >= after)[nth]?.a ?? 9999;

// Untertitel: Gruppen von bis zu 3 Wörtern, neue Gruppe nach Satzzeichen oder Pause
const GROUPS = (() => {
  const out: { text: string; a: number; e: number }[] = [];
  let cur: typeof ws = [];
  const flush = () => { if (cur.length) out.push({ text: cur.map((x) => x.raw.replace(/^F-?ck$/i, 'F*ck').replace(/^fuck$/i, 'F*ck')).join(' '), a: cur[0].a, e: cur[cur.length - 1].e }); cur = []; };
  ws.forEach((x, i) => {
    const prev = ws[i - 1];
    if (cur.length && (cur.length >= 3 || (prev && x.a - prev.e > 0.25))) flush();
    cur.push(x);
    if (/[.,?!]$/.test(x.raw)) flush();
  });
  flush();
  return out;
})();

/** Köder: bunter "lustiger Short" mit tanzender Figur. */
const Bait: React.FC<{ t: number }> = ({ t }) => {
  if (t >= BAIT) return null;
  const b = Math.abs(Math.sin(t * 9));
  return (
    <AbsoluteFill style={{ background: `linear-gradient(${160 + t * 40}deg, #FF4FB0, #FFB02E 55%, #3EC6E0)` }}>
      {Array.from({ length: 40 }, (_, i) => {
        const x = random(`cx${i}`) * W, y0 = random(`cy${i}`) * H;
        return <div key={i} style={{ position: 'absolute', left: x, top: (y0 + t * 500) % H, width: 18, height: 30, background: ['#fff', '#7A3BFF', '#3FD06A', '#FF3B45'][i % 4], transform: `rotate(${t * 300 + i * 30}deg)`, borderRadius: 4 }} />;
      })}
      <svg viewBox="0 0 1080 1920" width={W} height={H} style={{ position: 'absolute', inset: 0 }}>
        <g transform={`translate(540 ${1150 - 80 * b}) rotate(${12 * Math.sin(t * 9)}) scale(${1 + 0.06 * b} ${1 - 0.06 * b})`}>
          <ellipse cx={0} cy={0} rx={230} ry={260} fill="#7A3BFF" />
          <ellipse cx={-80} cy={-70} rx={70} ry={45} fill="#111" /><ellipse cx={80} cy={-70} rx={70} ry={45} fill="#111" />
          <rect x={-12} y={-80} width={24} height={14} fill="#111" />
          <path d="M-90 70 q90 90 180 0" stroke="#fff" strokeWidth={22} fill="none" strokeLinecap="round" />
          <ellipse cx={-250} cy={40 + 60 * Math.sin(t * 18)} rx={45} ry={80} fill="#7A3BFF" />
          <ellipse cx={250} cy={40 - 60 * Math.sin(t * 18)} rx={45} ry={80} fill="#7A3BFF" />
        </g>
      </svg>
      <div style={{ position: 'absolute', left: 0, right: 0, top: 420, textAlign: 'center', fontFamily: F, fontWeight: 800, fontSize: 92, color: '#fff', textShadow: '0 6px 0 #000, 0 0 30px rgba(0,0,0,.4)' }}>wait for it 😂😂</div>
    </AbsoluteFill>
  );
};

/** Nachtszene: Lichter (unscharf), Silhouette mit Randlicht, Korn, leichtes Handkamera-Schweben. */
const Night: React.FC<{ t: number }> = ({ t }) => {
  const lt = t - BAIT;
  const phone = Math.max(ease(t, at('2') - 0.2, at('2') + 0.1) * (1 - ease(t, at('days') - 0.1, at('days') + 0.2)), 0);
  const spot = ease(t, at('could') - 0.3, at('could') + 0.3) * (1 - ease(t, at('every') - 0.2, at('every')));
  const dx = 6 * Math.sin(lt * 0.9), dy = 5 * Math.cos(lt * 0.7);
  return (
    <AbsoluteFill style={{ background: '#050505', filter: 'grayscale(1)' }}>
      <svg viewBox="0 0 1080 1920" width={W} height={H} style={{ position: 'absolute', inset: 0, transform: `translate(${dx}px, ${dy}px) scale(1.04)` }}>
        <defs>
          <filter id="blur"><feGaussianBlur stdDeviation="16" /></filter>
          <radialGradient id="rim" cx="50%" cy="40%" r="60%"><stop offset="0" stopColor="#2a2a2a" /><stop offset="1" stopColor="#0b0b0b" /></radialGradient>
          <radialGradient id="glow" cx="50%" cy="50%" r="50%"><stop offset="0" stopColor="#fff" stopOpacity="0.55" /><stop offset="1" stopColor="#fff" stopOpacity="0" /></radialGradient>
        </defs>
        <g filter="url(#blur)">
          {[[120, 520, 38], [300, 470, 26], [820, 430, 46], [960, 560, 30], [700, 380, 20], [60, 700, 22], [1010, 760, 34], [480, 360, 18]].map(([x, y, r], i) => (
            <circle key={i} cx={x} cy={y} r={r * (1 + 0.1 * Math.sin(lt * 1.3 + i))} fill="#fff" opacity={0.75} />
          ))}
          <rect x={0} y={1040} width={1080} height={40} fill="#222" />
        </g>
        <ellipse cx={540} cy={900} rx={520} ry={520} fill="url(#glow)" opacity={0.25 + 0.5 * spot} />
        {/* Silhouette: Kopf mit Mütze, Schultern */}
        <g transform="translate(540 1020)">
          <path d="M-360 900 V560 q0 -230 360 -260 q360 30 360 260 V900 Z" fill="url(#rim)" stroke="#555" strokeWidth={3} />
          <ellipse cx={0} cy={60} rx={190} ry={235} fill="#141414" stroke="#6a6a6a" strokeWidth={3} />
          <path d="M-196 -10 q-6 -200 196 -210 q202 10 196 210 q-196 -70 -392 0 z" fill="#0c0c0c" stroke="#555" strokeWidth={3} />
          <rect x={-60} y={280} width={120} height={70} fill="#141414" />
        </g>
        {/* Handy-Licht im Gesicht */}
        <g opacity={phone}>
          <path d="M540 1500 L360 1180 L720 1180 Z" fill="#fff" opacity={0.15} />
          <rect x={470} y={1420} width={140} height={250} rx={22} fill="#0a0a0a" stroke="#ddd" strokeWidth={5} />
          <rect x={482} y={1436} width={116} height={218} rx={14} fill={`rgb(${150 + 60 * Math.abs(Math.sin(lt * 7))},${150 + 60 * Math.abs(Math.sin(lt * 7))},${150 + 60 * Math.abs(Math.sin(lt * 7))})`} />
        </g>
      </svg>
      {/* Uhrzeit bei "two a.m." */}
      <div style={{ position: 'absolute', left: 0, right: 0, top: 250, textAlign: 'center', fontFamily: F, fontWeight: 800, fontSize: 64, color: '#fff', opacity: phone, letterSpacing: 4 }}>2:00 AM</div>
      {/* Tage fliegen vorbei bei "days go by" */}
      {(() => {
        const p = ease(t, at('days') - 0.1, at('days') + 1.6);
        if (p <= 0 || p >= 1) return null;
        const days = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN', 'MON', 'TUE', 'WED'];
        const d = days[Math.min(days.length - 1, Math.floor(p * days.length))];
        return <div style={{ position: 'absolute', left: 0, right: 0, top: 240, textAlign: 'center', fontFamily: F, fontWeight: 800, fontSize: 80, color: '#fff', opacity: 0.85 }}>{d}</div>;
      })()}
      {/* Korn */}
      <svg width={W} height={H} style={{ position: 'absolute', inset: 0, opacity: 0.16, mixBlendMode: 'screen' }}>
        <filter id="n"><feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed={Math.floor(t * 30) % 100} /></filter>
        <rect width={W} height={H} filter="url(#n)" />
      </svg>
    </AbsoluteFill>
  );
};

/** YouTube-Handy: langes Video → rutscht zu Shorts (bei "end up on Shorts"). */
const YtPhone: React.FC<{ t: number }> = ({ t }) => {
  const show = ease(t, at('every') - 0.1, at('every') + 0.2) * (1 - ease(t, at('that', 0, at('every')) - 0.1, at('that', 0, at('every')) + 0.15));
  if (show <= 0) return null;
  const slip = ease(t, at('end') - 0.1, at('end') + 0.5);
  return (
    <div style={{ position: 'absolute', left: 540 - 230, top: 560, width: 460, height: 860, borderRadius: 54, border: '8px solid #ddd', background: '#0b0b0b', overflow: 'hidden', opacity: show, filter: 'grayscale(1)' }}>
      <div style={{ position: 'absolute', inset: 0, transform: `translateY(${-100 * slip}%)` }}>
        <div style={{ height: '100%', padding: '40px 22px' }}>
          {[0, 1, 2].map((i) => <div key={i} style={{ height: 200, marginBottom: 26, borderRadius: 16, background: ['#666', '#555', '#777'][i], position: 'relative' }}><div style={{ position: 'absolute', right: 10, bottom: 10, fontFamily: F, fontWeight: 800, fontSize: 22, color: '#fff' }}>{['18:42', '1:02:10', '24:05'][i]}</div></div>)}
        </div>
        <div style={{ height: '100%', background: 'radial-gradient(circle at 50% 40%, #aaa, #222 80%)', position: 'relative' }}>
          <div style={{ position: 'absolute', left: '50%', top: '40%', transform: 'translate(-40%,-50%)', borderTop: '40px solid transparent', borderBottom: '40px solid transparent', borderLeft: '66px solid #fff' }} />
          <div style={{ position: 'absolute', left: 0, right: 0, bottom: 120, textAlign: 'center', fontFamily: F, fontWeight: 800, fontSize: 40, color: '#fff' }}>SHORTS</div>
        </div>
      </div>
    </div>
  );
};

/** Logo (einzige Farbe) und Leiste ohne Shorts. */
const Product: React.FC<{ t: number }> = ({ t }) => {
  const logoAt = ws.find((x) => x.a > at('built') && (x.w === 'fck' || x.w === 'fuck' || x.w === 'f'))?.a ?? at('built') + 1.2;
  const p = ease(t, logoAt - 0.1, logoAt + 0.2) * (1 - ease(t, at('comment') - 0.2, at('comment')));
  const rm = lin(t, at('takes') - 0.05, at('takes') + 0.45);
  if (p <= 0) return null;
  const kinds = ['home', 'shorts', 'plus', 'you'] as const;
  return (
    <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', gap: 70, opacity: p, background: 'rgba(0,0,0,.55)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 26, filter: 'drop-shadow(0 0 30px rgba(229,23,43,.7))', transform: `scale(${0.85 + 0.15 * p})` }}>
        <Logo size={140} />
        <div style={{ fontFamily: F, fontWeight: 800, fontSize: 92, color: '#fff' }}>F<span style={{ color: RED }}>*</span>CK SHORTS</div>
      </div>
      <div style={{ width: 760, height: 150, borderRadius: 34, border: '5px solid #fff', display: 'flex', justifyContent: 'space-around', alignItems: 'center', opacity: ease(t, at('takes') - 0.3, at('takes')) }}>
        {kinds.map((k) => (k === 'shorts' && rm >= 1 ? null : (
          <div key={k} style={{ width: 90, height: 90, display: 'flex', alignItems: 'center', justifyContent: 'center', transform: k === 'shorts' ? `scale(${1.5 * (1 - rm)}) rotate(${rm * 90}deg)` : 'scale(1.5)', opacity: k === 'shorts' ? 1 - rm : 1 }}>
            <BarIcon kind={k} color={k === 'shorts' ? RED : '#fff'} />
          </div>
        )))}
      </div>
    </AbsoluteFill>
  );
};

const Cta: React.FC<{ t: number }> = ({ t }) => {
  const a = ease(t, at('comment') - 0.2, at('comment'));
  if (a <= 0) return null;
  const sign = ease(t, at('sign') - 0.05, at('sign') + 0.2);
  const tw = ease(t, at('twelve') < 9999 ? at('twelve') : at('12'), (at('twelve') < 9999 ? at('twelve') : at('12')) + 0.2);
  const lock = ease(t, at('lock', 0, at('comment')) - 0.05, at('lock', 0, at('comment')) + 0.2);
  return (
    <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', gap: 40, background: `rgba(0,0,0,${0.75 * a})` }}>
      <div style={{ opacity: a, transform: `scale(${0.85 + 0.15 * a})`, background: '#fff', color: '#000', fontFamily: F, fontWeight: 800, fontSize: 76, padding: '24px 64px', borderRadius: 80 }}>COMMENT "TEST"</div>
      <div style={{ opacity: sign, transform: `scale(${0.85 + 0.15 * sign})`, background: RED, color: '#fff', fontFamily: F, fontWeight: 800, fontSize: 58, padding: '20px 54px', borderRadius: 80, boxShadow: `0 0 30px ${RED}` }}>SIGN UP 👉 LINK IN BIO</div>
      <div style={{ opacity: tw, fontFamily: F, fontWeight: 800, fontSize: 40, color: '#ddd', textAlign: 'center' }}>WE NEED 12 TESTERS<br />COMING SOON TO GOOGLE PLAY</div>
      <div style={{ opacity: lock, marginTop: 60, fontFamily: F, fontWeight: 800, fontSize: 110, color: '#fff' }}>Lock in.</div>
    </AbsoluteFill>
  );
};

const Subs: React.FC<{ t: number }> = ({ t }) => {
  if (t >= at('comment') - 0.2) return null;
  const g = [...GROUPS].reverse().find((x) => t >= x.a - 0.05);
  if (!g || t > g.e + 0.5) return null;
  return (
    <div style={{ position: 'absolute', left: 60, right: 60, top: 470, textAlign: 'center', fontFamily: F, fontWeight: 800, fontSize: 68, color: '#fff', textShadow: '0 4px 14px rgba(0,0,0,.9)', lineHeight: 1.15 }}>{g.text}</div>
  );
};

export const Ad12: React.FC<{ voice: boolean }> = ({ voice }) => {
  useInter();
  const t = useCurrentFrame() / FPS12;
  return (
    <AbsoluteFill style={{ background: '#000', overflow: 'hidden' }}>
      {voice && <Sequence from={Math.round(BAIT * FPS12)}><Audio src={staticFile('voice12.wav')} /></Sequence>}
      {voice && <Audio src={staticFile('sfx12.wav')} volume={0.45} />}
      {t >= BAIT && (
        <>
          <Night t={t} />
          <YtPhone t={t} />
          <div style={{ position: 'absolute', left: 0, right: 0, top: 1390, textAlign: 'center', fontFamily: F, fontWeight: 800, fontSize: 42, color: '#fff', opacity: 0.9, textShadow: '0 2px 10px #000' }}>Lock in.</div>
          <Subs t={t} />
          <Product t={t} />
          <Cta t={t} />
        </>
      )}
      <Bait t={t} />
    </AbsoluteFill>
  );
};
