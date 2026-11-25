import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AttendanceService } from '../../services/attendance.service';
import { MemberService } from '../../services/member.service';
import { FingerprintService } from '../../services/fingerprint.service';
import { Attendance, getAttendanceDate, getCheckInTime, getCheckOutTime } from '../../models/attendance.model';

@Component({
  selector: 'app-memberattendance',
  templateUrl: './memberattendance.component.html',
  styleUrls: ['./memberattendance.component.css']
})
export class MemberattendanceComponent implements OnInit {
  attendanceList: Attendance[] = [];
  todayRecord: Attendance | null = null;
  loading: boolean = true;
  actionLoading: boolean = false;
  errorMessage: string = '';
  successMessage: string = '';
  isExpired: boolean = false;

  // Monthly stats
  visitsThisMonth: number = 0;
  lastCheckInDate: string = '';
  daysLeft: number = 0;

  // Helper methods exposed to template
  getDate = getAttendanceDate;
  getCheckIn = getCheckInTime;
  getCheckOut = getCheckOutTime;

  memberId: number = 0;
  memberName: string = '';

  constructor(
    private attendanceService: AttendanceService,
    private memberService: MemberService,
    private fingerprintService: FingerprintService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.checkMembershipStatus();
    this.loadMyAttendance();
  }

  checkMembershipStatus(): void {
    this.memberService.getMyProfile().subscribe({
      next: (profile) => {
        this.memberId = profile.id;
        this.memberName = profile.name;
        this.daysLeft = profile.daysLeft ?? 0;
        if (profile.status === 'EXPIRED' || (profile.daysLeft !== undefined && profile.daysLeft <= 0)) {
          this.isExpired = true;
        }
      }
    });
  }

  goToRenew(): void {
    this.router.navigate(['/member/plan']);
  }

  loadMyAttendance(): void {
    this.loading = true;
    this.attendanceService.getMyAttendance().subscribe({
      next: (data) => {
        this.attendanceList = data;
        this.computeStats();
        this.checkTodayStatus();
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load attendance records';
        this.loading = false;
      }
    });
  }

  computeStats(): void {
    const currentMonth = new Date().getMonth();
    const currentYear = new Date().getFullYear();

    this.visitsThisMonth = this.attendanceList.filter(a => {
      const dStr = getAttendanceDate(a);
      if (!dStr) return false;
      const d = new Date(dStr);
      return d.getMonth() === currentMonth && d.getFullYear() === currentYear;
    }).length;

    if (this.attendanceList.length > 0) {
      const sorted = [...this.attendanceList].sort((a, b) =>
        (getAttendanceDate(b) || '').localeCompare(getAttendanceDate(a) || '')
      );
      this.lastCheckInDate = getAttendanceDate(sorted[0]);
    }
  }

  checkTodayStatus(): void {
    const today = new Date().toISOString().split('T')[0];
    const match = this.attendanceList.find(a => getAttendanceDate(a) === today);
    this.todayRecord = match || null;
  }

  checkIn(): void {
    this.actionLoading = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.attendanceService.checkInMe().subscribe({
      next: () => {
        this.actionLoading = false;
        this.successMessage = 'Checked in successfully! Have a great workout!';
        this.loadMyAttendance();
      },
      error: (err) => {
        this.actionLoading = false;
        this.errorMessage = err.error?.message || 'Check-in failed';
      }
    });
  }

  checkInWithFingerprint(): void {
    this.actionLoading = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.fingerprintService.checkin({ userId: this.memberId }).subscribe({
      next: (res: any) => {
        this.actionLoading = false;
        this.successMessage = `⚡ Checked in successfully via Biometric Fingerprint! Welcome, ${res.name || this.memberName}!`;
        this.loadMyAttendance();
      },
      error: (err: any) => {
        this.actionLoading = false;
        const msg = err.error?.message || err.message || '';
        if (msg.includes('No fingerprint enrolled')) {
          this.errorMessage = 'No unique fingerprint enrolled yet. Please go to your Profile to register your fingerprint first.';
        } else {
          this.errorMessage = msg || 'Fingerprint biometric check-in failed';
        }
      }
    });
  }

  checkOut(): void {
    this.actionLoading = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.attendanceService.checkOutMe().subscribe({
      next: () => {
        this.actionLoading = false;
        this.successMessage = 'Checked out successfully! See you tomorrow!';
        this.loadMyAttendance();
      },
      error: (err) => {
        this.actionLoading = false;
        this.errorMessage = err.error?.message || 'Check-out failed';
      }
    });
  }
}
