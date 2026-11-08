import React from 'react';
import { AbsoluteFill, Audio, random, staticFile, useCurrentFrame } from 'remotion';
import { BarIcon, Logo, RED, ease, lin, useFonts } from './Ad';

/**
 * Werbevideo 6 "Stop giving a sh*t" (quer 16:9). Anfang = dieselben Sätze und derselbe Bildaufbau wie das Original
 * (Titel, YOU-Figur, Türen, Lachende, LET THEM.), eigene Stimme (Jerry B., public/voice6.wav) und eigene Zeichnungen.
 * Danach die Wendung zu F*ck Shorts. Zeiten = Wortzeiten der Aufnahme (Sekunden).
 */
export const FPS6 = 30;
export const DURATION6_S = 51.5;
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

const Floor: React.FC<{ y?: number; o?: number }> = ({ y = 880, o = 1 }) => (
  <div style={{ position: 'absolute', left: 0, right: 0, top: y, borderTop: '2px dashed rgba(255,255,255,.12)', opacity: o }} />
);
const Label: React.FC<{ text: string; o?: number; top?: number }> = ({ text, o = 1, top = 110 }) => (
  <Txt s={34} ls={0.12} style={{ position: 'absolute', left: 0, right: 0, top, opacity: o }}>{text}</Txt>
);
const You: React.FC<{ x?: number; y?: number; s?: number; o?: number }> = ({ x = 960, y = 800, s = 1.4, o = 1 }) => (
  <>
    <Person x={x} y={y} s={s} c="#fff" glow o={o} />
    <Txt s={18} style={{ position: 'absolute', left: x - 100, width: 200, top: y + 50 * s, color: '#999', opacity: o }}>YOU</Txt>
  </>
);
const Bulb: React.FC<{ x: number; y: number; on: number }> = ({ x, y, on }) => (
  <div style={{ position: 'absolute', left: x - 22, top: y - 26, width: 44, height: 52 }}>
    <div style={{ width: 44, height: 40, borderRadius: '50%', background: `rgba(255,255,255,${0.15 + 0.85 * on})`, boxShadow: on > 0.05 ? `0 0 ${50 * on}px rgba(255,255,255,${0.8 * on})` : undefined }} />
    <div style={{ width: 20, height: 10, margin: '2px auto 0', borderRadius: 3, background: '#666' }} />
  </div>
);
const Rings: React.FC<{ x: number; y: number; p: number; red?: boolean }> = ({ x, y, p, red }) => (
  <>
    {[140, 260, 400].map((r, i) => (
      <div key={i} style={{ position: 'absolute', left: x - r * p, top: y - r * p, width: 2 * r * p, height: 2 * r * p, borderRadius: '50%', border: `2px solid ${red ? RED : 'rgba(255,255,255,.25)'}`, opacity: 1 - i * 0.25 }} />
    ))}
  </>
);

// ------------------------------------------------------------------------------------------- Anfang (wie Original)

/** 0–3.4: STOP GIVING A SH*T about what people think. */
const S1: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 0, 3.45, 0.15);
  if (a <= 0) return null;
  const w = (st: number) => ease(t, st, st + 0.18);
  const crowd = [[420, 'WHAT THEY THINK'], [600, 'OPINIONS'], [780, 'JUDGING']] as const;
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Txt s={110} style={{ position: 'absolute', left: 0, right: 0, top: 120, opacity: w(0), transform: `scale(${1.2 - 0.2 * w(0)})` }}>STOP GIVING</Txt>
      <Txt s={130} glow={RGLOW} style={{ position: 'absolute', left: 0, right: 0, top: 250, opacity: w(1.18), transform: `scale(${1.3 - 0.3 * w(1.18)})` }}>A SH<span style={{ color: RED }}>*</span>T</Txt>
      <Floor />
      <You x={1180} />
      {crowd.map(([x, b], i) => {
        const p = ease(t, 1.98 + i * 0.25, 2.25 + i * 0.25);
        return (
          <React.Fragment key={i}>
            <Person x={x} y={810} s={1.1} c="#bbb" o={p} />
            <Bubble x={x} y={700 - (i % 2) * 40} text={b} p={p} />
          </React.Fragment>
        );
      })}
    </AbsoluteFill>
  );
};

