import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import {
  DayOneCalendarDayDto,
  DayOneCountsDto,
  ManagementDayOneAttachmentDto,
  ManagementDayOneLogDto,
  ManagementDayOneTagDefDto,
  ManagementDayOneWriteBody,
  ManagementTaskCategory,
  ManagementTaskDto,
  ManagementTaskType,
  ManagementTaskWriteBody,
  TaskMonthCalendarDto,
} from '../models/management.models';

export interface DayOneSearchParams {
  from?: string;
  to?: string;
  q?: string;
  tagIds?: number[];
  ownerUserId?: number;
}

@Injectable({ providedIn: 'root' })
export class ManagementApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/management`;
  private readonly dayOneRoot = `${environment.apiBaseUrl}/api/management/day-one`;

  listCategories() {
    return this.http.get<ManagementTaskCategory[]>(`${this.root}/categories`);
  }

  createCategory(body: Pick<ManagementTaskCategory, 'name'> & { description?: string | null }) {
    return this.http.post<ManagementTaskCategory>(`${this.root}/categories`, body);
  }

  deleteCategory(id: number) {
    return this.http.delete<void>(`${this.root}/categories/${id}`);
  }

  listTaskTypes() {
    return this.http.get<ManagementTaskType[]>(`${this.root}/task-types`);
  }

  createTaskType(body: Pick<ManagementTaskType, 'name'> & { notes?: string | null }) {
    return this.http.post<ManagementTaskType>(`${this.root}/task-types`, body);
  }

  deleteTaskType(id: number) {
    return this.http.delete<void>(`${this.root}/task-types/${id}`);
  }

  listTasksReport() {
    return this.http.get<ManagementTaskDto[]>(`${this.root}/reports/tasks`);
  }

  listTasks() {
    return this.http.get<ManagementTaskDto[]>(`${this.root}/tasks`);
  }

  listUnscheduledTasks() {
    return this.http.get<ManagementTaskDto[]>(`${this.root}/tasks/unscheduled`);
  }

  taskCalendar(year: number, month: number) {
    return this.http.get<TaskMonthCalendarDto>(`${this.root}/tasks/calendar`, {
      params: { year: String(year), month: String(month) },
    });
  }

  createTask(body: ManagementTaskWriteBody) {
    return this.http.post<ManagementTaskDto>(`${this.root}/tasks`, body);
  }

  updateTask(id: number, body: ManagementTaskWriteBody) {
    return this.http.put<ManagementTaskDto>(`${this.root}/tasks/${id}`, body);
  }

  deleteTask(id: number) {
    return this.http.delete<void>(`${this.root}/tasks/${id}`);
  }

  // --- Day One (journal) — /api/management/day-one/**

  listDayOneTagDefinitions() {
    return this.http.get<ManagementDayOneTagDefDto[]>(`${this.dayOneRoot}/tag-definitions`);
  }

  searchDayOneEntries(params: DayOneSearchParams) {
    let p = new HttpParams();
    if (params.from) {
      p = p.set('from', params.from);
    }
    if (params.to) {
      p = p.set('to', params.to);
    }
    if (params.q) {
      p = p.set('q', params.q);
    }
    (params.tagIds ?? []).forEach((id) => {
      p = p.append('tagIds', String(id));
    });
    if (params.ownerUserId != null) {
      p = p.set('ownerUserId', String(params.ownerUserId));
    }
    return this.http.get<ManagementDayOneLogDto[]>(`${this.dayOneRoot}/entries`, { params: p });
  }

  dayOneEntriesForMonth(year: number, month: number, q?: string, tagIds?: number[]) {
    const from = `${year}-${String(month).padStart(2, '0')}-01`;
    const last = new Date(year, month, 0).getDate();
    const to = `${year}-${String(month).padStart(2, '0')}-${String(last).padStart(2, '0')}`;
    return this.searchDayOneEntries({ from, to, q, tagIds });
  }

  createDayOneEntry(body: ManagementDayOneWriteBody) {
    return this.http.post<ManagementDayOneLogDto>(`${this.dayOneRoot}/entries`, body);
  }

  updateDayOneEntry(id: number, body: ManagementDayOneWriteBody) {
    return this.http.put<ManagementDayOneLogDto>(`${this.dayOneRoot}/entries/${id}`, body);
  }

  deleteDayOneEntry(id: number) {
    return this.http.delete<void>(`${this.dayOneRoot}/entries/${id}`);
  }

  dayOneCalendar(year: number, month: number) {
    return this.http.get<DayOneCalendarDayDto[]>(`${this.dayOneRoot}/calendar`, {
      params: { year: String(year), month: String(month) },
    });
  }

  dayOneCounts(year: number, month?: number, day?: number) {
    let p = new HttpParams().set('year', String(year));
    if (month != null) {
      p = p.set('month', String(month));
    }
    if (day != null) {
      p = p.set('day', String(day));
    }
    return this.http.get<DayOneCountsDto>(`${this.dayOneRoot}/counts`, { params: p });
  }

  uploadDayOneAttachment(entryId: number, file: File) {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<ManagementDayOneAttachmentDto>(`${this.dayOneRoot}/entries/${entryId}/attachments`, fd);
  }

  deleteDayOneAttachment(attachmentId: number) {
    return this.http.delete<void>(`${this.dayOneRoot}/attachments/${attachmentId}`);
  }
}
