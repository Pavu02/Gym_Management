import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { OnboardingService } from '../../services/onboarding.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {
  loginForm!: FormGroup;
  errorMessage: string = '';
  successMessage: string = '';
  loading: boolean = false;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private onboardingService: OnboardingService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    if (this.authService.isLoggedIn()) {
      this.redirectByRole(this.authService.getRole());
    }

    this.route.queryParams.subscribe(params => {
      if (params['registered']) {
        this.successMessage = 'Registration successful! Please login with your credentials.';
      }
    });

    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required]]
    });
  }

  onSubmit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    this.authService.login(this.loginForm.value).subscribe({
      next: (res) => {
        this.loading = false;
        if (res.role === 'MEMBER') {
          this.onboardingService.getStatus().subscribe({
            next: (status) => {
              if (status && status.onboardingCompleted) {
                this.router.navigate(['/member/profile']);
              } else {
                this.router.navigate(['/onboarding/plans']);
              }
            },
            error: () => {
              this.router.navigate(['/onboarding/plans']);
            }
          });
        } else {
          this.redirectByRole(res.role);
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Login failed. Please check your credentials.';
      }
    });
  }

  private redirectByRole(role: string | null): void {
    if (role === 'ADMIN') {
      this.router.navigate(['/admin/dashboard']);
    } else if (role === 'TRAINER') {
      this.router.navigate(['/trainer/members']);
    } else if (role === 'MEMBER') {
      this.onboardingService.getStatus().subscribe({
        next: (status) => {
          if (status && status.onboardingCompleted) {
            this.router.navigate(['/member/profile']);
          } else {
            this.router.navigate(['/onboarding/plans']);
          }
        },
        error: () => {
          this.router.navigate(['/onboarding/plans']);
        }
      });
    } else {
      this.router.navigate(['/home']);
    }
  }
}
