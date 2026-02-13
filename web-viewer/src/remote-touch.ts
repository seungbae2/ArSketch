/**
 * Remote touch event types matching Android's RemoteTouchEvent sealed class.
 * Kotlinx-serialization wire format uses FQ class names as discriminator.
 */

export const REMOTE_TOUCH_PREFIX = 'com.sb.arsketch.domain.model.RemoteTouchEvent';

export interface RemoteTouchDown {
  type: `${typeof REMOTE_TOUCH_PREFIX}.TouchDown`;
  normalizedX: number;
  normalizedY: number;
  color: number;      // ARGB signed Int (e.g., -16776961 for blue)
  thickness: number;   // 0.003, 0.006, 0.012
  mode: 'SURFACE' | 'AIR';
  senderId: string;
  timestamp: number;
}

export interface RemoteTouchMove {
  type: `${typeof REMOTE_TOUCH_PREFIX}.TouchMove`;
  normalizedX: number;
  normalizedY: number;
  senderId: string;
  timestamp: number;
}

export interface RemoteTouchUp {
  type: `${typeof REMOTE_TOUCH_PREFIX}.TouchUp`;
  senderId: string;
  timestamp: number;
}

export type RemoteTouchEvent = RemoteTouchDown | RemoteTouchMove | RemoteTouchUp;
