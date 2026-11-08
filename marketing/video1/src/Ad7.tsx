import React, { useEffect, useState } from 'react';
import { AbsoluteFill, Audio, continueRender, delayRender, random, staticFile, useCurrentFrame } from 'remotion';
import { BarIcon, Logo, RED, ease, lin } from './Ad';
import WORDS from './words7.json';

/**
 * Werbevideo 7 "One day … the last time" (quer 16:9). Bunter, flacher Zeichenstil mit grossen Wort-Untertiteln
 * (wie die Vorlage), alles selbst gezeichnet. Anfang = dieselben Sätze wie die Vorlage, eigene Stimme (Leon,
 * public/voice7.wav). Musik: `music` = 'own' (selbst erzeugt, zum Hochladen) oder 'orig' (Musik der Vorlage, NUR ANSCHAUEN).
 */
export const FPS7 = 30;
export const DURATION7_S = 59.6;

const FONT = 'Anton';
const useAnton = () => {
  const [h] = useState(() => delayRender('anton'));
  useEffect(() => {
    new FontFace(FONT, `url(${staticFile('anton-latin-400-normal.woff2')})`).load().then((f) => {
      document.fonts.add(f);
      continueRender(h);
    });
  }, [h]);
};

// ------------------------------------------------------------------------------------------- Bausteine

const SKIN = '#F4A47C';
const SKIN2 = '#C97A55';

/** Flache Figur (Kopf gross, Haare, Körper). dir = 1 schaut nach rechts, -1 nach links. */
const Kid: React.FC<{
  x: number; y: number; s?: number; hair?: string; shirt?: string; pants?: string; skin?: string;
  dir?: number; sit?: boolean; eyes?: 'open' | 'closed' | 'happy'; arm?: number; mouth?: 'o' | 'smile' | 'flat'; cap?: string;
}> = ({ x, y, s = 1, hair = '#2E86DE', shirt = '#E2386B', pants = '#3B2A7A', skin = SKIN, dir = 1, sit, eyes = 'open', arm = 0, mouth = 'smile', cap }) => (
  <g transform={`translate(${x} ${y}) scale(${s * dir} ${s})`}>
    {/* Beine */}
    {sit ? (
      <path d="M-34 70 h70 q22 0 22 22 v8 h-26 v-4 h-66 z" fill={pants} />
    ) : (
      <>
        <rect x={-30} y={70} width={24} height={70} rx={10} fill={pants} />
        <rect x={6} y={70} width={24} height={70} rx={10} fill={pants} />
        <ellipse cx={-16} cy={142} rx={20} ry={9} fill="#1E1A3A" />
        <ellipse cx={20} cy={142} rx={20} ry={9} fill="#1E1A3A" />
      </>
    )}
    {/* Körper */}
    <path d="M-44 80 q0 -70 44 -70 q44 0 44 70 z" fill={shirt} />
    {/* Arm */}
    <g transform={`rotate(${arm} 30 30)`}>
      <rect x={22} y={22} width={20} height={58} rx={10} fill={shirt} />
      <circle cx={32} cy={82} r={11} fill={skin} />
    </g>
    {/* Kopf */}
    <circle cx={4} cy={-38} r={46} fill={skin} />
    <ellipse cx={46} cy={-34} rx={8} ry={11} fill={SKIN2} opacity={0.5} />
    {/* Haare */}
    {cap ? (
      <>
        <path d="M-44 -48 q2 -46 50 -46 q44 0 46 40 z" fill={cap} />
        <rect x={20} y={-58} width={52} height={12} rx={6} fill={cap} />
      </>
    ) : (
      <path d="M-46 -30 q-6 -64 52 -62 q52 2 46 46 q-20 -18 -50 -16 q-24 2 -34 22 q-6 8 -14 10 z" fill={hair} />
    )}
    {/* Gesicht */}
    {eyes === 'closed' ? (
      <path d="M18 -40 q8 6 16 0" stroke="#2A1B3D" strokeWidth={4} fill="none" strokeLinecap="round" />
    ) : eyes === 'happy' ? (
      <path d="M18 -36 q8 -8 16 0" stroke="#2A1B3D" strokeWidth={4} fill="none" strokeLinecap="round" />
    ) : (
      <circle cx={26} cy={-40} r={5} fill="#2A1B3D" />
    )}
    {mouth === 'o' ? <ellipse cx={30} cy={-14} rx={6} ry={8} fill="#7A2A3A" /> : mouth === 'smile' ? (
      <path d="M20 -18 q10 10 20 0" stroke="#7A2A3A" strokeWidth={4} fill="none" strokeLinecap="round" />
    ) : <path d="M22 -16 h14" stroke="#7A2A3A" strokeWidth={4} strokeLinecap="round" />}
  </g>
);

const Stars: React.FC<{ n: number; t: number; h?: number; seed?: string }> = ({ n, t, h = 600, seed = 's' }) => (
  <>
    {Array.from({ length: n }, (_, i) => {
      const x = random(`${seed}x${i}`) * 1920;
      const y = random(`${seed}y${i}`) * h;
      const tw = 0.4 + 0.6 * Math.abs(Math.sin(t * (1 + random(`${seed}f${i}`) * 2) + i));
      return <circle key={i} cx={x} cy={y} r={1.5 + random(`${seed}r${i}`) * 2.5} fill="#fff" opacity={tw} />;
    })}
  </>
);

const Pine: React.FC<{ x: number; y: number; s: number; c: string }> = ({ x, y, s, c }) => (
  <path transform={`translate(${x} ${y}) scale(${s})`} d="M0 -260 L60 -150 L35 -150 L90 -60 L55 -60 L110 30 L-110 30 L-55 -60 L-90 -60 L-35 -150 L-60 -150 Z" fill={c} />
);

