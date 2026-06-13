import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  FinanceCreditCardDto,
  FinanceCreditCardOptionsDto,
  FinanceCreditCardRequestDto,
  FinanceCreditCardStatementDto,
  FinanceCreditCardStatementRequestDto,
  FinanceCreditCardSummaryDto,
} from '../models/finance.models';

@Injectable({ providedIn: 'root' })
export class FinanceCreditCardsApiService {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/finance/credit-cards';

  options() {
    return this.http.get<FinanceCreditCardOptionsDto>(`${this.root}/options`);
  }

  summary() {
    return this.http.get<FinanceCreditCardSummaryDto>(`${this.root}/summary`);
  }

  list() {
    return this.http.get<FinanceCreditCardDto[]>(this.root);
  }

  get(id: number) {
    return this.http.get<FinanceCreditCardDto>(`${this.root}/${id}`);
  }

  create(body: FinanceCreditCardRequestDto) {
    return this.http.post<FinanceCreditCardDto>(this.root, body);
  }

  update(id: number, body: FinanceCreditCardRequestDto) {
    return this.http.put<FinanceCreditCardDto>(`${this.root}/${id}`, body);
  }

  delete(id: number) {
    return this.http.delete<void>(`${this.root}/${id}`);
  }

  listStatements(cardId: number) {
    return this.http.get<FinanceCreditCardStatementDto[]>(`${this.root}/${cardId}/statements`);
  }

  createStatement(cardId: number, body: FinanceCreditCardStatementRequestDto) {
    return this.http.post<FinanceCreditCardStatementDto>(`${this.root}/${cardId}/statements`, body);
  }

  updateStatement(cardId: number, statementId: number, body: FinanceCreditCardStatementRequestDto) {
    return this.http.put<FinanceCreditCardStatementDto>(`${this.root}/${cardId}/statements/${statementId}`, body);
  }

  deleteStatement(cardId: number, statementId: number) {
    return this.http.delete<void>(`${this.root}/${cardId}/statements/${statementId}`);
  }
}
