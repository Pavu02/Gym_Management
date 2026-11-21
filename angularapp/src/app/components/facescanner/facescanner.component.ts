import { Component, OnInit, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import * as faceapi from '@vladmandic/face-api';
import { FaceService, FaceProfileDescriptorDTO, FaceCheckinResponseDTO } from '../../services/face.service';

@Component({
  selector: 'app-facescanner',
  templateUrl: './facescanner.component.html',
  styleUrls: ['./facescanner.component.css']
})
export class FaceScannerComponent implements OnInit, OnDestroy {
  @ViewChild('videoElement') videoRef!: ElementRef<HTMLVideoElement>;
  @ViewChild('canvasElement') canvasRef!: ElementRef<HTMLCanvasElement>;

  cameraActive = false;
  cameraLoading = false;
  cameraError = '';
  statusText = 'Initializing scanner...';

  enrolledCount = 0;
  todayCheckins: FaceCheckinResponseDTO[] = [];

  // Popup modal state
  activePopup: FaceCheckinResponseDTO | null = null;
  popupTimer: any = null;

  private mediaStream: MediaStream | null = null;
  private scanInterval: any = null;
  private faceMatcher: faceapi.FaceMatcher | null = null;
  private userProfilesMap = new Map<number, { name: string; role: string }>();

  // State tracking for consecutive matches & cooldown
  private matchStreakMap = new Map<number, number>(); // userId -> consecutive match count
  private cooldownMap = new Map<number, number>(); // userId -> timestamp of last check-in
  private audioCtx: AudioContext | null = null;

  constructor(private faceService: FaceService) {}

  async ngOnInit(): Promise<void> {
    await this.initScanner();
    this.loadTodayCheckins();
  }

  ngOnDestroy(): void {
    this.stopCamera();
    if (this.popupTimer) {
      clearTimeout(this.popupTimer);
    }
    if (this.audioCtx) {
      try { this.audioCtx.close(); } catch (_) {}
    }
  }

  async initScanner(): Promise<void> {
    try {
      this.statusText = 'Loading detection models...';
      const MODEL_URL = '/assets/models';
      await faceapi.nets.tinyFaceDetector.loadFromUri(MODEL_URL);
      await faceapi.nets.faceLandmark68Net.loadFromUri(MODEL_URL);
      await faceapi.nets.faceRecognitionNet.loadFromUri(MODEL_URL);

      await this.loadDescriptorsAndBuildMatcher();
      await this.startCamera();
    } catch (err: any) {
      this.cameraError = 'Initialization failed: ' + (err.message || '');
      this.statusText = 'Error initializing scanner.';
    }
  }

  async loadDescriptorsAndBuildMatcher(): Promise<void> {
    this.statusText = 'Loading enrolled face descriptors...';
    try {
      const descriptors = await this.faceService.getAllDescriptors().toPromise();
      if (!descriptors || descriptors.length === 0) {
        this.statusText = 'No enrolled faces found. Please enroll members or trainers.';
        this.enrolledCount = 0;
        this.faceMatcher = null;
        return;
      }

      this.enrolledCount = descriptors.length;
      this.userProfilesMap.clear();

      const labeledDescriptors: faceapi.LabeledFaceDescriptors[] = [];
      for (const d of descriptors) {
        this.userProfilesMap.set(d.userId, { name: d.name, role: d.role });
        const floatArray = new Float32Array(d.descriptor);
        labeledDescriptors.push(new faceapi.LabeledFaceDescriptors(d.userId.toString(), [floatArray]));
      }

      // Distance threshold 0.5 (as specified)
      this.faceMatcher = new faceapi.FaceMatcher(labeledDescriptors, 0.5);
      this.statusText = `Loaded ${this.enrolledCount} enrolled face templates. Scanner active.`;
    } catch (err: any) {
      this.statusText = 'Could not load face templates: ' + (err.message || '');
    }
  }

  async startCamera(): Promise<void> {
    this.cameraLoading = true;
    this.cameraError = '';
    try {
      this.mediaStream = await navigator.mediaDevices.getUserMedia({
        video: { width: { ideal: 640 }, height: { ideal: 480 }, facingMode: 'user' }
      });

      if (this.videoRef && this.videoRef.nativeElement) {
        this.videoRef.nativeElement.srcObject = this.mediaStream;
        await this.videoRef.nativeElement.play();
        this.cameraActive = true;
        this.cameraLoading = false;
        this.statusText = 'Camera live. Continuous scanning every 1s...';
        this.startScanningLoop();
      }
    } catch (err: any) {
      this.cameraActive = false;
      this.cameraLoading = false;
      this.cameraError = 'Webcam access failed: ' + (err.message || '');
      this.statusText = 'Webcam unavailable.';
    }
  }

  stopCamera(): void {
    if (this.scanInterval) {
      clearInterval(this.scanInterval);
      this.scanInterval = null;
    }
    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach(t => t.stop());
      this.mediaStream = null;
    }
    this.cameraActive = false;
    this.statusText = 'Camera paused.';
  }

  toggleCamera(): void {
    if (this.cameraActive) {
      this.stopCamera();
    } else {
      this.startCamera();
    }
  }

  startScanningLoop(): void {
    if (this.scanInterval) {
      clearInterval(this.scanInterval);
    }

    // Run detection every 1 second (as specified)
    this.scanInterval = setInterval(async () => {
      if (!this.cameraActive || !this.videoRef || !this.videoRef.nativeElement) {
        return;
      }

      const video = this.videoRef.nativeElement;
      if (video.paused || video.ended) return;

      try {
        const detections = await faceapi
          .detectAllFaces(video, new faceapi.TinyFaceDetectorOptions({ scoreThreshold: 0.5 }))
          .withFaceLandmarks()
          .withFaceDescriptors();

        this.processDetections(detections);
      } catch (e) {
        // Silently catch transient frame read errors
      }
    }, 1000);
  }

  processDetections(detections: any[]): void {
    if (!this.canvasRef || !this.videoRef) return;
    const canvas = this.canvasRef.nativeElement;
    const video = this.videoRef.nativeElement;

    const displaySize = { width: video.videoWidth || 640, height: video.videoHeight || 480 };
    faceapi.matchDimensions(canvas, displaySize);
    const resizedDetections = faceapi.resizeResults(detections, displaySize);

    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    if (resizedDetections.length === 0) {
      this.matchStreakMap.clear();
      return;
    }

    const now = Date.now();

    for (const d of resizedDetections) {
      const box = d.detection ? d.detection.box : d.box;
      if (!box) continue;

      let recognizedName = 'Unknown';
      let recognizedUserId: number | null = null;
      let isKnown = false;

      if (this.faceMatcher && d.descriptor) {
        const bestMatch = this.faceMatcher.findBestMatch(d.descriptor);
        if (bestMatch.label !== 'unknown' && bestMatch.distance <= 0.5) {
          recognizedUserId = parseInt(bestMatch.label, 10);
          const profile = this.userProfilesMap.get(recognizedUserId);
          recognizedName = profile ? `${profile.name} (${profile.role})` : `User #${recognizedUserId}`;
          isKnown = true;
        }
      }

      // Draw bounding box
      ctx.lineWidth = 3;
      ctx.strokeStyle = isKnown ? '#00e676' : '#ff1744';
      ctx.strokeRect(box.x, box.y, box.width, box.height);

      // Draw label background & text
      ctx.font = 'bold 15px Rajdhani, sans-serif';
      const textWidth = ctx.measureText(recognizedName).width;
      ctx.fillStyle = isKnown ? 'rgba(0, 230, 118, 0.85)' : 'rgba(255, 23, 68, 0.85)';
      ctx.fillRect(box.x, box.y > 25 ? box.y - 25 : box.y, textWidth + 12, 22);

      ctx.fillStyle = '#000';
      ctx.fillText(recognizedName, box.x + 6, box.y > 25 ? box.y - 8 : box.y + 16);

      // Check-in logic: requires 3 consecutive scans for the same recognizedUserId
      if (isKnown && recognizedUserId !== null) {
        // Check cooldown (60 seconds)
        const lastCheckinTime = this.cooldownMap.get(recognizedUserId) || 0;
        if (now - lastCheckinTime < 60000) {
          continue; // Currently in cooldown, skip triggering API
        }

        const currentStreak = (this.matchStreakMap.get(recognizedUserId) || 0) + 1;
        this.matchStreakMap.set(recognizedUserId, currentStreak);

        if (currentStreak >= 3) {
          // Trigger API check-in
          this.matchStreakMap.delete(recognizedUserId);
          this.cooldownMap.set(recognizedUserId, now);
          this.executeCheckin(recognizedUserId);
        }
      }
    }
  }

  executeCheckin(userId: number): void {
    this.faceService.faceCheckin(userId).subscribe({
      next: (res) => {
        this.showPopup(res);
        if (res.status === 'CHECKED_IN' || res.status === 'ALREADY_CHECKED_IN') {
          this.playBeep(true);
        } else {
          this.playBeep(false);
        }
        this.loadTodayCheckins();
      },
      error: (err) => {
        this.playBeep(false);
        console.error('Face check-in error:', err);
      }
    });
  }

  showPopup(record: FaceCheckinResponseDTO): void {
    if (this.popupTimer) {
      clearTimeout(this.popupTimer);
    }
    this.activePopup = record;
    // Auto-close after 6 seconds (as specified)
    this.popupTimer = setTimeout(() => {
      this.activePopup = null;
    }, 6000);
  }

  closePopup(): void {
    if (this.popupTimer) {
      clearTimeout(this.popupTimer);
    }
    this.activePopup = null;
  }

  loadTodayCheckins(): void {
    this.faceService.getTodayAttendance().subscribe({
      next: (list) => {
        this.todayCheckins = list;
      },
      error: () => {}
    });
  }

  // Web Audio API tone generator (pleasant beep on success, low buzz on denied)
  playBeep(success: boolean): void {
    try {
      if (!this.audioCtx) {
        const AudioContextClass = window.AudioContext || (window as any).webkitAudioContext;
        this.audioCtx = new AudioContextClass();
      }

      if (this.audioCtx.state === 'suspended') {
        this.audioCtx.resume();
      }

      const osc = this.audioCtx.createOscillator();
      const gain = this.audioCtx.createGain();

      osc.connect(gain);
      gain.connect(this.audioCtx.destination);

      if (success) {
        // High pleasant chime (880Hz to 1046Hz)
        osc.type = 'sine';
        osc.frequency.setValueAtTime(880, this.audioCtx.currentTime);
        osc.frequency.exponentialRampToValueAtTime(1046, this.audioCtx.currentTime + 0.15);
        gain.gain.setValueAtTime(0.2, this.audioCtx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.01, this.audioCtx.currentTime + 0.3);
        osc.start();
        osc.stop(this.audioCtx.currentTime + 0.3);
      } else {
        // Low tone buzz (160Hz)
        osc.type = 'sawtooth';
        osc.frequency.setValueAtTime(160, this.audioCtx.currentTime);
        gain.gain.setValueAtTime(0.25, this.audioCtx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.01, this.audioCtx.currentTime + 0.4);
        osc.start();
        osc.stop(this.audioCtx.currentTime + 0.4);
      }
    } catch (_) {
      // Audio autoplay may be blocked in some browser policies
    }
  }
}