/** 3.4–4.9: SERIOUSLY. */
const S2: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 3.45, 4.95, 0.12);
  if (a <= 0) return null;
  const p = ease(t, 3.5, 4.2);
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Txt s={70} style={{ position: 'absolute', left: 0, right: 0, top: 110, opacity: ease(t, 3.58, 3.75) }}>SERIOUSLY.</Txt>
      <Rings x={960} y={600} p={p} />
      <div style={{ position: 'absolute', inset: 0, transform: `scale(${1 + 0.15 * p})`, transformOrigin: '960px 640px' }}>
        <Person x={960} y={620} s={2.6} c="#fff" glow />
      </div>
    </AbsoluteFill>
  );
};

/** 4.9–7.3: WHAT THE F*CK ARE YOU SO AFRAID OF? – dunkle Türen mit Augen. */
const S3: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 4.95, 7.3);
  if (a <= 0) return null;
  const doors = [380, 640, 1280, 1540];
  const blink = (i: number) => (Math.sin(t * 3 + i * 2) > 0.95 ? 0.2 : 1);
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Txt s={46} style={{ position: 'absolute', left: 0, right: 0, top: 110, opacity: ease(t, 5.06, 5.3) }}>WHAT THE F<span style={{ color: RED }}>*</span>CK ARE YOU SO AFRAID OF?</Txt>
      <Floor />
      {doors.map((x, i) => {
        const p = ease(t, 5.2 + i * 0.15, 5.5 + i * 0.15);
        const h = i % 3 === 0 ? 300 : 420;
        return (
          <div key={i} style={{ position: 'absolute', left: x - 70, top: 880 - h, width: 140, height: h, border: '3px dashed rgba(255,255,255,.35)', borderRadius: 10, opacity: p }}>
            <div style={{ position: 'absolute', left: 38, top: 40, display: 'flex', gap: 22, opacity: blink(i) }}>
              <div style={{ width: 12, height: 12, borderRadius: '50%', background: i === 2 ? RED : '#fff' }} />
              <div style={{ width: 12, height: 12, borderRadius: '50%', background: i === 2 ? RED : '#fff' }} />
            </div>
          </div>
        );
      })}
      <You x={960} />
      <Txt s={70} style={{ position: 'absolute', left: 0, right: 0, top: 590, opacity: ease(t, 6.26, 6.45) }}>?</Txt>
    </AbsoluteFill>
  );
};

