import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { ManagementDeskContext } from '../services/management-desk.context';

const DESK_PATHS = [
  '/api/management/writeups',
  '/api/management/documents',
  '/api/management/recordings',
  '/api/management/accounts',
  '/api/management/subscriptions',
  '/api/management/auto-payments',
  '/api/management/notes',
  '/api/management/due',
  '/api/report-calendar',
];

export const managementDeskInterceptor: HttpInterceptorFn = (req, next) => {
  const desk = inject(ManagementDeskContext).desk();
  if (desk === 'LIFE') {
    return next(req);
  }
  const match = DESK_PATHS.some((p) => req.url.includes(p));
  if (!match) {
    return next(req);
  }
  return next(req.clone({ setParams: { desk } }));
};
