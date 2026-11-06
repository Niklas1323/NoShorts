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

// ------------------------------------------------------------------------------------------------ Szene 1: im Bett

const BedScene: React.FC<{ t: number }> = ({ t }) => {
  const { h, flash } = screenColor(t);
  const push = 1 + 0.06 * lin(t, 0, 3.2);
  const breathe = Math.sin(t * 2.2) * 6;
  const thumb = (t % 0.45) < 0.15 ? -18 * Math.sin(((t % 0.45) / 0.15) * Math.PI) : 0;
  const light = `hsla(${h}, 80%, 65%, ${0.55 + 0.25 * flash})`;
  return (
    <AbsoluteFill style={{ background: '#05060c', transform: `scale(${push})` }}>
      <svg width={W} height={H} viewBox={`0 0 ${W} ${H}`}>
        <defs>
          <radialGradient id="glow" cx="0.5" cy="0.5" r="0.5">
            <stop offset="0" stopColor={light} />
            <stop offset="1" stopColor="rgba(0,0,0,0)" />
          </radialGradient>
          <linearGradient id="blanket" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#1b2140" />
            <stop offset="1" stopColor="#0b0e1f" />
          </linearGradient>
        </defs>
        <g transform="translate(540 1000) scale(1.35) translate(-540 -980)">
        {/* Wand mit Fenster und Mondlicht */}
        <rect x={680} y={180} width={300} height={420} rx={10} fill="#0d1226" stroke="#1d2547" strokeWidth={8} />
        <line x1={830} y1={180} x2={830} y2={600} stroke="#1d2547" strokeWidth={6} />
        <circle cx={900} cy={280} r={34} fill="#cfd7ff" opacity={0.35} />
        {/* Kissen */}
        <ellipse cx={430} cy={1010} rx={330} ry={120} fill="#202748" />
        {/* Lichtschein vom Handy */}
        <circle cx={540} cy={960} r={520} fill="url(#glow)" opacity={0.85} />
        {/* Kopf (liegt auf der Seite) */}
        <g transform={`translate(0, ${breathe * 0.3})`}>
          <ellipse cx={420} cy={930} rx={150} ry={170} fill="#8c6a55" />
          <ellipse cx={420} cy={930} rx={150} ry={170} fill={`hsla(${h},70%,60%,${0.28 + 0.15 * flash})`} />
          {/* Haare */}
          <path d="M262 960 C 230 780, 360 700, 470 730 C 560 750, 600 820, 575 900 C 545 860, 500 850, 470 880 C 440 850, 380 860, 350 895 C 320 880, 290 910, 262 960 Z" fill="#2a1f1c" stroke={light} strokeWidth={4} strokeOpacity={0.5} />
          <ellipse cx={268} cy={950} rx={22} ry={34} fill="#7a5a48" />
          {/* Augen: offen, starren aufs Handy */}
          <ellipse cx={470} cy={930} rx={20} ry={9} fill="#f2f2f2" />
          <circle cx={478} cy={930} r={7} fill="#1a1a1a" />
          <circle cx={480} cy={928} r={2.5} fill={light} />
          <ellipse cx={380} cy={935} rx={18} ry={8} fill="#f2f2f2" />
          <circle cx={388} cy={935} r={6} fill="#1a1a1a" />
          <path d="M448 912 Q 470 902 492 912" stroke="#3a2a22" strokeWidth={5} fill="none" />
          <path d="M360 918 Q 380 909 400 918" stroke="#3a2a22" strokeWidth={5} fill="none" />
          <path d="M415 1000 Q 432 1006 450 1000" stroke="#4a3328" strokeWidth={5} fill="none" />
        </g>
        {/* Decke über dem Körper */}
        <path d={`M120 1060 C 300 ${1000 + breathe}, 700 ${1010 + breathe}, 1080 1080 L 1080 1920 L 0 1920 L 0 1100 Z`} fill="url(#blanket)" />
        {/* Hand mit Handy */}
        <g transform={`translate(0, ${thumb * 0.3})`}>
          <rect x={590} y={840} width={150} height={290} rx={26} fill="#0b0b0f" stroke="#2b2b35" strokeWidth={5} />
          <rect x={600} y={850} width={130} height={270} rx={20} fill={`hsl(${h} 60% ${18 + 10 * flash}%)`} opacity={0.35} />
          <path d="M560 1050 C 590 1020, 640 1040, 650 1080 L 640 1140 C 610 1150, 570 1120, 560 1090 Z" fill="#8c6a55" />
          <ellipse cx={612 + thumb * 0.2} cy={1010 + thumb} rx={22} ry={34} fill="#8c6a55" />
        </g>
        </g>
      </svg>
      {/* Uhrzeit: zu spät */}
      <div style={{ position: 'absolute', left: 90, top: 1460, fontFamily: FONT, fontWeight: 800, fontSize: 60, color: '#ff3b3b', letterSpacing: 4, textShadow: '0 0 24px rgba(255,40,40,.8)', opacity: 0.85 }}>
        2:47 AM
      </div>
    </AbsoluteFill>
  );
};

// ------------------------------------------------------------------------------------------------ Szene 2: Augen

