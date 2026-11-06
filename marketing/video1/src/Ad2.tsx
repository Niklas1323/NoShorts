import React from 'react';
import { AbsoluteFill, Audio, random, staticFile, useCurrentFrame } from 'remotion';
import { Card, Logo, BrandName, Pill, BarIcon, useFonts, ease, lin, RED, AMBER, FONT, HUES } from './Ad';
import { WORDS2, VOICE2_S } from './words2';

/**
 * Werbevideo 2: schnell, laut, "Dopamin". Alles über den ganzen Bildschirm, harte Schnitte, Wackeln, Blitze.
 * Alle Zeiten hängen an den Wörtern der Sprachaufnahme (words2.ts), die Geräusche (sfx2.py) an denselben Wörtern.
 */
export const FPS2 = 30;
export const DURATION2_S = VOICE2_S + 1.6;

const W = 1080;
const H = 1920;

const cue = (word: string, n = 0, fallback = 0) => {
  const hits = WORDS2.filter(([w]) => w === word);
  return hits.length > n ? hits[n][1] : fallback;
};

const KEY = new Set(['TONIGHT', 'FORGET', 'THIS', 'ONE', 'FIFTEEN', 'GONE', 'DREAMS', 'FRIENDS', 'WAITING', 'SWIPING', 'ENOUGH', 'KILLS', 'STAY', 'TESTERS', 'ANDROID', 'NOW', 'F*CK SHORTS']);
const HITS = ['TONIGHT', 'ONE', 'GONE', 'ENOUGH', 'KILLS', 'COMMENT', 'NOW'];

/** Zeitpunkte der Abschnitte */
const useCues = () => {
  const including = cue('INCLUDING', 0, 3);
  const shatter = cue('ONE', 0, 4);
  const hour = cue('HOUR', 0, 5) - 0.35;
  const fifteen = cue('FIFTEEN', 0, 6.5);
  const gone = cue('GONE', 0, 8);
  const your = cue('YOUR', 0, 9);
  const friends = cue('FRIENDS', 0, 10.5);
  const and = cue('AND', 0, 12);
  const enough = cue('ENOUGH', 0, 14);
  const fck = cue('F*CK SHORTS', 0, 15);
  const kills = cue('KILLS', 0, 15.6);
  const videos = cue('VIDEOS', 0, 17);
  const we = cue('WE', 0, 19);
  const comment = cue('COMMENT', 0, 22);
  return { including, shatter, hour, fifteen, gone, your, friends, and, enough, fck, kills, videos, we, comment };
};

// ------------------------------------------------------------------------------------------------ Effekte

/** Wackeln und Blitz nach harten Schlägen. */
const impact = (t: number) => {
  let shake = 0;
  let flash = 0;
  let punch = 0;
  for (const [w, s] of WORDS2) {
    const d = t - s;
    if (d < 0 || d > 0.4) continue;
    punch = Math.max(punch, 1 - d / 0.18);
    if (HITS.includes(w) || w === 'F*CK SHORTS') {
      shake = Math.max(shake, Math.exp(-d / 0.09));
      flash = Math.max(flash, Math.exp(-d / 0.06));
    }
  }
  return { shake, flash, punch: Math.max(0, punch) };
};

/** Feed aus Kärtchen über den ganzen Bildschirm. */
const FeedWall: React.FC<{ t: number; speed: number; cols?: number; dim?: number; seed?: number; jump?: number }> = ({ t, speed, cols = 4, dim = 0.45, seed = 0, jump = 0 }) => {
  const gap = 18;
  const cw = (W - gap * (cols + 1)) / cols;
  const ch = (cw * 16) / 9;
  const per = ch + gap;
  const count = Math.ceil(H / per) + 2;
  return (
    <AbsoluteFill>
      {Array.from({ length: cols }).flatMap((_, c) => {
        const dir = c % 2 === 0 ? 1 : 0.8;
        const off = (t * speed * dir + jump * per + c * per * 0.37) % per;
        return Array.from({ length: count }).map((__, k) => {
          const id = seed + c * 97 + k + Math.floor((t * speed * dir + jump * per) / per);
          return <Card key={`${c}-${k}`} seed={id} w={cw} h={ch} style={{ left: gap + c * (cw + gap), top: (k - 1) * per - off }} />;
        });
      })}
      <AbsoluteFill style={{ background: `rgba(5,2,3,${dim})` }} />
    </AbsoluteFill>
  );
};

