import { Component, OnInit, OnDestroy, ViewChild, ElementRef, Input, Output, EventEmitter } from '@angular/core';
import * as faceapi from '@vladmandic/face-api';
import { FaceService } from '../../services/face.service';

@Component({
  selector: 'app-face-enroll',
  templateUrl: './faceenroll.component.html',
  styleUrls: ['./faceenroll.component.css']
})
export class FaceEnrollComponent implements OnInit, OnDestroy {
  @Input() userId!: number;
  @Input() userName: string = '';
  @Input() role: string = 'MEMBER';
  @Output() enrolled = new EventEmitter<void>();
  @Output() close = new EventEmitter<void>();

  @ViewChild('videoElement') videoRef!: ElementRef<HTMLVideoElement>;
  @ViewChild('canvasElement') canvasRef!: ElementRef<HTMLCanvasElement>;

  modelsLoaded = false;
  cameraActive = false;
  cameraError = '';
  statusMessage = 'Initializing models...';
  detectionStatus: 'NO_FACE' | 'FACE_DETECTED' | 'MULTIPLE_FACES' = 'NO_FACE';

  consentGiven = false;
  capturing = false;
  captureProgress = 0; // 0 to 3
  successMessage = '';
  errorMessage = '';

  private mediaStream: MediaStream | null = null;
  private detectionInterval: any = null;

  constructor(private faceService: FaceService) {}

  async ngOnInit(): Promise<void> {
    await this.loadModels();
    if (this.modelsLoaded) {
      await this.startCamera();
    }
  }

  ngOnDestroy(): void {
    this.stopCamera();
  }

  async loadModels(): Promise<void> {
    try {
      this.statusMessage = 'Loading face recognition models...';
      const MODEL_URL = '/assets/models';
      await faceapi.nets.tinyFaceDetector.loadFromUri(MODEL_URL);
      await faceapi.nets.faceLandmark68Net.loadFromUri(MODEL_URL);
      await faceapi.nets.faceRecognitionNet.loadFromUri(MODEL_URL);
      this.modelsLoaded = true;
      this.statusMessage = 'Models loaded. Starting camera...';
    } catch (err: any) {
      this.cameraError = 'Failed to load face-api models from assets. ' + (err.message || '');
      this.statusMessage = 'Model loading failed.';
    }
  }

  async startCamera(): Promise<void> {
    this.cameraError = '';
    try {
      this.mediaStream = await navigator.mediaDevices.getUserMedia({
        video: { width: { ideal: 640 }, height: { ideal: 480 }, facingMode: 'user' }
      });

      if (this.videoRef && this.videoRef.nativeElement) {
        this.videoRef.nativeElement.srcObject = this.mediaStream;
        await this.videoRef.nativeElement.play();
        this.cameraActive = true;
        this.statusMessage = 'Camera active. Position your face inside the frame.';
        this.startFaceDetectionLoop();
      }
    } catch (err: any) {
      this.cameraActive = false;
      this.cameraError = 'Camera permission denied or camera unavailable: ' + (err.message || '');
      this.statusMessage = 'Camera unavailable.';
    }
  }

  startFaceDetectionLoop(): void {
    if (this.detectionInterval) {
      clearInterval(this.detectionInterval);
    }

    this.detectionInterval = setInterval(async () => {
      if (!this.cameraActive || !this.videoRef || !this.videoRef.nativeElement || this.capturing) {
        return;
      }

      const video = this.videoRef.nativeElement;
      if (video.paused || video.ended || !this.modelsLoaded) return;

      const detections = await faceapi
        .detectAllFaces(video, new faceapi.TinyFaceDetectorOptions({ scoreThreshold: 0.5 }))
        .withFaceLandmarks();

      if (detections.length === 0) {
        this.detectionStatus = 'NO_FACE';
        this.statusMessage = 'No face detected. Please look directly at the camera.';
      } else if (detections.length > 1) {
        this.detectionStatus = 'MULTIPLE_FACES';
        this.statusMessage = 'Multiple faces detected! Please ensure only one person is in frame.';
      } else {
        this.detectionStatus = 'FACE_DETECTED';
        this.statusMessage = 'Face detected! Ready to capture.';
      }

      this.drawDetectionBox(detections);
    }, 400);
  }

