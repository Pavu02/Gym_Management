import { Member } from './member.model';

export interface Payment {
  id?: number;
  member?: Member;
  amount: number;
  paymentDate: string;
  mode: string;
  status: string;
}

export interface PaymentRequest {
  memberId: number;
  amount: number;
  mode: string;
  status: string;
}
