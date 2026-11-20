export interface Plan {
  id?: number;
  name: string;
  durationMonths: number;
  price: number;
  description: string;
  features?: string;
  popular?: boolean;
}
