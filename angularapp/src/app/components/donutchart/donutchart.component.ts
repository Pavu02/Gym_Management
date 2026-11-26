import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';

export interface DonutSegment {
  label: string;
  value: number;
  color: string;
}

interface DonutArc {
  color: string;
  label: string;
  value: number;
  pct: string;
  startDeg: number;
  targetOffset: number;
}

@Component({
  selector: 'app-donut-chart',
  templateUrl: './donutchart.component.html',
  styleUrls: ['./donutchart.component.css']
})
export class DonutchartComponent implements OnChanges, OnDestroy {
  @Input() segments: DonutSegment[] = [];
  @Input() title = '';
  @Input() inView = false;

  readonly R = 54;
  readonly CX = 78;
  readonly CY = 78;
  readonly circumference = 2 * Math.PI * 54;

  total = 0;
  displayTotal = 0;
  arcs: DonutArc[] = [];
  hovered: number | null = null;

  private rafId: number | null = null;
  private hasCountedUp = false;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['segments']) {
      this.compute();
    }
    if (changes['inView'] || changes['segments']) {
      this.maybeCountUp();
    }
  }

  ngOnDestroy(): void {
    this.cancelRaf();
  }

  private cancelRaf(): void {
    if (this.rafId !== null && typeof cancelAnimationFrame !== 'undefined') {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
  }

  compute(): void {
    const valid = (this.segments || []).filter(s => Number.isFinite(s.value) && s.value > 0);
    const sum = valid.reduce((s, seg) => s + seg.value, 0);

    if (!valid.length || sum <= 0) {
      this.arcs = [];
      this.total = 0;
      this.displayTotal = 0;
      return;
    }

    this.total = sum;
    const C = this.circumference;
    let cumLen = 0;

    this.arcs = valid.map(seg => {
      const ratio = seg.value / sum;
      const len = ratio * C;
      const startDeg = -90 + (cumLen / C) * 360;
      const arc: DonutArc = {
        color: seg.color,
        label: seg.label,
        value: seg.value,
        pct: (ratio * 100).toFixed(1) + '%',
        startDeg,
        targetOffset: C - len
      };
      cumLen += len;
      return arc;
    });
  }

  private maybeCountUp(): void {
    if (this.total <= 0) {
      this.displayTotal = 0;
      return;
    }
    if (!this.inView) {
      if (!this.hasCountedUp) {
        this.displayTotal = 0;
      }
      return;
    }
    if (typeof requestAnimationFrame === 'undefined' ||
        (typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches)) {
      this.displayTotal = this.total;
      this.hasCountedUp = true;
      return;
    }

    this.cancelRaf();
    const startVal = this.hasCountedUp ? this.displayTotal : 0;
    const target = this.total;
    const duration = 1200;
    const startTime = performance.now();

    const step = (now: number) => {
      const elapsed = now - startTime;
      const t = Math.min(elapsed / duration, 1);
      const eased = 1 - Math.pow(1 - t, 3);
      this.displayTotal = Math.round(startVal + (target - startVal) * eased);
      if (t < 1) {
        this.rafId = requestAnimationFrame(step);
      } else {
        this.displayTotal = target;
        this.hasCountedUp = true;
        this.rafId = null;
      }
    };
    this.rafId = requestAnimationFrame(step);
  }

  hover(i: number | null): void {
    this.hovered = i;
  }

  toggleHover(i: number): void {
    this.hovered = this.hovered === i ? null : i;
  }
}
