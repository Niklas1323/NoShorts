import React from 'react';
import { AbsoluteFill, Audio, staticFile, useCurrentFrame } from 'remotion';
import { BarIcon, Logo, RED, ease, lin, useFonts } from './Ad';

/**
 * Werbevideo 8 "8 YEARS" (hochkant 9:16, ohne Stimme). Rechnung: 3 h/Tag × 365 = 1'095 h = 45,6 Tage pro Jahr;
 * Alter 13 bis 80 = 67 Jahre → 3'056 Tage = 8,4 Jahre. Alles, was zum Handy gehört, passiert IM Handy-Bildschirm.
 */
export const FPS8 = 30;
export const DURATION8_S = 23;
const F = 'Montserrat';
const GLOW = '0 0 18px rgba(255,255,255,.5), 0 0 40px rgba(255,255,255,.2)';
const RGLOW = `0 0 18px ${RED}, 0 0 46px rgba(229,23,43,.6)`;

// Zeitplan (Sekunden)
const T = { hook: 0, perDay: 2.6, perYear: 5.4, days: 8.2, life: 11.4, gone: 15.2, fix: 17.0, cta: 20.2 };

const sc = (t: number, a: number, b: number, f = 0.2) => Math.min(lin(t, a - 0.01, a + f), lin(t, b - f, b, 1, 0));
const fmt = (n: number) => Math.round(n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');

const Txt: React.FC<{ s: number; c?: string; glow?: string; style?: React.CSSProperties; children: React.ReactNode }> = ({ s, c = '#fff', glow = GLOW, style, children }) => (
  <div style={{ fontFamily: F, fontWeight: 900, fontSize: s, color: c, textShadow: glow, textAlign: 'center', lineHeight: 1.05, letterSpacing: s * 0.04, ...style }}>{children}</div>
);

const CARD_COLORS = ['#FF5A7A', '#FFB02E', '#3EC6E0', '#8A5AF0', '#3FBF6A', '#FF7A3D'];

/** Handy: Short-Videos wischen NUR im Bildschirm; unten die Leiste. removeShorts 0..1 lässt den Shorts-Knopf verschwinden,
 *  longVideo 0..1 zeigt statt Shorts ein normales langes Video. */
const Phone: React.FC<{ t: number; x: number; y: number; s: number; swipe?: boolean; removeShorts?: number; longVideo?: number }> = ({ t, x, y, s, swipe = true, removeShorts = 0, longVideo = 0 }) => {
  const W = 500, H = 1000;
  const period = 0.55;
  const k = Math.floor(t / period);
  const sp = swipe ? ease(t % period, 0, 0.18) : 1;
  const kinds = ['home', 'shorts', 'plus', 'you'] as const;
  const burst = lin(removeShorts, 0, 1);
  return (
    <div style={{ position: 'absolute', left: x - (W * s) / 2, top: y - (H * s) / 2, width: W, height: H, transform: `scale(${s})`, transformOrigin: '0 0' }}>
      <div style={{ position: 'absolute', inset: 0, borderRadius: 64, background: '#0d0d12', boxShadow: '0 0 0 6px #2a2a33, 0 30px 80px rgba(0,0,0,.6)' }} />
      <div style={{ position: 'absolute', left: 22, top: 22, right: 22, bottom: 22, borderRadius: 46, overflow: 'hidden', background: '#000' }}>
        {/* Inhalt: Shorts wischen */}
        <div style={{ position: 'absolute', inset: 0, opacity: 1 - longVideo }}>
          {[k - 1, k].map((j) => j >= 0 && (
            <div key={j} style={{ position: 'absolute', left: 0, right: 0, height: '100%', top: `${j === k ? 100 * (1 - sp) : -100 * sp}%`, background: `radial-gradient(circle at 50% 38%, ${CARD_COLORS[j % CARD_COLORS.length]}, #111 80%)` }}>
              <div style={{ position: 'absolute', left: '50%', top: '38%', transform: 'translate(-40%,-50%)', borderTop: '34px solid transparent', borderBottom: '34px solid transparent', borderLeft: '56px solid rgba(255,255,255,.9)' }} />
              <div style={{ position: 'absolute', left: 28, bottom: 170, width: 260, height: 18, borderRadius: 9, background: 'rgba(255,255,255,.7)' }} />
              <div style={{ position: 'absolute', left: 28, bottom: 140, width: 180, height: 18, borderRadius: 9, background: 'rgba(255,255,255,.45)' }} />
              {[0, 1, 2].map((i) => <div key={i} style={{ position: 'absolute', right: 26, bottom: 160 + i * 90, width: 56, height: 56, borderRadius: '50%', background: 'rgba(255,255,255,.25)' }} />)}
            </div>
          ))}
        </div>
        {/* Inhalt: lange Videos (Liste) */}
        <div style={{ position: 'absolute', inset: 0, opacity: longVideo, background: '#0f0f0f', padding: '40px 26px' }}>
          {[0, 1, 2].map((i) => (
            <div key={i} style={{ marginBottom: 34 }}>
              <div style={{ height: 230, borderRadius: 18, background: ['#3E6FE0', '#2E9E57', '#8A5AF0'][i], position: 'relative' }}>
                <div style={{ position: 'absolute', right: 12, bottom: 12, background: 'rgba(0,0,0,.75)', color: '#fff', fontFamily: F, fontWeight: 800, fontSize: 22, padding: '2px 10px', borderRadius: 8 }}>{['18:42', '1:02:10', '24:05'][i]}</div>
              </div>
              <div style={{ marginTop: 14, height: 16, width: '80%', borderRadius: 8, background: 'rgba(255,255,255,.6)' }} />
            </div>
          ))}
        </div>
        {/* Leiste */}
        <div style={{ position: 'absolute', left: 0, right: 0, bottom: 0, height: 110, background: '#141414', borderTop: '2px solid #2b2b2b', display: 'flex', justifyContent: 'space-around', alignItems: 'center' }}>
          {kinds.map((kd) => {
            if (kd === 'shorts' && removeShorts >= 1) return null;
            const isS = kd === 'shorts';
            return (
              <div key={kd} style={{ width: 64, height: 64, display: 'flex', alignItems: 'center', justifyContent: 'center', transform: isS ? `scale(${1 - burst}) rotate(${burst * 90}deg)` : 'scale(1.1)', filter: isS ? `drop-shadow(0 0 12px ${RED})` : undefined }}>
                <BarIcon kind={kd} color={isS ? RED : '#fff'} />
              </div>
            );
          })}
        </div>
        {/* Splitter des Shorts-Knopfs, bleiben im Bildschirm */}
        {burst > 0 && burst < 1 && Array.from({ length: 12 }, (_, i) => {
          const a = (i / 12) * Math.PI * 2;
          return <div key={i} style={{ position: 'absolute', left: 175 + Math.cos(a) * 90 * burst, top: 900 + Math.sin(a) * 60 * burst - 120 * burst, width: 12, height: 12, background: RED, opacity: 1 - burst, borderRadius: 2 }} />;
        })}
      </div>
    </div>
  );
};

/** 0–2.6: HOOK – 8 YEARS. */
const Hook: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, T.hook, T.perDay, 0.12);
  if (a <= 0) return null;
  const slam = ease(t, 0, 0.18);
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Txt s={420} c={RED} glow={RGLOW} style={{ position: 'absolute', left: 0, right: 0, top: 120, transform: `scale(${1.6 - 0.6 * slam})`, opacity: slam }}>8</Txt>
      <Txt s={190} c={RED} glow={RGLOW} style={{ position: 'absolute', left: 0, right: 0, top: 540, whiteSpace: 'nowrap', opacity: slam }}>YEARS</Txt>
      <Txt s={64} style={{ position: 'absolute', left: 0, right: 0, top: 780, opacity: ease(t, 0.45, 0.65) }}>OF YOUR LIFE.</Txt>
      <Txt s={64} style={{ position: 'absolute', left: 0, right: 0, top: 870, opacity: ease(t, 0.9, 1.1) }}>ON THIS. 👇</Txt>
      <Phone t={t} x={540} y={1360} s={0.52} />
    </AbsoluteFill>
  );
};

