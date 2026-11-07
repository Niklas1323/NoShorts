import React from 'react';
import { AbsoluteFill, Audio, random, staticFile, useCurrentFrame } from 'remotion';
import { BarIcon, Logo, RED, ease, lin, useFonts } from './Ad';

/**
 * Werbevideo 4 "Stop giving a f*ck" (quer 16:9, schwarz, weiss leuchtend, rote Akzente). Eigene Stimme (Jerry B.,
 * public/voice4.wav), eigene Zeichnungen im minimalistischen Stil. Zeiten = Wortzeiten der Aufnahme (Sekunden).
 */
export const FPS4 = 30;
export const DURATION4_S = 46.5;
const W = 1920;
const H = 1080;
const F = 'Montserrat';
const GLOW = '0 0 18px rgba(255,255,255,.55), 0 0 42px rgba(255,255,255,.25)';
const RGLOW = `0 0 18px ${RED}, 0 0 46px rgba(229,23,43,.6)`;

const sc = (t: number, a: number, b: number, f = 0.25) => Math.min(lin(t, a - 0.01, a + f), lin(t, b - f, b, 1, 0));

const Txt: React.FC<{ s: number; c?: string; glow?: string; ls?: number; style?: React.CSSProperties; children: React.ReactNode }> = ({ s, c = '#fff', glow = GLOW, ls = 0.18, style, children }) => (
  <div style={{ fontFamily: F, fontWeight: 900, fontSize: s, color: c, letterSpacing: s * ls, textShadow: glow, textAlign: 'center', whiteSpace: 'nowrap', ...style }}>{children}</div>
);

const Pill: React.FC<{ text: string; top: number; o?: number; red?: boolean }> = ({ text, top, o = 1, red }) => (
  <div style={{ position: 'absolute', top, left: '50%', transform: 'translateX(-50%)', opacity: o, background: red ? RED : '#fff', color: red ? '#fff' : '#000', fontFamily: F, fontWeight: 800, fontSize: 22, letterSpacing: 4, padding: '10px 26px', borderRadius: 40, whiteSpace: 'nowrap', boxShadow: red ? RGLOW : '0 0 24px rgba(255,255,255,.35)' }}>
    {text}
  </div>
);

/** Männchen: Kopf + Oberkörper. */
const Person: React.FC<{ x: number; y: number; s?: number; c?: string; glow?: boolean; o?: number }> = ({ x, y, s = 1, c = '#8a8a8a', glow, o = 1 }) => (
  <div style={{ position: 'absolute', left: x - 30 * s, top: y - 40 * s, width: 60 * s, height: 80 * s, opacity: o, filter: glow ? 'drop-shadow(0 0 14px rgba(255,255,255,.8))' : undefined }}>
    <div style={{ position: 'absolute', left: 17 * s, top: 0, width: 26 * s, height: 26 * s, borderRadius: '50%', background: c }} />
    <div style={{ position: 'absolute', left: 6 * s, top: 32 * s, width: 48 * s, height: 46 * s, borderRadius: `${22 * s}px ${22 * s}px ${6 * s}px ${6 * s}px`, background: c }} />
  </div>
);

const Bubble: React.FC<{ x: number; y: number; text: string; p: number; red?: boolean }> = ({ x, y, text, p, red }) => (
  <div style={{ position: 'absolute', left: x, top: y, transform: `translate(-50%,-50%) scale(${0.6 + 0.4 * p})`, opacity: p, border: `3px solid ${red ? RED : '#fff'}`, color: red ? RED : '#fff', borderRadius: 30, padding: '8px 18px', fontFamily: F, fontWeight: 800, fontSize: 18, letterSpacing: 2, background: '#000', boxShadow: red ? RGLOW : undefined }}>
    {text}
  </div>
);

