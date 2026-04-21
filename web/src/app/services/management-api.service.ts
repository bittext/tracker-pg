import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import {
  ManagementTaskCategory,
  ManagementTaskDto,
  ManagementTaskType,
  ManagementTaskWriteBody,
  TaskMonthCalendarDto,
} from '../models/management.models';

@Injectable({ providedIn: 'root' })
export class ManagementApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/management`;

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
}