/** Grosse Rechenzeile mit Zähler. */
const Calc: React.FC<{ t: number; a0: number; a1: number; top: string; from: number; to: number; unit: string; bottom: string; countEnd: number }> = ({ t, a0, a1, top, from, to, unit, bottom, countEnd }) => {
  const a = sc(t, a0, a1);
  if (a <= 0) return null;
  const v = from + (to - from) * ease(t, a0 + 0.35, countEnd);
  return (
    <AbsoluteFill style={{ opacity: a, alignItems: 'center', justifyContent: 'center', gap: 30 }}>
      <Txt s={56} c="#ccc" glow="none" style={{ opacity: ease(t, a0, a0 + 0.2) }}>{top}</Txt>
      <Txt s={210} c={RED} glow={RGLOW}>{fmt(v)}</Txt>
      <Txt s={88}>{unit}</Txt>
      <Txt s={46} c="#bbb" glow="none" style={{ opacity: ease(t, countEnd, countEnd + 0.2) }}>{bottom}</Txt>
    </AbsoluteFill>
  );
};

/** 8.2–11.4: Kalender mit 365 Tagen, 45 werden rot. */
const Days: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, T.days, T.life);
  if (a <= 0) return null;
  const n = Math.floor(45.6 * ease(t, T.days + 0.5, T.days + 2.0));
  const cols = 15;
  const cell = 46;
  const gridW = cols * cell;
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Txt s={56} c="#ccc" glow="none" style={{ position: 'absolute', left: 0, right: 0, top: 210 }}>THAT'S</Txt>
      <Txt s={150} c={RED} glow={RGLOW} style={{ position: 'absolute', left: 0, right: 0, top: 290 }}>{n} DAYS</Txt>
      <Txt s={52} style={{ position: 'absolute', left: 0, right: 0, top: 470 }}>NON-STOP. EVERY YEAR.</Txt>
      <div style={{ position: 'absolute', left: (1080 - gridW) / 2, top: 600, width: gridW }}>
        {Array.from({ length: 365 }, (_, i) => (
          <div key={i} style={{ position: 'absolute', left: (i % cols) * cell, top: Math.floor(i / cols) * cell, width: cell - 8, height: cell - 8, borderRadius: 8, background: i < n ? RED : '#2a2a30', boxShadow: i < n ? `0 0 10px ${RED}` : undefined }} />
        ))}
      </div>
      <Txt s={30} c="#888" glow="none" style={{ position: 'absolute', left: 0, right: 0, top: 1790 }}>1 SQUARE = 1 DAY · 365 DAYS</Txt>
    </AbsoluteFill>
  );
};

