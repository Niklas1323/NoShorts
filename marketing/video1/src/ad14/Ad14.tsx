// Video 14 ("Break the cycle"): Variante von Video 13. 3D-Handy im Stil "Papier + Handy + herausspringende Bedienelemente".
// Alles ist eine reine Funktion der Zeit t (Sekunden), damit Remotion jedes Bild einzeln rendern kann.
import React, { useEffect, useMemo, useState } from 'react';
import { AbsoluteFill, Audio, continueRender, delayRender, staticFile, useCurrentFrame } from 'remotion';
import { ThreeCanvas } from '@remotion/three';
import * as THREE from 'three';
import '@fontsource/inter/800.css';
import '@fontsource/inter/900.css';
import '@fontsource/instrument-serif/400-italic.css';
import PARTS from '../ad13/particles13.json';

export const FPS14 = 30;
export const DURATION14_S = 37;
const VW = 1080;
const VH = 1920;

// ------------------------------------------------------------------ Zeit-Helfer
const clamp01 = (x: number) => Math.max(0, Math.min(1, x));
const ramp = (t: number, a: number, b: number) => clamp01((t - a) / (b - a));
const lerp = (a: number, b: number, x: number) => a + (b - a) * x;
const easeIO = (x: number) => (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2);
const easeIO4 = (x: number) => (x < 0.5 ? 8 * x ** 4 : 1 - Math.pow(-2 * x + 2, 4) / 2);
const easeOut = (x: number) => 1 - Math.pow(1 - x, 3);
// federnd: schiesst ~10 % über und kommt zur Ruhe
const springy = (x: number) => (x <= 0 ? 0 : x >= 1 ? 1 : 1 - Math.exp(-5.2 * x) * Math.cos(7.2 * x));
/** 0 → 1 (federnd) ab a, ab b wieder → 0 */
const pop = (t: number, a: number, b = 1e9, din = 0.55, dout = 0.32) => {
  if (t < a) return 0;
  if (t < a + din) return springy((t - a) / din);
  if (t < b) return 1;
  return 1 - easeIO(ramp(t, b, b + dout));
};
/** weich 0 → 1 → 0 */
const win = (t: number, a: number, b: number, din = 0.3, dout = 0.3) => easeIO(ramp(t, a, a + din)) * (1 - easeIO(ramp(t, b, b + dout)));

// ------------------------------------------------------------------ Bilder
const SCREENS = ['yt_real', 'yt_fs', 'uninstall', 'lock', 'lock_mon', 'lock_fri', 'search', 'short_full', 'short_full2', 'short_full3', 'app_home'];
const BLURS = ['yt_real', 'yt_fs', 'lock', 'lock_mon', 'lock_fri', 'search', 'app_home'].map((n) => n + '_blur');
const LAYERS = ['screentime', 'bar_real', 'bar_real_full', 'bar_fs', 'bar_fs_empty', 'subs_slot', 'shorts_slot', 'notif0', 'notif1', 'notif2',
  'card0', 'card1', 'card2', 'header', 'vcard0', 'vcard1', 'vcard2', 'shield', 'closed', 'ring', 'ring_rect', 'gleam', 'stop'];
const SHADOWS = ['screentime', 'bar_real', 'bar_real_full', 'bar_fs', 'bar_fs_empty', 'notif0', 'notif1', 'notif2', 'card0', 'card1', 'card2',
  'header', 'shield', 'closed'].map((n) => n + '_sh');
const ALL = [...SCREENS, ...BLURS, ...LAYERS, ...SHADOWS];

type Assets = { img: Record<string, HTMLImageElement>; tex: Record<string, THREE.Texture> };

const useAssets = (): Assets | null => {
  const [handle] = useState(() => delayRender('ad14 Bilder'));
  const [a, setA] = useState<Assets | null>(null);
  useEffect(() => {
    Promise.all(
      ALL.map(
        (n) =>
          new Promise<[string, HTMLImageElement]>((res, rej) => {
            const im = new Image();
            im.onload = () => res([n, im]);
            im.onerror = () => rej(new Error('Bild fehlt: ' + n));
            im.src = staticFile(`ad13/${n}.png`);
          }),
      ),
    ).then((list) => {
      const img = Object.fromEntries(list);
      const tex: Record<string, THREE.Texture> = {};
      for (const n of LAYERS) {
        const tx = new THREE.Texture(img[n]);
        tx.colorSpace = THREE.SRGBColorSpace;
        tx.anisotropy = 8;
        tx.needsUpdate = true;
        tex[n] = tx;
      }
      setA({ img, tex });
      continueRender(handle);
    });
  }, [handle]);
  return a;
};

// ------------------------------------------------------------------ Handy-Masse (Einheiten)
const SW = 0.93; // Bildschirm-Breite
const SH = (SW * 2424) / 1080; // 2.087
const BW = SW + 0.075;
const BH = SH + 0.075;
const DEPTH = 0.05;
const BEV = 0.013;
const FRONT = DEPTH / 2 + BEV; // Vorderseite
const SURF = FRONT + 0.0022; // Bildschirm
const UPX = SW / 1080; // Einheiten pro Bildschirm-Pixel
const sx = (x: number) => (x / 1080 - 0.5) * SW;
const sy = (y: number) => (0.5 - y / 2424) * SH;

const roundRect = (w: number, h: number, r: number) => {
  const s = new THREE.Shape();
  const x = -w / 2;
  const y = -h / 2;
  s.moveTo(x + r, y);
  s.lineTo(x + w - r, y);
  s.quadraticCurveTo(x + w, y, x + w, y + r);
  s.lineTo(x + w, y + h - r);
  s.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
  s.lineTo(x + r, y + h);
  s.quadraticCurveTo(x, y + h, x, y + h - r);
  s.lineTo(x, y + r);
  s.quadraticCurveTo(x, y, x + r, y);
  return s;
};
const planeUV = (geo: THREE.BufferGeometry, w: number, h: number) => {
  const p = geo.attributes.position;
  const uv = new Float32Array(p.count * 2);
  for (let i = 0; i < p.count; i++) {
    uv[i * 2] = p.getX(i) / w + 0.5;
    uv[i * 2 + 1] = p.getY(i) / h + 0.5;
  }
  geo.setAttribute('uv', new THREE.BufferAttribute(uv, 2));
  return geo;
};

// ------------------------------------------------------------------ Bildschirm-Inhalt (2D-Canvas als Textur)
const CW = 720;
const CH = Math.round((CW * 2424) / 1080);

