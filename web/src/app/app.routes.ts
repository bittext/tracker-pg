import { Routes } from '@angular/router';
import { adminGuard } from './guards/admin.guard';
import { authGuard } from './guards/auth.guard';
import { onboardingAdminGuard } from './guards/onboarding-admin.guard';
import { onboardingCompleteGuard } from './guards/onboarding-complete.guard';
import { onboardingCredentialsGuard } from './guards/onboarding-credentials.guard';
import { onboardingMemberIdGuard } from './guards/onboarding-member-id.guard';
import { AdminComponent } from './pages/admin/admin.component';
import { ExerciseComponent } from './pages/exercise/exercise.component';
import { FinanceComponent } from './pages/finance/finance.component';
import { JournalComponent } from './pages/journal/journal.component';
import { ManagementComponent } from './pages/management/management.component';
import { ReportsComponent } from './pages/reports/reports.component';
import { WelcomeComponent } from './pages/welcome/welcome.component';
import { ContactComponent } from './pages/contact/contact.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'welcome' },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'privacy',
    loadComponent: () => import('./pages/privacy-policy/privacy-policy.component').then((m) => m.PrivacyPolicyComponent),
  },
  { path: 'privacy-policy', redirectTo: 'privacy', pathMatch: 'full' },
  {
    path: 'onboarding/credentials',
    canActivate: [authGuard, onboardingCredentialsGuard],
    loadComponent: () =>
      import('./pages/onboarding/onboarding-credentials.component').then((m) => m.OnboardingCredentialsComponent),
  },
  {
    path: 'onboarding/member-id',
    canActivate: [authGuard, onboardingMemberIdGuard],
    loadComponent: () =>
      import('./pages/onboarding/onboarding-member-id.component').then((m) => m.OnboardingMemberIdComponent),
  },
  {
    path: 'welcome',
    canActivate: [authGuard, onboardingCompleteGuard],
    component: WelcomeComponent,
  },
  {
    path: 'exercise',
    canActivate: [authGuard, onboardingCompleteGuard],
    component: ExerciseComponent,
  },
  {
    path: 'finance',
    canActivate: [authGuard, onboardingCompleteGuard],
    component: FinanceComponent,
  },
  {
    path: 'management',
    canActivate: [authGuard, onboardingCompleteGuard],
    component: ManagementComponent,
  },
  {
    path: 'journal',
    canActivate: [authGuard, onboardingCompleteGuard],
    component: JournalComponent,
  },
  {
    path: 'reports',
    canActivate: [authGuard, onboardingCompleteGuard],
    component: ReportsComponent,
  },
  {
    path: 'contact',
    canActivate: [authGuard, onboardingCompleteGuard],
    component: ContactComponent,
  },
  {
    path: 'admin',
    canActivate: [authGuard, onboardingAdminGuard],
    component: AdminComponent,
  },
  {
    path: 'logs',
    canActivate: [adminGuard],
    loadComponent: () => import('./pages/logs/logs.component').then((m) => m.LogsComponent),
  },
  { path: '**', redirectTo: 'welcome' },
];
