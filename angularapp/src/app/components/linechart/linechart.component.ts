import {
  Component, Input, OnChanges, AfterViewInit, OnDestroy, ElementRef, ViewChild
} from '@angular/core';
import { MonthlyRevenue } from '../../services/dashboard.service';

@Component({
  selector: 'app-line-chart',
  templateUrl: './linechart.component.html',
  styleUrls: ['./linechart.component.css']
})
export class LinechartComponent implements OnChanges, AfterViewInit, OnDestroy {
  @Input() data: MonthlyRevenue[] = [];
  @Input() inView = false;
  @ViewChild('svgEl') svgEl?: ElementRef<SVGSVGElement>;

  W = 500;
  H = 185;
  PAD_X = 34;
  PAD_TOP = 26;
  PAD_BOTTOM = 26;

  points: { x: number; y: number; val: number; label: string }[] = [];
  gridLines: { y: number; text: string }[] = [];
  pathD = '';
  areaD = '';
  pathLen = 600;
  totalRevenue = 0;
  changePct: number | null = null;
  changePositive = true;
  tooltip: { x: number; y: number; boxX: number; label: string; val: string } | null = null;
  activeIdx: number | null = null;
  private measureTimer?: any;

  ngOnChanges(): void {
    this.compute();
  }

  ngAfterViewInit(): void {
    this.measurePath();
  }

  ngOnDestroy(): void {
    if (this.measureTimer) clearTimeout(this.measureTimer);
  }

  formatINR(v: number): string {
    const safe = Number.isFinite(v) ? v : 0;
    return '₹' + safe.toLocaleString('en-IN', { maximumFractionDigits: 0 });
  }

  compute(): void {
    if (!this.data || this.data.length === 0) {
      this.points = [];
      this.pathD = '';
      this.areaD = '';
      this.gridLines = [];
      this.totalRevenue = 0;
      this.changePct = null;
      this.tooltip = null;
      return;
    }

    const vals = this.data.map(d => (Number.isFinite(d.amount) ? d.amount : 0));
    const maxVal = Math.max(...vals, 1);
    const chartW = this.W - this.PAD_X * 2;
    const chartH = this.H - this.PAD_TOP - this.PAD_BOTTOM;
    const baseY = this.H - this.PAD_BOTTOM;

    this.gridLines = [0.5, 1].map(frac => ({
      y: baseY - frac * chartH,
      text: this.formatCompact(maxVal * frac)
    }));

    const denom = Math.max(this.data.length - 1, 1);
    this.points = this.data.map((d, i) => {
      const amt = Number.isFinite(d.amount) ? d.amount : 0;
      const x = this.data.length === 1 ? this.W / 2 : this.PAD_X + (i / denom) * chartW;
      const y = baseY - (amt / maxVal) * chartH;
      return { x, y, val: amt, label: d.label };
    });

    if (this.points.length >= 2) {
      this.pathD = this.smoothPath(this.points);
      this.areaD =
        `${this.pathD} L${this.points[this.points.length - 1].x},${baseY} L${this.points[0].x},${baseY} Z`;
    } else {
      const p = this.points[0];
      this.pathD = `M${p.x - 20},${p.y} L${p.x + 20},${p.y}`;
      this.areaD = `M${p.x - 20},${p.y} L${p.x + 20},${p.y} L${p.x + 20},${baseY} L${p.x - 20},${baseY} Z`;
    }

    this.totalRevenue = vals.reduce((s, v) => s + v, 0);

    if (vals.length >= 2) {
      const prev = vals[vals.length - 2];
      const curr = vals[vals.length - 1];
      if (prev === 0 && curr === 0) {
        this.changePct = 0;
        this.changePositive = true;
      } else if (prev === 0) {
        this.changePct = 100;
        this.changePositive = true;
      } else {
        this.changePct = Math.round(((curr - prev) / prev) * 100);
        this.changePositive = this.changePct >= 0;
      }
    } else {
      this.changePct = null;
    }

    this.measurePath();
  }

  private measurePath(): void {
    if (this.measureTimer) clearTimeout(this.measureTimer);
    this.measureTimer = setTimeout(() => {
      const path = this.svgEl?.nativeElement.querySelector('.line-path') as SVGPathElement | null;
      if (path && typeof path.getTotalLength === 'function') {
        const len = path.getTotalLength();
        if (Number.isFinite(len) && len > 0) {
          this.pathLen = Math.ceil(len) + 4;
        }
      }
    }, 30);
  }

  private smoothPath(pts: { x: number; y: number }[]): string {
    if (pts.length < 2) return '';
    let d = `M${pts[0].x},${pts[0].y}`;
    for (let i = 1; i < pts.length; i++) {
      const dx = (pts[i].x - pts[i - 1].x) / 3;
      const cp1x = pts[i - 1].x + dx;
      const cp2x = pts[i].x - dx;
      d += ` C${cp1x},${pts[i - 1].y} ${cp2x},${pts[i].y} ${pts[i].x},${pts[i].y}`;
    }
    return d;
  }

  private formatCompact(v: number): string {
    if (v >= 100000) return '₹' + (v / 100000).toFixed(1) + 'L';
    if (v >= 1000) return '₹' + (v / 1000).toFixed(0) + 'k';
    return '₹' + Math.round(v);
  }

  showTip(p: { x: number; y: number; label: string; val: number }, idx: number): void {
    this.activeIdx = idx;
    const clampedX = Math.max(54, Math.min(this.W - 54, p.x));
    const clampedY = Math.max(34, p.y - 10);
    this.tooltip = {
      x: clampedX,
      y: clampedY,
      boxX: clampedX - 50,
      label: p.label,
      val: this.formatINR(p.val)
    };
  }

  toggleTip(p: { x: number; y: number; label: string; val: number }, idx: number): void {
    if (this.activeIdx === idx && this.tooltip) {
      this.hideTip();
    } else {
      this.showTip(p, idx);
    }
  }

  hideTip(): void {
    this.activeIdx = null;
    this.tooltip = null;
  }
}
