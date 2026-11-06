import React, { useEffect, useState } from 'react';
import { AbsoluteFill, Audio, continueRender, delayRender, random, staticFile, useCurrentFrame } from 'remotion';
import { Logo, BrandName, Pill, ease, lin, RED, AMBER, FONT } from './Ad';

/**
 * Video 3 "Break the pattern": Bild passend zu einem fremden TikTok-Sound (der Sound selbst kommt NICHT in die
 * hochgeladene Datei; auf TikTok wird er per "Sound verwenden" dazugenommen). Alles gezeichnet: Person im Bett mit
 * Handy, Augen in Grossaufnahme, Zimmer bei Nacht, Wörter im Takt, Schluss mit unserer App.
 */
export const FPS3 = 30;
export const DURATION3_S = 17.5;

const W = 1080;
const H = 1920;
const BOLD = 'Anton';
const SCRIPT = 'DancingScript';

const useFonts3 = () => {
  const [handle] = useState(() => delayRender('fonts3'));
  useEffect(() => {
    const faces = [
      new FontFace(BOLD, `url(${staticFile('anton-latin-400-normal.woff2')})`),
      new FontFace(SCRIPT, `url(${staticFile('dancing-script-latin-700-normal.woff2')})`, { weight: '700' }),
      new FontFace(FONT, `url(${staticFile('montserrat-latin-900-normal.woff2')})`, { weight: '900' }),
      new FontFace(FONT, `url(${staticFile('montserrat-latin-800-normal.woff2')})`, { weight: '800' }),
    ];
    Promise.all(faces.map((f) => f.load().then((x) => document.fonts.add(x)))).then(() => continueRender(handle));
  }, [handle]);
};

// Zeiten aus dem Sound (Wort-Anfänge in Sekunden)
type Tok = { w: string; s: number; style: 'bold' | 'script' | 'vert' | 'vertR'; x: number; y: number; size: number };
const P1: Tok[] = [
  { w: 'BREAK', s: 0.0, style: 'bold', x: 0.5, y: 0.2, size: 150 },
  { w: 'THE', s: 0.62, style: 'bold', x: 0.5, y: 0.28, size: 110 },
  { w: 'PATTERN', s: 0.8, style: 'bold', x: 0.5, y: 0.36, size: 150 },
  { w: 'today', s: 1.06, style: 'script', x: 0.6, y: 0.41, size: 130 },
  { w: 'or', s: 1.38, style: 'script', x: 0.3, y: 0.56, size: 90 },
  { w: 'THE', s: 1.72, style: 'vert', x: 0.36, y: 0.66, size: 110 },
  { w: 'LOOP', s: 1.88, style: 'vert', x: 0.48, y: 0.66, size: 170 },
  { w: 'WILL', s: 2.1, style: 'bold', x: 0.33, y: 0.76, size: 80 },
  { w: 'repeat', s: 2.32, style: 'script', x: 0.62, y: 0.78, size: 120 },
  { w: 'TOMORROW', s: 2.58, style: 'vertR', x: 0.72, y: 0.62, size: 90 },
];
const P2: Tok[] = [
  { w: 'BREAK', s: 3.68, style: 'bold', x: 0.5, y: 0.25, size: 240 },
  { w: 'the pattern', s: 3.76, style: 'script', x: 0.52, y: 0.33, size: 120 },
  { w: 'TODAY', s: 4.22, style: 'vert', x: 0.17, y: 0.3, size: 120 },
  { w: 'or', s: 4.56, style: 'script', x: 0.8, y: 0.42, size: 100 },
  { w: 'THE', s: 4.96, style: 'bold', x: 0.5, y: 0.6, size: 130 },
  { w: 'LOOP', s: 5.12, style: 'bold', x: 0.5, y: 0.69, size: 220 },
  { w: 'will repeat', s: 5.34, style: 'script', x: 0.5, y: 0.77, size: 110 },
  { w: 'TOMORROW', s: 5.84, style: 'vertR', x: 0.84, y: 0.66, size: 100 },
];
const P3: Tok[] = [
  { w: 'YOU', s: 6.84, style: 'bold', x: 0.38, y: 0.36, size: 140 },
  { w: 'MUST', s: 6.98, style: 'bold', x: 0.4, y: 0.44, size: 190 },
  { w: 'break', s: 7.14, style: 'script', x: 0.42, y: 0.5, size: 130 },
  { w: 'THE', s: 7.44, style: 'bold', x: 0.6, y: 0.33, size: 120 },
  { w: 'PATTERN', s: 7.64, style: 'vertR', x: 0.78, y: 0.42, size: 120 },
  { w: 'TODAY', s: 7.92, style: 'bold', x: 0.52, y: 0.27, size: 130 },
  { w: 'or', s: 8.3, style: 'script', x: 0.25, y: 0.55, size: 100 },
  { w: 'THE', s: 8.64, style: 'vert', x: 0.3, y: 0.62, size: 120 },
  { w: 'LOOP', s: 8.8, style: 'vert', x: 0.43, y: 0.64, size: 190 },
  { w: 'WILL', s: 9.02, style: 'bold', x: 0.62, y: 0.68, size: 110 },
];
const PHRASES: [Tok[], number][] = [
  [P1, 3.17],
  [P2, 6.41],
  [P3, 9.2],
];
const END_WORDS: [string, number, boolean][] = [
  ["DON'T", 9.2, false],
  ['LET', 9.6, false],
  ['YOUR', 10.0, false],
  ['PHONE', 10.2, false],
  ['TAKE', 10.4, false],
  ['YOUR', 10.6, false],
  ['LIFE', 10.8, true],
  ['AWAY', 11.4, true],
];
const END_OFF = 12.0;