const Phone: React.FC<{ x: number; y: number; s?: number; red?: number; children?: React.ReactNode; o?: number }> = ({ x, y, s = 1, red = 0, children, o = 1 }) => (
  <div style={{ position: 'absolute', left: x - 110 * s, top: y - 200 * s, width: 220 * s, height: 400 * s, borderRadius: 34 * s, border: `${5 * s}px solid ${red > 0.5 ? RED : '#fff'}`, boxShadow: red > 0.5 ? RGLOW : GLOW, overflow: 'hidden', opacity: o, background: '#050505' }}>
    {children}
  </div>
);

// ------------------------------------------------------------------------------------------- Szenen

/** 0–1.7: STOP. GIVING A F*CK */
const S1: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 0, 1.75, 0.15);
  if (a <= 0) return null;
  const w = (st: number) => ease(t, st, st + 0.18);
  return (
    <AbsoluteFill style={{ opacity: a, alignItems: 'center', justifyContent: 'center', gap: 6 }}>
      <Txt s={130} style={{ opacity: w(0), transform: `scale(${1.25 - 0.25 * w(0)})` }}>STOP.</Txt>
      <Txt s={96} style={{ opacity: w(0.28) }}>GIVING</Txt>
      <Txt s={130} glow={RGLOW} style={{ opacity: w(0.76), transform: `scale(${1.3 - 0.3 * w(0.76)})` }}>A F<span style={{ color: RED }}>*</span>CK</Txt>
    </AbsoluteFill>
  );
};

/** 1.7–4.6: Was die anderen sagen. */
const S2: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 1.7, 4.65);
  if (a <= 0) return null;
  const crowd = [
    [430, 600, 'NAH'], [560, 560, 'LAME'], [690, 610, 'BLAH'], [1230, 610, 'LOL'], [1360, 560, 'WHO?'], [1490, 600, 'BRO??'],
  ] as const;
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Pill text="WHAT THEY HAVE TO SAY" top={170} o={ease(t, 1.8, 2.1)} />
      <Person x={960} y={600} s={1.4} c="#fff" glow />
      <Txt s={26} style={{ position: 'absolute', left: 0, right: 0, top: 690 }}>YOU</Txt>
      {crowd.map(([x, y, b], i) => {
        const p = ease(t, 2.0 + i * 0.22, 2.3 + i * 0.22);
        const shake = t > 2.24 && t < 3.0 ? Math.sin(t * 60 + i) * 3 : 0;
        return (
          <React.Fragment key={i}>
            <Person x={x} y={y + shake} o={0.35 + 0.65 * p} />
            <Bubble x={x + 30} y={y - 80 + shake} text={b} p={p} red={i === 5} />
          </React.Fragment>
        );
      })}
    </AbsoluteFill>
  );
};

/** 4.7–9.2: Lichtkegel, THE ONLY ONE. */
const S3: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 4.65, 9.25);
  if (a <= 0) return null;
  const cone = ease(t, 4.8, 5.6);
  const ring = Array.from({ length: 12 }, (_, i) => {
    const ang = (i / 12) * Math.PI * 2;
    return [960 + Math.cos(ang) * 470, 560 + Math.sin(ang) * 260];
  });
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Txt s={64} style={{ position: 'absolute', left: 0, right: 0, top: 170, opacity: ease(t, 5.42, 5.75) }}>THE ONLY ONE</Txt>
      {ring.map(([x, y], i) => <Person key={i} x={x} y={y} s={0.7} c="#3a3a3a" o={0.4 + 0.6 * (1 - cone)} />)}
      <div style={{ position: 'absolute', left: 960 - 240 * cone, top: 270, width: 480 * cone, height: 420, opacity: cone * 0.9, clipPath: 'polygon(42% 0, 58% 0, 100% 100%, 0 100%)', background: 'linear-gradient(180deg, rgba(255,255,255,.04), rgba(255,255,255,.28))' }} />
      <div style={{ position: 'absolute', left: 960 - 240, top: 665, width: 480, height: 60, borderRadius: '50%', background: 'rgba(255,255,255,.18)', opacity: cone }} />
      <Person x={960} y={640} s={1.5} c="#fff" glow />
      <Txt s={26} style={{ position: 'absolute', left: 0, right: 0, top: 790, opacity: ease(t, 7.18, 7.5), color: '#ccc' }}>WHO GETS TO EXPERIENCE</Txt>
      <Txt s={40} style={{ position: 'absolute', left: 0, right: 0, top: 835, opacity: ease(t, 8.38, 8.7) }}>BEING YOU</Txt>
    </AbsoluteFill>
  );
};

