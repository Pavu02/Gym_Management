import { Component, Input, OnChanges } from '@angular/core';
import { TrainerLoad } from '../../services/dashboard.service';

@Component({
  selector: 'app-progress-bars',
  templateUrl: './progressbars.component.html',
  styleUrls: ['./progressbars.component.css']
})
export class ProgressbarsComponent implements OnChanges {
  @Input() items: TrainerLoad[] = [];
  @Input() inView = false;

  bars: {
    name: string;
    members: number;
    max: number;
    pct: number;
    barW: number;
    overloaded: boolean;
  }[] = [];

  ngOnChanges(): void {
    this.bars = (this.items || []).map(t => {
      const safeMax = t.max > 0 ? t.max : 15;
      const rawPct = (t.members / safeMax) * 100;
      const pct = Number.isFinite(rawPct) ? Math.min(Math.round(rawPct), 100) : 0;
      return {
        name: t.name,
        members: t.members,
        max: safeMax,
        pct,
        barW: Math.max((pct / 100) * 320, t.members > 0 ? 6 : 0),
        overloaded: rawPct > 90
      };
    });
  }
}
