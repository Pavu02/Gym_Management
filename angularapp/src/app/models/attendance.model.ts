import { Member } from './member.model';

export interface Attendance {
  id?: number;
  member?: Member;
  date: string;
  checkIn: string;
  checkOut?: string | null;
}