const SCREEN_SEQ: [number, string, number][] = [
  [0, 'yt_real', 0],
  [5.05, 'short_full', 0],
  [8.7, 'lock', 0],
  [9.1, 'lock_fri', 0.22],
  [10.4, 'uninstall', 0.25],
  [12.3, 'lock_mon', 0],
  [13.0, 'lock', 0.22],
  [16.6, 'yt_real', 0.3],
  [19.6, 'yt_fs', 0],
  [23.7, 'search', 0.2],
  [28.3, 'app_home', 0],
];
// Shorts weiterwischen ("Then another"): [Start, nächster Short]
const SWIPES: [number, string][] = [
  [5.82, 'short_full2'],
  [6.3, 'short_full3'],
];
const SHELF_CARD = { x: 30, y: 1375, w: 500, h: 888 }; // Short im Regal der Startseite
const FEED_VIDEO = { x: 540, y: 760 };
const CARD = { x: 32, y: 300, w: 496, h: 880 }; // angetippter Short im Suchraster
const OK_BTN = { x: 862, y: 1300 };
const TAP_SEARCH = 24.4;

type PopSpec = {
  key: string;
  tex: string;
  w: number;
  h: number;
  from: [number, number, number]; // x, y (Bildschirm-Pixel), Massstab
  to: [number, number, number];
  p: number; // 0 = im Bildschirm, 1 = herausgesprungen
  z: number; // Sprunghöhe (Einheiten)
  op?: number;
  rotZ?: number;
  rotY?: number;
  shadow?: boolean;
  children?: React.ReactNode;
};

const popPos = (s: PopSpec) => {
  const e = clamp01(s.p);
  const k = s.p; // federnd über 1 erlaubt für Grösse/Höhe
  return {
    x: lerp(s.from[0], s.to[0], e),
    y: lerp(s.from[1], s.to[1], e),
    s: lerp(s.from[2], s.to[2], k),
    z: SURF + 0.004 + s.z * k,
  };
};

const drawScreen = (ctx: CanvasRenderingContext2D, t: number, img: Record<string, HTMLImageElement>, pops: PopSpec[], ry: number) => {
  const k = CW / 1080;
  ctx.setTransform(k, 0, 0, k, 0, 0);
  ctx.globalAlpha = 1;
  ctx.globalCompositeOperation = 'source-over';
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, 1080, 2424);

  // Unschärfe/Abdunklung, solange etwas herausgesprungen ist
  const blur = Math.max(
    0.85 * win(t, -1, 2.55, 0.3, 0.35),
    0.6 * win(t, 6.9, 8.3, 0.3, 0.3),
    0.35 * win(t, 9.5, 10.3, 0.25, 0.25),
    win(t, 16.9, 18.95, 0.3, 0.35),
    0.85 * win(t, 21.15, 23.5, 0.4, 0.3),
    0.6 * win(t, 25.5, 26.8, 0.3, 0.3),
    0.6 * win(t, 26.95, 27.8, 0.25, 0.2),
    0.8 * win(t, 29.0, 99, 0.35, 0.3),
  );
  const dim = 0.4 * blur + 0.18 * win(t, 15.15, 16.3, 0.3, 0.3);

  const drawBase = (n: string, a: number) => {
    if (a <= 0) return;
    ctx.globalAlpha = a;
    ctx.drawImage(img[n], 0, 0, 1080, 2424);
    const b = img[n + '_blur'];
    if (b && blur > 0.01) {
      ctx.globalAlpha = a * blur;
      ctx.drawImage(b, 0, 0, 1080, 2424);
    }
  };
  let cur = 0;
  for (let i = 0; i < SCREEN_SEQ.length; i++) if (t >= SCREEN_SEQ[i][0]) cur = i;
  const [t0, name, xf] = SCREEN_SEQ[cur];
  if (xf > 0 && t < t0 + xf && cur > 0) {
    drawBase(SCREEN_SEQ[cur - 1][1], 1);
    drawBase(name, easeIO(ramp(t, t0, t0 + xf)));
  } else drawBase(name, 1);

  // "One Short": Short aus dem Regal geht auf
  if (t > 4.78 && t < 5.1) {
    const open = easeOut(ramp(t, 4.8, 5.05));
    ctx.save();
    ctx.globalAlpha = 1;
    const x = lerp(SHELF_CARD.x, 0, open);
    const y = lerp(SHELF_CARD.y, 0, open);
    const w = lerp(SHELF_CARD.w, 1080, open);
    const h = lerp(SHELF_CARD.h, 2424, open);
    ctx.beginPath();
    ctx.roundRect(x, y, w, h, lerp(24, 0, open));
    ctx.clip();
    ctx.drawImage(img.short_full, x, y, w, h);
    ctx.restore();
  }
  // "Then another": Weiterwischen
  if (t >= 5.82 && t < 8.7) {
    let curImg = 'short_full';
    for (const [at, n] of SWIPES) {
      if (t < at) break;
      const e = easeIO(ramp(t, at, at + 0.26));
      if (e < 1) {
        ctx.globalAlpha = 1;
        ctx.drawImage(img[curImg], 0, -2424 * e, 1080, 2424);
        ctx.drawImage(img[n], 0, 2424 * (1 - e), 1080, 2424);
      } else {
        ctx.globalAlpha = 1;
        ctx.drawImage(img[n], 0, 0, 1080, 2424);
      }
      curImg = n;
    }
    if (blur > 0.01 && t > 6.8) {
      ctx.globalAlpha = blur * 0.85;
      ctx.filter = 'blur(18px)';
      ctx.drawImage(img[curImg], 0, 0, 1080, 2424);
      ctx.filter = 'none';
    }
  }

  // angetippter Short in der Suche geht auf und sofort wieder zu (unsere Leiste bleibt stehen)
  if (t > TAP_SEARCH + 0.08 && t < 25.65) {
    const open = easeOut(ramp(t, TAP_SEARCH + 0.1, TAP_SEARCH + 0.34)) * (1 - easeIO(ramp(t, 25.38, 25.58)));
    if (open > 0.001) {
      const x = lerp(CARD.x, 0, open);
      const y = lerp(CARD.y, 0, open);
      const w = lerp(CARD.w, 1080, open);
      const h = lerp(CARD.h, 2424, open);
      const r = lerp(26, 0, open);
      ctx.save();
      ctx.globalAlpha = 1;
      ctx.beginPath();
      ctx.roundRect(x, y, w, h, r);
      ctx.clip();
      ctx.drawImage(img.short_full, x, y, w, h);
      ctx.restore();
      ctx.globalAlpha = 1;
      ctx.drawImage(img.yt_fs, 0, 2235, 1080, 189, 0, 2235, 1080, 189);
    }
  }

  if (dim > 0) {
    ctx.globalAlpha = dim;
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, 1080, 2424);
  }

  // Finger-Tipps
  const tap = (x: number, y: number, at: number) => {
    const a = win(t, at - 0.45, at + 0.12, 0.2, 0.2);
    if (a <= 0) return;
    const move = easeOut(ramp(t, at - 0.45, at - 0.05));
    const fx = lerp(x + 160, x, move);
    const fy = lerp(y + 260, y, move);
    const press = 1 - 0.18 * win(t, at - 0.06, at + 0.04, 0.06, 0.1);
    ctx.globalAlpha = a * 0.9;
    ctx.fillStyle = 'rgba(255,255,255,0.55)';
    ctx.strokeStyle = '#fff';
    ctx.lineWidth = 6;
    ctx.beginPath();
    ctx.arc(fx, fy, 48 * press, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
    const rp = ramp(t, at, at + 0.4);
    if (rp > 0 && rp < 1) {
      ctx.globalAlpha = (1 - rp) * 0.9;
      ctx.lineWidth = 8;
      ctx.beginPath();
      ctx.arc(x, y, 50 + rp * 120, 0, Math.PI * 2);
      ctx.stroke();
    }
  };
  tap(FEED_VIDEO.x, FEED_VIDEO.y, 3.85);
  tap(SHELF_CARD.x + SHELF_CARD.w / 2, SHELF_CARD.y + 330, 4.74);
  tap(OK_BTN.x, OK_BTN.y, 11.66);
  tap(CARD.x + CARD.w / 2, CARD.y + CARD.h * 0.45, TAP_SEARCH);

  // Schatten der herausgesprungenen Ebenen fallen auf den Bildschirm
  for (const s of pops) {
    if (!s.shadow || s.p <= 0.01) continue;
    const sh = img[s.tex + '_sh'];
    if (!sh) continue;
    const q = popPos(s);
    const pad = 70 * q.s;
    const off = 34 + 70 * clamp01(s.p);
    ctx.save();
    ctx.globalAlpha = 0.6 * clamp01(s.p) * (s.op ?? 1);
    ctx.translate(q.x, q.y + off);
    if (s.rotZ) ctx.rotate(-s.rotZ);
    ctx.drawImage(sh, -(s.w * q.s) / 2 - pad, -(s.h * q.s) / 2 - pad, s.w * q.s + 2 * pad, s.h * q.s + 2 * pad);
    ctx.restore();
  }

  // Bildschirm aus (vor dem Sperrbildschirm)
  const black = Math.max(ramp(t, 8.5, 8.66) * (1 - ramp(t, 8.72, 8.92)), ramp(t, 12.08, 12.25) * (1 - ramp(t, 12.3, 12.5)));
  if (black > 0) {
    ctx.globalAlpha = black;
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, 1080, 2424);
  }

  // Kamera-Loch
  ctx.globalAlpha = 1;
  ctx.fillStyle = '#020202';
  ctx.beginPath();
  ctx.arc(540, 64, 25, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = 'rgba(70,70,80,0.6)';
  ctx.lineWidth = 3;
  ctx.stroke();

  // Glas-Spiegelung, wandert mit der Drehung
  const off = ((ry * 900) % 2600) - 600;
  const g = ctx.createLinearGradient(off, 0, off + 900, 700);
  g.addColorStop(0, 'rgba(255,255,255,0)');
  g.addColorStop(0.5, 'rgba(255,255,255,0.07)');
  g.addColorStop(1, 'rgba(255,255,255,0)');
  ctx.globalAlpha = 1;
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, 1080, 2424);
};

