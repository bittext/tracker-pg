import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  inject,
} from '@angular/core';
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

type TranscriptTurn = {
  speaker: string | null;
  text: string;
  startSeconds: number | null;
  endSeconds: number | null;
};

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

  @ViewChild('audioEl') audioEl?: ElementRef<HTMLAudioElement>;
  @ViewChild('turnsList') turnsListEl?: ElementRef<HTMLElement>;

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
  reprocessing = false;
  renaming = false;
  editDisplayName = '';

  /** When on, highlight + scroll the turn that matches audio.currentTime. */
  followPlayback = true;
  activeTurnIndex = -1;

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
        this.snackBar.open(
          `Uploaded ${items.length} recording(s). Use Transcribe / Summarize when ready.`,
          'Dismiss',
          { duration: 4500 },
        );
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

  /** Force rebuild transcript + summary for the selected recording (manual; can take several minutes). */
  reprocessSelected(): void {
    if (!this.selected) {
      return;
    }
    this.reprocessing = true;
    this.api.reprocessRecording(this.selected.path).subscribe({
      next: (d) => {
        this.reprocessing = false;
        this.detail = d;
        this.patchListFlags(d);
        this.snackBar.open('Transcript and summary ready', 'Dismiss', { duration: 3000 });
      },
      error: (err) => {
        this.reprocessing = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Reprocess failed', 'Dismiss', {
          duration: 8000,
        });
        this.loadDetail(this.selected!.path);
      },
    });
  }

  selectRecording(item: ManagementRecordingItemDto): void {
    this.selected = item;
    this.detail = null;
    this.editDisplayName = item.displayName;
    this.transcriptFilter = '';
    this.activeTurnIndex = -1;
    this.loadAudio(item.path);
    this.loadDetail(item.path);
  }

  clearSelection(): void {
    this.selected = null;
    this.detail = null;
    this.editDisplayName = '';
    this.activeTurnIndex = -1;
    this.revokeAudio();
  }

  saveRename(): void {
    if (!this.selected) {
      return;
    }
    const name = this.editDisplayName.trim();
    if (!name) {
      this.snackBar.open('Name cannot be empty', 'Dismiss', { duration: 3000 });
      return;
    }
    if (name === this.selected.displayName) {
      this.snackBar.open('Name unchanged', 'Dismiss', { duration: 2000 });
      return;
    }
    this.renaming = true;
    this.api.renameRecording(this.selected.path, name).subscribe({
      next: (d) => {
        this.renaming = false;
        this.detail = d;
        this.editDisplayName = d.displayName;
        this.recordings = this.recordings.map((r) =>
          r.path === d.path ? { ...r, displayName: d.displayName } : r,
        );
        if (this.selected?.path === d.path) {
          this.selected = { ...this.selected, displayName: d.displayName };
        }
        this.snackBar.open('Name updated in Tracker (not in iCloud)', 'Dismiss', { duration: 3500 });
      },
      error: (err) => {
        this.renaming = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Rename failed', 'Dismiss', {
          duration: 6000,
        });
      },
    });
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
        this.editDisplayName = d.displayName;
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
      r.path === d.path
        ? {
            ...r,
            hasTranscript: hasT,
            hasSummary: hasS,
            processingStatus: d.processingStatus,
            processingError: d.processingError,
          }
        : r,
    );
    if (this.selected?.path === d.path) {
      this.selected = {
        ...this.selected,
        hasTranscript: hasT,
        hasSummary: hasS,
        processingStatus: d.processingStatus,
        processingError: d.processingError,
      };
    }
  }

  get filteredTranscript(): string {
    return this.detail?.transcript ?? '';
  }

  get hasTimedTurns(): boolean {
    return this.transcriptTurns.some((t) => t.startSeconds != null);
  }

  /** Prefer server timed segments; fall back to blank-line speaker parsing. */
  get transcriptTurns(): TranscriptTurn[] {
    const q = this.transcriptFilter.trim().toLowerCase();
    const segments = this.detail?.segments;
    let turns: TranscriptTurn[];
    if (segments && segments.length > 0) {
      turns = segments
        .filter((s) => !!(s.text && s.text.trim()))
        .map((s) => ({
          speaker: s.speaker?.trim() || null,
          text: s.text.trim(),
          startSeconds: s.startSeconds ?? null,
          endSeconds: s.endSeconds ?? null,
        }));
    } else {
      const text = this.filteredTranscript;
      if (!text.trim()) {
        return [];
      }
      const blocks = text
        .split(/\n{2,}/)
        .map((b) => b.trim())
        .filter(Boolean);
      const speakerRe = /^(Speaker\s+[^:\n]+|SPEAKER[_\-\s]?\d+)\s*:\s*([\s\S]*)$/i;
      turns = blocks.map((block) => {
        const m = block.match(speakerRe);
        if (m) {
          return {
            speaker: m[1].trim(),
            text: (m[2] || '').trim(),
            startSeconds: null,
            endSeconds: null,
          };
        }
        const lines = block.split('\n');
        if (lines.length >= 2 && /^Speaker\s+.+:\s*$/i.test(lines[0].trim())) {
          return {
            speaker: lines[0].replace(/:\s*$/, '').trim(),
            text: lines.slice(1).join('\n').trim(),
            startSeconds: null,
            endSeconds: null,
          };
        }
        return { speaker: null as string | null, text: block, startSeconds: null, endSeconds: null };
      });
    }
    if (!q) {
      return turns;
    }
    return turns.filter(
      (t) =>
        (t.speaker && t.speaker.toLowerCase().includes(q)) ||
        t.text.toLowerCase().includes(q),
    );
  }

  onAudioTime(): void {
    if (!this.followPlayback || !this.hasTimedTurns) {
      return;
    }
    const t = this.audioEl?.nativeElement?.currentTime;
    if (t == null || !Number.isFinite(t)) {
      return;
    }
    const idx = this.findActiveTurnIndex(t);
    if (idx === this.activeTurnIndex) {
      return;
    }
    this.activeTurnIndex = idx;
    queueMicrotask(() => this.scrollActiveTurnIntoView());
  }

  seekToTurn(turn: TranscriptTurn, index: number): void {
    const audio = this.audioEl?.nativeElement;
    if (!audio || turn.startSeconds == null) {
      return;
    }
    audio.currentTime = Math.max(0, turn.startSeconds);
    this.activeTurnIndex = index;
    void audio.play().catch(() => {
      /* autoplay may be blocked until user presses play once */
    });
  }

  formatTurnTime(seconds: number | null | undefined): string {
    if (seconds == null || !Number.isFinite(seconds) || seconds < 0) {
      return '';
    }
    const total = Math.floor(seconds);
    const h = Math.floor(total / 3600);
    const m = Math.floor((total % 3600) / 60);
    const s = total % 60;
    const mm = h > 0 ? String(m).padStart(2, '0') : String(m);
    const ss = String(s).padStart(2, '0');
    return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
  }

  private findActiveTurnIndex(t: number): number {
    const turns = this.transcriptTurns;
    let idx = -1;
    for (let i = 0; i < turns.length; i++) {
      const start = turns[i].startSeconds;
      if (start == null) {
        continue;
      }
      if (start <= t) {
        idx = i;
      } else {
        break;
      }
    }
    return idx;
  }

  private scrollActiveTurnIntoView(): void {
    if (this.activeTurnIndex < 0 || !this.followPlayback) {
      return;
    }
    const root = this.turnsListEl?.nativeElement;
    if (!root) {
      return;
    }
    const el = root.querySelector(`[data-turn-index="${this.activeTurnIndex}"]`) as HTMLElement | null;
    el?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
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