/** 7.3–13.3: Lachen / delusional / you can't – du links mit Glühbirne, drei rechts. */
const S4: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 7.3, 13.35);
  if (a <= 0) return null;
  const phase = t < 9.3 ? 0 : t < 11.25 ? 1 : 2;
  const starts = [7.42, 9.42, 11.34];
  const labels = ['SOMEONE LAUGHING AT YOUR DREAMS', "SOMEONE THINKING YOU'RE DELUSIONAL", "SOMEONE SAYING YOU CAN'T DO IT"];
  const bubbles = [['HA HA', 'HAHA!', 'LOL'], ['DELUSIONAL'], ["YOU CAN'T", 'NO WAY', 'IMPOSSIBLE']];
  const st = starts[phase];
  const others = [1180, 1380, 1580];
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Label text={labels[phase]} o={ease(t, st, st + 0.2)} />
      <Floor />
      <Bulb x={440} y={620} on={1 - 0.35 * phase} />
      <You x={440} y={800} />
      {others.map((x, i) => {
        const shake = phase === 0 && t > 8.1 ? Math.sin(t * 40 + i) * 4 : 0;
        const point = phase === 2 ? 1 : 0;
        return (
          <div key={i} style={{ position: 'absolute', inset: 0, transform: `translateY(${shake}px)` }}>
            <Person x={x} y={810} s={1.1} c="#bbb" />
            {point > 0 && <div style={{ position: 'absolute', left: x - 70, top: 800, width: 50, height: 8, borderRadius: 4, background: '#bbb', transform: 'rotate(-15deg)' }} />}
          </div>
        );
      })}
      {bubbles[phase].map((b, i) => {
        const p = ease(t, st + 0.3 + i * 0.22, st + 0.55 + i * 0.22);
        const x = bubbles[phase].length === 1 ? 1380 : others[i];
        return <Bubble key={`${phase}${i}`} x={x} y={phase === 1 ? 640 : 690} text={b} p={p} red={phase === 2 && i === 2} />;
      })}
      {phase === 1 && others.map((x, i) => <div key={i} style={{ position: 'absolute', left: x - 12, top: 720, width: 24, height: 24, borderRadius: '50%', background: '#777', opacity: ease(t, 9.7 + i * 0.1, 9.9 + i * 0.1) }} />)}
    </AbsoluteFill>
  );
};

/** 13.3–15.5: LET THEM. – die anderen verblassen, du bleibst im Licht. */
const S5: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 13.35, 15.5);
  if (a <= 0) return null;
  const fade = ease(t, 13.9, 14.9);
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Txt s={84} style={{ position: 'absolute', left: 0, right: 0, top: 110, opacity: ease(t, 13.5, 13.7), transform: `scale(${1.3 - 0.3 * ease(t, 13.5, 13.7)})` }}>LET THEM.</Txt>
      <Floor />
      <Rings x={960} y={760} p={ease(t, 13.5, 14.3)} />
      {[380, 560, 1360, 1540].map((x, i) => <Person key={i} x={x} y={810} s={1.1} c="#999" o={1 - fade} />)}
      <You x={960} />
    </AbsoluteFill>
  );
};

// ------------------------------------------------------------------------------------------- Wendung

/** 15.5–20.9: BUT BE HONEST. IT'S NOT THEM WHO'S STOPPING YOU. IT'S THIS. */
const S6: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 15.5, 20.95, 0.12);
  if (a <= 0) return null;
  const ph = ease(t, 19.66, 19.85);
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Txt s={60} style={{ position: 'absolute', left: 0, right: 0, top: 110, opacity: ease(t, 15.66, 15.9) * (1 - ph) }}>BUT BE HONEST.</Txt>
      <Txt s={38} style={{ position: 'absolute', left: 0, right: 0, top: 200, color: '#ccc', opacity: ease(t, 17.48, 17.7) * (1 - ph) }}>IT'S NOT THEM WHO'S STOPPING YOU.</Txt>
      <Txt s={96} c={RED} glow={RGLOW} style={{ position: 'absolute', left: 0, right: 0, top: 120, opacity: ph, transform: `scale(${1.5 - 0.5 * ph})` }}>IT'S THIS.</Txt>
      <Floor />
      <Bulb x={760} y={620} on={1 - 0.6 * ph} />
      <You x={760} y={800} o={1 - 0.4 * ph} />
      <div style={{ position: 'absolute', inset: 0, opacity: ph, transform: `translateX(${80 * (1 - ph)}px)` }}>
        <Phone x={1180} y={620} s={0.95} red={1}>
          <div style={{ position: 'absolute', inset: 0, background: 'radial-gradient(circle at 50% 50%, rgba(229,23,43,.55), transparent 70%)' }} />
          <div style={{ position: 'absolute', left: '50%', top: '50%', transform: 'translate(-50%,-50%) scale(2)' }}><BarIcon kind="shorts" color="#fff" /></div>
        </Phone>
      </div>
    </AbsoluteFill>
  );
};

