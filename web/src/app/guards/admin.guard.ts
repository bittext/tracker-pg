import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Requires a signed-in user whose app role is ADMIN. Used for /admin and for admin-only API routes. */
export const adminGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login'], {
      queryParams: { redirect: state.url || '/life/welcome' },
    });
  }
  if (auth.isAdmin()) {
    return true;
  }
  return router.createUrlTree(['/life/welcome']);
};