const Cloud: React.FC<{ x: number; y: number; s: number; c?: string; o?: number }> = ({ x, y, s, c = '#fff', o = 1 }) => (
  <g transform={`translate(${x} ${y}) scale(${s})`} fill={c} opacity={o}>
    <circle cx={0} cy={0} r={50} /><circle cx={55} cy={-20} r={65} /><circle cx={120} cy={0} r={48} />
    <rect x={0} y={0} width={120} height={48} />
  </g>
);

/** Szene: Hintergrund-SVG mit langsamem Kamera-Zoom. */
const Scene: React.FC<{ t: number; a: number; b: number; children: (lt: number) => React.ReactNode; zoom?: number; ox?: number; oy?: number }> = ({ t, a, b, children, zoom = 0.07, ox = 960, oy = 540 }) => {
  if (t < a || t >= b) return null;
  const z = 1 + zoom * lin(t, a, b);
  return (
    <AbsoluteFill>
      <svg viewBox="0 0 1920 1080" width={1920} height={1080} style={{ position: 'absolute', inset: 0 }}>
        <g transform={`translate(${ox} ${oy}) scale(${z}) translate(${-ox} ${-oy})`}>{children(t - a)}</g>
      </svg>
    </AbsoluteFill>
  );
};

const Grad: React.FC<{ id: string; c: string[]; vertical?: boolean }> = ({ id, c, vertical = true }) => (
  <defs>
    <linearGradient id={id} x1="0" y1="0" x2={vertical ? '0' : '1'} y2={vertical ? '1' : '0'}>
      {c.map((col, i) => <stop key={i} offset={i / (c.length - 1)} stopColor={col} />)}
    </linearGradient>
  </defs>
);

// ------------------------------------------------------------------------------------------- Szenen

const T = [0, 2.9, 6.6, 8.6, 12.95, 16.4, 20.65, 23.7, 28.95, 32.45, 37.6, 40.35, 42.55, 43.9, 47.05, 50.75, 55.85, DURATION7_S];

/** 1: Büro bei Nacht, Hauswart wischt. */
const S1: React.FC<{ t: number }> = ({ t }) => (
  <Scene t={t} a={T[0]} b={T[1]}>{(lt) => (
    <>
      <Grad id="s1" c={['#7A33B8', '#B24BC2']} />
      <rect width={1920} height={1080} fill="url(#s1)" />
      <rect y={860} width={1920} height={220} fill="#9B2FA6" />
      <rect x={420} y={150} width={1000} height={560} rx={20} fill="#3B2490" stroke="#5B3AB8" strokeWidth={14} />
      {Array.from({ length: 70 }, (_, i) => <circle key={i} cx={470 + random(`m${i}`) * 900} cy={200 + random(`n${i}`) * 460} r={3 + random(`o${i}`) * 4} fill="#7C6BE0" opacity={0.6} />)}
      {[0, 1, 2, 3].map((i) => <circle key={i} cx={700 + i * 150} cy={300 + (i % 2) * 120} r={9 + 3 * Math.sin(lt * 4 + i)} fill="#E8E2FF" opacity={0.9} />)}
      {[360, 960, 1560].map((x, i) => (
        <g key={i}>
          <rect x={x - 160} y={0} width={320} height={34} rx={10} fill="#5B2A8A" />
          <rect x={x - 140} y={30} width={280} height={8} rx={4} fill="#FFE27A" />
          <path d={`M${x - 140} 38 L${x + 140} 38 L${x + 230} 860 L${x - 230} 860 Z`} fill="#FFF3C4" opacity={0.08} />
        </g>
      ))}
      <rect x={60} y={640} width={360} height={30} rx={8} fill="#E07ED8" />
      <rect x={90} y={670} width={300} height={190} fill="#C25BBE" />
      <rect x={150} y={520} width={120} height={150} rx={30} fill="#A85AC8" />
      <path d="M90 600 q20 -80 60 -40 q10 -60 40 -10 q30 -40 30 40 z" fill="#E8B83A" />
      <rect x={95} y={600} width={60} height={42} rx={8} fill="#7A2E8C" />
      <ellipse cx={1400} cy={880} rx={120} ry={18} fill="#7FE0F0" opacity={0.5} />
      <rect x={1560} y={760} width={110} height={110} rx={14} fill="#2E6BD8" />
      <g transform={`rotate(${8 * Math.sin(lt * 5)} 1480 520)`}>
        <rect x={1475} y={520} width={14} height={360} rx={7} fill="#3A6FE0" transform="rotate(30 1480 520)" />
        <ellipse cx={1300} cy={830} rx={70} ry={26} fill="#9FE8F5" />
      </g>
      <Kid x={1520} y={568} s={2.2} dir={-1} cap="#7A2E9E" shirt="#BFE6F5" pants="#8A3AB8" arm={-40 + 10 * Math.sin(lt * 5)} mouth="flat" />
    </>
  )}</Scene>
);

/** 2: Himmel, Sonne, Flugzeug ("absolutely no idea"). */
const S2: React.FC<{ t: number }> = ({ t }) => (
  <Scene t={t} a={T[1]} b={T[2]} zoom={0.05}>{(lt) => (
    <>
      <Grad id="s2" c={['#46CDE6', '#A6F2F5']} />
      <rect width={1920} height={1080} fill="url(#s2)" />
      <circle cx={180} cy={980} r={420} fill="#FF9A3C" />
      <circle cx={180} cy={980} r={330} fill="#FFB65C" />
      {[0, 1, 2, 3].map((i) => <Cloud key={i} x={((i * 520 + lt * 40) % 2300) - 200} y={180 + (i % 2) * 220} s={1 + (i % 3) * 0.3} o={0.95} />)}
      <g transform={`translate(${-200 + lt * 520} ${300 - lt * 40}) rotate(-6)`}>
        <rect x={0} y={0} width={180} height={34} rx={17} fill="#5A4FCF" />
        <path d="M70 10 L120 -50 L140 -50 L110 14 Z M70 24 L120 80 L140 80 L110 20 Z M10 8 L-10 -30 L10 -30 L30 8 Z" fill="#3F37A8" />
        {[0, 1, 2, 3].map((i) => <circle key={i} cx={40 + i * 26} cy={16} r={5} fill="#BFF4FF" />)}
      </g>
      <path d="M0 960 Q480 820 960 930 T1920 900 V1080 H0 Z" fill="#3FBF6A" />
      <path d="M0 1010 Q520 930 1100 1000 T1920 980 V1080 H0 Z" fill="#2E9E57" />
    </>
  )}</Scene>
);