/** Strahlen, die sich drehen (für Schluss und Logo). */
const Rays: React.FC<{ t: number; color?: string; opacity?: number }> = ({ t, color = 'rgba(229,23,43,.35)', opacity = 1 }) => (
  <AbsoluteFill style={{ opacity }}>
    <div
      style={{
        position: 'absolute',
        left: W / 2 - 1400,
        top: H / 2 - 1400,
        width: 2800,
        height: 2800,
        transform: `rotate(${t * 25}deg)`,
        background: `repeating-conic-gradient(${color} 0deg 8deg, transparent 8deg 22deg)`,
        maskImage: 'radial-gradient(circle, black 10%, transparent 60%)',
        WebkitMaskImage: 'radial-gradient(circle, black 10%, transparent 60%)',
      }}
    />
  </AbsoluteFill>
);

const OutlineIcon: React.FC<{ d: string[]; circles?: [number, number, number][]; size: number; draw: number; color: string; glow?: number }> = ({ d, circles, size, draw, color, glow = 0 }) => (
  <svg width={size} height={size} viewBox="0 0 100 100" style={{ filter: glow ? `drop-shadow(0 0 ${30 * glow}px ${color})` : undefined, overflow: 'visible' }}>
    {d.map((p, k) => (
      <path key={k} d={p} pathLength={1} fill="none" stroke={color} strokeWidth={3} strokeLinecap="round" strokeLinejoin="round" strokeDasharray={1} strokeDashoffset={1 - draw} />
    ))}
    {circles?.map(([cx, cy, r], k) => (
      <circle key={k} cx={cx} cy={cy} r={r} pathLength={1} fill="none" stroke={color} strokeWidth={3} strokeDasharray={1} strokeDashoffset={1 - draw} />
    ))}
  </svg>
);

const STAR = ['M50 6 L61 38 L95 38 L67 58 L78 92 L50 71 L22 92 L33 58 L5 38 L39 38 Z'];
const HOURGLASS = ['M24 8 H76', 'M24 92 H76', 'M28 8 C 28 38, 50 42, 50 50 C 50 58, 28 62, 28 92', 'M72 8 C 72 38, 50 42, 50 50 C 50 58, 72 62, 72 92'];
const FRIENDS = ['M8 90 C 8 64, 50 64, 50 90', 'M50 90 C 50 64, 92 64, 92 90'];
const FRIEND_HEADS: [number, number, number][] = [
  [29, 46, 12],
  [71, 46, 12],
];

// ------------------------------------------------------------------------------------------------ Abschnitte

const SegA: React.FC<{ t: number; c: ReturnType<typeof useCues> }> = ({ t, c }) => {
  if (t >= c.including) return null;
  // Bei jedem Wort springt der Feed ein Stück weiter (wie ein Wisch).
  const jumps = WORDS2.filter(([, s]) => s <= t && s < c.including).length;
  const last = WORDS2.filter(([, s]) => s <= t).slice(-1)[0];
  const sinceJump = last ? t - last[1] : 1;
  const jump = jumps - Math.max(0, 1 - sinceJump / 0.15) ** 2;
  return <FeedWall t={t} speed={420} jump={jump} dim={0.5} />;
};

