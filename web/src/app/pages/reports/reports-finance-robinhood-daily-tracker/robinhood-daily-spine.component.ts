import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RhDailyMoneyPicture, RhDailyMoneyPicturePoint } from './rh-daily-money-picture.models';

type SpineLayer = 'all' | 'sale' | 'cash';

interface SpineBand {
  key: string;
  point: RhDailyMoneyPicturePoint;
  layer: SpineLayer;
}

@Component({
  selector: 'app-robinhood-daily-spine',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, DatePipe, MatButtonModule, MatIconModule],
  templateUrl: './robinhood-daily-spine.component.html',
  styleUrl: './robinhood-daily-spine.component.scss',
})
export class RobinhoodDailySpineComponent {
  @Input({ required: true }) picture!: RhDailyMoneyPicture;
  @Input() monthTitle = '';
  @Output() readonly openDay = new EventEmitter<RhDailyMoneyPicturePoint>();

  selectedDate = '';

  bands(): SpineBand[] {
    const out: SpineBand[] = [];
    for (const point of this.picture.points) {
      const split = point.sale.count > 0 && (point.added !== 0 || point.removed !== 0);
      if (split) {
        out.push({ key: `${point.date}-sale`, point, layer: 'sale' });
        out.push({ key: `${point.date}-cash`, point, layer: 'cash' });
      } else {
        out.push({ key: `${point.date}-all`, point, layer: 'all' });
      }
    }
    return out;
  }

  selected(): RhDailyMoneyPicturePoint | null {
    const date = this.selectedDate || this.defaultDate();
    return this.picture.points.find((p) => p.date === date) ?? this.picture.points.at(-1) ?? null;
  }

  select(point: RhDailyMoneyPicturePoint): void {
    this.selectedDate = point.date;
  }

  openSelected(): void {
    const point = this.selected();
    if (point) {
      this.openDay.emit(point);
    }
  }

  bookLeft(value: number | null): number {
    if (value == null) {
      return 24;
    }
    const books = this.picture.points.flatMap((p) => [p.book, p.bookIfNoIo]).filter((v): v is number => v != null);
    if (!books.length) {
      return 24;
    }
    const min = Math.min(...books);
    const max = Math.max(...books);
    if (min === max) {
      return 50;
    }
    return 24 + ((value - min) / (max - min)) * 68;
  }

  eventWidth(amount: number): number {
    const max = Math.max(
      1,
      ...this.picture.points.map((p) => Math.max(Math.abs(p.sale.net), p.added, p.removed)),
    );
    return Math.max(8, (Math.abs(amount) / max) * 72);
  }

  showSale(layer: SpineLayer, point: RhDailyMoneyPicturePoint): boolean {
    return layer !== 'cash' && point.sale.count > 0;
  }

  showCash(layer: SpineLayer, point: RhDailyMoneyPicturePoint): boolean {
    return layer !== 'sale' && (point.added !== 0 || point.removed !== 0);
  }

  bandLabel(band: SpineBand): string {
    const day = Number(band.point.date.slice(8, 10));
    if (band.layer === 'sale') {
      return `${day} sale`;
    }
    if (band.layer === 'cash') {
      return `${day} cash`;
    }
    return String(day);
  }

  selectedNote(point: RhDailyMoneyPicturePoint): string {
    const parts: string[] = [];
    if (point.sale.count) {
      parts.push(
        `FIFO sale ${point.sale.net >= 0 ? 'gain' : 'loss'} ${this.usd(point.sale.net)}${
          point.sale.symbols.length ? ` · ${point.sale.symbols.join(', ')}` : ''
        }`,
      );
    }
    if (point.added) {
      parts.push(`Cash added ${this.usd(point.added)}`);
    }
    if (point.removed) {
      parts.push(`Taken out ${this.usd(point.removed)}`);
    }
    if (!parts.length) {
      return 'No sale and no outside cash. Only the two books move.';
    }
    return parts.join('. ') + '.';
  }

  private defaultDate(): string {
    return this.picture.eventRows[0]?.date ?? this.picture.points.at(-1)?.date ?? '';
  }

  private usd(n: number): string {
    return n.toLocaleString('en-US', { style: 'currency', currency: 'USD' });
  }
}