/** 3: Vulkan ("You already have"). */
const S3: React.FC<{ t: number }> = ({ t }) => (
  <Scene t={t} a={T[2]} b={T[3]} zoom={0.12} oy={300}>{(lt) => (
    <>
      <Grad id="s3" c={['#4A0A1E', '#B3202E']} />
      <rect width={1920} height={1080} fill="url(#s3)" />
      <path d="M0 1080 L700 380 Q960 300 1220 380 L1920 1080 Z" fill="#3A0A16" />
      <path d="M820 330 Q960 280 1100 330 L1060 400 Q960 380 860 400 Z" fill="#FF7A1C" />
      <path d="M930 380 Q900 600 820 760 Q780 880 700 1080 L780 1080 Q860 860 960 640 Q1000 520 990 380 Z" fill="#FF9A2E" opacity={0.85} />
      {Array.from({ length: 26 }, (_, i) => {
        const p = (lt * 0.9 + random(`v${i}`)) % 1;
        const ang = -Math.PI / 2 + (random(`w${i}`) - 0.5) * 1.6;
        const d = 80 + 520 * p;
        return <circle key={i} cx={960 + Math.cos(ang) * d} cy={320 + Math.sin(ang) * d + 400 * p * p} r={10 + 14 * (1 - p)} fill={i % 3 ? '#FFB02E' : '#FFE07A'} opacity={1 - p} />;
      })}
      <path d="M0 980 Q400 900 800 960 T1920 940 V1080 H0 Z" fill="#250610" />
      {[0, 1, 2, 3, 4, 5].map((i) => <rect key={i} x={760 + i * 70} y={1000} width={40} height={10} rx={5} fill="#FF5A2E" opacity={0.6 + 0.4 * Math.sin(lt * 6 + i)} />)}
    </>
  )}</Scene>
);

/** 4: Nach der Schule nach Hause, Rucksack auf den Boden. */
const S4: React.FC<{ t: number }> = ({ t }) => {
  const throwT = 11.1;
  return (
    <Scene t={t} a={T[3]} b={T[4]} zoom={0.06}>{() => {
      const p = lin(t, throwT, throwT + 0.7);
      const bx = 900 + 520 * p;
      const by = 560 - 260 * Math.sin(Math.PI * p) + 300 * p;
      const land = t > throwT + 0.7;
      return (
        <>
          <Grad id="s4" c={['#FFB37A', '#F07E6E']} />
          <rect width={1920} height={1080} fill="url(#s4)" />
          <rect y={860} width={1920} height={220} fill="#B85A3C" />
          {Array.from({ length: 12 }, (_, i) => <rect key={i} x={i * 170} y={860} width={6} height={220} fill="#9E4A30" />)}
          <rect x={180} y={260} width={330} height={600} rx={10} fill="#7A3B5C" />
          <rect x={200} y={280} width={290} height={580} fill="#FFD9A0" />
          <path d="M200 280 L120 320 L120 900 L200 860 Z" fill="#5A2A44" />
          <rect x={1250} y={180} width={420} height={320} rx={16} fill="#8FD3F5" stroke="#E8946A" strokeWidth={16} />
          <circle cx={1560} cy={280} r={50} fill="#FFE27A" />
          <path d="M1250 440 Q1400 380 1670 430 V500 H1250 Z" fill="#6FC27A" />
          <rect x={1350} y={640} width={420} height={50} rx={10} fill="#E25B7A" />
          <rect x={1370} y={690} width={380} height={170} fill="#C2456A" />
          <Kid x={760} y={582} s={2.1} hair="#4A2A1A" shirt="#3E8EF0" pants="#2A2E6A" arm={t > throwT ? -120 : 10} mouth={t > throwT ? 'smile' : 'flat'} eyes={t > throwT + 0.2 ? 'happy' : 'open'} />
          <g transform={`translate(${land ? 1420 : bx} ${land ? 860 : by}) rotate(${land ? 8 : p * 360})`}>
            <rect x={-60} y={-80} width={120} height={140} rx={30} fill="#FFCB3D" />
            <rect x={-40} y={-10} width={80} height={50} rx={14} fill="#F2A21E" />
            <path d="M-30 -80 q30 -40 60 0" stroke="#D9861A" strokeWidth={10} fill="none" />
          </g>
        </>
      );
    }}</Scene>
  );
};

