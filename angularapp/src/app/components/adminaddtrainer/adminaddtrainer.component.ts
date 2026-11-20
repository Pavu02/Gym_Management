import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TrainerService } from '../../services/trainer.service';

@Component({
  selector: 'app-adminaddtrainer',
  templateUrl: './adminaddtrainer.component.html',
  styleUrls: ['./adminaddtrainer.component.css']
})
export class AdminaddtrainerComponent implements OnInit {
  trainerForm!: FormGroup;
  isEditMode: boolean = false;
  trainerId!: number;
  loading: boolean = false;
  errorMessage: string = '';
  successMessage: string = '';

  constructor(
    private fb: FormBuilder,
    private trainerService: TrainerService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEditMode = true;
      this.trainerId = +id;
    }

    this.initForm();

    if (this.isEditMode) {
      this.loadTrainer(this.trainerId);
    }
  }

  initForm(): void {
    if (this.isEditMode) {
      this.trainerForm = this.fb.group({
        name: ['', [Validators.required]],
        phone: ['', [Validators.required, Validators.pattern('^[0-9]{10}$')]],
        specialization: ['', [Validators.required]],
        experienceYears: [1, [Validators.required, Validators.min(0)]],
        bio: [''],
        rating: [4.5, [Validators.min(1), Validators.max(5)]],
        monthlyFee: [0, [Validators.min(0)]]
      });
    } else {
      this.trainerForm = this.fb.group({
        name: ['', [Validators.required]],
        email: ['', [Validators.required, Validators.email]],
        password: ['', [Validators.required, Validators.minLength(6)]],
        phone: ['', [Validators.required, Validators.pattern('^[0-9]{10}$')]],
        specialization: ['', [Validators.required]],
        experienceYears: [1, [Validators.required, Validators.min(0)]],
        bio: [''],
        rating: [4.5, [Validators.min(1), Validators.max(5)]],
        monthlyFee: [0, [Validators.min(0)]]
      });
    }
  }

  loadTrainer(id: number): void {
    this.loading = true;
    this.trainerService.getTrainerById(id).subscribe({
      next: (trainer) => {
        this.trainerForm.patchValue({
          name: trainer.user?.name,
          phone: trainer.user?.phone,
          specialization: trainer.specialization,
          experienceYears: trainer.experienceYears,
          bio: trainer.bio || '',
          rating: trainer.rating || 4.5,
          monthlyFee: trainer.monthlyFee || 0
        });
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load trainer';
        this.loading = false;
      }
    });
  }

  onSubmit(): void {
    if (this.trainerForm.invalid) {
      this.trainerForm.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';

    const payload = this.trainerForm.value;

    if (this.isEditMode) {
      this.trainerService.updateTrainer(this.trainerId, payload).subscribe({
        next: () => {
          this.loading = false;
          this.successMessage = 'Trainer updated successfully!';
          setTimeout(() => this.router.navigate(['/admin/trainers']), 1000);
        },
        error: (err) => {
          this.loading = false;
          this.errorMessage = err.error?.message || 'Failed to update trainer';
        }
      });
    } else {
      this.trainerService.addTrainer(payload).subscribe({
        next: () => {
          this.loading = false;
          this.successMessage = 'Trainer added successfully!';
          setTimeout(() => this.router.navigate(['/admin/trainers']), 1000);
        },
        error: (err) => {
          this.loading = false;
          this.errorMessage = err.error?.message || 'Failed to add trainer';
        }
      });
    }
  }
}
