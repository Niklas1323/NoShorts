import React, { useEffect, useState } from 'react';
import {
  AbsoluteFill,
  Audio,
  continueRender,
  delayRender,
  interpolate,
  random,
  staticFile,
  useCurrentFrame,
  Easing,
} from 'remotion';
import { WORDS } from './words';

/**
 * Werbevideo 1 "Tonight you'll forget" (40 s, 1080x1920). Alles mit Code gezeichnet, Zeiten in Sekunden passend zur
 * Sprachaufnahme (public/voice.wav). Szenen: S1 Hook, S2 Monate, S3 Feed, S4 Vergessen, S5 Was wartet, S6 App, S7 Tester.
 */
export const FPS = 30;
export const DURATION_S = 41;

const W = 1080;
const H = 1920;
export const RED = '#E5172B';
export const AMBER = '#FFB547';
const BG = '#0A0506';
export const FONT = 'Montserrat';

export const clamp = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' } as const;
export const lin = (t: number, a: number, b: number, from = 0, to = 1) => interpolate(t, [a, b], [from, to], clamp);
export const ease = (t: number, a: number, b: number, from = 0, to = 1) =>
  interpolate(t, [a, b], [from, to], { ...clamp, easing: Easing.bezier(0.22, 1, 0.36, 1) });

const SCENES = {
  s1: [0, 5.4],
  s2: [5.4, 11.9],
  s3: [11.9, 17.45],
  s4: [17.45, 20.3],
  s5: [20.3, 26.9],
  s6: [26.9, 34.45],
  s7: [34.45, DURATION_S],
} as const;

/** Sichtbarkeit einer Szene mit weichem Ein- und Ausblenden. */
const sceneAlpha = (t: number, [a, b]: readonly [number, number], fade = 0.3) =>
  Math.min(lin(t, a - 0.01, a + fade), b >= DURATION_S ? 1 : lin(t, b, b + fade, 1, 0));

/** Langsamer Zoom über die Szene, damit nie etwas ganz stillsteht. */
const drift = (t: number, [a, b]: readonly [number, number], amount = 0.05) => 1 + amount * lin(t, a, b);

// ------------------------------------------------------------------------------------------------ Fonts

export const useFonts = () => {
  const [handle] = useState(() => delayRender('fonts'));
  useEffect(() => {
    Promise.all(
      [800, 900].map((w) =>
        new FontFace(FONT, `url(${staticFile(`montserrat-latin-${w}-normal.woff2`)})`, { weight: String(w) })
          .load()
          .then((f) => document.fonts.add(f)),
      ),
    ).then(() => continueRender(handle));
  }, [handle]);
};

// ------------------------------------------------------------------------------------------------ Bausteine

export const HUES = [352, 8, 330, 280, 220, 200, 18, 300];

