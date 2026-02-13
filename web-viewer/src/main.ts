import { StrokeProcessor } from './stroke-processor';
import { LiveKitConnection, type ConnectionState } from './livekit-connection';
import { DrawingInput } from './drawing-input';
import {
  showConnectForm,
  hideConnectForm,
  showViewer,
  hideViewer,
  showError,
  hideError,
  setConnectButtonLoading,
  updateStatus,
} from './ui';

// --- DOM References ---
const connectForm = document.getElementById('connect-form') as HTMLFormElement;
const serverUrlInput = document.getElementById('server-url') as HTMLInputElement;
const tokenInput = document.getElementById('token') as HTMLInputElement;
const videoElement = document.getElementById('remote-video') as HTMLVideoElement;
const disconnectBtn = document.getElementById('disconnect-btn') as HTMLButtonElement;

// --- Pre-fill from env (Vite .env.local) ---
if (import.meta.env.VITE_LIVEKIT_URL) {
  serverUrlInput.value = import.meta.env.VITE_LIVEKIT_URL;
}
if (import.meta.env.VITE_LIVEKIT_TOKEN) {
  tokenInput.value = import.meta.env.VITE_LIVEKIT_TOKEN;
}

// --- Core instances ---
// StrokeProcessor: DataChannel 수신은 유지 (향후 활용 가능)
// 캔버스 오버레이 렌더링은 비활성화 — 비디오에 스트로크가 이미 포함됨
const strokeProcessor = new StrokeProcessor();

let participantCount = 0;
let drawingInput: DrawingInput | null = null;

const connection = new LiveKitConnection(videoElement, {
  onStateChange: (state: ConnectionState, error?: string) => {
    updateStatus(state, participantCount);

    if (state === 'disconnected') {
      handleDisconnect();
    }
    if (state === 'error' && error) {
      showError(error);
      setConnectButtonLoading(false);
      showConnectForm();
      hideViewer();
    }
  },
  onParticipantCountChange: (count: number) => {
    participantCount = count;
    updateStatus('connected', count);
  },
  onDataReceived: (payload: Uint8Array) => {
    strokeProcessor.onDataReceived(payload);
  },
});

// --- Connect ---
connectForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  hideError();

  const serverUrl = serverUrlInput.value.trim();
  const token = tokenInput.value.trim();

  if (!serverUrl || !token) {
    showError('Server URL and token are required.');
    return;
  }

  setConnectButtonLoading(true);

  try {
    await connection.connect(serverUrl, token);

    hideConnectForm();
    showViewer();

    // Initialize drawing input
    const drawingOverlay = document.getElementById('drawing-overlay')!;
    drawingInput = new DrawingInput(drawingOverlay, videoElement);
    drawingInput.setPublishFunction((data, topic) => connection.publishData(data, topic));

    // Color picker
    document.querySelectorAll('.color-swatch').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelector('.color-swatch.active')?.classList.remove('active');
        (btn as HTMLElement).classList.add('active');
        drawingInput?.setColor(Number((btn as HTMLElement).dataset.color));
      });
    });

    // Thickness picker
    document.querySelectorAll('.thickness-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelector('.thickness-btn.active')?.classList.remove('active');
        (btn as HTMLElement).classList.add('active');
        drawingInput?.setThickness(Number((btn as HTMLElement).dataset.thickness));
      });
    });

    // Mode toggle
    document.querySelectorAll('.mode-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelector('.mode-btn.active')?.classList.remove('active');
        (btn as HTMLElement).classList.add('active');
        drawingInput?.setMode((btn as HTMLElement).dataset.mode as 'AIR' | 'SURFACE');
      });
    });
  } catch {
    // Error already handled via onStateChange callback
    setConnectButtonLoading(false);
  }
});

// --- Disconnect ---
disconnectBtn.addEventListener('click', () => {
  connection.disconnect();
});

function handleDisconnect(): void {
  strokeProcessor.clear();
  drawingInput?.clearPublishFunction();
  drawingInput = null;
  setConnectButtonLoading(false);
  hideViewer();
  showConnectForm();
}