const SegA2: React.FC<{ t: number; c: ReturnType<typeof useCues> }> = ({ t, c }) => {
  if (t < c.including || t > c.shatter + 1.0) return null;
  const grow = ease(t, c.including, c.shatter);
  const cw = 360 + (W - 360) * grow;
  const ch = (cw * 16) / 9;
  if (t < c.shatter) {
    return (
      <AbsoluteFill>
        <Card seed={77} w={cw} h={ch} glow={1} style={{ left: W / 2 - cw / 2, top: H / 2 - ch / 2 }} />
        <AbsoluteFill style={{ background: 'rgba(0,0,0,.35)' }} />
      </AbsoluteFill>
    );
  }
  // Zerspringen in Kacheln über den ganzen Bildschirm
  const tile = 120;
  const cols = Math.ceil(W / tile);
  const rows = Math.ceil(H / tile);
  const hue = HUES[Math.floor(random(77) * HUES.length)];
  return (
    <AbsoluteFill>
      {Array.from({ length: cols * rows }).map((_, i) => {
        const x = (i % cols) * tile;
        const y = Math.floor(i / cols) * tile;
        const dx = x + tile / 2 - W / 2;
        const dy = y + tile / 2 - H / 2;
        const d = Math.hypot(dx, dy) || 1;
        const p = ease(t, c.shatter + random(`s${i}`) * 0.08, c.shatter + 0.75);
        const fly = (500 + random(`v${i}`) * 900) * p;
        return (
          <div
            key={i}
            style={{
              position: 'absolute',
              left: x + (dx / d) * fly,
              top: y + (dy / d) * fly + 300 * p * p,
              width: tile - 2,
              height: tile - 2,
              background: `hsl(${hue + (y / H) * 40} 65% ${40 - (y / H) * 18}%)`,
              opacity: 1 - p,
              transform: `rotate(${(random(`r${i}`) - 0.5) * 540 * p}deg) scale(${1 - 0.5 * p})`,
            }}
          />
        );
      })}
    </AbsoluteFill>
  );
};

const SegB: React.FC<{ t: number; c: ReturnType<typeof useCues> }> = ({ t, c }) => {
  if (t < c.hour || t > c.gone + 0.9) return null;
  if (t < c.fifteen) {
    // Rote Fläche mit riesiger Uhr, der Zeiger rast
    const r = 470;
    const angle = (t - c.hour) * 720;
    return (
      <AbsoluteFill style={{ background: RED }}>
        <svg width={W} height={H} viewBox={`0 0 ${W} ${H}`}>
          <circle cx={W / 2} cy={H / 2} r={r} fill="none" stroke="rgba(255,255,255,.9)" strokeWidth={18} />
          {Array.from({ length: 12 }).map((_, i) => {
            const a = (i / 12) * Math.PI * 2;
            return <line key={i} x1={W / 2 + Math.sin(a) * (r - 30)} y1={H / 2 - Math.cos(a) * (r - 30)} x2={W / 2 + Math.sin(a) * (r - 70)} y2={H / 2 - Math.cos(a) * (r - 70)} stroke="#fff" strokeWidth={12} strokeLinecap="round" />;
          })}
          <line x1={W / 2} y1={H / 2} x2={W / 2 + Math.sin((angle * Math.PI) / 180) * (r - 90)} y2={H / 2 - Math.cos((angle * Math.PI) / 180) * (r - 90)} stroke="#fff" strokeWidth={22} strokeLinecap="round" />
          <circle cx={W / 2} cy={H / 2} r={26} fill="#fff" />
        </svg>
        <AbsoluteFill style={{ background: 'rgba(0,0,0,.25)' }} />
      </AbsoluteFill>
    );
  }
  // Kalender über den ganzen Bildschirm, 15 Tage werden rot, bei "Gone" fliegt alles weg
  const cols = 5;
  const rows = 8;
  const gap = 18;
  const size = (W - gap * (cols + 1)) / cols;
  const top = (H - (rows * size + (rows - 1) * gap)) / 2;
  const boom = ease(t, c.gone, c.gone + 0.7);
  const count = Math.min(15, Math.floor(lin(t, c.fifteen, c.fifteen + 0.68) * 15));
  return (
    <AbsoluteFill style={{ background: '#0A0506' }}>
      {Array.from({ length: cols * rows }).map((_, i) => {
        const order = [12, 6, 18, 1, 24, 30, 9, 15, 21, 3, 27, 33, 36, 0, 39][Math.min(14, Math.max(0, count - 1))];
        const redOrder = [12, 6, 18, 1, 24, 30, 9, 15, 21, 3, 27, 33, 36, 0, 39].indexOf(i);
        const red = redOrder >= 0 && redOrder < count;
        const x = gap + (i % cols) * (size + gap);
        const y = top + Math.floor(i / cols) * (size + gap);
        const dx = x + size / 2 - W / 2;
        const dy = y + size / 2 - H / 2;
        const d = Math.hypot(dx, dy) || 1;
        const fly = boom * (700 + random(`b${i}`) * 900);
        void order;
        return (
          <div
            key={i}
            style={{
              position: 'absolute',
              left: x + (dx / d) * fly,
              top: y + (dy / d) * fly,
              width: size,
              height: size,
              borderRadius: 26,
              background: red ? RED : 'rgba(255,255,255,.08)',
              border: `3px solid ${red ? '#ff5b6a' : 'rgba(255,255,255,.1)'}`,
              boxShadow: red ? '0 0 50px rgba(229,23,43,.7)' : undefined,
              opacity: 1 - boom,
              transform: `rotate(${(random(`q${i}`) - 0.5) * 300 * boom}deg)`,
            }}
          />
        );
      })}
      <AbsoluteFill style={{ background: 'rgba(0,0,0,.3)' }} />
    </AbsoluteFill>
  );
};