/** 5: Nachts gamen mit dem besten Freund. */
const S5: React.FC<{ t: number }> = ({ t }) => (
  <Scene t={t} a={T[4]} b={T[5]} zoom={0.08} oy={600}>{(lt) => {
    const flick = 0.7 + 0.3 * Math.abs(Math.sin(lt * 9));
    return (
      <>
        <Grad id="s5" c={['#1B1652', '#4A2290']} />
        <rect width={1920} height={1080} fill="url(#s5)" />
        <rect x={120} y={120} width={300} height={260} rx={14} fill="#0E1238" stroke="#5A3AB8" strokeWidth={12} />
        <circle cx={330} cy={200} r={40} fill="#F2F0C8" />
        <Stars n={14} t={lt} h={380} seed="g" />
        <rect x={1460} y={140} width={260} height={340} rx={12} fill="#7A2EBE" />
        <circle cx={1590} cy={260} r={70} fill="#E04FA8" />
        <rect x={1500} y={380} width={180} height={30} rx={8} fill="#FFD34D" />
        <path d={`M960 300 L1500 1080 H420 Z`} fill="#6FE4FF" opacity={0.12 * flick} />
        <rect x={720} y={180} width={480} height={290} rx={18} fill="#14163A" />
        <rect x={740} y={198} width={440} height={254} rx={8} fill={`hsl(${190 + 30 * Math.sin(lt * 2)} 90% ${55 * flick}%)`} />
        <rect x={780} y={300} width={60} height={60} rx={8} fill="#FF5A7A" transform={`translate(${120 * Math.sin(lt * 3)} 0)`} />
        <rect x={1040} y={330} width={50} height={50} rx={25} fill="#FFE27A" transform={`translate(0 ${-50 * Math.abs(Math.sin(lt * 4))})`} />
        <rect x={430} y={720} width={1060} height={140} rx={50} fill="#E2386B" />
        <rect x={400} y={820} width={1120} height={200} rx={40} fill="#C22A5A" />
        <Kid x={760} y={650} s={2.1} dir={1} hair="#2E86DE" shirt="#FF7A3D" sit arm={-70 + 6 * Math.sin(lt * 14)} mouth="o" />
        <Kid x={1160} y={650} s={2.1} dir={-1} hair="#3A1E14" shirt="#3FBF8A" sit arm={-70 + 6 * Math.cos(lt * 13)} mouth="smile" eyes="happy" />
      </>
    );
  }}</Scene>
);

/** 6: Eltern holen dich ab (Auto vor der Schule, Abendsonne). */
const S6: React.FC<{ t: number }> = ({ t }) => (
  <Scene t={t} a={T[5]} b={T[6]} zoom={0.05}>{(lt) => {
    const carX = -500 + 1300 * ease(lt, 0, 1.6);
    return (
      <>
        <Grad id="s6" c={['#FF8C5A', '#FFC27A']} />
        <rect width={1920} height={1080} fill="url(#s6)" />
        <circle cx={1500} cy={330} r={120} fill="#FFE9A8" />
        <rect x={180} y={300} width={760} height={480} fill="#D9644E" />
        <path d="M150 300 L560 120 L970 300 Z" fill="#A8433A" />
        {[0, 1, 2].map((i) => <rect key={i} x={240 + i * 240} y={380} width={150} height={120} rx={8} fill="#FFE2A0" />)}
        <rect x={500} y={600} width={140} height={180} rx={8} fill="#7A2E2E" />
        <rect y={780} width={1920} height={300} fill="#5A4A8A" />
        {Array.from({ length: 10 }, (_, i) => <rect key={i} x={i * 220 + 40} y={920} width={120} height={14} rx={7} fill="#F5E6B8" />)}
        <Kid x={1360 - 160 * ease(lt, 1.8, 3.4)} y={640} s={1.2} dir={-1} hair="#F2C14E" shirt="#5AA8F0" arm={-30} />
        <g transform={`translate(${carX} 690)`}>
          <path d="M0 120 Q0 40 80 30 L200 -40 Q260 -70 360 -40 L470 30 Q560 40 560 120 Z" fill="#3E7BE0" />
          <path d="M210 -20 L250 -45 Q300 -60 350 -40 L420 25 L210 25 Z" fill="#BFF0FF" />
          <circle cx={130} cy={130} r={46} fill="#1E1A3A" /><circle cx={130} cy={130} r={20} fill="#9A9AC8" />
          <circle cx={440} cy={130} r={46} fill="#1E1A3A" /><circle cx={440} cy={130} r={20} fill="#9A9AC8" />
          <circle cx={300} cy={-5} r={24} fill={SKIN} />
          <g transform={`rotate(${-20 + 20 * Math.sin(lt * 8)} 330 0)`}><rect x={322} y={-40} width={14} height={44} rx={7} fill={SKIN} /></g>
        </g>
      </>
    );
  }}</Scene>
);

/** 7: Lagerfeuer ("none of it felt important"). */
const S7: React.FC<{ t: number }> = ({ t }) => (
  <Scene t={t} a={T[6]} b={T[7]} zoom={0.08} oy={700}>{(lt) => (
    <>
      <Grad id="s7" c={['#0C1140', '#2A2470']} />
      <rect width={1920} height={1080} fill="url(#s7)" />
      <Stars n={80} t={lt} h={560} seed="c" />
      {[100, 300, 1600, 1800, 1400].map((x, i) => <Pine key={i} x={x} y={820} s={1.4 - (i % 2) * 0.3} c="#16284A" />)}
      <path d="M0 820 Q960 760 1920 820 V1080 H0 Z" fill="#1E3A5A" />
      <path d="M200 860 L420 520 L640 860 Z" fill="#7A3ABE" /><path d="M420 520 L470 860 L640 860 Z" fill="#5A2A9A" />
      <circle cx={960} cy={880} r={220} fill="#FF8A3D" opacity={0.15} />
      {[0, 1, 2].map((i) => {
        const h = 120 + 30 * Math.sin(lt * 8 + i * 2);
        return <path key={i} d={`M${920 + i * 40} 900 Q${900 + i * 40} ${900 - h * 0.6} ${940 + i * 40} ${900 - h} Q${980 + i * 40} ${900 - h * 0.5} ${960 + i * 40} 900 Z`} fill={['#FF5A2E', '#FFB02E', '#FFE27A'][i]} />;
      })}
      <rect x={890} y={895} width={160} height={22} rx={11} fill="#6A3A22" transform="rotate(8 970 905)" />
      {Array.from({ length: 10 }, (_, i) => {
        const p = (lt * 0.6 + random(`e${i}`)) % 1;
        return <circle key={i} cx={960 + (random(`f${i}`) - 0.5) * 120 + 30 * Math.sin(p * 6)} cy={860 - p * 400} r={4} fill="#FFB02E" opacity={1 - p} />;
      })}
      <Kid x={1280} y={660} s={2.0} dir={-1} hair="#2E86DE" shirt="#E2386B" sit eyes="closed" mouth="smile" />
    </>
  )}</Scene>
);