/** Ein "Short": Hochkant-Kärtchen mit abstraktem Inhalt (keine echten Videos, keine Logos). */
export const Card: React.FC<{
  seed: number;
  w: number;
  h: number;
  blank?: number; // 0 = bunt, 1 = leer/grau
  glow?: number;
  style?: React.CSSProperties;
}> = ({ seed, w, h, blank = 0, glow = 0, style }) => {
  const hue = HUES[Math.floor(random(seed) * HUES.length)];
  const hue2 = (hue + 40 + random(seed + 1) * 60) % 360;
  const r = w * 0.09;
  return (
    <div
      style={{
        position: 'absolute',
        width: w,
        height: h,
        borderRadius: r,
        overflow: 'hidden',
        background: `linear-gradient(160deg, hsl(${hue} 65% 38%), hsl(${hue2} 60% 18%))`,
        boxShadow: `0 ${w * 0.05}px ${w * 0.18}px rgba(0,0,0,.55)${glow ? `, 0 0 ${w * 0.25 * glow}px rgba(229,23,43,${0.55 * glow})` : ''}`,
        border: `${Math.max(1, w * 0.006)}px solid rgba(255,255,255,.14)`,
        filter: blank ? `grayscale(${blank}) brightness(${1 - 0.55 * blank})` : undefined,
        ...style,
      }}
    >
      <div style={{ opacity: 1 - blank }}>
        <div
          style={{
            position: 'absolute',
            left: w * (0.15 + random(seed + 2) * 0.3),
            top: h * (0.2 + random(seed + 3) * 0.25),
            width: w * 0.55,
            height: w * 0.55,
            borderRadius: '50%',
            background: `hsl(${hue2} 80% 60%)`,
            filter: `blur(${w * 0.12}px)`,
            opacity: 0.7,
          }}
        />
        {/* Knöpfe rechts und Titelzeilen unten, wie in jedem Kurzvideo-Feed */}
        {[0, 1, 2].map((i) => (
          <div
            key={i}
            style={{
              position: 'absolute',
              right: w * 0.06,
              top: h * (0.5 + i * 0.09),
              width: w * 0.1,
              height: w * 0.1,
              borderRadius: '50%',
              background: 'rgba(255,255,255,.75)',
            }}
          />
        ))}
        <div style={{ position: 'absolute', left: w * 0.07, bottom: h * 0.11, width: w * 0.55, height: h * 0.022, borderRadius: 99, background: 'rgba(255,255,255,.85)' }} />
        <div style={{ position: 'absolute', left: w * 0.07, bottom: h * 0.07, width: w * 0.38, height: h * 0.018, borderRadius: 99, background: 'rgba(255,255,255,.5)' }} />
      </div>
      <div style={{ position: 'absolute', left: 0, bottom: 0, height: Math.max(3, h * 0.008), width: `${(30 + random(seed + 5) * 60) * (1 - blank)}%`, background: RED }} />
    </div>
  );
};

/** Unser Logo: rotes Quadrat, Hochkant-Video mit Schnitt (wie BrandLogo in der App). */
export const Logo: React.FC<{ size: number }> = ({ size: s }) => (
  <svg width={s} height={s} viewBox="0 0 100 100">
    <rect width="100" height="100" rx="27" fill={RED} />
    <rect x="29" y="20" width="42" height="60" rx="9" fill="#fff" />
    <line x1="27" y1="80" x2="75" y2="22" stroke={RED} strokeWidth="15" strokeLinecap="round" />
    <line x1="27" y1="80" x2="75" y2="22" stroke="#fff" strokeWidth="6.5" strokeLinecap="round" />
  </svg>
);

export const BrandName: React.FC<{ size: number }> = ({ size }) => (
  <div style={{ fontFamily: FONT, fontWeight: 900, fontSize: size, color: '#fff', letterSpacing: -size * 0.03 }}>
    F<span style={{ color: RED }}>*</span>ck Shorts
  </div>
);

export const Pill: React.FC<{ text: string; color?: string; size?: number; style?: React.CSSProperties }> = ({ text, color = '#fff', size = 34, style }) => (
  <div
    style={{
      position: 'absolute',
      left: '50%',
      transform: 'translateX(-50%)',
      fontFamily: FONT,
      fontWeight: 800,
      fontSize: size,
      letterSpacing: size * 0.12,
      color,
      padding: `${size * 0.35}px ${size * 0.8}px`,
      borderRadius: 999,
      border: `3px solid ${color}`,
      background: 'rgba(0,0,0,.45)',
      whiteSpace: 'nowrap',
      ...style,
    }}
  >
    {text}
  </div>
);

// ------------------------------------------------------------------------------------------------ Hintergrund