const SegC: React.FC<{ t: number; c: ReturnType<typeof useCues> }> = ({ t, c }) => {
  if (t < c.your - 0.05 || t >= c.enough) return null;
  const waiting1 = cue('WAITING', 0, c.friends - 0.6);
  const your2 = cue('YOUR', 1, c.friends - 0.2);
  const waiting2 = cue('WAITING', 1, c.and - 0.6);
  if (t < c.and) {
    let icon: React.ReactNode;
    const pulse = 1 + 0.04 * Math.sin(t * 14);
    if (t < waiting1) icon = <OutlineIcon d={STAR} size={900} draw={ease(t, c.your, c.your + 0.45)} color={AMBER} glow={1} />;
    else if (t < your2) icon = <OutlineIcon d={HOURGLASS} size={820} draw={ease(t, waiting1, waiting1 + 0.35)} color="#fff" glow={0.6} />;
    else if (t < waiting2) icon = <OutlineIcon d={FRIENDS} circles={FRIEND_HEADS} size={980} draw={ease(t, your2, your2 + 0.45)} color={AMBER} glow={1} />;
    else icon = <OutlineIcon d={HOURGLASS} size={820} draw={ease(t, waiting2, waiting2 + 0.35)} color="#fff" glow={0.6} />;
    const spin = t < waiting1 ? (t - c.your) * 30 : t >= waiting2 || (t >= waiting1 && t < your2) ? Math.sin(t * 20) * 4 : 0;
    return (
      <AbsoluteFill style={{ background: 'radial-gradient(circle at 50% 50%, #3a1606 0%, #0A0506 70%)', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ transform: `rotate(${spin}deg) scale(${pulse})`, opacity: 0.55 }}>{icon}</div>
      </AbsoluteFill>
    );
  }
  // "And you just keep swiping": ganze Bildschirme wischen nach oben, mit Bewegungsstreifen
  const swipes = WORDS2.filter(([, s]) => s >= c.and && s <= t).length;
  const last = WORDS2.filter(([, s]) => s >= c.and && s <= t).slice(-1)[0];
  const d = last ? t - last[1] : 1;
  const prog = Math.min(1, d / 0.18);
  const y = -(1 - (1 - prog) ** 3) * H;
  const blur = (1 - prog) * 30;
  return (
    <AbsoluteFill style={{ background: '#000' }}>
      {[0, 1].map((k) => (
        <div key={k} style={{ position: 'absolute', left: 0, top: y + k * H, filter: `blur(${blur * 0.3}px)` }}>
          <Card seed={500 + swipes + k} w={W} h={H} />
        </div>
      ))}
      {blur > 1 &&
        Array.from({ length: 14 }).map((_, i) => (
          <div key={i} style={{ position: 'absolute', left: 40 + random(`l${i}`) * 1000, top: random(`t${i}${swipes}`) * H, width: 6, height: 400 + random(`h${i}`) * 600, background: 'rgba(255,255,255,.5)', opacity: blur / 30, borderRadius: 3 }} />
        ))}
      <AbsoluteFill style={{ background: 'rgba(0,0,0,.4)' }} />
    </AbsoluteFill>
  );
};

