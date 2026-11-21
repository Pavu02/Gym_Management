import { Component } from '@angular/core';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent {
  billingCycle: 'monthly' | 'yearly' = 'monthly';
  selectedTrainerIndex: number = 0;
  openFaqIndex: number = 0;

  faqs = [
    {
      q: 'I need prior gym experience?',
      a: 'No. BeastX is designed for all fitness levels, from complete beginners to experienced athletes.'
    },
    {
      q: 'Do you offer personal training?',
      a: 'Yes! We offer 1-on-1 personal coaching with elite certified trainers tailored directly to your fitness goals and schedule.'
    },
    {
      q: 'Is there a free trial available?',
      a: 'Yes, we offer a complimentary 1-day pass so you can experience our world-class facilities and coaching before committing.'
    },
    {
      q: 'What membership plans do you offer?',
      a: 'We offer Starter, Pro, and Elite tiers with flexible monthly and discounted annual billing terms.'
    },
    {
      q: 'Are nutrition plans included?',
      a: 'Customized nutrition and macro consultation are included in our Pro and Elite membership tiers.'
    },
    {
      q: 'What are your gym opening hours?',
      a: 'Our facilities are open Monday through Friday from 5:00 AM to 11:00 PM, and Saturday & Sunday from 6:00 AM to 9:00 PM.'
    }
  ];

  trainerSpecialties = [
    { title: 'Certified Trainers', desc: 'Internationally accredited fitness coaches specialized in progressive overload and peak biomechanics.' },
    { title: 'Nutrition Experts', desc: 'Registered dieticians crafting tailored macro and micronutrient meal strategies for optimal recovery.' },
    { title: 'Elite Trainers', desc: 'Former competitive athletes bringing championship-grade conditioning and strength methodologies.' },
    { title: 'HIIT Specialists', desc: 'Dynamic cardiovascular conditioning pros focused on rapid metabolic burn and athletic agility.' }
  ];

  milestones = [
    { title: 'THE FOUNDING', year: '2015', side: 'left' },
    { title: 'FIRST 500 MEMBERS', year: '2018', side: 'right' },
    { title: 'LOCATION #2 OPENS', year: '2022', side: 'left' }
  ];

  constructor(public authService: AuthService) {}

  setBillingCycle(cycle: 'monthly' | 'yearly'): void {
    this.billingCycle = cycle;
  }

  selectTrainer(index: number): void {
    this.selectedTrainerIndex = index;
  }

  toggleFaq(index: number): void {
    this.openFaqIndex = this.openFaqIndex === index ? -1 : index;
  }
}
