import { Component, OnInit } from '@angular/core';
import { MemberService } from '../../services/member.service';
import { MemberResponse } from '../../models/member.model';

@Component({
  selector: 'app-memberprofile',
  templateUrl: './memberprofile.component.html',
  styleUrls: ['./memberprofile.component.css']
})
export class MemberprofileComponent implements OnInit {
  profile: MemberResponse | null = null;
  daysLeft: number = 0;
  loading: boolean = true;
  errorMessage: string = '';

  constructor(private memberService: MemberService) {}

  ngOnInit(): void {
    this.loadProfile();
  }

  loadProfile(): void {
    this.loading = true;
    this.memberService.getMyProfile().subscribe({
      next: (data) => {
        this.profile = data;
        this.calculateDaysLeft();
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load member profile';
        this.loading = false;
      }
    });
  }

  calculateDaysLeft(): void {
    if (!this.profile?.expiryDate) {
      this.daysLeft = 0;
      return;
    }
    const expiry = new Date(this.profile.expiryDate).getTime();
    const today = new Date().getTime();
    const diffDays = Math.ceil((expiry - today) / (1000 * 60 * 60 * 24));
    this.daysLeft = diffDays > 0 ? diffDays : 0;
  }
}
