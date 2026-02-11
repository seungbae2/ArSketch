import type { ConnectionState } from './livekit-connection';

export function showConnectForm(): void {
  document.getElementById('connect-screen')!.classList.remove('hidden');
  document.getElementById('viewer-screen')!.classList.add('hidden');
}

export function hideConnectForm(): void {
  document.getElementById('connect-screen')!.classList.add('hidden');
}

export function showViewer(): void {
  document.getElementById('viewer-screen')!.classList.remove('hidden');
}

export function hideViewer(): void {
  document.getElementById('viewer-screen')!.classList.add('hidden');
}

export function showError(message: string): void {
  const el = document.getElementById('error-message')!;
  el.textContent = message;
  el.classList.remove('hidden');
}

export function hideError(): void {
  const el = document.getElementById('error-message')!;
  el.textContent = '';
  el.classList.add('hidden');
}

export function setConnectButtonLoading(loading: boolean): void {
  const btn = document.getElementById('connect-btn') as HTMLButtonElement;
  btn.disabled = loading;
  btn.textContent = loading ? 'Connecting...' : 'Connect';
}

export function updateStatus(state: ConnectionState, participantCount: number): void {
  const dot = document.getElementById('status-dot')!;
  const text = document.getElementById('status-text')!;
  const count = document.getElementById('participant-count')!;

  dot.className = 'status-dot';
  switch (state) {
    case 'connected':
      dot.classList.add('connected');
      text.textContent = 'Connected';
      break;
    case 'connecting':
      dot.classList.add('connecting');
      text.textContent = 'Connecting...';
      break;
    case 'error':
      dot.classList.add('error');
      text.textContent = 'Error';
      break;
    default:
      text.textContent = 'Disconnected';
  }

  count.textContent = `${participantCount}`;
}