const Diamond: React.FC<{ x: number; y: number; s: number; drain?: number }> = ({ x, y, s, drain = 0 }) => (
  <svg style={{ position: 'absolute', left: x - 120 * s, top: y - 100 * s, filter: `drop-shadow(0 0 ${20 * (1 - drain) + 4}px ${drain > 0.5 ? RED : '#fff'})` }} width={240 * s} height={200 * s} viewBox="0 0 120 100">
    <path d="M25 5 L95 5 L115 32 L60 95 L5 32 Z" fill={drain > 0 ? `rgba(255,255,255,${0.95 - 0.8 * drain})` : '#fff'} stroke="#fff" strokeWidth="2" />
    <path d="M5 32 L115 32 M25 5 L42 32 L60 95 L78 32 L95 5 M42 32 L60 5 L78 32" fill="none" stroke="#000" strokeWidth="2" />
  </svg>
);

/** 9.3–12.4: VALUE THAT OVER ANYTHING. */
const S4: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 9.25, 12.45);
  if (a <= 0) return null;
  const d = ease(t, 9.3, 9.8);
  const items = Array.from({ length: 8 }, (_, i) => {
    const ang = (i / 8) * Math.PI * 2 + t * 0.25;
    return { x: 960 + Math.cos(ang) * 420, y: 560 + Math.sin(ang) * 250, k: i % 3 };
  });
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Pill text="VALUE THAT OVER ANYTHING" top={170} o={ease(t, 9.8, 10.1)} />
      {items.map((it, i) => (
        <div key={i} style={{ position: 'absolute', left: it.x - 26, top: it.y - 26, width: 52, height: 52, opacity: 0.55 * ease(t, 10.4 + i * 0.08, 10.7 + i * 0.08), border: '3px solid #777', borderRadius: it.k === 0 ? '50%' : 10, color: '#999', fontFamily: F, fontWeight: 900, fontSize: 26, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          {it.k === 0 ? '$' : ''}
        </div>
      ))}
      <div style={{ opacity: d, transform: `scale(${0.7 + 0.3 * d})`, transformOrigin: '960px 560px', position: 'absolute', inset: 0 }}>
        <Diamond x={960} y={540} s={1.2} />
      </div>
      <Txt s={30} style={{ position: 'absolute', left: 0, right: 0, top: 690, opacity: d }}>YOU</Txt>
    </AbsoluteFill>
  );
};

/** 12.4–15.1: SO WHY THE F*CK ARE YOU GIVING IT TO AN APP? – der Diamant fliesst ins rote Handy. */
const S5: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 12.45, 15.15);
  if (a <= 0) return null;
  const ph = ease(t, 13.3, 13.8);
  const drain = lin(t, 13.66, 14.7);
  const dots = Array.from({ length: 14 }, (_, i) => {
    const p = ((t * 1.6 + i / 14) % 1);
    return { x: 760 + (1180 - 760) * p, y: 560 - Math.sin(p * Math.PI) * 90, o: drain > 0 ? 1 : 0 };
  });
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Txt s={58} style={{ position: 'absolute', left: 0, right: 0, top: 150 }}>SO WHY THE F<span style={{ color: RED }}>*</span>CK</Txt>
      <Txt s={34} style={{ position: 'absolute', left: 0, right: 0, top: 235, opacity: ease(t, 13.4, 13.7), color: '#ddd' }}>ARE YOU GIVING IT TO AN APP?</Txt>
      <Diamond x={720} y={560} s={0.9 * (1 - 0.6 * drain)} drain={drain} />
      {dots.map((d, i) => <div key={i} style={{ position: 'absolute', left: d.x - 5, top: d.y - 5, width: 10, height: 10, borderRadius: '50%', background: '#fff', opacity: d.o * 0.9, boxShadow: GLOW }} />)}
      <div style={{ opacity: ph, transform: `translateX(${60 * (1 - ph)}px)`, position: 'absolute', inset: 0 }}>
        <Phone x={1230} y={560} s={0.95} red={1}>
          <div style={{ position: 'absolute', inset: 0, background: `radial-gradient(circle at 50% 50%, rgba(229,23,43,${0.2 + 0.5 * drain}), transparent 70%)` }} />
        </Phone>
      </div>
    </AbsoluteFill>
  );
};

