import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

interface DayOneEntry {
  id: number;
  dateIso: string;
  text: string;
  tags: string[];
  createdAtIso: string;
}

type JournalScope = 'day' | 'month' | 'year' | 'years' | 'all';

@Component({
  selector: 'app-day-one',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatSnackBarModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatChipsModule,
  ],
  templateUrl: './day-one.component.html',
  styleUrl: './day-one.component.scss',
})
export class DayOneComponent implements OnInit {
  private static readonly DAY_ONE_STORAGE_KEY = 'tracker.dayone.entries.v1';
  private readonly snackBar = inject(MatSnackBar);

  journalScope: JournalScope = 'day';
  journalSelectedIso = '';
  journalYearFrom = new Date().getFullYear();
  journalYearTo = new Date().getFullYear();
  journalSearchWords = '';
  journalSearchTag = '';
  journalComposeText = '';
  journalComposeTags = '';

  dayOneEntries: DayOneEntry[] = [];
  dayOneVisibleRows: DayOneEntry[] = [];
  dayOneTagOptions: string[] = [];

  ngOnInit(): void {
    this.journalSelectedIso = this.todayIso();
    this.loadDayOneLocal();
  }

  get journalSelectedDate(): Date {
    return this.dateFromIso(this.journalSelectedIso || this.todayIso());
  }

  set journalSelectedDate(v: Date | null) {
    if (!v) {
      return;
    }
    this.journalSelectedIso = this.toIsoDate(v);
    this.refreshDayOneVisibleEntries();
  }

  get journalMonthLabel(): string {
    const d = this.dateFromIso(this.journalSelectedIso || this.todayIso());
    return d.toLocaleString(undefined, { month: 'long', year: 'numeric' });
  }

  prevJournalDay(): void {
    const d = this.dateFromIso(this.journalSelectedIso || this.todayIso());
    d.setDate(d.getDate() - 1);
    this.journalSelectedIso = this.toIsoDate(d);
    this.refreshDayOneVisibleEntries();
  }

  nextJournalDay(): void {
    const d = this.dateFromIso(this.journalSelectedIso || this.todayIso());
    d.setDate(d.getDate() + 1);
    this.journalSelectedIso = this.toIsoDate(d);
    this.refreshDayOneVisibleEntries();
  }

  onDayOneFiltersChanged(): void {
    this.refreshDayOneVisibleEntries();
  }

  selectDayOneTag(tag: string): void {
    this.journalSearchTag = tag;
    this.refreshDayOneVisibleEntries();
  }

  saveDayOneEntry(): void {
    const text = (this.journalComposeText || '').trim();
    if (!text) {
      return;
    }
    const tags = (this.journalComposeTags || '')
      .split(',')
      .map((t) => t.trim())
      .filter((t) => !!t);
    const entry: DayOneEntry = {
      id: Date.now(),
      dateIso: this.journalSelectedIso || this.todayIso(),
      text,
      tags,
      createdAtIso: new Date().toISOString(),
    };
    this.dayOneEntries = [entry, ...this.dayOneEntries];
    this.journalComposeText = '';
    this.journalComposeTags = '';
    this.persistDayOneLocal();
    this.refreshDayOneDerived();
    this.snackBar.open('Day One entry saved', undefined, { duration: 2200 });
  }

  deleteDayOneEntry(row: DayOneEntry): void {
    this.dayOneEntries = this.dayOneEntries.filter((e) => e.id !== row.id);
    this.persistDayOneLocal();
    this.refreshDayOneDerived();
  }

  formatDayOneCreated(iso: string): string {
    const ms = Date.parse(iso);
    if (Number.isNaN(ms)) {
      return iso;
    }
    return new Date(ms).toLocaleString();
  }

  private loadDayOneLocal(): void {
    try {
      const raw = localStorage.getItem(DayOneComponent.DAY_ONE_STORAGE_KEY);
      if (!raw) {
        this.dayOneEntries = [];
        this.refreshDayOneDerived();
        return;
      }
      const parsed = JSON.parse(raw) as DayOneEntry[];
      this.dayOneEntries = Array.isArray(parsed)
        ? parsed.filter((e) => !!e && !!e.dateIso && !!e.text && Array.isArray(e.tags))
        : [];
      this.refreshDayOneDerived();
    } catch {
      this.dayOneEntries = [];
      this.refreshDayOneDerived();
    }
  }

  private persistDayOneLocal(): void {
    localStorage.setItem(DayOneComponent.DAY_ONE_STORAGE_KEY, JSON.stringify(this.dayOneEntries));
  }

  private refreshDayOneDerived(): void {
    this.dayOneTagOptions = this.computeDayOneAllTags();
    this.refreshDayOneVisibleEntries();
  }

  private refreshDayOneVisibleEntries(): void {
    this.dayOneVisibleRows = this.computeDayOneVisibleEntries();
  }

  private computeDayOneAllTags(): string[] {
    const s = new Set<string>();
    for (const e of this.dayOneEntries) {
      for (const t of e.tags) {
        const n = t.trim();
        if (n) {
          s.add(n);
        }
      }
    }
    return [...s].sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }));
  }

  private computeDayOneVisibleEntries(): DayOneEntry[] {
    const scope = this.journalScope;
    const sel = this.journalSelectedIso || this.todayIso();
    const words = (this.journalSearchWords || '').trim().toLowerCase();
    const tagQ = (this.journalSearchTag || '').trim().toLowerCase();
    const fromYear = Math.min(this.journalYearFrom, this.journalYearTo);
    const toYear = Math.max(this.journalYearFrom, this.journalYearTo);

    return [...this.dayOneEntries]
      .filter((e) => {
        if (scope === 'day') {
          return e.dateIso === sel;
        }
        if (scope === 'month') {
          return e.dateIso.slice(0, 7) === sel.slice(0, 7);
        }
        if (scope === 'year') {
          return e.dateIso.slice(0, 4) === sel.slice(0, 4);
        }
        if (scope === 'years') {
          const y = Number(e.dateIso.slice(0, 4));
          return Number.isFinite(y) && y >= fromYear && y <= toYear;
        }
        return true;
      })
      .filter((e) => {
        if (!words) {
          return true;
        }
        const hay = `${e.text} ${e.tags.join(' ')}`.toLowerCase();
        return hay.includes(words);
      })
      .filter((e) => {
        if (!tagQ) {
          return true;
        }
        return e.tags.some((t) => t.toLowerCase().includes(tagQ));
      })
      .sort((a, b) => b.dateIso.localeCompare(a.dateIso) || b.id - a.id);
  }

  private todayIso(): string {
    const d = new Date();
    const y = d.getFullYear();
    const mo = `${d.getMonth() + 1}`.padStart(2, '0');
    const day = `${d.getDate()}`.padStart(2, '0');
    return `${y}-${mo}-${day}`;
  }

  private dateFromIso(iso: string): Date {
    const y = Number(iso.slice(0, 4));
    const m = Number(iso.slice(5, 7));
    const d = Number(iso.slice(8, 10));
    return new Date(y, m - 1, d);
  }

  private toIsoDate(d: Date): string {
    const y = d.getFullYear();
    const m = `${d.getMonth() + 1}`.padStart(2, '0');
    const day = `${d.getDate()}`.padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}