  drawDetectionBox(detections: any[]): void {
    if (!this.canvasRef || !this.videoRef) return;
    const canvas = this.canvasRef.nativeElement;
    const video = this.videoRef.nativeElement;
    const displaySize = { width: video.videoWidth || 640, height: video.videoHeight || 480 };
    faceapi.matchDimensions(canvas, displaySize);

    const resizedDetections = faceapi.resizeResults(detections, displaySize);
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    for (const d of resizedDetections) {
      const box = d.detection ? d.detection.box : d.box;
      if (!box) continue;
      ctx.lineWidth = 3;
      ctx.strokeStyle = this.detectionStatus === 'FACE_DETECTED' ? '#00e676' : '#ff3d00';
      ctx.strokeRect(box.x, box.y, box.width, box.height);

      ctx.fillStyle = ctx.strokeStyle;
      ctx.font = 'bold 14px Rajdhani, sans-serif';
      const label = this.detectionStatus === 'FACE_DETECTED' ? 'Face Aligned' : 'Warning';
      ctx.fillText(label, box.x, box.y > 20 ? box.y - 8 : box.y + 15);
    }
  }

  async startCapture(): Promise<void> {
    if (!this.consentGiven) {
      this.errorMessage = 'You must agree to the biometric consent before enrolling.';
      return;
    }
    if (this.detectionStatus !== 'FACE_DETECTED') {
      this.errorMessage = 'Please ensure a single face is clearly visible and detected.';
      return;
    }

    this.capturing = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.captureProgress = 0;

    const samples: number[][] = [];
    const video = this.videoRef.nativeElement;

    try {
      for (let i = 1; i <= 3; i++) {
        this.captureProgress = i;
        this.statusMessage = `Capturing sample ${i} of 3... Hold still.`;

        // Small delay between samples
        await new Promise(res => setTimeout(res, 500));

        const detection = await faceapi
          .detectSingleFace(video, new faceapi.TinyFaceDetectorOptions({ scoreThreshold: 0.6 }))
          .withFaceLandmarks()
          .withFaceDescriptor();

        if (!detection || !detection.descriptor) {
          throw new Error(`Lost face alignment during sample ${i}. Please try again.`);
        }

        samples.push(Array.from(detection.descriptor));
      }

      this.statusMessage = 'Averaging descriptors and registering...';

      // Average the 3 samples across all 128 dimensions
      const averagedDescriptor: number[] = new Array(128).fill(0);
      for (let dim = 0; dim < 128; dim++) {
        let sum = 0;
        for (const s of samples) {
          sum += s[dim];
        }
        averagedDescriptor[dim] = sum / samples.length;
      }

      // Send to backend
      this.faceService.enrollFace(this.userId, averagedDescriptor).subscribe({
        next: () => {
          this.capturing = false;
          this.successMessage = `Face template registered successfully for ${this.userName || 'user'}!`;
          this.statusMessage = 'Enrollment complete!';
          setTimeout(() => {
            this.enrolled.emit();
            this.closeModal();
          }, 1500);
        },
        error: (err: any) => {
          this.capturing = false;
          this.errorMessage = err.error?.message || err.message || 'Failed to save face descriptor to server.';
          this.statusMessage = 'Enrollment failed.';
        }
      });

    } catch (err: any) {
      this.capturing = false;
      this.errorMessage = err.message || 'Error during capture. Please try again.';
      this.statusMessage = 'Capture interrupted.';
    }
  }

  stopCamera(): void {
    if (this.detectionInterval) {
      clearInterval(this.detectionInterval);
      this.detectionInterval = null;
    }
    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach(track => track.stop());
      this.mediaStream = null;
    }
    this.cameraActive = false;
  }

  closeModal(): void {
    this.stopCamera();
    this.close.emit();
  }
}
