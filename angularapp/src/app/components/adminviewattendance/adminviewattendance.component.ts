import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { FaceService, FaceCheckinResponseDTO } from '../../services/face.service';
import { MemberService } from '../../services/member.service';
import { TrainerService } from '../../services/trainer.service';
import { DayAttendance, PeakHour } from '../../services/dashboard.service';
import { DonutSegment } from '../donutchart/donutchart.component';

@Component({
  selector: 'app-adminviewattendance',
  templateUrl: './adminviewattendance.component.html',
  styleUrls: ['./adminviewattendance.component.css']
})
export class AdminviewattendanceComponent implements OnInit, OnDestroy {
  records: FaceCheckinResponseDTO[] = [];
  todayRecords: FaceCheckinResponseDTO[] = [];

  // Summary Metrics
  totalToday = 0;
  membersPresentToday = 0;
  trainersPresentToday = 0;
  biometricRate = 0;

  // Interactive Chart Data (derived reactively from records)
  chartDailyAttendance: DayAttendance[] = [];
  methodDonutSegs: DonutSegment[] = [];
  roleDonutSegs: DonutSegment[] = [];
  peakHourStats: PeakHour[] = [];

  // Filters & View State
  activePreset: 'TODAY' | '7D' | '30D' | 'ALL' = 'ALL';
  fromDate = '';
  toDate = '';
  selectedRole: 'ALL' | 'MEMBER' | 'TRAINER' = 'ALL';
  selectedMethod: 'ALL' | 'FACE' | 'FINGERPRINT' | 'MANUAL' = 'ALL';
  searchName = '';
  viewMode: 'table' | 'cards' = 'table';

  loading = true;
  errorMessage = '';
  successMessage = '';

  // Manual Check-In Modal State
  showManualModal = false;
  manualLoading = false;
  availableUsers: { id: number; name: string; role: string; email: string }[] = [];
  selectedManualUserId: number | null = null;

  private destroy$ = new Subject<void>();

  constructor(
    private faceService: FaceService,
    private memberService: MemberService,
    private trainerService: TrainerService
  ) {}

