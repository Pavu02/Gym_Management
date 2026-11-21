import { Member } from './member.model';

export interface Attendance {
  id?: number;
  member?: Member;
  // Backend returns these field names (JPA column-mapped names)
  attendanceDate?: string;
  checkInTime?: string;
  checkOutTime?: string | null;
  method?: string;
  // Legacy aliases kept for backward compat (some older records may use these)
  date?: string;
  checkIn?: string;
  checkOut?: string | null;
}

/** Returns the effective attendance date from either the new or old field */
export function getAttendanceDate(a: Attendance): string {
  return a.attendanceDate || a.date || '';
}

/** Returns the effective check-in time */
export function getCheckInTime(a: Attendance): string {
  return a.checkInTime || a.checkIn || '';
}

/** Returns the effective check-out time */
export function getCheckOutTime(a: Attendance): string | null | undefined {
  return a.checkOutTime !== undefined ? a.checkOutTime : a.checkOut;
}