const Background: React.FC<{ t: number }> = ({ t }) => {
  // Warmes Rot im Abschnitt "Was wartet" (Hoffnung), sonst kühles Dunkelrot.
  const warm = Math.min(lin(t, 20.3, 22), lin(t, 26.6, 27.4, 1, 0));
  return (
    <AbsoluteFill style={{ background: BG }}>
      <AbsoluteFill
        style={{
          background: `radial-gradient(circle at 50% ${45 + 5 * Math.sin(t * 0.3)}%, rgba(${120 + 80 * warm},${12 + 60 * warm},${20},${0.55}) 0%, rgba(10,5,6,0) 62%)`,
        }}
      />
      {/* Feines Pixelraster, das langsam wandert */}
      <AbsoluteFill
        style={{
          opacity: 0.22,
          backgroundImage:
            'linear-gradient(rgba(255,255,255,.07) 2px, transparent 2px), linear-gradient(90deg, rgba(255,255,255,.07) 2px, transparent 2px)',
          backgroundSize: '36px 36px',
          backgroundPosition: `0px ${-(t * 14) % 36}px`,
          maskImage: 'radial-gradient(circle at 50% 50%, black 30%, transparent 75%)',
          WebkitMaskImage: 'radial-gradient(circle at 50% 50%, black 30%, transparent 75%)',
        }}
      />
    </AbsoluteFill>
  );
};

// ------------------------------------------------------------------------------------------------ S1 Hook

const S1: React.FC<{ t: number }> = ({ t }) => {
  const a = sceneAlpha(t, SCENES.s1);
  if (a <= 0) return null;
  const cw = 470;
  const ch = Math.round((cw * 16) / 9);
  const cx = W / 2 - cw / 2;
  const cy = 1000 - ch / 2;
  const enter = ease(t, 0, 0.7);
  // Die Kärtchen dahinter (alle Videos von heute) verschwinden bei "forget".
  const ghosts = lin(t, 1.3, 2.6, 1, 0);
  // "Including this one": das Kärtchen zerfällt in Kacheln.
  const dissolveStart = 4.1;
  const cols = 10;
  const rows = 18;
  const tw = cw / cols;
  const th = ch / rows;
  return (
    <AbsoluteFill style={{ opacity: a, transform: `scale(${drift(t, SCENES.s1, 0.06)})` }}>
      {ghosts > 0 &&
        [-2, -1, 1, 2].map((k) => (
          <Card
            key={k}
            seed={100 + k}
            w={cw * 0.78}
            h={ch * 0.78}
            style={{
              left: W / 2 - (cw * 0.78) / 2 + k * 330 * enter,
              top: 1000 - (ch * 0.78) / 2 + Math.abs(k) * 40,
              opacity: 0.45 * ghosts * enter,
              transform: `rotate(${k * 4}deg) scale(${1 - Math.abs(k) * 0.08})`,
            }}
          />
        ))}
      {t < dissolveStart ? (
        <Card seed={7} w={cw} h={ch} glow={0.6 + 0.4 * Math.sin(t * 3)} style={{ left: cx, top: cy + 60 * (1 - enter), opacity: enter }} />
      ) : (
        <div style={{ position: 'absolute', left: cx, top: cy, width: cw, height: ch }}>
          {Array.from({ length: cols * rows }).map((_, i) => {
            const col = i % cols;
            const row = Math.floor(i / cols);
            const delay = random(`d${i}`) * 0.9 + (rows - row) * 0.02;
            const p = ease(t, dissolveStart + delay, dissolveStart + delay + 0.9);
            const hue = HUES[Math.floor(random(7) * HUES.length)];
            return (
              <div
                key={i}
                style={{
                  position: 'absolute',
                  left: col * tw + (random(`x${i}`) - 0.5) * 240 * p,
                  top: row * th - 420 * p * (0.5 + random(`y${i}`)),
                  width: tw - 1,
                  height: th - 1,
                  opacity: 1 - p,
                  transform: `scale(${1 - 0.6 * p})`,
                  background: `hsl(${hue + (row / rows) * 30} 60% ${36 - (row / rows) * 16}%)`,
                }}
              />
            );
          })}
        </div>
      )}
    </AbsoluteFill>
  );
};

// ------------------------------------------------------------------------------------------------ S2 Monate