// ------------------------------------------------------------------ 3D-Bausteine
const TexPlane: React.FC<{
  tex: THREE.Texture;
  w: number;
  h: number;
  op?: number;
  order?: number;
  additive?: boolean;
  position?: [number, number, number];
  rotation?: [number, number, number];
  scale?: number;
}> = ({ tex, w, h, op = 1, order = 5, additive, position, rotation, scale = 1 }) => (
  <mesh position={position} rotation={rotation} scale={[scale, scale, scale]} renderOrder={order} visible={op > 0.002}>
    <planeGeometry args={[w, h]} />
    <meshBasicMaterial
      map={tex}
      transparent
      opacity={op}
      depthWrite={false}
      toneMapped={false}
      blending={additive ? THREE.AdditiveBlending : THREE.NormalBlending}
      side={THREE.DoubleSide}
    />
  </mesh>
);

/** Ebene, die aus dem Bildschirm springt. Kinder liegen in Ebenen-Pixeln (0,0 = links oben). */
const Pop: React.FC<{ s: PopSpec; tex: Record<string, THREE.Texture> }> = ({ s, tex }) => {
  if (s.p <= 0.002) return null;
  const q = popPos(s);
  const op = (s.op ?? 1) * clamp01(s.p * 3);
  return (
    <group position={[sx(q.x), sy(q.y), q.z]} rotation={[0, s.rotY ?? 0, s.rotZ ?? 0]} scale={[q.s * UPX, q.s * UPX, q.s * UPX]}>
      <TexPlane tex={tex[s.tex]} w={s.w} h={s.h} op={op} order={10} />
      <group position={[-s.w / 2, s.h / 2, 0]} scale={[1, -1, 1]}>
        {s.children}
      </group>
    </group>
  );
};

/** Kind in Ebenen-Pixeln (y nach unten). */
const At: React.FC<{ x: number; y: number; z?: number; children: React.ReactNode }> = ({ x, y, z = 2, children }) => (
  <group position={[x, y, z]} scale={[1, -1, 1]}>
    {children}
  </group>
);

const Ring: React.FC<{ t: number; at: number; end?: number; size: number; tex: Record<string, THREE.Texture>; pulses?: number[]; gleams?: number[] }> = ({
  t,
  at,
  end = 1e9,
  size,
  tex,
  pulses = [],
  gleams = [],
}) => {
  const a = easeOut(ramp(t, at, at + 0.3)) * (1 - easeIO(ramp(t, end, end + 0.25)));
  if (a <= 0) return null;
  let sc = lerp(1.45, 1, easeOut(ramp(t, at, at + 0.35)));
  for (const p of pulses) sc *= 1 + 0.16 * Math.sin(Math.PI * ramp(t, p, p + 0.35));
  return (
    <group>
      <TexPlane tex={tex.ring} w={size} h={size} op={a} order={12} rotation={[0, 0, t * 0.55]} scale={sc} />
      {gleams.map((g, i) => {
        const p = ramp(t, g, g + 0.5);
        if (p <= 0 || p >= 1) return null;
        return (
          <TexPlane
            key={i}
            tex={tex.gleam}
            w={size * 1.25}
            h={size * 0.55}
            op={Math.sin(Math.PI * p) * a}
            order={13}
            additive
            position={[lerp(-size * 0.75, size * 0.75, easeIO(p)), 0, 1]}
            rotation={[0, 0, 0.32]}
          />
        );
      })}
    </group>
  );
};

