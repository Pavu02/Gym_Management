import { Component, OnInit } from '@angular/core';
import { TrainerService } from '../../services/trainer.service';
import { FaceService, FaceCheckinResponseDTO } from '../../services/face.service';
import { MemberResponse } from '../../models/member.model';

@Component({
  selector: 'app-trainerviewmembers',
  templateUrl: './trainerviewmembers.component.html',
  styleUrls: ['./trainerviewmembers.component.css']
})
export class TrainerviewmembersComponent implements OnInit {
  assignedMembers: MemberResponse[] = [];
  trainerAttendance: FaceCheckinResponseDTO[] = [];
  visitsThisMonth: number = 0;
  lastCheckinTime: string = '';

  loading: boolean = true;
  errorMessage: string = '';

  constructor(
    private trainerService: TrainerService,
    private faceService: FaceService
  ) {}

  ngOnInit(): void {
    this.loadMyMembers();
    this.loadMyAttendance();
  }

  loadMyMembers(): void {
    this.loading = true;
    this.trainerService.getMyMembers().subscribe({
      next: (data) => {
        this.assignedMembers = data;
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load assigned members';
        this.loading = false;
      }
    });
  }

  loadMyAttendance(): void {
    this.faceService.getMyAttendance().subscribe({
      next: (list) => {
        this.trainerAttendance = list;
        const now = new Date();
        const currentMonth = now.getMonth();
        const currentYear = now.getFullYear();

        this.visitsThisMonth = list.filter(item => {
          if (!item.checkInTime) return false;
          const d = new Date(item.checkInTime);
          return d.getMonth() === currentMonth && d.getFullYear() === currentYear;
        }).length;

        if (list.length > 0 && list[0].checkInTime) {
          this.lastCheckinTime = list[0].checkInTime;
        }
      },
      error: () => {}
    });
  }
}