// ------------------------------------------------------------------------------------------------ Licht vom Handy

/** Farbe des Handy-Lichts: wechselt alle ~0,45 s (jemand scrollt durch Shorts). */
const screenColor = (t: number) => {
  const k = Math.floor(t / 0.45);
  const hues = [210, 330, 20, 180, 270, 45, 0, 150];
  const h = hues[k % hues.length];
  const flash = 1 - Math.min(1, (t % 0.45) / 0.12); // kurz heller beim Wischen
  return { h, flash };
};

// ------------------------------------------------------------------------------------------------ Gemeinsam

const SKIN = '#7a5c4b';
const SKIN_LIT = '#a27b63';

/** Handy-Bildschirm mit Short-Feed: ein Kärtchen füllt den Bildschirm, beim Wischen rutscht das nächste hoch. */
const PhoneFeed: React.FC<{ x: number; y: number; w: number; h: number; t: number; r?: number; ui?: boolean }> = ({ x, y, w, h, t, r = 10, ui = true }) => {
  const k = Math.floor(t / 0.45);
  const p = Math.min(1, (t % 0.45) / 0.15);
  const slide = 1 - (1 - p) ** 3;
  const hues = [210, 330, 20, 180, 270, 45, 0, 150];
  const id = `pf${Math.round(x)}_${Math.round(y)}`;
  const card = (hue: number, oy: number, key: string) => (
    <g key={key} transform={`translate(0 ${oy})`}>
      <rect x={x} y={y} width={w} height={h} fill={`hsl(${hue} 65% 45%)`} />
      <circle cx={x + w * 0.45} cy={y + h * 0.45} r={w * 0.28} fill={`hsl(${(hue + 50) % 360} 80% 65%)`} opacity={0.8} />
      {ui && (
        <>
          <rect x={x + w * 0.08} y={y + h * 0.82} width={w * 0.5} height={h * 0.035} rx={h * 0.02} fill="#fff" opacity={0.8} />
          <rect x={x + w * 0.08} y={y + h * 0.88} width={w * 0.32} height={h * 0.03} rx={h * 0.015} fill="#fff" opacity={0.5} />
          {[0, 1, 2].map((i) => (
            <circle key={i} cx={x + w * 0.88} cy={y + h * (0.5 + i * 0.1)} r={w * 0.05} fill="#fff" opacity={0.75} />
          ))}
        </>
      )}
    </g>
  );
  return (
    <g>
      <defs>
        <clipPath id={id}>
          <rect x={x} y={y} width={w} height={h} rx={r} />
        </clipPath>
      </defs>
      <g clipPath={`url(#${id})`}>
        {card(hues[(k + hues.length - 1) % hues.length], -slide * h, 'a')}
        {card(hues[k % hues.length], (1 - slide) * h, 'b')}
      </g>
    </g>
  );
};

