import { type RemoteTouchEvent, REMOTE_TOUCH_PREFIX } from './remote-touch';

const REMOTE_TOUCH_TOPIC = 'remote_touch';
const THROTTLE_MS = 16;

/**
 * Web 뷰어의 드로잉 입력 핸들러.
 * 마우스/터치 이벤트를 정규화된 좌표(0-1)로 변환하여 DataChannel로 전송.
 */
export class DrawingInput {
  private overlay: HTMLElement;
  private videoElement: HTMLVideoElement;
  private senderId: string;
  private isDrawing = false;
  private lastSendTime = 0;

  private color = -16742145;   // Blue (0xFF0088FF.toInt())
  private thickness = 0.006;   // MEDIUM
  private mode: 'SURFACE' | 'AIR' = 'AIR';

  private publishFn: ((data: Uint8Array, topic: string) => void) | null = null;

  constructor(overlay: HTMLElement, videoElement: HTMLVideoElement) {
    this.overlay = overlay;
    this.videoElement = videoElement;
    this.senderId = crypto.randomUUID();
    this.setupEventListeners();
  }

  setPublishFunction(fn: (data: Uint8Array, topic: string) => void): void {
    this.publishFn = fn;
  }

  clearPublishFunction(): void {
    this.publishFn = null;
  }

  setColor(color: number): void {
    this.color = color;
  }

  setThickness(thickness: number): void {
    this.thickness = thickness;
  }

  setMode(mode: 'SURFACE' | 'AIR'): void {
    this.mode = mode;
  }

  private setupEventListeners(): void {
    this.overlay.addEventListener('mousedown', (e) => this.onPointerDown(e.clientX, e.clientY));
    this.overlay.addEventListener('mousemove', (e) => this.onPointerMove(e.clientX, e.clientY));
    this.overlay.addEventListener('mouseup', () => this.onPointerUp());
    this.overlay.addEventListener('mouseleave', () => this.onPointerUp());

    this.overlay.addEventListener('touchstart', (e) => {
      e.preventDefault();
      const t = e.touches[0];
      this.onPointerDown(t.clientX, t.clientY);
    }, { passive: false });
    this.overlay.addEventListener('touchmove', (e) => {
      e.preventDefault();
      const t = e.touches[0];
      this.onPointerMove(t.clientX, t.clientY);
    }, { passive: false });
    this.overlay.addEventListener('touchend', (e) => {
      e.preventDefault();
      this.onPointerUp();
    });
  }

  private onPointerDown(clientX: number, clientY: number): void {
    const norm = this.toNormalized(clientX, clientY);
    if (!norm) return;
    this.isDrawing = true;
    this.send({
      type: `${REMOTE_TOUCH_PREFIX}.TouchDown`,
      normalizedX: norm.x,
      normalizedY: norm.y,
      color: this.color,
      thickness: this.thickness,
      mode: this.mode,
      senderId: this.senderId,
      timestamp: Date.now(),
    });
  }

  private onPointerMove(clientX: number, clientY: number): void {
    if (!this.isDrawing) return;
    const now = Date.now();
    if (now - this.lastSendTime < THROTTLE_MS) return;
    this.lastSendTime = now;
    const norm = this.toNormalized(clientX, clientY);
    if (!norm) return;
    this.send({
      type: `${REMOTE_TOUCH_PREFIX}.TouchMove`,
      normalizedX: norm.x,
      normalizedY: norm.y,
      senderId: this.senderId,
      timestamp: Date.now(),
    });
  }

  private onPointerUp(): void {
    if (!this.isDrawing) return;
    this.isDrawing = false;
    this.send({
      type: `${REMOTE_TOUCH_PREFIX}.TouchUp`,
      senderId: this.senderId,
      timestamp: Date.now(),
    });
  }

  /**
   * clientXY -> 비디오 기준 0-1 좌표 (object-fit:contain 레터박스 보정)
   */
  private toNormalized(clientX: number, clientY: number): { x: number; y: number } | null {
    const video = this.videoElement;
    if (video.videoWidth === 0 || video.videoHeight === 0) return null;

    const rect = this.overlay.getBoundingClientRect();
    const relX = clientX - rect.left;
    const relY = clientY - rect.top;

    const containerW = rect.width;
    const containerH = rect.height;
    const videoRatio = video.videoWidth / video.videoHeight;
    const containerRatio = containerW / containerH;

    let renderW: number, renderH: number, offsetX: number, offsetY: number;
    if (videoRatio > containerRatio) {
      renderW = containerW;
      renderH = containerW / videoRatio;
      offsetX = 0;
      offsetY = (containerH - renderH) / 2;
    } else {
      renderH = containerH;
      renderW = containerH * videoRatio;
      offsetX = (containerW - renderW) / 2;
      offsetY = 0;
    }

    const videoX = relX - offsetX;
    const videoY = relY - offsetY;
    if (videoX < 0 || videoX > renderW || videoY < 0 || videoY > renderH) return null;

    return { x: videoX / renderW, y: videoY / renderH };
  }

  private send(event: RemoteTouchEvent): void {
    if (!this.publishFn) return;
    const json = JSON.stringify(event);
    const data = new TextEncoder().encode(json);
    this.publishFn(data, REMOTE_TOUCH_TOPIC);
  }
}
