import { Component } from '@angular/core';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-navbar',
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.css']
})
export class NavbarComponent {

  constructor(private authService: AuthService) {}

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