/** 8: Nachts im Bett, Handy leuchtet, Uhr rast. */
const S8: React.FC<{ t: number }> = ({ t }) => (
  <Scene t={t} a={T[7]} b={T[8]} zoom={0.1} ox={1100} oy={600}>{(lt) => (
    <>
      <Grad id="s8" c={['#0A0F2E', '#1C2258']} />
      <rect width={1920} height={1080} fill="url(#s8)" />
      <rect x={160} y={140} width={360} height={300} rx={14} fill="#060920" stroke="#2E3A7A" strokeWidth={12} />
      <circle cx={420} cy={230} r={44} fill="#E8E6C0" />
      <g transform="translate(1500 260)">
        <circle r={110} fill="#E8E6F5" /><circle r={96} fill="#2A2F6A" />
        <line x1={0} y1={0} x2={60 * Math.sin(lt * 9)} y2={-60 * Math.cos(lt * 9)} stroke="#fff" strokeWidth={8} strokeLinecap="round" />
        <line x1={0} y1={0} x2={40 * Math.sin(lt * 0.9)} y2={-40 * Math.cos(lt * 0.9)} stroke="#FF5A7A" strokeWidth={10} strokeLinecap="round" />
      </g>
      <rect x={300} y={700} width={1400} height={120} rx={30} fill="#3A3F8A" />
      <rect x={260} y={800} width={1480} height={200} rx={20} fill="#262A6A" />
      <rect x={320} y={620} width={300} height={110} rx={50} fill="#E8E6F5" />
      <path d="M560 700 Q900 600 1680 700 L1680 800 L560 800 Z" fill="#5A4FCF" />
      <path d={`M880 620 L1300 380 L1300 860 Z`} fill="#7FD8FF" opacity={0.18} />
      <Kid x={560} y={560} s={1.7} hair="#3A1E14" shirt="#5A4FCF" eyes="open" mouth="flat" />
      <g transform={`translate(860 560) rotate(-10)`}>
        <rect x={0} y={0} width={70} height={120} rx={12} fill="#14163A" />
        <rect x={6} y={8} width={58} height={104} rx={6} fill={`hsl(${(lt * 120) % 360} 80% 65%)`} />
      </g>
    </>
  )}</Scene>
);

const PHONE_CARDS = ['#FF5A7A', '#FFB02E', '#3EC6E0', '#8A5AF0', '#3FBF6A', '#FF7A3D', '#E04FA8', '#5A8AF0'];

/** 9: Swipe nach Swipe. */
const S9: React.FC<{ t: number }> = ({ t }) => (
  <Scene t={t} a={T[8]} b={T[9]} zoom={0.12}>{(lt) => {
    const k = Math.floor(lt / 0.32);
    const sp = ease(lt % 0.32, 0, 0.14);
    return (
      <>
        <Grad id="s9" c={['#5A1E9A', '#E0418A']} />
        <rect width={1920} height={1080} fill="url(#s9)" />
        {Array.from({ length: 24 }, (_, i) => {
          const p = (lt * 0.5 + random(`h${i}`)) % 1;
          return <path key={i} transform={`translate(${random(`hx${i}`) * 1920} ${1100 - p * 1200}) scale(${0.6 + random(`hs${i}`)})`} d="M0 10 C-20 -10 -30 15 0 35 C30 15 20 -10 0 10 Z" fill="#FFD1E8" opacity={0.5 * (1 - p)} />;
        })}
        <rect x={700} y={60} width={520} height={960} rx={60} fill="#14163A" />
        <clipPath id="ph9"><rect x={725} y={90} width={470} height={900} rx={40} /></clipPath>
        <g clipPath="url(#ph9)">
          {[k - 1, k].map((j) => j >= 0 && (
            <g key={j} transform={`translate(0 ${j === k ? 900 * (1 - sp) : -900 * sp})`}>
              <rect x={725} y={90} width={470} height={900} fill={PHONE_CARDS[j % PHONE_CARDS.length]} />
              <circle cx={960} cy={480} r={110} fill="#fff" opacity={0.25} />
              <path d="M930 430 L1010 480 L930 530 Z" fill="#fff" />
              <rect x={760} y={860} width={260} height={18} rx={9} fill="#fff" opacity={0.6} />
              <rect x={760} y={895} width={180} height={18} rx={9} fill="#fff" opacity={0.4} />
            </g>
          ))}
        </g>
        <g transform={`translate(1240 ${700 - 160 * sp})`} opacity={1 - sp * 0.3}>
          <circle r={34} fill={SKIN} /><rect x={-14} y={0} width={28} height={140} rx={14} fill={SKIN} />
        </g>
      </>
    );
  }}</Scene>
);