const SegD: React.FC<{ t: number; c: ReturnType<typeof useCues> }> = ({ t, c }) => {
  if (t < c.enough || t >= c.we) return null;
  if (t < c.fck) {
    // ENOUGH: Störbild, Streifen verrutschen
    const slices = 16;
    return (
      <AbsoluteFill style={{ background: '#000' }}>
        {Array.from({ length: slices }).map((_, i) => {
          const g = random(`g${i}${Math.floor(t * 20)}`);
          return <div key={i} style={{ position: 'absolute', left: (g - 0.5) * 140, top: (i * H) / slices, width: W, height: H / slices, background: g > 0.7 ? 'rgba(229,23,43,.6)' : g < 0.15 ? 'rgba(0,255,255,.18)' : 'transparent' }} />;
        })}
      </AbsoluteFill>
    );
  }
  const slam = ease(t, c.fck, c.fck + 0.25);
  const ring = lin(t, c.fck, c.fck + 0.9);
  const barIn = ease(t, c.fck + 0.3, c.fck + 0.6);
  const killed = t >= c.kills;
  const killP = ease(t, c.kills, c.kills + 0.5);
  const video = ease(t, c.videos - 0.1, c.videos + 0.3);
  const items: ('home' | 'shorts' | 'plus' | 'subs' | 'you')[] = ['home', 'shorts', 'plus', 'subs', 'you'];
  return (
    <AbsoluteFill style={{ background: '#0A0506' }}>
      <Rays t={t} opacity={1 - video * 0.6} />
      {/* Druckwelle */}
      {[0, 0.15].map((o) => (
        <div key={o} style={{ position: 'absolute', left: W / 2, top: 780, width: 0, height: 0 }}>
          <div style={{ position: 'absolute', left: -1400 * lin(ring, o, 1), top: -1400 * lin(ring, o, 1), width: 2800 * lin(ring, o, 1), height: 2800 * lin(ring, o, 1), borderRadius: '50%', border: `${14 * (1 - ring)}px solid rgba(229,23,43,${1 - ring})` }} />
        </div>
      ))}
      <div style={{ position: 'absolute', left: W / 2 - 210, top: 570, opacity: 1 - video, transform: `scale(${(2.4 - 1.4 * slam) * (1 - 0.4 * video)})` }}>
        <Logo size={420} />
      </div>
      <div style={{ position: 'absolute', left: 40, top: 1260, width: W - 80, height: 200, borderRadius: 52, background: '#1C1C1C', border: '2px solid rgba(255,255,255,.12)', opacity: barIn * (1 - video), transform: `translateY(${80 * (1 - barIn)}px)` }}>
        {items.map((k, i) => {
          const slot = (W - 80) / 5;
          const isShorts = k === 'shorts';
          return (
            <div
              key={k}
              style={{
                position: 'absolute',
                left: i * slot + slot / 2 - 50,
                top: 50 + (isShorts && killed ? 900 * killP * killP : 0),
                transform: `scale(1.5) rotate(${isShorts && killed ? 200 * killP : 0}deg)`,
                opacity: isShorts ? 1 - killP : 1,
                filter: isShorts ? `drop-shadow(0 0 20px ${RED})` : undefined,
              }}
            >
              <BarIcon kind={k} color={isShorts ? RED : '#fff'} />
            </div>
          );
        })}
        {/* Rotes Kreuz über dem Shorts-Knopf */}
        {killed && (
          <svg width={200} height={200} viewBox="0 0 100 100" style={{ position: 'absolute', left: (W - 80) / 5 + (W - 80) / 10 - 100, top: 0, opacity: 1 - killP * 0.8 }}>
            <path d="M15 15 L85 85 M85 15 L15 85" stroke={RED} strokeWidth={12} strokeLinecap="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - ease(t, c.kills, c.kills + 0.15)} />
          </svg>
        )}
      </div>
      {/* Das lange Video, das bleibt: gross über den ganzen Bildschirm */}
      {video > 0 && (
        <div style={{ position: 'absolute', left: W / 2 - 500, top: 1000, width: 1000, height: 563, opacity: video, transform: `scale(${0.85 + 0.15 * video})` }}>
          <div style={{ position: 'absolute', inset: 0, borderRadius: 40, overflow: 'hidden', background: 'linear-gradient(135deg, #3a2a55, #10283d)', border: '3px solid rgba(255,255,255,.25)', boxShadow: '0 0 120px rgba(255,255,255,.18)' }}>
            <div style={{ position: 'absolute', left: 440, top: 225, borderTop: '58px solid transparent', borderBottom: '58px solid transparent', borderLeft: '100px solid rgba(255,255,255,.92)' }} />
            <div style={{ position: 'absolute', left: 0, bottom: 0, height: 14, width: `${40 + 30 * lin(t, c.videos, c.we)}%`, background: RED }} />
          </div>
        </div>
      )}
    </AbsoluteFill>
  );
};

