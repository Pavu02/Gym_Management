import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { PlanService } from '../../services/plan.service';
import { Plan } from '../../models/plan.model';

@Component({
  selector: 'app-adminviewplans',
  templateUrl: './adminviewplans.component.html',
  styleUrls: ['./adminviewplans.component.css']
})
export class AdminviewplansComponent implements OnInit, OnDestroy {
  plans: Plan[] = [];
  filteredPlans: Plan[] = [];

  searchTerm = '';
  durationFilter: 'ALL' | 'POPULAR' | 'SHORT' | 'MEDIUM' | 'LONG' = 'ALL';
  sortBy: 'priceAsc' | 'priceDesc' | 'duration' | 'name' = 'priceAsc';
  viewMode: 'cards' | 'table' = 'cards';

  loading = true;
  errorMessage = '';
  successMessage = '';

  // KPI metrics
  popularPlanName = '—';
  startingPrice = 0;
  maxDuration = 0;
  popularCount = 0;

  private destroy$ = new Subject<void>();

  constructor(private planService: PlanService) {}

  ngOnInit(): void {
    this.loadPlans();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadPlans(): void {
    this.loading = true;
    this.planService.getAllPlans()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data) => {
          this.plans = data || [];
          this.computeMetrics();
          this.filterPlans();
          this.loading = false;
        },
        error: (err) => {
          this.errorMessage = err.error?.message || 'Failed to load membership plans';
          this.loading = false;
        }
      });
  }

  private computeMetrics(): void {
    if (!this.plans.length) {
      this.popularPlanName = '—';
      this.startingPrice = 0;
      this.maxDuration = 0;
      this.popularCount = 0;
      return;
    }

    const popular = this.plans.find(p => p.popular);
    this.popularPlanName = popular ? popular.name : this.plans[0].name;
    this.popularCount = this.plans.filter(p => p.popular).length;
    this.startingPrice = Math.min(...this.plans.map(p => p.price || 0));
    this.maxDuration = Math.max(...this.plans.map(p => p.durationMonths || 0));
  }

  filterPlans(): void {
    const q = this.searchTerm.trim().toLowerCase();

    let list = this.plans.filter(p => {
      if (this.durationFilter === 'POPULAR' && !p.popular) return false;
      if (this.durationFilter === 'SHORT' && (p.durationMonths || 0) > 1) return false;
      if (this.durationFilter === 'MEDIUM' && ((p.durationMonths || 0) < 2 || (p.durationMonths || 0) > 6)) return false;
      if (this.durationFilter === 'LONG' && (p.durationMonths || 0) < 7) return false;

      if (!q) return true;
      return (
        (p.name || '').toLowerCase().includes(q) ||
        (p.description || '').toLowerCase().includes(q) ||
        (p.features || '').toLowerCase().includes(q)
      );
    });

    list = [...list].sort((a, b) => {
      switch (this.sortBy) {
        case 'priceDesc':
          return (b.price || 0) - (a.price || 0);
        case 'duration':
          return (b.durationMonths || 0) - (a.durationMonths || 0);
        case 'name':
          return (a.name || '').localeCompare(b.name || '');
        case 'priceAsc':
        default:
          return (a.price || 0) - (b.price || 0);
      }
    });

    this.filteredPlans = list;
  }

  selectFilter(f: 'ALL' | 'POPULAR' | 'SHORT' | 'MEDIUM' | 'LONG'): void {
    this.durationFilter = f;
    this.filterPlans();
  }

  resetAllFilters(): void {
    this.searchTerm = '';
    this.durationFilter = 'ALL';
    this.sortBy = 'priceAsc';
    this.filterPlans();
  }

  getFeatures(featuresStr?: string): string[] {
    if (!featuresStr) return [];
    return featuresStr.split(',').map(f => f.trim()).filter(f => f.length > 0);
  }

  getMonthlyRate(plan: Plan): string {
    const months = plan.durationMonths && plan.durationMonths > 0 ? plan.durationMonths : 1;
    const perMonth = (plan.price || 0) / months;
    return '₹' + Math.round(perMonth).toLocaleString('en-IN');
  }

  formatINR(val?: number): string {
    const safe = val && Number.isFinite(val) ? val : 0;
    return '₹' + safe.toLocaleString('en-IN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
  }

  formatPriceWhole(val?: number): string {
    const safe = val && Number.isFinite(val) ? val : 0;
    return '₹' + Math.round(safe).toLocaleString('en-IN');
  }

  dismissAlerts(): void {
    this.successMessage = '';
    this.errorMessage = '';
  }

  deletePlan(id: number, name: string): void {
    if (!confirm(`Are you sure you want to delete the plan "${name}"?`)) {
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';

    this.planService.deletePlan(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
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
