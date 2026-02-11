// --- Shared geometry types ---

export interface Point3D {
  x: number;
  y: number;
  z: number;
}

export interface ViewerPoint {
  x: number;
  y: number;
}

export type DrawingMode = 'SURFACE' | 'AIR';

export interface ViewerStroke {
  id: string;
  points: ViewerPoint[];
  color: string; // CSS rgba() string (converted from ARGB Int)
  strokeWidth: number;
  isComplete: boolean;
}

// --- kotlinx-serialization wire format ---
// The Android app uses sealed class polymorphism with FQ class name as discriminator

const STROKE_EVENT_PREFIX = 'com.sb.arsketch.domain.model.StrokeEvent';

export const STROKE_EVENT_TYPES = {
  [`${STROKE_EVENT_PREFIX}.Started`]: 'Started',
  [`${STROKE_EVENT_PREFIX}.PointAdded`]: 'PointAdded',
  [`${STROKE_EVENT_PREFIX}.Ended`]: 'Ended',
  [`${STROKE_EVENT_PREFIX}.Deleted`]: 'Deleted',
  [`${STROKE_EVENT_PREFIX}.Restored`]: 'Restored',
  [`${STROKE_EVENT_PREFIX}.AllCleared`]: 'AllCleared',
} as const;

export type FQEventType = keyof typeof STROKE_EVENT_TYPES;
export type SimpleEventType = (typeof STROKE_EVENT_TYPES)[FQEventType];

// --- Raw JSON event interfaces (as received from DataChannel) ---

interface RawStarted {
  type: `${typeof STROKE_EVENT_PREFIX}.Started`;
  strokeId: string;
  startPoint: Point3D;
  color: number; // ARGB Int (signed 32-bit, e.g. -65536 for red)
  thickness: number;
  mode: DrawingMode;
  timestamp: number;
}

interface RawPointAdded {
  type: `${typeof STROKE_EVENT_PREFIX}.PointAdded`;
  strokeId: string;
  point: Point3D;
  timestamp: number;
}

interface RawEnded {
  type: `${typeof STROKE_EVENT_PREFIX}.Ended`;
  strokeId: string;
  timestamp: number;
}

interface RawDeleted {
  type: `${typeof STROKE_EVENT_PREFIX}.Deleted`;
  strokeId: string;
  timestamp: number;
}

interface RawRestored {
  type: `${typeof STROKE_EVENT_PREFIX}.Restored`;
  strokeId: string;
  timestamp: number;
}

interface RawAllCleared {
  type: `${typeof STROKE_EVENT_PREFIX}.AllCleared`;
  timestamp: number;
}

export type RawStrokeEvent =
  | RawStarted
  | RawPointAdded
  | RawEnded
  | RawDeleted
  | RawRestored
  | RawAllCleared;

// --- Parsed StrokeEvent (TypeScript discriminated union) ---

export type StrokeEvent =
  | { kind: 'Started'; strokeId: string; startPoint: Point3D; color: number; thickness: number; mode: DrawingMode; timestamp: number }
  | { kind: 'PointAdded'; strokeId: string; point: Point3D; timestamp: number }
  | { kind: 'Ended'; strokeId: string; timestamp: number }
  | { kind: 'Deleted'; strokeId: string; timestamp: number }
  | { kind: 'Restored'; strokeId: string; timestamp: number }
  | { kind: 'AllCleared'; timestamp: number };
