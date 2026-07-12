import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { StepUpService } from '../services/step-up.service';

const STEP_UP_HEADER = 'X-Step-Up-Token';

function isStepUpRequired(err: unknown): boolean {
  if (!(err instanceof HttpErrorResponse) || err.status !== 403) {
    return false;
  }
  const body = err.error;
  if (body && typeof body === 'object' && 'error' in body) {
    return (body as { error?: unknown }).error === 'step_up_required';
  }
  return false;
}

export const stepUpInterceptor: HttpInterceptorFn = (req, next) => {
  const stepUp = inject(StepUpService);

  const attempt = (token?: string) => {
    const headers = token ? req.headers.set(STEP_UP_HEADER, token) : req.headers;
    return next(req.clone({ headers }));
  };

  return attempt().pipe(
    catchError((err: unknown) => {
      if (!isStepUpRequired(err) || req.headers.has(STEP_UP_HEADER)) {
        return throwError(() => err);
      }
      return from(stepUp.promptAndIssueToken()).pipe(
        switchMap((token) => {
          if (!token) {
            return throwError(() => err);
          }
          return attempt(token);
        }),
      );
    }),
  );
};
