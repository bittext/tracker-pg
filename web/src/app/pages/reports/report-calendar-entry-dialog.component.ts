import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { CommonModule } from '@angular/common';
import {
  Component,
  OnDestroy,
  OnInit,
  TemplateRef,
  ViewChild,
  ViewContainerRef,
  inject,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import {
  reportCalendarTypeOptionsFromProvisioned,
  ReportCalendarAttachmentDto,
  ReportCalendarEntryDto,
  ReportCalendarType,
} from '../../models/report-calendar.models';
import { ReportCalendarApiService } from '../../services/report-calendar-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';

export interface ReportCalendarEntryDialogData {
  entry: ReportCalendarEntryDto | null;
  defaultDate: string;
  defaultType: ReportCalendarType;
  typeOptions?: ReadonlyArray<{ value: ReportCalendarType; label: string }>;
  /** When length > 1, prev/next browse through these entries in edit mode. */
  browseEntries?: ReportCalendarEntryDto[];
  browseIndex?: number;
}

@Component({
  selector: 'app-report-calendar-entry-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './report-calendar-entry-dialog.component.html',
  styleUrl: './report-calendar-entry-dialog.component.scss',
})
export class ReportCalendarEntryDialogComponent implements OnInit, OnDestroy {
  @ViewChild('rcPreviewTpl') rcPreviewTpl!: TemplateRef<unknown>;

  readonly dialogData = inject<ReportCalendarEntryDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<ReportCalendarEntryDialogComponent>);
  private readonly api = inject(ReportCalendarApiService);
  private readonly fb = inject(FormBuilder);
  private readonly overlay = inject(Overlay);
  private readonly vcr = inject(ViewContainerRef);
  private readonly dom = inject(DomSanitizer);
  private previewOverlayRef: OverlayRef | null = null;

  readonly typeOptions =
    this.dialogData.typeOptions ?? reportCalendarTypeOptionsFromProvisioned([]);
  /** Mutable copy so saves update the in-dialog browse list. */
  browseEntries: ReportCalendarEntryDto[] = [
    ...(this.dialogData.browseEntries?.length
      ? this.dialogData.browseEntries
      : this.dialogData.entry
        ? [this.dialogData.entry]
        : []),
  ];
  browseIndex = this.dialogData.browseIndex ?? 0;
  saving = false;
  uploading = false;
  err: string | null = null;
  /** True when at least one save succeeded while browsing. */
  private browseSaved = false;
  savedEntryId: number | null = null;
  attachments: ReportCalendarAttachmentDto[] = [];
  readonly imagePreviewUrls = new Map<number, string>();

  previewOpen = false;
  previewLoading = false;
  previewError: string | null = null;
  previewAtt: ReportCalendarAttachmentDto | null = null;
  previewBlobUrl: string | null = null;
  previewSafePdfUrl: SafeResourceUrl | null = null;
  /** True when previewBlobUrl was created for the overlay (not reused from thumbnail cache). */
  private previewOwnedUrl = false;

  entryForm = this.fb.group({
    entryDate: ['', Validators.required],
    calendarType: ['PERSONAL' as ReportCalendarType, Validators.required],
    title: [''],
    body: [''],
    details: [''],
  });

  get dialogTitle() {
    const base = this.dialogData.entry || this.savedEntryId != null ? 'Edit entry' : 'Add entry';
    if (this.canBrowse) {
      return `${base} (${this.browseIndex + 1} of ${this.browseEntries.length})`;
    }
    return base;
  }

  get canBrowse(): boolean {
    return this.browseEntries.length > 1;
  }

  get browseAtStart(): boolean {
    return this.browseIndex <= 0;
  }

  get browseAtEnd(): boolean {
    return this.browseIndex >= this.browseEntries.length - 1;
  }

  get canManageAttachments(): boolean {
    return this.savedEntryId != null;
  }

  ngOnInit(): void {
    const e = this.browseEntries[this.browseIndex] ?? this.dialogData.entry;
    if (e) {
      this.loadEntryIntoForm(e);
    } else {
      this.entryForm.patchValue({ entryDate: this.dialogData.defaultDate, calendarType: this.dialogData.defaultType });
    }
  }

  ngOnDestroy(): void {
    this.closePreview();
    this.previewOverlayRef?.dispose();
    this.previewOverlayRef = null;
    for (const url of this.imagePreviewUrls.values()) {
      URL.revokeObjectURL(url);
    }
    this.imagePreviewUrls.clear();
  }

  save(): void {
    this.err = null;
    if (this.entryForm.invalid) {
      return;
    }
    const v = this.entryForm.getRawValue();
    const body = {
      entryDate: v.entryDate!,
      calendarType: v.calendarType as ReportCalendarType,
      title: (v.title ?? '').trim() || null,
      body: (v.body ?? '').trim() || null,
      details: (v.details ?? '').trim() || null,
    };
    this.saving = true;
    const existingId = this.savedEntryId;
    const op = existingId != null ? this.api.update(existingId, body) : this.api.create(body);
    op.subscribe({
      next: (entry) => {
        this.saving = false;
        this.savedEntryId = entry.id;
        this.attachments = [...(entry.attachments ?? [])];
        if (this.canBrowse) {
          this.browseEntries[this.browseIndex] = entry;
          this.entryForm.markAsPristine();
          this.browseSaved = true;
        } else if (this.dialogData.entry) {
          this.ref.close(true);
        }
      },
      error: (err) => {
        this.saving = false;
        this.err = formatHttpErrorDetail(err);
      },
    });
  }

  done(): void {
    this.ref.close(true);
  }

  cancel(): void {
    if (this.canBrowse) {
      this.closeBrowse();
      return;
    }
    this.ref.close(this.savedEntryId != null && !this.dialogData.entry);
  }

  closeBrowse(): void {
    this.ref.close(this.browseSaved);
  }

  browsePrevious(): void {
    this.navigateBrowse(-1);
  }

  browseNext(): void {
    this.navigateBrowse(1);
  }

  private navigateBrowse(delta: number): void {
    if (!this.canBrowse || this.saving || this.uploading) {
      return;
    }
    const next = this.browseIndex + delta;
    if (next < 0 || next >= this.browseEntries.length) {
      return;
    }
    if (this.entryForm.dirty) {
      if (typeof window !== 'undefined' && !window.confirm('Discard unsaved changes and open another entry?')) {
        return;
      }
    }
    this.browseIndex = next;
    this.loadEntryIntoForm(this.browseEntries[next]);
  }

  private loadEntryIntoForm(e: ReportCalendarEntryDto): void {
    this.closePreview();
    this.err = null;
    this.clearImagePreviews();
    this.savedEntryId = e.id;
    this.attachments = [...(e.attachments ?? [])];
    this.entryForm.reset({
      entryDate: e.entryDate,
      calendarType: e.calendarType,
      title: e.title ?? '',
      body: e.body ?? '',
      details: e.details ?? '',
    });
    this.entryForm.markAsPristine();
    this.loadImagePreviews();
  }

  onFilesSelected(event: Event): void {
    const entryId = this.savedEntryId;
    if (entryId == null) {
      return;
    }
    const input = event.target as HTMLInputElement;
    const files = input.files;
    if (!files?.length) {
      return;
    }
    this.uploading = true;
    const list = Array.from(files);
    let i = 0;
    const step = (): void => {
      if (i >= list.length) {
        this.uploading = false;
        input.value = '';
        return;
      }
      this.api.uploadAttachment(entryId, list[i]).subscribe({
        next: (att) => {
          this.attachments = [...this.attachments, att];
          if (this.isImageAttachment(att)) {
            this.loadImagePreview(att.id);
          }
          i += 1;
          step();
        },
        error: (err) => {
          this.uploading = false;
          input.value = '';
          this.err = formatHttpErrorDetail(err);
        },
      });
    };
    step();
  }

  /** Full-screen preview on document.body (CDK overlay) — avoids iOS WebKit clipping inside mat-dialog. */
  openAttachment(att: ReportCalendarAttachmentDto): void {
    this.closePreview();
    this.previewAtt = att;
    this.previewOpen = true;
    this.previewError = null;
    this.previewLoading = false;
    this.previewSafePdfUrl = null;
    this.attachPreviewOverlay();

    const cached = this.imagePreviewUrls.get(att.id);
    if (cached && this.isImageAttachment(att)) {
      this.previewBlobUrl = cached;
      this.previewOwnedUrl = false;
      return;
    }

    this.previewLoading = true;
    this.api.getAttachmentBlob(att.id, 'inline').subscribe({
      next: (blob) => {
        this.previewLoading = false;
        this.previewBlobUrl = this.createBlobUrl(blob, att);
        this.previewOwnedUrl = true;
        if (this.isPdfAttachment(att)) {
          this.previewSafePdfUrl = this.dom.bypassSecurityTrustResourceUrl(this.previewBlobUrl);
        }
      },
      error: (err) => {
        this.previewLoading = false;
        this.previewError = formatHttpErrorDetail(err);
      },
    });
  }

  /** Direct tap handler — iOS blocks blob links with target=_blank after async fetch. */
  openPreviewBlob(): void {
    if (!this.previewBlobUrl) {
      return;
    }
    window.location.assign(this.previewBlobUrl);
  }

  closePreview(): void {
    this.detachPreviewOverlay();
    if (this.previewOwnedUrl && this.previewBlobUrl) {
      URL.revokeObjectURL(this.previewBlobUrl);
    }
    this.previewOpen = false;
    this.previewAtt = null;
    this.previewBlobUrl = null;
    this.previewSafePdfUrl = null;
    this.previewOwnedUrl = false;
    this.previewLoading = false;
    this.previewError = null;
  }

  removeAttachment(att: ReportCalendarAttachmentDto, ev: Event): void {
    ev.stopPropagation();
    if (this.previewAtt?.id === att.id) {
      this.closePreview();
    }
    this.api.deleteAttachment(att.id).subscribe({
      next: () => {
        this.revokePreview(att.id);
        this.attachments = this.attachments.filter((a) => a.id !== att.id);
      },
      error: (err) => {
        this.err = formatHttpErrorDetail(err);
      },
    });
  }

  isImageAttachment(att: ReportCalendarAttachmentDto | null | undefined): boolean {
    if (!att) {
      return false;
    }
    const ct = att.contentType?.toLowerCase() ?? '';
    if (ct.startsWith('image/')) {
      return true;
    }
    return /\.(jpe?g|png|gif|webp|bmp|svg|heic|heif)$/i.test(att.originalFilename);
  }

  isPdfAttachment(att: ReportCalendarAttachmentDto | null | undefined): boolean {
    if (!att) {
      return false;
    }
    const ct = att.contentType?.toLowerCase() ?? '';
    return ct === 'application/pdf' || att.originalFilename.toLowerCase().endsWith('.pdf');
  }

  previewUrl(att: ReportCalendarAttachmentDto): string | null {
    return this.imagePreviewUrls.get(att.id) ?? null;
  }

  private loadImagePreviews(): void {
    for (const att of this.attachments) {
      if (this.isImageAttachment(att)) {
        this.loadImagePreview(att.id);
      }
    }
  }

  private clearImagePreviews(): void {
    for (const url of this.imagePreviewUrls.values()) {
      URL.revokeObjectURL(url);
    }
    this.imagePreviewUrls.clear();
  }

  private loadImagePreview(attachmentId: number): void {
    if (this.imagePreviewUrls.has(attachmentId)) {
      return;
    }
    this.api.getAttachmentBlob(attachmentId, 'inline').subscribe({
      next: (blob) => {
        const att = this.attachments.find((a) => a.id === attachmentId);
        const url = att ? this.createBlobUrl(blob, att) : URL.createObjectURL(blob);
        this.imagePreviewUrls.set(attachmentId, url);
      },
    });
  }

  private attachPreviewOverlay(): void {
    if (!this.rcPreviewTpl || this.previewOverlayRef?.hasAttached()) {
      return;
    }
    if (!this.previewOverlayRef) {
      this.previewOverlayRef = this.overlay.create({
        hasBackdrop: false,
        scrollStrategy: this.overlay.scrollStrategies.block(),
        positionStrategy: this.overlay.position().global().left('0').top('0'),
        width: '100%',
        height: '100%',
        panelClass: 'rc-preview-overlay-host',
      });
    }
    this.previewOverlayRef.attach(new TemplatePortal(this.rcPreviewTpl, this.vcr));
  }

  private detachPreviewOverlay(): void {
    this.previewOverlayRef?.detach();
  }

  private createBlobUrl(blob: Blob, att: ReportCalendarAttachmentDto): string {
    const type =
      att.contentType?.split(';')[0]?.trim().toLowerCase() ||
      blob.type ||
      'application/octet-stream';
    const body = blob.type === type ? blob : new Blob([blob], { type });
    return URL.createObjectURL(body);
  }

  private revokePreview(attachmentId: number): void {
    const url = this.imagePreviewUrls.get(attachmentId);
    if (url) {
      URL.revokeObjectURL(url);
      this.imagePreviewUrls.delete(attachmentId);
    }
  }
}
