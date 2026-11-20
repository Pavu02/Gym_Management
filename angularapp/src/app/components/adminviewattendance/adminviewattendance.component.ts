import { Component, OnInit } from '@angular/core';
import { AttendanceService } from '../../services/attendance.service';
import { Attendance } from '../../models/attendance.model';

@Component({
  selector: 'app-adminviewattendance',
  templateUrl: './adminviewattendance.component.html',
  styleUrls: ['./adminviewattendance.component.css']
})
export class AdminviewattendanceComponent implements OnInit {
  attendanceRecords: Attendance[] = [];
  filteredRecords: Attendance[] = [];
  selectedDate: string = '';
  loading: boolean = true;
  errorMessage: string = '';

  constructor(private attendanceService: AttendanceService) {}

  ngOnInit(): void {
    this.loadAttendance();
  }

  loadAttendance(): void {
    this.loading = true;
    this.attendanceService.getAllAttendance().subscribe({
      next: (data) => {
        this.attendanceRecords = data;
        this.filterByDate();
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load attendance records';
        this.loading = false;
      }
    });
  }

  filterByDate(): void {
    if (!this.selectedDate) {
      this.filteredRecords = [...this.attendanceRecords];
    } else {
      this.filteredRecords = this.attendanceRecords.filter(a => a.date === this.selectedDate);
    }
  }

  resetFilter(): void {
    this.selectedDate = '';
    this.filterByDate();
  }
}