/** 10: Stunden, die nie zurückkommen – Kalenderblätter fliegen davon. */
const S10: React.FC<{ t: number }> = ({ t }) => (
  <Scene t={t} a={T[9]} b={T[10]} zoom={0.06}>{(lt) => (
    <>
      <Grad id="s10" c={['#1A1450', '#5A2A8A']} />
      <rect width={1920} height={1080} fill="url(#s10)" />
      <Stars n={50} t={lt} h={1080} seed="d" />
      {Array.from({ length: 16 }, (_, i) => {
        const st = i * 0.28;
        const p = lin(lt, st, st + 2.2);
        if (p <= 0) return null;
        const x = 960 + (random(`cx${i}`) - 0.3) * 900 * p + 500 * p;
        const y = 600 - 700 * p + 80 * Math.sin(p * 6 + i);
        return (
          <g key={i} transform={`translate(${x} ${y}) rotate(${(random(`cr${i}`) - 0.5) * 120 * p}) scale(${1 - 0.5 * p})`} opacity={1 - p}>
            <rect x={-70} y={-80} width={140} height={160} rx={12} fill="#F5F2FF" />
            <rect x={-70} y={-80} width={140} height={40} rx={12} fill="#FF5A7A" />
            <text x={0} y={50} textAnchor="middle" fontFamily={FONT} fontSize={70} fill="#2A1B5A">{(i * 7 + 3) % 31 + 1}</text>
          </g>
        );
      })}
      <g transform="translate(960 640)">
        <path d="M-150 -260 H150 V-230 Q150 -120 20 0 Q150 120 150 230 V260 H-150 V230 Q-150 120 -20 0 Q-150 -120 -150 -230 Z" fill="#BFE6FF" opacity={0.35} stroke="#E8F4FF" strokeWidth={10} />
        <path d={`M${-110 + 90 * lin(lt, 0, 5)} -220 H${110 - 90 * lin(lt, 0, 5)} Q60 -120 10 -20 H-10 Q-60 -120 ${-110 + 90 * lin(lt, 0, 5)} -220 Z`} fill="#FFC24D" />
        <path d={`M-120 240 Q0 ${240 - 140 * lin(lt, 0, 5)} 120 240 Z`} fill="#FFC24D" />
        <rect x={-4} y={-20} width={8} height={240} fill="#FFC24D" opacity={0.8} />
        <rect x={-180} y={-290} width={360} height={36} rx={12} fill="#7A4ABE" /><rect x={-180} y={256} width={360} height={36} rx={12} fill="#7A4ABE" />
      </g>
    </>
  )}</Scene>
);

/** App-Leiste (Home, Shorts, Plus, You) im Handy. */
const AppBar: React.FC<{ x: number; y: number; w: number; shortsP?: number; red?: boolean }> = ({ x, y, w, shortsP = 1, red }) => {
  const kinds = ['home', 'shorts', 'plus', 'you'] as const;
  return (
    <div style={{ position: 'absolute', left: x - w / 2, top: y, width: w, height: 120, display: 'flex', justifyContent: 'space-around', alignItems: 'center', background: '#14163A', borderRadius: 24 }}>
      {kinds.map((k) => (k === 'shorts' && shortsP <= 0.01 ? null : (
        <div key={k} style={{ width: 80, height: 80, display: 'flex', alignItems: 'center', justifyContent: 'center', transform: k === 'shorts' ? `scale(${1.3 * shortsP})` : 'scale(1.2)', opacity: k === 'shorts' ? shortsP : 1, filter: k === 'shorts' && red ? `drop-shadow(0 0 14px ${RED})` : undefined }}>
          <BarIcon kind={k} color={k === 'shorts' && red ? RED : '#fff'} />
        </div>
      )))}
    </div>
  );
};

/** 11: YouTube lässt Shorts nicht ausschalten (Schloss). */
const S11: React.FC<{ t: number }> = ({ t }) => {
  if (t < T[10] || t >= T[11]) return null;
  const lt = t - T[10];
  const tryP = (s: number) => (lt > s && lt < s + 0.5 ? Math.sin(((lt - s) / 0.5) * Math.PI) : 0);
  const knob = Math.max(tryP(1.2), tryP(1.9)) * 0.5;
  return (
    <AbsoluteFill style={{ background: 'linear-gradient(160deg, #3A0E5A, #B0204A)', transform: `scale(${1 + 0.05 * lt / 2.7})` }}>
      <div style={{ position: 'absolute', left: 660, top: 60, width: 600, height: 960, borderRadius: 60, background: '#0E0F26', boxShadow: '0 30px 80px rgba(0,0,0,.4)' }} />
      <div style={{ position: 'absolute', left: 690, top: 100, width: 540, height: 760, borderRadius: 36, background: 'linear-gradient(180deg,#2A2E6A,#1A1C44)' }}>
        {[0, 1].map((i) => <div key={i} style={{ margin: '30px 30px 0', height: 260, borderRadius: 24, background: ['#5A8AF0', '#8A5AF0'][i], opacity: 0.8 }} />)}
      </div>
      <AppBar x={960} y={870} w={520} red />
      <svg style={{ position: 'absolute', left: 820, top: 820, overflow: 'visible' }} width={130} height={200}>
        <path d="M40 60 V30 a25 25 0 0 1 50 0 V60" stroke="#FFD34D" strokeWidth={14} fill="none" />
        <rect x={20} y={55} width={90} height={74} rx={14} fill="#FFD34D" />
        <circle cx={65} cy={90} r={10} fill="#8A5A00" />
      </svg>
      <div style={{ position: 'absolute', left: 1330, top: 460, width: 260, height: 130, borderRadius: 70, background: RED, boxShadow: `0 0 40px ${RED}` }}>
        <div style={{ position: 'absolute', left: 14 + knob * 130, top: 14, width: 102, height: 102, borderRadius: '50%', background: '#fff' }} />
      </div>
    </AbsoluteFill>
  );
};

/** 12: Nachts am Laptop bauen. */
const S12: React.FC<{ t: number }> = ({ t }) => (
  <Scene t={t} a={T[11]} b={T[12]} zoom={0.07}>{(lt) => (
    <>
      <Grad id="s12" c={['#141A4A', '#2E2A7A']} />
      <rect width={1920} height={1080} fill="url(#s12)" />
      <rect x={1100} y={120} width={640} height={420} rx={14} fill="#0A0E2A" stroke="#3A3F8A" strokeWidth={14} />
      {Array.from({ length: 22 }, (_, i) => <rect key={i} x={1140 + (i % 8) * 72} y={420 - (i % 4) * 60 - random(`b${i}`) * 80} width={48} height={140 + random(`bh${i}`) * 120} fill="#1E2660" />)}
      {Array.from({ length: 30 }, (_, i) => <rect key={i} x={1150 + random(`l${i}`) * 540} y={260 + random(`lm${i}`) * 260} width={10} height={14} fill="#FFE27A" opacity={0.4 + 0.6 * (Math.sin(lt * 3 + i) > 0 ? 1 : 0.3)} />)}
      <path d="M520 220 L340 820 L760 820 Z" fill="#FFE9A8" opacity={0.18} />
      <rect x={480} y={160} width={80} height={60} rx={20} fill="#FFB02E" />
      <rect y={820} width={1920} height={260} fill="#5A3A8A" />
      <rect x={760} y={600} width={460} height={230} rx={14} fill="#C8CCF5" />
      <rect x={780} y={618} width={420} height={195} rx={6} fill="#14163A" />
      {Array.from({ length: 7 }, (_, i) => <rect key={i} x={800 + (i % 3) * 20} y={634 + i * 25 - ((lt * 60) % 25)} width={120 + random(`c${i}`) * 220} height={10} rx={5} fill={['#FF5A7A', '#3EC6E0', '#FFE27A'][i % 3]} />)}
      <rect x={720} y={820} width={540} height={24} rx={12} fill="#9AA0E0" />
      <Kid x={1420} y={536} s={2.0} dir={-1} hair="#3A1E14" shirt="#E2386B" arm={-80 + 8 * Math.sin(lt * 20)} mouth="flat" />
    </>
  )}</Scene>
);