/** Feines Korn, damit es wie gemalt wirkt. */
const Grain: React.FC<{ t: number; opacity?: number }> = ({ t, opacity = 0.12 }) => (
  <svg width={W} height={H} style={{ position: 'absolute', inset: 0, mixBlendMode: 'overlay', opacity }}>
    <filter id={`grain${Math.floor(t * 12)}`}>
      <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves={2} seed={Math.floor(t * 12)} />
    </filter>
    <rect width={W} height={H} filter={`url(#grain${Math.floor(t * 12)})`} />
  </svg>
);

// ------------------------------------------------------------------------------------------------ Szene 1: im Bett (Gesicht auf dem Kissen)

const BedScene: React.FC<{ t: number }> = ({ t }) => {
  const { h, flash } = screenColor(t);
  const zoom = 1.04 + 0.08 * lin(t, 0, 3.2);
  const breathe = Math.sin(t * 2.0) * 5;
  const blink = t > 1.55 && t < 1.75 ? Math.sin(((t - 1.55) / 0.2) * Math.PI) : 0;
  const light = `hsla(${h},75%,62%,${0.42 + 0.2 * flash})`;
  // Kopf liegt schräg auf dem Kissen, Blick nach unten rechts aufs Handy
  return (
    <AbsoluteFill style={{ background: '#090a12', transform: `scale(${zoom})` }}>
      <svg width={W} height={H} viewBox={`0 0 ${W} ${H}`}>
        <defs>
          <radialGradient id="pillow" cx="0.35" cy="0.4" r="0.7">
            <stop offset="0" stopColor="#4b5272" />
            <stop offset="1" stopColor="#151829" />
          </radialGradient>
          <radialGradient id="skin1" cx="0.62" cy="0.7" r="0.75">
            <stop offset="0" stopColor={SKIN_LIT} />
            <stop offset="1" stopColor="#4a352b" />
          </radialGradient>
          <radialGradient id="glow1" cx="0.5" cy="0.5" r="0.5">
            <stop offset="0" stopColor={light} />
            <stop offset="1" stopColor="rgba(0,0,0,0)" />
          </radialGradient>
          <linearGradient id="blanket1" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#262c52" />
            <stop offset="1" stopColor="#0c0f22" />
          </linearGradient>
        </defs>
        {/* Kissen */}
        <path d="M-60 520 C 200 380, 760 400, 1140 560 L 1140 1500 L -60 1500 Z" fill="url(#pillow)" />
        <path d="M-60 560 C 240 470, 640 480, 900 560" stroke="#5a6185" strokeWidth={6} fill="none" opacity={0.5} />
        <g transform={`rotate(-14 540 960) translate(0 ${breathe * 0.4})`}>
          {/* Ohr, Hals */}
          <ellipse cx={205} cy={980} rx={48} ry={80} fill="#5e4538" />
          <ellipse cx={872} cy={985} rx={44} ry={78} fill="#6a4d3f" />
          <path d="M880 945 Q 900 985 878 1025" stroke="#4e3628" strokeWidth={6} fill="none" opacity={0.7} />
          {/* Gesicht */}
          <ellipse cx={540} cy={960} rx={340} ry={430} fill="url(#skin1)" />
          <ellipse cx={540} cy={960} rx={340} ry={430} fill={`hsla(${h},70%,60%,${0.18 + 0.12 * flash})`} />
          {/* Haare, Strähnen über der Stirn */}
          <path d="M180 900 C 150 560, 360 450, 560 480 C 760 500, 900 620, 880 820 C 850 745, 780 705, 700 712 C 620 720, 555 752, 490 785 C 425 815, 355 805, 305 770 C 270 800, 240 845, 220 900 Z" fill="#1e1614" />
          {/* Augenbrauen, müde */}
          <path d="M335 850 Q 410 822 480 850" stroke="#2a1d17" strokeWidth={22} strokeLinecap="round" fill="none" />
          <path d="M600 850 Q 670 824 745 852" stroke="#2a1d17" strokeWidth={22} strokeLinecap="round" fill="none" />
          <Eye cx={410} cy={930} t={t} lid={0.42 + 0.58 * blink} scale={0.42} lookX={18} lookY={14} />
          <Eye cx={672} cy={930} t={t} lid={0.42 + 0.58 * blink} scale={0.42} lookX={18} lookY={14} />
          {/* Augenringe */}
          <path d="M350 985 Q 410 1010 470 988" stroke="#4d3329" strokeWidth={10} fill="none" opacity={0.6} />
          <path d="M612 985 Q 672 1010 732 988" stroke="#4d3329" strokeWidth={10} fill="none" opacity={0.6} />
          {/* Nase, Mund */}
          <path d="M545 960 Q 560 1090 520 1130 Q 548 1150 585 1138" stroke="#4e3628" strokeWidth={9} fill="none" />
          <path d="M480 1238 Q 545 1226 608 1240" stroke="#4e3628" strokeWidth={9} fill="none" strokeLinecap="round" />
        </g>
        {/* Lichtkegel vom Handy unten rechts */}
        <circle cx={800} cy={1350} r={620} fill="url(#glow1)" opacity={0.75} />
        {/* Decke bis zum Kinn */}
        <path d={`M-60 ${1330 + breathe} C 260 ${1250 + breathe}, 700 ${1300 + breathe}, 1140 ${1270 + breathe} L 1140 1980 L -60 1980 Z`} fill="url(#blanket1)" />
        <path d={`M120 ${1420 + breathe} C 340 ${1380 + breathe}, 560 ${1440 + breathe}, 760 ${1400 + breathe}`} stroke="#323a6a" strokeWidth={8} fill="none" opacity={0.6} />
        {/* Hand hält das Handy (wir sehen die Rückseite, die Finger liegen hinten drauf) */}
        <g transform={`rotate(-18 880 1560) translate(0 ${breathe * 0.5})`}>
          {/* Handgelenk und Handfläche kommen von unten rechts */}
          <path d="M930 1980 C 940 1840, 960 1760, 1000 1700 L 1110 1720 L 1120 1980 Z" fill="url(#skin1)" />
          <rect x={760} y={1340} width={250} height={470} rx={40} fill="#0c0c11" stroke="#2c2c36" strokeWidth={6} />
          <rect x={792} y={1372} width={74} height={110} rx={26} fill="#15151c" />
          <circle cx={829} cy={1404} r={18} fill="#22222c" />
          <circle cx={829} cy={1450} r={14} fill="#22222c" />
          <rect x={752} y={1332} width={266} height={486} rx={46} fill="none" stroke={light} strokeWidth={6} opacity={0.55} />
          {/* Handfläche umfasst die rechte Kante, die Finger kommen daraus und liegen hinten auf dem Handy */}
          <path d="M985 1470 C 1030 1450, 1090 1460, 1110 1500 L 1120 1840 C 1080 1860, 1020 1850, 990 1820 C 1000 1720, 1000 1580, 985 1470 Z" fill="url(#skin1)" />
          {[0, 1, 2, 3].map((i) => {
            const y = 1505 + i * 72;
            const len = [150, 162, 156, 132][i];
            return (
              <g key={i}>
                <path d={`M1030 ${y} L ${1030 - len + 28} ${y} A 28 28 0 0 0 ${1030 - len + 28} ${y + 56} L 1030 ${y + 56} Z`} fill="url(#skin1)" />
                <path d={`M1030 ${y} L ${1030 - len + 28} ${y} A 28 28 0 0 0 ${1030 - len + 28} ${y + 56} L 1030 ${y + 56} Z`} fill={`hsla(${h},70%,60%,${0.1 + 0.1 * flash})`} />
                {/* Fingernagel-Andeutung und Knöchelfalte */}
                <path d={`M${1030 - len + 18} ${y + 14} q -8 14 0 28`} stroke="#5e4538" strokeWidth={4} fill="none" opacity={0.5} />
                <path d={`M${1030 - len * 0.45} ${y + 10} q 6 18 0 36`} stroke="#5e4538" strokeWidth={4} fill="none" opacity={0.45} />
              </g>
            );
          })}
          {/* Fugen zwischen den Fingern */}
          {[1, 2, 3].map((i) => (
            <path key={i} d={`M${1030 - 90} ${1505 + i * 72 - 4} L 1030 ${1505 + i * 72 - 4}`} stroke="#4a352b" strokeWidth={5} opacity={0.6} />
          ))}
          <path d="M985 1470 C 1000 1580, 1000 1720, 990 1820" stroke="#4a352b" strokeWidth={6} fill="none" opacity={0.35} />
        </g>
      </svg>
      <Grain t={t} />
      <AbsoluteFill style={{ background: 'radial-gradient(circle at 55% 55%, transparent 40%, rgba(0,0,0,.75) 100%)' }} />
      <div style={{ position: 'absolute', left: 80, top: 160, fontFamily: FONT, fontWeight: 800, fontSize: 54, color: '#ff3b3b', letterSpacing: 4, textShadow: '0 0 24px rgba(255,40,40,.8)', opacity: 0.8 }}>2:47 AM</div>
    </AbsoluteFill>
  );
};