/** Shorts-Symbol zerfällt zu Staub (Punkte aus dem Symbol gelesen). */
const Dust: React.FC<{ t: number; at: number }> = ({ t, at }) => {
  const pts = PARTS as [number, number][];
  const mesh = useMemo(() => {
    const m = new THREE.InstancedMesh(new THREE.PlaneGeometry(1, 1), new THREE.MeshBasicMaterial({ color: '#ffffff', transparent: true, depthWrite: false, toneMapped: false, side: THREE.DoubleSide, blending: THREE.AdditiveBlending }), pts.length);
    m.renderOrder = 14;
    const col = new THREE.Color();
    pts.forEach((_, i) => {
      col.set(i % 3 === 0 ? '#F5C451' : i % 3 === 1 ? '#FFE9B8' : '#ffffff');
      m.setColorAt(i, col);
    });
    return m;
  }, [pts]);
  const rnd = (i: number, k: number) => {
    const v = Math.sin(i * 127.1 + k * 311.7) * 43758.5453;
    return v - Math.floor(v);
  };
  const tau = t - at;
  const o = new THREE.Object3D();
  pts.forEach(([x, y], i) => {
    const delay = ((x - 365) / 80) * 0.3 + rnd(i, 1) * 0.08;
    const d = Math.max(0, tau - delay);
    const life = 0.8 + rnd(i, 2) * 0.4;
    const vx = 160 + rnd(i, 3) * 320;
    const vy = -(140 + rnd(i, 4) * 300);
    const sw = Math.sin(d * 8 + i) * 26 * d;
    o.position.set(x + vx * d + sw, y + vy * d + 120 * d * d, 1 + rnd(i, 5) * 160 * d);
    const sc = tau < 0 ? 0 : d === 0 ? 2.6 : (3.4 + 3 * rnd(i, 7)) * Math.max(0, 1 - d / life) * Math.min(1, 0.6 + d * 4);
    o.scale.set(sc, sc, sc);
    o.rotation.set(0, 0, d * 6 * (rnd(i, 6) - 0.5));
    o.updateMatrix();
    mesh.setMatrixAt(i, o.matrix);
  });
  mesh.instanceMatrix.needsUpdate = true;
  return <primitive object={mesh} />;
};

/** Matcap: Kugel-Bild mit Grundfarbe, Glanzlicht oben links und warmem Rand (Papier-Umgebung). */
const matcapCache: Record<string, THREE.Texture> = {};
const matcap = (base: string, hi: string, warm: string, sharp: number) => {
  const key = base + hi + warm + sharp;
  if (matcapCache[key]) return matcapCache[key];
  const c = document.createElement('canvas');
  c.width = c.height = 256;
  const g = c.getContext('2d')!;
  g.fillStyle = base;
  g.fillRect(0, 0, 256, 256);
  // warmer Rand unten rechts
  let r = g.createRadialGradient(128, 128, 60, 128, 128, 132);
  r.addColorStop(0, 'rgba(0,0,0,0)');
  r.addColorStop(1, warm);
  g.fillStyle = r;
  g.fillRect(0, 0, 256, 256);
  // breites Glanzlicht oben links
  r = g.createRadialGradient(84, 70, 4, 84, 70, 120 - sharp * 60);
  r.addColorStop(0, hi);
  r.addColorStop(1, 'rgba(0,0,0,0)');
  g.fillStyle = r;
  g.fillRect(0, 0, 256, 256);
  // schmaler Lichtstreifen
  g.globalAlpha = 0.5 * sharp;
  r = g.createRadialGradient(178, 60, 2, 178, 60, 26);
  r.addColorStop(0, '#ffffff');
  r.addColorStop(1, 'rgba(255,255,255,0)');
  g.fillStyle = r;
  g.fillRect(0, 0, 256, 256);
  const tx = new THREE.CanvasTexture(c);
  tx.colorSpace = THREE.SRGBColorSpace;
  matcapCache[key] = tx;
  return tx;
};

const usePhoneGeo = () =>
  useMemo(() => {
    const bs = BEV;
    const body = new THREE.ExtrudeGeometry(roundRect(BW - 2 * bs, BH - 2 * bs, 0.145), {
      depth: DEPTH,
      bevelEnabled: true,
      bevelThickness: bs,
      bevelSize: bs,
      bevelSegments: 5,
      curveSegments: 28,
    });
    body.translate(0, 0, -DEPTH / 2);
    const glass = planeUV(new THREE.ShapeGeometry(roundRect(BW - 0.012, BH - 0.012, 0.15), 28), BW, BH);
    const screen = planeUV(new THREE.ShapeGeometry(roundRect(SW, SH, 0.105), 28), SW, SH);
    const visor = new THREE.ExtrudeGeometry(roundRect(BW * 0.84, 0.2, 0.1), { depth: 0.026, bevelEnabled: true, bevelThickness: 0.006, bevelSize: 0.006, bevelSegments: 3, curveSegments: 20 });
    return { body, glass, screen, visor };
  }, []);

