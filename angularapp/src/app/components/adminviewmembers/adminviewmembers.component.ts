import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MemberService } from '../../services/member.service';
import { AttendanceService } from '../../services/attendance.service';
import { FingerprintService, FingerprintDeviceDTO } from '../../services/fingerprint.service';
import { MemberResponse } from '../../models/member.model';

@Component({
  selector: 'app-adminviewmembers',
  templateUrl: './adminviewmembers.component.html',
  styleUrls: ['./adminviewmembers.component.css']
})
export class AdminviewmembersComponent implements OnInit, OnDestroy {
  members: MemberResponse[] = [];
  filteredMembers: MemberResponse[] = [];

  searchTerm = '';
  statusFilter: 'ALL' | 'ACTIVE' | 'PENDING_PAYMENT' | 'EXPIRED' | 'DEACTIVATED' = 'ALL';
  sortBy: 'name' | 'expiryAsc' | 'expiryDesc' | 'status' = 'name';
  viewMode: 'cards' | 'table' = 'cards';

  loading = true;
  errorMessage = '';
  successMessage = '';
  copiedText = '';

  selectedMemberForModal: MemberResponse | null = null;
  selectedMemberForFaceEnroll: MemberResponse | null = null;

  // Deactivate / Reactivate Confirmation Modal State
  showDeactivateModal = false;
  showReactivateModal = false;
  targetMemberForState: MemberResponse | null = null;
  deactivationReasonInput = '';
  stateActionSubmitting = false;

  // Member registered fingerprint devices
  memberFingerprintDevices: FingerprintDeviceDTO[] = [];
  loadingMemberDevices = false;

  // Roster KPI counts
  activeCount = 0;
  pendingCount = 0;
  expiredCount = 0;
  deactivatedCount = 0;
  expiringSoonCount = 0;
  activeRatio = 0;

  private destroy$ = new Subject<void>();
  private copyTimer?: any;
  private syncInterval?: any;

  constructor(
    private memberService: MemberService,
    private attendanceService: AttendanceService,
    private fingerprintService: FingerprintService
  ) {}

  ngOnInit(): void {
    this.loadMembers();
    this.syncInterval = setInterval(() => this.refreshMembersSilently(), 30000);
  }

  @HostListener('window:focus')
  onWindowFocus(): void {
    this.refreshMembersSilently();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    if (this.copyTimer) clearTimeout(this.copyTimer);
    if (this.syncInterval) clearInterval(this.syncInterval);
  }