const MONTHS_RED = [11, 35, 59, 83, 107];
const S2: React.FC<{ t: number }> = ({ t }) => {
  const a = sceneAlpha(t, SCENES.s2);
  if (a <= 0) return null;
  const size = 58;
  const gap = 12;
  const cols = 12;
  const rows = 10;
  const gw = cols * size + (cols - 1) * gap;
  const gh = rows * size + (rows - 1) * gap;
  const left = W / 2 - gw / 2;
  const top = 1040 - gh / 2;
  return (
    <AbsoluteFill style={{ opacity: a, transform: `scale(${drift(t, SCENES.s2)})` }}>
      <Pill text="10 YEARS · 120 MONTHS" size={30} style={{ top: top - 120, opacity: ease(t, 5.7, 6.3) }} />
      {Array.from({ length: cols * rows }).map((_, i) => {
        const col = i % cols;
        const row = Math.floor(i / cols);
        const appear = 5.55 + i * 0.014;
        const p = ease(t, appear, appear + 0.35);
        const redIdx = MONTHS_RED.indexOf(i);
        const redAt = 8.5 + redIdx * 0.22;
        const red = redIdx >= 0 ? ease(t, redAt, redAt + 0.3) : 0;
        return (
          <div
            key={i}
            style={{
              position: 'absolute',
              left: left + col * (size + gap),
              top: top + row * (size + gap) - 30 * (1 - p),
              width: size,
              height: size,
              borderRadius: 12,
              opacity: p,
              background: red ? `rgba(229,23,43,${0.35 + 0.65 * red})` : 'rgba(255,255,255,.12)',
              border: `2px solid ${red ? RED : 'rgba(255,255,255,.12)'}`,
              boxShadow: red ? `0 0 ${40 * red}px rgba(229,23,43,.8)` : undefined,
              transform: `scale(${1 + 0.25 * red * Math.max(0, Math.sin((t - redAt) * 6) * lin(t, redAt, redAt + 0.6, 1, 0))})`,
            }}
          />
        );
      })}
      <Pill text="≈ 5 MONTHS. GONE." color={RED} size={40} style={{ top: top + gh + 70, opacity: ease(t, 9.4, 9.9) }} />
    </AbsoluteFill>
  );
};

// ------------------------------------------------------------------------------------------------ S3 + S4 Feed

/** Scrollweg des Feeds: beschleunigt ab "Ten became an hour", bremst beim Vergessen ab. */
const feedOffset = (t: number) => {
  const start = 15.75;
  if (t <= start) return 0;
  let pos = 0;
  const dt = 1 / 120;
  for (let x = start; x < t; x += dt) {
    const v = 5200 * Math.min(ease(x, start, 16.9), ease(x, 17.5, 18.7, 1, 0));
    pos += v * dt;
  }
  return pos;
};

