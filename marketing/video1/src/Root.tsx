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
  </>
);
