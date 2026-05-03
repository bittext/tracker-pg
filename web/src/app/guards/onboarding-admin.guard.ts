import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { MeMemberApiService } from '../services/me-member-api.service';

/**
 * Admin is reachable during onboarding for profile capture; otherwise same gating as {@link onboardingCompleteGuard}
 * except profile phase stays on Admin.
 */
export const onboardingAdminGuard: CanActivateFn = () => {
  const api = inject(MeMemberApiService);
  const router = inject(Router);
  return api.getOnboardingStatus().pipe(
    map((s) => {
      if (s.onboardingCompleted) {
        return true;
      }
      if (!s.credentialsStepCompleted) {
        return router.createUrlTree(['/onboarding/credentials']);
      }
      if (s.profileSubmitted) {
        return router.createUrlTree(['/onboarding/member-id']);
      }
      return true;
    }),
  );
};