/** 15.2–20.9: 15 SECONDS. SWIPE. … 3 HOURS. GONE. */
const S6: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 15.15, 20.95);
  if (a <= 0) return null;
  const speed = t < 19.1 ? 1 : 1 + (t - 19.1) * 4;
  const scroll = (t - 15.2) * 260 * speed;
  const gone = ease(t, 20.14, 20.6);
  const hours = ease(t, 19.12, 19.5);
  const swipeAt = [16.3, 18.26];
  const flash = Math.max(...swipeAt.map((s) => (t > s && t < s + 0.35 ? 1 - (t - s) / 0.35 : 0)));
  const clockA = ((t - 15.2) * 2.2 * speed) % (Math.PI * 2);
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <div style={{ position: 'absolute', inset: 0, opacity: 1 - gone, transform: `scale(${1 - 0.15 * gone})` }}>
        <Phone x={760} y={560} s={1.05} red={hours > 0.5 ? 1 : 0}>
          {Array.from({ length: 8 }, (_, i) => {
            const y = ((i * 400 - scroll) % 3200 + 3200) % 3200 - 400;
            return <div key={i} style={{ position: 'absolute', left: 14, right: 14, top: y + 14, height: 372, borderRadius: 20, background: `rgba(255,255,255,${0.08 + 0.05 * (i % 2)})` }} />;
          })}
        </Phone>
        <svg style={{ position: 'absolute', left: 1080, top: 380 }} width="280" height="280" viewBox="0 0 100 100">
          <circle cx="50" cy="50" r="44" fill="none" stroke={hours > 0.5 ? RED : '#fff'} strokeWidth="4" />
          <line x1="50" y1="50" x2={50 + 32 * Math.sin(clockA)} y2={50 - 32 * Math.cos(clockA)} stroke={hours > 0.5 ? RED : '#fff'} strokeWidth="4" strokeLinecap="round" />
          <line x1="50" y1="50" x2={50 + 20 * Math.sin(clockA / 12)} y2={50 - 20 * Math.cos(clockA / 12)} stroke={hours > 0.5 ? RED : '#fff'} strokeWidth="5" strokeLinecap="round" />
        </svg>
        <Txt s={44} style={{ position: 'absolute', left: 1020, width: 400, top: 690, opacity: t < 19.1 ? ease(t, 15.22, 15.5) : 0 }}>15 SECONDS</Txt>
        <Txt s={74} c={RED} glow={RGLOW} style={{ position: 'absolute', left: 1020, width: 400, top: 680, opacity: hours, transform: `scale(${1.3 - 0.3 * hours})` }}>3 HOURS</Txt>
        <Txt s={40} style={{ position: 'absolute', left: 560, width: 400, top: 170, opacity: flash }}>SWIPE ↑</Txt>
      </div>
      <Txt s={150} style={{ position: 'absolute', left: 0, right: 0, top: 440, opacity: gone }}>GONE.</Txt>
    </AbsoluteFill>
  );
};

