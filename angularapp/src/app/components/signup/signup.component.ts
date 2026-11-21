import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { BiometricService } from '../../services/biometric.service';

export const passwordMatchValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const password = control.get('password');
  const confirmPassword = control.get('confirmPassword');

  if (!password || !confirmPassword) {
    return null;
  }

  return password.value === confirmPassword.value ? null : { passwordMismatch: true };
};

@Component({
  selector: 'app-signup',
  templateUrl: './signup.component.html',
  styleUrls: ['./signup.component.css']
})
export class SignupComponent implements OnInit, OnDestroy {
  @ViewChild('videoPlayer') videoPlayer?: ElementRef<HTMLVideoElement>;

  signupForm!: FormGroup;
  errorMessage: string = '';
  loading: boolean = false;

  // Step state: 'FORM' -> 'FACE_SCAN' -> 'SUCCESS'
  currentStep: 'FORM' | 'FACE_SCAN' = 'FORM';

  // Camera & Face Scan state
  mediaStream: MediaStream | null = null;
  cameraLoading: boolean = false;
  cameraDenied: boolean = false;
  noCamera: boolean = false;
  modelsLoaded: boolean = false;
  cameraError: string = '';

  biometricConsent: boolean = true;
  scanStatusMessage: string = 'Initializing face detection models...';
  scanQualityWarning: string = '';
  scanSamples: Float32Array[] = [];
  sampleLandmarks: any[] = [];
  requiredSamples: number = 3;
  capturing: boolean = false;
  scanIntervalId: any = null;