// ------------------------------------------------------------------------------------------------ Szene 2: Augen

const Eye: React.FC<{ cx: number; cy?: number; t: number; lid: number; scale?: number; lookX?: number; lookY?: number }> = ({ cx, cy = 980, t, lid, scale = 1, lookX = 0, lookY = 0 }) => {
  const rx = 230 * scale;
  const ry = 120 * scale;
  const ix = cx + lookX * scale * 2;
  const iy = cy + lookY * scale * 2;
  const id = `eye${Math.round(cx)}_${Math.round(cy)}`;
  return (
    <g>
      <defs>
        <clipPath id={id}>
          <ellipse cx={cx} cy={cy} rx={rx} ry={ry} />
        </clipPath>
      </defs>
      <ellipse cx={cx} cy={cy} rx={rx} ry={ry} fill="#e8e3df" />
      <g clipPath={`url(#${id})`}>
        <circle cx={ix} cy={iy} r={105 * scale} fill="#5d7f9a" />
        <circle cx={ix} cy={iy} r={105 * scale} fill="none" stroke="#2c4252" strokeWidth={14 * scale} />
        <circle cx={ix} cy={iy} r={46 * scale} fill="#0a0a0a" />
        {/* Spiegelung: das Handy mit dem Feed, nur innerhalb des Handys */}
        <g opacity={0.7}>
          <PhoneFeed x={ix + 8 * scale} y={iy - 70 * scale} w={58 * scale} h={100 * scale} r={10 * scale} t={t} ui={scale > 0.6} />
        </g>
        <circle cx={ix - 30 * scale} cy={iy - 40 * scale} r={10 * scale} fill="#fff" opacity={0.8} />
        {/* Lid */}
        <rect x={cx - rx} y={cy - ry - 10} width={rx * 2} height={(ry * 2 + 20) * lid} fill={SKIN} />
      </g>
      <ellipse cx={cx} cy={cy} rx={rx} ry={ry} fill="none" stroke="#3b2a22" strokeWidth={8 * Math.max(scale, 0.6)} />
      {scale === 1 && (
        <path d={`M${cx - rx - 10} ${cy - ry - 40} Q ${cx} ${cy - ry - 110} ${cx + rx + 10} ${cy - ry - 50}`} stroke="#2a1d17" strokeWidth={34} fill="none" strokeLinecap="round" />
      )}
    </g>
  );
};