const S3S4: React.FC<{ t: number }> = ({ t }) => {
  const a = Math.min(lin(t, 11.9, 12.25), lin(t, 20.0, 20.4, 1, 0));
  if (a <= 0) return null;
  // Ein Kärtchen -> zehn -> endloser Feed
  const one = Math.min(ease(t, 12.0, 12.6), lin(t, 14.85, 15.15, 1, 0));
  const ten = Math.min(ease(t, 14.85, 15.3), lin(t, 15.75, 16.05, 1, 0));
  const feed = ease(t, 15.75, 16.1);
  const pulse = 1 + 0.06 * Math.max(0, Math.sin((t - 13.98) * 8)) * lin(t, 13.98, 14.6, 1, 0);
  const cw = 320;
  const ch = Math.round((cw * 16) / 9);
  const small = 150;
  const smallH = Math.round((small * 16) / 9);
  const fw = 300;
  const fh = Math.round((fw * 16) / 9);
  const fgap = 26;
  const offset = feedOffset(t);
  const blank = ease(t, 17.6, 18.5);
  return (
    <AbsoluteFill style={{ opacity: a }}>
      {one > 0 && <Card seed={21} w={cw} h={ch} glow={0.7} style={{ left: W / 2 - cw / 2, top: 1120 - ch / 2, opacity: one, transform: `scale(${(0.9 + 0.1 * one) * pulse})` }} />}
      {ten > 0 &&
        Array.from({ length: 10 }).map((_, i) => {
          const col = i % 5;
          const row = Math.floor(i / 5);
          const p = ease(t, 14.88 + i * 0.03, 15.3 + i * 0.03);
          const tx = W / 2 - small / 2 + (col - 2) * (small + 20) * p;
          const ty = 1120 - smallH / 2 + (row - 0.5) * (smallH + 20) * p;
          return <Card key={i} seed={30 + i} w={small} h={smallH} style={{ left: tx, top: ty, opacity: ten }} />;
        })}
      {feed > 0 &&
        [0, 1, 2].flatMap((col) =>
          Array.from({ length: 40 }).map((_, k) => {
            const colShift = col === 1 ? fh / 2 : 0;
            const baseY = k * (fh + fgap) + colShift - offset * (col === 1 ? 1.08 : 1);
            const y = ((baseY % (40 * (fh + fgap))) + 40 * (fh + fgap)) % (40 * (fh + fgap)) - fh;
            if (y > H + 50 || y < -fh - 50) return null;
            const id = col * 40 + k;
            // S4: Kärtchen fallen nacheinander herunter
            const fallStart = 18.6 + random(`f${id}`) * 0.9;
            const fall = Math.max(0, t - fallStart);
            const fy = 0.5 * 4200 * fall * fall;
            const rot = (random(`r${id}`) - 0.5) * 60 * lin(t, fallStart, fallStart + 1);
            return (
              <Card
                key={id}
                seed={200 + id}
                w={fw}
                h={fh}
                blank={blank}
                style={{
                  left: W / 2 - (3 * fw + 2 * fgap) / 2 + col * (fw + fgap),
                  top: y + 260 + fy,
                  opacity: feed * lin(t, fallStart + 0.3, fallStart + 0.9, 1, 0),
                  transform: `rotate(${rot}deg)`,
                }}
              />
            );
          }),
        )}
      {/* Bewegungsunschärfe-Gefühl: dunkle Ränder oben und unten */}
      <AbsoluteFill style={{ background: 'linear-gradient(180deg, rgba(10,5,6,.95) 0%, rgba(10,5,6,0) 22%, rgba(10,5,6,0) 78%, rgba(10,5,6,.95) 100%)', opacity: feed }} />
    </AbsoluteFill>
  );
};

// ------------------------------------------------------------------------------------------------ S5 Was wartet

const ICONS: { at: number; label: string; paths: string[]; circles?: [number, number, number][] }[] = [
  {
    at: 20.38,
    label: 'THE BOOK',
    paths: ['M10 22 C 28 12, 46 12, 60 22 L 60 78 C 46 68, 28 68, 10 78 Z', 'M60 22 C 74 12, 92 12, 110 22 L 110 78 C 92 68, 74 68, 60 78 Z'],
  },
  {
    at: 21.56,
    label: 'THE SONG',
    paths: ['M44 70 L 44 16 L 92 6 L 92 60', 'M44 26 L 92 16'],
    circles: [
      [32, 70, 12],
      [80, 60, 12],
    ],
  },
  {
    at: 23.24,
    label: 'THE CALL',
    paths: ['M22 92 C 22 62, 98 62, 98 92'],
    circles: [[60, 34, 20]],
  },
];

const S5: React.FC<{ t: number }> = ({ t }) => {
  const a = sceneAlpha(t, SCENES.s5);
  if (a <= 0) return null;
  const waiting = ease(t, 25.8, 26.4);
  return (
    <AbsoluteFill style={{ opacity: a, transform: `scale(${drift(t, SCENES.s5)})` }}>
      {ICONS.map((icon, i) => {
        const draw = ease(t, icon.at, icon.at + 0.9);
        const glow = waiting * (0.75 + 0.25 * Math.sin(t * 3 + i));
        const color = waiting > 0 ? `rgba(255,181,71,${0.55 + 0.45 * glow})` : 'rgba(255,255,255,.85)';
        const x = W / 2 + (i - 1) * 300;
        return (
          <div key={i} style={{ position: 'absolute', left: x - 120, top: 1150, width: 240, textAlign: 'center', opacity: lin(t, icon.at - 0.05, icon.at + 0.2) }}>
            <svg width={240} height={190} viewBox="0 0 120 100" style={{ filter: waiting ? `drop-shadow(0 0 ${18 * glow}px ${AMBER})` : undefined }}>
              {icon.paths.map((d, k) => (
                <path key={k} d={d} pathLength={1} fill="none" stroke={color} strokeWidth={4} strokeLinecap="round" strokeLinejoin="round" strokeDasharray={1} strokeDashoffset={1 - draw} />
              ))}
              {icon.circles?.map(([cx, cy, r], k) => (
                <circle key={k} cx={cx} cy={cy} r={r} pathLength={1} fill="none" stroke={color} strokeWidth={4} strokeDasharray={1} strokeDashoffset={1 - draw} />
              ))}
            </svg>
            <div style={{ fontFamily: FONT, fontWeight: 800, fontSize: 30, letterSpacing: 4, marginTop: 14, color: waiting ? AMBER : 'rgba(255,255,255,.6)', opacity: draw }}>
              {icon.label}
            </div>
          </div>
        );
      })}
    </AbsoluteFill>
  );
};