const Phone: React.FC<{ t: number; a: Assets; pops: PopSpec[]; ry: number; children: React.ReactNode }> = ({ t, a, pops, ry, children }) => {
  const geo = usePhoneGeo();
  const { canvas, ctex } = useMemo(() => {
    const c = document.createElement('canvas');
    c.width = CW;
    c.height = CH;
    const tx = new THREE.CanvasTexture(c);
    tx.colorSpace = THREE.SRGBColorSpace;
    tx.generateMipmaps = false;
    tx.minFilter = THREE.LinearFilter;
    return { canvas: c, ctex: tx };
  }, []);
  // jedes Bild neu zeichnen (vor dem 3D-Rendern)
  drawScreen(canvas.getContext('2d')!, t, a.img, pops, ry);
  ctex.needsUpdate = true;

  const mats = useMemo(
    () => ({
      cap: new THREE.MeshMatcapMaterial({ matcap: matcap('#0b0b0d', '#3a3a40', '#26262b', 0.35) }),
      side: new THREE.MeshMatcapMaterial({ matcap: matcap('#16161a', '#b9b9c2', '#5c5650', 0.75) }),
      glass: new THREE.MeshMatcapMaterial({ matcap: matcap('#020203', '#4a4a52', '#141416', 0.9) }),
      visor: new THREE.MeshMatcapMaterial({ matcap: matcap('#121215', '#8d8d96', '#3d3833', 0.7) }),
      lens: new THREE.MeshMatcapMaterial({ matcap: matcap('#030305', '#6a6a78', '#1a1a20', 0.95) }),
      button: new THREE.MeshMatcapMaterial({ matcap: matcap('#16161a', '#b9b9c2', '#5c5650', 0.75) }),
    }),
    [],
  );
  return (
    <group>
      <mesh geometry={geo.body} material={[mats.cap, mats.side]} />
      <mesh geometry={geo.glass} material={mats.glass} position={[0, 0, FRONT + 0.0008]} />
      <mesh geometry={geo.screen} position={[0, 0, SURF]} renderOrder={2}>
        <meshBasicMaterial map={ctex} toneMapped={false} />
      </mesh>
      {/* Tasten rechts */}
      <mesh position={[BW / 2 + 0.004, 0.52, 0]} material={mats.button}>
        <boxGeometry args={[0.014, 0.17, 0.03]} />
      </mesh>
      <mesh position={[BW / 2 + 0.004, 0.2, 0]} material={mats.button}>
        <boxGeometry args={[0.014, 0.3, 0.03]} />
      </mesh>
      {/* Kamera-Leiste hinten (Pixel-Stil) */}
      <group position={[0, BH / 2 - 0.32, -FRONT - 0.026]}>
        <mesh geometry={geo.visor} material={mats.visor} />
        {[-0.24, -0.04].map((x) => (
          <mesh key={x} position={[x, 0, -0.008]} rotation={[Math.PI / 2, 0, 0]} material={mats.lens}>
            <cylinderGeometry args={[0.062, 0.062, 0.012, 32]} />
          </mesh>
        ))}
        <mesh position={[0.2, 0, -0.006]} rotation={[Math.PI / 2, 0, 0]}>
          <cylinderGeometry args={[0.022, 0.022, 0.012, 20]} />
          <meshBasicMaterial color="#d9d2c0" />
        </mesh>
      </group>
      {children}
    </group>
  );
};

// ------------------------------------------------------------------ Ablauf der 3D-Szene
type Pose = { x: number; y: number; rx: number; ry: number; rz: number };
const P0: Pose = { x: 0.03, y: -0.74, rx: -0.25, ry: 0.11, rz: -0.03 };
const MOVES: [number, number, Pose][] = [
  [2.6, 3.4, { x: -0.02, y: -0.56, rx: -0.2, ry: -0.13, rz: 0.035 }],
  [8.4, 9.2, { x: 0.01, y: -0.58, rx: -0.24, ry: 0.08, rz: -0.02 }],
  [10.2, 11.0, { x: -0.02, y: -0.56, rx: -0.2, ry: -0.13, rz: 0.035 }],
  [12.3, 13.1, { x: 0, y: -0.58, rx: -0.25, ry: -0.04, rz: 0.0 }],
  [16.4, 17.2, { x: 0.03, y: -0.6, rx: -0.25, ry: 0.11, rz: -0.03 }],
  [19.0, 20.2, { x: 0.02, y: -0.6, rx: -0.27, ry: 0.14, rz: -0.04 }],
  [23.4, 24.1, { x: -0.02, y: -0.56, rx: -0.18, ry: -0.13, rz: 0.035 }],
  [27.75, 28.85, { x: 0.02, y: -1.08, rx: -0.27, ry: 0.12, rz: -0.04 }],
];
const SPINS: [number, number][] = [
  [19.0, 20.2],
  [27.75, 28.85],
];

const poseAt = (t: number) => {
  const p = { ...P0 };
  for (const [a, b, q] of MOVES) {
    const e = easeIO(ramp(t, a, b));
    if (e <= 0) continue;
    (Object.keys(q) as (keyof Pose)[]).forEach((k) => (p[k] = lerp(p[k], q[k], e)));
  }
  // Einstieg: leicht von unten
  p.y -= 0.18 * (1 - easeOut(ramp(t, 0, 0.7)));
  let spin = 0;
  for (const [a, b] of SPINS) spin += Math.PI * 2 * easeIO4(ramp(t, a, b));
  p.ry += spin + 0.035 * Math.sin(t * 0.8);
  p.rx += 0.015 * Math.sin(t * 0.6 + 1);
  return p;
};

const popsAt = (t: number, tex: Record<string, THREE.Texture>): PopSpec[] => {
  const L: PopSpec[] = [];
  const BAR_FROM: [number, number, number] = [540, 2235 + 75, 1];

  // "Three hours. Gone.": Bildschirmzeit springt heraus und fällt dann weg
  const fall = ramp(t, 8.02, 8.5);
  if (t < 8.5) {
    L.push({
      key: 'st',
      tex: 'screentime',
      w: 900,
      h: 400,
      from: [540, 1250, 0.8],
      to: [540, 1060 + 900 * fall * fall, 1.2],
      p: pop(t, 6.86),
      z: 0.42,
      op: 1 - fall,
      rotZ: -0.5 * fall * fall,
      shadow: fall < 0.05,
    });
  }

  // Mitteilungen auf dem Sperrbildschirm
  const NT = [13.9, 14.5, 15.1];
  NT.forEach((at, i) => {
    let p = pop(t, at, i === 0 ? 16.5 : 16.4, 0.6, 0.35);
    let op = 1;
    if (i > 0) {
      const fade = easeIO(ramp(t, 15.1, 15.45));
      op = lerp(1, 0.28, fade);
      p = p * lerp(1, 0.55, fade);
    }
    L.push({
      key: 'n' + i,
      tex: 'notif' + i,
      w: 960,
      h: 214,
      from: [540, 1010 + i * 236, 1],
      to: [540, 900 + i * 262, 1.24],
      p,
      z: 0.3 - i * 0.02,
      op,
      shadow: true,
      children:
        i === 0 ? (
          <At x={480} y={107} z={3}>
            <TexPlane tex={tex.ring_rect} w={1046} h={290} op={win(t, 15.15, 16.3, 0.3, 0.3)} order={12} scale={1 + 0.03 * Math.sin(Math.PI * ramp(t, 15.15, 15.55))} />
            {(() => {
              const g = ramp(t, 15.3, 15.9);
              return g > 0 && g < 1 ? (
                <TexPlane tex={tex.gleam} w={700} h={150} op={Math.sin(Math.PI * g)} order={13} additive position={[lerp(-420, 420, easeIO(g)), 0, 1]} rotation={[0, 0, 0.25]} />
              ) : null;
            })()}
          </At>
        ) : null,
    });
  });

  // "So delete the Shorts, not YouTube": echte Leiste springt heraus, Shorts-Symbol zerfällt
  L.push({
    key: 'bar1',
    tex: t < 18.3 ? 'bar_real_full' : 'bar_real',
    w: 1080,
    h: 150,
    from: BAR_FROM,
    to: [540, 1520, 1.32],
    p: pop(t, 16.88, 18.95),
    z: 0.36,
    shadow: true,
    children: (
      <>
        <At x={405} y={75}>
          <Ring t={t} at={17.3} end={18.2} size={210} tex={tex} gleams={[17.45]} />
        </At>
        {t >= 18.3 && t < 20 ? <Dust t={t} at={18.3} /> : null}
      </>
    ),
  });

  // "No Shorts button": unsere Leiste, Shorts-Knopf dreht sich zu "Abos"
  const flip = Math.PI * easeIO(ramp(t, 21.6, 21.95));
  L.push({
    key: 'bar6',
    tex: 'bar_fs_empty',
    w: 1080,
    h: 150,
    from: BAR_FROM,
    to: [540, 1560, 1.3],
    p: pop(t, 21.12, 23.55) * (1 - 0.72 * win(t, 22.25, 23.35, 0.25, 0.4)),
    z: 0.36,
    shadow: true,
    children: (
      <>
        <At x={405} y={75} z={1}>
          <group rotation={[0, flip, 0]}>
            <TexPlane tex={tex.shorts_slot} w={300} h={150} order={11} op={flip < Math.PI / 2 ? 1 : 0} />
            <group rotation={[0, Math.PI, 0]}>
              <TexPlane tex={tex.subs_slot} w={300} h={150} order={11} op={flip >= Math.PI / 2 ? 1 : 0} />
            </group>
          </group>
        </At>
        <At x={405} y={62} z={3}>
          <Ring t={t} at={21.25} end={23.2} size={200} tex={tex} gleams={[21.4]} />
        </At>
      </>
    ),
  });

  // "closes before it hooks you"
  L.push({
    key: 'closed',
    tex: 'closed',
    w: 520,
    h: 120,
    from: [540, 1250, 0.8],
    to: [540, 1080, 1.75],
    p: pop(t, 25.5, 26.8),
    z: 0.42,
    shadow: true,
  });

  // Schluss: Schutz-Schild bleibt stehen
  L.push({
    key: 'shield',
    tex: 'shield',
    w: 440,
    h: 440,
    from: [540, 656, 1],
    to: [540, 980, 1.62],
    p: pop(t, 29.0, 1e9, 0.65),
    z: 0.45,
    shadow: true,
  });
  return L;
};

