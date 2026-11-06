import React from 'react';
import { Composition } from 'remotion';
import { Ad, DURATION_S, FPS } from './Ad';

export const Root: React.FC = () => (
  <>
    {/* Mit Stimme */}
    <Composition id="Ad" component={Ad} durationInFrames={Math.round(DURATION_S * FPS)} fps={FPS} width={1080} height={1920} defaultProps={{ withVoice: true }} />
    {/* Ohne Ton (Stimme wird separat angelegt / Musik in der App dazu) */}
    <Composition id="AdSilent" component={Ad} durationInFrames={Math.round(DURATION_S * FPS)} fps={FPS} width={1080} height={1920} defaultProps={{ withVoice: false }} />
  </>
);