// ------------------------------------------------------------------------------------------------ S6 Die App

export const BarIcon: React.FC<{ kind: 'home' | 'shorts' | 'plus' | 'subs' | 'you'; color: string }> = ({ kind, color }) => {
  const p = { fill: 'none', stroke: color, strokeWidth: 3.2, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const };
  return (
    <svg width={64} height={64} viewBox="0 0 40 40">
      {kind === 'home' && <path d="M7 18 L20 7 L33 18 L33 33 L7 33 Z" {...p} />}
      {kind === 'shorts' && (
        <>
          <rect x="11" y="5" width="18" height="30" rx="5" {...p} />
          <path d="M17 15 L24 20 L17 25 Z" fill={color} stroke="none" />
        </>
      )}
      {kind === 'plus' && (
        <>
          <circle cx="20" cy="20" r="14" {...p} />
          <path d="M20 13 L20 27 M13 20 L27 20" {...p} />
        </>
      )}
      {kind === 'subs' && (
        <>
          <rect x="6" y="12" width="28" height="21" rx="4" {...p} />
          <path d="M10 7 L30 7" {...p} />
          <path d="M17 18 L24 22.5 L17 27 Z" fill={color} stroke="none" />
        </>
      )}
      {kind === 'you' && (
        <>
          <circle cx="20" cy="15" r="6.5" {...p} />
          <path d="M8 34 C 8 24, 32 24, 32 34" {...p} />
        </>
      )}
    </svg>
  );
};

