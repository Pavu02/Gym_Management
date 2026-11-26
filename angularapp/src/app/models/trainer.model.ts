import { User } from './user.model';

export interface Trainer {
  id?: number;
  user?: User;
  specialization: string;
  experienceYears: number;
  bio?: string;
  rating?: number;
  monthlyFee?: number;
  maxMembers?: number;
  active?: boolean;
}

export interface TrainerRequest {
  name: string;
  email: string;
  password?: string;
  phone?: string;
  specialization: string;
  experienceYears: number;
  bio?: string;
  rating?: number;
  monthlyFee?: number;
}