/** 13: Logo-Knall. */
const S13: React.FC<{ t: number }> = ({ t }) => {
  if (t < T[12] || t >= T[13]) return null;
  const lt = t - T[12];
  const p = ease(lt, 0.1, 0.45);
  return (
    <AbsoluteFill style={{ background: 'radial-gradient(circle at 50% 50%, #FF4A5A 0%, #C2102A 45%, #4A0616 100%)', alignItems: 'center', justifyContent: 'center' }}>
      <svg style={{ position: 'absolute', inset: 0 }} viewBox="0 0 1920 1080">
        {Array.from({ length: 16 }, (_, i) => <path key={i} d="M960 540 L940 -400 L980 -400 Z" fill="#fff" opacity={0.08} transform={`rotate(${i * 22.5 + lt * 20} 960 540)`} />)}
      </svg>
      <div style={{ marginTop: -330, transform: `scale(${0.4 + 0.5 * p})`, opacity: p, filter: 'drop-shadow(0 20px 40px rgba(0,0,0,.35))' }}>
        <Logo size={300} />
      </div>
    </AbsoluteFill>
  );
};

/** 14: Shorts-Knopf fliegt raus. */
const S14: React.FC<{ t: number }> = ({ t }) => {
  if (t < T[13] || t >= T[14]) return null;
  const lt = t - T[13];
  const pop = lin(t, 44.74, 45.3);
  const shortsP = 1 - ease(t, 44.74, 45.0);
  return (
    <AbsoluteFill style={{ background: 'linear-gradient(160deg, #2EC4E0, #6A4AE0)', transform: `scale(${1 + 0.05 * lt / 3})` }}>
      <div style={{ position: 'absolute', left: 660, top: 60, width: 600, height: 960, borderRadius: 60, background: '#0E0F26' }} />
      <div style={{ position: 'absolute', left: 690, top: 100, width: 540, height: 760, borderRadius: 36, background: 'linear-gradient(180deg,#2A2E6A,#1A1C44)' }}>
        {[0, 1].map((i) => <div key={i} style={{ margin: '30px 30px 0', height: 260, borderRadius: 24, background: ['#FFB02E', '#3FBF6A'][i] }} />)}
      </div>
      <AppBar x={960} y={870} w={520} shortsP={shortsP} red={t < 44.74} />
      {pop > 0 && pop < 1 && Array.from({ length: 14 }, (_, i) => {
        const ang = random(`q${i}`) * Math.PI * 2;
        const d = 60 + 260 * pop;
        return <div key={i} style={{ position: 'absolute', left: 880 + Math.cos(ang) * d, top: 930 + Math.sin(ang) * d - 200 * pop, width: 18, height: 18, borderRadius: 4, background: i % 2 ? RED : '#FFE27A', opacity: 1 - pop, transform: `rotate(${ang * 90}deg)` }} />;
      })}
    </AbsoluteFill>
  );
};

/** 15: Draussen in der Sonne, Zeit kommt zurück. */
const S15: React.FC<{ t: number }> = ({ t }) => (
  <Scene t={t} a={T[14]} b={T[15]} zoom={0.06}>{(lt) => {
    const sun = ease(t, 48.7, 50.0);
    const fx = (lt * 0.8) % 1;
    return (
      <>
        <Grad id="s15" c={['#5AC8F0', '#C8F0FF']} />
        <rect width={1920} height={1080} fill="url(#s15)" />
        <circle cx={1500} cy={520 - 300 * sun} r={130} fill="#FFE27A" />
        <circle cx={1500} cy={520 - 300 * sun} r={180} fill="#FFE27A" opacity={0.25} />
        {[0, 1, 2].map((i) => <Cloud key={i} x={((i * 700 + lt * 30) % 2300) - 200} y={150 + i * 60} s={0.9} />)}
        <path d="M0 720 Q500 600 960 700 T1920 680 V1080 H0 Z" fill="#5FD06A" />
        <path d="M0 820 Q600 740 1200 820 T1920 800 V1080 H0 Z" fill="#3FB85A" />
        {[220, 1720].map((x, i) => (
          <g key={i}><rect x={x - 20} y={560} width={40} height={220} fill="#7A4A2A" /><circle cx={x} cy={520} r={130} fill="#2E9E57" /><circle cx={x + 60} cy={580} r={90} fill="#3FB85A" /></g>
        ))}
        <Kid x={620} y={516} s={2.0} hair="#2E86DE" shirt="#FF7A3D" arm={-100 + 40 * Math.sin(lt * 3)} eyes="happy" />
        <Kid x={1300} y={516} s={2.0} dir={-1} hair="#3A1E14" shirt="#8A5AF0" arm={-100 + 40 * Math.cos(lt * 3)} eyes="happy" mouth="o" />
        <ellipse cx={700 + 520 * (Math.sin(fx * Math.PI * 2) * 0.5 + 0.5)} cy={430 - 160 * Math.abs(Math.sin(fx * Math.PI * 2))} rx={40} ry={12} fill="#FF4A7A" />
      </>
    );
  }}</Scene>
);