/** Ring aus Shorts-Karten, der ums Handy kreist ("the cycle"). mode 'fade' = verschwindet, 'break' = zerbricht. */
const Orbit: React.FC<{ t: number; a: number; b: number; mode: 'fade' | 'break'; tex: Record<string, THREE.Texture> }> = ({ t, a, b, mode, tex }) => {
  if (t < a || t > b + 0.7) return null;
  const N = 8;
  const tau = t - Math.min(t, b);
  const run = Math.min(t, b) - a;
  const theta = 1.3 * run + 1.1 * run * run + (mode === 'fade' ? 3 * tau : 0);
  const appear = springy(ramp(t, a, a + 0.55));
  const H = 0.6;
  const k = H / 712;
  const R0 = lerp(0.55, 1.02, appear);
  const out = mode === 'fade' ? easeIO(ramp(t, b, b + 0.35)) : 0;
  const rnd = (i: number, j: number) => {
    const v = Math.sin(i * 91.7 + j * 47.3) * 43758.5453;
    return v - Math.floor(v);
  };
  return (
    <group position={[0, 0.08, 0]} rotation={[0.18, 0, -0.08]}>
      {Array.from({ length: N }).map((_, i) => {
        const ang = theta + (i * Math.PI * 2) / N;
        let R = R0 + out * 0.5;
        let y = 0;
        let rx = 0;
        let rz = 0;
        let op = clamp01(appear * 1.5) * (1 - out);
        let sc = k * appear;
        if (mode === 'break' && tau > 0) {
          const d = tau;
          R += (1.6 + rnd(i, 1) * 1.6) * d;
          y += (rnd(i, 2) - 0.5) * 2.4 * d;
          rx = (rnd(i, 3) - 0.5) * 9 * d;
          rz = (rnd(i, 4) - 0.5) * 9 * d;
          op *= 1 - easeIO(ramp(d, 0.1, 0.6));
          sc *= 1 - 0.3 * d;
        }
        return (
          <group key={i} position={[Math.sin(ang) * R, y, Math.cos(ang) * R]} rotation={[rx, ang, rz]} scale={[sc, sc, sc]}>
            <TexPlane tex={tex['vcard' + (i % 3)]} w={400} h={712} op={op} order={15} />
          </group>
        );
      })}
    </group>
  );
};

const Scene: React.FC<{ t: number; a: Assets }> = ({ t, a }) => {
  const pose = poseAt(t);
  const pops = popsAt(t, a.tex);
  const tex = a.tex;

  // "no endless swiping": Short-Karten laufen endlos nach oben, dann Stopp-Schild
  const conv = win(t, 22.3, 23.25, 0.15, 0.3);
  const runT = Math.min(t, 22.86) - 22.3 + 0.12 * easeOut(ramp(t, 22.86, 23.02));
  const stopP = pop(t, 22.86, 23.45, 0.5, 0.3);
  const glowP = pop(t, 29.0, 1e9, 0.65);

  return (
    <>
      <ambientLight intensity={0.55} />
      <directionalLight position={[-3, 5, 6]} intensity={2.2} />
      <directionalLight position={[4, -2, 3]} intensity={0.6} color="#ffe9cc" />
      <group position={[pose.x, pose.y, 0]} rotation={[pose.rx, pose.ry, pose.rz]}>
        <Phone t={t} a={a} pops={pops} ry={pose.ry}>
          {pops.map((s) => (
            <Pop key={s.key} s={s} tex={tex} />
          ))}
          {conv > 0 ? (
            <group position={[sx(540), sy(1000), SURF + 0.5]} scale={[UPX * 1.1, UPX * 1.1, UPX * 1.1]}>
              {[0, 1, 2, 3].map((i) => {
                const y = 760 - runT * 1700 + i * 760;
                const vis = clamp01(1.6 - Math.abs(y) / 560) * conv;
                return <TexPlane key={i} tex={tex['vcard' + (i % 3)]} w={400} h={712} op={vis} order={15} position={[0, -y, 0]} />;
              })}
            </group>
          ) : null}
          {stopP > 0 ? (
            <group position={[sx(540), sy(1050), SURF + 0.62]} scale={[UPX * stopP * 1.5, UPX * stopP * 1.5, UPX]}>
              <TexPlane tex={tex.stop} w={300} h={300} op={clamp01(stopP * 2)} order={16} />
            </group>
          ) : null}
          {glowP > 0 ? <Glow t={t} p={glowP} /> : null}
          <Orbit t={t} a={0.0} b={2.55} mode="fade" tex={tex} />
          <Orbit t={t} a={9.5} b={10.35} mode="fade" tex={tex} />
          <Orbit t={t} a={26.9} b={27.72} mode="break" tex={tex} />
        </Phone>
      </group>
    </>
  );
};

