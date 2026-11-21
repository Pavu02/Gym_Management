import { Component, OnInit } from '@angular/core';
import { MemberService } from '../../services/member.service';
import { AttendanceService } from '../../services/attendance.service';
import { MemberResponse } from '../../models/member.model';

@Component({
  selector: 'app-adminviewmembers',
  templateUrl: './adminviewmembers.component.html',
  styleUrls: ['./adminviewmembers.component.css']
})
export class AdminviewmembersComponent implements OnInit {
  members: MemberResponse[] = [];
  filteredMembers: MemberResponse[] = [];
  searchTerm: string = '';
  statusFilter: string = 'ALL';
  loading: boolean = true;
  errorMessage: string = '';
  successMessage: string = '';
  selectedMemberForModal: MemberResponse | null = null;
  selectedMemberForFaceEnroll: MemberResponse | null = null;

  constructor(
    private memberService: MemberService,
    private attendanceService: AttendanceService
  ) {}

  ngOnInit(): void {
    this.loadMembers();
  }

  loadMembers(): void {
    this.loading = true;
    this.memberService.getAllMembers().subscribe({
      next: (data) => {
        this.members = data;
        this.applyFilter();
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load members';
        this.loading = false;
      }
    });
  }

  applyFilter(): void {
    this.filteredMembers = this.members.filter(m => {
      const matchSearch =
        m.name.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        m.email.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        (m.phone && m.phone.includes(this.searchTerm));

      const matchStatus =
        this.statusFilter === 'ALL' || m.status.toUpperCase() === this.statusFilter.toUpperCase();

      return matchSearch && matchStatus;
    });
  }

  openDetailsModal(member: MemberResponse): void {
    this.selectedMemberForModal = member;
  }

  closeDetailsModal(): void {
    this.selectedMemberForModal = null;
  }

  openFaceEnroll(member: MemberResponse): void {
    this.selectedMemberForFaceEnroll = member;
  }

  closeFaceEnroll(): void {
    this.selectedMemberForFaceEnroll = null;
  }

  onFaceEnrolled(): void {
    this.successMessage = `Face template registered successfully for ${this.selectedMemberForFaceEnroll?.name}!`;
    this.selectedMemberForFaceEnroll = null;
  }

  getAge(dob?: string): number | string {
    if (!dob) return 'N/A';
    const birth = new Date(dob);
    const now = new Date();
    let age = now.getFullYear() - birth.getFullYear();
    const m = now.getMonth() - birth.getMonth();
    if (m < 0 || (m === 0 && now.getDate() < birth.getDate())) age--;
    return age > 0 ? age : 'N/A';
  }

  checkIn(member: MemberResponse): void {
    this.errorMessage = '';
    this.successMessage = '';
    this.attendanceService.checkIn(member.id).subscribe({
      next: () => {
        this.successMessage = `Member ${member.name} checked in successfully!`;
      },
      error: (err) => {
        this.errorMessage = err.error?.message || `Failed to check in ${member.name}`;
      }
    });
  }

  checkOut(member: MemberResponse): void {
    this.errorMessage = '';
    this.successMessage = '';
    this.attendanceService.checkOut(member.id).subscribe({
      next: () => {
        this.successMessage = `Member ${member.name} checked out successfully!`;
      },
      error: (err) => {
        this.errorMessage = err.error?.message || `Failed to check out ${member.name}`;
      }
    });
  }

  deleteMember(id: number, name: string): void {
    if (!confirm(`Are you sure you want to delete member "${name}"? This will also remove attendance and payment records.`)) {
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';

    this.memberService.deleteMember(id).subscribe({
      next: () => {
        this.successMessage = `Member "${name}" deleted successfully!`;
        this.loadMembers();
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to delete member';
      }
    });
  }
}
