import { Component, OnInit } from '@angular/core';
import { TrainerService } from '../../services/trainer.service';
import { Trainer } from '../../models/trainer.model';

@Component({
  selector: 'app-adminviewtrainers',
  templateUrl: './adminviewtrainers.component.html',
  styleUrls: ['./adminviewtrainers.component.css']
})
export class AdminviewtrainersComponent implements OnInit {
  trainers: Trainer[] = [];
  loading: boolean = true;
  errorMessage: string = '';
  successMessage: string = '';
  selectedTrainerForFaceEnroll: Trainer | null = null;

  constructor(private trainerService: TrainerService) {}

  ngOnInit(): void {
    this.loadTrainers();
  }

  openFaceEnroll(trainer: Trainer): void {
    this.selectedTrainerForFaceEnroll = trainer;
  }

  closeFaceEnroll(): void {
    this.selectedTrainerForFaceEnroll = null;
  }

  onFaceEnrolled(): void {
    this.successMessage = `Face template registered successfully for ${this.selectedTrainerForFaceEnroll?.user?.name || 'Trainer'}!`;
    this.selectedTrainerForFaceEnroll = null;
  }

  loadTrainers(): void {
    this.loading = true;
    this.trainerService.getAllTrainers().subscribe({
      next: (data) => {
        this.trainers = data;
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load trainers';
        this.loading = false;
      }
    });
  }

  deleteTrainer(id: number, name: string): void {
    if (!confirm(`Are you sure you want to delete trainer "${name}"? Members assigned to this trainer will be unassigned.`)) {
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';

    this.trainerService.deleteTrainer(id).subscribe({
      next: () => {
        this.successMessage = `Trainer "${name}" deleted successfully!`;
        this.loadTrainers();
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to delete trainer';
      }
    });
  }
}
