import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { TrainerService } from '../../services/trainer.service';
import { Trainer } from '../../models/trainer.model';

export interface SpecChip {
  name: string;
  count: number;
}

@Component({
  selector: 'app-adminviewtrainers',
  templateUrl: './adminviewtrainers.component.html',
  styleUrls: ['./adminviewtrainers.component.css']
})
export class AdminviewtrainersComponent implements OnInit, OnDestroy {
  trainers: Trainer[] = [];
  filteredTrainers: Trainer[] = [];
  specializations: SpecChip[] = [];

  loading = true;
  errorMessage = '';
  successMessage = '';
  copiedText = '';

  searchTerm = '';
  selectedSpec = 'ALL';
  sortBy: 'rating' | 'experience' | 'feeAsc' | 'feeDesc' | 'name' = 'rating';
  viewMode: 'cards' | 'table' = 'cards';

  selectedTrainerForFaceEnroll: Trainer | null = null;
  selectedTrainerForModal: Trainer | null = null;

  // Roster KPI aggregates
  avgRating = 0;
  avgExperience = 0;
  avgMonthlyFee = 0;

  private destroy$ = new Subject<void>();
  private copyTimer?: any;

  constructor(private trainerService: TrainerService) {}

  ngOnInit(): void {
    this.loadTrainers();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    if (this.copyTimer) clearTimeout(this.copyTimer);
  }

  loadTrainers(): void {
    this.loading = true;
    this.trainerService.getAllTrainers()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data) => {
          this.trainers = data || [];
          this.computeRosterMetrics();
          this.applyFilters();
          this.loading = false;
        },
        error: (err) => {
          this.errorMessage = err.error?.message || 'Failed to load trainers';
          this.loading = false;
        }
      });
  }

  private computeRosterMetrics(): void {
    const count = this.trainers.length;
    if (count === 0) {
      this.avgRating = 0;
      this.avgExperience = 0;
      this.avgMonthlyFee = 0;
      this.specializations = [];
      return;
    }

    const ratingSum = this.trainers.reduce((sum, t) => sum + (t.rating ?? 4.5), 0);
    const expSum = this.trainers.reduce((sum, t) => sum + (t.experienceYears ?? 0), 0);
    const feeSum = this.trainers.reduce((sum, t) => sum + (t.monthlyFee ?? 0), 0);

    this.avgRating = Math.round((ratingSum / count) * 10) / 10;
    this.avgExperience = Math.round((expSum / count) * 10) / 10;
    this.avgMonthlyFee = Math.round(feeSum / count);

    const specMap = new Map<string, number>();
    for (const t of this.trainers) {
      const spec = (t.specialization || 'General Coaching').trim();
      specMap.set(spec, (specMap.get(spec) || 0) + 1);
    }
    this.specializations = Array.from(specMap.entries())
      .map(([name, cnt]) => ({ name, count: cnt }))
      .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name));
  }

  applyFilters(): void {
    const q = this.searchTerm.trim().toLowerCase();

    let list = this.trainers.filter(t => {
      const spec = (t.specialization || 'General Coaching').trim();
      if (this.selectedSpec !== 'ALL' && spec !== this.selectedSpec) {
        return false;
      }
      if (!q) return true;
      const name = (t.user?.name || '').toLowerCase();
      const email = (t.user?.email || '').toLowerCase();
      const phone = (t.user?.phone || '').toLowerCase();
      const bio = (t.bio || '').toLowerCase();
      return (
        name.includes(q) ||
        email.includes(q) ||
        phone.includes(q) ||
        spec.toLowerCase().includes(q) ||
        bio.includes(q)
      );
    });

    list = [...list].sort((a, b) => {
      switch (this.sortBy) {
        case 'rating':
          return (b.rating ?? 4.5) - (a.rating ?? 4.5);
        case 'experience':
          return (b.experienceYears ?? 0) - (a.experienceYears ?? 0);
        case 'feeAsc':
          return (a.monthlyFee ?? 0) - (b.monthlyFee ?? 0);
        case 'feeDesc':
          return (b.monthlyFee ?? 0) - (a.monthlyFee ?? 0);
        case 'name':
          return (a.user?.name || '').localeCompare(b.user?.name || '');
        default:
          return 0;
      }
    });

    this.filteredTrainers = list;
  }

  selectSpec(spec: string): void {
    this.selectedSpec = spec;
    this.applyFilters();
  }

  clearSearch(): void {
    this.searchTerm = '';
    this.applyFilters();
  }

  resetAllFilters(): void {
    this.searchTerm = '';
    this.selectedSpec = 'ALL';
    this.sortBy = 'rating';
    this.applyFilters();
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

  getCoachTier(trainer: Trainer): string {
    const rating = trainer.rating ?? 4.5;
    const exp = trainer.experienceYears ?? 0;
    if (rating >= 4.8 || exp >= 6) return 'ELITE COACH';
    if (exp >= 3 || rating >= 4.6) return 'SENIOR COACH';
    return 'PRO COACH';
  }

  getTierClass(trainer: Trainer): string {
    const rating = trainer.rating ?? 4.5;
    const exp = trainer.experienceYears ?? 0;
    if (rating >= 4.8 || exp >= 6) return 'tier-elite';
    if (exp >= 3 || rating >= 4.6) return 'tier-senior';
    return 'tier-pro';
  }

  getStars(rating?: number): boolean[] {
    const val = Math.round(rating ?? 4.5);
    return [1, 2, 3, 4, 5].map(n => n <= val);
  }

  formatFee(fee?: number): string {
    const val = fee ?? 0;
    return '₹' + val.toLocaleString('en-IN', { maximumFractionDigits: 0 });
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

  openProfileModal(trainer: Trainer): void {
    this.selectedTrainerForModal = trainer;
  }

  closeProfileModal(): void {
    this.selectedTrainerForModal = null;
  }

  openFaceEnroll(trainer: Trainer): void {
    this.selectedTrainerForModal = null;
    this.selectedTrainerForFaceEnroll = trainer;
  }

  closeFaceEnroll(): void {
    this.selectedTrainerForFaceEnroll = null;
  }

  onFaceEnrolled(): void {
    this.successMessage = `Face template registered successfully for ${this.selectedTrainerForFaceEnroll?.user?.name || 'Trainer'}!`;
    this.selectedTrainerForFaceEnroll = null;
  }

  dismissAlerts(): void {
    this.successMessage = '';
    this.errorMessage = '';
  }

  deleteTrainer(id: number, name: string): void {
    if (!confirm(`Are you sure you want to delete trainer "${name}"? Members assigned to this trainer will be unassigned.`)) {
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';

    this.trainerService.deleteTrainer(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          if (this.selectedTrainerForModal?.id === id) {
            this.selectedTrainerForModal = null;
          }
          this.successMessage = `Trainer "${name}" deleted successfully!`;
          this.loadTrainers();
        },
        error: (err) => {
          this.errorMessage = err.error?.message || 'Failed to delete trainer';
        }
      });
  }
}