const Eye: React.FC<{ cx: number; t: number; lid: number }> = ({ cx, t, lid }) => {
  const { h, flash } = screenColor(t);
  const scroll = (t * 260) % 120;
  const cy = 980;
  const rx = 230;
  const ry = 120;
  const id = `eye${cx}`;
  return (
    <g>
      <defs>
        <clipPath id={id}>
          <ellipse cx={cx} cy={cy} rx={rx} ry={ry} />
        </clipPath>
      </defs>
      <ellipse cx={cx} cy={cy} rx={rx} ry={ry} fill="#e8e3df" />
      <g clipPath={`url(#${id})`}>
        <circle cx={cx} cy={cy} r={105} fill="#5d7f9a" />
        <circle cx={cx} cy={cy} r={105} fill="none" stroke="#2c4252" strokeWidth={14} />
        <circle cx={cx} cy={cy} r={46} fill="#0a0a0a" />
        {/* Spiegelung: Handy mit scrollenden Kärtchen */}
        <g opacity={0.75}>
          <rect x={cx + 8} y={cy - 70} width={58} height={100} rx={10} fill={`hsl(${h} 70% ${55 + 20 * flash}%)`} opacity={0.55} />
          {[0, 1, 2].map((k) => (
            <rect key={k} x={cx + 14} y={cy - 64 + ((k * 40 - scroll + 240) % 120)} width={46} height={30} rx={5} fill="#fff" opacity={0.25} />
          ))}
        </g>
        <circle cx={cx - 30} cy={cy - 40} r={10} fill="#fff" opacity={0.8} />
        {/* Lid */}
        <rect x={cx - rx} y={cy - ry - 10} width={rx * 2} height={(ry * 2 + 20) * lid} fill="#7a5c4b" />
      </g>
      <ellipse cx={cx} cy={cy} rx={rx} ry={ry} fill="none" stroke="#3b2a22" strokeWidth={8} />
      <path d={`M${cx - rx - 10} ${cy - ry - 40} Q ${cx} ${cy - ry - 110} ${cx + rx + 10} ${cy - ry - 50}`} stroke="#2a1d17" strokeWidth={34} fill="none" strokeLinecap="round" />
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
  const zoom = 1.12 - 0.12 * ease(t, 6.84, 9.2);
  const sway = Math.sin(t * 1.5) * 4;
  return (
    <AbsoluteFill style={{ background: '#070810', transform: `scale(${zoom})` }}>
      <svg width={W} height={H} viewBox={`0 0 ${W} ${H}`}>
        <defs>
          <radialGradient id="g3" cx="0.5" cy="0.5" r="0.5">
            <stop offset="0" stopColor={`hsla(${h},80%,62%,${0.6 + 0.25 * flash})`} />
            <stop offset="1" stopColor="rgba(0,0,0,0)" />
          </radialGradient>
        </defs>
        {/* Regal, Poster, Lampe aus */}
        <rect x={80} y={300} width={260} height={360} fill="#10142a" />
        <rect x={110} y={330} width={200} height={300} fill="#191f3d" />
        <rect x={700} y={420} width={320} height={22} fill="#141933" />
        {[0, 1, 2, 3].map((k) => (
          <rect key={k} x={720 + k * 60} y={330} width={40} height={90} fill={['#2a1f3d', '#1f2b3d', '#3d1f2a', '#1f3d33'][k]} />
        ))}
        {/* Uhr auf dem Regal */}
        <rect x={880} y={360} width={130} height={56} rx={8} fill="#0b0b0b" />
        <text x={945} y={402} textAnchor="middle" fontFamily={FONT} fontWeight={800} fontSize={36} fill="#ff3b3b">3:12</text>
        {/* Lichtschein */}
        <circle cx={600} cy={1160} r={460} fill="url(#g3)" />
        {/* Person sitzt gekrümmt auf der Bettkante */}
        <g transform={`translate(${sway}, 0)`}>
          <rect x={150} y={1420} width={800} height={160} rx={30} fill="#141933" />
          <path d="M380 1440 C 360 1260, 420 1120, 520 1080 C 600 1060, 650 1110, 640 1200 L 660 1440 Z" fill="#1d2142" />
          {/* Kopf nach vorne gebeugt */}
          <circle cx={600} cy={1000} r={88} fill="#8c6a55" />
          <circle cx={600} cy={1000} r={88} fill={`hsla(${h},70%,60%,${0.3 + 0.15 * flash})`} />
          <path d="M512 990 C 510 900, 640 880, 690 960 C 650 940, 600 950, 580 980 C 560 960, 530 970, 512 990 Z" fill="#14100e" />
          {/* Arme zum Handy */}
          <path d="M560 1150 C 600 1220, 680 1230, 720 1180" stroke="#1d2142" strokeWidth={60} strokeLinecap="round" fill="none" />
          <rect x={700} y={1110} width={70} height={130} rx={14} fill="#0b0b0f" stroke="#2b2b35" strokeWidth={4} transform="rotate(-20 735 1175)" />
          <rect x={706} y={1116} width={58} height={118} rx={10} fill={`hsl(${h} 70% ${45 + 20 * flash}%)`} transform="rotate(-20 735 1175)" />
        </g>
      </svg>
      <AbsoluteFill style={{ background: 'radial-gradient(circle at 55% 58%, transparent 35%, rgba(0,0,0,.75) 100%)' }} />
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
        </AbsoluteFill>
      )}
      {/* Kurze Störung beim Wechsel der Szenen (wie Schnitte im Original) */}
      {[3.17, 6.41, 9.2].map((c) => (t >= c && t < c + 0.06 ? <AbsoluteFill key={c} style={{ background: '#fff', opacity: 0.12 }} /> : null))}
      {refSound && <Audio src={staticFile('ref_sound.wav')} />}
    </AbsoluteFill>
  );
};
void random;
