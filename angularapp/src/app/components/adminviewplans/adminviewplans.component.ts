import { Component, OnInit } from '@angular/core';
import { PlanService } from '../../services/plan.service';
import { Plan } from '../../models/plan.model';

@Component({
  selector: 'app-adminviewplans',
  templateUrl: './adminviewplans.component.html',
  styleUrls: ['./adminviewplans.component.css']
})
export class AdminviewplansComponent implements OnInit {
  plans: Plan[] = [];
  filteredPlans: Plan[] = [];
  searchTerm: string = '';
  loading: boolean = true;
  errorMessage: string = '';
  successMessage: string = '';

  constructor(private planService: PlanService) {}

  ngOnInit(): void {
    this.loadPlans();
  }

  loadPlans(): void {
    this.loading = true;
    this.planService.getAllPlans().subscribe({
      next: (data) => {
        this.plans = data;
        this.filterPlans();
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load membership plans';
        this.loading = false;
      }
    });
  }

  filterPlans(): void {
    if (!this.searchTerm.trim()) {
      this.filteredPlans = [...this.plans];
    } else {
      const term = this.searchTerm.toLowerCase();
      this.filteredPlans = this.plans.filter(p =>
        p.name.toLowerCase().includes(term) ||
        p.description.toLowerCase().includes(term) ||
        (p.features && p.features.toLowerCase().includes(term))
      );
    }
  }

  getFeatures(featuresStr?: string): string[] {
    if (!featuresStr) return [];
    return featuresStr.split(',').map(f => f.trim()).filter(f => f.length > 0);
  }

  deletePlan(id: number, name: string): void {
    if (!confirm(`Are you sure you want to delete the plan "${name}"?`)) {
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';

    this.planService.deletePlan(id).subscribe({
      next: () => {
        this.successMessage = `Plan "${name}" deleted successfully!`;
        this.loadPlans();
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to delete plan. It might be assigned to members.';
      }
    });
  }
}