/** 16: Sonnenuntergang auf dem Hügel, zwei Freunde. */
const S16: React.FC<{ t: number }> = ({ t }) => (
  <Scene t={t} a={T[15]} b={T[16]} zoom={0.08} oy={700}>{(lt) => (
    <>
      <Grad id="s16" c={['#5A2A8A', '#E25B7A', '#FFB27A']} />
      <rect width={1920} height={1080} fill="url(#s16)" />
      <circle cx={960} cy={780 + 40 * lin(lt, 0, 5)} r={210} fill="#FFE2A0" />
      <circle cx={960} cy={780 + 40 * lin(lt, 0, 5)} r={300} fill="#FFE2A0" opacity={0.2} />
      {[0, 1, 2].map((i) => <path key={i} transform={`translate(${300 + lt * 60 + i * 80} ${260 + i * 30 + 10 * Math.sin(lt * 4 + i)})`} d="M0 0 q15 -12 30 0 q15 -12 30 0" stroke="#3A1A4A" strokeWidth={5} fill="none" />)}
      <path d="M0 860 Q960 700 1920 860 V1080 H0 Z" fill="#2A1640" />
      <Kid x={840} y={620} s={1.8} dir={1} hair="#2E86DE" shirt="#1E1236" pants="#1E1236" skin="#2A1640" sit eyes="closed" />
      <Kid x={1080} y={620} s={1.8} dir={-1} hair="#2A1640" shirt="#1E1236" pants="#1E1236" skin="#2A1640" sit eyes="closed" />
    </>
  )}</Scene>
);

/** 17: Schluss – COMMENT "TEST". */
const S17: React.FC<{ t: number }> = ({ t }) => {
  if (t < T[16]) return null;
  const btn = ease(t, 56.5, 56.8);
  return (
    <AbsoluteFill style={{ background: 'linear-gradient(160deg, #4A1A8A, #C2102A)', alignItems: 'center', justifyContent: 'center', gap: 40 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 34, transform: `scale(${0.9 + 0.1 * ease(t, T[16], T[16] + 0.4)})` }}>
        <Logo size={170} />
        <div style={{ fontFamily: FONT, fontSize: 150, color: '#fff', letterSpacing: 4 }}>F<span style={{ color: '#FFD0D6' }}>*</span>CK SHORTS</div>
      </div>
      <div style={{ opacity: btn, transform: `scale(${0.8 + 0.2 * btn + 0.03 * Math.sin(t * 6)})`, background: '#fff', color: RED, fontFamily: FONT, fontSize: 84, letterSpacing: 3, padding: '18px 70px', borderRadius: 80 }}>
        COMMENT "TEST"
      </div>
      <div style={{ fontFamily: FONT, fontSize: 40, color: '#FFE2E8', letterSpacing: 3, opacity: ease(t, 57.3, 57.6) }}>TRY IT FIRST · ANDROID · COMING SOON TO GOOGLE PLAY</div>
    </AbsoluteFill>
  );
};

// ------------------------------------------------------------------------------------------- Untertitel

type W = [string, number, number];
const clean = (w: string) => w.replace(/[.,!?"]/g, '').toUpperCase().replace(/^FUCK$/, 'F*CK');
const GROUPS: { text: string; at: number; end: number }[] = (() => {
  const out: { text: string; at: number; end: number }[] = [];
  const ws = WORDS as W[];
  let i = 0;
  while (i < ws.length) {
    const [w, a, e] = ws[i];
    let text = clean(w);
    let end = e;
    const nxt = ws[i + 1];
    const short = text.length <= 4;
    if (nxt && short && nxt[1] - e < 0.2 && clean(nxt[0]).length <= 7 && !/[.,?]$/.test(w)) {
      text += ' ' + clean(nxt[0]);
      end = nxt[2];
      i += 1;
    }
    out.push({ text, at: a, end });
    i += 1;
  }
  return out;
})();

const Captions: React.FC<{ t: number }> = ({ t }) => {
  if (t >= T[16] - 0.05) return null;
  const g = [...GROUPS].reverse().find((x) => t >= x.at);
  if (!g) return null;
  const next = GROUPS[GROUPS.indexOf(g) + 1];
  const until = Math.min(next ? next.at : g.end + 0.6, g.end + 0.6);
  if (t > until) return null;
  const p = ease(t, g.at, g.at + 0.08);
  return (
    <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', mixBlendMode: 'difference', pointerEvents: 'none' }}>
      <div style={{ fontFamily: FONT, fontSize: 190, lineHeight: 1, color: '#F2F8B0', transform: `scaleX(0.78) scale(${0.92 + 0.08 * p})`, whiteSpace: 'nowrap', letterSpacing: 2 }}>{g.text}</div>
    </AbsoluteFill>
  );
};

export const Ad7: React.FC<{ music: 'own' | 'orig' | 'none' }> = ({ music }) => {
  useAnton();
  const frame = useCurrentFrame();
  const t = frame / FPS7;
  return (
    <AbsoluteFill style={{ background: '#000', overflow: 'hidden' }}>
      <Audio src={staticFile('voice7.wav')} />
      {music === 'own' && <Audio src={staticFile('music7.wav')} volume={0.3} />}
      {music === 'orig' && <Audio src={staticFile('music7_orig.wav')} volume={0.55} />}
      <S1 t={t} /><S2 t={t} /><S3 t={t} /><S4 t={t} /><S5 t={t} /><S6 t={t} /><S7 t={t} /><S8 t={t} />
      <S9 t={t} /><S10 t={t} /><S11 t={t} /><S12 t={t} /><S13 t={t} /><S14 t={t} /><S15 t={t} /><S16 t={t} /><S17 t={t} />
      <Captions t={t} />
    </AbsoluteFill>
  );
};
