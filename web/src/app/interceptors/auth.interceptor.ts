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
      /** 403 can mean "not allowed" (e.g. not ADMIN) with a still-valid session — do not clear auth. */
      const shouldReauth =
        err instanceof HttpErrorResponse && isApiCall && err.status === 401;
      if (shouldReauth) {
        const redirect = router.url && router.url !== '/login' ? router.url : '/life/welcome';
        auth.logout(false);
        router.navigate(['/login'], { queryParams: { redirect } });
      }
      return throwError(() => err);
    }),
  );
};
