import React from 'react';
import { Composition } from 'remotion';
import { Ad, DURATION_S, FPS } from './Ad';
import { Ad2, DURATION2_S, FPS2 } from './Ad2';
import { Ad3, DURATION3_S, FPS3 } from './Ad3';
import { Ad4, DURATION4_S, FPS4 } from './Ad4';
import { Ad5, DURATION5_S, FPS5 } from './Ad5';
import { Ad6, DURATION6_S, FPS6 } from './Ad6';
import { DURATION6F_S } from './map6';
import { DURATION6FM_S } from './map6M';
import { Ad7, DURATION7_S, FPS7 } from './Ad7';
import { Ad8, DURATION8_S, FPS8 } from './Ad8';
import { Ad9, DURATION9_S, FPS9 } from './Ad9';
import { DURATION6V_S } from './map6v';
import { DURATION6V3_S } from './map6v3';
import { DURATION6V4_S } from './map6v4';

export const Root: React.FC = () => (
  <>
    {/* Mit Stimme */}
    <Composition id="Ad" component={Ad} durationInFrames={Math.round(DURATION_S * FPS)} fps={FPS} width={1080} height={1920} defaultProps={{ withVoice: true }} />
    {/* Ohne Ton (Stimme wird separat angelegt / Musik in der App dazu) */}
    <Composition id="AdSilent" component={Ad} durationInFrames={Math.round(DURATION_S * FPS)} fps={FPS} width={1080} height={1920} defaultProps={{ withVoice: false }} />
    {/* Video 2: schnell, Stimme + Effekte (Silent = ohne Ton, Ton wird separat gemischt) */}
    <Composition id="Ad2Silent" component={Ad2} durationInFrames={Math.round(DURATION2_S * FPS2)} fps={FPS2} width={1080} height={1920} defaultProps={{ voice: false, sfx: false }} />
    {/* Video 3: ohne Ton (Sound kommt auf TikTok dazu); Ad3Preview nur zum Anschauen, NIE hochladen */}
    <Composition id="Ad3" component={Ad3} durationInFrames={Math.round(DURATION3_S * FPS3)} fps={FPS3} width={1080} height={1920} defaultProps={{ refSound: false }} />
    {/* Video 4: quer 16:9, schwarz, eigene Stimme */}
    <Composition id="Ad4" component={Ad4} durationInFrames={Math.round(DURATION4_S * FPS4)} fps={FPS4} width={1920} height={1080} defaultProps={{ voice: true }} />
    {/* Video 4b: kürzerer Swipe-Teil, YouTube behalten, mehr Dopamin (Zoom, Blitze, Effekte) */}
    <Composition id="Ad5" component={Ad5} durationInFrames={Math.round(DURATION5_S * FPS5)} fps={FPS5} width={1920} height={1080} defaultProps={{ voice: true }} />
    {/* Video 6: "Stop giving a sh*t" – Anfang wie das Original, dann F*ck Shorts */}
    <Composition id="Ad6" component={Ad6} durationInFrames={Math.round(DURATION6_S * FPS6)} fps={FPS6} width={1920} height={1080} defaultProps={{ voice: true }} />
    {/* Video 6 schnell: Pausen raus, Tempo x1.5, harte Schnitte = halbe Länge */}
    <Composition id="Ad6Fast" component={Ad6} durationInFrames={Math.round(DURATION6F_S * FPS6)} fps={FPS6} width={1920} height={1080} defaultProps={{ voice: true, fast: true }} />
    {/* Video 6 mittel: Pausen 0.3 s, Tempo x1.3 = ~1.5x kürzer */}
    <Composition id="Ad6Mid" component={Ad6} durationInFrames={Math.round(DURATION6FM_S * FPS6)} fps={FPS6} width={1920} height={1080} defaultProps={{ voice: true, mid: true }} />
    {/* Video 6 Fassung 2: mehr Schimpfwörter, Schluss "Comment TEST + Link in Bio", Tempo wie Mittel */}
    <Composition id="Ad6V2" component={Ad6} durationInFrames={Math.round(DURATION6V_S * FPS6)} fps={FPS6} width={1920} height={1080} defaultProps={{ voice: true, v2: true }} />
    {/* Video 6 Fassung 3: wie Fassung 2, aber wütende Stimme und stärkeres Wackeln */}
    <Composition id="Ad6V3" component={Ad6} durationInFrames={Math.round(DURATION6V3_S * FPS6)} fps={FPS6} width={1920} height={1080} defaultProps={{ voice: true, v3: true }} />
    {/* Video 6 Fassung 4: wie 3, Schluss ohne "Do it now", dafür Google Play / 12 Tester / 14 Tage im Bild */}
    <Composition id="Ad6V4" component={Ad6} durationInFrames={Math.round(DURATION6V4_S * FPS6)} fps={FPS6} width={1920} height={1080} defaultProps={{ voice: true, v4: true }} />
    {/* Video 7: bunter Zeichenstil "One day ... the last time"; Ad7 = eigene Musik (hochladen), Ad7Orig = Musik der Vorlage (NUR ANSCHAUEN) */}
    <Composition id="Ad7" component={Ad7} durationInFrames={Math.round(DURATION7_S * FPS7)} fps={FPS7} width={1920} height={1080} defaultProps={{ music: 'own' as const }} />
    <Composition id="Ad7Orig" component={Ad7} durationInFrames={Math.round(DURATION7_S * FPS7)} fps={FPS7} width={1920} height={1080} defaultProps={{ music: 'orig' as const }} />
    {/* Video 8: "8 YEARS" hochkant, ohne Stimme, Rechnung 3 h/Tag von 13 bis 80 */}
    <Composition id="Ad8" component={Ad8} durationInFrames={Math.round(DURATION8_S * FPS8)} fps={FPS8} width={1080} height={1920} defaultProps={{ sound: true }} />
    {/* Video 9: "Lock the f*ck in" – Anfang wie die Vorlage, dann F*ck Shorts (quer) */}
    <Composition id="Ad9" component={Ad9} durationInFrames={Math.round(DURATION9_S * FPS9)} fps={FPS9} width={1920} height={1080} defaultProps={{ voice: true }} />
  </>
);