/** 21.0–24.9: DELETE TIKTOK / INSTAGRAM / ALL THAT SH*T. */
const S7: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 20.95, 24.9);
  if (a <= 0) return null;
  const apps = [
    { name: 'TIKTOK', at: 21.12, x: 560 },
    { name: 'INSTAGRAM', at: 22.18, x: 960 },
    { name: 'ALL THAT SH*T', at: 23.24, x: 1360 },
  ];
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Txt s={72} c={RED} glow={RGLOW} style={{ position: 'absolute', left: 0, right: 0, top: 160 }}>DELETE</Txt>
      {apps.map((ap, i) => {
        const inn = ease(t, 20.95 + i * 0.08, 21.3 + i * 0.08);
        const x = ease(t, ap.at, ap.at + 0.25);
        const fall = Math.max(0, t - ap.at - 0.45);
        return (
          <div key={i} style={{ position: 'absolute', left: ap.x - 130, top: 420 + fall * fall * 900, width: 260, opacity: inn * (1 - lin(fall, 0.3, 0.6)), transform: `rotate(${fall * 40 * (i - 1 || 1)}deg)` }}>
            <div style={{ width: 200, height: 200, margin: '0 auto', borderRadius: 48, border: '5px solid #fff', boxShadow: GLOW, position: 'relative' }}>
              <svg style={{ position: 'absolute', inset: -10 }} width="220" height="220" viewBox="0 0 220 220">
                <line x1="40" y1="40" x2={40 + 140 * x} y2={40 + 140 * x} stroke={RED} strokeWidth="16" strokeLinecap="round" />
                <line x1="180" y1="40" x2={180 - 140 * x} y2={40 + 140 * x} stroke={RED} strokeWidth="16" strokeLinecap="round" />
              </svg>
            </div>
            <Txt s={26} style={{ marginTop: 24 }}>{ap.name}</Txt>
          </div>
        );
      })}
    </AbsoluteFill>
  );
};

/** Leiste im Stil einer Video-App: Home, Shorts, Plus, You. */
const Bar: React.FC<{ x: number; y: number; shortsRed: number; remove?: number; w?: number }> = ({ x, y, shortsRed, remove = 0, w = 760 }) => {
  const kinds = ['home', 'shorts', 'plus', 'you'] as const;
  const n = 4 - remove;
  return (
    <div style={{ position: 'absolute', left: x - w / 2, top: y - 70, width: w, height: 140, borderRadius: 36, border: '4px solid #fff', boxShadow: GLOW, background: '#060606' }}>
      {kinds.map((k, i) => {
        const isS = k === 'shorts';
        const idx = i < 1 ? i : isS ? 1 : i - remove;
        const cx = (w / n) * (idx + 0.5);
        const sp = isS ? 1 - remove : 1;
        const col = isS && shortsRed > 0.5 ? RED : '#fff';
        return (
          <div key={k} style={{ position: 'absolute', left: (isS ? (w / 4) * 1.5 : cx) - 48, top: 22, width: 96, height: 96, display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: sp, transform: `scale(${isS ? (1 + 0.35 * shortsRed) * (0.4 + 0.6 * sp) : 1.25}) rotate(${isS ? remove * 40 : 0}deg)`, filter: isS && shortsRed > 0.5 ? `drop-shadow(0 0 16px ${RED})` : 'drop-shadow(0 0 6px rgba(255,255,255,.5))' }}>
            <BarIcon kind={k} color={col} />
          </div>
        );
      })}
    </div>
  );
};