const SegE: React.FC<{ t: number; c: ReturnType<typeof useCues> }> = ({ t, c }) => {
  if (t < c.we) return null;
  const inP = ease(t, c.we, c.we + 0.35);
  const cta = ease(t, c.comment, c.comment + 0.3);
  const androidAt = cue('ANDROID', 0, c.comment - 1);
  const android = ease(t, androidAt, androidAt + 0.25);
  const pulse = 1 + 0.05 * Math.sin((t - c.comment) * 9) * cta;
  return (
    <AbsoluteFill style={{ background: '#0A0506' }}>
      <Rays t={t} color="rgba(229,23,43,.45)" />
      {/* Konfetti über den ganzen Bildschirm */}
      {Array.from({ length: 70 }).map((_, i) => {
        const start = c.we + random(`c${i}`) * 1.2;
        const fall = Math.max(0, t - start);
        if (fall <= 0) return null;
        const x = random(`x${i}`) * W;
        const y = -60 + fall * (500 + random(`y${i}`) * 600);
        return <div key={i} style={{ position: 'absolute', left: x + Math.sin(fall * 3 + i) * 40, top: y, width: 18, height: 30, background: i % 3 === 0 ? '#fff' : i % 3 === 1 ? RED : AMBER, transform: `rotate(${fall * 300 + i * 40}deg)`, opacity: 0.85 }} />;
      })}
      <div style={{ position: 'absolute', top: 260, width: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', opacity: inP, transform: `scale(${0.7 + 0.3 * inP})` }}>
        <Logo size={200} />
        <div style={{ marginTop: 22 }}>
          <BrandName size={96} />
        </div>
      </div>
      <Pill text="LAUNCHING SOON" size={34} style={{ top: 690, opacity: inP }} />
      {cta > 0 && (
        <div style={{ position: 'absolute', top: 930, width: '100%', textAlign: 'center', opacity: cta, transform: `scale(${(1.6 - 0.6 * cta) * pulse})` }}>
          <div style={{ fontFamily: FONT, fontWeight: 900, fontSize: 110, color: '#fff', lineHeight: 1, letterSpacing: -3 }}>COMMENT</div>
          <div style={{ fontFamily: FONT, fontWeight: 900, fontSize: 170, color: RED, lineHeight: 1.05, letterSpacing: -5, textShadow: '0 0 60px rgba(229,23,43,.8)' }}>“TESTER”</div>
        </div>
      )}
      <Pill text="ANDROID ONLY" size={42} color={AMBER} style={{ top: 1330, opacity: android, transform: `translateX(-50%) scale(${0.8 + 0.2 * android})` }} />
    </AbsoluteFill>
  );
};

// ------------------------------------------------------------------------------------------------ Wörter

const Words: React.FC<{ t: number; c: ReturnType<typeof useCues> }> = ({ t, c }) => {
  if (t >= c.comment) return null;
  let idx = -1;
  for (let i = 0; i < WORDS2.length; i++) if (WORDS2[i][1] <= t + 0.01) idx = i;
  if (idx < 0) return null;
  const [word, start] = WORDS2[idx];
  const next = idx + 1 < WORDS2.length ? WORDS2[idx + 1][1] : start + 0.8;
  if (t > Math.min(next, start + 0.8)) return null;
  const d = t - start;
  // Reinknallen: gross -> normal mit kleinem Überschwingen
  const big = word.length > 8 ? 1.3 : 1.7;
  const s = d < 0.08 ? big - (big - 0.9) * (d / 0.08) : 0.9 + 0.1 * Math.min(1, (d - 0.08) / 0.07);
  const isKey = KEY.has(word);
  const rot = (random(`w${idx}`) - 0.5) * 7;
  const len = word.length;
  const size = Math.min(isKey ? 230 : 190, 860 / Math.max(3, len * 0.8));
  const inD = t >= c.fck && t < c.we;
  const inE = t >= c.we;
  const y = inD ? (t < c.videos ? 1580 : 520) : inE ? 1110 : 960;
  return (
    <div
      style={{
        position: 'absolute',
        left: 0,
        width: W,
        top: y,
        transform: `translateY(-50%) scale(${s}) rotate(${rot}deg)`,
        textAlign: 'center',
        fontFamily: FONT,
        fontWeight: 900,
        fontSize: size,
        lineHeight: 1,
        letterSpacing: -size * 0.03,
        color: isKey ? (t >= c.your && t < c.and ? AMBER : RED) : '#fff',
        textShadow: '0 10px 50px rgba(0,0,0,1), 0 0 4px rgba(0,0,0,.9)',
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

export const Ad2: React.FC<{ voice: boolean; sfx: boolean }> = ({ voice, sfx }) => {
  useFonts();
  const frame = useCurrentFrame();
  const t = frame / FPS2;
  const c = useCues();
  const { shake, flash, punch } = impact(t);
  const sx = (random(`sx${frame}`) - 0.5) * 50 * shake;
  const sy = (random(`sy${frame}`) - 0.5) * 50 * shake;
  const redFlash = WORDS2.some(([w, s]) => ['GONE', 'ENOUGH', 'KILLS'].includes(w) && t >= s && t - s < 0.12);
  return (
    <AbsoluteFill style={{ background: '#0A0506', overflow: 'hidden' }}>
      <AbsoluteFill style={{ transform: `translate(${sx}px, ${sy}px) scale(${1.04 + 0.05 * punch})` }}>
        <SegA t={t} c={c} />
        <SegA2 t={t} c={c} />
        <SegB t={t} c={c} />
        <SegC t={t} c={c} />
        <SegD t={t} c={c} />
        <SegE t={t} c={c} />
        <Words t={t} c={c} />
      </AbsoluteFill>
      <AbsoluteFill style={{ background: redFlash ? RED : '#fff', opacity: flash * (redFlash ? 0.55 : 0.7), mixBlendMode: redFlash ? 'screen' : 'normal' }} />
      <AbsoluteFill style={{ background: 'radial-gradient(circle at 50% 50%, transparent 60%, rgba(0,0,0,.6) 100%)' }} />
      {voice && <Audio src={staticFile('voice2.wav')} />}
      {sfx && <Audio src={staticFile('sfx2.wav')} volume={0.9} />}
    </AbsoluteFill>
  );
};
