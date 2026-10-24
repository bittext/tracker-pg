import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Btc15StreakDayDto, Btc15StreakDeskDto, Btc15StreakRunDto } from '../../../models/rh-predict.models';
import { RhPredictApiService } from '../../../services/rh-predict-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-markets-predict-streaks',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatProgressBarModule, MatSnackBarModule, RouterLink],
  templateUrl: './markets-predict-streaks.component.html',
  styleUrl: './markets-predict-streaks.component.scss',
})
export class MarketsPredictStreaksComponent implements OnInit {
  private readonly api = inject(RhPredictApiService);
  private readonly snack = inject(MatSnackBar);
  private readonly route = inject(ActivatedRoute);

  locked = false;
  loading = false;
  series = 'KXBTC15M';
  backLink = '/markets/predict/btc-15';
  backLabel = 'BTC 15 min';
  from = '';
  to = '';
  preset: 'today' | 'yesterday' | 'week' | 'custom' = 'week';
  desk: Btc15StreakDeskDto | null = null;
  readonly choices = [
    { id: 'KXBTC15M', label: 'BTC 15 min' },
    { id: 'KXETH15M', label: 'ETH 15 min' },
    { id: 'KXDOGE15M', label: 'DOGE 15 min' },
    { id: 'KXSOL15M', label: 'SOL 15 min' },
  ];

  ngOnInit(): void {
    const data = this.route.snapshot.data;
    this.locked = data['locked'] === true;
    const series = data['series'];
    if (typeof series === 'string' && series) {
      this.series = series;
    }
    const home = quarterHome(this.series);
    if (home) {
      this.backLink = home.link;
      this.backLabel = home.label;
    }
    this.choose('week');
  }

  choose(preset: 'today' | 'yesterday' | 'week'): void {
    const today = centralToday();
    this.preset = preset;
    this.to = today;
    if (preset === 'today') {
      this.from = today;
    } else if (preset === 'yesterday') {
      const prior = shiftDate(today, -1);
      this.from = prior;
      this.to = prior;
    } else {
      this.from = shiftDate(today, -6);
    }
    this.load();
  }

  onRangeChange(): void {
    this.preset = 'custom';
    this.load();
  }

  pickSeries(series: string): void {
    this.series = series;
    this.load();
  }

  lengths(desk: Btc15StreakDeskDto): { length: number; count: number }[] {
    return [
      { length: 2, count: desk.run2 },
      { length: 3, count: desk.run3 },
      { length: 4, count: desk.run4 },
      { length: 5, count: desk.run5 },
      { length: 6, count: desk.run6 },
      { length: 7, count: desk.run7 },
    ];
  }

  barWidth(count: number, desk: Btc15StreakDeskDto): string {
    const max = Math.max(desk.run2, desk.run3, desk.run4, desk.run5, desk.run6, desk.run7, 1);
    return `${Math.max(6, Math.round((count / max) * 100))}%`;
  }

  dayPills(day: Btc15StreakDayDto): { length: number; count: number }[] {
    return [
      { length: 2, count: day.run2 },
      { length: 3, count: day.run3 },
      { length: 4, count: day.run4 },
      { length: 5, count: day.run5 },
      { length: 6, count: day.run6 },
      { length: 7, count: day.run7 },
    ].filter((pill) => pill.count > 0);
  }

  runsOn(day: string): Btc15StreakRunDto[] {
    return (this.desk?.runs ?? []).filter((run) => run.date === day);
  }

  load(): void {
    if (!this.from || !this.to) {
      return;
    }
    this.loading = true;
    this.api.streaks(this.series.trim().toUpperCase(), this.from, this.to).subscribe({
      next: (desk) => {
        this.desk = desk;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.snack.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 6000 });
      },
    });
  }
}

function quarterHome(series: string): { link: string; label: string } | null {
  switch (series) {
    case 'KXETH15M':
      return { link: '/markets/predict/eth-15', label: 'ETH 15 min' };
    case 'KXDOGE15M':
      return { link: '/markets/predict/doge-15', label: 'DOGE 15 min' };
    case 'KXSOL15M':
      return { link: '/markets/predict/sol-15', label: 'SOL 15 min' };
    case 'KXBTC15M':
      return { link: '/markets/predict/btc-15', label: 'BTC 15 min' };
    default:
      return null;
  }
}

function centralToday(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Chicago',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
}

function shiftDate(iso: string, days: number): string {
  const [year, month, day] = iso.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}
