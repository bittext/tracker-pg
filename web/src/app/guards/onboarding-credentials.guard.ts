import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { MeMemberApiService } from '../services/me-member-api.service';

/** Only the credentials wizard; redirects if user belongs on another onboarding step. */
export const onboardingCredentialsGuard: CanActivateFn = () => {
  const api = inject(MeMemberApiService);
  const router = inject(Router);
  return api.getOnboardingStatus().pipe(
    map((s) => {
      if (s.onboardingCompleted) {
        return router.createUrlTree(['/welcome']);
      }
      if (s.credentialsStepCompleted && !s.profileSubmitted) {
        return router.createUrlTree(['/admin'], { queryParams: { onboardingProfile: '1' } });
      }
      if (s.profileSubmitted && !s.onboardingCompleted) {
        return router.createUrlTree(['/onboarding/member-id']);
      }
      return true;
    }),
  );
};