const EyesScene: React.FC<{ t: number }> = ({ t }) => {
  const { h, flash } = screenColor(t);
  // Schwere Lider, ein Blinzeln bei 5,0 s
  const blink = t > 5.0 && t < 5.25 ? Math.sin(((t - 5.0) / 0.25) * Math.PI) : 0;
  const lid = 0.22 + 0.06 * lin(t, 3.6, 6.4) + 0.78 * blink;
  const zoom = 1 + 0.08 * lin(t, 3.6, 6.4);
  return (
    <AbsoluteFill style={{ background: '#6e5242', transform: `scale(${zoom})` }}>
      <AbsoluteFill style={{ background: `radial-gradient(circle at 50% 55%, hsla(${h},70%,60%,${0.35 + 0.2 * flash}) 0%, rgba(0,0,0,.75) 75%)` }} />
      <svg width={W} height={H} viewBox={`0 0 ${W} ${H}`}>
        {/* Haare oben */}
        <path d="M0 0 H1080 V520 C 900 640, 760 560, 620 640 C 480 560, 300 660, 160 580 C 90 540, 40 560, 0 600 Z" fill="#15100d" />
        <Eye cx={270} t={t} lid={lid} />
        <Eye cx={810} t={t} lid={lid} />
        <path d="M520 1180 Q 540 1320 500 1380 Q 540 1400 580 1380" stroke="#4e3628" strokeWidth={10} fill="none" />
      </svg>
      <AbsoluteFill style={{ background: 'radial-gradient(circle at 50% 50%, transparent 40%, rgba(0,0,0,.7) 100%)' }} />
    </AbsoluteFill>
  );
};

