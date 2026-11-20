import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { OnboardingService } from '../services/onboarding.service';
import { AuthService } from '../services/auth.service';
import { map, catchError, of } from 'rxjs';

export const onboardingGuard: CanActivateFn = (route, state) => {
  const onboardingService = inject(OnboardingService);
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isLoggedIn()) {
    router.navigate(['/login']);
    return false;
  }

  const role = authService.getRole();
  if (role !== 'MEMBER') {
    router.navigate(['/error']);
    return false;
  }

  const url = state.url;

  return onboardingService.getStatus().pipe(
    map((status) => {
      const isCompleted = status.onboardingCompleted;

      if (url.startsWith('/onboarding')) {
        if (isCompleted) {
          router.navigate(['/member/profile']);
          return false;
        }
        return true;
      }

      if (url.startsWith('/member')) {
        if (!isCompleted) {
          router.navigate(['/onboarding/plans']);
          return false;
        }
        return true;
      }

      return true;
    }),
    catchError(() => {
      if (url.startsWith('/member')) {
        router.navigate(['/onboarding/plans']);
        return of(false);
      }
      return of(true);
    })
  );
};
