import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';

import { authInterceptor } from './interceptors/auth.interceptor';
import { stepUpInterceptor } from './interceptors/step-up.interceptor';
import { routes } from './app.routes';
import { assertAppNavRegistryValid } from './config/app-nav.config';

assertAppNavRegistryValid();

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, stepUpInterceptor])),
    provideAnimations(),
  ],
};