/** 20.9–26.2: SWIPE SWIPE SWIPE – 3 HOURS A DAY – GONE. */
const S7: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 20.9, 26.25, 0.08);
  if (a <= 0) return null;
  const sw = [20.98, 21.98, 22.72];
  const k = sw.filter((x) => t >= x).length - 1;
  const since = k >= 0 ? t - sw[k] : 0;
  const slide = ease(since, 0, 0.16);
  const hours = ease(t, 23.74, 23.92);
  const gone = ease(t, 25.18, 25.36);
  const hues = ['#ff3b45', '#ffffff', '#ff3b45'];
  const clockA = (t - 21) * (t < 23.7 ? 4 : 30);
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <div style={{ position: 'absolute', inset: 0, opacity: 1 - gone }}>
        <Phone x={960} y={560} s={1.15} red={hours > 0.5 ? 1 : 0}>
          {k >= 0 && [k - 1, k].map((j) => j >= 0 && (
            <div key={j} style={{ position: 'absolute', left: 14, right: 14, height: 420, top: j === k ? 14 + 440 * (1 - slide) : 14 - 440 * slide, borderRadius: 20, background: `radial-gradient(circle at 50% 40%, ${hues[j % 3]}55, rgba(255,255,255,.06))` }}>
              <div style={{ position: 'absolute', left: '50%', top: '42%', transform: 'translate(-50%,-50%)', borderTop: '26px solid transparent', borderBottom: '26px solid transparent', borderLeft: '44px solid rgba(255,255,255,.85)' }} />
            </div>
          ))}
        </Phone>
        {k >= 0 && t < 23.7 && (
          <Txt s={110} style={{ position: 'absolute', left: k % 2 ? 1160 : 220, width: 560, top: 470, transform: `scale(${1.5 - 0.5 * ease(since, 0, 0.12)}) rotate(${k % 2 ? 6 : -6}deg)`, opacity: 1 - lin(since, 0.5, 0.7) }}>SWIPE</Txt>
        )}
        <svg style={{ position: 'absolute', left: 1240, top: 400, opacity: hours }} width="300" height="300" viewBox="0 0 100 100">
          <circle cx="50" cy="50" r="44" fill="none" stroke={RED} strokeWidth="5" />
          <line x1="50" y1="50" x2={50 + 32 * Math.sin(clockA)} y2={50 - 32 * Math.cos(clockA)} stroke={RED} strokeWidth="5" strokeLinecap="round" />
        </svg>
        <Txt s={84} c={RED} glow={RGLOW} style={{ position: 'absolute', left: 40, width: 760, top: 470, opacity: hours, transform: `scale(${1.6 - 0.6 * hours})` }}>3 HOURS</Txt>
        <Txt s={40} style={{ position: 'absolute', left: 40, width: 760, top: 580, opacity: ease(t, 24.3, 24.5) }}>A DAY</Txt>
      </div>
      <Txt s={190} style={{ position: 'absolute', left: 0, right: 0, top: 410, opacity: gone, transform: `scale(${1.4 - 0.4 * gone})`, letterSpacing: 40 }}>GONE.</Txt>
    </AbsoluteFill>
  );
};

