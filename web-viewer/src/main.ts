import { StrokeProcessor } from './stroke-processor';
import { LiveKitConnection, type ConnectionState } from './livekit-connection';
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

// --- Core instances ---
// StrokeProcessor: DataChannel 수신은 유지 (향후 활용 가능)
// 캔버스 오버레이 렌더링은 비활성화 — 비디오에 스트로크가 이미 포함됨
const strokeProcessor = new StrokeProcessor();

let participantCount = 0;

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
  setConnectButtonLoading(false);
  hideViewer();
  showConnectForm();
}
