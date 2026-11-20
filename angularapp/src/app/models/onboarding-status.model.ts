export interface OnboardingStatus {
  hasMember: boolean;
  onboardingCompleted: boolean;
  status: string | null;
  planId: number | null;
  trainerId: number | null;
}