/** 11.4–15.2: Lebensjahre 13–80, zusammengezählt 8,4 Jahre rot. */
const Life: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, T.life, T.gone);
  if (a <= 0) return null;
  const years = 67; // 13 bis 80
  const shown = Math.floor(years * ease(t, T.life + 0.2, T.life + 1.3));
  const red = 8.4 * ease(t, T.life + 1.7, T.life + 2.9);
  const cols = 8;
  const cell = 104;
  const gridW = cols * cell;
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Txt s={52} c="#ccc" glow="none" style={{ position: 'absolute', left: 0, right: 0, top: 170 }}>FROM AGE 13 TO 80</Txt>
      <Txt s={40} c="#999" glow="none" style={{ position: 'absolute', left: 0, right: 0, top: 240, opacity: ease(t, T.life + 1.5, T.life + 1.7) }}>ALL THAT TIME ADDED UP:</Txt>
      <div style={{ position: 'absolute', left: (1080 - gridW) / 2, top: 330, width: gridW }}>
        {Array.from({ length: years }, (_, i) => {
          const fromEnd = years - 1 - i;
          const fill = Math.max(0, Math.min(1, red - fromEnd));
          return (
            <div key={i} style={{ position: 'absolute', left: (i % cols) * cell, top: Math.floor(i / cols) * cell, width: cell - 14, height: cell - 14, borderRadius: 14, background: '#2a2a30', opacity: i < shown ? 1 : 0, overflow: 'hidden' }}>
              <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: `${fill * 100}%`, background: RED, boxShadow: fill > 0 ? `0 0 14px ${RED}` : undefined }} />
              {(i === 0 || i === years - 1) && <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: F, fontWeight: 900, fontSize: 30, color: '#fff' }}>{i === 0 ? 13 : 80}</div>}
            </div>
          );
        })}
      </div>
      <Txt s={170} c={RED} glow={RGLOW} style={{ position: 'absolute', left: 0, right: 0, top: 1300, opacity: ease(t, T.life + 2.9, T.life + 3.1), transform: `scale(${1.3 - 0.3 * ease(t, T.life + 2.9, T.life + 3.1)})` }}>8.4 YEARS</Txt>
      <Txt s={30} c="#888" glow="none" style={{ position: 'absolute', left: 0, right: 0, top: 1520 }}>1 SQUARE = 1 YEAR</Txt>
    </AbsoluteFill>
  );
};

