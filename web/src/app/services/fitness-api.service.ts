import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import {
  BodyWeightLog,
  DailyExerciseReportDto,
  Exercise,
  ExerciseDayLog,
  MonthActivityCalendarDto,
  MonthlyExerciseReportDto,
} from '../models/fitness.models';

@Injectable({ providedIn: 'root' })
export class FitnessApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/fitness`;

  listExercises() {
    return this.http.get<Exercise[]>(`${this.root}/exercises`);
  }

  createExercise(body: Partial<Exercise>) {
    return this.http.post<Exercise>(`${this.root}/exercises`, body);
  }

  updateExercise(id: number, body: Partial<Exercise>) {
    return this.http.put<Exercise>(`${this.root}/exercises/${id}`, body);
  }

  deleteExercise(id: number) {
    return this.http.delete<void>(`${this.root}/exercises/${id}`);
  }

  listBodyWeight() {
    return this.http.get<BodyWeightLog[]>(`${this.root}/body-weight`);
  }

  addBodyWeight(body: Omit<BodyWeightLog, 'id'>) {
    return this.http.post<BodyWeightLog>(`${this.root}/body-weight`, body);
  }

  deleteBodyWeight(id: number) {
    return this.http.delete<void>(`${this.root}/body-weight/${id}`);
  }

  listDayLogsForDay(exerciseId: number, day: string) {
    return this.http.get<ExerciseDayLog[]>(`${this.root}/exercises/${exerciseId}/day-logs`, {
      params: { day },
    });
  }

  /** All exercises’ day logs in an inclusive date range (filter by exercise in the UI if needed). */
  listDayLogsBetween(from: string, to: string) {
    return this.http.get<ExerciseDayLog[]>(`${this.root}/day-logs`, {
      params: { from, to },
    });
  }

  addDayLog(
    exerciseId: number,
    body: Pick<ExerciseDayLog, 'performedOn' | 'notes'> & { durationMinutes?: number | null },
  ) {
    return this.http.post<ExerciseDayLog>(`${this.root}/exercises/${exerciseId}/day-logs`, body);
  }

  deleteDayLog(id: number) {
    return this.http.delete<void>(`${this.root}/day-logs/${id}`);
  }

  dailyReport(date: string) {
    return this.http.get<DailyExerciseReportDto>(`${this.root}/reports/daily`, {
      params: { date },
    });
  }

  monthlyReport(year: number, month: number) {
    return this.http.get<MonthlyExerciseReportDto>(`${this.root}/reports/monthly`, {
      params: { year: String(year), month: String(month) },
    });
  }

  monthActivityCalendar(year: number, month: number) {
    return this.http.get<MonthActivityCalendarDto>(`${this.root}/reports/month-calendar`, {
      params: { year: String(year), month: String(month) },
    });
  }
}
