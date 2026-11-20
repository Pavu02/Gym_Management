import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-onboardingstepper',
  templateUrl: './onboardingstepper.component.html',
  styleUrls: ['./onboardingstepper.component.css']
})
export class OnboardingstepperComponent {
  @Input() step: number = 1;

  steps = [
    { num: 1, label: 'Plan' },
    { num: 2, label: 'Trainer' },
    { num: 3, label: 'Profile' },
    { num: 4, label: 'Payment' }
  ];
}
