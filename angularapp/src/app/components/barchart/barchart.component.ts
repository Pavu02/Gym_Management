import { Component, Input, OnChanges } from '@angular/core';
import { DayAttendance } from '../../services/dashboard.service';

@Component({
  selector: 'app-bar-chart',
  templateUrl: './barchart.component.html',
  styleUrls: ['./barchart.component.css']
})
export class BarchartComponent implements OnChanges {
  @Input() data: DayAttendance[] = [];
  @Input() todayIndex = 6;
  @Input() inView = false;

  W = 360;
  H = 180;
  PAD_TOP = 26;
  PAD_BOTTOM = 26;
  PAD_X = 14;

  bars: {
    x: number;
    y: number;
    w: number;
    h: number;
    label: string;
    count: number;
    isToday: boolean;
  }[] = [];

  avgY = 120;
  avg = 0;
  hoveredIdx: number | null = null;

  ngOnChanges(): void {
    this.compute();
  }

  compute(): void {
    if (!this.data || !this.data.length) {
      this.bars = [];
      return;
    }

    const counts = this.data.map(d => (Number.isFinite(d.count) ? d.count : 0));
    const maxVal = Math.max(...counts, 1);
    const total = counts.reduce((s, c) => s + c, 0);
    this.avg = total / this.data.length;

    const chartW = this.W - this.PAD_X * 2;
    const chartH = this.H - this.PAD_TOP - this.PAD_BOTTOM;
    const baseY = this.H - this.PAD_BOTTOM;

    this.avgY = baseY - (this.avg / maxVal) * chartH;

    const slotW = chartW / this.data.length;
    const barW = Math.min(28, slotW * 0.62);

    this.bars = this.data.map((d, i) => {
      const cnt = Number.isFinite(d.count) ? d.count : 0;
      const rawH = (cnt / maxVal) * chartH;
      const barH = cnt > 0 ? Math.max(rawH, 6) : 3;
      const x = this.PAD_X + i * slotW + (slotW - barW) / 2;
      const y = baseY - barH;
      return {
        x,
        y,
        w: barW,
        h: barH,
        label: d.label,
        count: cnt,
        isToday: i === this.todayIndex
      };
    });
  }

  showBar(i: number): void {
    this.hoveredIdx = i;
  }

  toggleBar(i: number): void {
    this.hoveredIdx = this.hoveredIdx === i ? null : i;
  }

  hideBar(): void {
    this.hoveredIdx = null;
  }
}
