import React from 'react';
import { Composition } from 'remotion';
import { Ad, DURATION_S, FPS } from './Ad';
import { Ad2, DURATION2_S, FPS2 } from './Ad2';

export const Root: React.FC = () => (
  <>
    {/* Mit Stimme */}
    <Composition id="Ad" component={Ad} durationInFrames={Math.round(DURATION_S * FPS)} fps={FPS} width={1080} height={1920} defaultProps={{ withVoice: true }} />
    {/* Ohne Ton (Stimme wird separat angelegt / Musik in der App dazu) */}
    <Composition id="AdSilent" component={Ad} durationInFrames={Math.round(DURATION_S * FPS)} fps={FPS} width={1080} height={1920} defaultProps={{ withVoice: false }} />
    {/* Video 2: schnell, Stimme + Effekte (Silent = ohne Ton, Ton wird separat gemischt) */}
    <Composition id="Ad2Silent" component={Ad2} durationInFrames={Math.round(DURATION2_S * FPS2)} fps={FPS2} width={1080} height={1920} defaultProps={{ voice: false, sfx: false }} />
  </>
);
