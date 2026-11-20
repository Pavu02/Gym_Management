import { Component, OnInit } from '@angular/core';
import { AttendanceService } from '../../services/attendance.service';
import { Attendance } from '../../models/attendance.model';

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

  constructor(private attendanceService: AttendanceService) {}

  ngOnInit(): void {
    this.loadMyAttendance();
  }

  loadMyAttendance(): void {
    this.loading = true;
    this.attendanceService.getMyAttendance().subscribe({
      next: (data) => {
        this.attendanceList = data;
        this.checkTodayStatus();
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load attendance records';
        this.loading = false;
      }
    });
  }

  checkTodayStatus(): void {
    const today = new Date().toISOString().split('T')[0];
    const match = this.attendanceList.find(a => a.date === today);
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
