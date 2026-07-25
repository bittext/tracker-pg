import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  ManagementRecordingDayDto,
  ManagementRecordingDetailDto,
  ManagementRecordingItemDto,
  ManagementRecordingListDto,
} from '../../../models/management.models';
import { ManagementApiService } from '../../../services/management-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-management-recordings-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
  ],
  templateUrl: './management-recordings-panel.component.html',
  styleUrl: './management-recordings-panel.component.scss',
})
export class ManagementRecordingsPanelComponent implements OnInit, OnDestroy {
  private readonly api = inject(ManagementApiService);
  private readonly snackBar = inject(MatSnackBar);

  loadingList = false;
  loadingDetail = false;
  transcribing = false;
  summarizing = false;
  searching = false;
  uploading = false;

  list: ManagementRecordingListDto | null = null;
  days: ManagementRecordingDayDto[] = [];
  recordings: ManagementRecordingItemDto[] = [];
  selectedDay: string | null = null;
  searchQuery = '';
  searchActive = false;

  selected: ManagementRecordingItemDto | null = null;
  detail: ManagementRecordingDetailDto | null = null;
  transcriptFilter = '';

  audioObjectUrl: string | null = null;
  audioError = false;
  audioLoading = false;

  ngOnInit(): void {
    this.refreshAll();
  }

  ngOnDestroy(): void {
    this.revokeAudio();
  }

  refreshAll(): void {
    this.loadList(this.selectedDay);
  }