/** 26.2–31.6: Träume sterben nicht am Lachen – sondern einen Short nach dem anderen. */
const S8: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 26.25, 31.65);
  if (a <= 0) return null;
  const hitsT = [29.76, 30.16, 30.5, 30.76];
  const n = hitsT.filter((x) => t >= x).length;
  const on = 1 - n / 4;
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Txt s={40} style={{ position: 'absolute', left: 0, right: 0, top: 110, opacity: ease(t, 26.38, 26.6) }}>YOUR DREAMS DON'T DIE BECAUSE PEOPLE LAUGH.</Txt>
      <Txt s={60} c={RED} glow={RGLOW} style={{ position: 'absolute', left: 0, right: 0, top: 190, opacity: ease(t, 28.8, 29.0) }}>THEY DIE ONE SHORT AT A TIME.</Txt>
      <Floor />
      <Bulb x={760} y={600} on={on} />
      <You x={760} y={800} o={0.5 + 0.5 * on} />
      {hitsT.map((h, i) => {
        const p = lin(t, h - 0.35, h);
        if (p <= 0 || p >= 1) return null;
        return <div key={i} style={{ position: 'absolute', left: 1400 - 640 * p - 22, top: 420 + 180 * p * p, width: 44, height: 76, borderRadius: 10, border: `4px solid ${RED}`, boxShadow: RGLOW, transform: `rotate(${-20 + 40 * p}deg)` }} />;
      })}
      <div style={{ position: 'absolute', inset: 0, opacity: ease(t, 29.2, 29.5) }}>
        <Phone x={1460} y={480} s={0.6} red={1} />
      </div>
    </AbsoluteFill>
  );
};

/** 31.6–34.6: AND YOUTUBE WON'T EVEN LET YOU TURN THEM OFF. */
const S9: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 31.65, 34.65, 0.12);
  if (a <= 0) return null;
  const red = ease(t, 32.32, 32.45);
  const tryP = (s: number) => (t > s && t < s + 0.5 ? Math.sin(((t - s) / 0.5) * Math.PI) : 0);
  const knob = Math.max(tryP(33.1), tryP(33.7)) * 0.6;
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Txt s={48} style={{ position: 'absolute', left: 0, right: 0, top: 110, opacity: ease(t, 31.84, 32.0) }}>AND YOUTUBE WON'T EVEN LET YOU</Txt>
      <Txt s={72} c={RED} glow={RGLOW} style={{ position: 'absolute', left: 0, right: 0, top: 190, opacity: ease(t, 33.48, 33.65) }}>TURN THEM OFF.</Txt>
      <Bar x={960} y={520} shortsRed={red} />
      <div style={{ position: 'absolute', left: 960 - 140, top: 680, width: 280, height: 140, borderRadius: 80, border: `6px solid ${RED}`, boxShadow: RGLOW }}>
        <div style={{ position: 'absolute', left: 16 + knob * 140, top: 16, width: 96, height: 96, borderRadius: '50%', background: RED }} />
      </div>
      <Txt s={24} c={RED} glow={RGLOW} style={{ position: 'absolute', left: 0, right: 0, top: 850 }}>SHORTS: ALWAYS ON</Txt>
    </AbsoluteFill>
  );
};

/** 34.6–37.1: SO I DID. – F*CK SHORTS. */
const S10: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 34.65, 37.15);
  if (a <= 0) return null;
  const logo = ease(t, 35.78, 36.0);
  return (
    <AbsoluteFill style={{ opacity: a, alignItems: 'center', justifyContent: 'center' }}>
      <Txt s={70} style={{ position: 'absolute', top: 230, left: 0, right: 0, opacity: ease(t, 34.72, 34.9) * (1 - 0.5 * logo) }}>SO I DID.</Txt>
      <div style={{ opacity: logo, transform: `scale(${0.6 + 0.4 * logo})`, filter: `drop-shadow(0 0 ${40 * logo}px ${RED})`, display: 'flex', alignItems: 'center', gap: 40, marginTop: 120 }}>
        <Logo size={200} />
        <Txt s={110} ls={0.05}>F<span style={{ color: RED }}>*</span>CK SHORTS</Txt>
      </div>
    </AbsoluteFill>
  );
};

