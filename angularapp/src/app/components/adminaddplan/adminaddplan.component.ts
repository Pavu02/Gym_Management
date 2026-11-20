import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { PlanService } from '../../services/plan.service';

@Component({
  selector: 'app-adminaddplan',
  templateUrl: './adminaddplan.component.html',
  styleUrls: ['./adminaddplan.component.css']
})
export class AdminaddplanComponent implements OnInit {
  planForm!: FormGroup;
  isEditMode: boolean = false;
  planId!: number;
  loading: boolean = false;
  errorMessage: string = '';
  successMessage: string = '';

  constructor(
    private fb: FormBuilder,
    private planService: PlanService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.planForm = this.fb.group({
      name: ['', [Validators.required]],
      durationMonths: [1, [Validators.required, Validators.min(1)]],
      price: [0, [Validators.required, Validators.min(0.01)]],
      description: ['', [Validators.required]]
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEditMode = true;
      this.planId = +id;
      this.loadPlan(this.planId);
    }
  }

  loadPlan(id: number): void {
    this.loading = true;
    this.planService.getPlanById(id).subscribe({
      next: (plan) => {
        this.planForm.patchValue({
          name: plan.name,
          durationMonths: plan.durationMonths,
          price: plan.price,
          description: plan.description
        });
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load plan details';
        this.loading = false;
      }
    });
  }

  onSubmit(): void {
    if (this.planForm.invalid) {
      this.planForm.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';

    const planData = this.planForm.value;

    if (this.isEditMode) {
      this.planService.updatePlan(this.planId, planData).subscribe({
        next: () => {
          this.loading = false;
          this.successMessage = 'Plan updated successfully!';
          setTimeout(() => this.router.navigate(['/admin/plans']), 1000);
        },
        error: (err) => {
          this.loading = false;
          this.errorMessage = err.error?.message || 'Failed to update plan';
        }
      });
    } else {
      this.planService.addPlan(planData).subscribe({
        next: () => {
          this.loading = false;
          this.successMessage = 'Plan added successfully!';
          setTimeout(() => this.router.navigate(['/admin/plans']), 1000);
        },
        error: (err) => {
          this.loading = false;
          this.errorMessage = err.error?.message || 'Failed to add plan';
        }
      });
    }
  }
}
