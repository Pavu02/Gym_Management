import { Component, OnDestroy, OnInit } from '@angular/core';
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
  statusFilter: 'ALL' | 'ACTIVE' | 'PENDING_PAYMENT' | 'EXPIRED' = 'ALL';
  sortBy: 'name' | 'expiryAsc' | 'expiryDesc' | 'status' = 'name';
  viewMode: 'cards' | 'table' = 'cards';

  loading = true;
  errorMessage = '';
  successMessage = '';
  copiedText = '';

  selectedMemberForModal: MemberResponse | null = null;
  selectedMemberForFaceEnroll: MemberResponse | null = null;

  // Member registered fingerprint devices
  memberFingerprintDevices: FingerprintDeviceDTO[] = [];
  loadingMemberDevices = false;

  // Roster KPI counts
  activeCount = 0;
  pendingCount = 0;
  expiredCount = 0;
  expiringSoonCount = 0;
  activeRatio = 0;

  private destroy$ = new Subject<void>();
  private copyTimer?: any;

  constructor(
    private memberService: MemberService,
    private attendanceService: AttendanceService,
    private fingerprintService: FingerprintService
  ) {}

  ngOnInit(): void {
    this.loadMembers();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    if (this.copyTimer) clearTimeout(this.copyTimer);
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

  private computeMetrics(): void {
    const total = this.members.length;
    this.activeCount = this.members.filter(m => (m.status || '').toUpperCase() === 'ACTIVE').length;
    this.pendingCount = this.members.filter(m => (m.status || '').toUpperCase() === 'PENDING_PAYMENT').length;
    this.expiredCount = this.members.filter(m => (m.status || '').toUpperCase() === 'EXPIRED').length;
    this.expiringSoonCount = this.members.filter(m =>
      (m.status || '').toUpperCase() === 'ACTIVE' &&
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

      const matchStatus =
        this.statusFilter === 'ALL' ||
        (m.status || '').toUpperCase() === this.statusFilter.toUpperCase();

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

  selectStatus(status: 'ALL' | 'ACTIVE' | 'PENDING_PAYMENT' | 'EXPIRED'): void {
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

  getInitials(name?: string): string {
    if (!name || !name.trim()) return 'BX';
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  getStatusClass(status?: string): string {
    const s = (status || '').toUpperCase();
    if (s === 'ACTIVE') return 'st-active';
    if (s === 'EXPIRED') return 'st-expired';
    return 'st-pending';
  }

  getStatusLabel(status?: string): string {
    const s = (status || '').toUpperCase();
    if (s === 'PENDING_PAYMENT') return 'PENDING';
    return s || 'PENDING';
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
    this.selectedMemberForModal = null;
    this.selectedMemberForFaceEnroll = member;
  }

  closeFaceEnroll(): void {
    this.selectedMemberForFaceEnroll = null;
  }

  onFaceEnrolled(): void {
    this.successMessage = `Face template registered successfully for ${this.selectedMemberForFaceEnroll?.name}!`;
    this.selectedMemberForFaceEnroll = null;
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
    this.errorMessage = '';
    this.successMessage = '';
    this.attendanceService.checkIn(member.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.successMessage = `Member ${member.name} checked in successfully!`;
        },
        error: (err) => {
          this.errorMessage = err.error?.message || `Failed to check in ${member.name}`;
        }
      });
  }

  checkOut(member: MemberResponse): void {
    this.errorMessage = '';
    this.successMessage = '';
    this.attendanceService.checkOut(member.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.successMessage = `Member ${member.name} checked out successfully!`;
        },
        error: (err) => {
          this.errorMessage = err.error?.message || `Failed to check out ${member.name}`;
        }
      });
  }

  deleteMember(id: number, name: string): void {
    const target = this.members.find(m => m.id === id);
    if (target && this.isProtectedAdmin(target)) {
      this.errorMessage = 'Admin account cannot be deleted from the database.';
      return;
    }

    if (!confirm(`Are you sure you want to delete member "${name}"? This will also remove attendance and payment records.`)) {
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';

    this.memberService.deleteMember(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          if (this.selectedMemberForModal?.id === id) {
            this.selectedMemberForModal = null;
          }
          this.successMessage = `Member "${name}" deleted successfully!`;
          this.loadMembers();
        },
        error: (err) => {
          this.errorMessage = err.error?.message || 'Failed to delete member';
        }
      });
  }

  isProtectedAdmin(member?: MemberResponse | null): boolean {
    if (!member) return false;
    const email = (member.email || '').trim().toLowerCase();
    const name = (member.name || '').trim().toUpperCase();
    return email === 'admin@gym.com' || name === 'ADMIN';
  }
}
