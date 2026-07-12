import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Requires auth.canAccessMarkets(); otherwise redirect to Life welcome. */
export const marketsGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login'], {
      queryParams: { redirect: state.url || '/markets' },
    });
  }
  if (auth.canAccessMarkets()) {
    return true;
  }
  return router.createUrlTree(['/life/welcome']);
};
