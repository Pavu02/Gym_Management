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
  returnUrl: string | null = null;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private onboardingService: OnboardingService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    const rawReturnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    if (rawReturnUrl && rawReturnUrl.startsWith('/') && !rawReturnUrl.startsWith('//')) {
      this.returnUrl = rawReturnUrl;
    }

    if (this.authService.isLoggedIn()) {
      this.redirectAfterAuth(this.authService.getRole());
    }

    this.route.queryParams.subscribe(params => {
      if (params['registered']) {
        this.successMessage = 'Registration successful! Please login with your credentials.';
      }
      if (params['deactivated']) {
        this.errorMessage = 'Your account has been deactivated. Please contact the gym administrator.';
      }
      if (params['returnUrl'] && params['returnUrl'].startsWith('/') && !params['returnUrl'].startsWith('//')) {
        this.returnUrl = params['returnUrl'];
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
        this.redirectAfterAuth(res.role);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Login failed. Please check your credentials.';
      }
    });
  }

  private redirectAfterAuth(role: string | null): void {
    if (this.returnUrl) {
      this.router.navigateByUrl(this.returnUrl);
      return;
    }
    this.redirectByRole(role);
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