/** 15.2–17.0: GONE. */
const Gone: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, T.gone, T.fix, 0.12);
  if (a <= 0) return null;
  return (
    <AbsoluteFill style={{ opacity: a, alignItems: 'center', justifyContent: 'center', gap: 30 }}>
      <Txt s={62}>8 YEARS YOU'LL</Txt>
      <Txt s={62}>NEVER GET BACK.</Txt>
      <Txt s={64} c={RED} glow={RGLOW} style={{ marginTop: 40, opacity: ease(t, T.gone + 0.7, T.gone + 0.9) }}>FOR VIDEOS YOU</Txt>
      <Txt s={64} c={RED} glow={RGLOW} style={{ opacity: ease(t, T.gone + 0.7, T.gone + 0.9) }}>DON'T EVEN REMEMBER.</Txt>
    </AbsoluteFill>
  );
};

/** 17.0–20.2: YouTube lässt dich Shorts nicht ausschalten → F*ck Shorts entfernt den Knopf. */
const Fix: React.FC<{ t: number }> = ({ t }) => {
  const a = sc(t, T.fix, T.cta, 0.15);
  if (a <= 0) return null;
  const rm = lin(t, T.fix + 1.5, T.fix + 2.0);
  const long = ease(t, T.fix + 1.9, T.fix + 2.3);
  return (
    <AbsoluteFill style={{ opacity: a }}>
      <Txt s={52} style={{ position: 'absolute', left: 0, right: 0, top: 170, opacity: 1 - ease(t, T.fix + 1.3, T.fix + 1.5) }}>YOUTUBE WON'T LET YOU</Txt>
      <Txt s={52} c={RED} glow={RGLOW} style={{ position: 'absolute', left: 0, right: 0, top: 240, opacity: 1 - ease(t, T.fix + 1.3, T.fix + 1.5) }}>TURN SHORTS OFF.</Txt>
      <div style={{ position: 'absolute', left: 0, right: 0, top: 150, display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 24, opacity: ease(t, T.fix + 1.4, T.fix + 1.6) }}>
        <Logo size={110} />
        <Txt s={84}>F<span style={{ color: RED }}>*</span>CK SHORTS</Txt>
      </div>
      <Txt s={40} c="#ddd" glow="none" style={{ position: 'absolute', left: 0, right: 0, top: 290, opacity: long }}>REMOVES SHORTS. KEEPS YOUTUBE.</Txt>
      <Phone t={t} x={540} y={1180} s={0.78} swipe={rm <= 0} removeShorts={rm} longVideo={long} />
    </AbsoluteFill>
  );
};

