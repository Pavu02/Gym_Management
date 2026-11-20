export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
  phone?: string;
}

export interface AuthResponse {
  token: string;
  userId: number;
  name: string;
  email: string;
  role: string;
}

export interface Login extends LoginRequest {}
