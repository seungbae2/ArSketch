import type { ViewerStroke } from './types';

/**
 * Port of Android StrokeOverlay.
 *
 * Renders ViewerStroke[] onto an HTML Canvas using the same
 * coordinate transform as the Compose Canvas:
 *   screenX = centerX + point.x * scale
 *   screenY = centerY - point.y * scale
 *
 * When a video element is provided, the stroke coordinate system
 * is aligned to the video's visible area (accounting for object-fit: contain
 * letterboxing) so strokes overlay the video content correctly.
 */
export class StrokeRenderer {
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private videoElement: HTMLVideoElement | null = null;
  private centerX = 0;
  private centerY = 0;
  private scale = 0;
  private cssWidth = 0;
  private cssHeight = 0;
  private animFrameId: number | null = null;

  constructor(canvas: HTMLCanvasElement) {
    this.canvas = canvas;
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('Canvas 2D context not available');
    this.ctx = ctx;
  }

  setVideoElement(video: HTMLVideoElement): void {
    this.videoElement = video;
  }

  /**
   * Resize canvas to match container CSS pixel dimensions.
   * Handles HiDPI by scaling the backing store.
   */
  resize(cssWidth: number, cssHeight: number): void {
    this.cssWidth = cssWidth;
    this.cssHeight = cssHeight;

    const dpr = window.devicePixelRatio || 1;
    this.canvas.width = cssWidth * dpr;
    this.canvas.height = cssHeight * dpr;
    this.canvas.style.width = `${cssWidth}px`;
    this.canvas.style.height = `${cssHeight}px`;

    // Reset transform (setting canvas.width resets it) then apply DPR scale
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    this.recalculateCoordinates();
  }

  /** Render all strokes onto the canvas. */
  render(strokes: ViewerStroke[]): void {
    // Recalculate each frame in case video dimensions changed
    this.recalculateCoordinates();

    this.ctx.clearRect(0, 0, this.cssWidth, this.cssHeight);

    for (const stroke of strokes) {
      if (stroke.points.length < 2) continue;

      this.ctx.beginPath();
      this.ctx.strokeStyle = stroke.color;
      this.ctx.lineWidth = stroke.strokeWidth;
      this.ctx.lineCap = 'round';
      this.ctx.lineJoin = 'round';

      const first = stroke.points[0];
      this.ctx.moveTo(
        this.centerX + first.x * this.scale,
        this.centerY - first.y * this.scale,
      );

      for (let i = 1; i < stroke.points.length; i++) {
        const pt = stroke.points[i];
        this.ctx.lineTo(
          this.centerX + pt.x * this.scale,
          this.centerY - pt.y * this.scale,
        );
      }

      this.ctx.stroke();
    }
  }

  /**
   * Start a requestAnimationFrame loop that re-renders
   * only when the stroke data has changed.
   */
  startRenderLoop(getStrokes: () => ViewerStroke[], isDirty: () => boolean, clearDirty: () => void): void {
    const loop = () => {
      if (isDirty()) {
        this.render(getStrokes());
        clearDirty();
      }
      this.animFrameId = requestAnimationFrame(loop);
    };
    this.animFrameId = requestAnimationFrame(loop);
  }

  stopRenderLoop(): void {
    if (this.animFrameId !== null) {
      cancelAnimationFrame(this.animFrameId);
      this.animFrameId = null;
    }
  }

  /**
   * Calculate centerX, centerY, scale to align with the video's
   * visible area when using object-fit: contain (letterboxing).
   *
   * If no video or video has no intrinsic dimensions yet,
   * falls back to using the full canvas dimensions.
   */
  private recalculateCoordinates(): void {
    const video = this.videoElement;

    if (video && video.videoWidth > 0 && video.videoHeight > 0) {
      const videoRatio = video.videoWidth / video.videoHeight;
      const containerRatio = this.cssWidth / this.cssHeight;

      let renderWidth: number;
      let renderHeight: number;
      let offsetX: number;
      let offsetY: number;

      if (videoRatio > containerRatio) {
        // Video wider than container → bars on top/bottom
        renderWidth = this.cssWidth;
        renderHeight = this.cssWidth / videoRatio;
        offsetX = 0;
        offsetY = (this.cssHeight - renderHeight) / 2;
      } else {
        // Video taller than container → bars on left/right
        renderHeight = this.cssHeight;
        renderWidth = this.cssHeight * videoRatio;
        offsetX = (this.cssWidth - renderWidth) / 2;
        offsetY = 0;
      }

      this.centerX = offsetX + renderWidth / 2;
      this.centerY = offsetY + renderHeight / 2;
      this.scale = Math.min(renderWidth, renderHeight);
    } else {
      // Fallback: no video dimensions available yet
      this.centerX = this.cssWidth / 2;
      this.centerY = this.cssHeight / 2;
      this.scale = Math.min(this.cssWidth, this.cssHeight);
    }
  }
}
