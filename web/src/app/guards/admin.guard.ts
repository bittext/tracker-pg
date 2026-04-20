import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Requires a logged-in user whose role is ADMIN (server logs API is admin-only). */
export const adminGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login'], {
      queryParams: { redirect: state.url || '/exercise' },
    });
  }
  if (auth.isAdmin()) {
    return true;
  }
  return router.createUrlTree(['/exercise']);
};