/** 24.9–32.5: YouTube öffnen … THERE IT IS. Kein Ausschalter. */
const S8: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 24.9, 32.55);
  if (a <= 0) return null;
  const vid = ease(t, 25.8, 26.3);
  const red = ease(t, 29.1, 29.3);
  const pulse = red > 0 ? 1 + 0.06 * Math.sin(t * 12) : 1;
  const sw = t > 30.0;
  // Schalter: springt 2x fast um und wieder zurück
  const tryP = (s: number) => (t > s && t < s + 0.5 ? Math.sin(((t - s) / 0.5) * Math.PI) : 0);
  const knob = Math.max(tryP(30.6), tryP(31.3)) * 0.6;
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <div style={{ position: 'absolute', inset: 0, opacity: sw ? 1 - ease(t, 30.0, 30.3) : 1 }}>
        <Txt s={50} style={{ position: 'absolute', left: 0, right: 0, top: 150, opacity: ease(t, 25.8, 26.1) }}>YOU OPEN YOUTUBE</Txt>
        <div style={{ position: 'absolute', left: 960 - 280, top: 270, width: 560, height: 315, borderRadius: 26, border: '4px solid #fff', boxShadow: GLOW, opacity: vid }}>
          <div style={{ position: 'absolute', left: 250, top: 120, borderTop: '38px solid transparent', borderBottom: '38px solid transparent', borderLeft: '64px solid #fff' }} />
          <Txt s={22} style={{ position: 'absolute', left: 0, right: 0, bottom: -44, color: '#bbb' }}>ONE VIDEO</Txt>
        </div>
        <div style={{ position: 'absolute', inset: 0, transform: `scale(${pulse})`, transformOrigin: '960px 800px' }}>
          <Bar x={960} y={800} shortsRed={red} />
        </div>
        <Pill text="THERE IT IS." top={910} red o={red} />
      </div>
      <div style={{ position: 'absolute', inset: 0, opacity: sw ? ease(t, 30.0, 30.3) : 0 }}>
        <Txt s={56} style={{ position: 'absolute', left: 0, right: 0, top: 200 }}>NO OFF SWITCH.</Txt>
        <div style={{ position: 'absolute', left: 960 - 170, top: 430, width: 340, height: 170, borderRadius: 90, border: `6px solid ${RED}`, boxShadow: RGLOW }}>
          <div style={{ position: 'absolute', left: 18 + knob * 170, top: 18, width: 122, height: 122, borderRadius: '50%', background: RED }} />
        </div>
        <Txt s={30} c={RED} glow={RGLOW} style={{ position: 'absolute', left: 0, right: 0, top: 650 }}>SHORTS: ALWAYS ON</Txt>
        <Txt s={24} style={{ position: 'absolute', left: 0, right: 0, top: 720, color: '#999', opacity: ease(t, 31.3, 31.6) }}>(THEIR "LIMIT"? JUST TAP ✕.)</Txt>
      </div>
    </AbsoluteFill>
  );
};

/** 32.6–35.1: SO I BUILT SOMETHING. – F*CK SHORTS. */
const S9: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 32.55, 35.15);
  if (a <= 0) return null;
  const logo = ease(t, 34.0, 34.4);
  return (
    <AbsoluteFill style={{ opacity: a, alignItems: 'center', justifyContent: 'center' }}>
      <Txt s={46} style={{ position: 'absolute', top: 260, left: 0, right: 0, opacity: ease(t, 32.66, 33.0) * (1 - 0.6 * logo) }}>SO I BUILT SOMETHING.</Txt>
      <div style={{ opacity: logo, transform: `scale(${0.6 + 0.4 * logo})`, filter: `drop-shadow(0 0 ${40 * logo}px ${RED})`, display: 'flex', alignItems: 'center', gap: 40, marginTop: 120 }}>
        <Logo size={200} />
        <Txt s={110} ls={0.05} glow={GLOW}>F<span style={{ color: RED }}>*</span>CK SHORTS</Txt>
      </div>
    </AbsoluteFill>
  );
};

/** 35.1–38.85: Shorts-Knopf wird entfernt. FOR GOOD. */
const S10: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 35.15, 38.85);
  if (a <= 0) return null;
  const rm = ease(t, 35.9, 36.6);
  const shards = Array.from({ length: 10 }, (_, i) => ({ ang: random(`a${i}`) * Math.PI * 2, d: 80 + random(`d${i}`) * 220 }));
  const burst = lin(t, 35.9, 36.6);
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Txt s={44} style={{ position: 'absolute', left: 0, right: 0, top: 200 }}>DELETES THE SHORTS BUTTON</Txt>
      <Txt s={30} style={{ position: 'absolute', left: 0, right: 0, top: 268, color: '#bbb', opacity: ease(t, 36.62, 36.9) }}>FROM THE REAL YOUTUBE APP</Txt>
      <Bar x={960} y={560} shortsRed={1} remove={rm} />
      {burst > 0 && burst < 1 && shards.map((s, i) => (
        <div key={i} style={{ position: 'absolute', left: 960 - 95 + Math.cos(s.ang) * s.d * burst, top: 560 + Math.sin(s.ang) * s.d * burst, width: 14, height: 14, background: RED, opacity: 1 - burst, transform: `rotate(${s.ang * 90}deg)`, boxShadow: RGLOW }} />
      ))}
      <Txt s={84} style={{ position: 'absolute', left: 0, right: 0, top: 740, opacity: ease(t, 37.96, 38.2), transform: `scale(${1.2 - 0.2 * ease(t, 37.96, 38.2)})` }}>✓ FOR GOOD.</Txt>
    </AbsoluteFill>
  );
};

