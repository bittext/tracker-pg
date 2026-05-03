import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { MeMemberApiService } from '../services/me-member-api.service';

/** Member ID celebration step — only when profile is saved but onboarding not finished. */
export const onboardingMemberIdGuard: CanActivateFn = () => {
  const api = inject(MeMemberApiService);
  const router = inject(Router);
  return api.getOnboardingStatus().pipe(
    map((s) => {
      if (s.onboardingCompleted) {
        return router.createUrlTree(['/welcome']);
      }
      if (!s.credentialsStepCompleted) {
        return router.createUrlTree(['/onboarding/credentials']);
      }
      if (!s.profileSubmitted) {
        return router.createUrlTree(['/admin'], { queryParams: { onboardingProfile: '1' } });
      }
      return true;
    }),
  );
};
