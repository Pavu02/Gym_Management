import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Trainer, TrainerRequest } from '../models/trainer.model';
import { MemberResponse } from '../models/member.model';

@Injectable({
  providedIn: 'root'
})
export class TrainerService {
  apiUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  getAllTrainers(): Observable<Trainer[]> {
    return this.http.get<Trainer[]>(`${this.apiUrl}/trainers`);
  }

  getTrainerById(id: number): Observable<Trainer> {
    return this.http.get<Trainer>(`${this.apiUrl}/trainers/${id}`);
  }

  addTrainer(trainer: TrainerRequest): Observable<Trainer> {
    return this.http.post<Trainer>(`${this.apiUrl}/trainers`, trainer);
  }

  updateTrainer(id: number, trainer: TrainerRequest): Observable<Trainer> {
    return this.http.put<Trainer>(`${this.apiUrl}/trainers/${id}`, trainer);
  }

  deleteTrainer(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/trainers/${id}`);
  }

  getMyMembers(): Observable<MemberResponse[]> {
    return this.http.get<MemberResponse[]>(`${this.apiUrl}/trainers/my-members`);
  }
}