const S6: React.FC<{ t: number }> = ({ t }) => {
  const a = sceneAlpha(t, SCENES.s6);
  if (a <= 0) return null;
  const barIn = ease(t, 27.0, 27.7);
  const shake = t > 30.25 && t < 30.85 ? Math.sin(t * 90) * 6 * lin(t, 30.25, 30.85) : 0;
  const drop = Math.max(0, t - 30.85);
  const close = ease(t, 31.2, 31.9);
  const items: { kind: 'home' | 'shorts' | 'plus' | 'subs' | 'you'; label: string }[] = [
    { kind: 'home', label: 'Home' },
    { kind: 'shorts', label: 'Shorts' },
    { kind: 'plus', label: '' },
    { kind: 'subs', label: 'Subs' },
    { kind: 'you', label: 'You' },
  ];
  const barW = 900;
  const barTop = 1260;
  const slot5 = barW / 5;
  const slot4 = barW / 4;
  const pos4 = { home: 0, plus: 3, subs: 1, you: 2 } as const; // wie unsere Leiste: Home, Subs, You, Plus
  const video = ease(t, 32.8, 33.4);
  const logo = ease(t, 30.2, 30.7);
  return (
    <AbsoluteFill style={{ opacity: a, transform: `scale(${drift(t, SCENES.s6, 0.04)})` }}>
      {/* Das lange Video, das bleibt */}
      <div style={{ position: 'absolute', left: W / 2 - 400, top: 700, width: 800, height: 450, opacity: video, transform: `translateY(${40 * (1 - video)}px)` }}>
        <div style={{ position: 'absolute', inset: 0, borderRadius: 32, overflow: 'hidden', background: 'linear-gradient(135deg, #3a2a55, #10283d)', border: '2px solid rgba(255,255,255,.18)', boxShadow: '0 30px 80px rgba(0,0,0,.6)' }}>
          <div style={{ position: 'absolute', left: 360, top: 185, width: 0, height: 0, borderTop: '40px solid transparent', borderBottom: '40px solid transparent', borderLeft: '68px solid rgba(255,255,255,.9)' }} />
          <div style={{ position: 'absolute', left: 0, bottom: 0, height: 10, width: `${42 + 10 * lin(t, 32.8, 41)}%`, background: RED }} />
        </div>
      </div>
      {/* Logo klein über der Leiste, sobald der Name fällt */}
      <div style={{ position: 'absolute', left: W / 2 - 60, top: barTop - 190, opacity: logo * (1 - video), transform: `scale(${0.8 + 0.2 * logo})` }}>
        <Logo size={120} />
      </div>
      <div
        style={{
          position: 'absolute',
          left: W / 2 - barW / 2,
          top: barTop + 60 * (1 - barIn),
          width: barW,
          height: 170,
          borderRadius: 44,
          background: '#1C1C1C',
          border: '2px solid rgba(255,255,255,.1)',
          boxShadow: '0 30px 80px rgba(0,0,0,.6)',
          opacity: barIn,
        }}
      >
        {items.map((it) => {
          const isShorts = it.kind === 'shorts';
          const i5 = items.indexOf(it);
          const x5 = i5 * slot5 + slot5 / 2;
          const x4 = isShorts ? x5 : pos4[it.kind as keyof typeof pos4] * slot4 + slot4 / 2;
          const x = x5 + (x4 - x5) * close;
          const glow = isShorts ? 0.6 + 0.4 * Math.sin(t * 5) : 0;
          const color = isShorts ? RED : '#fff';
          const fy = isShorts ? 0.5 * 5200 * drop * drop : 0;
          const rot = isShorts ? drop * 160 : 0;
          return (
            <div
              key={it.kind}
              style={{
                position: 'absolute',
                left: x - 70 + (isShorts ? shake : 0),
                top: 28 + fy,
                width: 140,
                textAlign: 'center',
                opacity: isShorts ? lin(t, 30.85, 31.5, 1, 0) : 1,
                transform: `rotate(${rot}deg)`,
                filter: isShorts ? `drop-shadow(0 0 ${20 * glow}px ${RED})` : undefined,
              }}
            >
              <BarIcon kind={it.kind} color={color} />
              <div style={{ fontFamily: FONT, fontWeight: 800, fontSize: 24, color, marginTop: 4 }}>{it.label}</div>
            </div>
          );
        })}
      </div>
    </AbsoluteFill>
  );
};

// ------------------------------------------------------------------------------------------------ S7 Tester

