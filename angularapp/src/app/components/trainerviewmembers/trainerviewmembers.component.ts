import { Component, OnInit } from '@angular/core';
import { TrainerService } from '../../services/trainer.service';
import { MemberResponse } from '../../models/member.model';

@Component({
  selector: 'app-trainerviewmembers',
  templateUrl: './trainerviewmembers.component.html',
  styleUrls: ['./trainerviewmembers.component.css']
})
export class TrainerviewmembersComponent implements OnInit {
  assignedMembers: MemberResponse[] = [];
  loading: boolean = true;
  errorMessage: string = '';

  constructor(private trainerService: TrainerService) {}

  ngOnInit(): void {
    this.loadMyMembers();
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
}
