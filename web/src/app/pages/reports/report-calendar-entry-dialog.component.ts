import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  REPORT_CALENDAR_TYPE_OPTIONS,
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
  ],
  templateUrl: './report-calendar-entry-dialog.component.html',
  styleUrl: './report-calendar-entry-dialog.component.scss',
})
export class ReportCalendarEntryDialogComponent implements OnInit {
  private readonly ref = inject(MatDialogRef<ReportCalendarEntryDialogComponent>);
  private readonly data = inject<ReportCalendarEntryDialogData>(MAT_DIALOG_DATA);
  private readonly api = inject(ReportCalendarApiService);
  private readonly fb = inject(FormBuilder);

  readonly typeOptions = REPORT_CALENDAR_TYPE_OPTIONS;
  saving = false;
  err: string | null = null;

  entryForm = this.fb.group({
    entryDate: [this.data.defaultDate, Validators.required],
    calendarType: [this.data.defaultType, Validators.required],
    title: [''],
    body: [''],
  });

  get title() {
    return this.data.entry ? 'Edit entry' : 'Add entry';
  }

  ngOnInit(): void {
    const e = this.data.entry;
    if (e) {
      this.entryForm.patchValue({
        entryDate: e.entryDate,
        calendarType: e.calendarType,
        title: e.title ?? '',
        body: e.body ?? '',
      });
    } else {
      this.entryForm.patchValue({ entryDate: this.data.defaultDate, calendarType: this.data.defaultType });
    }
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
    const e = this.data.entry;
    const op = e ? this.api.update(e.id, body) : this.api.create(body);
    op.subscribe({
      next: () => {
        this.saving = false;
        this.ref.close(true);
      },
      error: (err) => {
        this.saving = false;
        this.err = formatHttpErrorDetail(err);
      },
    });
  }

  cancel(): void {
    this.ref.close(false);
  }
}
