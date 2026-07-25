import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import {
  ManagementMonthNoteCalendarDto,
  ManagementMonthNoteDto,
  ManagementNowCardType,
  ManagementNowCardTypeWriteBody,
  ManagementCalendarType,
  ManagementCalendarTypeWriteBody,
  ManagementTaskCategory,
  ManagementTaskDto,
  ManagementTaskType,
  ManagementTaskWriteBody,
  ManagementMonthNoteWriteBody,
  ManagementMonthNoteAttachmentDto,
  ManagementAccountDto,
  ManagementAccountImportResultDto,
  ManagementAccountWriteBody,
  ManagementDocumentDto,
  ManagementDocumentWriteBody,
  ManagementRecordingDetailDto,
  ManagementRecordingItemDto,
  ManagementRecordingListDto,
  ManagementWriteupAttachmentDto,
  ManagementWriteupDto,
  ManagementWriteupWriteBody,
  ManagementWorkLogAttachmentDto,
  ManagementWorkLogCalendarDto,
  ManagementWorkLogEntryDto,
  ManagementWorkLogEntryWriteBody,
  TaskMonthCalendarDto,
  TravelGeocodeResultDto,
  TravelPlaceMapDto,
  TravelPlacePhotoDto,
  TravelPlaceWriteBody,
  TravelTripDetailDto,
  TravelTripSummaryDto,
  TravelTripWriteBody,
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

  listNowCardTypes() {
    return this.http.get<ManagementNowCardType[]>(`${this.root}/now-card-types`);
  }

  createNowCardType(body: ManagementNowCardTypeWriteBody) {
    return this.http.post<ManagementNowCardType>(`${this.root}/now-card-types`, body);
  }

  deleteNowCardType(id: number) {
    return this.http.delete<void>(`${this.root}/now-card-types/${id}`);
  }

  listCalendarTypes() {
    return this.http.get<ManagementCalendarType[]>(`${this.root}/calendar-types`);
  }

  createCalendarType(body: ManagementCalendarTypeWriteBody) {
    return this.http.post<ManagementCalendarType>(`${this.root}/calendar-types`, body);
  }

  deleteCalendarType(id: number) {
    return this.http.delete<void>(`${this.root}/calendar-types/${id}`);
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

  notesCalendar(year: number) {
    return this.http.get<ManagementMonthNoteCalendarDto>(`${this.root}/notes/calendar`, {
      params: { year: String(year) },
    });
  }

  listMonthNotes(year: number, month?: number | null) {
    const p: Record<string, string> = { year: String(year) };
    if (month != null) {
      p['month'] = String(month);
    }
    return this.http.get<ManagementMonthNoteDto[]>(`${this.root}/notes`, { params: p });
  }

  getMonthNote(id: number) {
    return this.http.get<ManagementMonthNoteDto>(`${this.root}/notes/${id}`);
  }

  createMonthNote(body: ManagementMonthNoteWriteBody) {
    return this.http.post<ManagementMonthNoteDto>(`${this.root}/notes`, body);
  }

  updateMonthNote(id: number, body: ManagementMonthNoteWriteBody) {
    return this.http.put<ManagementMonthNoteDto>(`${this.root}/notes/${id}`, body);
  }

  deleteMonthNote(id: number) {
    return this.http.delete<void>(`${this.root}/notes/${id}`);
  }

  uploadMonthNoteAttachment(noteId: number, file: File) {
    const fd = new FormData();
    fd.append('file', file, file.name);
    return this.http.post<ManagementMonthNoteAttachmentDto>(`${this.root}/notes/${noteId}/attachments`, fd);
  }

  deleteMonthNoteAttachment(attachmentId: number) {
    return this.http.delete<void>(`${this.root}/notes/attachments/${attachmentId}`);
  }

  getMonthNoteAttachmentBlob(attachmentId: number, disposition: 'inline' | 'attachment' = 'inline') {
    return this.http.get(`${this.root}/notes/attachments/${attachmentId}/file`, {
      responseType: 'blob',
      params: { disposition },
    });
  }

  listWriteups(year: number) {
    return this.http.get<ManagementWriteupDto[]>(`${this.root}/writeups`, {
      params: { year: String(year) },
    });
  }

  getWriteup(id: number) {
    return this.http.get<ManagementWriteupDto>(`${this.root}/writeups/${id}`);
  }

  createWriteup(body: ManagementWriteupWriteBody) {
    return this.http.post<ManagementWriteupDto>(`${this.root}/writeups`, body);
  }

  updateWriteup(id: number, body: ManagementWriteupWriteBody) {
    return this.http.put<ManagementWriteupDto>(`${this.root}/writeups/${id}`, body);
  }

  deleteWriteup(id: number) {
    return this.http.delete<void>(`${this.root}/writeups/${id}`);
  }

  uploadWriteupAttachment(writeupId: number, file: File) {
    const fd = new FormData();
    fd.append('file', file, file.name);
    return this.http.post<ManagementWriteupAttachmentDto>(`${this.root}/writeups/${writeupId}/attachments`, fd);
  }

  deleteWriteupAttachment(attachmentId: number) {
    return this.http.delete<void>(`${this.root}/writeups/attachments/${attachmentId}`);
  }

  getWriteupAttachmentBlob(attachmentId: number, disposition: 'inline' | 'attachment' = 'inline') {
    return this.http.get(`${this.root}/writeups/attachments/${attachmentId}/file`, {
      responseType: 'blob',
      params: { disposition },
    });
  }

  private readonly workLogRoot = `${this.root}/work-log`;

  workLogListBetween(fromIsoDate: string, toIsoDate: string) {
    return this.http.get<ManagementWorkLogEntryDto[]>(`${this.workLogRoot}/entries`, {
      params: { from: fromIsoDate, to: toIsoDate },
    });
  }

  workLogListForDay(dateIso: string) {
    return this.http.get<ManagementWorkLogEntryDto[]>(`${this.workLogRoot}/entries/day`, {
      params: { date: dateIso },
    });
  }

  workLogCalendar(year: number) {
    return this.http.get<ManagementWorkLogCalendarDto>(`${this.workLogRoot}/calendar`, {
      params: { year: String(year) },
    });
  }

  getWorkLogEntry(id: number) {
    return this.http.get<ManagementWorkLogEntryDto>(`${this.workLogRoot}/entries/${id}`);
  }

  createWorkLogEntry(body: ManagementWorkLogEntryWriteBody) {
    return this.http.post<ManagementWorkLogEntryDto>(`${this.workLogRoot}/entries`, body);
  }

  updateWorkLogEntry(id: number, body: ManagementWorkLogEntryWriteBody) {
    return this.http.put<ManagementWorkLogEntryDto>(`${this.workLogRoot}/entries/${id}`, body);
  }

  deleteWorkLogEntry(id: number) {
    return this.http.delete<void>(`${this.workLogRoot}/entries/${id}`);
  }

  uploadWorkLogAttachment(entryId: number, file: File) {
    const fd = new FormData();
    fd.append('file', file, file.name);
    return this.http.post<ManagementWorkLogAttachmentDto>(`${this.workLogRoot}/entries/${entryId}/attachments`, fd);
  }

  deleteWorkLogAttachment(attachmentId: number) {
    return this.http.delete<void>(`${this.workLogRoot}/attachments/${attachmentId}`);
  }

  getWorkLogAttachmentBlob(attachmentId: number, disposition: 'inline' | 'attachment' = 'inline') {
    return this.http.get(`${this.workLogRoot}/attachments/${attachmentId}/file`, {
      responseType: 'blob',
      params: { disposition },
    });
  }

  private readonly travelRoot = `${this.root}/travel`;

  travelGeocode(q: string) {
    return this.http.get<TravelGeocodeResultDto>(`${this.travelRoot}/geocode`, {
      params: { q },
    });
  }

  listTravelTrips() {
    return this.http.get<TravelTripSummaryDto[]>(`${this.travelRoot}/trips`);
  }

  getTravelTrip(id: number) {
    return this.http.get<TravelTripDetailDto>(`${this.travelRoot}/trips/${id}`);
  }

  createTravelTrip(body: TravelTripWriteBody) {
    return this.http.post<TravelTripDetailDto>(`${this.travelRoot}/trips`, body);
  }

  updateTravelTrip(id: number, body: TravelTripWriteBody) {
    return this.http.put<TravelTripDetailDto>(`${this.travelRoot}/trips/${id}`, body);
  }

  deleteTravelTrip(id: number) {
    return this.http.delete<void>(`${this.travelRoot}/trips/${id}`);
  }

  travelPlacesForMap(fromIso?: string | null, toIso?: string | null) {
    const params: Record<string, string> = {};
    if (fromIso) {
      params['from'] = fromIso;
    }
    if (toIso) {
      params['to'] = toIso;
    }
    return this.http.get<TravelPlaceMapDto[]>(`${this.travelRoot}/places`, { params });
  }

  addTravelPlace(tripId: number, body: TravelPlaceWriteBody) {
    return this.http.post<TravelTripDetailDto>(`${this.travelRoot}/trips/${tripId}/places`, body);
  }

  reorderTravelPlaces(tripId: number, orderedPlaceIds: number[]) {
    return this.http.put<TravelTripDetailDto>(`${this.travelRoot}/trips/${tripId}/places/order`, {
      orderedPlaceIds,
    });
  }

  updateTravelPlace(placeId: number, body: TravelPlaceWriteBody) {
    return this.http.put<TravelTripDetailDto>(`${this.travelRoot}/places/${placeId}`, body);
  }

  deleteTravelPlace(placeId: number) {
    return this.http.delete<void>(`${this.travelRoot}/places/${placeId}`);
  }

  uploadTravelPlacePhoto(placeId: number, file: File) {
    const fd = new FormData();
    fd.append('file', file, file.name);
    return this.http.post<TravelPlacePhotoDto>(`${this.travelRoot}/places/${placeId}/photos`, fd);
  }

  deleteTravelPlacePhoto(photoId: number) {
    return this.http.delete<void>(`${this.travelRoot}/photos/${photoId}`);
  }

  getTravelPlacePhotoBlob(photoId: number, disposition: 'inline' | 'attachment' = 'inline') {
    return this.http.get(`${this.travelRoot}/photos/${photoId}/file`, {
      responseType: 'blob',
      params: { disposition },
    });
  }

  private readonly accountsRoot = `${this.root}/accounts`;

  listAccounts() {
    return this.http.get<ManagementAccountDto[]>(this.accountsRoot);
  }

  createAccount(body: ManagementAccountWriteBody) {
    return this.http.post<ManagementAccountDto>(this.accountsRoot, body);
  }

  updateAccount(id: number, body: ManagementAccountWriteBody) {
    return this.http.put<ManagementAccountDto>(`${this.accountsRoot}/${id}`, body);
  }

  deleteAccount(id: number) {
    return this.http.delete<void>(`${this.accountsRoot}/${id}`);
  }

  /** One-time import: pushes legacy localStorage entries to the server. Returns inserted / skipped counts. */
  bulkImportAccounts(entries: ManagementAccountWriteBody[]) {
    return this.http.post<ManagementAccountImportResultDto>(`${this.accountsRoot}/bulk-import`, { entries });
  }

  private readonly documentsRoot = `${this.root}/documents`;

  listDocuments() {
    return this.http.get<ManagementDocumentDto[]>(this.documentsRoot);
  }

  uploadDocument(file: File, displayName: string, docType: string) {
    const fd = new FormData();
    fd.append('file', file, file.name);
    fd.append('displayName', displayName);
    fd.append('docType', docType);
    return this.http.post<ManagementDocumentDto>(this.documentsRoot, fd);
  }

  updateDocument(id: number, body: ManagementDocumentWriteBody) {
    return this.http.put<ManagementDocumentDto>(`${this.documentsRoot}/${id}`, body);
  }

  deleteDocument(id: number) {
    return this.http.delete<void>(`${this.documentsRoot}/${id}`);
  }

  getDocumentBlob(id: number, disposition: 'inline' | 'attachment' = 'inline') {
    return this.http.get(`${this.documentsRoot}/${id}/file`, {
      responseType: 'blob',
      params: { disposition },
    });
  }

  private readonly recordingsRoot = `${this.root}/recordings`;

  listRecordings(day?: string | null) {
    const params: Record<string, string> = {};
    if (day) {
      params['day'] = day;
    }
    return this.http.get<ManagementRecordingListDto>(this.recordingsRoot, { params });
  }

  searchRecordings(q: string) {
    return this.http.get<ManagementRecordingItemDto[]>(`${this.recordingsRoot}/search`, {
      params: { q },
    });
  }

  getRecordingDetail(path: string) {
    return this.http.get<ManagementRecordingDetailDto>(`${this.recordingsRoot}/detail`, {
      params: { path },
    });
  }

  getRecordingBlob(path: string, disposition: 'inline' | 'attachment' = 'inline') {
    return this.http.get(`${this.recordingsRoot}/file`, {
      responseType: 'blob',
      params: { path, disposition },
    });
  }

  transcribeRecording(path: string, force = false) {
    return this.http.post<ManagementRecordingDetailDto>(`${this.recordingsRoot}/transcribe`, {
      path,
      force,
    });
  }

  summarizeRecording(path: string, force = false) {
    return this.http.post<ManagementRecordingDetailDto>(`${this.recordingsRoot}/summarize`, {
      path,
      force,
    });
  }
}
