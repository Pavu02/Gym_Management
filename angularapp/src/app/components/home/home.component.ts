import {
  Component,
  OnInit,
  AfterViewInit,
  OnDestroy,
  ElementRef,
  QueryList,
  ViewChildren
} from '@angular/core';
import { AuthService } from '../../services/auth.service';

export interface ProgramItem {
  id: string;
  tag: string;
  title: string;
  subtitle: string;
  desc: string;
  img: string;
  alt: string;
  points: string[];
}

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChildren('revealEl') revealEls!: QueryList<ElementRef<HTMLElement>>;
  @ViewChildren('statsBlock') statsBlocks!: QueryList<ElementRef<HTMLElement>>;

  billingCycle: 'monthly' | 'yearly' = 'monthly';
  selectedProgramIndex = 0;
  selectedTrainerIndex = 0;
  openFaqIndex = 0;

  // Animated count-up stats
  statYears = 0;
  statMembers = 0;
  statTrainers = 0;
  statStudios = 0;
  private statsAnimated = false;

  programs: ProgramItem[] = [
    {
      id: 'strength',
      tag: 'HYPERTROPHY & POWER',
      title: 'STRENGTH TRAINING',
      subtitle: 'Calibrated power racks, biomechanics & progressive periodization',
      desc: 'Build dense muscle, increase maximal force output, and master compound barbell mechanics with periodized training blocks tailored to your baseline.',
      img: 'https://images.unsplash.com/photo-1534367507873-d2d7e24c797f?q=80&w=1000&auto=format&fit=crop',
      alt: 'Athlete performing barbell squat in rack',
      points: [
        'Personalized periodized splits',
        'Progressive overload tracking',
        '1-on-1 barbell form diagnostics',
        'Biometric recovery check-ins'
      ]
    },
    {
      id: 'conditioning',
      tag: 'METABOLIC PERFORMANCE',
      title: 'HIIT & CONDITIONING',
      subtitle: 'High-output turf intervals, sled drives & anaerobic capacity',
      desc: 'Torch stubborn body fat and elevate VO2 max through explosive metabolic circuits engineered around heart-rate zones and functional athleticism.',
      img: 'https://images.unsplash.com/photo-1571019614242-c5c5dee9f50b?q=80&w=1000&auto=format&fit=crop',
      alt: 'Athlete conditioning core and agility workout',
      points: [
        'Zone-tracked interval blocks',
        'Sled, kettlebell & assault circuits',
        'Coach-led tempo pacing',
        'Post-session mobility cool-down'
      ]
    },
    {
      id: 'mobility',
      tag: 'BALANCE & LONGEVITY',
      title: 'YOGA & MOBILITY FLOW',
      subtitle: 'Joint resilience, core stability & active parasympathetic recovery',
      desc: 'Unlock deep hip, thoracic, and shoulder range of motion so heavy lifts feel effortless while protecting joints from overuse fatigue.',
      img: 'https://images.unsplash.com/photo-1540497077202-7c8a3999166f?q=80&w=1000&auto=format&fit=crop',
      alt: 'Gym functional mobility and suspension floor',
      points: [
        'End-range joint strengthening',
        'Guided breathwork & core flow',
        'Post-lift fascial decompression',
        'Injury-prevention screening'
      ]
    },
    {
      id: 'combat',
      tag: 'SPEED & PRECISION',
      title: 'BOXING & ATHLETIC LAB',
      subtitle: 'Footwork mastery, rotational power & explosive hand-eye stamina',
      desc: 'Combine championship heavy-bag combinations with rotational core power and conditioning rounds built for sharp reflexes and grit.',
      img: 'https://images.unsplash.com/photo-1517838277536-f5f99be501cd?q=80&w=1000&auto=format&fit=crop',
      alt: 'Strength and explosive athletic preparation',
      points: [
        'Technical heavy-bag combinations',
        'Rotational & core torque drills',
        'Championship round conditioning',
        'Agility ladder footwork precision'
      ]
    }
  ];

  trainerSpecialties = [
    {
      title: 'Strength & Muscle Gain',
      tier: 'ELITE HYPERTROPHY',
      metric: '5+ Years Avg Tenure',
      desc: 'Internationally certified coaches engineering progressive overload cycles, compound mechanics, and lean muscle symmetry.'
    },
    {
      title: 'Weight Loss & Yoga Flow',
      tier: 'SUSTAINABLE TRANSFORMATION',
      metric: '4.9 ★ Athlete Rating',
      desc: 'Holistic fat-loss conditioning paired with core stability, mindful breathwork, and high-compliance movement habits.'
    },
    {
      title: 'Boxing & Conditioning',
      tier: 'COMBAT ENGINE',
      metric: 'High-Intensity Pros',
      desc: 'Former competitive fighters sharpening speed, grit, footwork, and relentless cardiovascular output.'
    },
    {
      title: 'Sports Nutrition Strategy',
      tier: 'MACRO ARCHITECTURE',
      metric: 'Tailored Fuel Plans',
      desc: 'Evidence-backed macro splits, hydration timing, and recovery supplementation integrated into your membership.'
    }
  ];

  testimonials = [
    {
      quote: 'Face check-in takes less than a second, the racks are competition calibrated, and my squat jumped 35 kg in four months with Coach Rahul.',
      name: 'Aarav Kulkarni',
      tag: 'Pro Member · 14 Months',
      initials: 'AK'
    },
    {
      quote: 'Unlike crowded commercial gyms, BeastX actually tracks your attendance and pairs structure with intensity. Hands down the finest studio in the city.',
      name: 'Meera Deshmukh',
      tag: 'Elite VIP · 2 Years',
      initials: 'MD'
    },
    {
      quote: 'From fingerprint phone check-in when my hands are full to Coach Priya’s yoga mobility sessions, every detail feels thoughtfully engineered.',
      name: 'Rohan Verma',
      tag: 'Pro Member · 9 Months',
      initials: 'RV'
    }
  ];

  milestones = [
    {
      title: 'THE FOUNDING SANCTUARY',
      desc: 'Opened our first raw iron training studio with 12 competition racks and a 30-athlete founding brotherhood.',
      year: '2015'
    },
    {
      title: '500+ ACTIVE ATHLETES',
      desc: 'Expanded strength, conditioning, and dedicated nutrition coaching under a unified performance roof.',
      year: '2018'
    },
    {
      title: '3 CITYWIDE FLAGSHIPS',
      desc: 'Brought calibrated Eleiko equipment, recovery lounges, and championship trainers to three metro hubs.',
      year: '2022'
    },
    {
      title: 'AI BIOMETRIC CHECK-IN',
      desc: 'Launched zero-friction Face Recognition & WebAuthn mobile fingerprint access across all BeastX kiosks.',
      year: '2026'
    }
  ];

  faqs = [
    {
      q: 'Do I need prior gym experience to join BeastX?',
      a: 'Not at all. Every new member completes streamlined onboarding, goal mapping, and baseline assessment so our coaches scale intensity cleanly from day one.'
    },
    {
      q: 'How do Face ID & phone fingerprint check-ins work?',
      a: 'Enroll your face or smartphone passkey once in your member profile. Walk up to our front-desk kiosk camera—or scan the rotating QR with your phone fingerprint—for sub-second check-in.'
    },
    {
      q: 'Can I pair my membership with a dedicated personal trainer?',
      a: 'Yes! During onboarding—or anytime from your member dashboard—you can select a certified trainer matched to strength gain, fat loss, yoga mobility, or boxing conditioning.'
    },
    {
      q: 'What membership plans and billing cycles are offered?',
      a: 'We offer Basic, Pro Performance, and Elite VIP memberships with flexible monthly passes and discounted annual billing options.'
    },
    {
      q: 'Are customized nutrition and workout guidance included?',
      a: 'Pro Performance and Elite VIP tiers include macro strategy, diet consultations, and structured progressive training guidance.'
    },
    {
      q: 'What are your studio operating hours?',
      a: 'BeastX is open Monday through Friday from 5:00 AM to 11:00 PM, and Saturday & Sunday from 6:00 AM to 9:00 PM.'
    }
  ];

  private revealObserver?: IntersectionObserver;
  private statsObserver?: IntersectionObserver;
  private rafIds: number[] = [];

  constructor(public authService: AuthService) {}

  ngOnInit(): void {
    if (typeof IntersectionObserver === 'undefined') {
      this.setStaticStats();
    }
  }

  ngAfterViewInit(): void {
    this.setupRevealObserver();
    this.setupStatsObserver();
  }

  ngOnDestroy(): void {
    this.revealObserver?.disconnect();
    this.statsObserver?.disconnect();
    if (typeof cancelAnimationFrame !== 'undefined') {
      this.rafIds.forEach(id => cancelAnimationFrame(id));
    }
    this.rafIds = [];
  }

  get activeProgram(): ProgramItem {
    return this.programs[this.selectedProgramIndex] || this.programs[0];
  }

  get activeTrainerSpec() {
    return this.trainerSpecialties[this.selectedTrainerIndex] || this.trainerSpecialties[0];
  }

  getPortalRoute(): string {
    const role = (this.authService.getRole() || '').toUpperCase();
    if (role === 'ADMIN') return '/admin/dashboard';
    if (role === 'TRAINER') return '/trainer/members';
    return '/member/profile';
  }

  setBillingCycle(cycle: 'monthly' | 'yearly'): void {
    this.billingCycle = cycle;
  }

  selectProgram(index: number): void {
    this.selectedProgramIndex = index;
  }

  selectTrainer(index: number): void {
    this.selectedTrainerIndex = index;
  }

  toggleFaq(index: number): void {
    this.openFaqIndex = this.openFaqIndex === index ? -1 : index;
  }

  private setupRevealObserver(): void {
    if (typeof IntersectionObserver === 'undefined') {
      this.revealEls?.forEach(ref => ref.nativeElement.classList.add('in-view'));
      return;
    }

    this.revealObserver = new IntersectionObserver(
      (entries) => {
        entries.forEach(entry => {
          if (entry.isIntersecting) {
            entry.target.classList.add('in-view');
            this.revealObserver?.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.1 }
    );

    this.revealEls?.forEach(ref => this.revealObserver?.observe(ref.nativeElement));
  }

  private setupStatsObserver(): void {
    if (typeof IntersectionObserver === 'undefined') {
      this.setStaticStats();
      return;
    }

    this.statsObserver = new IntersectionObserver(
      (entries) => {
        entries.forEach(entry => {
          if (entry.isIntersecting && !this.statsAnimated) {
            this.statsAnimated = true;
            this.animateHeroMetrics();
            this.statsObserver?.disconnect();
          }
        });
      },
      { threshold: 0.2 }
    );

    this.statsBlocks?.forEach(ref => this.statsObserver?.observe(ref.nativeElement));
  }

  private setStaticStats(): void {
    this.statYears = 12;
    this.statMembers = 5000;
    this.statTrainers = 44;
    this.statStudios = 3;
  }

  private animateHeroMetrics(): void {
    const reduced =
      typeof requestAnimationFrame === 'undefined' ||
      (typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches);

    if (reduced) {
      this.setStaticStats();
      return;
    }

    this.countValue('statYears', 12, 1100);
    this.countValue('statMembers', 5000, 1400);
    this.countValue('statTrainers', 44, 1200);
    this.countValue('statStudios', 3, 900);
  }

  private countValue(
    prop: 'statYears' | 'statMembers' | 'statTrainers' | 'statStudios',
    target: number,
    duration: number
  ): void {
    const startTime = performance.now();
    const step = (now: number) => {
      const progress = Math.min((now - startTime) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      this[prop] = Math.round(target * eased);
      if (progress < 1) {
        this.rafIds.push(requestAnimationFrame(step));
      } else {
        this[prop] = target;
      }
    };
    this.rafIds.push(requestAnimationFrame(step));
  }
}
