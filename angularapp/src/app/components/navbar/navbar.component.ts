import { Component } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-navbar',
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.css']
})
export class NavbarComponent {
  isOnboarding: boolean = false;

  constructor(private authService: AuthService, private router: Router) {
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: any) => {
      this.isOnboarding = event.urlAfterRedirects ? event.urlAfterRedirects.startsWith('/onboarding') : event.url.startsWith('/onboarding');
    });
  }

  isLoggedIn(): boolean {
    return this.authService.isLoggedIn();
  }

  getRole(): string | null {
    return this.authService.getRole();
  }

  getName(): string | null {
    return this.authService.getName();
  }

  logout(): void {
    this.authService.logout();
  }
}