/** grüner Schein hinter dem Schild */
const Glow: React.FC<{ t: number; p: number }> = ({ p }) => {
  const tex = useMemo(() => {
    const c = document.createElement('canvas');
    c.width = c.height = 256;
    const g = c.getContext('2d')!;
    const r = g.createRadialGradient(128, 128, 10, 128, 128, 128);
    r.addColorStop(0, 'rgba(60,230,140,0.9)');
    r.addColorStop(0.45, 'rgba(40,200,120,0.35)');
    r.addColorStop(1, 'rgba(40,200,120,0)');
    g.fillStyle = r;
    g.fillRect(0, 0, 256, 256);
    const tx = new THREE.CanvasTexture(c);
    tx.colorSpace = THREE.SRGBColorSpace;
    return tx;
  }, []);
  const q = popPos({ key: 'g', tex: 'shield', w: 440, h: 440, from: [540, 656, 1], to: [540, 980, 1.62], p, z: 0.45 });
  return (
    <group position={[sx(q.x), sy(q.y), q.z - 0.01]} scale={[UPX * q.s, UPX * q.s, UPX]}>
      <TexPlane tex={tex} w={900} h={900} op={clamp01(p) * 0.85} order={9} additive />
    </group>
  );
};

// ------------------------------------------------------------------ Text (oben links)
type Word = [string, number];
type Line = { size: number; words: Word[]; hl?: number; weight?: number };
type Block = { a: number; b: number; lines: Line[] };

const S = 68;
const BLOCKS: Block[] = [
  { a: 0, b: 2.6, lines: [{ size: 138, weight: 900, words: [['BREAK', 0], ['THE', 0.28]] }, { size: 176, weight: 900, words: [['CYCLE,', 0.48]] }, { size: 62, words: [['or', 1.14], ['it', 1.4], ['will', 1.52], ['repeat.', 1.8]] }] },
  { a: 2.78, b: 4.6, lines: [{ size: S, words: [['You', 2.8], ['open', 2.9], ['YouTube', 3.12]] }, { size: S, words: [['for', 3.46], ['one', 3.78], ['video.', 4.08]] }] },
  { a: 4.8, b: 6.62, lines: [{ size: S, words: [['One', 4.82], ['Short.', 5.06]] }, { size: S, words: [['Then', 5.8], ['another.', 6.02]] }] },
  { a: 6.84, b: 8.58, lines: [{ size: 86, words: [['Three', 6.86], ['hours.', 7.18]] }, { size: 170, weight: 900, words: [['Gone.', 8.06]] }] },
  { a: 8.8, b: 10.24, lines: [{ size: S, words: [['Tomorrow?', 8.82]] }, { size: S, words: [['Same', 9.58], ['thing.', 9.82]] }] },
  { a: 10.44, b: 12.36, lines: [{ size: S, words: [['Deleting', 10.46], ['YouTube', 10.74]] }, { size: S, words: [["won't", 11.22], ['fix', 11.66], ['it.', 11.9]] }] },
  { a: 12.56, b: 13.6, lines: [{ size: S, words: [["You're", 12.58], ['back', 12.7]] }, { size: S, words: [['in', 12.88], ['three', 13.04], ['days,', 13.26]] }] },
  {
    a: 13.8,
    b: 16.66,
    lines: [
      { size: S, words: [['because', 13.82], ['everything', 14.02]] },
      { size: S, words: [['you', 14.44], ['actually', 14.68], ['learn', 15.12], ['from', 15.46]] },
      { size: S, words: [['is', 15.76], ['on', 16.02], ['it.', 16.3]] },
    ],
  },
  { a: 16.86, b: 19.22, lines: [{ size: S, words: [['So', 16.88], ['delete', 17.04], ['the', 17.3]] }, { size: 160, weight: 900, words: [['SHORTS,', 17.46]] }, { size: 62, words: [['not', 18.3], ['YouTube.', 18.5]] }] },
  { a: 19.42, b: 20.92, lines: [{ size: 86, words: [["That's", 19.44], ['F*ck', 19.84], ['Shorts.', 20.18]] }] },
  { a: 21.12, b: 23.6, lines: [{ size: S, words: [['No', 21.14], ['Shorts', 21.34], ['button.', 21.6]] }, { size: S, words: [['No', 22.32], ['endless', 22.48], ['swiping.', 22.86]] }] },
  { a: 23.8, b: 25.02, lines: [{ size: S, words: [['Tap', 23.82], ['a', 24.02], ['Short', 24.18]] }, { size: S, words: [['by', 24.3], ['accident,', 24.48]] }] },
  { a: 25.22, b: 26.94, lines: [{ size: S, words: [['it', 25.24], ['closes', 25.38]] }, { size: S, words: [['before', 25.78], ['it', 26.16], ['hooks', 26.34], ['you.', 26.5]] }] },
  { a: 27.14, b: 27.92, lines: [{ size: 150, weight: 900, words: [['BREAK', 27.16], ['THE', 27.42]] }, { size: 190, weight: 900, words: [['CYCLE.', 27.6]] }] },
];

const INK = '#1C1814';
const GREY = [168, 158, 145];
const DARK = [28, 24, 20];
const mixCol = (x: number) => `rgb(${GREY.map((g, i) => Math.round(lerp(g, DARK[i], x))).join(',')})`;

const WordSpan: React.FC<{ w: string; at: number; t: number; out: number; last: boolean }> = ({ w, at, t, out, last }) => {
  const inP = easeOut(ramp(t, at - 0.04, at + 0.3));
  const col = ramp(t, at + 0.05, at + 0.45);
  const y = (1 - inP) * 150 - out * 150;
  return (
    <span style={{ display: 'inline-block', transform: `translateY(${y}%)`, color: mixCol(col), filter: `blur(${(1 - inP) * 6}px)`, marginRight: last ? 0 : '0.24em' }}>
      {w}
    </span>
  );
};

