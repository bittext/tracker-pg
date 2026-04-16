import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token;

  const isApiCall = req.url.startsWith('/api') || req.url.includes('/api/');
  const request = token && isApiCall ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(request).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401) {
        const redirect = router.url && router.url !== '/login' ? router.url : '/exercise';
        auth.logout(false);
        router.navigate(['/login'], { queryParams: { redirect } });
      }
      return throwError(() => err);
    }),
  );
};
