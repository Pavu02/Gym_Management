import {
  Component, OnInit, OnDestroy, AfterViewInit, ElementRef, QueryList, ViewChildren
} from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import {
  DashboardService,
  DashboardAnalytics,
  MonthlyCount
} from '../../services/dashboard.service';
import { DashboardStats } from '../../models/dashboard.model';
import { FaceService } from '../../services/face.service';
import { DonutSegment } from '../donutchart/donutchart.component';

interface SparklineData {
  pathD: string;
  areaD: string;
  points: { x: number; y: number; label: string; value: number }[];
  pathLen: number;
}

@Component({
  selector: 'app-admindashboard',
  templateUrl: './admindashboard.component.html',
  styleUrls: ['./admindashboard.component.css']
})
export class AdmindashboardComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChildren('chartSection') chartSections!: QueryList<ElementRef<HTMLElement>>;

  stats: DashboardStats | null = null;
  analytics: DashboardAnalytics | null = null;
  loading = true;
  analyticsLoading = true;
  errorMessage = '';

  lastUpdatedLabel = 'just now';
  private lastUpdatedTime: number | null = null;

  // Today's Check-ins by Method
  faceCheckinsToday = 0;
  fingerprintCheckinsToday = 0;
  manualCheckinsToday = 0;

  // Animated count-up values (0 -> target over 1.2s)
  animTotalMembers = 0;
  animActiveMembers = 0;
  animPendingMembers = 0;
  animExpiredMembers = 0;
  animTotalTrainers = 0;
  animTotalPlans = 0;
  animTodayAttendance = 0;
  animMonthlyRevenue = 0;

  // Mini stat sparklines & trend chips
  membersSparkPath = '';
  membersTrendText = '';
  membersTrendPositive = true;

  attendanceSparkPath = '';
  attendanceTrendText = '';
  attendanceTrendPositive = true;

  revenueSparkPath = '';
  revenueTrendText = '';
  revenueTrendPositive = true;

  // Row 6: New members sparkline card
  newMembersSpark: SparklineData = { pathD: '', areaD: '', points: [], pathLen: 380 };
  newMembersTotal = 0;
  newMembersTrendText = '';
  newMembersTrendPositive = true;
  newMemberTip: { x: number; y: number; boxX: number; label: string; value: number } | null = null;
  activeNewMemberIdx: number | null = null;

  // Donut chart segments
  checkInDonutSegs: DonutSegment[] = [];
  memberStatusSegs: DonutSegment[] = [];

  // Plan bars max for scaling
  maxPlanCount = 1;

  // Per-row intersection state (triggers once per section)
  inViewRows: Record<string, boolean> = {
    row3: false,
    row4: false,
    row5: false,
    row6: false
  };

  get greeting(): string {
    const h = new Date().getHours();
    if (h < 12) return 'Good morning';
    if (h < 17) return 'Good afternoon';
    return 'Good evening';
  }

  get currentDate(): string {
    return new Date().toLocaleDateString('en-IN', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    });
  }

  get todayIndex(): number {
    return (this.analytics?.attendanceLast7Days?.length ?? 7) - 1;
  }

  private destroy$ = new Subject<void>();
  private observer?: IntersectionObserver;
  private refreshInterval?: any;
  private ageTickerInterval?: any;
  private rafHandles = new Map<string, number>();

  constructor(
    private dashboardService: DashboardService,
    private faceService: FaceService
  ) {}

  ngOnInit(): void {
    if (typeof IntersectionObserver === 'undefined') {
      this.revealAllRows();
    }
    this.loadAll();
    this.refreshInterval = setInterval(() => this.loadAll(), 60000);
    this.ageTickerInterval = setInterval(() => this.updateLastUpdatedLabel(), 30000);
  }

  ngAfterViewInit(): void {
    this.setupIntersectionObserver();
    this.chartSections.changes
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.observeAllSections());
  }

  loadAll(): void {
    this.loadStats();
    this.loadAnalytics();
    this.loadTodayMethods();
  }

  refresh(): void {
    if (this.loading || this.analyticsLoading) return;
    this.loadAll();
  }

  loadStats(): void {
    this.loading = true;
    this.errorMessage = '';
    this.dashboardService.getStats().pipe(takeUntil(this.destroy$)).subscribe({
      next: (data) => {
        this.stats = data;
        this.loading = false;
        this.markUpdatedNow();
        this.countUp('animTotalMembers', data.totalMembers || 0, false);
        this.countUp('animActiveMembers', data.activeMembers || 0, false);
        this.countUp('animPendingMembers', data.pendingMembers || 0, false);
        this.countUp('animExpiredMembers', data.expiredMembers || 0, false);
        this.countUp('animTotalTrainers', data.totalTrainers || 0, false);
        this.countUp('animTotalPlans', data.totalPlans || 0, false);
        this.countUp('animTodayAttendance', data.todayAttendance || 0, false);
        this.countUp('animMonthlyRevenue', data.monthlyRevenue || 0, true);
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load dashboard statistics';
        this.loading = false;
      }
    });
  }

  loadAnalytics(): void {
    this.analyticsLoading = true;
    this.dashboardService.getAnalytics().pipe(takeUntil(this.destroy$)).subscribe({
      next: (data) => {
        this.analytics = data;
        this.analyticsLoading = false;
        this.markUpdatedNow();
        this.buildDonutData(data);
        this.buildPlanMetrics(data);
        this.buildSparklines(data);
        setTimeout(() => this.observeAllSections(), 40);
      },
      error: () => {
        this.analyticsLoading = false;
        setTimeout(() => this.observeAllSections(), 40);
      }
    });
  }

  loadTodayMethods(): void {
    this.faceService.getTodayAttendance().pipe(takeUntil(this.destroy$)).subscribe({
      next: (records) => {
        const list = records || [];
        this.faceCheckinsToday = list.filter(r => (r.method || 'FACE').toUpperCase() === 'FACE').length;
        this.fingerprintCheckinsToday = list.filter(r => (r.method || '').toUpperCase() === 'FINGERPRINT').length;
        this.manualCheckinsToday = list.filter(r => (r.method || '').toUpperCase() === 'MANUAL').length;
      },
      error: () => {}
    });
  }

  private buildDonutData(data: DashboardAnalytics): void {
    const ci = data.checkInsByMethod;
    const ciTotal = (ci?.face || 0) + (ci?.fingerprint || 0) + (ci?.manual || 0);
    if (ciTotal > 0) {
      this.checkInDonutSegs = [
        { label: 'Face', value: ci.face, color: '#8B5CF6' },
        { label: 'Fingerprint', value: ci.fingerprint, color: '#FF5A00' },
        { label: 'Manual', value: ci.manual, color: '#6B7280' }
      ].filter(s => s.value > 0);
    } else {
      this.checkInDonutSegs = [];
    }

    const ms = data.membersByStatus;
    const msTotal = (ms?.active || 0) + (ms?.expired || 0) + (ms?.pendingOnboarding || 0);
    if (msTotal > 0) {
      this.memberStatusSegs = [
        { label: 'Active', value: ms.active, color: '#22C55E' },
        { label: 'Expired', value: ms.expired, color: '#EF4444' },
        { label: 'Pending', value: ms.pendingOnboarding, color: '#FF8A1F' }
      ].filter(s => s.value > 0);
    } else {
      this.memberStatusSegs = [];
    }
  }

  private buildPlanMetrics(data: DashboardAnalytics): void {
    const counts = (data.membersByPlan || []).map(p => p.count || 0);
    this.maxPlanCount = Math.max(...counts, 1);
  }

  private buildSparklines(data: DashboardAnalytics): void {
    // 1. Total Members mini sparkline + % trend (from newMembersByMonth)
    const nmList = data.newMembersByMonth || [];
    const nmVals = nmList.map(m => m.count || 0);
    this.membersSparkPath = this.computeMiniSparkPath(nmVals, 76, 26);
    const mTrend = this.computePercentDelta(nmVals);
    this.membersTrendText = mTrend.label + ' this month';
    this.membersTrendPositive = mTrend.positive;

    // 2. Today's Attendance mini sparkline + trend vs 7d avg
    const attVals = (data.attendanceLast7Days || []).map(d => d.count || 0);
    this.attendanceSparkPath = this.computeMiniSparkPath(attVals, 76, 26);
    const attTrend = this.computeTodayVsAvg(attVals);
    this.attendanceTrendText = attTrend.label + ' vs 7d avg';
    this.attendanceTrendPositive = attTrend.positive;

    // 3. Monthly Revenue mini sparkline + trend vs prev month
    const revVals = (data.revenueByMonth || []).map(r => r.amount || 0);
    this.revenueSparkPath = this.computeMiniSparkPath(revVals, 76, 26);
    const revTrend = this.computePercentDelta(revVals);
    this.revenueTrendText = revTrend.label + ' this month';
    this.revenueTrendPositive = revTrend.positive;

    // 4. Row 6 New Members sparkline card
    this.newMembersTotal = nmVals.reduce((s, v) => s + v, 0);
    this.newMembersTrendText = mTrend.label + ' vs prev mo';
    this.newMembersTrendPositive = mTrend.positive;
    this.newMembersSpark = this.computeLargeSparkline(nmList, 360, 118);
  }

  private computeMiniSparkPath(values: number[], w: number, h: number): string {
    if (!values.length) return '';
    if (values.length === 1) {
      const y = h / 2;
      return `M2,${y} L${w - 2},${y}`;
    }
    const max = Math.max(...values, 1);
    const pad = 3;
    const innerW = w - pad * 2;
    const innerH = h - pad * 2;
    const pts = values.map((v, i) => ({
      x: pad + (i / (values.length - 1)) * innerW,
      y: pad + (1 - v / max) * innerH
    }));
    return this.smoothCurve(pts);
  }

  private computeLargeSparkline(list: MonthlyCount[], w: number, h: number): SparklineData {
    if (!list.length) {
      return { pathD: '', areaD: '', points: [], pathLen: 380 };
    }
    const vals = list.map(d => d.count || 0);
    const max = Math.max(...vals, 1);
    const padX = 20;
    const padTop = 20;
    const padBot = 24;
    const innerW = w - padX * 2;
    const innerH = h - padTop - padBot;
    const baseY = h - padBot;

    const denom = Math.max(list.length - 1, 1);
    const points = list.map((d, i) => {
      const val = Number.isFinite(d.count) ? d.count : 0;
      const x = list.length === 1 ? w / 2 : padX + (i / denom) * innerW;
      const y = baseY - (val / max) * innerH;
      return { x, y, label: d.label, value: val };
    });

    const pathD = points.length >= 2
      ? this.smoothCurve(points)
      : `M${points[0].x - 20},${points[0].y} L${points[0].x + 20},${points[0].y}`;
    const areaD = `${pathD} L${points[points.length - 1].x},${baseY} L${points[0].x},${baseY} Z`;

    return { pathD, areaD, points, pathLen: Math.ceil(w * 1.25) };
  }

  private smoothCurve(pts: { x: number; y: number }[]): string {
    if (pts.length < 2) return '';
    let d = `M${pts[0].x.toFixed(1)},${pts[0].y.toFixed(1)}`;
    for (let i = 1; i < pts.length; i++) {
      const dx = (pts[i].x - pts[i - 1].x) / 3;
      const cp1x = pts[i - 1].x + dx;
      const cp2x = pts[i].x - dx;
      d += ` C${cp1x.toFixed(1)},${pts[i - 1].y.toFixed(1)} ${cp2x.toFixed(1)},${pts[i].y.toFixed(1)} ${pts[i].x.toFixed(1)},${pts[i].y.toFixed(1)}`;
    }
    return d;
  }

  private computePercentDelta(vals: number[]): { label: string; positive: boolean } {
    if (vals.length < 2) return { label: '0%', positive: true };
    const prev = vals[vals.length - 2] || 0;
    const curr = vals[vals.length - 1] || 0;
    if (prev === 0 && curr === 0) return { label: '0%', positive: true };
    if (prev === 0) return { label: '+100%', positive: true };
    const pct = Math.round(((curr - prev) / prev) * 100);
    return { label: (pct >= 0 ? '+' : '') + pct + '%', positive: pct >= 0 };
  }

  private computeTodayVsAvg(vals: number[]): { label: string; positive: boolean } {
    if (!vals.length) return { label: '0%', positive: true };
    const todayVal = vals[vals.length - 1] || 0;
    const avg = vals.reduce((s, v) => s + v, 0) / vals.length;
    if (avg === 0) return { label: '0%', positive: true };
    const pct = Math.round(((todayVal - avg) / avg) * 100);
    return { label: (pct >= 0 ? '+' : '') + pct + '%', positive: pct >= 0 };
  }

  showNewMemberTip(p: { x: number; y: number; label: string; value: number }, idx: number): void {
    this.activeNewMemberIdx = idx;
    const clampedX = Math.max(52, Math.min(360 - 52, p.x));
    const clampedY = Math.max(26, p.y - 10);
    this.newMemberTip = {
      x: clampedX,
      y: clampedY,
      boxX: clampedX - 48,
      label: p.label,
      value: p.value
    };
  }

  toggleNewMemberTip(p: { x: number; y: number; label: string; value: number }, idx: number): void {
    if (this.activeNewMemberIdx === idx && this.newMemberTip) {
      this.hideNewMemberTip();
    } else {
      this.showNewMemberTip(p, idx);
    }
  }

  hideNewMemberTip(): void {
    this.activeNewMemberIdx = null;
    this.newMemberTip = null;
  }

  private markUpdatedNow(): void {
    this.lastUpdatedTime = Date.now();
    this.lastUpdatedLabel = 'just now';
  }

  private updateLastUpdatedLabel(): void {
    if (!this.lastUpdatedTime) return;
    const diffSec = Math.floor((Date.now() - this.lastUpdatedTime) / 1000);
    if (diffSec < 60) {
      this.lastUpdatedLabel = 'just now';
    } else {
      const mins = Math.floor(diffSec / 60);
      this.lastUpdatedLabel = `${mins}m ago`;
    }
  }

  private countUp(
    field:
      | 'animTotalMembers'
      | 'animActiveMembers'
      | 'animPendingMembers'
      | 'animExpiredMembers'
      | 'animTotalTrainers'
      | 'animTotalPlans'
      | 'animTodayAttendance'
      | 'animMonthlyRevenue',
    target: number,
    preserveDecimals: boolean
  ): void {
    const existing = this.rafHandles.get(field);
    if (existing !== undefined && typeof cancelAnimationFrame !== 'undefined') {
      cancelAnimationFrame(existing);
      this.rafHandles.delete(field);
    }

    const reducedMotion =
      typeof requestAnimationFrame === 'undefined' ||
      (typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches);

    if (reducedMotion || target === 0) {
      (this as any)[field] = target;
      return;
    }

    const duration = 1200;
    const startVal = 0;
    const startTime = performance.now();

    const step = (now: number) => {
      const progress = Math.min((now - startTime) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      const val = startVal + (target - startVal) * eased;
      (this as any)[field] = preserveDecimals ? Math.round(val * 100) / 100 : Math.round(val);
      if (progress < 1) {
        this.rafHandles.set(field, requestAnimationFrame(step));
      } else {
        (this as any)[field] = target;
        this.rafHandles.delete(field);
      }
    };
    this.rafHandles.set(field, requestAnimationFrame(step));
  }

  private setupIntersectionObserver(): void {
    if (typeof IntersectionObserver === 'undefined') {
      this.revealAllRows();
      return;
    }
    this.observer = new IntersectionObserver(
      (entries) => {
        entries.forEach(entry => {
          if (entry.isIntersecting) {
            const el = entry.target as HTMLElement;
            const rowKey = el.getAttribute('data-row');
            el.classList.add('in-view');
            if (rowKey) {
              this.inViewRows[rowKey] = true;
            }
            this.observer?.unobserve(el);
          }
        });
      },
      { threshold: 0.12 }
    );
    this.observeAllSections();
  }

  private observeAllSections(): void {
    if (!this.observer || !this.chartSections) return;
    this.chartSections.forEach(ref => {
      const el = ref.nativeElement;
      const rowKey = el.getAttribute('data-row');
      if (rowKey && this.inViewRows[rowKey]) {
        el.classList.add('in-view');
        return;
      }
      this.observer?.observe(el);
    });
  }

  private revealAllRows(): void {
    Object.keys(this.inViewRows).forEach(k => (this.inViewRows[k] = true));
    this.chartSections?.forEach(ref => ref.nativeElement.classList.add('in-view'));
  }

  formatINR(val: number): string {
    const safe = Number.isFinite(val) ? val : 0;
    return '₹' + safe.toLocaleString('en-IN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.observer?.disconnect();
    if (this.refreshInterval) clearInterval(this.refreshInterval);
    if (this.ageTickerInterval) clearInterval(this.ageTickerInterval);
    if (typeof cancelAnimationFrame !== 'undefined') {
      this.rafHandles.forEach(id => cancelAnimationFrame(id));
    }
    this.rafHandles.clear();
  }
}
