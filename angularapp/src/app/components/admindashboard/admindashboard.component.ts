import { Component, OnInit } from '@angular/core';
import { DashboardService } from '../../services/dashboard.service';
import { DashboardStats } from '../../models/dashboard.model';

@Component({
  selector: 'app-admindashboard',
  templateUrl: './admindashboard.component.html',
  styleUrls: ['./admindashboard.component.css']
})
export class AdmindashboardComponent implements OnInit {
  stats: DashboardStats | null = null;
  loading: boolean = true;
  errorMessage: string = '';

  constructor(private dashboardService: DashboardService) {}

  ngOnInit(): void {
    this.loadStats();
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
}
