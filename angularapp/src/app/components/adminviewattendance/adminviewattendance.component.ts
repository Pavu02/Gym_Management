import { Component, OnInit } from '@angular/core';
import { FaceService, FaceCheckinResponseDTO } from '../../services/face.service';
import { MemberService } from '../../services/member.service';
import { TrainerService } from '../../services/trainer.service';
import { MemberResponse } from '../../models/member.model';
import { Trainer } from '../../models/trainer.model';

@Component({
  selector: 'app-adminviewattendance',
  templateUrl: './adminviewattendance.component.html',
  styleUrls: ['./adminviewattendance.component.css']
})
export class AdminviewattendanceComponent implements OnInit {
  records: FaceCheckinResponseDTO[] = [];
  todayRecords: FaceCheckinResponseDTO[] = [];

  // Summary Metrics
  totalToday = 0;
  membersPresentToday = 0;
  trainersPresentToday = 0;

  // Filters
  fromDate: string = '';
  toDate: string = '';
  selectedRole: string = 'ALL';
  selectedMethod: string = 'ALL';
  searchName: string = '';

  loading: boolean = true;
  errorMessage: string = '';
  successMessage: string = '';

  // Manual Check-In Modal State
  showManualModal: boolean = false;
  manualLoading: boolean = false;
  availableUsers: { id: number; name: string; role: string; email: string }[] = [];
  selectedManualUserId: number | null = null;

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

  loadTodaySummary(): void {
    this.faceService.getTodayAttendance().subscribe({
      next: (data) => {
        this.todayRecords = data;
        this.totalToday = data.length;
        this.membersPresentToday = data.filter(r => r.role === 'MEMBER').length;
        this.trainersPresentToday = data.filter(r => r.role === 'TRAINER').length;
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
    ).subscribe({
      next: (data) => {
        let filtered = data || [];
        if (this.selectedMethod !== 'ALL') {
          filtered = filtered.filter(r => (r.method || 'FACE').toUpperCase() === this.selectedMethod);
        }
        this.records = filtered;
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load attendance records';
        this.loading = false;
      }
    });
  }

  onFilterChange(): void {
    this.loadAttendance();
  }

  resetFilters(): void {
    this.fromDate = '';
    this.toDate = '';
    this.selectedRole = 'ALL';
    this.selectedMethod = 'ALL';
    this.searchName = '';
    this.loadAttendance();
  }

  loadUserOptions(): void {
    this.availableUsers = [];
    this.memberService.getAllMembers().subscribe({
      next: (members) => {
        for (const m of members) {
          this.availableUsers.push({
            id: m.id,
            name: m.name,
            role: 'MEMBER',
            email: m.email
          });
        }
      }
    });

    this.trainerService.getAllTrainers().subscribe({
      next: (trainers) => {
        for (const t of trainers) {
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

    this.faceService.manualCheckin(this.selectedManualUserId).subscribe({
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