  ngOnInit(): void {
    this.loadTodaySummary();
    this.loadAttendance();
    this.loadUserOptions();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadTodaySummary(): void {
    this.faceService.getTodayAttendance()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data) => {
          const list = data || [];
          this.todayRecords = list;
          this.totalToday = list.length;
          this.membersPresentToday = list.filter(r => r.role === 'MEMBER').length;
          this.trainersPresentToday = list.filter(r => r.role === 'TRAINER').length;
        },
        error: () => {}
      });
  }

  loadAttendance(): void {
    this.loading = true;
    this.faceService.getAllAttendance(
      this.fromDate || undefined,
      this.toDate || undefined,
      this.selectedRole !== 'ALL' ? this.selectedRole : undefined,
      this.searchName || undefined
    )
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data) => {
          let filtered = data || [];
          if (this.selectedMethod !== 'ALL') {
            filtered = filtered.filter(
              r => (r.method || 'FACE').toUpperCase() === this.selectedMethod
            );
          }
          this.records = filtered;
          this.rebuildCharts(filtered);
          this.loading = false;
        },
        error: (err) => {
          this.errorMessage = err.error?.message || 'Failed to load attendance records';
          this.loading = false;
        }
      });
  }

  private rebuildCharts(list: FaceCheckinResponseDTO[]): void {
    // 1. Biometric verification share %
    const faceCount = list.filter(r => (r.method || 'FACE').toUpperCase() === 'FACE').length;
    const fpCount = list.filter(r => (r.method || '').toUpperCase() === 'FINGERPRINT').length;
    const manualCount = list.filter(r => (r.method || '').toUpperCase() === 'MANUAL').length;
    const bioTotal = faceCount + fpCount;
    this.biometricRate = list.length > 0 ? Math.round((bioTotal / list.length) * 100) : 0;

    // 2. Method Donut
    this.methodDonutSegs = [
      { label: 'Face', value: faceCount, color: '#8B5CF6' },
      { label: 'Fingerprint', value: fpCount, color: '#FF5A00' },
      { label: 'Manual', value: manualCount, color: '#6B7280' }
    ].filter(s => s.value > 0);

    // 3. Role Donut
    const memberCnt = list.filter(r => (r.role || '').toUpperCase() === 'MEMBER').length;
    const trainerCnt = list.filter(r => (r.role || '').toUpperCase() === 'TRAINER').length;
    this.roleDonutSegs = [
      { label: 'Members', value: memberCnt, color: '#22C55E' },
      { label: 'Trainers', value: trainerCnt, color: '#38BDF8' }
    ].filter(s => s.value > 0);

    // 4. Last 7 Days Bar Chart (from checkInTime)
    const today = new Date();
    const dateCounts = new Map<string, number>();
    for (const r of list) {
      if (!r.checkInTime) continue;
      const d = new Date(r.checkInTime);
      if (isNaN(d.getTime())) continue;
      const key = `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
      dateCounts.set(key, (dateCounts.get(key) || 0) + 1);
    }

    const daily: DayAttendance[] = [];
    for (let i = 6; i >= 0; i--) {
      const day = new Date(today);
      day.setDate(today.getDate() - i);
      const key = `${day.getFullYear()}-${day.getMonth()}-${day.getDate()}`;
      const label = day.toLocaleDateString('en-US', { weekday: 'short' });
      daily.push({ label, count: dateCounts.get(key) || 0 });
    }
    this.chartDailyAttendance = daily;

    // 5. Peak Hours Heatmap (5..23)
    const hourCounts = new Map<number, number>();
    for (const r of list) {
      if (!r.checkInTime) continue;
      const d = new Date(r.checkInTime);
      if (isNaN(d.getTime())) continue;
      const hr = d.getHours();
      hourCounts.set(hr, (hourCounts.get(hr) || 0) + 1);
    }
    const hours: PeakHour[] = [];
    for (let h = 5; h <= 23; h++) {
      hours.push({ hour: h, count: hourCounts.get(h) || 0 });
    }
    this.peakHourStats = hours;
  }

  applyPreset(preset: 'TODAY' | '7D' | '30D' | 'ALL'): void {
    this.activePreset = preset;
    const now = new Date();
    const fmt = (d: Date) => d.toISOString().slice(0, 10);

    if (preset === 'TODAY') {
      const todayStr = fmt(now);
      this.fromDate = todayStr;
      this.toDate = todayStr;
    } else if (preset === '7D') {
      const start = new Date(now);
      start.setDate(now.getDate() - 6);
      this.fromDate = fmt(start);
      this.toDate = fmt(now);
    } else if (preset === '30D') {
      const start = new Date(now);
      start.setDate(now.getDate() - 29);
      this.fromDate = fmt(start);
      this.toDate = fmt(now);
    } else {
      this.fromDate = '';
      this.toDate = '';
    }
    this.loadAttendance();
  }

  selectRole(role: 'ALL' | 'MEMBER' | 'TRAINER'): void {
    this.selectedRole = role;
    this.loadAttendance();
  }

  selectMethod(method: 'ALL' | 'FACE' | 'FINGERPRINT' | 'MANUAL'): void {
    this.selectedMethod = method;
    this.loadAttendance();
  }

  setViewMode(mode: 'table' | 'cards'): void {
    this.viewMode = mode;
  }

  onDateOrSearchChange(): void {
    if (this.fromDate || this.toDate) {
      this.activePreset = 'ALL';
    }
    this.loadAttendance();
  }

  resetFilters(): void {
    this.activePreset = 'ALL';
    this.fromDate = '';
    this.toDate = '';
    this.selectedRole = 'ALL';
    this.selectedMethod = 'ALL';
    this.searchName = '';
    this.loadAttendance();
  }

  getInitials(name?: string): string {
    if (!name || !name.trim()) return 'BX';
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  exportCsv(): void {
    if (!this.records.length) return;
    const escape = (val: string) => {
      const s = (val || '').replace(/"/g, '""');
      return s.includes(',') || s.includes('"') ? `"${s}"` : s;
    };
    const header = 'Name,Email/Phone,Role,Check-In DateTime,Method,Status\n';
    const rows = this.records.map(r =>
      [
        escape(r.name),
        escape(r.email || r.phone || ''),
        escape(r.role || ''),
        escape(r.checkInTime || ''),
        escape((r.method || 'FACE').toUpperCase()),
        escape(r.status || 'Present')
      ].join(',')
    ).join('\n');

    const blob = new Blob([header + rows], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `beastx-attendance-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  dismissAlerts(): void {
    this.successMessage = '';
    this.errorMessage = '';
  }

  loadUserOptions(): void {
    this.availableUsers = [];
    this.memberService.getAllMembers()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (members) => {
          for (const m of members || []) {
            this.availableUsers.push({
              id: m.id,
              name: m.name,
              role: 'MEMBER',
              email: m.email
            });
          }
        }
      });

    this.trainerService.getAllTrainers()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (trainers) => {
          for (const t of trainers || []) {
            if (t.user) {
              this.availableUsers.push({
                id: t.user.id || t.id!,
                name: t.user.name,
                role: 'TRAINER',
                email: t.user.email
              });
            }
          }
        }
      });
  }

  openManualModal(): void {
    this.showManualModal = true;
    this.selectedManualUserId = null;
    this.errorMessage = '';
    this.successMessage = '';
  }

  closeManualModal(): void {
    this.showManualModal = false;
    this.selectedManualUserId = null;
  }

  submitManualCheckin(): void {
    if (!this.selectedManualUserId) {
      return;
    }

    this.manualLoading = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.faceService.manualCheckin(this.selectedManualUserId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (res) => {
          this.manualLoading = false;
          this.closeManualModal();
          if (res.status === 'CHECKED_IN') {
            this.successMessage = `Attendance marked manually for ${res.name}!`;
          } else if (res.status === 'ALREADY_CHECKED_IN') {
            this.successMessage = `${res.name} was already marked present today.`;
          } else {
            this.errorMessage = `Could not mark attendance: ${res.status}`;
          }
          this.loadTodaySummary();
          this.loadAttendance();
        },
        error: (err) => {
          this.manualLoading = false;
          this.errorMessage = err.error?.message || 'Manual check-in failed';
        }
      });
  }
}