  loadMembers(): void {
    this.loading = true;
    this.memberService.getAllMembers()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data) => {
          this.members = data || [];
          this.computeMetrics();
          this.applyFilter();
          this.loading = false;
        },
        error: (err) => {
          this.errorMessage = err.error?.message || 'Failed to load members';
          this.loading = false;
        }
      });
  }

  refreshMembersSilently(): void {
    if (this.stateActionSubmitting) return;
    this.memberService.getAllMembers()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data) => {
          this.members = data || [];
          this.computeMetrics();
          this.applyFilter();
          if (this.selectedMemberForModal) {
            const updated = this.members.find(m => m.id === this.selectedMemberForModal?.id);
            if (updated) {
              this.selectedMemberForModal = updated;
            }
          }
        },
        error: () => {}
      });
  }

  private computeMetrics(): void {
    const total = this.members.length;
    this.activeCount = this.members.filter(m =>
      (m.status || '').toUpperCase() === 'ACTIVE' && m.active !== false
    ).length;
    this.pendingCount = this.members.filter(m => {
      const s = (m.status || '').toUpperCase();
      return s === 'PENDING_PAYMENT' || s === 'PENDING';
    }).length;
    this.expiredCount = this.members.filter(m => (m.status || '').toUpperCase() === 'EXPIRED').length;
    this.deactivatedCount = this.members.filter(m => (m.status || '').toUpperCase() === 'DEACTIVATED').length;
    this.expiringSoonCount = this.members.filter(m =>
      (m.status || '').toUpperCase() === 'ACTIVE' &&
      m.active !== false &&
      m.daysLeft !== null &&
      m.daysLeft !== undefined &&
      m.daysLeft <= 7
    ).length;
    this.activeRatio = total > 0 ? Math.round((this.activeCount / total) * 100) : 0;
  }

  applyFilter(): void {
    const q = this.searchTerm.trim().toLowerCase();

    let list = this.members.filter(m => {
      const name = (m.name || '').toLowerCase();
      const email = (m.email || '').toLowerCase();
      const phone = (m.phone || '').toLowerCase();
      const plan = (m.planName || '').toLowerCase();
      const trainer = (m.trainerName || '').toLowerCase();
      const goal = (m.fitnessGoal || '').toLowerCase();

      const matchSearch =
        !q ||
        name.includes(q) ||
        email.includes(q) ||
        phone.includes(q) ||
        plan.includes(q) ||
        trainer.includes(q) ||
        goal.includes(q);

      const memberStatus = (m.status || '').toUpperCase();
      const matchStatus =
        this.statusFilter === 'ALL' ||
        memberStatus === this.statusFilter.toUpperCase() ||
        (this.statusFilter === 'PENDING_PAYMENT' && memberStatus === 'PENDING');

      return matchSearch && matchStatus;
    });

    list = [...list].sort((a, b) => {
      switch (this.sortBy) {
        case 'expiryAsc':
          return (a.daysLeft ?? 99999) - (b.daysLeft ?? 99999);
        case 'expiryDesc':
          return (b.daysLeft ?? -1) - (a.daysLeft ?? -1);
        case 'status':
          return (a.status || '').localeCompare(b.status || '');
        case 'name':
        default:
          return (a.name || '').localeCompare(b.name || '');
      }
    });

    this.filteredMembers = list;
  }

  selectStatus(status: 'ALL' | 'ACTIVE' | 'PENDING_PAYMENT' | 'EXPIRED' | 'DEACTIVATED'): void {
    this.statusFilter = status;
    this.applyFilter();
  }

  clearSearch(): void {
    this.searchTerm = '';
    this.applyFilter();
  }

  resetAllFilters(): void {
    this.searchTerm = '';
    this.statusFilter = 'ALL';
    this.sortBy = 'name';
    this.applyFilter();
  }

  setViewMode(mode: 'cards' | 'table'): void {
    this.viewMode = mode;
  }

  isDeactivated(member?: MemberResponse | null): boolean {
    return !!member && ((member.status || '').toUpperCase() === 'DEACTIVATED' || member.active === false);
  }

  getInitials(name?: string): string {
    if (!name || !name.trim()) return 'BX';
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  getStatusClass(status?: string): string {
    const s = (status || '').toUpperCase();
    if (s === 'ACTIVE') return 'st-active';
    if (s === 'DEACTIVATED' || s === 'SUSPENDED') return 'st-deactivated';
    if (s === 'EXPIRED') return 'st-expired';
    return 'st-pending';
  }

  getStatusLabel(status?: string): string {
    const s = (status || '').toUpperCase();
    if (s === 'PENDING_PAYMENT') return 'PENDING';
    if (s === 'DEACTIVATED') return 'DEACTIVATED';
    return s || 'PENDING';
  }

  getFaceBadgeLabel(member: MemberResponse): string {
    if (this.isDeactivated(member)) return 'Face ID: Disabled';
    if (member.faceEnrolled) return 'Face ID: Active';
    if (member.faceReenrollmentRequired) return 'Face ID: Re-enroll Req.';
    return 'Face ID: Off';
  }

  getPasskeyBadgeLabel(member: MemberResponse): string {
    if (this.isDeactivated(member)) return 'Passkey / QR: Disabled';
    const cnt = member.activeBiometricDevices ?? 0;
    if (cnt > 0) return `Passkey: ${cnt} Active`;
    return 'Passkey: Not Enrolled';
  }

  copyContact(text?: string): void {
    if (!text) return;
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      navigator.clipboard.writeText(text).then(() => {
        this.copiedText = text;
        if (this.copyTimer) clearTimeout(this.copyTimer);
        this.copyTimer = setTimeout(() => (this.copiedText = ''), 1800);
      }).catch(() => {});
    }
  }

  dismissAlerts(): void {
    this.successMessage = '';
    this.errorMessage = '';
  }

  openDetailsModal(member: MemberResponse): void {
    this.selectedMemberForModal = member;
    this.memberFingerprintDevices = [];
    this.loadingMemberDevices = true;
    this.fingerprintService.getMemberDevices(member.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (devices) => {
          this.memberFingerprintDevices = devices || [];
          this.loadingMemberDevices = false;
        },
        error: () => {
          this.loadingMemberDevices = false;
        }
      });
  }

  closeDetailsModal(): void {
    this.selectedMemberForModal = null;
    this.memberFingerprintDevices = [];
  }

  revokeDevice(deviceId: number, deviceName: string): void {
    if (!confirm(`Are you sure you want to revoke "${deviceName}" for this member?`)) {
      return;
    }
    this.fingerprintService.adminDeleteDevice(deviceId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.memberFingerprintDevices = this.memberFingerprintDevices.filter(d => d.id !== deviceId);
          this.successMessage = `Device "${deviceName}" revoked successfully.`;
        },
        error: (err: any) => {
          this.errorMessage = err.error?.message || 'Failed to revoke device';
        }
      });
  }

  openFaceEnroll(member: MemberResponse): void {
    if (this.isDeactivated(member)) {
      this.errorMessage = 'Cannot enroll Face ID for a deactivated member. Reactivate the member first.';
      return;
    }
    this.selectedMemberForModal = null;
    this.selectedMemberForFaceEnroll = member;
  }

  closeFaceEnroll(): void {
    this.selectedMemberForFaceEnroll = null;
  }

  onFaceEnrolled(): void {
    this.successMessage = `Face template registered successfully for ${this.selectedMemberForFaceEnroll?.name}!`;
    this.selectedMemberForFaceEnroll = null;
    this.refreshMembersSilently();
  }

  getAge(dob?: string): number | string {
    if (!dob) return 'N/A';
    const birth = new Date(dob);
    const now = new Date();
    let age = now.getFullYear() - birth.getFullYear();
    const m = now.getMonth() - birth.getMonth();
    if (m < 0 || (m === 0 && now.getDate() < birth.getDate())) age--;
    return age > 0 ? age : 'N/A';
  }

  checkIn(member: MemberResponse): void {
    if (this.isDeactivated(member)) {
      this.errorMessage = `Access Denied — ${member.name} is deactivated.`;
      return;
    }
    this.errorMessage = '';
    this.successMessage = '';
    this.attendanceService.checkIn(member.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.successMessage = `Member ${member.name} checked in successfully!`;
          member.hasOpenAttendance = true;
          this.refreshMembersSilently();
        },
        error: (err) => {
          this.errorMessage = err.error?.message || `Failed to check in ${member.name}`;
          if (err.status === 403 || err.status === 409) {
            this.refreshMembersSilently();
          }
        }
      });
  }

  checkOut(member: MemberResponse): void {
    if (this.isDeactivated(member) && !member.hasOpenAttendance) {
      this.errorMessage = 'Check Out is disabled for deactivated members without an open session.';
      return;
    }
    this.errorMessage = '';
    this.successMessage = '';
    this.attendanceService.checkOut(member.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.successMessage = `Member ${member.name} checked out successfully!`;
          member.hasOpenAttendance = false;
          this.refreshMembersSilently();
        },
        error: (err) => {
          this.errorMessage = err.error?.message || `Failed to check out ${member.name}`;
        }
      });
  }

  openDeactivateModal(member: MemberResponse): void {
    if (this.isProtectedAdmin(member)) {
      this.errorMessage = 'Admin account cannot be deactivated or deleted from the database.';
      return;
    }
    if (this.isDeactivated(member)) {
      return;
    }
    this.errorMessage = '';
    this.targetMemberForState = member;
    this.deactivationReasonInput = '';
    this.showDeactivateModal = true;
  }

  closeDeactivateModal(): void {
    if (this.stateActionSubmitting) return;
    this.showDeactivateModal = false;
    this.targetMemberForState = null;
    this.deactivationReasonInput = '';
  }

  confirmDeactivateMember(): void {
    if (this.stateActionSubmitting || !this.targetMemberForState) {
      return;
    }
    const target = this.targetMemberForState;
    const reason = this.deactivationReasonInput.trim();
    this.stateActionSubmitting = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.memberService.deactivateMember(target.id, reason || undefined, target.version)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (res) => {
          this.stateActionSubmitting = false;
          this.showDeactivateModal = false;
          this.targetMemberForState = null;
          this.deactivationReasonInput = '';

          // Immediate optimistic state reflection without page reload
          this.members = this.members.map(m => {
            if (m.id !== target.id) return m;
            const updated: MemberResponse = {
              ...m,
              status: 'DEACTIVATED',
              active: false,
              faceEnabled: false,
              faceEnrolled: false,
              faceReenrollmentRequired: true,
              activeBiometricDevices: 0,
              hasOpenAttendance: false,
              deactivatedAt: new Date().toISOString(),
              deactivationReason: reason || 'Deactivated by administrator',
              version: res.version ?? ((m.version ?? 0) + 1)
            };
            if (this.selectedMemberForModal?.id === target.id) {
              this.selectedMemberForModal = updated;
              this.memberFingerprintDevices = [];
            }
            return updated;
          });
          this.computeMetrics();
          this.applyFilter();
          this.successMessage = `${target.name} has been deactivated cleanly. Historical attendance and payment records are preserved.`;
          this.refreshMembersSilently();
        },
        error: (err) => {
          this.stateActionSubmitting = false;
          this.showDeactivateModal = false;
          this.targetMemberForState = null;
          this.errorMessage = err.error?.message || 'Failed to deactivate member';
          if (err.status === 409) {
            this.loadMembers();
          }
        }
      });
  }

  openReactivateModal(member: MemberResponse): void {
    if (!this.isDeactivated(member)) {
      return;
    }
    this.errorMessage = '';
    this.targetMemberForState = member;
    this.showReactivateModal = true;
  }

  closeReactivateModal(): void {
    if (this.stateActionSubmitting) return;
    this.showReactivateModal = false;
    this.targetMemberForState = null;
  }

  confirmReactivateMember(): void {
    if (this.stateActionSubmitting || !this.targetMemberForState) {
      return;
    }
    const target = this.targetMemberForState;
    this.stateActionSubmitting = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.memberService.reactivateMember(target.id, target.version)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (res) => {
          this.stateActionSubmitting = false;
          this.showReactivateModal = false;
          this.targetMemberForState = null;

          this.members = this.members.map(m => {
            if (m.id !== target.id) return m;
            const updated: MemberResponse = {
              ...m,
              status: res.status || 'ACTIVE',
              active: true,
              faceEnabled: false,
              faceEnrolled: false,
              faceReenrollmentRequired: true,
              activeBiometricDevices: 0,
              deactivatedAt: undefined,
              deactivationReason: undefined,
              version: res.version ?? ((m.version ?? 0) + 1)
            };
            if (this.selectedMemberForModal?.id === target.id) {
              this.selectedMemberForModal = updated;
            }
            return updated;
          });
          this.computeMetrics();
          this.applyFilter();
          this.successMessage = `${target.name} reactivated with status ${res.status}. Previously revoked biometrics remain disabled until re-enrolled.`;
          this.refreshMembersSilently();
        },
        error: (err) => {
          this.stateActionSubmitting = false;
          this.showReactivateModal = false;
          this.targetMemberForState = null;
          this.errorMessage = err.error?.message || 'Failed to reactivate member';
          if (err.status === 409) {
            this.loadMembers();
          }
        }
      });
  }

  isProtectedAdmin(member?: MemberResponse | null): boolean {
    if (!member) return false;
    const role = (member.role || '').trim().toUpperCase();
    const email = (member.email || '').trim().toLowerCase();
    const name = (member.name || '').trim().toUpperCase();
    return role === 'ADMIN' || email === 'admin@gym.com' || name === 'ADMIN';
  }
}
