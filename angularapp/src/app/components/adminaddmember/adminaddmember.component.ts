import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MemberService } from '../../services/member.service';
import { PlanService } from '../../services/plan.service';
import { TrainerService } from '../../services/trainer.service';
import { Plan } from '../../models/plan.model';
import { Trainer } from '../../models/trainer.model';

@Component({
  selector: 'app-adminaddmember',
  templateUrl: './adminaddmember.component.html',
  styleUrls: ['./adminaddmember.component.css']
})
export class AdminaddmemberComponent implements OnInit {
  memberForm!: FormGroup;
  isEditMode: boolean = false;
  memberId!: number;
  plans: Plan[] = [];
  trainers: Trainer[] = [];
  loading: boolean = false;
  errorMessage: string = '';
  successMessage: string = '';

  constructor(
    private fb: FormBuilder,
    private memberService: MemberService,
    private planService: PlanService,
    private trainerService: TrainerService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEditMode = true;
      this.memberId = +id;
    }

    this.initForm();
    this.loadDropdownData();

    if (this.isEditMode) {
      this.loadMember(this.memberId);
    }
  }

  initForm(): void {
    if (this.isEditMode) {
      this.memberForm = this.fb.group({
        name: ['', [Validators.required]],
        phone: ['', [Validators.required, Validators.pattern('^[0-9]{10}$')]],
        planId: [null, [Validators.required]],
        trainerId: [null]
      });
    } else {
      this.memberForm = this.fb.group({
        name: ['', [Validators.required]],
        email: ['', [Validators.required, Validators.email]],
        password: ['', [Validators.required, Validators.minLength(6)]],
        phone: ['', [Validators.required, Validators.pattern('^[0-9]{10}$')]],
        planId: [null, [Validators.required]],
        trainerId: [null]
      });
    }
  }

  loadDropdownData(): void {
    this.planService.getAllPlans().subscribe({
      next: (plans) => (this.plans = plans),
      error: () => {}
    });

    this.trainerService.getAllTrainers().subscribe({
      next: (trainers) => (this.trainers = trainers),
      error: () => {}
    });
  }

  loadMember(id: number): void {
    this.loading = true;
    this.memberService.getMemberById(id).subscribe({
      next: (member) => {
        this.memberForm.patchValue({
          name: member.name,
          phone: member.phone
        });

        // Set matching planId by planName
        const matchingPlan = this.plans.find(p => p.name === member.planName);
        if (matchingPlan) {
          this.memberForm.patchValue({ planId: matchingPlan.id });
        }

        // Set matching trainerId
        if (member.trainerName) {
          const matchingTrainer = this.trainers.find(t => t.user?.name === member.trainerName);
          if (matchingTrainer) {
            this.memberForm.patchValue({ trainerId: matchingTrainer.id });
          }
        }

        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load member';
        this.loading = false;
      }
    });
  }

  onSubmit(): void {
    if (this.memberForm.invalid) {
      this.memberForm.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';

    const formVal = this.memberForm.value;
    const trainerIdVal = formVal.trainerId ? +formVal.trainerId : null;
    const planIdVal = +formVal.planId;

    const payload = {
      ...formVal,
      planId: planIdVal,
      trainerId: trainerIdVal
    };

    if (this.isEditMode) {
      this.memberService.updateMember(this.memberId, payload).subscribe({
        next: () => {
          this.loading = false;
          this.successMessage = 'Member updated successfully!';
          setTimeout(() => this.router.navigate(['/admin/members']), 1000);
        },
        error: (err) => {
          this.loading = false;
          this.errorMessage = err.error?.message || 'Failed to update member';
        }
      });
    } else {
      this.memberService.addMember(payload).subscribe({
        next: () => {
          this.loading = false;
          this.successMessage = 'Member added successfully!';
          setTimeout(() => this.router.navigate(['/admin/members']), 1000);
        },
        error: (err) => {
          this.loading = false;
          this.errorMessage = err.error?.message || 'Failed to add member';
        }
      });
    }
  }
}