/** 37.1–43.6: Knopf weg, lange Videos bleiben, Fokus kommt zurück. */
const S11: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 37.15, 43.65);
  if (a <= 0) return null;
  const rm = ease(t, 37.86, 38.4);
  const shards = Array.from({ length: 10 }, (_, i) => ({ ang: random(`a${i}`) * Math.PI * 2, d: 80 + random(`d${i}`) * 220 }));
  const burst = lin(t, 37.86, 38.5);
  const focus = ease(t, 41.86, 42.4);
  const vids = ease(t, 40.26, 40.5);
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Txt s={46} style={{ position: 'absolute', left: 0, right: 0, top: 110 }}>DELETES THE SHORTS BUTTON</Txt>
      <Txt s={30} style={{ position: 'absolute', left: 0, right: 0, top: 180, color: '#bbb', opacity: ease(t, 38.78, 39.0) }}>FROM THE REAL YOUTUBE APP</Txt>
      <div style={{ position: 'absolute', inset: 0, opacity: 1 - focus }}>
        <Bar x={960} y={380} shortsRed={1} remove={rm} />
        {burst > 0 && burst < 1 && shards.map((s, i) => (
          <div key={i} style={{ position: 'absolute', left: 960 - 95 + Math.cos(s.ang) * s.d * burst, top: 380 + Math.sin(s.ang) * s.d * burst, width: 14, height: 14, background: RED, opacity: 1 - burst, transform: `rotate(${s.ang * 90}deg)`, boxShadow: RGLOW }} />
        ))}
        {[0, 1, 2].map((i) => (
          <div key={i} style={{ position: 'absolute', left: 960 + (i - 1) * 420 - 180, top: 560 + 30 * (1 - vids), width: 360, height: 200, opacity: vids, borderRadius: 20, border: '4px solid #fff', boxShadow: GLOW }}>
            <div style={{ position: 'absolute', left: 150, top: 70, borderTop: '28px solid transparent', borderBottom: '28px solid transparent', borderLeft: '48px solid #fff' }} />
            <div style={{ position: 'absolute', right: 14, bottom: 10, fontFamily: F, fontWeight: 800, fontSize: 20, color: '#fff' }}>{['18:42', '1:02:10', '24:05'][i]}</div>
          </div>
        ))}
        <Txt s={40} style={{ position: 'absolute', left: 0, right: 0, top: 800, opacity: vids }}>✓ YOUR LONG VIDEOS STAY.</Txt>
      </div>
      <div style={{ position: 'absolute', inset: 0, opacity: focus }}>
        <Bulb x={960} y={560} on={focus} />
        <Rings x={960} y={560} p={focus} />
        <You x={960} y={760} />
        <Txt s={64} style={{ position: 'absolute', left: 0, right: 0, top: 900, transform: `scale(${1.3 - 0.3 * focus})` }}>YOUR FOCUS COMES BACK.</Txt>
      </div>
    </AbsoluteFill>
  );
};

/** 43.6–47.7: STOP GIVING YOUR TIME … */
const S12: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, 43.65, 47.75);
  if (a <= 0) return null;
  const fall = Math.max(0, t - 46.64);
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Txt s={70} style={{ position: 'absolute', left: 0, right: 0, top: 150, opacity: ease(t, 44.06, 44.3) }}>STOP GIVING YOUR TIME</Txt>
      <Txt s={32} style={{ position: 'absolute', left: 0, right: 0, top: 250, color: '#ccc', opacity: ease(t, 45.34, 45.6) }}>TO AN APP THAT DOESN'T GIVE A F<span style={{ color: RED }}>*</span>CK ABOUT YOU</Txt>
      <div style={{ position: 'absolute', left: 960 - 200, top: 400, width: 400, height: 330, clipPath: 'polygon(42% 0, 58% 0, 100% 100%, 0 100%)', background: 'linear-gradient(180deg, rgba(255,255,255,.04), rgba(255,255,255,.28))' }} />
      <Person x={960} y={700} s={1.5} c="#fff" glow />
      <div style={{ position: 'absolute', left: 1080, top: 640 + fall * fall * 1200, transform: `rotate(${fall * 120}deg)`, opacity: 1 - lin(fall, 0.4, 0.8) }}>
        <div style={{ width: 70, height: 120, borderRadius: 14, border: `5px solid ${RED}`, boxShadow: RGLOW }} />
      </div>
    </AbsoluteFill>
  );
};

