import { Component, Input, OnChanges } from '@angular/core';
import { PeakHour } from '../../services/dashboard.service';

interface HeatCell {
  hour: number;
  count: number;
  label: string;
  shortLabel: string;
  showTick: boolean;
  fill: string;
  x: number;
  w: number;
}

@Component({
  selector: 'app-heatmap-strip',
  templateUrl: './heatmapstrip.component.html',
  styleUrls: ['./heatmapstrip.component.css']
})
export class HeatmapstripComponent implements OnChanges {
  @Input() hours: PeakHour[] = [];
  @Input() inView = false;

  W = 760;
  H = 82;
  cells: HeatCell[] = [];
  busiestLabel = '';
  hasActivity = false;
  activeIdx: number | null = null;
  tooltip: { label: string; count: number; x: number } | null = null;

  ngOnChanges(): void {
    this.compute();
  }

  compute(): void {
    if (!this.hours || !this.hours.length) {
      this.cells = [];
      this.busiestLabel = '';
      this.hasActivity = false;
      this.tooltip = null;
      return;
    }

    const maxCount = Math.max(...this.hours.map(h => h.count || 0), 0);
    this.hasActivity = maxCount > 0;

    const gap = 4;
    const total = this.hours.length;
    const cellW = (this.W - gap * (total - 1)) / total;

    this.cells = this.hours.map((h, i) => {
      const cnt = Number.isFinite(h.count) ? h.count : 0;
      const intensity = maxCount > 0 ? cnt / maxCount : 0;
      const fill = cnt === 0
        ? '#1F1F1F'
        : `rgba(255, 90, 0, ${(0.22 + intensity * 0.78).toFixed(2)})`;
      return {
        hour: h.hour,
        count: cnt,
        label: this.fmt(h.hour),
        shortLabel: this.fmtShort(h.hour),
        showTick: i === 0 || i === total - 1 || h.hour % 3 === 0,
        fill,
        x: i * (cellW + gap),
        w: cellW
      };
    });

    if (this.hasActivity) {
      const busiest = this.hours.reduce((best, h) => (h.count > best.count ? h : best), this.hours[0]);
      this.busiestLabel = this.fmt(busiest.hour);
    } else {
      this.busiestLabel = 'No check-ins yet';
    }
  }

  fmt(h: number): string {
    const ampm = h < 12 ? 'AM' : 'PM';
    const h12 = h % 12 === 0 ? 12 : h % 12;
    return `${h12} ${ampm}`;
  }

  private fmtShort(h: number): string {
    const ampm = h < 12 ? 'a' : 'p';
    const h12 = h % 12 === 0 ? 12 : h % 12;
    return `${h12}${ampm}`;
  }

  showTip(c: HeatCell, idx: number): void {
    this.activeIdx = idx;
    const center = c.x + c.w / 2;
    const clampedX = Math.max(76, Math.min(this.W - 76, center));
    this.tooltip = { label: c.label, count: c.count, x: clampedX };
  }

  toggleTip(c: HeatCell, idx: number): void {
    if (this.activeIdx === idx && this.tooltip) {
      this.hideTip();
    } else {
      this.showTip(c, idx);
    }
  }

  hideTip(): void {
    this.activeIdx = null;
    this.tooltip = null;
  }
}
