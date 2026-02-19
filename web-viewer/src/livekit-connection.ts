import {
  Room,
  RoomEvent,
  Track,
  type RemoteTrackPublication,
  type RemoteParticipant,
} from 'livekit-client';

export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'error';

export interface LiveKitCallbacks {
  onStateChange: (state: ConnectionState, error?: string) => void;
  onParticipantCountChange: (count: number) => void;
}

/**
 * LiveKit room connection manager for the web viewer.
 *
 * Subscribes to:
 * - Remote video tracks (AR camera feed with strokes already rendered)
 * - Participant connect/disconnect events
 *
 * Publishes:
 * - DataChannel messages on "remote_touch" topic (web viewer drawing input)
 */
export class LiveKitConnection {
  private room: Room | null = null;
  private videoElement: HTMLVideoElement;
  private callbacks: LiveKitCallbacks;

  constructor(videoElement: HTMLVideoElement, callbacks: LiveKitCallbacks) {
    this.videoElement = videoElement;
    this.callbacks = callbacks;
  }

  async connect(serverUrl: string, token: string): Promise<void> {
    this.callbacks.onStateChange('connecting');

    try {
      this.room = new Room({
        adaptiveStream: true,
        dynacast: true,
      });

      this.setupEventListeners();
      await this.room.connect(serverUrl, token);

      console.log('[ArSketch] Connected to room:', this.room.name);
      console.log('[ArSketch] Remote participants:', this.room.remoteParticipants.size);
      for (const [id, p] of this.room.remoteParticipants) {
        console.log('[ArSketch] Participant:', id, p.identity);
        for (const [sid, pub] of p.trackPublications) {
          console.log('[ArSketch]   Track pub:', sid, 'kind:', pub.kind, 'subscribed:', pub.isSubscribed, 'track:', pub.track ? 'yes' : 'no');
        }
      }

      // Attach any already-published tracks from participants who joined before us
      this.attachExistingTracks();

      // +1 to include local participant, matching Android behavior
      const participantCount = this.room.remoteParticipants.size + 1;
      this.callbacks.onParticipantCountChange(participantCount);
      this.callbacks.onStateChange('connected');
    } catch (e) {
      const message = e instanceof Error ? e.message : 'Connection failed';
      this.callbacks.onStateChange('error', message);
      throw e;
    }
  }

  disconnect(): void {
    if (this.room) {
      this.room.disconnect();
      this.room = null;
    }
    this.callbacks.onStateChange('disconnected');
  }

  /**
   * DataChannel로 데이터 발행. remote_touch 토픽 전송에 사용.
   */
  publishData(data: Uint8Array, topic: string): void {
    if (!this.room) return;
    this.room.localParticipant.publishData(data, {
      reliable: true,
      topic: topic,
    });
  }

  /**
   * When the viewer joins after the host, tracks are already published.
   * TrackSubscribed may not fire for them, so we manually attach.
   */
  private attachExistingTracks(): void {
    if (!this.room) return;

    for (const participant of this.room.remoteParticipants.values()) {
      for (const publication of participant.trackPublications.values()) {
        if (publication.track && publication.track.kind === Track.Kind.Video) {
          console.log('[ArSketch] Attaching existing video track from', participant.identity);
          publication.track.attach(this.videoElement);
        }
      }
    }
  }

  private setupEventListeners(): void {
    if (!this.room) return;

    // Subscribe to remote video tracks (AR camera feed with strokes baked in)
    this.room.on(
      RoomEvent.TrackSubscribed,
      (track: Track, _publication: RemoteTrackPublication, participant: RemoteParticipant) => {
        console.log('[ArSketch] TrackSubscribed:', track.kind, 'from', participant.identity);
        if (track.kind === Track.Kind.Video) {
          console.log('[ArSketch] Attaching video track to element');
          track.attach(this.videoElement);
        }
      },
    );

    this.room.on(
      RoomEvent.TrackUnsubscribed,
      (track: Track) => {
        if (track.kind === Track.Kind.Video) {
          track.detach(this.videoElement);
        }
      },
    );

    // Participant tracking
    this.room.on(RoomEvent.ParticipantConnected, () => {
      if (this.room) {
        this.callbacks.onParticipantCountChange(this.room.remoteParticipants.size + 1);
      }
    });

    this.room.on(RoomEvent.ParticipantDisconnected, () => {
      if (this.room) {
        this.callbacks.onParticipantCountChange(this.room.remoteParticipants.size + 1);
      }
    });

    this.room.on(RoomEvent.Disconnected, () => {
      this.callbacks.onStateChange('disconnected');
    });
  }
}
