import { Plan } from './plan.model';
import { Trainer } from './trainer.model';
import { User } from './user.model';

export interface Member {
  id?: number;
  user?: User;
  plan?: Plan;
  trainer?: Trainer;
  joinDate?: string;
  expiryDate?: string;
  status?: string;
}

export interface MemberRequest {
  name: string;
  email: string;
  password?: string;
  phone?: string;
  planId: number;
  trainerId?: number | null;
}

export interface MemberResponse {
  id: number;
  name: string;
  email: string;
  phone: string;
  planName: string;
  trainerName?: string;
  joinDate: string;
  expiryDate: string;
  status: string;
}