  registeredEmail: string = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private biometricService: BiometricService,
    private router: Router
  ) {}

  ngOnInit(): void {
    if (this.authService.isLoggedIn()) {
      this.router.navigate(['/home']);
    }

    this.signupForm = this.fb.group({
      name: ['', [Validators.required]],
      email: ['', [Validators.required, Validators.email]],
      phone: ['', [Validators.required, Validators.pattern('^[0-9]{10}$')]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', [Validators.required]]
    }, { validators: passwordMatchValidator });
  }

  ngOnDestroy(): void {
    this.stopCamera();
    if (this.scanIntervalId) {
      clearInterval(this.scanIntervalId);
    }
  }

  onSubmit(): void {
    if (this.signupForm.invalid) {
      this.signupForm.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    const { name, email, phone, password } = this.signupForm.value;
    this.registeredEmail = email;

    this.authService.register({ name, email, phone, password }).subscribe({
      next: (res) => {
        this.loading = false;
        // Registration successful; transition to Face Scan step
        this.currentStep = 'FACE_SCAN';
        setTimeout(() => {
          this.initCameraAndModels();
        }, 100);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Registration failed. Please try again.';
      }
    });
  }

  async initCameraAndModels(): Promise<void> {
    this.cameraLoading = true;
    this.cameraDenied = false;
    this.noCamera = false;
    this.cameraError = '';
    this.scanStatusMessage = 'Loading face recognition neural network...';

    try {
      await this.biometricService.loadModels();
      this.modelsLoaded = true;
      this.scanStatusMessage = 'Requesting camera access...';

      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        this.noCamera = true;
        this.cameraLoading = false;
        this.scanStatusMessage = 'Camera API not supported in this browser.';
        return;
      }

      this.mediaStream = await navigator.mediaDevices.getUserMedia({
        video: {
          width: { ideal: 640 },
          height: { ideal: 480 },
          facingMode: 'user'
        },
        audio: false
      });

      if (this.videoPlayer && this.videoPlayer.nativeElement) {
        this.videoPlayer.nativeElement.srcObject = this.mediaStream;
        await this.videoPlayer.nativeElement.play();
      }

      this.cameraLoading = false;
      this.scanStatusMessage = 'Position your face inside the orange guide.';
      this.startFaceMonitoring();
    } catch (err: any) {
      this.cameraLoading = false;
      if (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError') {
        this.cameraDenied = true;
        this.cameraError = 'Camera permission denied. Please allow camera access in browser settings.';
      } else if (err.name === 'NotFoundError' || err.name === 'DevicesNotFoundError') {
        this.noCamera = true;
        this.cameraError = 'No camera device detected on this device.';
      } else {
        this.cameraError = err.message || 'Unable to start camera.';
      }
      this.scanStatusMessage = 'Camera unavailable.';
    }
  }

  startFaceMonitoring(): void {
    if (this.scanIntervalId) {
      clearInterval(this.scanIntervalId);
    }

    this.scanIntervalId = setInterval(async () => {
      if (this.capturing || !this.videoPlayer || !this.videoPlayer.nativeElement) {
        return;
      }

      const video = this.videoPlayer.nativeElement;
      if (video.paused || video.ended || !this.modelsLoaded) {
        return;
      }

      try {
        const allFaces = await this.biometricService.detectAllFaces(video);
        if (allFaces.length === 0) {
          this.scanQualityWarning = 'No face detected. Please face the camera.';
          return;
        }
        if (allFaces.length > 1) {
          this.scanQualityWarning = 'Multiple faces detected! Please ensure only one person is in frame.';
          return;
        }

        const face = await this.biometricService.detectSingleFaceWithDescriptor(video);
        if (!face) {
          this.scanQualityWarning = 'Aligning face...';
          return;
        }

        if (face.score < 0.8) {
          this.scanQualityWarning = 'Low confidence (' + Math.round(face.score * 100) + '%). Improve lighting or face camera directly.';
          return;
        }

        const boxWidth = face.box.width;
        const videoWidth = video.videoWidth || 640;
        if (boxWidth / videoWidth < 0.22) {
          this.scanQualityWarning = 'Face too far. Please move closer to the camera.';
          return;
        }

        this.scanQualityWarning = '';

        if (this.scanSamples.length === 0) {
          this.scanStatusMessage = 'Hold steady for Sample 1 of 3...';
        } else if (this.scanSamples.length === 1) {
          this.scanStatusMessage = 'Liveness check: Please turn your head slightly or blink for Sample 2...';
        } else if (this.scanSamples.length === 2) {
          this.scanStatusMessage = 'Hold still for final Sample 3...';
        }
      } catch (e) {
        // Continue monitoring
      }
    }, 400);
  }

  async captureSample(): Promise<void> {
    if (!this.biometricConsent) {
      this.errorMessage = 'You must give consent to enroll face biometrics.';
      return;
    }

    if (!this.videoPlayer || !this.videoPlayer.nativeElement) {
      return;
    }

    this.capturing = true;
    this.errorMessage = '';
    const video = this.videoPlayer.nativeElement;

    try {
      const allFaces = await this.biometricService.detectAllFaces(video);
      if (allFaces.length === 0) {
        this.errorMessage = 'No face detected in camera view.';
        this.capturing = false;
        return;
      }
      if (allFaces.length > 1) {
        this.errorMessage = 'Multiple faces detected. Only one person allowed.';
        this.capturing = false;
        return;
      }

      const face = await this.biometricService.detectSingleFaceWithDescriptor(video);
      if (!face) {
        this.errorMessage = 'Unable to extract biometric landmarks. Hold steady.';
        this.capturing = false;
        return;
      }

      if (face.score < 0.8) {
        this.errorMessage = 'Detection confidence too low (' + Math.round(face.score * 100) + '%). Improve lighting.';
        this.capturing = false;
        return;
      }

      // Liveness validation between samples
      if (this.scanSamples.length > 0) {
        const prevLandmarks = this.sampleLandmarks[this.sampleLandmarks.length - 1];
        const prevNose = prevLandmarks.getNose()[3]; // nose tip
        const currNose = face.landmarks.getNose()[3];

        const deltaX = Math.abs(currNose.x - prevNose.x);
        const deltaY = Math.abs(currNose.y - prevNose.y);

        // If sample 2, ensure micro-movement occurred
        if (this.scanSamples.length === 1 && deltaX < 1.5 && deltaY < 1.5) {
          this.errorMessage = 'Liveness check: Please turn head slightly or blink before capturing sample 2.';
          this.capturing = false;
          return;
        }
      }

      this.scanSamples.push(face.descriptor);
      this.sampleLandmarks.push(face.landmarks);
      this.errorMessage = '';

      if (this.scanSamples.length < this.requiredSamples) {
        this.capturing = false;
        return;
      }

      // All 3 samples captured -> Average & submit to backend
      this.scanStatusMessage = 'Averaging samples & encrypting biometrics...';
      const averagedDescriptor = this.biometricService.averageDescriptors(this.scanSamples);

      this.biometricService.enrollFace({
        descriptor: averagedDescriptor,
        consent: this.biometricConsent
      }).subscribe({
        next: () => {
          this.capturing = false;
          this.stopCamera();
          this.router.navigate(['/login'], { queryParams: { faceEnrolled: 'true' } });
        },
        error: (err) => {
          this.capturing = false;
          this.errorMessage = err.error?.message || 'Biometric enrollment failed. You can re-enroll at the gym desk.';
        }
      });

    } catch (err: any) {
      this.capturing = false;
      this.errorMessage = err.message || 'Error capturing face sample.';
    }
  }

  retryCamera(): void {
    this.stopCamera();
    this.initCameraAndModels();
  }

  skipFaceEnrollment(): void {
    this.stopCamera();
    this.router.navigate(['/login'], { queryParams: { registered: 'true', enrollLater: 'true' } });
  }

  stopCamera(): void {
    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach(track => track.stop());
      this.mediaStream = null;
    }
  }
}
