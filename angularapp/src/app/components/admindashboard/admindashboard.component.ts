import { Component, OnInit } from '@angular/core';
import { DashboardService } from '../../services/dashboard.service';
import { DashboardStats } from '../../models/dashboard.model';
import { FaceService } from '../../services/face.service';

@Component({
  selector: 'app-admindashboard',
  templateUrl: './admindashboard.component.html',
  styleUrls: ['./admindashboard.component.css']
})
export class AdmindashboardComponent implements OnInit {
  stats: DashboardStats | null = null;
  loading: boolean = true;
  errorMessage: string = '';

  // Today's Check-ins by Method
  faceCheckinsToday: number = 0;
  fingerprintCheckinsToday: number = 0;
  manualCheckinsToday: number = 0;

  constructor(
    private dashboardService: DashboardService,
    private faceService: FaceService
  ) {}

  ngOnInit(): void {
    this.loadStats();
    this.loadTodayMethods();
  }

  loadStats(): void {
    this.loading = true;
    this.dashboardService.getStats().subscribe({
      next: (data) => {
        this.stats = data;
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load dashboard statistics';
        this.loading = false;
      }
    });
  }

  loadTodayMethods(): void {
    this.faceService.getTodayAttendance().subscribe({
      next: (records) => {
        const list = records || [];
        this.faceCheckinsToday = list.filter(r => (r.method || 'FACE').toUpperCase() === 'FACE').length;
        this.fingerprintCheckinsToday = list.filter(r => (r.method || '').toUpperCase() === 'FINGERPRINT').length;
        this.manualCheckinsToday = list.filter(r => (r.method || '').toUpperCase() === 'MANUAL').length;
      },
      error: () => {}
    });
  }
}