/** 20.2–Ende: CTA. */
const Cta: React.FC<{ t: number }> = ({ t }) => {
  const a = ease(t, T.cta, T.cta + 0.25);
  if (a <= 0) return null;
  const b1 = ease(t, T.cta + 0.3, T.cta + 0.5);
  const b2 = ease(t, T.cta + 0.8, T.cta + 1.0);
  return (
    <AbsoluteFill style={{ opacity: a, alignItems: 'center', justifyContent: 'center', gap: 40 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
        <Logo size={120} />
        <Txt s={84}>F<span style={{ color: RED }}>*</span>CK SHORTS</Txt>
      </div>
      <div style={{ opacity: b1, transform: `scale(${0.8 + 0.2 * b1 + 0.03 * Math.sin(t * 8)})`, background: '#fff', color: RED, fontFamily: F, fontWeight: 900, fontSize: 70, padding: '24px 60px', borderRadius: 80 }}>COMMENT "TEST"</div>
      <div style={{ opacity: b2, transform: `scale(${0.85 + 0.15 * b2 + 0.03 * Math.sin(t * 8 + 1)})`, background: RED, color: '#fff', fontFamily: F, fontWeight: 900, fontSize: 56, padding: '20px 50px', borderRadius: 80, boxShadow: RGLOW }}>SIGN UP 👉 LINK IN BIO</div>
      <Txt s={34} c="#ccc" glow="none" style={{ marginTop: 20, opacity: ease(t, T.cta + 1.4, T.cta + 1.6) }}>COMING SOON TO GOOGLE PLAY</Txt>
      <Txt s={30} c="#aaa" glow="none" style={{ opacity: ease(t, T.cta + 1.4, T.cta + 1.6) }}>WE NEED 12 TESTERS · BE ONE OF THEM</Txt>
    </AbsoluteFill>
  );
};

const PUNCH = [0, 0.45, 0.9, T.perDay + 0.35, T.perYear + 0.35, T.days, T.life + 2.9, T.gone, T.gone + 0.7, T.fix + 1.5, T.cta];
const SHAKE = [0, T.life + 2.9, T.gone + 0.7, T.fix + 1.5];

export const Ad8: React.FC<{ sound: boolean }> = ({ sound }) => {
  useFonts();
  const t = useCurrentFrame() / FPS8;
  const pk = Math.max(0, ...PUNCH.map((p) => (t >= p ? Math.exp(-(t - p) / 0.1) : 0)));
  const sk = Math.max(0, ...SHAKE.map((p) => (t >= p && t < p + 0.3 ? 1 - (t - p) / 0.3 : 0)));
  return (
    <AbsoluteFill style={{ background: 'radial-gradient(70% 55% at 50% 45%, #1a1014 0%, #050505 75%)', overflow: 'hidden' }}>
      {sound && <Audio src={staticFile('sfx8.wav')} />}
      <AbsoluteFill style={{ transform: `translate(${sk * 12 * Math.sin(t * 97)}px, ${sk * 9 * Math.cos(t * 83)}px) scale(${1 + 0.05 * pk})` }}>
        <Hook t={t} />
        <Calc t={t} a0={T.perDay} a1={T.perYear} top="IF YOU SCROLL" from={0} to={3} unit="HOURS A DAY" bottom="OF SHORTS, REELS & TIKTOK" countEnd={T.perDay + 1.2} />
        <Calc t={t} a0={T.perYear} a1={T.days} top="× 365 DAYS =" from={0} to={1095} unit="HOURS A YEAR" bottom="GONE. EVERY. YEAR." countEnd={T.perYear + 1.6} />
        <Days t={t} />
        <Life t={t} />
        <Gone t={t} />
        <Fix t={t} />
        <Cta t={t} />
      </AbsoluteFill>
      <AbsoluteFill style={{ background: `radial-gradient(70% 60% at 50% 50%, transparent 55%, rgba(229,23,43,${0.2 * sk}) 100%)`, pointerEvents: 'none' }} />
    </AbsoluteFill>
  );
};