/** 38.9–42.3: STOP GIVING YOUR TIME … */
const S11: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 38.85, 42.3);
  if (a <= 0) return null;
  const fall = Math.max(0, t - 40.2);
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Txt s={70} style={{ position: 'absolute', left: 0, right: 0, top: 170 }}>STOP GIVING YOUR TIME</Txt>
      <Txt s={30} style={{ position: 'absolute', left: 0, right: 0, top: 265, color: '#ccc', opacity: ease(t, 40.2, 40.5) }}>TO AN APP THAT DOESN'T GIVE A F<span style={{ color: RED }}>*</span>CK ABOUT YOU</Txt>
      <div style={{ position: 'absolute', left: 960 - 200, top: 400, width: 400, height: 330, clipPath: 'polygon(42% 0, 58% 0, 100% 100%, 0 100%)', background: 'linear-gradient(180deg, rgba(255,255,255,.04), rgba(255,255,255,.28))' }} />
      <Person x={960} y={700} s={1.5} c="#fff" glow />
      <div style={{ position: 'absolute', left: 1080, top: 640 + fall * fall * 1200, transform: `rotate(${fall * 120}deg)`, opacity: 1 - lin(fall, 0.4, 0.8) }}>
        <div style={{ width: 70, height: 120, borderRadius: 14, border: `5px solid ${RED}`, boxShadow: RGLOW }} />
      </div>
    </AbsoluteFill>
  );
};

/** 42.3–Ende: COMMENT "TEST". */
const S12: React.FC<{ t: number }> = ({ t }) => {
  const a = ease(t, 42.3, 42.6);
  if (a <= 0) return null;
  const btn = ease(t, 42.86, 43.2);
  return (
    <AbsoluteFill style={{ opacity: a, alignItems: 'center', justifyContent: 'center', gap: 34 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 30, filter: `drop-shadow(0 0 30px rgba(229,23,43,.6))` }}>
        <Logo size={130} />
        <Txt s={80} ls={0.05}>F<span style={{ color: RED }}>*</span>CK SHORTS</Txt>
      </div>
      <div style={{ opacity: btn, transform: `scale(${0.8 + 0.2 * btn + 0.03 * Math.sin(t * 8)})`, background: '#fff', color: RED, fontFamily: F, fontWeight: 900, fontSize: 56, letterSpacing: 4, padding: '22px 60px', borderRadius: 70, boxShadow: '0 0 40px rgba(255,255,255,.45)' }}>
        COMMENT "TEST"
      </div>
      <Txt s={26} style={{ color: '#ccc', opacity: ease(t, 43.42, 43.8) }}>BE ONE OF THE FIRST · COMING SOON TO GOOGLE PLAY</Txt>
    </AbsoluteFill>
  );
};

export const Ad4: React.FC<{ voice: boolean }> = ({ voice }) => {
  useFonts();
  const frame = useCurrentFrame();
  const t = frame / FPS4;
  return (
    <AbsoluteFill style={{ background: 'radial-gradient(60% 70% at 50% 50%, #121212 0%, #000 75%)', fontFamily: F }}>
      {voice && <Audio src={staticFile('voice4.wav')} />}
      <S1 t={t} />
      <S2 t={t} />
      <S3 t={t} />
      <S4 t={t} />
      <S5 t={t} />
      <S6 t={t} />
      <S7 t={t} />
      <S8 t={t} />
      <S9 t={t} />
      <S10 t={t} />
      <S11 t={t} />
      <S12 t={t} />
    </AbsoluteFill>
  );
};
