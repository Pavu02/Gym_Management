import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { RegisteredDeviceDTO, WebAuthnService } from '../../services/webauthn.service';

@Component({
  selector: 'app-registered-devices',
  templateUrl: './registered-devices.component.html',
  styleUrls: ['./registered-devices.component.css']
})
export class RegisteredDevicesComponent implements OnInit, OnDestroy {
  devices: RegisteredDeviceDTO[] = [];
  loading = true;
  errorMessage = '';
  successMessage = '';

  editingId: number | null = null;
  editName = '';

  showRevokeAllModal = false;
  revokePassword = '';
  revokingAll = false;

  private destroy$ = new Subject<void>();

  constructor(private webAuthnService: WebAuthnService) {}

  ngOnInit(): void {
    this.loadDevices();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get activeDevicesCount(): number {
    return this.devices.filter(d => d.active).length;
  }

  loadDevices(): void {
    this.loading = true;
    this.webAuthnService.getMyDevices()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (list) => {
          this.devices = list || [];
          this.loading = false;
        },
        error: (err) => {
          this.errorMessage = err?.error?.message || 'Unable to load registered check-in devices.';
          this.loading = false;
        }
      });
  }

  onDeviceEnrolled(device: RegisteredDeviceDTO): void {
    this.successMessage = `Biometric device "${device.deviceName}" registered successfully.`;
    this.errorMessage = '';
    this.loadDevices();
  }

  startRename(device: RegisteredDeviceDTO): void {
    this.editingId = device.id;
    this.editName = device.deviceName;
  }

  cancelRename(): void {
    this.editingId = null;
    this.editName = '';
  }

  saveRename(device: RegisteredDeviceDTO): void {
    if (!this.editName.trim()) {
      return;
    }
    this.webAuthnService.renameDevice(device.id, this.editName.trim())
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.editingId = null;
          this.successMessage = 'Device renamed successfully.';
          this.loadDevices();
        },
        error: (err) => {
          this.errorMessage = err?.error?.message || 'Failed to rename device.';
        }
      });
  }

  revokeSingle(device: RegisteredDeviceDTO): void {
    if (!confirm(`Revoke check-in permission for "${device.deviceName}"?`)) {
      return;
    }
    this.webAuthnService.revokeDevice(device.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.successMessage = `Device "${device.deviceName}" revoked.`;
          this.loadDevices();
        },
        error: (err) => {
          this.errorMessage = err?.error?.message || 'Failed to revoke device.';
        }
      });
  }

  openRevokeAll(): void {
    this.revokePassword = '';
    this.errorMessage = '';
    this.showRevokeAllModal = true;
  }

  closeRevokeAll(): void {
    this.showRevokeAllModal = false;
    this.revokePassword = '';
  }

  confirmRevokeAll(): void {
    if (!this.revokePassword.trim()) {
      this.errorMessage = 'Enter your current account password to confirm.';
      return;
    }
    this.revokingAll = true;
    this.webAuthnService.revokeAllDevices(this.revokePassword)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (res) => {
          this.revokingAll = false;
          this.showRevokeAllModal = false;
          this.successMessage = `Revoked ${res.revokedCount} registered check-in device(s).`;
          this.loadDevices();
        },
        error: (err) => {
          this.revokingAll = false;
          this.errorMessage = err?.error?.message || 'Password verification failed.';
        }
      });
  }
}