  loadList(day: string | null = this.selectedDay): void {
    this.loadingList = true;
    this.searchActive = false;
    this.searchQuery = '';
    this.api.listRecordings(day).subscribe({
      next: (res) => {
        this.list = res;
        this.days = res.days ?? [];
        this.recordings = res.recordings ?? [];
        this.loadingList = false;
        if (this.selected && !this.recordings.some((r) => r.path === this.selected!.path)) {
          this.clearSelection();
        }
      },
      error: (err) => {
        this.loadingList = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Failed to load recordings', 'Dismiss', {
          duration: 6000,
        });
      },
    });
  }

  selectDay(day: string | null): void {
    this.selectedDay = day;
    this.loadList(day);
  }

  onUploadSelected(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const fileList = input.files;
    if (!fileList || fileList.length === 0) {
      return;
    }
    const files: File[] = [];
    const relativePaths: string[] = [];
    for (let i = 0; i < fileList.length; i++) {
      const f = fileList.item(i);
      if (!f) {
        continue;
      }
      const rel = (f as File & { webkitRelativePath?: string }).webkitRelativePath || f.name;
      if (!/\.(m4a|mp3|wav|webm|ogg)$/i.test(rel)) {
        continue;
      }
      files.push(f);
      relativePaths.push(rel);
    }
    input.value = '';
    if (files.length === 0) {
      this.snackBar.open('No audio files found in that selection', 'Dismiss', { duration: 4000 });
      return;
    }
    this.uploading = true;
    this.api.uploadRecordings(files, relativePaths).subscribe({
      next: (items) => {
        this.uploading = false;
        this.snackBar.open(`Uploaded ${items.length} recording(s)`, 'Dismiss', { duration: 3000 });
        this.refreshAll();
      },
      error: (err) => {
        this.uploading = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Upload failed', 'Dismiss', { duration: 8000 });
      },
    });
  }

  runSearch(): void {
    const q = this.searchQuery.trim();
    if (q.length < 2) {
      this.snackBar.open('Enter at least 2 characters to search', 'Dismiss', { duration: 3000 });
      return;
    }
    this.searching = true;
    this.api.searchRecordings(q).subscribe({
      next: (items) => {
        this.recordings = items;
        this.searchActive = true;
        this.searching = false;
      },
      error: (err) => {
        this.searching = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Search failed', 'Dismiss', { duration: 6000 });
      },
    });
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.searchActive = false;
    this.loadList(this.selectedDay);
  }

  selectRecording(item: ManagementRecordingItemDto): void {
    this.selected = item;
    this.detail = null;
    this.transcriptFilter = '';
    this.loadAudio(item.path);
    this.loadDetail(item.path);
  }

  clearSelection(): void {
    this.selected = null;
    this.detail = null;
    this.revokeAudio();
  }

  deleteSelected(): void {
    if (!this.selected) {
      return;
    }
    const path = this.selected.path;
    this.api.deleteRecording(path).subscribe({
      next: () => {
        this.clearSelection();
        this.refreshAll();
        this.snackBar.open('Recording deleted', 'Dismiss', { duration: 2500 });
      },
      error: (err) => {
        this.snackBar.open(formatHttpErrorDetail(err) || 'Delete failed', 'Dismiss', { duration: 6000 });
      },
    });
  }

  private loadDetail(path: string): void {
    this.loadingDetail = true;
    this.api.getRecordingDetail(path).subscribe({
      next: (d) => {
        this.detail = d;
        this.loadingDetail = false;
        this.patchListFlags(d);
      },
      error: (err) => {
        this.loadingDetail = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Failed to load detail', 'Dismiss', {
          duration: 5000,
        });
      },
    });
  }

  private loadAudio(path: string): void {
    this.revokeAudio();
    this.audioError = false;
    this.audioLoading = true;
    this.api.getRecordingBlob(path, 'inline').subscribe({
      next: (blob) => {
        this.audioObjectUrl = URL.createObjectURL(blob);
        this.audioLoading = false;
      },
      error: () => {
        this.audioLoading = false;
        this.audioError = true;
      },
    });
  }

  private revokeAudio(): void {
    if (this.audioObjectUrl) {
      URL.revokeObjectURL(this.audioObjectUrl);
      this.audioObjectUrl = null;
    }
  }

  transcribe(force = false): void {
    if (!this.selected) {
      return;
    }
    this.transcribing = true;
    this.api.transcribeRecording(this.selected.path, force).subscribe({
      next: (d) => {
        this.detail = d;
        this.transcribing = false;
        this.patchListFlags(d);
        this.snackBar.open('Transcript ready', 'Dismiss', { duration: 2500 });
      },
      error: (err) => {
        this.transcribing = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Transcription failed', 'Dismiss', {
          duration: 8000,
        });
      },
    });
  }

  summarize(force = false): void {
    if (!this.selected) {
      return;
    }
    this.summarizing = true;
    this.api.summarizeRecording(this.selected.path, force).subscribe({
      next: (d) => {
        this.detail = d;
        this.summarizing = false;
        this.patchListFlags(d);
        this.snackBar.open('Summary ready', 'Dismiss', { duration: 2500 });
      },
      error: (err) => {
        this.summarizing = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Summarize failed', 'Dismiss', {
          duration: 8000,
        });
      },
    });
  }

  private patchListFlags(d: ManagementRecordingDetailDto): void {
    const hasT = !!(d.transcript && d.transcript.trim());
    const hasS = !!(d.summary && d.summary.trim());
    this.recordings = this.recordings.map((r) =>
      r.path === d.path ? { ...r, hasTranscript: hasT, hasSummary: hasS } : r,
    );
    if (this.selected?.path === d.path) {
      this.selected = { ...this.selected, hasTranscript: hasT, hasSummary: hasS };
    }
  }

  get filteredTranscript(): string {
    const text = this.detail?.transcript ?? '';
    const q = this.transcriptFilter.trim().toLowerCase();
    if (!q || !text) {
      return text;
    }
    return text
      .split(/\n+/)
      .filter((line) => line.toLowerCase().includes(q))
      .join('\n');
  }

  formatBytes(n: number): string {
    if (n < 1024) {
      return `${n} B`;
    }
    if (n < 1024 * 1024) {
      return `${(n / 1024).toFixed(1)} KB`;
    }
    return `${(n / (1024 * 1024)).toFixed(1)} MB`;
  }

  formatDay(iso: string | null | undefined): string {
    if (!iso || iso.length < 10) {
      return '—';
    }
    const y = Number(iso.slice(0, 4));
    const m = Number(iso.slice(5, 7));
    const d = Number(iso.slice(8, 10));
    return new Date(y, m - 1, d).toLocaleDateString(undefined, {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  }
}