// ------------------------------------------------------------------------------------------------ Szene 3: Zimmer

const RoomScene: React.FC<{ t: number }> = ({ t }) => {
  const { h, flash } = screenColor(t);
  const zoom = 1.1 - 0.1 * ease(t, 6.84, 9.2);
  const breathe = Math.sin(t * 1.8) * 4;
  const light = `hsla(${h},75%,62%,${0.5 + 0.22 * flash})`;
  const thumb = (t % 0.45) < 0.15 ? -10 * Math.sin(((t % 0.45) / 0.15) * Math.PI) : 0;
  return (
    <AbsoluteFill style={{ background: '#07080f', transform: `scale(${zoom})` }}>
      <svg width={W} height={H} viewBox={`0 0 ${W} ${H}`}>
        <defs>
          <radialGradient id="g3" cx="0.5" cy="0.5" r="0.5">
            <stop offset="0" stopColor={light} />
            <stop offset="1" stopColor="rgba(0,0,0,0)" />
          </radialGradient>
          <linearGradient id="hoodie" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0" stopColor="#141831" />
            <stop offset="1" stopColor="#2b3263" />
          </linearGradient>
          <radialGradient id="skin3" cx="0.75" cy="0.8" r="0.8">
            <stop offset="0" stopColor={SKIN_LIT} />
            <stop offset="1" stopColor="#3f2d24" />
          </radialGradient>
        </defs>
        {/* Zimmer: Fenster, Regal mit Uhr */}
        <rect x={70} y={260} width={300} height={420} rx={8} fill="#0d1226" stroke="#1a2142" strokeWidth={8} />
        <circle cx={300} cy={340} r={30} fill="#cfd7ff" opacity={0.25} />
        <rect x={640} y={470} width={380} height={20} fill="#141933" />
        {[0, 1, 2, 3].map((k) => (
          <rect key={k} x={660 + k * 52} y={380} width={38} height={90} fill={['#2a1f3d', '#1f2b3d', '#3d1f2a', '#1f3d33'][k]} />
        ))}
        <rect x={880} y={410} width={130} height={56} rx={8} fill="#0b0b0b" />
        <text x={945} y={452} textAnchor="middle" fontFamily={FONT} fontWeight={800} fontSize={36} fill="#ff3b3b">3:12</text>
        {/* Bett */}
        <path d="M0 1500 L 1080 1460 L 1080 1920 L 0 1920 Z" fill="#161a33" />
        <path d="M0 1500 L 1080 1460" stroke="#2a3060" strokeWidth={10} />
        {/* Lichtschein vom Handy */}
        <circle cx={700} cy={1240} r={520} fill="url(#g3)" opacity={0.9} />
        {/* Person von der Seite, Blick nach rechts unten: Rücken rund, Kopf weit nach vorne */}
        <g transform={`translate(0 ${breathe})`}>
          {/* Oberschenkel */}
          <path d="M330 1470 C 420 1430, 640 1420, 760 1440 L 770 1520 C 640 1520, 430 1530, 330 1540 Z" fill="#1a1f3d" />
          {/* Rücken (C-Kurve) und Bauch */}
          <path d="M330 1500 C 230 1360, 250 1150, 380 1040 C 460 975, 560 975, 620 1010 C 660 1080, 650 1180, 640 1250 C 640 1330, 660 1400, 650 1460 L 360 1500 Z" fill="url(#hoodie)" />
          {/* Kapuze im Nacken */}
          <path d="M400 1030 C 450 980, 560 975, 600 1010 C 560 1020, 470 1030, 430 1060 Z" fill="#11152a" />
          {/* Hals nach vorne */}
          <path d="M575 1010 C 610 1000, 660 1010, 680 1050 L 640 1090 C 620 1060, 600 1050, 575 1050 Z" fill="#4a352b" />
          {/* Kopf nach unten geneigt */}
          <g transform="rotate(38 700 990)">
            <ellipse cx={700} cy={990} rx={120} ry={140} fill="url(#skin3)" />
            <ellipse cx={700} cy={990} rx={120} ry={140} fill={`hsla(${h},70%,60%,${0.15 + 0.12 * flash})`} />
            {/* Haare: liegen auf dem Hinterkopf und oben auf dem Schädel */}
            <path d="M586 1035 C 560 920, 620 845, 712 846 C 778 848, 816 880, 822 925 C 800 912, 768 908, 740 916 C 700 928, 664 958, 645 1000 C 632 1030, 610 1050, 586 1035 Z" fill="#1e1614" />
            <ellipse cx={622} cy={1005} rx={20} ry={30} fill="#3f2d24" />
            {/* Gesicht im Profil, alles innerhalb des Kopfes: Auge halb zu, Nase als kleine Wölbung, Mund */}
            <path d="M752 962 Q 772 972 790 963" stroke="#2a1d17" strokeWidth={7} fill="none" strokeLinecap="round" />
            <path d="M754 969 Q 772 977 788 970" stroke={light} strokeWidth={3} fill="none" opacity={0.8} />
            <path d="M812 978 Q 836 1002 814 1018" stroke="#4e3628" strokeWidth={7} fill={SKIN} />
            <path d="M770 1060 Q 782 1066 794 1058" stroke="#4e3628" strokeWidth={6} fill="none" strokeLinecap="round" />
          </g>
          {/* Arme: Ellbogen auf den Knien, Hände mit Handy vor dem Bauch */}
          <path d="M520 1110 C 520 1220, 560 1320, 640 1340 C 690 1350, 740 1330, 760 1300" stroke="#232955" strokeWidth={74} strokeLinecap="round" fill="none" />
          {/* Handy: Bildschirm zeigt nach oben zum Gesicht, wir sehen die Kante */}
          <g transform="rotate(-42 790 1262)">
            <rect x={705} y={1250} width={170} height={24} rx={10} fill="#0b0b0f" stroke="#2c2c36" strokeWidth={3} />
            <rect x={711} y={1246} width={158} height={6} rx={3} fill={light} />
          </g>
          {/* Schein vom Bildschirm hoch ins Gesicht */}
          <path d="M760 1250 L 735 1060 L 850 1040 L 850 1200 Z" fill={light} opacity={0.12} />
          {/* Hand umfasst das Handy unten, Daumen scrollt oben auf dem Bildschirm */}
          <ellipse cx={765} cy={1300} rx={40} ry={34} fill="url(#skin3)" />
          <ellipse cx={812} cy={1238} rx={15} ry={25} fill="url(#skin3)" transform={`rotate(${-42 + 16 * Math.max(0, -thumb) / 10} 805 1258)`} />
        </g>
      </svg>
      <Grain t={t} />
      <AbsoluteFill style={{ background: 'radial-gradient(circle at 60% 60%, transparent 35%, rgba(0,0,0,.78) 100%)' }} />
    </AbsoluteFill>
  );
};

