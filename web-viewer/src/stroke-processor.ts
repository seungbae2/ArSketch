import {
  STROKE_EVENT_TYPES,
  type FQEventType,
  type RawStrokeEvent,
  type StrokeEvent,
  type ViewerPoint,
  type ViewerStroke,
} from './types';
import { argbIntToCss } from './color-utils';

const THICKNESS_SCALE = 500;

interface MutableViewerStroke {
  id: string;
  points: ViewerPoint[];
  color: string;
  strokeWidth: number;
}

/**
 * Port of Android StrokeEventReceiver.
 *
 * Receives raw DataChannel bytes, deserializes JSON,
 * manages stroke state (active / completed / deleted),
 * and produces ViewerStroke[] snapshots.
 */
export class StrokeProcessor {
  private activeStrokes = new Map<string, MutableViewerStroke>();
  private completedStrokes: ViewerStroke[] = [];
  private deletedStrokes = new Map<string, ViewerStroke>();
  private dirty = false;

  onChange: (() => void) | null = null;

  /** Accept raw bytes from LiveKit DataChannel. */
  onDataReceived(data: Uint8Array): void {
    try {
      const jsonString = new TextDecoder().decode(data);
      const raw: RawStrokeEvent = JSON.parse(jsonString);
      const simpleType = STROKE_EVENT_TYPES[raw.type as FQEventType];
      if (!simpleType) {
        console.warn('Unknown StrokeEvent type:', raw.type);
        return;
      }
      const event = this.toStrokeEvent(raw, simpleType);
      this.processEvent(event);
    } catch (e) {
      console.error('Failed to deserialize StrokeEvent:', e);
    }
  }

  getSnapshot(): ViewerStroke[] {
    const activeList: ViewerStroke[] = [];
    for (const active of this.activeStrokes.values()) {
      activeList.push({
        id: active.id,
        points: [...active.points],
        color: active.color,
        strokeWidth: active.strokeWidth,
        isComplete: false,
      });
    }
    return [...this.completedStrokes, ...activeList];
  }

  isDirty(): boolean {
    return this.dirty;
  }

  clearDirty(): void {
    this.dirty = false;
  }

  clear(): void {
    this.activeStrokes.clear();
    this.completedStrokes = [];
    this.deletedStrokes.clear();
    this.dirty = true;
    this.onChange?.();
  }

  // --- Private ---

  private processEvent(event: StrokeEvent): void {
    switch (event.kind) {
      case 'Started': {
        const point: ViewerPoint = { x: event.startPoint.x, y: event.startPoint.y };
        this.activeStrokes.set(event.strokeId, {
          id: event.strokeId,
          points: [point],
          color: argbIntToCss(event.color),
          strokeWidth: event.thickness * THICKNESS_SCALE,
        });
        break;
      }
      case 'PointAdded': {
        const active = this.activeStrokes.get(event.strokeId);
        if (active) {
          active.points.push({ x: event.point.x, y: event.point.y });
        }
        break;
      }
      case 'Ended': {
        const active = this.activeStrokes.get(event.strokeId);
        if (active) {
          this.activeStrokes.delete(event.strokeId);
          this.completedStrokes.push({
            id: active.id,
            points: [...active.points],
            color: active.color,
            strokeWidth: active.strokeWidth,
            isComplete: true,
          });
        }
        break;
      }
      case 'Deleted': {
        // Edge case: match Android behavior exactly.
        // Always remove from activeStrokes (if present).
        this.activeStrokes.delete(event.strokeId);
        // Only store in deletedStrokes if it was in completedStrokes.
        const idx = this.completedStrokes.findIndex((s) => s.id === event.strokeId);
        if (idx !== -1) {
          const [removed] = this.completedStrokes.splice(idx, 1);
          this.deletedStrokes.set(event.strokeId, removed);
        }
        break;
      }
      case 'Restored': {
        const stroke = this.deletedStrokes.get(event.strokeId);
        if (stroke) {
          this.deletedStrokes.delete(event.strokeId);
          this.completedStrokes.push(stroke);
        }
        break;
      }
      case 'AllCleared': {
        this.activeStrokes.clear();
        this.completedStrokes = [];
        this.deletedStrokes.clear();
        break;
      }
    }

    this.dirty = true;
    this.onChange?.();
  }

  private toStrokeEvent(raw: RawStrokeEvent, kind: string): StrokeEvent {
    switch (kind) {
      case 'Started': {
        const r = raw as Extract<RawStrokeEvent, { type: `${string}.Started` }>;
        return { kind: 'Started', strokeId: r.strokeId, startPoint: r.startPoint, color: r.color, thickness: r.thickness, mode: r.mode, timestamp: r.timestamp };
      }
      case 'PointAdded': {
        const r = raw as Extract<RawStrokeEvent, { type: `${string}.PointAdded` }>;
        return { kind: 'PointAdded', strokeId: r.strokeId, point: r.point, timestamp: r.timestamp };
      }
      case 'Ended': {
        const r = raw as Extract<RawStrokeEvent, { type: `${string}.Ended` }>;
        return { kind: 'Ended', strokeId: r.strokeId, timestamp: r.timestamp };
      }
      case 'Deleted': {
        const r = raw as Extract<RawStrokeEvent, { type: `${string}.Deleted` }>;
        return { kind: 'Deleted', strokeId: r.strokeId, timestamp: r.timestamp };
      }
      case 'Restored': {
        const r = raw as Extract<RawStrokeEvent, { type: `${string}.Restored` }>;
        return { kind: 'Restored', strokeId: r.strokeId, timestamp: r.timestamp };
      }
      case 'AllCleared': {
        const r = raw as Extract<RawStrokeEvent, { type: `${string}.AllCleared` }>;
        return { kind: 'AllCleared', timestamp: r.timestamp };
      }
      default:
        throw new Error(`Unknown event kind: ${kind}`);
    }
  }
}
