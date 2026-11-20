import { Component, OnInit } from '@angular/core';
import { MemberService } from '../../services/member.service';
import { TrainerService } from '../../services/trainer.service';
import { MemberResponse } from '../../models/member.model';
import { Trainer } from '../../models/trainer.model';

@Component({
  selector: 'app-membertrainer',
  templateUrl: './membertrainer.component.html',
  styleUrls: ['./membertrainer.component.css']
})
export class MembertrainerComponent implements OnInit {
  profile: MemberResponse | null = null;
  currentTrainer: Trainer | null = null;
  availableTrainers: Trainer[] = [];
  loading: boolean = true;
  submitting: boolean = false;
  showChangeList: boolean = false;
  errorMessage: string = '';
  successMessage: string = '';

  constructor(
    private memberService: MemberService,
    private trainerService: TrainerService
  ) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.errorMessage = '';

    this.memberService.getMyProfile().subscribe({
      next: (profile) => {
        this.profile = profile;
        this.loadTrainers();
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load member profile';
        this.loadTrainers();
      }
    });
  }

  loadTrainers(): void {
    this.trainerService.getAllTrainers().subscribe({
      next: (trainers) => {
        this.availableTrainers = trainers;
        if (this.profile?.trainerId) {
          this.currentTrainer = trainers.find(t => t.id === this.profile?.trainerId) || null;
        } else {
          this.currentTrainer = null;
        }
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  toggleChangeTrainer(): void {
    this.showChangeList = !this.showChangeList;
    this.errorMessage = '';
    this.successMessage = '';
  }

  changeTrainer(trainerId?: number): void {
    if (!trainerId) return;

    this.submitting = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.memberService.changeTrainer(trainerId).subscribe({
      next: (updated) => {
        this.profile = updated;
        this.currentTrainer = this.availableTrainers.find(t => t.id === updated.trainerId) || null;
        this.submitting = false;
        this.showChangeList = false;
        this.successMessage = `Personal trainer updated to ${updated.trainerName}!`;
      },
      error: (err) => {
        this.submitting = false;
        this.errorMessage = err.error?.message || 'Failed to change trainer.';
      }
    });
  }

  removeTrainer(): void {
    if (!confirm('Are you sure you want to remove your personal trainer?')) return;

    this.submitting = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.memberService.removeTrainer().subscribe({
      next: (updated) => {
        this.profile = updated;
        this.currentTrainer = null;
        this.submitting = false;
        this.successMessage = 'Personal trainer removed. You are now training independently.';
      },
      error: (err) => {
        this.submitting = false;
        this.errorMessage = err.error?.message || 'Failed to remove trainer.';
      }
    });
  }

  getInitials(name?: string): string {
    if (!name) return 'TR';
    const parts = name.trim().split(' ');
    if (parts.length > 1) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.substring(0, 2).toUpperCase();
  }
}
