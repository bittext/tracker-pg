import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { MeMemberApiService } from '../services/me-member-api.service';

/** Sends users through credentials → Admin profile → member-id until onboarding is complete. */
export const onboardingCompleteGuard: CanActivateFn = () => {
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
      if (!s.profileSubmitted) {
        return router.createUrlTree(['/admin'], { queryParams: { onboardingProfile: '1' } });
      }
      return router.createUrlTree(['/onboarding/member-id']);
    }),
  );
};