const S7: React.FC<{ t: number }> = ({ t }) => {
  const a = sceneAlpha(t, SCENES.s7);
  if (a <= 0) return null;
  const logo = ease(t, 34.5, 35.2);
  const soon = ease(t, 34.75, 35.3);
  const cta = ease(t, 38.3, 38.8);
  const android = ease(t, 37.3, 37.8);
  const pulse = 1 + 0.035 * Math.sin((t - 38.3) * 4) * cta;
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <div style={{ position: 'absolute', top: 360, width: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', opacity: logo, transform: `translateY(${30 * (1 - logo)}px)` }}>
        <Logo size={190} />
        <div style={{ marginTop: 28 }}>
          <BrandName size={92} />
        </div>
      </div>
      <Pill text="LAUNCHING SOON" size={32} style={{ top: 750, opacity: soon }} />
      <div style={{ position: 'absolute', top: 1010, width: '100%', textAlign: 'center', opacity: cta, transform: `scale(${(0.85 + 0.15 * cta) * pulse})` }}>
        <svg width={110} height={100} viewBox="0 0 44 40" style={{ marginBottom: 18 }}>
          <path d="M6 4 H38 A4 4 0 0 1 42 8 V26 A4 4 0 0 1 38 30 H18 L9 37 V30 H6 A4 4 0 0 1 2 26 V8 A4 4 0 0 1 6 4 Z" fill={RED} />
          <path d="M11 13 H33 M11 20 H26" stroke="#fff" strokeWidth={3} strokeLinecap="round" />
        </svg>
        <div style={{ fontFamily: FONT, fontWeight: 900, fontSize: 84, color: '#fff', lineHeight: 1.05, letterSpacing: -2 }}>COMMENT</div>
        <div style={{ fontFamily: FONT, fontWeight: 900, fontSize: 120, color: RED, lineHeight: 1.05, letterSpacing: -3, textShadow: '0 0 40px rgba(229,23,43,.6)' }}>“TESTER”</div>
      </div>
      <Pill text="ANDROID ONLY" size={34} color={AMBER} style={{ top: 1420, opacity: android }} />
    </AbsoluteFill>
  );
};

// ------------------------------------------------------------------------------------------------ Wörter

const KEY = new Set(['FORGET', 'TODAY', 'THIS', 'FIVE', 'MONTHS', 'NEVER', 'DECIDED', 'HOUR', 'REMEMBER', 'SINGLE', 'STILL', 'WAITING', 'REMOVES', 'STAY', 'TESTERS', 'ANDROID', 'F*CK SHORTS']);

const wordY = (t: number) => {
  if (t < SCENES.s2[0]) return 980;
  if (t < SCENES.s4[0]) return 470;
  if (t < SCENES.s5[0]) return 940;
  if (t < SCENES.s6[0]) return 820;
  if (t < SCENES.s7[0]) return 470;
  return 1130;
};

const Words: React.FC<{ t: number }> = ({ t }) => {
  if (t > 38.3) return null; // ab hier steht die Aufforderung
  let idx = -1;
  for (let i = 0; i < WORDS.length; i++) if (WORDS[i][1] <= t + 0.02) idx = i;
  if (idx < 0) return null;
  const [word, start] = WORDS[idx];
  const next = idx + 1 < WORDS.length ? WORDS[idx + 1][1] : start + 1;
  const end = Math.min(next, start + 0.95);
  if (t > end) return null;
  const pop = ease(t, start, start + 0.12);
  const isKey = KEY.has(word.replace(/[^A-Z*' ]/g, ''));
  const warm = t > SCENES.s5[0] && t < SCENES.s6[0];
  const color = isKey ? (warm ? AMBER : RED) : '#fff';
  const len = word.length;
  const size = Math.min(150, 900 / Math.max(4, len * 0.68));
  return (
    <div
      style={{
        position: 'absolute',
        left: 0,
        width: W,
        top: wordY(t),
        transform: `translateY(-50%) scale(${0.82 + 0.18 * pop})`,
        textAlign: 'center',
        fontFamily: FONT,
        fontWeight: 900,
        fontSize: size,
        letterSpacing: -size * 0.02,
        color,
        opacity: pop,
        textShadow: '0 8px 40px rgba(0,0,0,.95), 0 0 2px rgba(0,0,0,.9)',
      }}
    >
      {word === 'F*CK SHORTS' ? (
        <>
          F<span style={{ color: RED }}>*</span>CK SHORTS
        </>
      ) : (
        word
      )}
    </div>
  );
};

// ------------------------------------------------------------------------------------------------ Video

export const Ad: React.FC<{ withVoice: boolean }> = ({ withVoice }) => {
  useFonts();
  const frame = useCurrentFrame();
  const t = frame / FPS;
  return (
    <AbsoluteFill style={{ background: BG, overflow: 'hidden' }}>
      <Background t={t} />
      <S1 t={t} />
      <S2 t={t} />
      <S3S4 t={t} />
      <S5 t={t} />
      <S6 t={t} />
      <S7 t={t} />
      <Words t={t} />
      {/* Leichte Vignette */}
      <AbsoluteFill style={{ background: 'radial-gradient(circle at 50% 50%, transparent 55%, rgba(0,0,0,.55) 100%)', pointerEvents: 'none' }} />
      {withVoice && <Audio src={staticFile('voice.wav')} />}
    </AbsoluteFill>
  );
};
