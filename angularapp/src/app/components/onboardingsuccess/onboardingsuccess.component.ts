import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MemberService } from '../../services/member.service';
import { AuthService } from '../../services/auth.service';
import { MemberResponse } from '../../models/member.model';

@Component({
  selector: 'app-onboardingsuccess',
  templateUrl: './onboardingsuccess.component.html',
  styleUrls: ['./onboardingsuccess.component.css']
})
export class OnboardingsuccessComponent implements OnInit {
  member: MemberResponse | null = null;
  userName: string = '';

  constructor(
    private memberService: MemberService,
    private authService: AuthService,
    private router: Router
  ) {
    const nav = this.router.getCurrentNavigation();
    if (nav?.extras?.state?.['member']) {
      this.member = nav.extras.state['member'];
    }
  }

  ngOnInit(): void {
    this.userName = this.authService.getName() || 'Member';
    if (!this.member) {
      this.memberService.getMyProfile().subscribe({
        next: (data) => {
          this.member = data;
        },
        error: () => {
          // If already active, still show success
        }
      });
    }
  }

  goToDashboard(): void {
    this.router.navigate(['/member/profile']);
  }
}
