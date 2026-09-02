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
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  ManagementRecordingDayDto,
  ManagementRecordingDetailDto,
  ManagementRecordingImageDto,
  ManagementRecordingItemDto,
  ManagementRecordingListDto,
} from '../../../models/management.models';
import { ManagementApiService } from '../../../services/management-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import {
  RecordingImageGalleryDialogComponent,
  RecordingImageGalleryData,
} from './recording-image-gallery-dialog.component';

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
    MatDialogModule,
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
  private readonly dialog = inject(MatDialog);

  @ViewChild('audioEl') audioEl?: ElementRef<HTMLAudioElement>;
  @ViewChild('turnsList') turnsListEl?: ElementRef<HTMLElement>;

  loadingList = false;
  loadingDetail = false;
  transcribing = false;
  summarizing = false;
  searching = false;
  uploading = false;
  uploadingImages = false;
  downloading = false;
  droppingFolder = false;

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

  /** Thumbnail object URLs keyed by image id. */
  imagePreviewUrls = new Map<number, string>();

  /** When on, highlight + scroll the turn that matches audio.currentTime. */
  followPlayback = true;
  activeTurnIndex = -1;
  private processingPollId: number | null = null;

  ngOnInit(): void {
    this.refreshAll();
    this.processingPollId = window.setInterval(() => this.pollProcessing(), 5000);
  }

  ngOnDestroy(): void {
    if (this.processingPollId != null) {
      window.clearInterval(this.processingPollId);
    }
    this.revokeAudio();
    this.revokeImagePreviews();
  }

  get processingCount(): number {
    return this.recordings.filter(
      (r) => r.processingStatus === 'PENDING' || r.processingStatus === 'PROCESSING',
    ).length;
  }

  get images(): ManagementRecordingImageDto[] {
    return this.detail?.images ?? [];
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
    const collected = collectRecordingUploads(Array.from(fileList));
    input.value = '';
    this.uploadCollected(collected);
  }

  onFolderDragOver(ev: DragEvent): void {
    if (!ev.dataTransfer?.types?.includes('Files') || this.uploading) {
      return;
    }
    ev.preventDefault();
    ev.dataTransfer.dropEffect = 'copy';
    this.droppingFolder = true;
  }

  onFolderDragLeave(ev: DragEvent): void {
    const next = ev.relatedTarget as Node | null;
    if (next && (ev.currentTarget as Node).contains(next)) {
      return;
    }
    this.droppingFolder = false;
  }

  async onFolderDrop(ev: DragEvent): Promise<void> {
    ev.preventDefault();
    this.droppingFolder = false;
    if (this.uploading || !ev.dataTransfer) {
      return;
    }
    const collected = collectRecordingUploads(await filesFromDataTransfer(ev.dataTransfer));
    this.uploadCollected(collected);
  }

  get recordingGroups(): { folder: string; items: ManagementRecordingItemDto[] }[] {
    const map = new Map<string, ManagementRecordingItemDto[]>();
    for (const r of this.recordings) {
      const folder = parentFolder(r.path);
      const list = map.get(folder) ?? [];
      list.push(r);
      map.set(folder, list);
    }
    return [...map.entries()].map(([folder, items]) => ({ folder, items }));
  }

  private uploadCollected(collected: { file: File; relativePath: string }[]): void {
    if (collected.length === 0) {
      this.snackBar.open('No audio or image files found in that folder', 'Dismiss', { duration: 4000 });
      return;
    }
    const audioFirst = [...collected].sort((a, b) => {
      const aAudio = isAudioPath(a.relativePath) ? 0 : 1;
      const bAudio = isAudioPath(b.relativePath) ? 0 : 1;
      return aAudio - bAudio;
    });
    const files = audioFirst.map((c) => c.file);
    const relativePaths = audioFirst.map((c) => c.relativePath);
    this.uploading = true;
    this.api.uploadRecordings(files, relativePaths).subscribe({
      next: (res) => {
        this.uploading = false;
        const n = res.recordings?.length ?? 0;
        const photos = res.imageCount ?? 0;
        let msg: string;
        if (n && photos) {
          msg = `Uploaded ${n} recording(s) and kept ${photos} photo(s) in their folders. Transcript and summary will build in the background.`;
        } else if (n) {
          msg = `Uploaded ${n} recording(s). Transcript and summary will build in the background.`;
        } else {
          msg = `Kept ${photos} photo(s) with the matching recording(s).`;
        }
        this.snackBar.open(msg, 'Dismiss', { duration: 5000 });
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

  /** Queue (or force) rebuild transcript + summary for the selected recording. */
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
        this.scheduleImagePreviews(d.images ?? []);
        if (d.processingStatus === 'PENDING' || d.processingStatus === 'PROCESSING') {
          this.snackBar.open('Queued for transcript + summary', 'Dismiss', { duration: 3000 });
        } else {
          this.snackBar.open('Transcript and summary ready', 'Dismiss', { duration: 3000 });
        }
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
    this.revokeImagePreviews();
    this.loadAudio(item.path);
    this.loadDetail(item.path);
  }

  clearSelection(): void {
    this.selected = null;
    this.detail = null;
    this.editDisplayName = '';
    this.activeTurnIndex = -1;
    this.revokeAudio();
    this.revokeImagePreviews();
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
        this.scheduleImagePreviews(d.images ?? []);
        this.recordings = this.recordings.map((r) =>
          r.path === d.path ? { ...r, displayName: d.displayName } : r,
        );
        if (this.selected?.path === d.path) {
          this.selected = { ...this.selected, displayName: d.displayName };
        }
        this.snackBar.open(
          'Name updated in Tracker. Download the file to replace it in iCloud Drive.',
          'Dismiss',
          { duration: 4500 },
        );
      },
      error: (err) => {
        this.renaming = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Rename failed', 'Dismiss', {
          duration: 6000,
        });
      },
    });
  }

  downloadSelected(): void {
    if (!this.selected) {
      return;
    }
    this.downloading = true;
    this.api.getRecordingBlob(this.selected.path, 'attachment').subscribe({
      next: (blob) => {
        this.downloading = false;
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = this.selected!.displayName || 'recording.m4a';
        a.click();
        URL.revokeObjectURL(url);
        this.snackBar.open(
          'Downloaded with the Tracker name — drop into your Just Press Record / iCloud folder to sync.',
          'Dismiss',
          { duration: 5000 },
        );
      },
      error: (err) => {
        this.downloading = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Download failed', 'Dismiss', {
          duration: 6000,
        });
      },
    });
  }

  onImagesSelected(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const fileList = input.files;
    if (!this.selected || !fileList || fileList.length === 0) {
      return;
    }
    const files: File[] = [];
    for (let i = 0; i < fileList.length; i++) {
      const f = fileList.item(i);
      if (f && /\.(jpe?g|png|gif|webp|heic|heif)$/i.test(f.name)) {
        files.push(f);
      }
    }
    input.value = '';
    if (files.length === 0) {
      this.snackBar.open('No image files selected', 'Dismiss', { duration: 3000 });
      return;
    }
    this.uploadingImages = true;
    this.api.uploadRecordingImages(this.selected.path, files).subscribe({
      next: () => {
        this.uploadingImages = false;
        this.snackBar.open(`Uploaded ${files.length} image(s)`, 'Dismiss', { duration: 2500 });
        this.loadDetail(this.selected!.path);
      },
      error: (err) => {
        this.uploadingImages = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Image upload failed', 'Dismiss', {
          duration: 7000,
        });
      },
    });
  }

  openImageGallery(startIndex = 0): void {
    const images = this.images;
    if (images.length === 0) {
      return;
    }
    this.dialog.open<RecordingImageGalleryDialogComponent, RecordingImageGalleryData>(
      RecordingImageGalleryDialogComponent,
      {
        width: 'min(96vw, 56rem)',
        maxWidth: '96vw',
        data: { images, startIndex },
      },
    );
  }

  deleteImage(img: ManagementRecordingImageDto, ev?: Event): void {
    ev?.stopPropagation();
    if (!confirm(`Remove image “${img.originalFilename}”?`)) {
      return;
    }
    this.api.deleteRecordingImage(img.id).subscribe({
      next: () => {
        this.revokeOneImagePreview(img.id);
        if (this.detail) {
          this.detail = {
            ...this.detail,
            images: (this.detail.images ?? []).filter((i) => i.id !== img.id),
          };
        }
        this.snackBar.open('Image removed', 'Dismiss', { duration: 2000 });
      },
      error: (err) => {
        this.snackBar.open(formatHttpErrorDetail(err) || 'Could not delete image', 'Dismiss', {
          duration: 6000,
        });
      },
    });
  }

  imagePreviewUrl(id: number): string | null {
    return this.imagePreviewUrls.get(id) ?? null;
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
        this.scheduleImagePreviews(d.images ?? []);
      },
      error: (err) => {
        this.loadingDetail = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Failed to load detail', 'Dismiss', {
          duration: 5000,
        });
      },
    });
  }

  private pollProcessing(): void {
    if (
      this.loadingList ||
      this.searching ||
      !this.recordings.some(
        (r) => r.processingStatus === 'PENDING' || r.processingStatus === 'PROCESSING',
      )
    ) {
      return;
    }
    this.api.listRecordings(this.selectedDay).subscribe({
      next: (res) => {
        const updated = new Map(res.recordings.map((r) => [r.path, r]));
        this.recordings = this.recordings.map((r) => updated.get(r.path) ?? r);
        if (this.selected) {
          const selectedUpdate = updated.get(this.selected.path);
          if (selectedUpdate) {
            this.selected = selectedUpdate;
          }
        }
      },
    });
    if (
      this.selected &&
      (this.selected.processingStatus === 'PENDING' ||
        this.selected.processingStatus === 'PROCESSING')
    ) {
      const path = this.selected.path;
      this.api.getRecordingDetail(path).subscribe({
        next: (detail) => {
          if (this.selected?.path !== path) {
            return;
          }
          this.detail = detail;
          this.scheduleImagePreviews(detail.images ?? []);
          this.selected = {
            ...this.selected,
            hasTranscript: !!detail.transcript,
            hasSummary: !!detail.summary,
            processingStatus: detail.processingStatus,
            processingError: detail.processingError,
          };
          this.patchListFlags(detail);
        },
      });
    }
  }

  private scheduleImagePreviews(images: ManagementRecordingImageDto[]): void {
    const keep = new Set(images.map((i) => i.id));
    for (const id of [...this.imagePreviewUrls.keys()]) {
      if (!keep.has(id)) {
        this.revokeOneImagePreview(id);
      }
    }
    for (const img of images) {
      if (this.imagePreviewUrls.has(img.id)) {
        continue;
      }
      this.api.getRecordingImageBlob(img.id, 'inline').subscribe({
        next: (blob) => {
          if (!this.detail?.images?.some((i) => i.id === img.id)) {
            return;
          }
          this.imagePreviewUrls.set(img.id, URL.createObjectURL(blob));
        },
      });
    }
  }

  private revokeOneImagePreview(id: number): void {
    const url = this.imagePreviewUrls.get(id);
    if (url) {
      URL.revokeObjectURL(url);
      this.imagePreviewUrls.delete(id);
    }
  }

  private revokeImagePreviews(): void {
    for (const url of this.imagePreviewUrls.values()) {
      URL.revokeObjectURL(url);
    }
    this.imagePreviewUrls.clear();
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
      const end = turns[i].endSeconds;
      if (end != null && Number.isFinite(end) && t >= start && t < end) {
        return i;
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
    el?.scrollIntoView({ block: 'center', behavior: 'smooth' });
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

const AUDIO_RE = /\.(m4a|mp3|wav|webm|ogg)$/i;
const IMAGE_RE = /\.(jpe?g|png|gif|webp|heic|heif)$/i;

function parentFolder(path: string | null | undefined): string {
  if (!path) {
    return '';
  }
  const slash = path.lastIndexOf('/');
  return slash > 0 ? path.slice(0, slash) : '';
}

function leafName(path: string): string {
  const slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
  return slash >= 0 ? path.slice(slash + 1) : path;
}

function isAudioPath(path: string): boolean {
  return AUDIO_RE.test(leafName(path));
}

function isImagePath(path: string): boolean {
  return IMAGE_RE.test(leafName(path));
}

function isJunkPath(path: string): boolean {
  const leaf = leafName(path);
  if (!leaf || leaf.startsWith('.')) {
    return true;
  }
  const lower = leaf.toLowerCase();
  return lower === 'thumbs.db' || lower.endsWith('.icloud');
}

function collectRecordingUploads(files: File[]): { file: File; relativePath: string }[] {
  const out: { file: File; relativePath: string }[] = [];
  for (const file of files) {
    const rel = (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name;
    if (isJunkPath(rel) || (!isAudioPath(rel) && !isImagePath(rel))) {
      continue;
    }
    out.push({ file, relativePath: rel });
  }
  return out;
}

async function filesFromDataTransfer(dt: DataTransfer): Promise<File[]> {
  const items = dt.items;
  const entries: FileSystemEntry[] = [];
  if (items) {
    for (let i = 0; i < items.length; i++) {
      const entry = items[i].webkitGetAsEntry?.();
      if (entry) {
        entries.push(entry);
      }
    }
  }
  if (entries.length === 0) {
    return Array.from(dt.files || []);
  }
  const collected: File[] = [];
  for (const entry of entries) {
    await walkFileTree(entry, entry.name, collected);
  }
  return collected;
}

function walkFileTree(entry: FileSystemEntry, path: string, out: File[]): Promise<void> {
  if (entry.isFile) {
    return new Promise((resolve, reject) => {
      (entry as FileSystemFileEntry).file((file) => {
        Object.defineProperty(file, 'webkitRelativePath', { value: path, configurable: true });
        out.push(file);
        resolve();
      }, reject);
    });
  }
  if (!entry.isDirectory) {
    return Promise.resolve();
  }
  const reader = (entry as FileSystemDirectoryEntry).createReader();
  return new Promise((resolve, reject) => {
    const readBatch = (): void => {
      reader.readEntries(async (batch) => {
        if (!batch.length) {
          resolve();
          return;
        }
        try {
          for (const child of batch) {
            const childPath = path ? `${path}/${child.name}` : child.name;
            await walkFileTree(child, childPath, out);
          }
          readBatch();
        } catch (err) {
          reject(err);
        }
      }, reject);
    };
    readBatch();
  });
}
