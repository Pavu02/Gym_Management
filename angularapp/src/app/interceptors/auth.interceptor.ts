import { Injectable } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
  HttpErrorResponse
} from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  constructor(private authService: AuthService) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const token = this.authService.getToken();

    if (token) {
      request = request.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
    }

    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401 && !request.url.includes('/api/auth/')) {
          const currentPath = typeof window !== 'undefined'
            ? (window.location.pathname + window.location.search)
            : '';
          const preserveReturnUrl = currentPath.startsWith('/check-in')
            || currentPath.startsWith('/checkin')
            || currentPath.startsWith('/member/devices');
          if (!request.url.includes('/revoke-all') && !request.url.includes('/register/options')) {
            this.authService.logout(preserveReturnUrl ? currentPath : undefined);
          }
        }
        return throwError(() => error);
      })
    );
  }
}