/** 47.7–Ende: COMMENT "TEST". */
const S13: React.FC<{ t: number }> = ({ t }) => {
  const a = ease(t, 47.7, 47.95);
  if (a <= 0) return null;
  const btn = ease(t, 48.58, 48.8);
  return (
    <AbsoluteFill style={{ opacity: a, alignItems: 'center', justifyContent: 'center', gap: 34 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 30, filter: 'drop-shadow(0 0 30px rgba(229,23,43,.6))' }}>
        <Logo size={130} />
        <Txt s={80} ls={0.05}>F<span style={{ color: RED }}>*</span>CK SHORTS</Txt>
      </div>
      <div style={{ opacity: btn, transform: `scale(${0.8 + 0.2 * btn + 0.03 * Math.sin(t * 8)})`, background: '#fff', color: RED, fontFamily: F, fontWeight: 900, fontSize: 56, letterSpacing: 4, padding: '22px 60px', borderRadius: 70, boxShadow: '0 0 40px rgba(255,255,255,.45)' }}>
        COMMENT "TEST"
      </div>
      <Txt s={26} style={{ color: '#ccc', opacity: ease(t, 49.22, 49.5) }}>TRY IT FIRST · COMING SOON TO GOOGLE PLAY</Txt>
    </AbsoluteFill>
  );
};

const PUNCH = [0, 1.18, 3.58, 5.46, 7.42, 9.42, 11.34, 13.5, 15.66, 19.94, 20.98, 21.98, 22.72, 23.74, 25.18, 28.8, 32.32, 33.82, 35.78, 37.86, 40.26, 41.94, 44.06, 46.64, 48.58];
const SHAKE = [1.18, 5.46, 13.5, 19.94, 23.74, 25.18, 32.32, 35.78, 37.86, 46.64];
const FLASH = [1.18, 13.5, 19.94, 20.98, 21.98, 22.72, 23.74, 25.18, 35.78, 37.86];

export const Ad6: React.FC<{ voice: boolean }> = ({ voice }) => {
  useFonts();
  const frame = useCurrentFrame();
  const t = frame / FPS6;
  const pk = Math.max(0, ...PUNCH.map((p) => (t >= p ? Math.exp(-(t - p) / 0.11) : 0)));
  const sk = Math.max(0, ...SHAKE.map((p) => (t >= p && t < p + 0.3 ? 1 - (t - p) / 0.3 : 0)));
  const fl = Math.max(0, ...FLASH.map((p) => (t >= p && t < p + 0.12 ? 1 - (t - p) / 0.12 : 0)));
  const dx = sk * 14 * Math.sin(t * 97);
  const dy = sk * 10 * Math.cos(t * 83);
  return (
    <AbsoluteFill style={{ background: 'radial-gradient(60% 70% at 50% 50%, #141010 0%, #000 75%)', fontFamily: F, overflow: 'hidden' }}>
      {voice && <Audio src={staticFile('voice6.wav')} />}
      {voice && <Audio src={staticFile('sfx6.wav')} volume={0.5} />}
      <AbsoluteFill style={{ transform: `translate(${dx}px, ${dy}px) scale(${1 + 0.06 * pk})` }}>
        <S1 t={t} /><S2 t={t} /><S3 t={t} /><S4 t={t} /><S5 t={t} /><S6 t={t} /><S7 t={t} />
        <S8 t={t} /><S9 t={t} /><S10 t={t} /><S11 t={t} /><S12 t={t} /><S13 t={t} />
      </AbsoluteFill>
      <AbsoluteFill style={{ background: '#fff', opacity: fl * 0.3, pointerEvents: 'none' }} />
      <AbsoluteFill style={{ background: `radial-gradient(70% 60% at 50% 50%, transparent 55%, rgba(229,23,43,${0.18 * sk}) 100%)`, pointerEvents: 'none' }} />
    </AbsoluteFill>
  );
};