const TextBlock: React.FC<{ b: Block; t: number }> = ({ b, t }) => {
  if (t < b.a - 0.1 || t > b.b + 0.5) return null;
  return (
    <div style={{ position: 'absolute', left: 76, top: 292 }}>
      {b.lines.map((ln, li) => {
        const out = easeIO(ramp(t, b.b + li * 0.03, b.b + 0.2 + li * 0.03));
        const hl = ln.hl !== undefined ? easeOut(ramp(t, ln.hl, ln.hl + 0.4)) * (1 - out) : 0;
        return (
          <div key={li} style={{ position: 'relative', overflow: 'hidden', paddingBottom: ln.size * 0.12, marginBottom: -ln.size * 0.1 }}>
            {hl > 0 ? (
              <div
                style={{
                  position: 'absolute',
                  left: -10,
                  top: ln.size * 0.22,
                  height: ln.size * 0.92,
                  width: '100%',
                  background: '#F5C451',
                  transform: `scaleX(${hl})`,
                  transformOrigin: 'left',
                  borderRadius: 6,
                }}
              />
            ) : null}
            <div
              style={{
                position: 'relative',
                display: 'inline-block',
                fontFamily: 'Inter',
                fontWeight: ln.weight ?? 800,
                fontSize: ln.size,
                lineHeight: 1.08,
                letterSpacing: '-0.04em',
                whiteSpace: 'nowrap',
                color: INK,
                paddingRight: hl > 0 ? 14 : 0,
              }}
            >
              {ln.words.map(([w, at], wi) => (
                <WordSpan key={wi} w={w} at={at} t={t} out={out} last={wi === ln.words.length - 1} />
              ))}
            </div>
          </div>
        );
      })}
    </div>
  );
};

const AppIcon: React.FC<{ size: number }> = ({ size }) => (
  <svg width={size} height={size} viewBox="0 0 100 100">
    <rect width="100" height="100" rx="24" fill="#E5172B" />
    <rect x="31" y="20" width="38" height="60" rx="9" fill="#fff" />
    <line x1="27" y1="83" x2="73" y2="18" stroke="#E5172B" strokeWidth="13" strokeLinecap="round" />
    <line x1="27" y1="83" x2="73" y2="18" stroke="#fff" strokeWidth="5.5" strokeLinecap="round" />
  </svg>
);

const NAME = 'F*ck Shorts.';
const Ending: React.FC<{ t: number }> = ({ t }) => {
  if (t < 27.95) return null;
  const iconP = springy(ramp(t, 27.98, 28.45));
  const typed = Math.floor(clamp01((t - 28.0) / 0.4) * NAME.length + 0.001);
  const signP = springy(ramp(t, 28.1, 28.6));
  const linkP = springy(ramp(t, 29.08, 29.6));
  const hl = easeOut(ramp(t, 29.2, 29.6));
  const freeP = easeOut(ramp(t, 30.18, 30.55));
  const comP = easeOut(ramp(t, 31.4, 31.75));
  // "but most important: sign up. Link in my bio." -> SIGN UP und LINK IN BIO pochen
  const pulse = (at: number) => 1 + 0.07 * Math.sin(Math.PI * ramp(t, at, at + 0.35));
  const signPulse = pulse(33.74) * pulse(28.78);
  const linkPulse = pulse(34.5);
  return (
    <div style={{ position: 'absolute', left: 70, top: 236, whiteSpace: 'nowrap' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 18 }}>
        <div style={{ transform: `scale(${iconP})` }}>
          <AppIcon size={72} />
        </div>
        <div style={{ fontFamily: 'Inter', fontWeight: 900, fontSize: 70, letterSpacing: '-0.045em', color: INK }}>
          {NAME.slice(0, typed)}
          <span style={{ display: 'inline-block', width: 5, height: 62, marginLeft: 6, background: '#E5172B', verticalAlign: '-8px', opacity: t < 28.6 || Math.floor((t - 28.6) / 0.45) % 2 === 0 ? 1 : 0 }} />
        </div>
      </div>
      <div style={{ marginTop: 4, transform: `scale(${signP * signPulse})`, transformOrigin: 'left center', fontFamily: 'Inter', fontWeight: 900, fontSize: 218, lineHeight: 1.0, letterSpacing: '-0.055em', color: INK }}>
        SIGN UP
      </div>
      <div style={{ position: 'relative', display: 'inline-block', marginTop: 6, transform: `scale(${linkP * linkPulse})`, transformOrigin: 'left center' }}>
        <div style={{ position: 'absolute', left: -10, right: -14, top: 26, bottom: 4, background: '#F5C451', transform: `scaleX(${hl})`, transformOrigin: 'left', borderRadius: 8 }} />
        <div style={{ position: 'relative', fontFamily: 'Inter', fontWeight: 900, fontSize: 118, lineHeight: 1.05, letterSpacing: '-0.045em', color: INK }}>
          <span style={{ fontSize: 104 }}>👉</span> LINK IN BIO
        </div>
      </div>
      <div style={{ marginTop: 14, fontFamily: 'Instrument Serif', fontStyle: 'italic', fontSize: 58, color: '#4A4239', opacity: freeP, transform: `translateY(${(1 - freeP) * 18}px)` }}>
        Free for testers right now.
      </div>
      <div style={{ marginTop: 4, fontFamily: 'Inter', fontWeight: 700, fontSize: 38, letterSpacing: '-0.02em', color: '#8A8076', opacity: comP, transform: `translateY(${(1 - comP) * 14}px)` }}>
        (and comment “tester”)
      </div>
    </div>
  );
};

// ------------------------------------------------------------------ Gesamt
export const Ad14: React.FC<{ voice?: boolean }> = ({ voice = true }) => {
  const frame = useCurrentFrame();
  const t = frame / FPS14;
  const a = useAssets();
  return (
    <AbsoluteFill style={{ background: '#E7DCCA', overflow: 'hidden' }}>
      <AbsoluteFill style={{ background: 'radial-gradient(ellipse 90% 70% at 50% 42%, #F3EBDD 0%, #E9DFCD 55%, #D9CBB3 100%)' }} />
      <AbsoluteFill style={{ backgroundImage: `url(${staticFile('ad13/grain.png')})`, backgroundSize: '256px 256px', opacity: 0.9 }} />
      {a ? (
        <ThreeCanvas width={VW} height={VH} camera={{ position: [0, 0, 9], fov: 21 }} gl={{ antialias: false, alpha: true }}>
          <Scene t={t} a={a} />
        </ThreeCanvas>
      ) : null}
      {BLOCKS.map((b, i) => (
        <TextBlock key={i} b={b} t={t} />
      ))}
      <Ending t={t} />
      {voice ? <Audio src={staticFile('voice14.wav')} /> : null}
      <Audio src={staticFile('music14.wav')} volume={0.9} />
      <Audio src={staticFile('sfx14.wav')} volume={0.9} />
    </AbsoluteFill>
  );
};
