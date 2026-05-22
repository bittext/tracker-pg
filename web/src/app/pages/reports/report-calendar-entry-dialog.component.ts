import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  REPORT_CALENDAR_TYPE_OPTIONS,
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
  readonly dialogData = inject<ReportCalendarEntryDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<ReportCalendarEntryDialogComponent>);
  private readonly api = inject(ReportCalendarApiService);
  private readonly fb = inject(FormBuilder);

  readonly typeOptions = REPORT_CALENDAR_TYPE_OPTIONS;
  saving = false;
  uploading = false;
  err: string | null = null;
  savedEntryId: number | null = null;
  attachments: ReportCalendarAttachmentDto[] = [];
  readonly imagePreviewUrls = new Map<number, string>();

  previewOpen = false;
  previewLoading = false;
  previewError: string | null = null;
  previewAtt: ReportCalendarAttachmentDto | null = null;
  previewBlobUrl: string | null = null;
  /** True when previewBlobUrl was created for the overlay (not reused from thumbnail cache). */
  private previewOwnedUrl = false;

  entryForm = this.fb.group({
    entryDate: ['', Validators.required],
    calendarType: ['PERSONAL' as ReportCalendarType, Validators.required],
    title: [''],
    body: [''],
  });

  get dialogTitle() {
    return this.dialogData.entry || this.savedEntryId != null ? 'Edit entry' : 'Add entry';
  }

  get canManageAttachments(): boolean {
    return this.savedEntryId != null;
  }

  ngOnInit(): void {
    const e = this.dialogData.entry;
    if (e) {
      this.savedEntryId = e.id;
      this.attachments = [...(e.attachments ?? [])];
      this.entryForm.patchValue({
        entryDate: e.entryDate,
        calendarType: e.calendarType,
        title: e.title ?? '',
        body: e.body ?? '',
      });
      this.loadImagePreviews();
    } else {
      this.entryForm.patchValue({ entryDate: this.dialogData.defaultDate, calendarType: this.dialogData.defaultType });
    }
  }

  ngOnDestroy(): void {
    this.closePreview();
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
    };
    this.saving = true;
    const existingId = this.savedEntryId;
    const op = existingId != null ? this.api.update(existingId, body) : this.api.create(body);
    op.subscribe({
      next: (entry) => {
        this.saving = false;
        this.savedEntryId = entry.id;
        this.attachments = [...(entry.attachments ?? [])];
        if (this.dialogData.entry) {
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
    this.ref.close(this.savedEntryId != null && !this.dialogData.entry);
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

  /** Inline full-screen preview — works on iOS WebKit browsers (Safari, Orion) without nested dialogs. */
  openAttachment(att: ReportCalendarAttachmentDto): void {
    this.closePreview();
    this.previewAtt = att;
    this.previewOpen = true;
    this.previewError = null;
    this.previewLoading = false;

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
        this.previewBlobUrl = URL.createObjectURL(blob);
        this.previewOwnedUrl = true;
      },
      error: (err) => {
        this.previewLoading = false;
        this.previewError = formatHttpErrorDetail(err);
      },
    });
  }

  closePreview(): void {
    if (this.previewOwnedUrl && this.previewBlobUrl) {
      URL.revokeObjectURL(this.previewBlobUrl);
    }
    this.previewOpen = false;
    this.previewAtt = null;
    this.previewBlobUrl = null;
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

  private loadImagePreview(attachmentId: number): void {
    if (this.imagePreviewUrls.has(attachmentId)) {
      return;
    }
    this.api.getAttachmentBlob(attachmentId, 'inline').subscribe({
      next: (blob) => {
        this.imagePreviewUrls.set(attachmentId, URL.createObjectURL(blob));
      },
    });
  }

  private revokePreview(attachmentId: number): void {
    const url = this.imagePreviewUrls.get(attachmentId);
    if (url) {
      URL.revokeObjectURL(url);
      this.imagePreviewUrls.delete(attachmentId);
    }
  }
}