// ------------------------------------------------------------------------------------------------ Wörter

const TokView: React.FC<{ tok: Tok; t: number }> = ({ tok, t }) => {
  const d = t - tok.s;
  if (d < 0) return null;
  const pop = ease(t, tok.s, tok.s + 0.12);
  const rot = tok.style === 'vert' ? -90 : tok.style === 'vertR' ? 90 : tok.style === 'script' ? -6 : 0;
  const isScript = tok.style === 'script';
  return (
    <div
      style={{
        position: 'absolute',
        left: tok.x * W,
        top: tok.y * H,
        transform: `translate(-50%, -50%) rotate(${rot}deg) scale(${1.25 - 0.25 * pop})`,
        opacity: pop,
        fontFamily: isScript ? SCRIPT : BOLD,
        fontWeight: isScript ? 700 : 400,
        fontSize: tok.size,
        lineHeight: 1,
        color: '#fff',
        whiteSpace: 'nowrap',
        textShadow: '0 4px 24px rgba(0,0,0,.85)',
        letterSpacing: isScript ? 0 : 2,
      }}
    >
      {tok.w}
    </div>
  );
};

// ------------------------------------------------------------------------------------------------ Video

export const Ad3: React.FC<{ refSound: boolean }> = ({ refSound }) => {
  useFonts3();
  const frame = useCurrentFrame();
  const t = frame / FPS3;
  const phrase = PHRASES.find(([toks, end]) => t >= toks[0].s - 0.05 && t < end);
  let scene: React.ReactNode = null;
  if (t < 3.17) scene = <BedScene t={t} />;
  else if (t >= 3.6 && t < 6.41) scene = <EyesScene t={t} />;
  else if (t >= 6.84 && t < 9.2) scene = <RoomScene t={t} />;
  const endWord = t >= 9.2 && t < END_OFF ? END_WORDS.filter(([, s]) => s <= t).slice(-1)[0] : undefined;
  // Schluss: unsere App
  const logo = ease(t, 12.4, 12.75);
  const line1 = ease(t, 13.4, 13.7);
  const line2 = ease(t, 14.5, 14.8);
  const cta = ease(t, 15.4, 15.7);
  return (
    <AbsoluteFill style={{ background: '#000', overflow: 'hidden' }}>
      {scene}
      {phrase && (
        <AbsoluteFill>
          {phrase[0].map((tok, i) => (
            <TokView key={i} tok={tok} t={t} />
          ))}
        </AbsoluteFill>
      )}
      {endWord && (
        <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center' }}>
          <div style={{ fontFamily: BOLD, fontSize: 150, letterSpacing: 4, color: endWord[2] ? '#ff1f1f' : '#fff' }}>{endWord[0]}</div>
        </AbsoluteFill>
      )}
      {t >= 12.3 && (
        <AbsoluteFill>
          <div style={{ position: 'absolute', top: 470, width: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', opacity: logo, transform: `scale(${1.3 - 0.3 * logo})` }}>
            <Logo size={210} />
            <div style={{ marginTop: 26 }}>
              <BrandName size={100} />
            </div>
          </div>
          <div style={{ position: 'absolute', top: 900, width: '100%', textAlign: 'center', fontFamily: FONT, fontWeight: 800, fontSize: 52, color: '#fff', lineHeight: 1.3, opacity: line1, transform: `translateY(${20 * (1 - line1)}px)` }}>
            Removes Shorts from YouTube.
            <br />
            <span style={{ color: 'rgba(255,255,255,.65)' }}>Your videos stay.</span>
          </div>
          <div style={{ position: 'absolute', top: 1110, width: '100%', textAlign: 'center', fontFamily: FONT, fontWeight: 900, fontSize: 64, color: RED, opacity: line2, transform: `translateY(${20 * (1 - line2)}px)` }}>
            We're looking for testers
          </div>
          <div style={{ position: 'absolute', top: 1230, width: '100%', textAlign: 'center', fontFamily: FONT, fontWeight: 900, fontSize: 78, color: '#fff', opacity: cta, transform: `scale(${1 + 0.03 * Math.sin((t - 15.4) * 6) * cta})` }}>
            Comment “tester”
          </div>
          <Pill text="ANDROID ONLY" size={34} color={AMBER} style={{ top: 1370, opacity: cta }} />
          <div style={{ position: 'absolute', top: 1500, width: '100%', textAlign: 'center', fontFamily: FONT, fontWeight: 800, fontSize: 44, color: '#fff', lineHeight: 1.35, opacity: ease(t, 16.0, 16.3) }}>
            Releasing soon.
            <br />
            <span style={{ color: 'rgba(255,255,255,.7)' }}>More on our profile.</span>
          </div>
        </AbsoluteFill>
      )}
      {/* Kurze Störung beim Wechsel der Szenen (wie Schnitte im Original) */}
      {[3.17, 6.41, 9.2].map((c) => (t >= c && t < c + 0.06 ? <AbsoluteFill key={c} style={{ background: '#fff', opacity: 0.12 }} /> : null))}
      {refSound && <Audio src={staticFile('ref_sound.wav')} />}
    </AbsoluteFill>
  );
};
void random;
