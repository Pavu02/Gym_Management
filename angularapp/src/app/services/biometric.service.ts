import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as faceapi from '@vladmandic/face-api';

export interface FaceEnrollmentRequest {
  descriptor: number[];
  consent: boolean;
  password?: string;
  memberId?: number;
}

export interface BiometricStatusResponse {
  faceEnrolled: boolean;
  faceEnrolledAt: string | null;
  consentGiven: boolean;
  consentAt: string | null;
  passkeyCount: number;
  fingerprintEnrolled?: boolean;
  fingerprintEnrolledAt?: string | null;
}

export interface BiometricResponse {
  status: string;
  message: string;
  memberId: number;
  enrolledAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class BiometricService {
  private apiUrl = 'http://localhost:8080/api/member/biometrics';
  private modelsLoaded = false;

  constructor(private http: HttpClient) {}

  async loadModels(): Promise<void> {
    if (this.modelsLoaded) {
      return;
    }

    const MODEL_URL = '/assets/models';
    await Promise.all([
      faceapi.nets.tinyFaceDetector.loadFromUri(MODEL_URL),
      faceapi.nets.faceLandmark68Net.loadFromUri(MODEL_URL),
      faceapi.nets.faceRecognitionNet.loadFromUri(MODEL_URL)
    ]);

    this.modelsLoaded = true;
  }

  isModelsLoaded(): boolean {
    return this.modelsLoaded;
  }

  async detectSingleFaceWithDescriptor(videoEl: HTMLVideoElement): Promise<{
    descriptor: Float32Array;
    score: number;
    box: faceapi.Box;
    landmarks: faceapi.FaceLandmarks68;
  } | null> {
    await this.loadModels();

    const options = new faceapi.TinyFaceDetectorOptions({ inputSize: 224, scoreThreshold: 0.5 });
    const detection = await faceapi
      .detectSingleFace(videoEl, options)
      .withFaceLandmarks()
      .withFaceDescriptor();

    if (!detection) {
      return null;
    }

    return {
      descriptor: detection.descriptor,
      score: detection.detection.score,
      box: detection.detection.box,
      landmarks: detection.landmarks
    };
  }

  async detectAllFaces(videoEl: HTMLVideoElement): Promise<faceapi.FaceDetection[]> {
    await this.loadModels();
    const options = new faceapi.TinyFaceDetectorOptions({ inputSize: 224, scoreThreshold: 0.5 });
    return faceapi.detectAllFaces(videoEl, options);
  }

  calculateEuclideanDistance(desc1: number[] | Float32Array, desc2: number[] | Float32Array): number {
    return faceapi.euclideanDistance(desc1, desc2);
  }

  averageDescriptors(descriptors: Float32Array[]): number[] {
    if (descriptors.length === 0) return [];
    const len = descriptors[0].length;
    const avg = new Array<number>(len).fill(0);

    for (const desc of descriptors) {
      for (let i = 0; i < len; i++) {
        avg[i] += desc[i];
      }
    }

    for (let i = 0; i < len; i++) {
      avg[i] /= descriptors.length;
    }

    return avg;
  }

  enrollFace(request: FaceEnrollmentRequest): Observable<BiometricResponse> {
    return this.http.post<BiometricResponse>(`${this.apiUrl}/face`, request);
  }

  getBiometricStatus(memberId?: number): Observable<BiometricStatusResponse> {
    if (memberId) {
      return this.http.get<BiometricStatusResponse>(`${this.apiUrl}/status/${memberId}`);
    }
    return this.http.get<BiometricStatusResponse>(`${this.apiUrl}/status`);
  }

  deleteBiometrics(memberId?: number): Observable<void> {
    if (memberId) {
      return this.http.delete<void>(`${this.apiUrl}/${memberId}`);
    }
    return this.http.delete<void>(this.apiUrl);
  }
}
