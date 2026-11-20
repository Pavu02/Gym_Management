export interface OnboardingSummary {
  planName: string;
  planPrice: number;
  durationMonths: number;
  trainerName: string | null;
  trainerFee: number;
  totalAmount: number;
}
